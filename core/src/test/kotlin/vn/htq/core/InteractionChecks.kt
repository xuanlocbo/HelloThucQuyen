package vn.htq.core

/** Source-derived fixtures are test-only. Never call these factories from the UI. */
internal object InteractionChecks {
    private fun Obj.rows(k:String)=a(k).map{it.obj()}
    private fun Obj.ints(k:String)=a(k).map{(it as Number).toInt()}
    private fun Obj.lists(k:String)=a(k).map{it.arr().map{v->v.str()}}
    private fun Obj.pairs(k:String)=lists(k).map{it[0] to it[1]}
    private fun numberId(scene:Obj,value:Int)=scene.rows("objects").single{it.optional("role") in setOf("number_tile","numeric_response")&&it.n("value")==value}.s("object_id")
    private fun poolSelection(pool:Map<String,String>,counts:Obj)=counts.flatMap{(art,n)->pool.filterValues{it==art}.keys.take((n as Number).toInt())}
    fun reference(id:String,r:Obj,s:Obj,h:List<Obj>):Response=when(r.s("type")){
        "single_object"->Response.Single(r.s("accepted_id"))
        "set_equals"->Response.Selection(r.strings("accepted_ids"))
        "ordered_sequence"->Response.Sequence(r.lists("accepted_sequences").first())
        "participation"->Response.ParticipationDone
        "pair_map","memory_pairs"->Response.Pairs(r.pairs("pairs"))
        "shape_group_placement"->Response.Pairs(r.rows("groups").flatMap{it.strings("sources").zip(it.strings("destinations"))})
        "one_per_destination"->Response.Pairs(r.strings("source_ids").zip(r.strings("destination_ids")))
        "transfer_count","numeric_result_after_transfer"->{val ts=s.o("transfer_setup");val ids=s.strings("distinct_instance_ids");val pool=if(ts.optional("direction")=="add")ids.drop(ts.n("initial"))else ids.take(ts.n("initial"));Response.Transfer(pool.take(r.n("required_moved")),"destination",if(r.s("type")=="numeric_result_after_transfer")numberId(s,r.n("required_result"))else null)}
        "numeric_result_after_combine"->Response.Combine(InteractionRules.splitIds(id,s),r.n("required_result"))
        "numeric_result_after_split","exact_partition","distinct_unordered_partitions"->{
            val ids=InteractionRules.splitIds(id,s);val counts=when(r.s("type")){
                "numeric_result_after_split"->listOf(listOf(r.n("required_first_bin_count"),r.n("required_result")))
                "exact_partition"->listOf(r.ints("accepted_counts"))
                else->r.a("accepted_partitions").take(r.n("partition_count")).map{it.arr().map{v->(v as Number).toInt()}}
            };Response.Split(counts.map{listOf(ids.take(it[0]),ids.drop(it[0]))},if(r.s("type")=="numeric_result_after_split")r.n("required_result")else null)
        }
        "spatial_relations"->Response.Spatial(r.rows("relations").map{x->val step=s.o("native_binding").rows("steps").single{it.s("movable_id")==x.s("object_id")};val zone=s.rows("objects").single{it.s("object_id") in step.strings("zone_ids")&&it.optional("relation")==x.s("relation")};x.s("object_id") to zone.s("object_id")})
        "numeric_measure"->{val b=s.o("native_binding");val m=b.o("measurement");val num=numberId(s,r.n("required_result"));if(b.s("scene_type")=="graded_length_measure")Response.Length(b.strings("unit_ids").take(m.n("target_length_units")).mapIndexed{i,v->LengthUnit(v,(i*m.n("unit_length")).toDouble())},num)else Response.Capacity(b.strings("container_ids").flatMapIndexed{i,v->List(m.ints("capacities")[i]-m.ints("initial_fill_units")[i]){Pour(b.s("cup_id"),v)}},num)}
        "clock_hands"->Response.Clock(r.n("hour"),r.n("minute"))
        "any_hotspot_region"->{val g=h.single{it.s("id")==r.s("geometry_id")};val p=g.rows("regions").first{it.s("key") in r.strings("accepted_region_keys")}.a("polygons").first().arr().first().arr();Response.Hotspot(g.s("id"),(p[0] as Number).toDouble(),(p[1] as Number).toDouble())}
        "exact_art_quantity_set"->Response.Selection(poolSelection(InteractionRules.poolInstances(id,s),r.o("required_counts")))
        "ordered_quantity_steps"->Response.QuantitySteps(r.rows("steps").map{poolSelection(InteractionRules.poolInstances(id,s),it.o("required_counts"))})
        "place_setting_counts"->Response.Pairs(r.rows("item_groups").flatMap{g->g.strings("source_ids").zip(r.strings("destinations").flatMap{d->List(g.n("per_destination")){d}})})
        "ordered_actions"->Response.Actions(r.rows("actions").map{SceneAction(it.s("object_id"),it.s("target_id"),it.s("verb"))})
        "unordered_chains"->Response.Chains(r.lists("chains"))
        else->error("Unimplemented test fixture ${r.s("type")}")
    }
    fun invalid(a:Response):List<Response> = when(a){
        is Response.Single->listOf(Response.Single("foreign"),Response.Selection(listOf(a.id)))
        is Response.Selection->listOf(a.copy(ids=a.ids+"foreign"),a.copy(ids=a.ids+a.ids.first()))
        is Response.Sequence->listOf(a.copy(ids=a.ids+"foreign"),a.copy(ids=emptyList()))
        is Response.Pairs->listOf(a.copy(pairs=a.pairs+a.pairs.first()),a.copy(pairs=a.pairs.dropLast(1)),a.copy(pairs=a.pairs.mapIndexed{i,p->if(i==0)p.first to "foreign" else p}))
        is Response.Transfer->listOf(a.copy(movedIds=a.movedIds+a.movedIds.first()),a.copy(movedIds=a.movedIds.dropLast(1)),a.copy(zone="source"),a.copy(movedIds=listOf("foreign")))+if(a.numberId!=null)listOf(a.copy(numberId="foreign"))else emptyList()
        is Response.Combine->listOf(a.copy(number=a.number+1),a.copy(movedIds=a.movedIds.dropLast(1)),a.copy(movedIds=a.movedIds+a.movedIds.first()))
        is Response.Split->listOf(a.copy(rounds=emptyList()),a.copy(rounds=a.rounds.map{listOf(it[0]+it[1].first(),it[1])}))+if(a.rounds.size>1)listOf(a.copy(rounds=listOf(a.rounds[0],a.rounds[0].reversed())))else if(a.number!=null)listOf(a.copy(number=a.number+1))else emptyList()
        is Response.Spatial->listOf(a.copy(placements=a.placements.dropLast(1)),a.copy(placements=a.placements+a.placements.first()),a.copy(placements=a.placements.map{it.first to "foreign"}))
        is Response.Length->listOf(a.copy(units=a.units.dropLast(1)),a.copy(units=a.units.map{it.copy(start=0.0)}),a.copy(units=a.units.map{it.copy(id=a.units.first().id)}),a.copy(units=a.units.map{it.copy(start=Double.NaN)}),a.copy(numberId="foreign"))
        is Response.Capacity->listOf(a.copy(pours=a.pours.dropLast(1)),a.copy(pours=a.pours+a.pours.first()),a.copy(pours=a.pours.map{it.copy(cupId="foreign")}),a.copy(numberId="foreign"))
        is Response.Clock->listOf(a.copy(hour=if(a.hour==12)1 else a.hour+1),a.copy(minute=5),a.copy(minute=60))
        is Response.Hotspot->listOf(a.copy(x=Double.NaN),a.copy(x=-0.1),a.copy(geometryId="foreign"),a.copy(x=0.0,y=0.0))
        is Response.QuantitySteps->listOf(a.copy(steps=a.steps.reversed()),a.copy(steps=a.steps.dropLast(1)),a.copy(steps=a.steps.map{it+it.first()}))
        is Response.Actions->listOf(a.copy(actions=a.actions.reversed()),a.copy(actions=a.actions.dropLast(1)),a.copy(actions=a.actions.map{it.copy(verb="foreign")}))
        is Response.Chains->listOf(a.copy(chains=a.chains.dropLast(1)),a.copy(chains=a.chains.map{it.reversed()}),a.copy(chains=a.chains+listOf(a.chains.first())))
        Response.ParticipationDone->listOf(Response.Single("foreign"))
    }
}
