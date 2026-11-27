package vn.htq.mobile

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
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
 * Eight actual native buttons draw individual artwork regions and use their
 * existing navigation callbacks. Nothing in the lesson renderer is changed.
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
    private fun imageButton(w:GardenWidgets,a:HomeAction,fit:ImageView.ScaleType)=ImageButton(activity).apply{
        val tile=tiles.getValue(a.id)
        setImageBitmap(crop(a.id,tile.box));scaleType=fit
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
            val views=actions.take(6).map{a->if(large)artworkCard(w,a)else imageButton(w,a,ImageView.ScaleType.FIT_CENTER)}
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
                actions.drop(6).forEachIndexed{n,a->row.addView(imageButton(w,a,ImageView.ScaleType.FIT_CENTER),
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
            actions.forEach{addView(imageButton(w,it,ImageView.ScaleType.FIT_XY))}
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
            super.onDraw(canvas);canvas.drawBitmap(art,null,RectF(picture),paint)
        }
    }
}
