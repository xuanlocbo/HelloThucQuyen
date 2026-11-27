package vn.htq.mobile

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.AssetManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class HomeAction(val id:String,val label:String,val open:()->Unit)
internal data class HomeLayer(val id:String,val kind:String,val x:Int,val y:Int,val bitmap:Bitmap){
    val width get()=bitmap.width
    val height get()=bitmap.height
}

/** Lossless, independently decoded artwork cut from the final owner-approved image. */
internal class HomeArt private constructor(val width:Int,val height:Int,val scene:Bitmap,val layers:List<HomeLayer>){
    val byId=layers.associateBy{it.id}
    val buttons=layers.filter{it.kind=="button"}
    val decoration=layers.filter{it.kind!="button"}
    companion object{
        fun load(assets:AssetManager):HomeArt{
            val pictures=linkedMapOf<String,Bitmap>()
            var metadata:String?=null
            for(part in listOf("scene","header","tiles_a","tiles_b")){
                assets.open("htq_home/layers/$part.zip").use{raw->
                    ZipInputStream(raw).use{z->
                        while(true){
                            val entry=z.nextEntry?:break
                            require(!entry.isDirectory)
                            val bytes=z.readBytes()
                            when{
                                entry.name=="layout.json"->{require(metadata==null);metadata=bytes.toString(Charsets.UTF_8)}
                                entry.name.endsWith(".webp")->{
                                    val id=entry.name.removeSuffix(".webp")
                                    require(id !in pictures)
                                    pictures[id]=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)).apply{density=Bitmap.DENSITY_NONE}
                                }
                                else->error("Unexpected home art entry")
                            }
                            z.closeEntry()
                        }
                    }
                }
            }
            val m=JSONObject(requireNotNull(metadata))
            val width=m.getInt("width");val height=m.getInt("height")
            require(width==941&&height==1672)
            val scene=pictures.remove("scene")?:error("Missing independent scenery")
            require(scene.width==width&&scene.height==height)
            val rows=m.getJSONArray("layers")
            val layers=(0 until rows.length()).map{n->
                val o=rows.getJSONObject(n);val id=o.getString("id")
                val bitmap=pictures.remove(id)?:error("Missing artwork $id")
                val x=o.getInt("x");val y=o.getInt("y")
                require(bitmap.width==o.getInt("width")&&bitmap.height==o.getInt("height"))
                require(x>=0&&y>=0&&x+bitmap.width<=width&&y+bitmap.height<=height)
                HomeLayer(id,o.getString("kind"),x,y,bitmap)
            }
            require(pictures.isEmpty()&&layers.size==16&&layers.count{it.kind=="button"}==8)
            return HomeArt(width,height,scene,layers)
        }
    }
}

/**
 * Only SCR-003. No flattened home poster and no transparent hotspot buttons.
 * One scenery layer, eight independently drawn decorative layers, eight actual
 * native artwork buttons. Motion is stopped when the view loses focus/detaches.
 */
internal class ApprovedHome(private val activity:Activity){
    private val art by lazy{HomeArt.load(activity.assets)}
    private val fills=listOf("green","blue","peach","lilac","yellow","white","green","lilac")
    private val artBottom=listOf(675,675,950,950,1205,1205,1458,1458)
    private val headerArt by lazy{Bitmap.createBitmap(art.scene,0,0,art.width,500)}
    private val scaled=object:LruCache<String,Bitmap>(16*1024*1024){
        override fun sizeOf(key:String,value:Bitmap)=value.byteCount
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Retain the validated coverage-weighted downsampling, including premultiplied
    // alpha for the independent sprites. All 17 lossless layers keep original pixels.
    private fun smooth(source:Bitmap,key:String,width:Int,height:Int):Bitmap{
        if(source.width==width&&source.height==height)return source
        val cacheKey="$key:$width:$height"
        scaled.get(cacheKey)?.let{return it}
        val sw=source.width;val sh=source.height
        val result=if(width>=sw||height>=sh){
            Bitmap.createScaledBitmap(source,width,height,true)
        }else{
            val pixels=IntArray(sw*sh);source.getPixels(pixels,0,sw,0,0,sw,sh)
            val output=IntArray(width*height);val area=sw.toLong()*sh
            for(y in 0 until height){
                val y0=y*sh;val y1=(y+1)*sh
                for(x in 0 until width){
                    val x0=x*sw;val x1=(x+1)*sw
                    var alpha=0L;var red=0L;var green=0L;var blue=0L
                    for(sy in y0/height..(y1-1)/height){
                        val wy=minOf(y1,(sy+1)*height)-maxOf(y0,sy*height)
                        for(sx in x0/width..(x1-1)/width){
                            val wx=minOf(x1,(sx+1)*width)-maxOf(x0,sx*width)
                            val weight=wx.toLong()*wy
                            val c=pixels[sy*sw+sx];val aw=(c ushr 24)*weight
                            alpha+=aw;red+=((c ushr 16) and 255)*aw
                            green+=((c ushr 8) and 255)*aw;blue+=(c and 255)*aw
                        }
                    }
                    output[y*width+x]=if(alpha==0L)0 else Color.argb(
                        ((alpha+area/2)/area).toInt(),((red+alpha/2)/alpha).toInt(),
                        ((green+alpha/2)/alpha).toInt(),((blue+alpha/2)/alpha).toInt())
                }
            }
            Bitmap.createBitmap(output,width,height,Bitmap.Config.ARGB_8888)
        }
        result.density=Bitmap.DENSITY_NONE;scaled.put(cacheKey,result)
        return result
    }
    private fun drawArt(canvas:Canvas,source:Bitmap,key:String,w:Int,h:Int,fit:Boolean){
        if(w<=0||h<=0)return
        val ratio=minOf(w.toDouble()/source.width,h.toDouble()/source.height)
        val dw=if(fit)(source.width*ratio).roundToInt().coerceIn(1,w)else w
        val dh=if(fit)(source.height*ratio).roundToInt().coerceIn(1,h)else h
        val x=(w-dw)/2;val y=(h-dh)/2
        canvas.drawBitmap(smooth(source,key,dw,dh),null,Rect(x,y,x+dw,y+dh),paint)
    }
    private fun motionAllowed(w:GardenWidgets)=!w.reducedMotion&&(Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled())
    private fun highlight(w:GardenWidgets):android.graphics.drawable.Drawable{
        fun shape(fill:Int,stroke:Int=0)=GradientDrawable().apply{
            setColor(fill);cornerRadius=w.dp(22).toFloat()
            if(stroke!=0)setStroke(w.dp(3),stroke)
        }
        val focus=StateListDrawable().apply{
            addState(intArrayOf(android.R.attr.state_pressed),shape(0x1124664F,0))
            addState(intArrayOf(android.R.attr.state_focused),shape(0x1124664F,Color.WHITE))
            addState(intArrayOf(),shape(Color.TRANSPARENT))
        }
        return if(w.reducedMotion)focus else RippleDrawable(ColorStateList.valueOf(0x2824664F),focus,shape(Color.WHITE))
    }
    // Use the actual native click contract, including keyboard/accessibility clicks.
    // A bounded release animation finishes before navigating; repeated taps cannot
    // stack routes. Pending work is cancelled when the view detaches.
    private fun bindPress(v:View,w:GardenWidgets,a:HomeAction){
        var pending=false
        v.tag="htq_home:"+a.id;v.contentDescription=a.label
        v.isClickable=true;v.isFocusable=true;v.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        v.foreground=highlight(w)
        fun normal(){v.scaleX=1f;v.scaleY=1f}
        v.setOnTouchListener{_,event->
            if(motionAllowed(w)&&!pending&&v.isEnabled){
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{v.animate().cancel();v.animate().scaleX(.95f).scaleY(.95f).setDuration(75).setInterpolator(LinearInterpolator()).start()}
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{v.animate().cancel();v.animate().scaleX(1f).scaleY(1f).setDuration(95).setInterpolator(OvershootInterpolator(1.2f)).start()}
                }
            }
            false
        }
        v.setOnClickListener{
            if(!pending){
                if(!motionAllowed(w)){normal();a.open()}
                else{
                    pending=true;v.animate().cancel()
                    v.scaleX=minOf(v.scaleX,.96f);v.scaleY=minOf(v.scaleY,.96f)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(OvershootInterpolator(1.4f)).withEndAction{
                            if(pending){pending=false;normal();if(v.isAttachedToWindow&&v.hasWindowFocus())a.open()}
                        }.start()
                }
            }
        }
        v.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(view:View){}
            override fun onViewDetachedFromWindow(view:View){pending=false;view.animate().cancel();normal()}
        })
    }
    private fun imageButton(w:GardenWidgets,a:HomeAction,fit:Boolean=true)=object:ImageButton(activity){
        override fun onDraw(canvas:Canvas){
            drawArt(canvas,art.byId.getValue(a.id).bitmap,a.id,width,height,fit)
        }
    }.apply{
        // The drawable is real and independently backed by this button's artwork.
        setImageBitmap(art.byId.getValue(a.id).bitmap)
        scaleType=ImageView.ScaleType.CENTER
        background=null;imageTintList=null;setPadding(0,0,0,0);minimumHeight=w.dp(56)
        bindPress(this,w,a)
    }
    private fun artworkCard(w:GardenWidgets,a:HomeAction):View{
        val layer=art.byId.getValue(a.id);val n=art.buttons.indexOf(layer)
        val cut=(artBottom[n]-layer.y).coerceIn(1,layer.height)
        val illustration=Bitmap.createBitmap(layer.bitmap,0,0,layer.width,cut)
        return w.column().apply{
            background=w.rounded(fills[n],"edge");setPadding(w.dp(4),w.dp(4),w.dp(4),w.dp(4))
            minimumHeight=w.dp(56)
            w.add(this,ImageView(activity).apply{
                setImageBitmap(illustration);scaleType=ImageView.ScaleType.FIT_CENTER
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },0,if(w.viewport.width>w.viewport.height)84 else 112)
            w.add(this,w.text(a.label,22,true).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},4)
            bindPress(this,w,a)
        }
    }

    fun create(w:GardenWidgets,actions:List<HomeAction>,extras:LinearLayout):View{
        require(actions.map{it.id}==art.buttons.map{it.id}){"Approved home route order changed"}
        val root=w.column().apply{
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xff049af0.toInt(),0xff82e4ff.toInt(),0xff8bc72c.toInt()))
            setOnApplyWindowInsetsListener{view,insets->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
            }
        }
        val large=w.largeText||w.viewport.fontScale>=1.25f
        val reflow=large||w.viewport.width<300||w.viewport.width>w.viewport.height
        if(!reflow){
            val poster=Scene(w,actions)
            val scroll=object:ScrollView(activity){
                override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
                    poster.viewportHeight=MeasureSpec.getSize(heightMeasureSpec)
                    super.onMeasure(widthMeasureSpec,heightMeasureSpec)
                }
            }.apply{
                isFillViewport=true;isVerticalScrollBarEnabled=false
                addView(poster,ViewGroup.LayoutParams(-1,-2))
            }
            root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        }else{
            // Same independent artwork reflowed; large-text mode has real scaled labels.
            val scroll=ScrollView(activity).apply{isFillViewport=true}
            val body=w.column().apply{setPadding(w.dp(12),w.dp(4),w.dp(12),w.dp(12))}
            w.add(body,Scene(w,emptyList(),headerOnly=true),0,if(w.viewport.width>w.viewport.height)112 else 188)
            val views=actions.take(6).map{a->if(large)artworkCard(w,a)else imageButton(w,a)}
            if(large)w.grid(body,views,2,120)
            else views.chunked(2).forEach{items->
                val row=w.row()
                items.forEachIndexed{n,v->row.addView(v,LinearLayout.LayoutParams(0,w.dp(132),1f).apply{if(n>0)marginStart=w.dp(8)})}
                w.add(body,row,8)
            }
            scroll.addView(body,ViewGroup.LayoutParams(-1,-2));root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
            val bottom=w.column().apply{setPadding(w.dp(8),0,w.dp(8),w.dp(4))}
            if(large){
                val g=w.button(actions[6].label,"green",action=actions[6].open)
                val p=w.button(actions[7].label,"lilac",adult=true,action=actions[7].open)
                bindPress(g,w,actions[6]);bindPress(p,w,actions[7]);w.pair(bottom,g,p)
            }else{
                val row=w.row()
                actions.drop(6).forEachIndexed{n,a->row.addView(imageButton(w,a),LinearLayout.LayoutParams(0,w.dp(66),1f).apply{if(n>0)marginStart=w.dp(8)})}
                w.add(bottom,row,4)
            }
            root.addView(bottom,LinearLayout.LayoutParams(-1,-2))
        }
        if(extras.childCount>0){extras.setPadding(w.dp(8),0,w.dp(8),w.dp(4));root.addView(extras,LinearLayout.LayoutParams(-1,-2))}
        return root
    }

    private inner class Scene(private val w:GardenWidgets,actions:List<HomeAction>,private val headerOnly:Boolean=false):ViewGroup(activity){
        var viewportHeight=0
        private val sourceHeight=if(headerOnly)500 else art.height
        private val sceneBitmap=if(headerOnly)headerArt else art.scene
        private val picture=Rect()
        private val layers=art.decoration+if(headerOnly)emptyList()else art.buttons
        private val bounds=Array(layers.size){Rect()}
        private var animator:ValueAnimator?=null
        private var phase=0.0
        private val effects=Paint(Paint.ANTI_ALIAS_FLAG)
        init{
            setWillNotDraw(false);clipChildren=false;clipToPadding=false
            val routes=actions.associateBy{it.id}
            layers.forEach{layer->
                if(layer.kind=="button")addView(imageButton(w,routes.getValue(layer.id),fit=false))
                else addView(object:View(activity){
                    override fun onDraw(canvas:Canvas){drawArt(canvas,layer.bitmap,layer.id,width,height,false)}
                }.apply{
                    tag="htq_home_layer:"+layer.id
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
            tag=picture;contentDescription="Hello Thục Quyên";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
            val width=MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            val available=if(headerOnly)MeasureSpec.getSize(heightMeasureSpec)else viewportHeight
            val fit=(available.toDouble()*art.width/sourceHeight).roundToInt()
            val posterWidth=if(headerOnly)minOf(width,fit.coerceAtLeast(1))else minOf(width,maxOf(w.dp(300),fit))
            val posterHeight=(posterWidth.toDouble()*sourceHeight/art.width).roundToInt()
            val height=maxOf(posterHeight,available)
            val x=(width-posterWidth)/2;val y=(height-posterHeight)/2
            picture.set(x,y,x+posterWidth,y+posterHeight)
            for((n,layer) in layers.withIndex()){
                val dst=bounds[n]
                dst.set(x+(layer.x.toDouble()*posterWidth/art.width).roundToInt(),
                    y+(layer.y.toDouble()*posterHeight/sourceHeight).roundToInt(),
                    x+((layer.x+layer.width).toDouble()*posterWidth/art.width).roundToInt(),
                    y+((layer.y+layer.height).toDouble()*posterHeight/sourceHeight).roundToInt())
                getChildAt(n).measure(MeasureSpec.makeMeasureSpec(dst.width().coerceAtLeast(1),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dst.height().coerceAtLeast(1),MeasureSpec.EXACTLY))
            }
            setMeasuredDimension(width,resolveSize(height,heightMeasureSpec))
        }
        override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){bounds.forEachIndexed{n,q->getChildAt(n).layout(q.left,q.top,q.right,q.bottom)}}
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas)
            if(picture.width()>0&&picture.height()>0)
                canvas.drawBitmap(smooth(sceneBitmap,if(headerOnly)"header_scene"else"scene",picture.width(),picture.height()),null,picture,paint)
        }
        override fun dispatchDraw(canvas:Canvas){
            val save=canvas.save();canvas.clipRect(picture)
            super.dispatchDraw(canvas)
            if(animator!=null&&picture.width()>0){
                val s=picture.width().toFloat()/art.width
                // Gentle glints in the sky; never over lesson labels.
                fun glint(x:Float,y:Float,offset:Double){
                    val light=((sin(phase+offset)+1.0)/2.0).toFloat()
                    effects.color=Color.argb((100+145*light).toInt(),255,250,188)
                    val cx=picture.left+x*s;val cy=picture.top+y*s;val z=(3+6*light)*s
                    val star=Path().apply{moveTo(cx,cy-z);lineTo(cx+z*.28f,cy-z*.28f);lineTo(cx+z,cy);lineTo(cx+z*.28f,cy+z*.28f);lineTo(cx,cy+z);lineTo(cx-z*.28f,cy+z*.28f);lineTo(cx-z,cy);lineTo(cx-z*.28f,cy-z*.28f);close()}
                    canvas.drawPath(star,effects)
                }
                glint(820f,78f,0.0);glint(255f,105f,2.0)
                if(!headerOnly){
                    // A small native vector butterfly over the empty meadow, clear of controls.
                    val bx=picture.left+(850f+23f*sin(phase).toFloat())*s
                    val by=picture.top+(1585f+10f*sin(phase*2).toFloat())*s
                    val flap=(.55f+.45f*kotlin.math.abs(sin(phase*8).toFloat()))
                    val saved=canvas.save();canvas.translate(bx,by);canvas.rotate(-15f)
                    effects.color=0xfff9ae20.toInt()
                    canvas.drawOval(RectF(-17*s*flap,-13*s,-2*s,3*s),effects);canvas.drawOval(RectF(2*s,-13*s,17*s*flap,3*s),effects)
                    effects.color=0xffffdc69.toInt()
                    canvas.drawOval(RectF(-12*s*flap,0f,-1*s,11*s),effects);canvas.drawOval(RectF(1*s,0f,12*s*flap,11*s),effects)
                    effects.color=0xff78472c.toInt();canvas.drawOval(RectF(-2*s,-9*s,2*s,10*s),effects)
                    canvas.restoreToCount(saved)
                }
            }
            canvas.restoreToCount(save)
        }
        private fun resetDecor(){
            for((n,layer) in layers.withIndex())if(layer.kind!="button"){
                getChildAt(n).apply{translationX=0f;translationY=0f;scaleX=1f;scaleY=1f;alpha=1f}
            }
            phase=0.0;invalidate()
        }
        private fun stopMotion(){animator?.cancel();animator=null;resetDecor()}
        private fun updateMotion(){
            if(!isAttachedToWindow||!hasWindowFocus()||windowVisibility!=View.VISIBLE||!motionAllowed(w)){stopMotion();return}
            if(animator!=null)return
            animator=ValueAnimator.ofFloat(0f,1f).apply{
                duration=6200;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator()
                addUpdateListener{a->
                    if(!motionAllowed(w)){stopMotion();return@addUpdateListener}
                    phase=(a.animatedValue as Float)*2.0*PI
                    val s=picture.width().toFloat()/art.width
                    for((n,layer) in layers.withIndex()){
                        val v=getChildAt(n)
                        when(layer.id){
                            "girl"->v.translationY=(-2.6*sin(phase)).toFloat()*s
                            "rabbit"->v.translationY=(-2.8*sin(phase+1.1)).toFloat()*s
                            "cat"->v.translationY=(-2.8*sin(phase+2.0)).toFloat()*s
                            "cloud_left","cloud_right","cloud_low"->v.translationX=(4.0*sin(phase+layer.x*.003)).toFloat()*s
                            "stars"->{v.alpha=(.94+.06*sin(phase+1.5)).toFloat().coerceIn(.88f,1f);val k=(1.0+.008*sin(phase)).toFloat();v.scaleX=k;v.scaleY=k}
                        }
                    }
                    invalidate()
                }
            }
            animator?.start()
        }
        override fun onAttachedToWindow(){super.onAttachedToWindow();updateMotion()}
        override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);updateMotion()}
        override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(isAttachedToWindow)updateMotion()}
        override fun onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow()}
    }
}
