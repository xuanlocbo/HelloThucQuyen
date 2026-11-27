package vn.htq.core

import vn.htq.core.ui.*

/** A verified interaction projection. The renderer gets source IDs, not evaluation criteria. */
class TaskInteraction(private val catalog:Catalog, val task:UiTask){
    private val scene=catalog.tasks.getValue(task.id).o("scene")
    private val objects=scene.a("objects").map{it.obj()}.associateBy{it.s("object_id")}
    private val engine=AssessmentEngine(catalog)
    private val groups=if(task.prelude)task.afterPrelude else task.groups
    val type=engine.responseType(task.id)
    private val selectableRoles=setOf("choices","word","numbers","listen_options","sequence_pieces","direction_endpoints","story_cards")
    private val selectable=groups.filter{it.role in selectableRoles}.flatMap{it.objectIds}.toSet()
    private val sources=groups.filter{it.role=="sources"}.flatMap{it.objectIds}.toSet()
    private val destinations=groups.filter{it.role=="destinations"}.flatMap{it.objectIds}.toSet()
    private val memory=groups.filter{it.role=="memory"}.flatMap{it.objectIds}.toSet()
    private val pairTypes=setOf("pair_map","one_per_destination","place_setting_counts")
    private val sourcePairs=type in pairTypes || type=="participation"&&task.screen=="SCR-041"
    private val reusable=objects.filterValues{it.s("role")=="reusable_pattern_tile"}.keys
    private val slots=groups.filter{it.role in setOf("sequence_slots","pattern","direction_endpoints")}.sumOf{g->g.items.count{it==UiElement.Blank}}
    private val neutralOpen=type=="participation" && (task.screen in setOf("SCR-065","SCR-069") || task.screen=="SCR-066" && scene.o("native_binding").optional("scene_type")=="participation_story" || task.screen=="SCR-041") && task.id!="12.05-L3-Q02"
    private val IDsValid=(selectable+sources+destinations+memory).all{it in objects}
    val supported=IDsValid && when(type){
        "single_object","set_equals" -> selectable.isNotEmpty()
        "ordered_sequence" -> selectable.isNotEmpty() && slots>0
        in pairTypes -> sources.isNotEmpty()&&destinations.isNotEmpty()
        "memory_pairs" -> memory.size in setOf(4,6,8)
        "participation" -> neutralOpen && (task.screen=="SCR-069"||selectable.isNotEmpty()||sources.isNotEmpty()&&destinations.isNotEmpty())
        else -> false
    }
    private val selected=mutableListOf<String>()
    private val assignments=linkedMapOf<String,String>()
    private val matched=mutableListOf<Pair<String,String>>()
    private val flipped=mutableListOf<String>()
    var pendingSource:String?=null;private set
    var attempted=false;private set
    var verdict:Verdict?=null;private set
    val finished get()=verdict is Verdict.Completed
    val isPairing get()=sourcePairs
    val isMemory get()=type=="memory_pairs"
    val isSequence get()=type=="ordered_sequence"
    val selectedIds:List<String> get()=selected.toList()
    val pairAssignments:Map<String,String> get()=assignments.toMap()
    val matchedPairs:List<Pair<String,String>> get()=matched.toList()
    val faceUpIds:Set<String> get()=(flipped+matched.flatMap{listOf(it.first,it.second)}).toSet()
    fun isSelected(id:String)=id in selected||id==pendingSource||id in faceUpIds
    fun canTap(role:String,id:String):Boolean=supported&&!finished&&when{
        isMemory -> role=="memory"&&id in memory
        isPairing -> role=="sources"&&id in sources||role=="destinations"&&id in destinations
        else -> role in selectableRoles&&id in selectable
    }
    fun tap(role:String,id:String):Boolean{
        if(!canTap(role,id))return false
        if(verdict==Verdict.Retry)verdict=null
        when{
            isMemory -> {
                if(id in faceUpIds||flipped.size==2)return false
                flipped.add(id)
                if(flipped.size==2){
                    // Checking a pair is permitted only after the child explicitly flips both cards.
                    val a=flipped[0];val b=flipped[1]
                    if(engine.memoryPairMatches(task.id,a,b)){matched.add(a to b);flipped.clear()}
                }
            }
            isPairing -> if(role=="sources")pendingSource=if(pendingSource==id)null else id else{
                val source=pendingSource?:return false
                assignments[source]=id;pendingSource=null
            }
            isSequence -> {
                if(id !in reusable&&id in selected)return false
                if(selected.size>=slots)return false
                selected.add(id)
            }
            type=="single_object" -> {selected.clear();selected.add(id)}
            else -> if(id in selected)selected.remove(id)else selected.add(id)
        }
        return true
    }
    fun discardTransient(){pendingSource=null;flipped.clear()}
    fun discardVerdict(){verdict=null}
    fun closeFlipped():Boolean{if(!isMemory||finished||flipped.isEmpty())return false;flipped.clear();return true}
    fun undo():Boolean{
        if(finished||!supported)return false
        verdict=null
        if(isMemory)return closeFlipped()
        if(isPairing){if(pendingSource!=null){pendingSource=null;return true};val key=assignments.keys.lastOrNull()?:return false;assignments.remove(key);return true}
        if(selected.isEmpty())return false;selected.removeAt(selected.lastIndex);return true
    }
    fun clear():Boolean{if(finished||!supported)return false;selected.clear();assignments.clear();matched.clear();flipped.clear();pendingSource=null;verdict=null;return true}
    fun number(id:String):Int?=when{
        isPairing -> assignments.keys.indexOf(id).takeIf{it>=0}?.plus(1)
        isSequence -> selected.indexOf(id).takeIf{it>=0}?.plus(1)
        else -> null
    }
    fun linkedNumbers(id:String):List<Int> = assignments.entries.mapIndexedNotNull{i,e->if(e.value==id)i+1 else null}
    fun memoryFace(id:String):UiElement?{
        if(!isMemory||id !in faceUpIds)return null
        val o=objects.getValue(id)
        return o.optional("glyph")?.let{UiElement.Glyph(it)}?:o.optional("art_id")?.let{UiElement.Image(catalog.assetPath(it,"image"))}
    }
    /** The option itself is projected from the source, never reconstructed from an answer. */
    fun chosenVisual(id:String):UiElement?=groups.firstOrNull{it.role in selectableRoles&&id in it.objectIds}?.let{it.items[it.objectIds.indexOf(id)]}
    fun pageCommitted(ids:List<String>):Boolean = ids.isNotEmpty() && when{
        isSequence -> ids.all{it in selected}
        isPairing -> ids.all{it in assignments}
        isMemory -> ids.all{it in faceUpIds}&&flipped.isEmpty()
        else -> false
    }
    fun response():Response? {
        if(!supported||finished)return null
        return when(type){
            "single_object" -> selected.singleOrNull()?.let{Response.Single(it)}
            "set_equals" -> selected.takeIf{it.isNotEmpty()}?.let{Response.Selection(it.toList())}
            "ordered_sequence" -> selected.takeIf{it.isNotEmpty()}?.let{Response.Sequence(it.toList())}
            in pairTypes -> assignments.takeIf{it.isNotEmpty()}?.let{Response.Pairs(it.entries.map{e->e.key to e.value})}
            "memory_pairs" -> if(matched.size*2==memory.size)Response.Pairs(matched.toList())else null
            "participation" -> Response.ParticipationDone
            else -> null
        }
    }
    fun submit(receipt:ListeningReceipt?):Verdict{
        val r=response()?:return Verdict.NotSubmitted
        val result=engine.evaluate(task.id,r,true,receipt)
        if(result!=Verdict.NotSubmitted){attempted=true;verdict=result}
        return result
    }
    /** Persist committed responses, never playback proof, a ticket, or a half-flipped pair. */
    fun saveFields():Obj=buildMap {
        if(isSequence)put("sequence_ids",selected.toList()) else if(!isPairing&&!isMemory)put("selected_object_ids",selected.toList())
        if(isPairing)put("paired_ids",assignments.entries.map{listOf(it.key,it.value)})
        if(isMemory)put("memory_matched_pairs",matched.map{listOf(it.first,it.second)})
    }
    fun restoreFields(snapshot:Obj):Boolean=runCatching{
        val ids=if(isSequence)snapshot["sequence_ids"] else snapshot["selected_object_ids"]
        val ss=ids?.arr()?.map{it.str()}.orEmpty()
        require(ss.size<=maxOf(selectable.size,slots)&&ss.all{it in selectable}&&ss.filter{it !in reusable}.distinct().size==ss.count{it !in reusable})
        if(type=="single_object")require(ss.size<=1)
        if(isSequence)require(ss.size<=slots)
        fun pairs(k:String)=snapshot[k]?.arr()?.map{it.arr().let{p->require(p.size==2);p[0].str() to p[1].str()}}.orEmpty()
        val ps=if(isPairing)pairs("paired_ids")else emptyList()
        require(ps.map{it.first}.distinct().size==ps.size&&ps.all{it.first in sources&&it.second in destinations})
        val ms=if(isMemory)pairs("memory_matched_pairs")else emptyList()
        val all=ms.flatMap{listOf(it.first,it.second)}
        require(all.distinct().size==all.size&&all.all{it in memory}&&ms.all{engine.memoryPairMatches(task.id,it.first,it.second)})
        selected.clear();selected.addAll(ss);assignments.clear();ps.forEach{assignments[it.first]=it.second};matched.clear();matched.addAll(ms)
        pendingSource=null;flipped.clear();verdict=null;attempted=false;true
    }.getOrDefault(false)
}
