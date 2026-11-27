package vn.htq.core.ui
import java.io.File
import java.util.zip.ZipFile
import vn.htq.core.*

fun main(args: Array<String>) {
    val p="app/src/main/assets/htq/"
    ZipFile(args[1]).use { z ->
        val paths=z.entries().asSequence().map{it.name}.filter{it.startsWith(p)}.map{it.removePrefix(p)}.toSet()
        val raw=File(args[0]).readText()
        val c=UiContent.parse(raw,paths)
        val original=Json.parse(z.getInputStream(z.getEntry(p+"engine/v52/task_bindings.json")).bufferedReader().use{it.readText()}).arr().map{it.obj()}
        original.forEach{t->val a=c.tasks.getValue(t.s("task_id"));check(a.prompt==t.s("child_prompt") && a.lesson==t.s("lesson_id") && a.level==t.s("level_id") && a.screen==t.s("screen_id") && a.choiceSlots==t.strings("choice_slot_ids")); val gated=t.o("audio").a("story_before_question").isNotEmpty();check(a.prelude==gated);if(!a.ready)check(a.groups.isEmpty());check(a.afterPrelude.isNotEmpty()==gated)}
        check(c.tasks.size==660)
        val sampleLetters=mapOf("05.06-L1-Q01" to "M","05.06-L1-Q02" to "H","05.06-L2-Q01" to "Ô","05.06-L3-Q01" to "H")
        for(t in original.filter{it.o("scene")["word_lines"]!=null}){
            val id=t.s("task_id");val scene=t.o("scene");val groups=c.tasks.getValue(id).groups
            val sample=sampleLetters[id]
            check(groups.filter{it.role=="sample_glyph"}.flatMap{it.items}==(sample?.let{listOf<UiElement>(UiElement.Glyph(it))}?:emptyList<UiElement>()))
            val lines=scene.strings("word_lines");val words=groups.filter{it.role=="word"};check(words.size==lines.size)
            val objects=scene.a("objects").map{it.obj()}
            for((n,line) in lines.withIndex()){
                val g=words[n];val source=objects.filter{it.n("word_index")==n}.sortedBy{it.n("character_index")}
                check(g.objectIds==source.map{it.s("object_id")} && !g.shuffle)
                check(g.items==line.filterNot{it.isWhitespace()}.map{UiElement.Glyph(it.toString())})
                check(g.wordBreaks==(1 until source.size).filter{source[it].n("character_index")>source[it-1].n("character_index")+1})
            }
        }
        check(c.tasks.values.count{t->t.groups.any{it.role=="sample_glyph"}}==4)
        check(c.tasks.values.flatMap{it.choiceSlots}.size==816)
        val keys=setOf("accepted_id","accepted_ids","accepted_sequences","correct_answer","evaluation_ref","context_candidates","feedback_after_response_id","hint_on_request_id","source_context_reference","back_art_id")
        fun noLeak(v:Any?) {when(v){is Map<*,*>->{check(v.keys.none{it in keys});v.values.forEach(::noLeak)};is List<*>->v.forEach(::noLeak)}}
        noLeak(Json.parse(raw))
        check(c.tasks.values.filter{it.screen=="SCR-061" && it.ready}.all{t->t.groups.flatMap{it.items}.all{it is UiElement.Image}})
        val profiles=listOf(UiViewport(360,800,1f),UiViewport(320,640,1f),UiViewport(640,360,1f),UiViewport(800,1024,1f),UiViewport(320,640,1.3f),UiViewport(640,360,1.3f))
        var checks=0
        profiles.forEach{v->
            for(n in 1..6){val cols=v.columns(n);check(cols in 1..n);check(v.cardWidth(cols)>=ApprovedUi.childMinDp);check(v.childTarget>=56);check(v.pageSize(n) in 1..n);checks++}
            c.tasks.values.forEach{t->(t.groups+t.afterPrelude).forEach{g->val cols=v.columns(g.columns);check(v.cardWidth(cols)>=56);check(g.items.chunked(v.pageSize(6)).flatten()==g.items);checks++}}
        }
        check(c.tasks.values.count{it.prelude}==39)
        check(c.tasks.values.all{it.ready || it.prelude})
        val byTask=original.associateBy{it.s("task_id")}
        var pageChecks=0
        profiles.forEach{v->c.tasks.values.forEach{t->
            val source=byTask.getValue(t.id).o("scene")
            val os=source.a("objects").map{it.obj()}.associateBy{it.s("object_id")}
            for(g in t.groups+t.afterPrelude){
                val pages=g.pageIndices(v);check(pages.flatten()==g.items.indices.toList())
                if(g.pageSizes.isNotEmpty())check(pages.map{it.size}==g.pageSizes)
                if(g.role=="memory"){
                    check(g.objectIds.toSet()==os.keys);check(g.columns==2);check(pages.all{it.size in listOf(4,6)})
                    for(page in pages){val objects=page.map{os.getValue(g.objectIds[it])};val keys=objects.groupingBy{it.optional("glyph")?.lowercase() ?: it.s("art_id")}.eachCount();check(keys.values.all{it==2});check(page.all{(g.items[it] as UiElement.Image).path=="art/img_edu_0124.webp"})}
                }
                val tray=(source["native_binding"] as? Map<*,*>)?.get("tray") as? Map<*,*>
                if(tray!=null && g.role in setOf("sequence_pieces","sources") && g.objectIds.isNotEmpty()){
                    val expected=tray["pages"].arr().map{it.arr().map{q->q.str()}}
                    check(pages.map{pg->pg.map{g.objectIds[it]}}==expected)
                }
                if(g.pageSizes.isNotEmpty())pageChecks++
            }
            val sg=t.groups.singleOrNull{it.role=="sources"&&it.pager=="paired"}
            val dg=t.groups.singleOrNull{it.role=="destinations"&&it.pager=="paired"}
            if(sg!=null){check(dg!=null);val a=sg.pageIndices(v);val b=dg.pageIndices(v);check(a.map{it.size}==b.map{it.size});check(a.all{it.size in 2..3});
                for(n in a.indices){val sa=a[n].map{os.getValue(sg.objectIds[it])};val da=b[n].map{os.getValue(dg!!.objectIds[it])};if(t.id=="03.01-L2-Q02")check(sa.map{it.n("instance_number")}==da.map{it.n("instance_number")})else { val key:(Obj)->String={x->x.optional("glyph")?.lowercase()?:x.s("art_id")};check(sa.map(key).toSet()==da.map(key).toSet()) }}
            }
        }}
        val days=c.tasks.getValue("04.07-L3-Q02");check(days.groups.single{it.role=="sequence_pieces"}.pageSizes==listOf(3,3,1));check(days.groups.single{it.role=="sequence_slots"}.pageSizes==listOf(4,3));check(days.groups.all{it.advanceRequiresCommit});check(c.tasks.getValue("04.02-L3-Q02").groups.single().pageSizes==listOf(4,4))
        println("UI_SOURCE_PAGING_PASS profiles=${profiles.size} explicit_group_checks=$pageChecks")
        var geometryChecks=0
        for(v in profiles){for((iw,ih) in listOf(800f to 600f,600f to 800f,600f to 600f)){
            val fit=FitCenter.of(v.available.toFloat(),v.height.toFloat(),iw,ih)
            check(fit.left>=-0.001f && fit.top>=-0.001f)
            for(x in listOf(0.1f,0.5f,0.9f))for(y in listOf(0.1f,0.5f,0.9f)){
                val point=requireNotNull(fit.inverse(fit.x(x),fit.y(y)))
                check(kotlin.math.abs(point.first-x)<0.0001f && kotlin.math.abs(point.second-y)<0.0001f);geometryChecks++
            }
        }}
        var zoomChecks=0
        for(v in profiles)for((iw,ih) in listOf(800f to 600f,600f to 800f,600f to 600f))for(zoom in listOf(1f,2f,3f))for(pan in listOf(-5000f,0f,5000f)){
            val vw=v.available.toFloat();val vh=v.height.toFloat();val z=ZoomWindow.of(vw,vh,iw,ih,zoom,pan,-pan)
            check(kotlin.math.abs(z.width/z.height-iw/ih)<0.0001f)
            if(z.width>vw){check(z.left<=0.001f&&z.left+z.width>=vw-0.001f)}else check(kotlin.math.abs(z.left-(vw-z.width)/2)<0.001f)
            if(z.height>vh){check(z.top<=0.001f&&z.top+z.height>=vh-0.001f)}else check(kotlin.math.abs(z.top-(vh-z.height)/2)<0.001f)
            zoomChecks++
        }
        println("UI_ZOOM_CLAMP_PASS $zoomChecks")
        println("UI_GATE_PASS prelude=39 after_listening_projection=39 initial_choices_hidden=true")
        println("UI_GEOMETRY_PASS fit_center_roundtrips=$geometryChecks (not Android pointer tests)")
        check(runCatching{UiContent.parse(raw,emptySet())}.isFailure)
        println("UI_DATA_PASS areas=${c.areas.size} topics=${c.topics.size} lessons=${c.lessons.size} levels=${c.levels.size} tasks=${c.tasks.size} ready=${c.tasks.values.count{it.ready}} deferred=${c.tasks.values.count{!it.ready}}")
        println("UI_LIBRARY_PASS letters=${c.letters.size} numbers=${c.numbers.size} english_groups=${c.englishGroups.size} vocabulary=${c.vocabulary.size} phrases=${c.phrases.size} storyboards=${c.stories.size}")
        println("UI_DP_PLANNER_PASS profiles=${profiles.size} checks=$checks (not Android measured layout)")
        println("UI_ALLOWLIST_PASS no raw evaluation/answer metadata; unknown paths rejected")
    }
}
