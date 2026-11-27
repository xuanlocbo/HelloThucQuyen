package vn.htq.core

import vn.htq.core.ui.UiTask

/** Open activities have real editable workspaces, never correctness or speech scoring. */
class CreativeInteraction internal constructor(private val catalog:Catalog,val task:UiTask){
    private val scene=catalog.tasks.getValue(task.id).o("scene")
    private val b=scene["native_binding"]?.obj().orEmpty()
    private val objs=scene.a("objects").map{it.obj()}.associateBy{it.s("object_id")}
    private fun Obj.ids(k:String)=this[k]?.arr()?.map{it.str()}.orEmpty()
    private fun Obj.floats(k:String)=this[k]?.arr()?.map{(it as Number).toFloat()}.orEmpty()
    private fun art(id:String?)=id?.let{catalog.assetPath(it,"image")}
    data class Card(val id:String,val path:String?,val label:String,val color:Int?=null,val crop:List<Float> = emptyList())
    data class Piece(val id:Int,val source:String,val x:Float,val y:Float,val slot:String?,val rotation:Int=0,val scale:Float=1f)
    data class Mark(val mode:String,val color:Int,val width:Int,val points:List<Pair<Float,Float>>,val asset:String?=null)
    private fun card(id:String):Card{val o=objs.getValue(id);val rgba=o["rgba"]?.arr()?.map{(it as Number).toInt()};val color=rgba?.let{(it[3] shl 24) or (it[0] shl 16) or (it[1] shl 8) or it[2]};return Card(id,art(o.optional("art_id")),o.optional("accessibility_label")?:"Hình",color,o.floats("display_crop_normalized"))}
    private fun cards(vararg roles:String)=objs.filterValues{it.optional("role") in roles}.keys.map(::card)
    private val type=b.optional("scene_type")
    val build=type=="participation_build";val draw=type=="participation_draw";val rhythm=type=="participation_rhythm"
    val trace=task.screen=="SCR-062";val sign=type=="participation_sign_or_draw"
    val supported=task.screen in setOf("SCR-062","SCR-063","SCR-064","SCR-066","SCR-067")&&(build||draw||rhythm||trace||sign)
    private val construction=b["construction"]?.obj().orEmpty();private val drawing=b["canvas"]?.obj().orEmpty();private val sequence=b["sequence"]?.obj().orEmpty()
    val palette=cards("palette_piece");val colors=cards("color_swatch");val decorations=cards("decoration");val references=cards("reference","stroke_reference");val signs=cards("sign_card");val pads=cards("sound_pad","rest")
    val contextCards=cards("context");val buildSlots=construction["slots"]?.arr()?.map{it.obj().s("slot_id")}.orEmpty()
    val buildKind=construction.optional("kind")?:"free_canvas"
    val buildBackground=art(b.optional("canvas_context_id")?.let{objs[it]?.optional("art_id")})
    val allowRotate=b.b("free_rotation");val allowScale=b.b("free_scale")
    val maxRhythm=if(rhythm)sequence.n("max_sequence_length")else 0
    val rhythmSlots=sequence["slots"]?.arr()?.size?:0
    val beatMillis=if(rhythm)((sequence["timing_s"] as Number).toDouble()*1000).toLong()else 850L
    val drawingMode=if(draw)drawing.s("mode")else "free_stroke"
    val sourceCrop=if(draw)drawing.floats("canvas_content_crop")else listOf(0f,0f,1f,1f)
    val rainbowCenter=drawing["rainbow_geometry"]?.obj()?.floats("center_normalized").orEmpty()
    val rainbowRadii=drawing["rainbow_geometry"]?.obj()?.floats("band_boundary_radii_px")?.map{it/512f}.orEmpty()
    private val history=mutableListOf<Obj>();private val redoHistory=mutableListOf<Obj>()
    private val pieces=mutableListOf<Piece>();private val marks=mutableListOf<Mark>();private val beats=mutableListOf<String>()
    private var counter=0
    var selectedPalette:String?=null;private set
    var selectedPiece:Int?=null;private set
    var selectedDecoration:String?=null;private set
    var selectedSlot:Int?=null;private set
    var selectedColor:String?=null;private set
    var selectedReference:String?=null;private set
    var selectedSign:String?=null;private set
    var brushMode=if(drawingMode in setOf("bounded_fill","rainbow_bands"))"fill" else "brush";private set
    var brushWidth=10;private set
    val color get()=colors.firstOrNull{it.id==selectedColor}?.color?:0xff24664f.toInt()
    val canvasPath get()=when{trace->art(scene.optional("trace_template_art_id"));sign->references.firstOrNull{it.id==selectedReference}?.path;draw->art(drawing.optional("art_id"));else->buildBackground}
    val placedPieces get()=pieces.toList();val committedMarks get()=marks.toList();val committedBeats get()=beats.toList()
    private fun inside(x:Float,y:Float)=x.isFinite()&&y.isFinite()&&x in 0f..1f&&y in 0f..1f
    private fun insideCanvas(x:Float,y:Float)=inside(x,y)&&x in sourceCrop[0]..sourceCrop[2]&&y in sourceCrop[1]..sourceCrop[3]
    fun pickPalette(id:String):Boolean{if(!build||palette.none{it.id==id})return false;selectedPalette=id;selectedPiece=null;return true}
    fun pickPiece(id:Int):Boolean{if(!build||pieces.none{it.id==id})return false;selectedPiece=id;selectedPalette=null;return true}
    fun pickDecoration(id:String):Boolean{if(!draw||decorations.none{it.id==id})return false;selectedDecoration=id;return true}
    fun pickSlot(index:Int):Boolean{if(!rhythm||index !in 0..beats.size||index>=maxRhythm)return false;selectedSlot=index;return true}
    fun selectColor(id:String)=record(mapOf("op" to "color","id" to id))
    fun chooseReference(id:String)=record(mapOf("op" to "reference","id" to id))
    fun chooseSign(id:String)=record(mapOf("op" to "sign","id" to id))
    fun setBrush(mode:String)=record(mapOf("op" to "brush","mode" to mode))
    fun setWidth(width:Int)=record(mapOf("op" to "width","n" to width))
    fun place(x:Float,y:Float,slot:String?=null):Boolean{
        val move=selectedPiece;val src=selectedPalette
        return if(move!=null)record(mapOf("op" to "move","n" to move,"x" to x,"y" to y,"slot" to slot)) else if(src!=null)record(mapOf("op" to "place","source" to src,"x" to x,"y" to y,"slot" to slot)) else false
    }
    fun removePiece()=selectedPiece?.let{record(mapOf("op" to "remove","n" to it))}?:false
    fun rotate()=selectedPiece?.let{record(mapOf("op" to "rotate","n" to it))}?:false
    fun scale()=selectedPiece?.let{record(mapOf("op" to "scale","n" to it))}?:false
    fun stroke(points:List<Pair<Float,Float>>,boundedFill:Boolean=false):Boolean{
        if(points.isEmpty())return false
        val mode=if(selectedDecoration!=null)"decoration" else if(brushMode=="fill"&&!boundedFill)"brush" else brushMode
        return record(mapOf("op" to "mark","mode" to mode,"color" to color,"width" to brushWidth,"points" to points.take(256).map{listOf(it.first,it.second)},"asset" to selectedDecoration))
    }
    fun pad(id:String):Boolean {val index=selectedSlot?:beats.size;val ok=record(mapOf("op" to "pad","id" to id,"index" to index));if(ok)selectedSlot=null;return ok}
    fun discardTransient(){selectedPalette=null;selectedPiece=null;selectedDecoration=null;selectedSlot=null}
    private fun apply(e:Obj):Boolean=runCatching {
        when(e.s("op")){
            "color"->{require(draw||sign);val id=e.s("id");require(colors.any{it.id==id});selectedColor=id;selectedDecoration=null}
            "reference"->{require(sign);val id=e.s("id");require(references.any{it.id==id});selectedReference=id;selectedSign=null;marks.clear()}
            "sign"->{require(sign);val id=e.s("id");require(signs.any{it.id==id});selectedSign=id}
            "brush"->{require(draw||trace||sign);val mode=e.s("mode");require(mode in setOf("brush","erase")||mode=="fill"&&drawingMode in setOf("bounded_fill","rainbow_bands"));brushMode=mode;selectedDecoration=null}
            "width"->{require(draw||trace||sign);val n=e.n("n");require(n in setOf(5,10,16));brushWidth=n}
            "place","move"->{require(build);val moving=e.s("op")=="move";val old=if(moving)pieces.single{it.id==e.n("n")}else null;val src=old?.source?:e.s("source");require(palette.any{it.id==src});val x=(e["x"] as Number).toFloat();val y=(e["y"] as Number).toFloat();val slot=e.optional("slot");require(inside(x,y));require(if(buildSlots.isEmpty())slot==null&&x in 0.05f..0.95f&&y in 0.05f..0.95f else slot in buildSlots);require(pieces.none{it.id!=old?.id&&slot!=null&&it.slot==slot});require(moving||pieces.size<construction.n("max_instances"));if(old!=null){pieces[pieces.indexOf(old)]=old.copy(x=x,y=y,slot=slot)}else pieces.add(Piece(++counter,src,x,y,slot));selectedPiece=null}
            "remove","rotate","scale"->{require(build);val item=pieces.single{it.id==e.n("n")};val index=pieces.indexOf(item);when(e.s("op")){"remove"->{pieces.removeAt(index);selectedPiece=null};"rotate"->{require(allowRotate);pieces[index]=item.copy(rotation=(item.rotation+45)%360)};else->{require(allowScale);pieces[index]=item.copy(scale=if(item.scale>=1.5f)0.75f else item.scale+0.25f)}}}
            "mark"->{require(draw||trace||sign&&selectedReference!=null);require(marks.size<120);val mode=e.s("mode");require(mode in setOf("brush","erase")||mode=="fill"&&drawingMode in setOf("bounded_fill","rainbow_bands")||mode=="decoration"&&decorations.any{it.id==e.optional("asset")});val width=e.n("width");require(width in setOf(5,10,16));val col=e.n("color");require(col==0xff24664f.toInt()||colors.any{it.color==col});val ps=e.a("points").map{v->val p=v.arr();require(p.size==2);((p[0] as Number).toFloat() to (p[1] as Number).toFloat()).also{require(insideCanvas(it.first,it.second))}};require(ps.isNotEmpty()&&ps.size<=256);marks.add(Mark(mode,col,width,ps,e.optional("asset")))}
            "pad"->{require(rhythm);val id=e.s("id");val ix=e.n("index");require(pads.any{it.id==id}&&ix in 0..beats.size&&ix<maxRhythm);if(ix==beats.size)beats.add(id)else beats[ix]=id}
            else->error("Unknown creative event")
        };true
    }.getOrDefault(false)
    private fun record(e:Obj):Boolean{if(!supported||history.size>=400||!apply(e))return false;history.add(e);redoHistory.clear();return true}
    private fun reset(){pieces.clear();marks.clear();beats.clear();counter=0;discardTransient();selectedColor=null;selectedReference=null;selectedSign=null;brushMode=if(drawingMode in setOf("bounded_fill","rainbow_bands"))"fill" else "brush";brushWidth=10}
    private fun replay(){reset();history.forEach{check(apply(it))};discardTransient()}
    fun undo():Boolean{if(history.isEmpty())return false;redoHistory.add(history.removeAt(history.lastIndex));replay();return true}
    fun redo():Boolean{if(redoHistory.isEmpty())return false;history.add(redoHistory.removeAt(redoHistory.lastIndex));replay();return true}
    fun clear():Boolean{reset();history.clear();redoHistory.clear();return true}
    fun saveFields():Obj=mapOf("valid_committed_response" to mapOf("schema" to "htq.creative.events.v1","events" to history.toList()))
    fun restoreFields(s:Obj):Boolean=runCatching{val raw=s["valid_committed_response"]?:return@runCatching clear();val o=raw.obj();require(o.s("schema")=="htq.creative.events.v1");val rows=o.a("events").map{it.obj()};require(rows.size<=400);val copy=CreativeInteraction(catalog,task);rows.forEach{require(copy.record(it))};clear();history.addAll(rows);replay();true}.getOrDefault(false)
    fun response():Response=Response.ParticipationDone
}
