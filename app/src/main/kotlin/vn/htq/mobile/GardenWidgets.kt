package vn.htq.mobile

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.floor
import kotlin.math.ceil
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.Switch
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import vn.htq.core.ui.ApprovedUi
import vn.htq.core.ui.UiViewport
import vn.htq.core.ui.UiOutline
import vn.htq.core.ui.FitCenter
import vn.htq.core.ui.UiElement
import vn.htq.core.ui.ZoomWindow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

/** Native View toolkit, not a WebView or a screenshot. All sizes are dp/sp. */
internal class GardenWidgets(private val activity: Activity) {
    private val config get() = activity.resources.configuration
    var largeText=false
    var reducedMotion=false
    private fun scaledSp(size:Int):Float=size*(if(largeText)1.18f else 1f)
    val viewport get() = UiViewport(config.screenWidthDp.coerceAtLeast(200),config.screenHeightDp.coerceAtLeast(200),(config.fontScale*(if(largeText)1.18f else 1f)).coerceAtLeast(1f))
    // CI prepares the licensed Noto faces; fail visibly rather than silently changing typography.
    private val regular = Typeface.createFromAsset(activity.assets,"htq-font/NotoSans-Regular.ttf")
    private val bold = Typeface.createFromAsset(activity.assets,"htq-font/NotoSans-Bold.ttf")
    private val cache = object : LruCache<String, Bitmap>(24*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.byteCount
    }
    private val maskCache=object:LruCache<String,BooleanArray>(8*1024*1024){override fun sizeOf(key:String,value:BooleanArray)=value.size}
    private var maskSource:String?=null
    private var maskKeys=mutableListOf<String>()
    fun cachedFillMask(source:String,seed:Int,calculate:()->BooleanArray?):BooleanArray?{
        if(maskSource!=source){maskCache.evictAll();maskKeys.clear();maskSource=source}
        for(key in maskKeys.toList()){val mask=maskCache.get(key);if(mask==null)maskKeys.remove(key)else if(seed in mask.indices&&mask[seed])return mask}
        val mask=calculate()?:return null
        val key="$source:$seed";maskCache.put(key,mask);if(key !in maskKeys)maskKeys.add(key)
        return mask
    }
    fun dp(value:Int):Int=(value*activity.resources.displayMetrics.density+0.5f).toInt()
    fun color(key:String):Int=Color.parseColor(ApprovedUi.palette.getValue(key))
    fun rounded(fill:String="white",stroke:String="softedge",radius:Int=22):GradientDrawable=GradientDrawable().apply {
        setColor(color(fill));cornerRadius=dp(radius).toFloat();setStroke(dp(1),color(stroke))
    }
    private fun ripple(fill:String,stroke:String):android.graphics.drawable.Drawable=if(reducedMotion)rounded(fill,stroke)else RippleDrawable(ColorStateList.valueOf(0x2224664F),rounded(fill,stroke),null)
    fun column():LinearLayout=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
    fun row():LinearLayout=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun add(parent:LinearLayout,view:View,gap:Int=12,height:Int=ViewGroup.LayoutParams.WRAP_CONTENT){
        parent.addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,if(height>=0)dp(height) else height).apply{topMargin=dp(gap)})
    }
    fun text(value:String,size:Int=18,heavy:Boolean=false,ink:String="ink",center:Boolean=true):TextView=TextView(activity).apply{
        text=value;textSize=scaledSp(size);typeface=if(heavy)bold else regular;setTextColor(color(ink));gravity=if(center)Gravity.CENTER else Gravity.START
        setPadding(dp(6),dp(5),dp(6),dp(5));setLineSpacing(dp(3).toFloat(),1f);setSingleLine(false)
    }
    fun button(label:String,fill:String="green",adult:Boolean=false,enabled:Boolean=true,action:(()->Unit)?=null):Button=Button(activity).apply{
        text=label;textSize=scaledSp(if(adult)18 else ApprovedUi.buttonSp);typeface=bold;setAllCaps(false);setSingleLine(false)
        gravity=Gravity.CENTER;minimumHeight=dp(if(adult)ApprovedUi.adultMinDp else ApprovedUi.preferredDp);minHeight=minimumHeight
        setPadding(dp(12),dp(10),dp(12),dp(10));setTextColor(color(if(enabled && fill=="teal")"white" else if(enabled)"ink" else "disabledink"));background=ripple(if(enabled)fill else "disabled","edge")
        isEnabled=enabled && action!=null;isFocusable=true;if(action!=null)setOnClickListener{action()}
    }
    fun adultSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit):View=Switch(activity).apply {
        text=label;typeface=regular;textSize=scaledSp(18);setTextColor(color("ink"));minHeight=dp(64)
        setPadding(dp(12),dp(8),dp(12),dp(8));background=rounded("white");isChecked=checked
        contentDescription=label;setOnCheckedChangeListener{_,v->onChange(v)}
    }
    fun adultSlider(label:String,initial:Int,maxValue:Int,format:(Int)->String,onChange:(Int)->Unit):View=column().apply {
        background=rounded("white");setPadding(dp(12),dp(8),dp(12),dp(8))
        val value=text("$label: ${format(initial)}",18,center=false);add(this,value,0)
        add(this,SeekBar(activity).apply {max=maxValue;progress=initial;minimumHeight=dp(56);contentDescription=label
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(bar:SeekBar?,n:Int,fromUser:Boolean){value.text="$label: ${format(n)}";onChange(n)}
                override fun onStartTrackingTouch(bar:SeekBar?)=Unit
                override fun onStopTrackingTouch(bar:SeekBar?)=Unit
            })},0)
    }
    fun adultInput(label:String):EditText=EditText(activity).apply {
        inputType=android.text.InputType.TYPE_CLASS_NUMBER;setSingleLine(true);textSize=24f;typeface=regular
        minimumHeight=dp(56);hint=label;contentDescription=label
    }
    // View-only zoom: one FitCenter transform, clamped pan, no answer overlays or response events.
    fun zoomArt(path:String,zoomed:Boolean,point:Pair<Double,Double>?=null,onTap:((Double,Double)->Unit)?=null):View=object:View(activity){
        private val bmp=bitmap(path)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var panX=0f;private var panY=0f;private var lastX=0f;private var lastY=0f;private var startX=0f;private var startY=0f;private var moved=false
        private fun frame()=ZoomWindow.of(width.toFloat(),height.toFloat(),bmp.width.toFloat(),bmp.height.toFloat(),if(zoomed)2f else 1f,panX,panY)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return;val r=frame();paint.style=Paint.Style.FILL;c.drawBitmap(bmp,null,android.graphics.RectF(r.left,r.top,r.left+r.width,r.top+r.height),paint)
            point?.let{val x=r.left+it.first.toFloat()*r.width;val y=r.top+it.second.toFloat()*r.height;paint.style=Paint.Style.STROKE;paint.color=color("navy");paint.strokeWidth=dp(3).toFloat();c.drawCircle(x,y,dp(13).toFloat(),paint);paint.style=Paint.Style.FILL}
        }
        override fun onTouchEvent(event:MotionEvent):Boolean{
            if((!zoomed&&onTap==null)||width<=0||height<=0)return super.onTouchEvent(event)
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN->{lastX=event.x;lastY=event.y;startX=event.x;startY=event.y;moved=false;if(zoomed)parent.requestDisallowInterceptTouchEvent(true);return true}
                MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(event.x-startX)>dp(8)||kotlin.math.abs(event.y-startY)>dp(8))moved=true
                    if(zoomed&&moved){panX+=event.x-lastX;panY+=event.y-lastY;val r=frame();panX=r.left-(width-r.width)/2;panY=r.top-(height-r.height)/2;invalidate()};lastX=event.x;lastY=event.y;return true}
                MotionEvent.ACTION_UP->{parent.requestDisallowInterceptTouchEvent(false);if(!moved){performClick();val r=frame();val x=(event.x-r.left)/r.width;val y=(event.y-r.top)/r.height;if(x in 0f..1f&&y in 0f..1f)onTap?.invoke(x.toDouble(),y.toDouble())};return true}
                MotionEvent.ACTION_CANCEL->{parent.requestDisallowInterceptTouchEvent(false);return true}
            };return true
        }
        override fun performClick():Boolean{super.performClick();return true}
        init{contentDescription=if(zoomed)"Tranh phóng to, kéo để xem" else "Tranh";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=zoomed||onTap!=null;isFocusable=isClickable;minimumHeight=dp(56)}
    }
    data class GardenItem(val path:String,val x:Float,val y:Float)
    fun gardenScene(path:String,items:List<GardenItem>,onPlace:((Double,Double)->Unit)?=null):View=object:View(activity){
        private val bg=bitmap(path);private val pictures=items.map{it.path}.distinct().associateWith(::decorativeBitmap);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var downX=0f;private var downY=0f;private var moved=false
        private val slop=android.view.ViewConfiguration.get(activity).scaledTouchSlop
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            val r=FitCenter.of(width.toFloat(),height.toFloat(),bg.width.toFloat(),bg.height.toFloat());c.drawBitmap(bg,null,android.graphics.RectF(r.left,r.top,r.left+r.width,r.top+r.height),paint)
            val size=min(r.width,r.height)*.22f
            items.forEachIndexed{i,item->val b=pictures.getValue(item.path);val f=FitCenter.of(size,size,b.width.toFloat(),b.height.toFloat());val x=(r.x(item.x)-size/2).coerceIn(r.left,r.left+r.width-size);val y=(r.y(item.y)-size/2).coerceIn(r.top,r.top+r.height-size);c.drawBitmap(b,null,android.graphics.RectF(x+f.left,y+f.top,x+f.left+f.width,y+f.top+f.height),paint)}
        }
        override fun onTouchEvent(e:MotionEvent):Boolean{
            if(onPlace==null)return super.onTouchEvent(e)
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;moved=false;return true}
                MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(e.x-downX)>slop||kotlin.math.abs(e.y-downY)>slop)moved=true;return true}
                MotionEvent.ACTION_UP->{if(!moved&&width>0&&height>0){val r=FitCenter.of(width.toFloat(),height.toFloat(),bg.width.toFloat(),bg.height.toFloat());r.inverse(e.x,e.y)?.let{q->performClick();onPlace(q.first.toDouble(),q.second.toDouble())}};return true}
                MotionEvent.ACTION_CANCEL->{moved=true;return true}
            };return true
        }
        override fun performClick():Boolean{super.performClick();return true}
        init{contentDescription=if(onPlace==null)"Khu vườn, ${items.size} vật phẩm đã đặt" else "Chạm vào vườn để đặt vật";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=onPlace!=null;isFocusable=onPlace!=null;minimumHeight=dp(56)}
    }
    fun bitmap(path:String):Bitmap{
        require(path.matches(Regex("[a-z0-9_./-]+")) && path.split('/').none{it.isEmpty()||it==".."||it=="."})
        cache.get(path)?.let{return it}
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        activity.assets.open("htq/$path").use{BitmapFactory.decodeStream(it,null,options)}
        require(options.outWidth>0 && options.outHeight>0)
        var sample=1;while(options.outWidth/sample>1280 || options.outHeight/sample>1280)sample*=2
        val decoded=activity.assets.open("htq/$path").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
        val result=requireNotNull(decoded);cache.put(path,result);return result
    }
    val compactPortrait get()=viewport.width<400 && viewport.height>=viewport.width
    private fun decorativeBitmap(path:String):Bitmap{
        val key="@decorative:$path"
        cache.get(key)?.let{return it}
        val src=bitmap(path)
        if(!src.hasAlpha()){cache.put(key,src);return src}
        var left=src.width;var top=src.height;var right=-1;var bottom=-1
        val row=IntArray(src.width)
        for(y in 0 until src.height){
            src.getPixels(row,0,src.width,0,y,src.width,1)
            for(x in row.indices)if(row[x] ushr 24 >= 16){
                left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
            }
        }
        if(right<left||bottom<top){cache.put(key,src);return src}
        val margin=maxOf(2,maxOf(right-left+1,bottom-top+1)/25)
        left=(left-margin).coerceAtLeast(0);top=(top-margin).coerceAtLeast(0)
        right=(right+margin).coerceAtMost(src.width-1);bottom=(bottom+margin).coerceAtMost(src.height-1)
        val cropped=if(left==0&&top==0&&right==src.width-1&&bottom==src.height-1)src
            else Bitmap.createBitmap(src,left,top,right-left+1,bottom-top+1)
        cache.put(key,cropped);return cropped
    }
    private fun decorativeImage(path:String,description:String?=null):ImageView=ImageView(activity).apply{
        scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(decorativeBitmap(path));contentDescription=description
        importantForAccessibility=if(description==null)View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun decorativeArt(parent:LinearLayout,path:String,height:Int,description:String?=null){add(parent,decorativeImage(path,description),8,height)}
    fun gardenHeight(path:String):Int{
        val art=bitmap(path)
        return (viewport.available.toFloat()*art.height/art.width).toInt().coerceIn(56,300)
    }
    fun homeCard(title:String,path:String,fill:String,action:()->Unit):LinearLayout=column().apply{
        background=ripple(fill,"edge");minimumHeight=dp(ApprovedUi.preferredDp);setPadding(dp(6),dp(6),dp(6),dp(6))
        isClickable=true;isFocusable=true;contentDescription=title;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        add(this,decorativeImage(path),0,if(compactPortrait)48 else if(viewport.wide)76 else 90)
        add(this,text(title,20,true).apply{
            if(compactPortrait){setPadding(0,0,0,0);setLineSpacing(0f,1f);minLines=2}
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },4)
        setOnClickListener{action()}
    }
    fun image(path:String,description:String?=null):ImageView=ImageView(activity).apply{
        scaleType=ImageView.ScaleType.FIT_CENTER;adjustViewBounds=true;setImageBitmap(bitmap(path));contentDescription=description
        importantForAccessibility=if(description==null)View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun croppedImage(path:String,rect:List<Float>,mirror:Boolean):ImageView {
        val bmp=bitmap(path)
        val left=floor(rect[0]*bmp.width).toInt().coerceIn(0,bmp.width-1)
        val top=floor(rect[1]*bmp.height).toInt().coerceIn(0,bmp.height-1)
        val right=ceil(rect[2]*bmp.width).toInt().coerceIn(left+1,bmp.width)
        val bottom=ceil(rect[3]*bmp.height).toInt().coerceIn(top+1,bmp.height)
        val transform=Matrix().apply{if(mirror)setScale(-1f,1f)}
        val result=Bitmap.createBitmap(bmp,left,top,right-left,bottom-top,transform,true)
        return ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(result);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    fun countedImage(path:String,count:Int):View=object:View(activity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val image=bitmap(path)
        override fun onDraw(c:Canvas){super.onDraw(c);if(count==0)return
            val cols=5
            val rows=2
            val unit=min(width.toFloat()/cols,height.toFloat()/rows)*.92f
            val scale=min(unit/image.width,unit/image.height)
            val iw=image.width*scale;val ih=image.height*scale
            for(i in 0 until count){val x=(width-cols*unit)/2+(i%cols)*unit+(unit-iw)/2;val y=(height-rows*unit)/2+(i/cols)*unit+(unit-ih)/2
                c.drawBitmap(image,null,android.graphics.RectF(x,y,x+iw,y+ih),paint)}
        }
        init{minimumHeight=dp(72);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    data class BoardPlacement(val path:String,val crop:List<Float>,val target:List<Float>)
    fun outlineBoard(path:String,shapes:List<UiOutline>,placements:List<BoardPlacement> = emptyList(),numberTargets:Boolean=false):View=object:View(activity){
        private val image=bitmap(path)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            val fit=FitCenter.of(width.toFloat(),height.toFloat(),image.width.toFloat(),image.height.toFloat())
            paint.style=Paint.Style.FILL;c.drawBitmap(image,null,android.graphics.RectF(fit.left,fit.top,fit.left+fit.width,fit.top+fit.height),paint)
            placements.forEach{p->
                val bmp=bitmap(p.path);val src=if(p.crop.isEmpty())android.graphics.Rect(0,0,bmp.width,bmp.height)else sourceRect(bmp,p.crop)
                val r=p.target;val x=fit.x(r[0]);val y=fit.y(r[1]);val local=FitCenter.of(fit.x(r[2])-x,fit.y(r[3])-y,src.width().toFloat(),src.height().toFloat())
                c.drawBitmap(bmp,src,android.graphics.RectF(x+local.left,y+local.top,x+local.left+local.width,y+local.top+local.height),paint)
            }
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2).toFloat();paint.color=color("edge")
            shapes.forEach{shape->val r=shape.rect;val rect=android.graphics.RectF(fit.x(r[0]),fit.y(r[1]),fit.x(r[2]),fit.y(r[3]))
                when(shape.shape){
                    "rect"->c.drawRect(rect,paint)
                    "ellipse"->c.drawOval(rect,paint)
                    "polygon"->{val outline=android.graphics.Path();shape.points.forEachIndexed{i,p->if(i==0)outline.moveTo(fit.x(p[0]),fit.y(p[1]))else outline.lineTo(fit.x(p[0]),fit.y(p[1]))};outline.close();c.drawPath(outline,paint)}
                }
            }
            paint.style=Paint.Style.FILL
            if(numberTargets){paint.typeface=bold;paint.textSize=dp(18).toFloat();paint.textAlign=Paint.Align.CENTER
                shapes.forEachIndexed{i,shape->val r=shape.rect;val x=fit.x((r[0]+r[2])/2);val y=fit.y((r[1]+r[3])/2);paint.color=color("cream");c.drawCircle(x,y,dp(12).toFloat(),paint);paint.color=color("ink");c.drawText((i+1).toString(),x,y-(paint.ascent()+paint.descent())/2,paint)}
            }
        }
        init{contentDescription="Khuôn ghép hình";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
    }
    private fun sourceRect(image:Bitmap,r:List<Float>)=android.graphics.Rect(
        floor(r[0]*image.width).toInt().coerceIn(0,image.width-1),floor(r[1]*image.height).toInt().coerceIn(0,image.height-1),
        ceil(r[2]*image.width).toInt().coerceIn(1,image.width),ceil(r[3]*image.height).toInt().coerceIn(1,image.height))
    fun polygonImage(e:UiElement.PolygonCrop):View=object:View(activity){
        private val image=bitmap(e.path);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            val r=sourceRect(image,e.rect);val fit=FitCenter.of(width.toFloat(),height.toFloat(),r.width().toFloat(),r.height().toFloat())
            val outline=android.graphics.Path();e.points.forEachIndexed{i,p->val x=fit.x((p[0]-e.rect[0])/(e.rect[2]-e.rect[0]));val y=fit.y((p[1]-e.rect[1])/(e.rect[3]-e.rect[1]));if(i==0)outline.moveTo(x,y)else outline.lineTo(x,y)};outline.close()
            c.save();if(e.mirror)c.scale(-1f,1f,width/2f,height/2f);c.clipPath(outline)
            c.drawBitmap(image,r,android.graphics.RectF(fit.left,fit.top,fit.left+fit.width,fit.top+fit.height),paint);c.restore()
        }
        init{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    fun scaledImage(e:UiElement.Scaled):View=object:View(activity){
        private val image=bitmap(e.path);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            val r=sourceRect(image,e.rect);val fit=FitCenter.of(width.toFloat(),height.toFloat(),r.width().toFloat(),r.height().toFloat())
            val ww=fit.width*e.scale;val hh=if(e.axis=="height_and_width_uniform")fit.height*e.scale else fit.height
            val x=(width-ww)/2f;val y=if(e.alignment=="bottom")height-hh else (height-hh)/2f
            c.drawBitmap(image,r,android.graphics.RectF(x,y,x+ww,y+hh),paint)
        }
        init{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    fun annulus(e:UiElement.Annulus):View=object:View(activity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);val dim=min(width,height).toFloat();if(dim<=0)return
            val x=(width-dim)/2+dim*e.center[0];val y=(height-dim)/2+dim*e.center[1];val a=dim*e.outer;val b=dim*e.inner
            val outline=android.graphics.Path().apply{addCircle(x,y,if(e.region=="outer")a else b,android.graphics.Path.Direction.CW);if(e.region=="outer")addCircle(x,y,b,android.graphics.Path.Direction.CCW)}
            paint.style=Paint.Style.FILL;paint.color=color("cream");c.drawCircle(x,y,a,paint);paint.color=color("green");c.drawPath(outline,paint)
            c.save();c.clipPath(outline);paint.color=color("edge");paint.strokeWidth=dp(1).toFloat();var line=x-a*2;while(line<x+a*2){c.drawLine(line,y-a,line+a*2,y+a,paint);line+=dp(9)};c.restore()
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(e.stroke).toFloat();paint.color=Color.parseColor(e.outerColor);c.drawCircle(x,y,a,paint);paint.color=Color.parseColor(e.innerColor);c.drawCircle(x,y,b,paint);paint.style=Paint.Style.FILL
        }
        init{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    }
    fun spatial(e:UiElement.Spatial,movablePath:String?=null,zoneIndex:Int=-1,depth:Int=0):View=object:View(activity){
        private val image=bitmap(e.path);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            fun box(center:List<Float>,size:List<Float>):android.graphics.RectF {val x=center[0]*width;val y=center[1]*height;val ww=size[0]*width;val hh=size[1]*height;return android.graphics.RectF(x-ww/2,y-hh/2,x+ww/2,y+hh/2)}
            fun placed(){if(movablePath==null||zoneIndex !in e.zones.indices)return;val z=e.zones[zoneIndex];val dst=box(z.center,z.size);val bmp=bitmap(movablePath);val f=FitCenter.of(dst.width(),dst.height(),bmp.width.toFloat(),bmp.height.toFloat());c.drawBitmap(bmp,null,android.graphics.RectF(dst.left+f.left,dst.top+f.top,dst.left+f.left+f.width,dst.top+f.top+f.height),paint)}
            paint.style=Paint.Style.FILL;if(depth<0)placed()
            val r=box(e.center,e.size);val f=FitCenter.of(r.width(),r.height(),image.width.toFloat(),image.height.toFloat());c.drawBitmap(image,null,android.graphics.RectF(r.left+f.left,r.top+f.top,r.left+f.left+f.width,r.top+f.top+f.height),paint)
            if(depth>=0)placed()
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2).toFloat();paint.color=color("edge");paint.pathEffect=android.graphics.DashPathEffect(floatArrayOf(dp(7).toFloat(),dp(5).toFloat()),0f)
            e.zones.forEach{c.drawRoundRect(box(it.center,it.size),dp(10).toFloat(),dp(10).toFloat(),paint)};paint.pathEffect=null;paint.style=Paint.Style.FILL
        }
        init{contentDescription="Sân đặt vật"}
    }
    fun measureLength(e:UiElement.MeasureLength,placedCount:Int?=null):View=object:View(activity){
        private val target=bitmap(e.path);private val unit=bitmap(e.unitPath);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            val tr=sourceRect(target,e.rect);val ur=sourceRect(unit,e.unitRect)
            val step=minOf((width-dp(24)).coerceAtLeast(1).toFloat()/maxOf(e.length,e.unitCount),height*.23f*tr.width()/tr.height()/e.length,height*.10f*ur.width()/ur.height())
            val tw=step*e.length;val x=if(placedCount==null)(width-tw)/2 else (width-step*maxOf(e.length,e.unitCount))/2
            val th=tw*tr.height()/tr.width();val uh=step*ur.height()/ur.width()
            c.drawBitmap(target,tr,android.graphics.RectF(x,height*.22f-th/2,x+tw,height*.22f+th/2),paint)
            paint.color=color("edge");paint.strokeWidth=dp(2).toFloat();c.drawLine(x,height*.46f,x+tw,height*.46f,paint)
            val ux=if(placedCount==null)(width-e.unitCount*step)/2 else x
            for(i in 0 until (placedCount?:e.unitCount)){val left=ux+i*step;val y=if(placedCount==null)height*.73f else height*.52f;c.drawBitmap(unit,ur,android.graphics.RectF(left,y,left+step,y+uh),paint)}
        }
        init{contentDescription="Vật cần đo và các que đo"}
    }
    fun vessel(e:UiElement.Vessel):View=object:View(activity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val source=bitmap(e.path)
        override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
            // Initial visual model: same unnumbered vessel; no computed answer shown.
            val fit=FitCenter.of(width.toFloat(),height*.28f,source.width.toFloat(),source.height.toFloat());c.drawBitmap(source,null,android.graphics.RectF(fit.left,fit.top,fit.left+fit.width,fit.top+fit.height),paint)
            val l=width*.26f;val r=width*.74f;val t=height*.31f;val b=height*.92f;val jar=android.graphics.RectF(l,t,r,b)
            paint.style=Paint.Style.FILL;paint.color=color("cream");c.drawRoundRect(jar,dp(8).toFloat(),dp(8).toFloat(),paint)
            if(e.fill>0){paint.color=color("blue");c.drawRect(l+dp(2),b-(b-t)*e.fill,r-dp(2),b-dp(2),paint)}
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2).toFloat();paint.color=color("edge");c.drawRoundRect(jar,dp(8).toFloat(),dp(8).toFloat(),paint);paint.style=Paint.Style.FILL
        }
        init{contentDescription="Bình ${e.label}"}
    }
    fun art(parent:LinearLayout,path:String,height:Int=184,description:String?=null){add(parent,image(path,description),8,height)}
    fun card(title:String,path:String?,fill:String="white",height:Int=92,action:()->Unit):LinearLayout=column().apply{
        background=ripple(fill,"edge");minimumHeight=dp(ApprovedUi.preferredDp);setPadding(dp(8),dp(8),dp(8),dp(8));isClickable=true;isFocusable=true;contentDescription=title
        path?.let{add(this,image(it),0,height)};add(this,text(title,20,true),4);setOnClickListener{action()}
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        for(i in 0 until childCount)getChildAt(i).importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun grid(parent:LinearLayout,views:List<View>,wanted:Int=2,minCard:Int=100,availableDp:Int=viewport.available){
        val cols=wanted.coerceAtLeast(1).coerceAtMost(((availableDp+ApprovedUi.gapDp)/(minCard+ApprovedUi.gapDp)).coerceAtLeast(1));views.chunked(cols).forEach{items->
            val r=row();items.forEachIndexed{i,v->r.addView(v,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{if(i>0)marginStart=dp(12)})}
            repeat(cols-items.size){r.addView(View(activity),LinearLayout.LayoutParams(0,1,1f).apply{marginStart=dp(12)})};add(parent,r)
        }
    }
    fun pair(parent:LinearLayout,first:View,second:View){
        // Large-font compact profile uses vertical buttons, never squeezed targets.
        if(viewport.available<268 || config.fontScale>=1.25f && viewport.available<420){add(parent,first);add(parent,second)}else{
            val r=row();r.addView(first,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));r.addView(second,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=dp(12)});add(parent,r)
        }
    }
    // Responsive task split uses the approved landscape instruction/scene arrangement.
    val taskSplit get()=viewport.width>=540 && viewport.width>viewport.height
    private val taskLeadDp get()=minOf(220,((viewport.available-ApprovedUi.gapDp)*0.32f).toInt())
    val taskBoardDp get()=if(taskSplit)viewport.available-ApprovedUi.gapDp-taskLeadDp else viewport.available
    fun taskColumns(parent:LinearLayout,instructions:(LinearLayout)->Unit,scene:(LinearLayout)->Unit){
        if(!taskSplit){instructions(parent);scene(parent);return}
        val strip=row().apply{gravity=Gravity.TOP}
        val lead=column().apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL}
        val board=column().apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL}
        instructions(lead);scene(board)
        strip.addView(lead,LinearLayout.LayoutParams(dp(taskLeadDp),ViewGroup.LayoutParams.WRAP_CONTENT))
        strip.addView(board,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=dp(ApprovedUi.gapDp)})
        add(parent,strip,0)
    }
    fun navButton(path:String,label:String,action:()->Unit):ImageButton=ImageButton(activity).apply{
        setImageBitmap(bitmap(path));scaleType=ImageView.ScaleType.FIT_CENTER
        background=ripple("white","edge");minimumWidth=dp(64);minimumHeight=dp(64)
        setPadding(dp(12),dp(12),dp(12),dp(12));contentDescription=label
        isFocusable=true;setOnClickListener{action()}
    }
    fun shell(title:String,backgroundArt:String?,back:(()->Unit)?,home:(()->Unit)?,backdrop:Boolean=false,footer:((LinearLayout)->Unit)?=null,body:(LinearLayout)->Unit):View{
        val root=column().apply{setBackgroundColor(color("cream"))}
        root.setOnApplyWindowInsetsListener{view,insets->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
        }
        val header=row().apply{setPadding(dp(viewport.margin),dp(6),dp(viewport.margin),dp(6));setBackgroundColor(color("cream"))}
        back?.let{header.addView(navButton("ui/icons/ico_002.webp","Quay lại",it),LinearLayout.LayoutParams(dp(64),dp(64)))}
        header.addView(text(title,28,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        home?.let{header.addView(navButton("ui/icons/ico_001.webp","Trang chủ",it),LinearLayout.LayoutParams(dp(64),dp(64)))}
        root.addView(header,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        val scroll=ScrollView(activity).apply{isFillViewport=true}
        val content=column().apply{setPadding(dp(viewport.margin),dp(4),dp(viewport.margin),dp(20))}
        if(backgroundArt!=null && !backdrop){val bg=image(backgroundArt);bg.scaleType=ImageView.ScaleType.FIT_CENTER;add(content,bg,0,if(viewport.wide)90 else 120)}
        body(content);scroll.addView(content,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        val panel=if(backdrop && backgroundArt!=null)FrameLayout(activity).apply{
            addView(image(backgroundArt).apply{scaleType=ImageView.ScaleType.FIT_CENTER;alpha=0.18f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},FrameLayout.LayoutParams(-1,-1))
            addView(scroll,FrameLayout.LayoutParams(-1,-1))
        }else scroll
        root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
        footer?.let{draw->val tray=column().apply{setPadding(dp(viewport.margin),0,dp(viewport.margin),dp(8));setBackgroundColor(color("cream"))};draw(tray);root.addView(tray,LinearLayout.LayoutParams(-1,-2))}
        return root
    }
    fun clock(hour:Int?,minute:Int=0):View=object:View(activity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=min(width,height)*0.44f
            paint.color=color("peach");c.drawCircle(cx,cy,r,paint);paint.color=color("cream");c.drawCircle(cx,cy,r*.90f,paint)
            paint.color=color("ink");paint.textAlign=Paint.Align.CENTER;paint.typeface=bold;paint.textSize=r*.19f
            for(i in 1..12){val a=Math.PI*i/6-Math.PI/2;val x=cx+(cos(a)*r*.72).toFloat();val y=cy+(sin(a)*r*.72).toFloat();c.drawText(i.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)}
            if(hour!=null){paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=r*.055f;paint.color=color("navy");val ma=Math.PI*minute/30-Math.PI/2;c.drawLine(cx,cy,cx+(cos(ma)*r*.63).toFloat(),cy+(sin(ma)*r*.63).toFloat(),paint)
                val a=Math.PI*(hour+minute/60.0)/6-Math.PI/2;paint.color=color("pink");paint.strokeWidth=r*.075f;c.drawLine(cx,cy,cx+(cos(a)*r*.46).toFloat(),cy+(sin(a)*r*.46).toFloat(),paint)}
            paint.color=color("ink");c.drawCircle(cx,cy,r*.045f,paint)
        }
        init{contentDescription="Mặt đồng hồ";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
    }
    fun clear(){cache.evictAll()}
}
