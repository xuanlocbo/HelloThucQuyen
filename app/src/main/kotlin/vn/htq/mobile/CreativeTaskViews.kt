package vn.htq.mobile

import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import vn.htq.core.CreativeInteraction
import vn.htq.core.FillRegions
import vn.htq.core.ui.FitCenter

/** Editable native canvas; commits only on touch-up, no recording or aesthetic grading. */
internal class CreativeTaskViews(private val w:GardenWidgets,private val d:CreativeInteraction,
    private val finished:Boolean,private val pages:MutableMap<String,Int>,private val commit:(()->Boolean)->Unit,
    private val refresh:()->Unit,private val playObject:(String)->Unit,private val playRhythm:(List<String>)->Unit,private val stop:()->Unit){
    private fun title(b:LinearLayout,label:String)=w.add(b,w.text(label,20,true),8)
    private fun button(b:LinearLayout,label:String,enabled:Boolean=true,fn:()->Boolean){w.add(b,w.button(label,"white",enabled=!finished&&enabled,action={commit(fn)}))}
    private fun card(c:CreativeInteraction.Card,selected:Boolean=false,fn:(()->Boolean)?=null):View=w.column().apply{
        background=w.rounded(if(selected)"blue" else "white",if(selected)"navy" else "edge");minimumHeight=w.dp(64);setPadding(w.dp(4),w.dp(6),w.dp(4),w.dp(6));tag=c.id;contentDescription=c.label;isSelected=selected
        c.path?.let{p->w.add(this,if(c.crop.isEmpty())w.image(p)else w.croppedImage(p,c.crop,false),0,76)}
        w.add(this,w.text(c.label,18,true),0);if(!finished&&fn!=null){isFocusable=true;setOnClickListener{commit(fn)}}
    }
    private fun <T> page(b:LinearLayout,key:String,items:List<T>,render:(T)->View){if(items.isEmpty())return;val chunks=items.chunked(w.viewport.pageSize(5));val k="creative:${d.task.id}:$key";val n=(pages[k]?:0).coerceIn(0,chunks.lastIndex);w.grid(b,chunks[n].map(render),3,80,w.taskBoardDp);if(chunks.size>1){w.add(b,w.text("${n+1} / ${chunks.size}",18),4);w.pair(b,w.button("Trước","white",enabled=n>0,action={pages[k]=n-1;refresh()}),w.button("Sau","green",enabled=n<chunks.lastIndex,action={pages[k]=n+1;refresh()}))}}
    fun draw(b:LinearLayout){
        if(d.build){
            d.contextCards.filter{it.path!=d.buildBackground}.forEach{c->c.path?.let{w.art(b,it,104)}}
            title(b,"Chọn mảnh ghép");page(b,"palette",d.palette){c->card(c,d.selectedPalette==c.id){d.pickPalette(c.id)}}
            title(b,"Bức tranh của bé")
            if(d.buildSlots.isNotEmpty())page(b,"slots",d.buildSlots){slot->val placed=d.placedPieces.firstOrNull{it.slot==slot};w.column().apply{background=w.rounded("cream","edge");minimumHeight=w.dp(64);contentDescription="Ô ${d.buildSlots.indexOf(slot)+1}";placed?.let{p->val c=d.palette.single{it.id==p.source};c.path?.let{w.add(this,if(c.crop.isEmpty())w.image(it)else w.croppedImage(it,c.crop,false),0,76)}}?:w.add(this,w.text("Ô trống",18),0);if(!finished){isFocusable=true;setOnClickListener{commit{if(d.selectedPalette!=null||d.selectedPiece!=null)d.place(.5f,.5f,slot)else placed?.let{d.pickPiece(it.id)}?:false}}}}}
            else w.add(b,buildCanvas(b),0,if(w.viewport.wide)250 else 320)
            if(d.placedPieces.isNotEmpty()){title(b,"Mảnh đã đặt");page(b,"pieces",d.placedPieces){p->val c=d.palette.single{it.id==p.source};card(c.copy(label="Mảnh ${p.id}"),d.selectedPiece==p.id){d.pickPiece(p.id)}};button(b,"Cất mảnh",d.selectedPiece!=null){d.removePiece()};if(d.allowRotate)button(b,"Xoay",d.selectedPiece!=null){d.rotate()};if(d.allowScale)button(b,"Đổi kích cỡ",d.selectedPiece!=null){d.scale()}}
        }else if(d.rhythm){
            title(b,"Chọn âm thanh");page(b,"pads",d.pads){c->card(c,false){val ok=d.pad(c.id);if(ok)playObject(c.id);ok}}
            title(b,"Nhịp của bé");val count=maxOf(d.rhythmSlots,d.committedBeats.size+if(d.committedBeats.size<d.maxRhythm)1 else 0).coerceAtMost(d.maxRhythm)
            page(b,"beats",(0 until count).toList()){i->val c=d.committedBeats.getOrNull(i)?.let{id->d.pads.single{it.id==id}};if(c!=null)card(c.copy(label="${i+1}. ${c.label}"),d.selectedSlot==i){d.pickSlot(i)}else w.button("Ô ${i+1}","white",enabled=!finished&&i==d.committedBeats.size,action={commit{d.pickSlot(i)}})}
            w.pair(b,w.button("Nghe nhịp","teal",enabled=d.committedBeats.isNotEmpty(),action={playRhythm(d.committedBeats)}),w.button("Dừng","white",action=stop))
        }else{
            if(d.sign){title(b,"Chọn biển để nghe");page(b,"signs",d.signs){c->card(c,d.selectedSign==c.id){val ok=d.chooseSign(c.id);if(ok)playObject(c.id);ok}};title(b,"Hoặc chọn nét để tô");page(b,"references",d.references){c->card(c,d.selectedReference==c.id){d.chooseReference(c.id)}};if(d.selectedReference==null)return}
            else if(d.references.isNotEmpty()){title(b,"Hình gợi ý");page(b,"refs",d.references){card(it)}}
            if(d.colors.isNotEmpty()){title(b,"Chọn màu");page(b,"colors",d.colors){c->card(c,d.selectedColor==c.id){d.selectColor(c.id)}}}
            if(d.decorations.isNotEmpty()){title(b,"Chọn họa tiết");page(b,"decorations",d.decorations){c->card(c,d.selectedDecoration==c.id){d.pickDecoration(c.id)}}}
            val tools=mutableListOf("Bút vẽ" to "brush","Tẩy" to "erase");if(d.drawingMode in setOf("bounded_fill","rainbow_bands"))tools.add("Tô màu" to "fill")
            w.grid(b,tools.map{(label,mode)->w.button(label,if(d.brushMode==mode)"green" else "white",enabled=!finished,action={commit{d.setBrush(mode)}})},2,90,w.taskBoardDp)
            w.grid(b,listOf(5,10,16).map{n->w.button(when(n){5->"Nét nhỏ";10->"Nét vừa";else->"Nét lớn"},if(d.brushWidth==n)"green" else "white",enabled=!finished,action={commit{d.setWidth(n)}})},2,90,w.taskBoardDp)
            w.add(b,drawingCanvas(b),0,if(w.viewport.wide)250 else 340)
        }
        button(b,"Làm lại thao tác đã hoàn tác"){d.redo()}
    }
    private fun buildCanvas(parent:LinearLayout):View=object:View(parent.context){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var downX=0f;private var downY=0f
        private fun frame():FitCenter {val img=d.buildBackground?.let(w::bitmap);return FitCenter.of(width.toFloat(),height.toFloat(),(img?.width?:512).toFloat(),(img?.height?:512).toFloat())}
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return;val f=frame();c.drawColor(Color.WHITE);d.buildBackground?.let{p->c.drawBitmap(w.bitmap(p),null,RectF(f.left,f.top,f.left+f.width,f.top+f.height),paint)};c.save();c.clipRect(f.left,f.top,f.left+f.width,f.top+f.height)
            d.placedPieces.forEach{piece->val card=d.palette.single{it.id==piece.source};val bmp=card.path?.let(w::bitmap)?:return@forEach;val src=if(card.crop.size==4)Rect((card.crop[0]*bmp.width).toInt(),(card.crop[1]*bmp.height).toInt(),(card.crop[2]*bmp.width).toInt(),(card.crop[3]*bmp.height).toInt())else Rect(0,0,bmp.width,bmp.height);val side=minOf(f.width,f.height)*.22f*piece.scale;val p=FitCenter.of(side,side,src.width().toFloat(),src.height().toFloat());val x=f.x(piece.x);val y=f.y(piece.y);c.save();c.rotate(piece.rotation.toFloat(),x,y);c.drawBitmap(bmp,src,RectF(x-side/2+p.left,y-side/2+p.top,x-side/2+p.left+p.width,y-side/2+p.top+p.height),paint);c.restore();if(d.selectedPiece==piece.id){paint.style=Paint.Style.STROKE;paint.color=w.color("navy");paint.strokeWidth=w.dp(3).toFloat();c.drawRect(x-side/2,y-side/2,x+side/2,y+side/2,paint);paint.style=Paint.Style.FILL}}
            c.restore()
        }
        override fun onTouchEvent(e:MotionEvent):Boolean{if(finished)return false;when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;return true};MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.x-downX)<=w.dp(12)&&kotlin.math.abs(e.y-downY)<=w.dp(12)){val q=frame().inverse(e.x,e.y);if(q!=null){performClick();commit{if(d.selectedPalette!=null||d.selectedPiece!=null)d.place(q.first,q.second)else d.placedPieces.asReversed().firstOrNull{kotlin.math.abs(it.x-q.first)<.12f&&kotlin.math.abs(it.y-q.second)<.12f}?.let{d.pickPiece(it.id)}?:false}}};return true};MotionEvent.ACTION_CANCEL->return true};return true}
        override fun performClick():Boolean{super.performClick();return true}
        init{contentDescription="Bức tranh, chạm để đặt mảnh";isClickable=!finished;minimumHeight=w.dp(56)}
    }
    private fun drawingCanvas(parent:LinearLayout):View=object:View(parent.context){
        private val base=d.canvasPath?.let(w::bitmap)
        private val bw=base?.width?:512;private val bh=base?.height?:512
        private val crop=d.sourceCrop
        private val src=Rect((crop[0]*bw).toInt(),(crop[1]*bh).toInt(),(crop[2]*bw).toInt(),(crop[3]*bh).toInt())
        private val pixels=IntArray(bw*bh).also{a->if(base!=null)base.getPixels(a,0,bw,0,0,bw,bh)else a.fill(Color.WHITE)}
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var drawing=mutableListOf<Pair<Float,Float>>()
        private var layer:Bitmap?=null
        private var outline:Bitmap?=null
        private fun frame()=FitCenter.of(width.toFloat(),height.toFloat(),src.width().toFloat(),src.height().toFloat())
        private fun normalized(x:Float,y:Float):Pair<Float,Float>?=frame().inverse(x,y)?.let{(a,b)->(src.left+a*src.width())/bw to (src.top+b*src.height())/bh}
        private fun mask(x:Float,y:Float):BooleanArray?{
            if(d.drawingMode=="rainbow_bands"){val band=FillRegions.rainbowBand(d.rainbowCenter,d.rainbowRadii,x,y)?:return null;return BooleanArray(bw*bh){i->FillRegions.rainbowBand(d.rainbowCenter,d.rainbowRadii,(i%bw+.5f)/bw,(i/bw+.5f)/bh)==band&&((pixels[i] ushr 24)<128||minOf((pixels[i] ushr 16)and 255,(pixels[i] ushr 8)and 255,pixels[i]and 255)>160)}}
            if(d.drawingMode!="bounded_fill")return null
            val px=(x*bw).toInt().coerceIn(0,bw-1);val py=(y*bh).toInt().coerceIn(0,bh-1)
            return w.cachedFillMask("${d.canvasPath}:$bw:$bh",py*bw+px){FillRegions.boundedMask(pixels,bw,bh,px,py)}
        }
        private fun renderMark(c:Canvas,m:CreativeInteraction.Mark,scale:Float){
            if(m.mode=="fill"){val p=m.points.first();val region=mask(p.first,p.second)?:return;val a=IntArray(region.size){i->if(region[i])m.color else Color.TRANSPARENT};val b=Bitmap.createBitmap(a,bw,bh,Bitmap.Config.ARGB_8888);c.drawBitmap(b,0f,0f,paint);b.recycle();return}
            if(m.mode=="decoration"){val p=m.points.first();val bmp=d.decorations.firstOrNull{it.id==m.asset}?.path?.let(w::bitmap)?:return;val side=minOf(bw,bh)*.14f;val f=FitCenter.of(side,side,bmp.width.toFloat(),bmp.height.toFloat());val x=p.first*bw-side/2;val y=p.second*bh-side/2;c.drawBitmap(bmp,null,RectF(x+f.left,y+f.top,x+f.left+f.width,y+f.top+f.height),paint);return}
            paint.color=m.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=w.dp(m.width)/scale;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            if(m.mode=="erase")paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            val path=Path();m.points.forEachIndexed{i,p->if(i==0)path.moveTo(p.first*bw,p.second*bh)else path.lineTo(p.first*bw,p.second*bh)}
            if(m.points.size==1){val p=m.points.first();c.drawPoint(p.first*bw,p.second*bh,paint)}else c.drawPath(path,paint)
            paint.xfermode=null;paint.style=Paint.Style.FILL
        }
        override fun onSizeChanged(a:Int,b:Int,oldA:Int,oldB:Int){super.onSizeChanged(a,b,oldA,oldB);layer?.recycle();layer=null}
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return;val f=frame();val dst=RectF(f.left,f.top,f.left+f.width,f.top+f.height);c.drawColor(Color.WHITE);base?.let{c.drawBitmap(it,src,dst,paint)}
            val scale=f.width/src.width();val saved=layer?:Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888).also{layer=it;val lc=Canvas(it);d.committedMarks.forEach{m->renderMark(lc,m,scale)}}
            c.drawBitmap(saved,src,dst,paint)
            // Keep the original dark outline above child fill and erasure layers.
            if(base!=null&&d.drawingMode in setOf("bounded_fill","rainbow_bands")){
                val edge=outline?:Bitmap.createBitmap(IntArray(pixels.size){i->val v=pixels[i];if((v ushr 24)>=128&&minOf((v ushr 16)and 255,(v ushr 8)and 255,v and 255)<160)v else Color.TRANSPARENT},bw,bh,Bitmap.Config.ARGB_8888).also{outline=it}
                c.drawBitmap(edge,src,dst,paint)
            }
            if(drawing.isNotEmpty()&&d.brushMode=="brush"&&d.selectedDecoration==null){c.save();c.clipRect(dst);paint.color=d.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=w.dp(d.brushWidth).toFloat();paint.strokeCap=Paint.Cap.ROUND;val p=Path();drawing.forEachIndexed{i,(x,y)->val xx=f.left+(x*bw-src.left)*scale;val yy=f.top+(y*bh-src.top)*scale;if(i==0)p.moveTo(xx,yy)else p.lineTo(xx,yy)};c.drawPath(p,paint);paint.style=Paint.Style.FILL;c.restore()}
        }
        override fun onTouchEvent(e:MotionEvent):Boolean {if(finished)return false;when(e.actionMasked){MotionEvent.ACTION_DOWN->{val q=normalized(e.x,e.y)?:return false;drawing=mutableListOf(q);parent.requestDisallowInterceptTouchEvent(true);invalidate();return true};MotionEvent.ACTION_MOVE->{normalized(e.x,e.y)?.let{q->if(drawing.size<256)drawing.add(q)};invalidate();return true};MotionEvent.ACTION_UP->{parent.requestDisallowInterceptTouchEvent(false);normalized(e.x,e.y)?.let{if(drawing.size<256)drawing.add(it)};val points=drawing.toList();drawing.clear();performClick();if(points.isNotEmpty()){val q=points.first();val fillMode=d.brushMode=="fill"&&d.selectedDecoration==null;val fill=fillMode&&points.size<=4&&mask(q.first,q.second)!=null;if(!fillMode||fill||points.any{p->kotlin.math.hypot(p.first-q.first,p.second-q.second)>.01f})commit{d.stroke(if(fill)listOf(q)else points,fill)}};invalidate();return true};MotionEvent.ACTION_CANCEL->{drawing.clear();parent.requestDisallowInterceptTouchEvent(false);invalidate();return true}};return true}
        override fun performClick():Boolean{super.performClick();return true}
        override fun onDetachedFromWindow(){layer?.recycle();layer=null;outline?.recycle();outline=null;super.onDetachedFromWindow()}
        init{contentDescription="Bảng vẽ, chạm hoặc kéo ngón tay để tô";minimumHeight=w.dp(56);isClickable=!finished;isFocusable=isClickable}
    }
}
