package vn.htq.core

/** Committed user actions, expressed in stable source IDs, never display indices. */
sealed class Response {
    data class Single(val id: String): Response()
    data class Selection(val ids: List<String>): Response()
    data class Sequence(val ids: List<String>): Response()
    data class Pairs(val pairs: List<Pair<String,String>>): Response()
    data class Transfer(val movedIds: List<String>, val zone: String, val numberId: String? = null): Response()
    data class Combine(val movedIds: List<String>, val number: Int): Response()
    data class Split(val rounds: List<List<List<String>>>, val number: Int? = null): Response()
    data class Spatial(val placements: List<Pair<String,String>>): Response()
    data class Length(val units: List<LengthUnit>, val numberId: String): Response()
    data class Capacity(val pours: List<Pour>, val numberId: String): Response()
    data class Clock(val hour: Int, val minute: Int): Response()
    data class Hotspot(val geometryId: String, val x: Double, val y: Double): Response()
    data class QuantitySteps(val steps: List<List<String>>): Response()
    data class Actions(val actions: List<SceneAction>): Response()
    data class Chains(val chains: List<List<String>>): Response()
    object ParticipationDone: Response()
}
data class LengthUnit(val id: String, val start: Double)
data class Pour(val cupId: String, val containerId: String)
data class SceneAction(val objectId: String, val targetId: String, val verb: String)

/** Pure domain evaluator. Its input is committed scene evidence, not UI correctness flags.
 * Unknown/malformed evidence fails closed. UI must construct evidence from actual interaction.
 */
internal object InteractionRules {
    val types=setOf("single_object","set_equals","ordered_sequence","participation","pair_map",
        "shape_group_placement","one_per_destination","transfer_count","numeric_result_after_transfer",
        "numeric_result_after_combine","numeric_result_after_split","distinct_unordered_partitions","exact_partition",
        "memory_pairs","spatial_relations","numeric_measure","clock_hands","any_hotspot_region",
        "exact_art_quantity_set","ordered_quantity_steps","place_setting_counts","ordered_actions","unordered_chains")
    private fun Obj.ints(k:String)=a(k).map{(it as Number).let{n->n.toInt().also{v->require(n.toDouble()==v.toDouble())}}}
    private fun Obj.rows(k:String)=a(k).map{it.obj()}
    private fun Obj.lists(k:String)=a(k).map{it.arr().map{v->v.str()}}
    private fun Obj.pairs(k:String)=lists(k).map{require(it.size==2);it[0] to it[1]}
    private fun <T> unique(xs:List<T>)=xs.size==xs.toSet().size
    private fun <T> exact(xs:List<T>, expected:Collection<T>)=unique(xs)&&xs.size==expected.size&&xs.toSet()==expected.toSet()
    private fun unorderedPair(p:Pair<String,String>)=listOf(p.first,p.second).sorted()
    private fun objects(scene:Obj)=scene.rows("objects").associateBy{it.s("object_id")}
    private fun number(scene:Obj,id:String?)=objects(scene)[id]?.let{if(it.optional("role") in setOf("number_tile","numeric_response"))it.n("value")else null}
    private fun placements(response:Response):List<Pair<String,String>> = (response as Response.Pairs).pairs
    private fun allSources(ps:List<Pair<String,String>>, sources:List<String>, destinations:List<String>)=
        exact(ps.map{it.first},sources)&&ps.all{it.second in destinations}
    private fun transferAllowed(scene:Obj):List<String> {
        val s=scene.o("transfer_setup");val ids=scene.strings("distinct_instance_ids")
        val initial=s.n("initial")
        return if(s.optional("direction")=="add") ids.drop(initial) else ids.take(initial)
    }
    // Synthetic IDs are deterministic for scenes whose official pool has no individual IDs.
    // They are derived only from the source pool order and instance count, not from answers.
    fun splitIds(taskId:String,scene:Obj):List<String> {
        val s=scene.o("split_setup");val counts=s.ints("counts")
        val count=if(s.s("operation")=="combine")counts.sum()else counts.first()
        return (1..count).map{"$taskId:split$it"}
    }
    fun poolInstances(taskId:String,scene:Obj):Map<String,String> = buildMap {
        scene.rows("selection_pool").forEachIndexed{index,row->
            repeat(row.n("available_count")){n->put("$taskId:pool${index+1}-${n+1}",row.s("art_id"))}
        }
    }
    private fun countsMatch(ids:List<String>,pool:Map<String,String>,required:Obj):Boolean {
        if(!unique(ids)||ids.any{it !in pool})return false
        val actual=ids.groupingBy{pool.getValue(it)}.eachCount()
        val expected=required.mapValues{(_,n)->(n as Number).toInt()}.filterValues{it>0}
        return actual==expected
    }
    private fun inside(x:Double,y:Double,polygon:Arr):Boolean {
        if(polygon.size<3)return false
        val points=polygon.map{it.arr().let{p->(p[0] as Number).toDouble() to (p[1] as Number).toDouble()}}
        var hit=false
        var last=points.last()
        for(p in points){
            val dx=p.first-last.first;val dy=p.second-last.second
            val cross=(x-last.first)*dy-(y-last.second)*dx
            if(kotlin.math.abs(cross)<1e-10 && x>=minOf(last.first,p.first)-1e-10 && x<=maxOf(last.first,p.first)+1e-10 && y>=minOf(last.second,p.second)-1e-10 && y<=maxOf(last.second,p.second)+1e-10)return true
            if((p.second>y)!=(last.second>y) && x<(last.first-p.first)*(y-p.second)/(last.second-p.second)+p.first)hit=!hit
            last=p
        }
        return hit
    }
    fun matches(taskId:String,rule:Obj,scene:Obj,hotspots:List<Obj>,response:Response):Boolean = runCatching {
        when(rule.s("type")){
            "single_object" -> (response as? Response.Single)?.id==rule.s("accepted_id")
            "set_equals" -> exact((response as Response.Selection).ids,rule.strings("accepted_ids"))
            "ordered_sequence" -> (response as? Response.Sequence)?.ids?.let{it in rule.lists("accepted_sequences")}==true
            "participation" -> response==Response.ParticipationDone
            "pair_map" -> {
                val ps=placements(response);val expected=rule.pairs("pairs")
                unique(ps.map{it.first})&&(rule.b("allow_many_to_one")||unique(ps.map{it.second}))&&exact(ps,expected)
            }
            "shape_group_placement" -> {
                val ps=placements(response);val groups=rule.rows("groups")
                allSources(ps,groups.flatMap{it.strings("sources")},groups.flatMap{it.strings("destinations")})&&
                    exact(ps.map{it.second},groups.flatMap{it.strings("destinations")})&&ps.all{p->groups.any{p.first in it.strings("sources")&&p.second in it.strings("destinations")}}
            }
            "one_per_destination" -> {
                val ps=placements(response);val ds=rule.strings("destination_ids")
                allSources(ps,rule.strings("source_ids"),ds)&&ds.all{d->ps.count{it.second==d}==rule.n("each_destination_count")}
            }
            "transfer_count","numeric_result_after_transfer" -> {
                val a=response as Response.Transfer;val setup=scene.o("transfer_setup");val moved=a.movedIds
                val countOk=unique(moved)&&moved.size==rule.n("required_moved")&&moved.all{it in transferAllowed(scene)}&&a.zone=="destination"
                if(rule.s("type")=="transfer_count")countOk&&a.numberId==null&&a.zone==rule.s("target_zone")
                else {val dir=setup.s("direction");val result=setup.n("initial")+(if(dir=="add")moved.size else -moved.size)
                    countOk&&dir==rule.s("operation")&&result==rule.n("required_result")&&number(scene,a.numberId)==result}
            }
            "numeric_result_after_combine" -> {
                val a=response as Response.Combine;val ids=splitIds(taskId,scene)
                exact(a.movedIds,ids)&&a.number==ids.size&&a.number==rule.n("required_result")
            }
            "numeric_result_after_split","exact_partition","distinct_unordered_partitions" -> {
                val a=response as Response.Split;val ids=splitIds(taskId,scene)
                val valid=a.rounds.all{it.size==2&&exact(it.flatten(),ids)}
                val counts=a.rounds.map{round->round.map{it.size}}
                valid&&ids.size==rule.n("total")&&when(rule.s("type")){
                    "numeric_result_after_split" -> counts.size==1&&counts[0]==listOf(rule.n("required_first_bin_count"),rule.n("required_result"))&&a.number==counts[0][1]
                    "exact_partition" -> counts==listOf(rule.ints("accepted_counts"))&&a.number==null
                    else -> {val normalized=counts.map{it.sorted()};val accepted=rule.a("accepted_partitions").map{it.arr().map{n->(n as Number).toInt()}.sorted()}
                        counts.size==rule.n("partition_count")&&counts.all{it.all{n->n>=rule.n("minimum_each_bin")}}&&unique(normalized)&&normalized.all{it in accepted}&&a.number==null}
                }
            }
            "memory_pairs" -> {
                val ps=placements(response);val expected=rule.pairs("pairs")
                unique(ps.flatMap{listOf(it.first,it.second)})&&exact(ps.map(::unorderedPair),expected.map(::unorderedPair))
            }
            "spatial_relations" -> {
                val ps=(response as Response.Spatial).placements;val obs=objects(scene);val steps=scene.o("native_binding").rows("steps")
                exact(ps.map{it.first},steps.map{it.s("movable_id")})&&ps.all{p->
                    val step=steps.single{it.s("movable_id")==p.first};val zone=obs.getValue(p.second)
                    p.second in step.strings("zone_ids")&&zone.s("role")=="spatial_zone"&&zone.s("anchor_id")==step.s("anchor_id")&&
                        rule.rows("relations").any{r->r.s("object_id")==p.first&&r.s("anchor_id")==step.s("anchor_id")&&r.s("relation")==zone.s("relation")}}
            }
            "numeric_measure" -> {
                val b=scene.o("native_binding");val m=b.o("measurement");val required=rule.n("required_result")
                when(response){
                    is Response.Length -> {val target=m.n("target_length_units");val units=response.units.sortedBy{it.start}
                        b.s("scene_type")=="graded_length_measure"&&units.size==target&&unique(units.map{it.id})&&units.all{it.id in b.strings("unit_ids")&&it.start.isFinite()}&&
                            units.withIndex().all{(n,u)->kotlin.math.abs(u.start-n*m.n("unit_length"))<=1e-6}&&number(scene,response.numberId)==required&&target==required}
                    is Response.Capacity -> {val ds=b.strings("container_ids");val caps=m.ints("capacities");val initial=m.ints("initial_fill_units")
                        b.s("scene_type")=="graded_capacity_measure"&&response.pours.all{it.cupId==b.s("cup_id")&&it.containerId in ds}&&
                            ds.indices.all{n->response.pours.count{it.containerId==ds[n]}+initial[n]==caps[n]}&&number(scene,response.numberId)==required}
                    else -> false
                }
            }
            "clock_hands" -> {val a=response as Response.Clock;a.hour in 1..12&&a.minute in 0..59&&a.minute%5==0&&a.hour==rule.n("hour")&&a.minute==rule.n("minute")}
            "any_hotspot_region" -> {
                val a=response as Response.Hotspot
                a.x.isFinite()&&a.y.isFinite()&&a.x in 0.0..1.0&&a.y in 0.0..1.0&&a.geometryId==rule.s("geometry_id")&&hotspots.any{h->
                    h.s("id")==a.geometryId&&taskId in h.strings("task_ids")&&h.rows("regions").any{r->r.s("key") in rule.strings("accepted_region_keys")&&r.a("polygons").any{inside(a.x,a.y,it.arr())}}}
            }
            "exact_art_quantity_set" -> countsMatch((response as Response.Selection).ids,poolInstances(taskId,scene),rule.o("required_counts"))
            "ordered_quantity_steps" -> {val a=response as Response.QuantitySteps;val steps=rule.rows("steps");val pool=poolInstances(taskId,scene)
                a.steps.size==steps.size&&unique(a.steps.flatten())&&a.steps.indices.all{n->a.steps[n].size==steps[n].n("total")&&countsMatch(a.steps[n],pool,steps[n].o("required_counts"))}}
            "place_setting_counts" -> {val ps=placements(response);val ds=rule.strings("destinations");val gs=rule.rows("item_groups")
                allSources(ps,gs.flatMap{it.strings("source_ids")},ds)&&gs.all{g->ds.all{d->ps.count{it.first in g.strings("source_ids")&&it.second==d}==g.n("per_destination")}}}
            "ordered_actions" -> (response as Response.Actions).actions==rule.rows("actions").map{SceneAction(it.s("object_id"),it.s("target_id"),it.s("verb"))}
            "unordered_chains" -> exact((response as Response.Chains).chains,rule.lists("chains"))
            else -> false
        }
    }.getOrDefault(false)
}
