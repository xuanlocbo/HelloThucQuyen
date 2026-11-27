package vn.htq.core

import vn.htq.core.ui.*

/** The visible scene supplies candidates; assessment rules are never used to build a control.
 * The event log is replayed through the same validators for undo and restore.
 */
class AdvancedInteraction internal constructor(private val catalog:Catalog,val task:UiTask,val type:String){
    private val scene=catalog.tasks.getValue(task.id).o("scene")
    private val binding=scene["native_binding"]?.obj().orEmpty()
    private val objects=scene.a("objects").map{it.obj()}.associateBy{it.s("object_id")}
    private val transfer=scene["transfer_setup"]?.obj().orEmpty()
    private val split=scene["split_setup"]?.obj().orEmpty()
    private val measurement=binding["measurement"]?.obj().orEmpty()
    private val clock=binding["clock"]?.obj().orEmpty()
    private fun Obj.ids(k:String)=this[k]?.arr()?.map{it.str()}.orEmpty()
    private fun Obj.ints(k:String)=this[k]?.arr()?.map{(it as Number).toInt()}.orEmpty()
    private fun art(id:String?)=id?.let{catalog.assetPath(it,"image")}
    data class Card(val id:String,val path:String?,val label:String,val crop:List<Float> = emptyList())
    data class SpatialStep(val source:Card,val anchor:UiElement.Spatial,val zones:List<Card>,val zoneDepths:List<Int>)
    data class Vessel(val id:String,val path:String,val label:String,val capacity:Int,val initial:Int)
    private fun objCard(id:String,label:String?=null):Card {val o=objects.getValue(id);return Card(id,o.optional("visual_path")?.also{require(it in catalog.paths)}?:art(o.optional("art_id")),label?:o.optional("accessibility_label")?:"Vật",o["display_crop_normalized"]?.arr()?.map{(it as Number).toFloat()}.orEmpty())}
    val isTransfer=type in setOf("transfer_count","numeric_result_after_transfer")
    val isSplit=type in setOf("numeric_result_after_combine","numeric_result_after_split","exact_partition","distinct_unordered_partitions")
    val isPool=type in setOf("exact_art_quantity_set","ordered_quantity_steps")
    val isLength=type=="numeric_measure"&&binding.optional("scene_type")=="graded_length_measure"
    val isCapacity=type=="numeric_measure"&&binding.optional("scene_type")=="graded_capacity_measure"
    val isClock=type=="clock_hands"
    val isHotspot=type=="any_hotspot_region"
    val isShape=type=="shape_group_placement"
    val isSpatial=type=="spatial_relations"
    val isActions=type=="ordered_actions"
    val isChains=type=="unordered_chains"
    val isMix=type=="single_object"&&task.groups.any{it.role=="mix_sources"}
    val isSimulation=type=="participation"&&binding.optional("scene_type")=="participation_simulation"
    val isCareer=type=="participation"&&objects.values.any{it.optional("role")=="tool_choice"}
    val supported=isMix||isSimulation||isCareer||isTransfer||isSplit||isPool||isLength||isCapacity||isClock||isHotspot||isShape||isSpatial||isActions||isChains
    val addDirection=transfer.optional("direction")=="add"
    val combine=type=="numeric_result_after_combine"
    val twoRounds=type=="distinct_unordered_partitions"
    val twoStages=type=="ordered_quantity_steps"
    val initialTransfer=if(isTransfer)transfer.n("initial") else 0
    val sourceArt=art(transfer.optional("source_art_id"))
    val destinationArt=art(transfer.optional("destination_art_id")?:split.optional("bin_art_id"))
    val instances:List<Card> = when {
        isTransfer->scene.strings("distinct_instance_ids").mapIndexed{i,id->Card(id,art(transfer.s("item_art_id")),"Vật ${i+1}")}
        isSplit->{val ids=InteractionRules.splitIds(task.id,scene);val arts=split.strings("item_art_ids");ids.mapIndexed{i,id->Card(id,art(arts[if(combine&&arts.size>1&&i>=split.ints("counts")[0])1 else 0]),"Vật ${i+1}")}}
        isPool->InteractionRules.poolInstances(task.id,scene).entries.mapIndexed{i,(id,aid)->Card(id,art(aid),"Vật ${i+1}")}
        else->emptyList()
    }
    val sourceCards=when {
        isShape->objects.filterValues{it.optional("role")=="source"}.keys.map{objCard(it)}
        isActions->objects.keys.mapIndexed{i,id->objCard(id,"Vật ${i+1}")}
        else->emptyList()
    }
    val targetCards=if(isShape)binding.ids("targets").mapIndexed{i,id->Card(id,art(scene.s("canvas_art_id")),"Vị trí ${i+1}",objects.getValue(id).a("rect_normalized").map{(it as Number).toFloat()})} else emptyList()
    val shapeTargets=targetCards.map{c->val o=objects.getValue(c.id);val g=o.o("geometry");c.id to UiOutline(g.s("type"),c.crop,g["points"]?.arr()?.map{p->p.arr().map{(it as Number).toFloat()}}.orEmpty())}.toMap()
    val combineFirst=if(combine)split.ints("counts").first()else 0
    val board=task.groups.flatMap{it.items}.filterIsInstance<UiElement.Board>().firstOrNull()
    val spatialSteps=if(isSpatial)binding.a("steps").map{v->val s=v.obj();val ids=s.ids("zone_ids");
        fun Obj.floats(k:String)=a(k).map{(it as Number).toFloat()}
        SpatialStep(objCard(s.s("movable_id")),UiElement.Spatial(requireNotNull(art(objects.getValue(s.s("anchor_id")).s("art_id"))),s.floats("anchor_center"),s.floats("anchor_size_normalized"),ids.map{val z=objects.getValue(it);UiZone(z.floats("center_normalized"),z.floats("visual_size_normalized"))}),ids.map{objCard(it)},ids.map{objects.getValue(it).n("z_index")})}else emptyList()
    val chainColumns=if(isChains)scene.a("columns").map{v->v.arr().map{objCard(it.str())}}else emptyList()
    val lengthUnits=if(isLength)binding.ids("unit_ids").mapIndexed{i,id->objCard(id,"Que ${i+1}")}else emptyList()
    val lengthBoard=(if(task.prelude)task.afterPrelude else task.groups).flatMap{it.items}.filterIsInstance<UiElement.MeasureLength>().firstOrNull()
    val vessels=if(isCapacity)binding.ids("container_ids").mapIndexed{i,id->Vessel(id,requireNotNull(art(objects.getValue(id).s("art_id"))),('A'+i).toString(),measurement.ints("capacities")[i],measurement.ints("initial_fill_units")[i])}else emptyList()
    val cup=if(isCapacity)objCard(binding.s("cup_id"),"Cốc đo")else null
    val hotspotPath=art(scene.optional("hotspot_base_art_id"))
    private val geometry=scene.ids("hotspot_geometry_ids").firstOrNull()
    private val numberCards=objects.filterValues{it.optional("role") in setOf("numeric_response","number_tile")}.map{(id,v)->Card(id,null,v.n("value").toString())}
    val numbers=if(isSplit&&type in setOf("numeric_result_after_combine","numeric_result_after_split"))(0..10).map{Card("number:$it",null,it.toString())}else numberCards
    val mixInputs=if(isMix)task.groups.filter{it.role=="mix_sources"}.flatMap{it.items}.mapIndexed{i,e->Card("mix:$i",(e as UiElement.Image).path,"Màu ${i+1}")}else emptyList()
    // Model pigments from the two source swatches; result is independent of the assessment answer ID.
    private val mixResultPath=if(isMix)when(mixInputs.map{it.path}.toSet()){
        setOf(art("IMG-EDU-0270"),art("IMG-EDU-0263"))->art("IMG-EDU-0256")
        setOf(art("IMG-EDU-0263"),art("IMG-EDU-0264"))->art("IMG-EDU-0265")
        else->error("Unknown source pigment pair")
    }else null
    val predictionCards=if(isMix||isSimulation)objects.filterValues{it.optional("role")=="choice"}.keys.map{objCard(it)}else emptyList()
    val simulationInputs=if(isSimulation)binding.o("initial_stage").ids("object_ids").map{objCard(it)}else emptyList()
    private val resultStage=if(isSimulation)binding.o("result_stage")else emptyMap()
    val careerCards=if(isCareer)objects.filterValues{it.optional("role")=="career_choice"}.keys.map{objCard(it)}else emptyList()
    val toolCards=if(isCareer)objects.filterValues{it.optional("role")=="tool_choice"}.keys.map{objCard(it)}else emptyList()
    private var viewed=false;private var prediction:String?=null;private var career:String?=null;private val chosenTools=linkedSetOf<String>()
    private val mixSelected=linkedSetOf<String>()
    val resultViewed get()=viewed
    val predictionId get()=prediction
    val careerId get()=career
    val chosenToolIds get()=chosenTools.toSet()
    val mixSelection get()=mixSelected.toSet()
    val visibleResultCards get()=if(!viewed)emptyList()else if(isMix)listOf(Card("mixed",mixResultPath,"Màu sau khi pha"))else resultStage.ids("object_ids").map{objCard(it)}
    val waterComparison get()=viewed&&isSimulation&&resultStage["explicit_native_state"]!=null
    fun choosePigment(id:String)=record(listOf("pigment",id))
    fun viewResult()=record(listOf("view"))
    fun predict(id:String)=record(listOf("predict",id))
    fun chooseCareer(id:String)=record(listOf("career",id))
    fun chooseTool(id:String)=record(listOf("tool",id))
    private val needsNumber=isLength||isCapacity||type in setOf("numeric_result_after_transfer","numeric_result_after_combine","numeric_result_after_split")
    private val moved=linkedSetOf<String>();private val placed=linkedMapOf<String,String>();private val rounds=mutableListOf<List<List<String>>>()
    private val pool=linkedSetOf<String>();private val stages=mutableListOf<List<String>>()
    private val units=mutableListOf<LengthUnit>();private val pours=mutableListOf<Pour>();private val actions=mutableListOf<SceneAction>();private val chains=mutableListOf<List<String>>();private val chain=mutableListOf<String>()
    private var numberId:String?=null
    var hour=if(isClock)clock.n("initial_hour")else 12;private set
    var minute=if(isClock)clock.n("initial_minute")else 0;private set
    private var clockTouched=false
    var point:Pair<Double,Double>?=null;private set
    var pending:String?=null;private set
    private var cupSelected=false
    val cupReady get()=cupSelected
    private val events=mutableListOf<List<String>>()
    val movedIds get()=moved.toSet();val assignments get()=placed.toMap();val selectedIds get()=(pool+chain+stages.flatten()+chains.flatten()).toSet();val committedRounds get()=rounds.toList();val committedStages get()=stages.toList();val committedChains get()=chains.toList();val committedActions get()=actions.toList()
    val selectedNumber get()=numberId;val placedUnits get()=units.toList()
    fun fill(v:Vessel)=(v.initial+pours.count{it.containerId==v.id}).toFloat()/v.capacity
    fun transferAvailable(id:String)=instances.indexOfFirst{it.id==id}.let{i->i>=0&&if(addDirection)i>=initialTransfer else i<initialTransfer}
    private fun validInstance(id:String)=instances.any{it.id==id}
    private fun numericValue()=numberId?.removePrefix("number:")?.toIntOrNull()
    fun selectSource(id:String):Boolean {
        if(!(isShape||isSpatial||isActions||isSplit&&!combine)||id !in (sourceCards+spatialSteps.map{it.source}+if(isSplit)instances else emptyList()).map{it.id})return false
        pending=if(pending==id)null else id;return true
    }
    fun selectCup():Boolean{if(!isCapacity)return false;cupSelected=!cupSelected;return true}
    fun discardTransient(){pending=null;cupSelected=false}
    fun drop(target:String,verb:String=""):Boolean {val src=pending?:return false;return record(if(isSplit&&!combine)listOf("move",src,target)else listOf("place",src,target,verb)).also{if(it)pending=null}}
    fun move(id:String,zone:String="destination")=record(listOf("move",id,zone))
    fun choose(id:String)=record(listOf("choose",id))
    fun setNumber(id:String)=record(listOf("number",id))
    fun commitRound()=record(listOf("round"))
    fun commitStage()=record(listOf("stage"))
    fun setHour(n:Int)=record(listOf("hour",n.toString()))
    fun setMinute(n:Int)=record(listOf("minute",n.toString()))
    fun touch(x:Double,y:Double)=record(listOf("touch",x.toString(),y.toString()))
    fun addUnit(id:String)=record(listOf("unit",id))
    fun pour(id:String):Boolean {if(!cupSelected)return false;return record(listOf("pour",id)).also{if(it)cupSelected=false}}
    fun chainTap(id:String)=record(listOf("chain",id))
    private fun splitRound()=listOf(instances.map{it.id}.filter{placed[it]=="left"},instances.map{it.id}.filter{placed[it]=="right"})
    private fun applyEvent(e:List<String>):Boolean=runCatching {
        when(e.firstOrNull()){
            "pigment"->{require(e.size==2&&isMix&&!viewed&&mixInputs.any{it.id==e[1]});if(!mixSelected.add(e[1]))mixSelected.remove(e[1])}
            "view"->{require(e.size==1&&!viewed&&(isSimulation||isMix&&mixSelected.size==mixInputs.size));viewed=true}
            "predict"->{require(e.size==2&&(isSimulation&&!viewed||isMix&&viewed)&&predictionCards.any{it.id==e[1]});prediction=e[1]}
            "career"->{require(e.size==2&&isCareer&&careerCards.any{it.id==e[1]});career=e[1];chosenTools.clear()}
            "tool"->{require(e.size==2&&isCareer&&career!=null&&toolCards.any{it.id==e[1]});if(e[1] in chosenTools)chosenTools.remove(e[1])else{require(chosenTools.size<2);chosenTools.add(e[1])}}
            "move"->{require(e.size==3);val id=e[1];val zone=e[2];require(validInstance(id));when{
                isTransfer->{require(zone=="destination"&&transferAvailable(id));if(!moved.add(id))moved.remove(id)}
                isSplit&&combine->{require(zone=="destination");if(!moved.add(id))moved.remove(id)}
                isSplit&&!combine->{require(zone in setOf("left","right","source")&&rounds.size<2);if(zone=="source")placed.remove(id)else placed[id]=zone}
                else->error("wrong mechanic")}}
            "choose"->{require(e.size==2&&isPool&&validInstance(e[1])&&e[1] !in stages.flatten()&&stages.size<2);if(!pool.add(e[1]))pool.remove(e[1])}
            "number"->{require(e.size==2&&needsNumber&&numbers.any{it.id==e[1]});numberId=e[1]}
            "round"->{require(e.size==1&&twoRounds&&rounds.isEmpty()&&placed.size==instances.size);rounds.add(splitRound());placed.clear()}
            "stage"->{require(e.size==1&&twoStages&&stages.isEmpty()&&pool.isNotEmpty());stages.add(pool.toList());pool.clear()}
            "hour"->{require(e.size==2&&isClock);hour=e[1].toInt().also{require(it in 1..12)};clockTouched=true}
            "minute"->{require(e.size==2&&isClock);minute=e[1].toInt().also{require(it in 0..55&&it%5==0)};clockTouched=true}
            "touch"->{require(e.size==3&&isHotspot&&geometry!=null);val x=e[1].toDouble();val y=e[2].toDouble();require(x.isFinite()&&y.isFinite()&&x in 0.0..1.0&&y in 0.0..1.0);point=x to y}
            "unit"->{require(e.size==2&&isLength&&lengthUnits.any{it.id==e[1]}&&units.none{it.id==e[1]});units.add(LengthUnit(e[1],(units.size*measurement.n("unit_length")).toDouble()))}
            "pour"->{require(e.size==2&&isCapacity);val v=vessels.single{it.id==e[1]};require(v.initial+pours.count{it.containerId==v.id}<v.capacity);pours.add(Pour(requireNotNull(cup).id,v.id))}
            "place"->{require(e.size==4);val src=e[1];val dst=e[2];when{
                isShape->{require(src in sourceCards.map{it.id}&&dst in targetCards.map{it.id}&&e[3].isEmpty());require(placed.none{it.key!=src&&it.value==dst});placed[src]=dst}
                isSpatial->{require(e[3].isEmpty());val s=spatialSteps.single{it.source.id==src};require(dst in s.zones.map{it.id});placed[src]=dst}
                isActions->{require(src!=dst&&sourceCards.any{it.id==src}&&sourceCards.any{it.id==dst}&&e[3] in setOf("place_inside","place_on")&&actions.size<2);actions.add(SceneAction(src,dst,e[3]))}
                else->error("wrong mechanic")}}
            "chain"->{require(e.size==2&&isChains&&chains.size<chainColumns.first().size);val id=e[1];require(chainColumns[chain.size].any{it.id==id}&&id !in chains.flatten());chain.add(id);if(chain.size==chainColumns.size){chains.add(chain.toList());chain.clear()}}
            else->error("unknown event")
        };true
    }.getOrDefault(false)
    private fun record(e:List<String>):Boolean{if(!supported||events.size>=256)return false;if(!applyEvent(e))return false;events.add(e.toList());return true}
    private fun reset(){viewed=false;prediction=null;career=null;chosenTools.clear();mixSelected.clear();moved.clear();placed.clear();rounds.clear();pool.clear();stages.clear();units.clear();pours.clear();actions.clear();chains.clear();chain.clear();numberId=null;hour=if(isClock)clock.n("initial_hour")else 12;minute=if(isClock)clock.n("initial_minute")else 0;clockTouched=false;point=null;discardTransient()}
    fun clear():Boolean{reset();events.clear();return true}
    fun undo():Boolean{if(pending!=null||cupSelected){discardTransient();return true};if(events.isEmpty())return false;val rest=events.dropLast(1);reset();events.clear();rest.forEach{check(record(it))};return true}
    fun saveFields():Obj=mapOf("valid_committed_response" to mapOf("schema" to "htq.interaction.events.v1","events" to events.map{it.toList()}))
    fun restoreFields(state:Obj):Boolean=runCatching{
        val raw=state["valid_committed_response"]?:return@runCatching clear()
        val o=raw.obj();require(o.s("schema")=="htq.interaction.events.v1");val es=o.a("events");require(es.size<=256)
        val copy=AdvancedInteraction(catalog,task,type);for(e in es)require(copy.record(e.arr().map{it.str()}))
        clear();copy.events.forEach{check(record(it))};true
    }.getOrDefault(false)
    fun response():Response? {if(!supported||needsNumber&&numberId==null)return null;return when{
        isMix->if(viewed)prediction?.let{Response.Single(it)}else null
        isSimulation||isCareer->Response.ParticipationDone
        isTransfer->if(moved.isNotEmpty())Response.Transfer(moved.toList(),"destination",numberId)else null
        isSplit&&combine->if(moved.isNotEmpty())Response.Combine(moved.toList(),numericValue()?:return null)else null
        isSplit->if(placed.size==instances.size&&(!twoRounds||rounds.size==1))Response.Split(rounds+listOf(splitRound()),numericValue())else null
        isPool&&twoStages->if(stages.size==1&&pool.isNotEmpty())Response.QuantitySteps(stages+listOf(pool.toList()))else null
        isPool->if(pool.isNotEmpty())Response.Selection(pool.toList())else null
        isLength->if(units.isNotEmpty())Response.Length(units.toList(),requireNotNull(numberId))else null
        isCapacity->if(pours.isNotEmpty())Response.Capacity(pours.toList(),requireNotNull(numberId))else null
        isClock->if(clockTouched)Response.Clock(hour,minute)else null
        isHotspot->point?.let{Response.Hotspot(requireNotNull(geometry),it.first,it.second)}
        isShape->if(placed.size==sourceCards.size)Response.Pairs(placed.entries.map{it.key to it.value})else null
        isSpatial->if(placed.size==spatialSteps.size)Response.Spatial(placed.entries.map{it.key to it.value})else null
        isActions->if(actions.size==2)Response.Actions(actions.toList())else null
        isChains->if(chains.size==chainColumns.first().size)Response.Chains(chains.toList())else null
        else->null
    }}
}
