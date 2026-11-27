package vn.htq.core.ui

import vn.htq.core.*

/** Only presentation fields from the locked source are exposed; no answer table. */
data class UiCard(val id: String, val title: String, val image: String)
data class UiArea(val card: UiCard, val topics: List<String>, val pages: List<List<String>>)
data class UiTopic(val card: UiCard, val background: String, val lessons: List<String>)
data class UiIntro(val image: String, val audio: String?)
data class UiLesson(val card: UiCard, val topic: String, val levels: List<String>, val intro: List<UiIntro>, val voice: List<String>, val stories: List<String>)
data class UiLevel(val id: String, val lesson: String, val title: String, val tasks: List<String>)
data class UiLetter(val id: String, val upper: String, val lower: String, val word: String, val images: List<String>, val audio: String)
data class UiNumber(val number: Int, val label: String, val audio: String)
data class UiEnglish(val id: String, val lesson: String, val text: String, val images: List<String>, val audio: String, val optional: Boolean, val meaning: String?)
data class UiStoryPage(val image: String, val text: String, val audio: String)
data class UiStory(val id: String, val title: String, val lesson: String, val language: String, val screen: String, val scenes: List<UiStoryPage>)
sealed class UiElement {
    data class Image(val path: String): UiElement()
    data class Glyph(val text: String): UiElement()
    data class Clock(val face: String, val hour: Int): UiElement()
    data class Crop(val path:String, val rect:List<Float>,val mirror:Boolean):UiElement()
    data class Count(val path:String,val count:Int):UiElement()
    data class Numbered(val path:String,val text:String):UiElement()
    data class Board(val path:String,val shapes:List<UiOutline>):UiElement()
    data class PolygonCrop(val path:String,val rect:List<Float>,val points:List<List<Float>>,val mirror:Boolean):UiElement()
    data class Scaled(val path:String,val rect:List<Float>,val scale:Float,val axis:String,val alignment:String):UiElement()
    data class LabelCard(val text:String,val style:String):UiElement()
    data class Annulus(val center:List<Float>,val outer:Float,val inner:Float,val region:String,val outerColor:String,val innerColor:String,val stroke:Int):UiElement()
    data class ClockBlank(val face:String):UiElement()
    data class Spatial(val path:String,val center:List<Float>,val size:List<Float>,val zones:List<UiZone>):UiElement()
    data class MeasureLength(val path:String,val rect:List<Float>,val unitPath:String,val unitRect:List<Float>,val length:Int,val unitCount:Int):UiElement()
    data class Vessel(val path:String,val label:String,val fill:Float):UiElement()
    object Speaker: UiElement()
    object Blank: UiElement()
}
data class UiZone(val center:List<Float>,val size:List<Float>)
data class UiOutline(val shape:String,val rect:List<Float>,val points:List<List<Float>>)
/** Same fit-center transform used to draw a source canvas and invert pointer coordinates. */
data class FitCenter(val left:Float,val top:Float,val width:Float,val height:Float) {
    fun x(n:Float)=left+n*width
    fun y(n:Float)=top+n*height
    fun inverse(x:Float,y:Float):Pair<Float,Float>? {
        val a=(x-left)/width;val b=(y-top)/height
        return if(a in 0f..1f && b in 0f..1f) a to b else null
    }
    companion object {fun of(viewWidth:Float,viewHeight:Float,imageWidth:Float,imageHeight:Float):FitCenter {
        require(listOf(viewWidth,viewHeight,imageWidth,imageHeight).all{it.isFinite() && it>0f})
        val scale=minOf(viewWidth/imageWidth,viewHeight/imageHeight);val w=imageWidth*scale;val h=imageHeight*scale
        return FitCenter((viewWidth-w)/2,(viewHeight-h)/2,w,h)
    }}
}
/** Image zoom geometry is independent from any assessment/hotspot answer. */
data class ZoomWindow(val left:Float,val top:Float,val width:Float,val height:Float) {
    companion object {
        fun of(vw:Float,vh:Float,iw:Float,ih:Float,zoom:Float,panX:Float,panY:Float):ZoomWindow {
            require(zoom.isFinite() && zoom in 1f..3f && panX.isFinite() && panY.isFinite())
            val fit=FitCenter.of(vw,vh,iw,ih);val w=fit.width*zoom;val h=fit.height*zoom
            val dx=((w-vw)/2).coerceAtLeast(0f);val dy=((h-vh)/2).coerceAtLeast(0f)
            return ZoomWindow((vw-w)/2+panX.coerceIn(-dx,dx),(vh-h)/2+panY.coerceIn(-dy,dy),w,h)
        }
    }
}
data class UiGroup(val role: String, val columns: Int, val items: List<UiElement>, val shuffle: Boolean,
                   val pageSizes: List<Int> = emptyList(), val objectIds: List<String> = emptyList(),
                   val pager: String? = null, val advanceRequiresCommit: Boolean = false,
                   val wordBreaks: List<Int> = emptyList()) {
    init {
        require(items.isNotEmpty())
        require(pageSizes.isEmpty() || (pageSizes.all { it in 1..6 } && pageSizes.sum()==items.size))
        require(objectIds.isEmpty() || (objectIds.size==items.size && objectIds.toSet().size==items.size))
        require(pageSizes.isEmpty() || objectIds.isNotEmpty())
        require(pager==null || (pager=="paired" && pageSizes.isNotEmpty()))
        require(!advanceRequiresCommit || pageSizes.isNotEmpty())
        require(wordBreaks.isEmpty() || (role=="word" && wordBreaks==wordBreaks.distinct().sorted() && wordBreaks.all{it in 1 until items.size}))
        if(role=="word")require(!shuffle && items.all{it is UiElement.Glyph} && objectIds.size==items.size)
    }
    /** Fixed source pages are partitioned before any within-page shuffle. */
    fun pageIndices(viewport:UiViewport):List<List<Int>> {
        if(pageSizes.isNotEmpty()){var at=0;return pageSizes.map{n->(at until at+n).toList().also{at+=n}}}
        val maxItems=when(role){"pattern","context","countables","count_groups","reserve","sequence_slots","sources","destinations","bins","memory","word"->items.size;else->viewport.pageSize(6)}
        return items.indices.toList().chunked(maxItems)
    }
}
data class UiTask(val id: String, val lesson: String, val level: String, val screen: String, val prompt: String, val background: String?, val ready: Boolean, val listen: Boolean, val groups: List<UiGroup>, val choiceSlots: List<String>,val prelude:Boolean=false,val preludeImage:String?=null,val afterPrelude:List<UiGroup> = emptyList())
class UiContent private constructor(
    val screens: Map<String,List<String>>, val screenNames: Map<String,String>, val icons: Map<String,String>,
    val areas: List<UiArea>, val topics: Map<String,UiTopic>, val lessons: Map<String,UiLesson>, val levels: Map<String,UiLevel>,
    val letters: List<UiLetter>, val numbers: List<UiNumber>, val englishGroups: List<UiCard>,
    val vocabulary: List<UiEnglish>, val phrases: List<UiEnglish>, val stories: Map<String,UiStory>,
    val tasks: Map<String,UiTask>, val rewardArt: Map<String,String>, val welcomeAudio: String?
) {
    fun screenArt(id: String) = screens.getValue(id)
    fun lessonForTask(id: String) = lessons.getValue(tasks.getValue(id).lesson)
    companion object {
        fun parse(json: String, allowedPaths: Set<String>): UiContent {
            val root = Json.parse(json).obj()
            require(root.s("version") == ApprovedUi.contentVersion)
            fun path(v: String, audio: Boolean = false): String {
                require(v.matches(Regex("[a-z0-9_./-]+")) && v.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
                require(v in allowedPaths)
                require(if (audio) v.endsWith(".ogg") && v.startsWith("audio/") else v.endsWith(".webp") || v.endsWith(".png"))
                return v
            }
            fun audio(v: String) = path(v, true)
            fun fraction(v:Any?):Float=(v as Number).toFloat().also{require(it.isFinite()&&it in 0f..1f)}
            fun coords(v:Any?,n:Int):List<Float> = v.arr().map(::fraction).also{require(it.size==n)}
            fun rect(v:Any?)=coords(v,4).also{require(it[0]<it[2]&&it[1]<it[3])}
            fun hex(v:String)=v.also{require(it.matches(Regex("#[0-9a-fA-F]{6}")))}
            fun images(o: Obj,k: String) = o.strings(k).map { path(it) }
            fun card(o: Obj) = UiCard(o.s("id"),o.s("title"),path(o.s("image")))
            fun rows(k: String) = root.a(k).map { it.obj() }
            fun <T> unique(items: List<T>, key: (T)->String): Map<String,T> = items.associateBy(key).also { require(it.size == items.size) }
            val screensRaw=root.o("screens").mapValues{it.value.obj()}
            require(screensRaw.keys == ApprovedUi.screens.map{it.id}.toSet())
            val screens=screensRaw.mapValues{images(it.value,"fixed")}
            val names=screensRaw.mapValues{it.value.s("name")}
            val icons=root.o("icons").mapValues{path(it.value.str())}
            val areas=rows("areas").map { o -> UiArea(card(o),o.strings("topics"),o.a("pages").map{it.arr().map{v->v.str()}}) }
            val topics=unique(rows("topics").map { o -> UiTopic(card(o),path(o.s("background")),o.strings("lessons")) }){it.card.id}
            val lessons=unique(rows("lessons").map { o -> UiLesson(card(o),o.s("topic"),o.strings("levels"),o.a("intro").map { v -> val a=v.obj();UiIntro(path(a.s("image")),a.optional("audio")?.let(::audio)) },o.strings("voice").map(::audio),o.strings("stories")) }){it.card.id}
            val levels=unique(rows("levels").map{UiLevel(it.s("id"),it.s("lesson"),it.s("title"),it.strings("tasks"))}){it.id}
            val letters=rows("letters").map{UiLetter(it.s("id"),it.s("upper"),it.s("lower"),it.s("word"),images(it,"images"),audio(it.s("audio")))}
            val numbers=rows("numbers").map{UiNumber(it.n("number"),it.s("label"),audio(it.s("audio")))}
            val en=rows("englishGroups").map(::card)
            val vocabulary=rows("vocab").map{UiEnglish(it.s("id"),it.s("lesson"),it.s("word"),images(it,"images"),audio(it.s("audio")),it.b("optional"),null)}
            val phrases=rows("phrases").map{UiEnglish(it.s("id"),it.s("lesson"),it.s("text"),images(it,"images"),audio(it.s("audio")),false,it.s("meaning"))}
            val stories=unique(rows("stories").map{o->UiStory(o.s("id"),o.s("title"),o.s("lesson"),o.s("language"),o.s("screen"),o.a("scenes").map{v->val s=v.obj();UiStoryPage(path(s.s("image")),s.s("text"),audio(s.s("audio")))})}){it.id}
            val tasks=unique(rows("tasks").map { o ->
                val scr=ApprovedUi.screen(o.s("screen"));require(scr.taskCount>0)
                fun parseGroups(values:Arr)=values.map { v -> val g=v.obj();UiGroup(g.s("role"),g.n("columns").also{require(it in 1..6)},g.a("items").map { e -> val item=e.obj();when(item.s("kind")) {
                    "image"->UiElement.Image(path(item.s("path")))
                    "glyph"->UiElement.Glyph(item.s("text").also{require(it.length in 1..8)})
                    "clock"->UiElement.Clock(path(item.s("path")),item.n("hour").also{require(it in 1..12)})
                    "crop"->UiElement.Crop(path(item.s("path")),item.a("rect").map{(it as Number).toFloat()}.also{require(it.size==4 && it.all{n->n.isFinite() && n in 0f..1f} && it[0]<it[2] && it[1]<it[3])},item.b("mirror"))
                    "count"->UiElement.Count(path(item.s("path")),item.n("count").also{require(it in 0..10)})
                    "numbered"->UiElement.Numbered(path(item.s("path")),item.s("text").also{require(it.matches(Regex("[0-9]{1,2}")))})
                    "board"->UiElement.Board(path(item.s("path")),item.a("shapes").map{q->val a=q.obj();val shape=a.s("shape").also{require(it in setOf("rect","ellipse","polygon"))};val rect=a.a("rect").map{(it as Number).toFloat()};require(rect.size==4&&rect.all{it.isFinite()&&it in 0f..1f}&&rect[0]<rect[2]&&rect[1]<rect[3]);val pts=a.a("points").map{v->v.arr().map{(it as Number).toFloat()}.also{require(it.size==2&&it.all{n->n.isFinite()&&n in 0f..1f})}};require(if(shape=="polygon")pts.size>=3 else pts.isEmpty());UiOutline(shape,rect,pts)}.also{require(it.isNotEmpty())})
                    "polygonCrop"->UiElement.PolygonCrop(path(item.s("path")),rect(item["rect"]),item.a("points").map{coords(it,2)}.also{require(it.size>=3)},item.b("mirror"))
                    "scaled"->UiElement.Scaled(path(item.s("path")),rect(item["rect"]),fraction(item["scale"]).also{require(it>0f)},item.s("axis").also{require(it in setOf("height_and_width_uniform","length_only_horizontal"))},item.s("alignment").also{require(it in setOf("bottom","center_line"))})
                    "labelCard"->UiElement.LabelCard(item.s("text").also{require(it.length in 1..80)},item.s("style").also{require(it in setOf("calendar","page","listen"))})
                    "annulus"->UiElement.Annulus(coords(item["center"],2),fraction(item["outer"]),fraction(item["inner"]),item.s("region").also{require(it in setOf("inner","outer"))},hex(item.s("outerColor")),hex(item.s("innerColor")),item.n("stroke").also{require(it in 1..8)}).also{require(it.inner<it.outer)}
                    "clockBlank"->UiElement.ClockBlank(path(item.s("path")))
                    "spatial"->UiElement.Spatial(path(item.s("path")),coords(item["center"],2),coords(item["size"],2),item.a("zones").map{q->val a=q.obj();UiZone(coords(a["center"],2),coords(a["size"],2))}.also{require(it.isNotEmpty())})
                    "measureLength"->UiElement.MeasureLength(path(item.s("path")),rect(item["rect"]),path(item.s("unitPath")),rect(item["unitRect"]),item.n("length").also{require(it in 1..10)},item.n("unitCount").also{require(it in 1..12)})
                    "vessel"->UiElement.Vessel(path(item.s("path")),item.s("label").also{require(it in setOf("A","B"))},fraction(item["fill"]))
                    "speaker"->UiElement.Speaker
                    "blank"->UiElement.Blank
                    else->error("Unsupported display element")
                } }.also{require(it.isNotEmpty())},g.b("shuffle"),
                    g["pageSizes"]?.arr()?.map{n->(n as Number).toInt().also{require(n.toDouble()==it.toDouble())}} ?: emptyList(),
                    g["objectIds"]?.arr()?.map{it.str()} ?: emptyList(),g.optional("pager"),g.b("advanceRequiresCommit"),
                    g["wordBreaks"]?.arr()?.map{n->(n as Number).toInt().also{require(n.toDouble()==it.toDouble())}} ?: emptyList()) }
                val groups=parseGroups(o.a("groups"))
                val after=parseGroups(o["afterPrelude"]?.arr() ?: emptyList())
                for (set in listOf(groups,after)) {
                    set.forEach{g->require(g.objectIds.all{it.startsWith(o.s("id")+":") || it in o.strings("choiceSlots")})}
                    val paired=set.filter{it.pager=="paired"}
                    if(paired.isNotEmpty())require(paired.size==2 && paired.map{it.role}.toSet()==setOf("sources","destinations") && paired[0].pageSizes==paired[1].pageSizes)
                }
                require(o.b("ready")==groups.isNotEmpty());require(!o.b("prelude")||!o.b("ready"))
                require(o.b("prelude")==after.isNotEmpty())
                UiTask(o.s("id"),o.s("lesson"),o.s("level"),scr.id,o.s("prompt"),o.optional("background")?.let{path(it)},o.b("ready"),o.b("listen"),groups,o.strings("choiceSlots"),o.b("prelude"),o.optional("preludeImage")?.let{path(it)},after)
            }){it.id}
            require(areas.size==6 && topics.size==17 && lessons.size==110 && levels.size==330 && tasks.size==660)
            require(areas.flatMap{it.topics}.sorted()==topics.keys.sorted())
            areas.forEach{require(it.pages.flatten()==it.topics && it.pages.all{p->p.size in 1..5})}
            require(topics.values.flatMap{it.lessons}.sorted()==lessons.keys.sorted())
            require(lessons.values.flatMap{it.levels}.sorted()==levels.keys.sorted())
            require(levels.values.flatMap{it.tasks}.sorted()==tasks.keys.sorted())
            lessons.values.forEach{l->require(l.topic in topics && l.levels.all{levels.getValue(it).lesson==l.card.id} && l.stories.all{it in stories})}
            tasks.values.forEach{t->require(t.lesson in lessons && levels.getValue(t.level).lesson==t.lesson && t.id in levels.getValue(t.level).tasks)}
            require(tasks.values.flatMap{it.choiceSlots}.let{it.size==816 && it.toSet().size==816})
            require(tasks.values.groupingBy{it.screen}.eachCount()==ApprovedUi.screens.filter{it.taskCount>0}.associate{it.id to it.taskCount})
            require(letters.size==29 && numbers.map{it.number}==(0..10).toList() && en.size==10 && vocabulary.size==90 && phrases.size==20 && stories.size==10)
            require(vocabulary.count{!it.optional}==60 && vocabulary.count{it.optional}==30)
            require((vocabulary+phrases).all{it.lesson in lessons && it.images.isNotEmpty()})
            return UiContent(screens,names,icons,areas,topics,lessons,levels,letters,numbers,en,vocabulary,phrases,stories,tasks,root.o("rewardArt").mapValues{path(it.value.str())},root.optional("welcomeAudio")?.let(::audio))
        }
    }
}

/** DP-only planning; Android measurement remains a separate device acceptance gate. */
data class UiViewport(val width: Int, val height: Int, val fontScale: Float) {
    init {require(width>=200 && height>=200 && fontScale>=1f)}
    val margin: Int get()=if(width<360) ApprovedUi.compactMarginDp else ApprovedUi.marginDp
    val available: Int get()=width-2*margin
    val wide: Boolean get()=width>=600 && width>height
    val childTarget: Int get()=ApprovedUi.preferredDp
    fun columns(wanted: Int,minCard: Int=100): Int = wanted.coerceAtLeast(1).coerceAtMost(((available+ApprovedUi.gapDp)/(minCard+ApprovedUi.gapDp)).coerceAtLeast(1))
    fun pageSize(wanted: Int): Int=when { fontScale>=1.25f && width<400 -> minOf(wanted,4); wide -> minOf(wanted,6); else -> minOf(wanted,6) }.coerceAtLeast(1)
    fun cardWidth(columns: Int): Int=(available-(columns-1)*ApprovedUi.gapDp)/columns
}
