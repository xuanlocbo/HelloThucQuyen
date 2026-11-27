package vn.htq.mobile

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.roundToInt

internal data class HomeAction(val id:String,val label:String,val open:()->Unit)

/**
 * The user-approved SCR-003 composition, not a regenerated mockup.
 * Art is loaded once per Activity, outside the locked learning catalog.
 * The portrait poster is rendered exactly once, with native clickable regions
 * on top. Reflowed variants use the same artwork. Nothing in the lesson renderer changes.
 */
internal class ApprovedHome(private val activity:Activity) {
    private val art by lazy {
        activity.assets.open("htq_home/approved.webp").use{stream->
            requireNotNull(BitmapFactory.decodeStream(stream)).also{require(it.width==941&&it.height==1672)}
        }
    }
    private data class Tile(val box:Rect,val artBottom:Int,val fill:String)
    private val tiles=linkedMapOf(
        "AREA-01" to Tile(Rect(24,510,464,780),677,"green"),
        "AREA-02" to Tile(Rect(475,510,919,780),677,"blue"),
        "AREA-03" to Tile(Rect(24,785,464,1028),928,"peach"),
        "AREA-04" to Tile(Rect(475,785,919,1028),928,"lilac"),
        "AREA-05" to Tile(Rect(24,1035,464,1308),1194,"yellow"),
        "AREA-06" to Tile(Rect(475,1035,919,1308),1188,"white"),
        "garden" to Tile(Rect(24,1340,464,1539),1451,"green"),
        "parent" to Tile(Rect(475,1340,919,1539),1451,"lilac")
    )
    private val bitmaps=mutableMapOf<String,Bitmap>()
    private fun crop(key:String,box:Rect)=bitmaps.getOrPut(key){
        Bitmap.createBitmap(art,box.left,box.top,box.width(),box.height())
    }
    // Cache at final physical-pixel dimensions. Downsampling by a single
    // GPU bilinear tap loses detail when the ratio is about 3:1 on small displays.
    // Coverage-weighted area averaging removes that aliasing without changing art.
    private val scaled=object:LruCache<String,Bitmap>(12*1024*1024){
        override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
    }
    private fun smooth(source:Bitmap,key:String,width:Int,height:Int):Bitmap{
        if(source.width==width&&source.height==height)return source
        val cacheKey="$key:$width:$height"
        scaled.get(cacheKey)?.let{return it}
        val sw=source.width;val sh=source.height
        val result=if(width>=sw||height>=sh){
            Bitmap.createScaledBitmap(source,width,height,true)
        }else{
            val pixels=IntArray(sw*sh);source.getPixels(pixels,0,sw,0,0,sw,sh)
            val output=IntArray(width*height)
            val area=sw.toLong()*sh
            for(y in 0 until height){
                val y0=y*sh;val y1=(y+1)*sh
                for(x in 0 until width){
                    val x0=x*sw;val x1=(x+1)*sw
                    var red=0L;var green=0L;var blue=0L
                    for(sy in y0/height..(y1-1)/height){
                        val wy=minOf(y1,(sy+1)*height)-maxOf(y0,sy*height)
                        for(sx in x0/width..(x1-1)/width){
                            val wx=minOf(x1,(sx+1)*width)-maxOf(x0,sx*width)
                            val weight=wx.toLong()*wy
                            val c=pixels[sy*sw+sx]
                            red+=((c ushr 16) and 255)*weight
                            green+=((c ushr 8) and 255)*weight
                            blue+=(c and 255)*weight
                        }
                    }
                    output[y*width+x]=Color.rgb(((red+area/2)/area).toInt(),((green+area/2)/area).toInt(),((blue+area/2)/area).toInt())
                }
            }
            Bitmap.createBitmap(output,width,height,Bitmap.Config.ARGB_8888)
        }
        result.density=Bitmap.DENSITY_NONE
        scaled.put(cacheKey,result)
        return result
    }
    private fun highlight(w:GardenWidgets):android.graphics.drawable.Drawable {
        fun shape(fill:Int,stroke:Int=0)=GradientDrawable().apply{
            setColor(fill);cornerRadius=w.dp(22).toFloat()
            if(stroke!=0)setStroke(w.dp(3),stroke)
        }
        val focus=StateListDrawable().apply{
            addState(intArrayOf(android.R.attr.state_focused),shape(0x1124664F,Color.WHITE))
            if(w.reducedMotion)addState(intArrayOf(android.R.attr.state_pressed),shape(0x3324664F,Color.WHITE))
            addState(intArrayOf(),shape(Color.TRANSPARENT))
        }
        return if(w.reducedMotion)focus else RippleDrawable(ColorStateList.valueOf(0x4424664F),focus,shape(Color.WHITE))
    }
    private fun imageButton(w:GardenWidgets,a:HomeAction,paintArt:Boolean=true)=object:ImageButton(activity){
        override fun onDraw(canvas:Canvas){
            if(paintArt&&width>0&&height>0){
                val source=crop(a.id,tiles.getValue(a.id).box)
                val ratio=minOf(width.toDouble()/source.width,height.toDouble()/source.height)
                val dw=(source.width*ratio).roundToInt().coerceIn(1,width)
                val dh=(source.height*ratio).roundToInt().coerceIn(1,height)
                val x=(width-dw)/2;val y=(height-dh)/2
                canvas.drawBitmap(smooth(source,a.id,dw,dh),null,Rect(x,y,x+dw,y+dh),null)
            }
            // The portrait button has no duplicate bitmap. Its foreground
            // retains the focus/press highlight and Android accessibility.
        }
    }.apply{
        scaleType=ImageView.ScaleType.CENTER
        background=null;imageTintList=null;setPadding(0,0,0,0)
        foreground=highlight(w);contentDescription=a.label
        tag="htq_home:"+a.id
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable=true;isClickable=true;minimumHeight=w.dp(56)
        setOnClickListener{a.open()}
    }
    private fun artworkCard(w:GardenWidgets,a:HomeAction):View{
        val t=tiles.getValue(a.id)
        return w.column().apply{
            background=w.rounded(t.fill,"edge");foreground=highlight(w)
            setPadding(w.dp(4),w.dp(4),w.dp(4),w.dp(4))
            minimumHeight=w.dp(56);contentDescription=a.label;tag="htq_home:"+a.id
            isFocusable=true;isClickable=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            w.add(this,ImageView(activity).apply{
                setImageBitmap(crop(a.id+":illustration",Rect(t.box.left,t.box.top,t.box.right,t.artBottom)))
                scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },0,if(w.viewport.width> w.viewport.height)84 else 112)
            w.add(this,w.text(a.label,22,true).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},4)
            setOnClickListener{a.open()}
        }
    }
    fun create(w:GardenWidgets,actions:List<HomeAction>,extras:LinearLayout):View{
        require(actions.map{it.id}==tiles.keys.toList()){"Approved home route order changed"}
        val root=w.column().apply{
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xff15a9f2.toInt(),0xff82e4ff.toInt(),0xff8bc72c.toInt()))
            setOnApplyWindowInsetsListener{view,insets->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
            }
        }
        val large=w.largeText||w.viewport.fontScale>=1.25f
        val reflow=large||w.viewport.width<300||w.viewport.width>w.viewport.height
        if(!reflow){
            val poster=Poster(w,actions)
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
            // Same approved illustrations, reflowed for landscape/large-text use.
            // Labels remain real scaled Noto text in the accessibility variant.
            val scroll=ScrollView(activity).apply{isFillViewport=true}
            val body=w.column().apply{setPadding(w.dp(12),w.dp(4),w.dp(12),w.dp(12))}
            w.add(body,ImageView(activity).apply{
                setImageBitmap(crop("header",Rect(0,0,941,503)));scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription="Hello Thục Quyên"
            },0,if(w.viewport.width>w.viewport.height)112 else 188)
            val views=actions.take(6).map{a->if(large)artworkCard(w,a)else imageButton(w,a)}
            // Non-large reflow uses measured image tiles, not their intrinsic pixel size.
            if(large)w.grid(body,views,2,120)
            else{
                views.chunked(2).forEach{items->
                    val row=w.row()
                    items.forEachIndexed{n,v->row.addView(v,LinearLayout.LayoutParams(0,w.dp(132),1f).apply{if(n>0)marginStart=w.dp(8)})}
                    w.add(body,row,8)
                }
            }
            scroll.addView(body,ViewGroup.LayoutParams(-1,-2));root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
            val bottom=w.column().apply{setPadding(w.dp(8),0,w.dp(8),w.dp(4))}
            if(large){
                w.pair(bottom,w.button(actions[6].label,"green",action=actions[6].open),w.button(actions[7].label,"lilac",adult=true,action=actions[7].open))
            }else{
                val row=w.row()
                actions.drop(6).forEachIndexed{n,a->row.addView(imageButton(w,a),
                    LinearLayout.LayoutParams(0,w.dp(66),1f).apply{if(n>0)marginStart=w.dp(8)})}
                w.add(bottom,row,4)
            }
            root.addView(bottom,LinearLayout.LayoutParams(-1,-2))
        }
        // Resume and opt-in music are preserved even though the reference depicts the fresh home.
        if(extras.childCount>0){
            extras.setPadding(w.dp(8),0,w.dp(8),w.dp(4))
            root.addView(extras,LinearLayout.LayoutParams(-1,-2))
        }
        return root
    }
    private inner class Poster(private val w:GardenWidgets,private val actions:List<HomeAction>):ViewGroup(activity){
        var viewportHeight=0
        private val picture=Rect()
        private val bounds=Array(actions.size){Rect()}
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        init{
            setWillNotDraw(false)
            actions.forEach{addView(imageButton(w,it,paintArt=false))}
            // The live rectangle is also used by the pixel-fidelity check.
            tag=picture
            contentDescription="Hello Thục Quyên"
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
            val width=MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            val fit=(viewportHeight.toDouble()*art.width/art.height).roundToInt()
            // Never squash the 56dp child targets to fit a short window; scroll instead.
            val posterWidth=minOf(width,maxOf(w.dp(300),fit))
            val posterHeight=(posterWidth.toDouble()*art.height/art.width).roundToInt()
            val height=maxOf(posterHeight,viewportHeight)
            val x=(width-posterWidth)/2;val y=(height-posterHeight)/2
            picture.set(x,y,x+posterWidth,y+posterHeight)
            for((n,a) in actions.withIndex()){
                val b=tiles.getValue(a.id).box
                val dst=bounds[n]
                dst.set(x+(b.left.toDouble()*posterWidth/art.width).roundToInt(),
                    y+(b.top.toDouble()*posterHeight/art.height).roundToInt(),
                    x+(b.right.toDouble()*posterWidth/art.width).roundToInt(),
                    y+(b.bottom.toDouble()*posterHeight/art.height).roundToInt())
                getChildAt(n).measure(MeasureSpec.makeMeasureSpec(dst.width(),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dst.height(),MeasureSpec.EXACTLY))
            }
            setMeasuredDimension(width,resolveSize(height,heightMeasureSpec))
        }
        override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
            bounds.forEachIndexed{n,q->getChildAt(n).layout(q.left,q.top,q.right,q.bottom)}
        }
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas)
            if(picture.width()>0&&picture.height()>0){
                val frame=smooth(art,"poster",picture.width(),picture.height())
                canvas.drawBitmap(frame,null,picture,paint)
            }
        }
    }
}
