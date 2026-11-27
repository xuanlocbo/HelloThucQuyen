package vn.htq.mobile

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import vn.htq.core.MotionDecision

/** Explicit, silent playback of the approved demonstration. Nothing starts during render/restore. */
internal class MotionPreview(private val w:GardenWidgets,private val reduced:Boolean,
    private val allowed:()->Boolean,private val request:(Boolean)->MotionDecision,private val beforePlayback:()->Unit){
    private lateinit var content:LinearLayout
    private var running:Animatable?=null
    fun attach(parent:LinearLayout){
        w.add(parent,w.button("Xem minh họa","white",action=::play))
        content=w.column().apply{visibility=View.GONE}
        content.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(v:View)=Unit
            override fun onViewDetachedFromWindow(v:View){running?.stop();running=null}
        })
        w.add(parent,content,4)
    }
    fun stop(){
        running?.stop();running=null
        if(::content.isInitialized){content.removeAllViews();content.visibility=View.GONE}
    }
    private fun play(){
        if(!allowed()||!::content.isInitialized||!content.isAttachedToWindow)return
        beforePlayback();stop()
        val staticOnly=reduced||Build.VERSION.SDK_INT<28
        val media=request(staticOnly) as? MotionDecision.Media?:return
        if(!staticOnly&&Build.VERSION.SDK_INT>=28&&playAnimated(media.path))return
        val still=if(staticOnly)media else request(true) as? MotionDecision.Media?:return
        val frames=still.reducedSteps.ifEmpty{listOf(still.path)}
        showStep(frames,0)
    }
    @android.annotation.TargetApi(28)
    private fun playAnimated(path:String):Boolean=runCatching{
        val src=ImageDecoder.createSource(content.context.assets,"htq/$path")
        val drawable=ImageDecoder.decodeDrawable(src) as? AnimatedImageDrawable?:return false
        drawable.repeatCount=0
        val view=ImageView(content.context).apply{
            scaleType=ImageView.ScaleType.FIT_CENTER;setImageDrawable(drawable)
            contentDescription="Minh họa chuyển động"
        }
        content.visibility=View.VISIBLE
        w.add(content,view,0,240)
        w.add(content,w.button("Dừng minh họa","white",action=::stop))
        running=drawable;drawable.start();true
    }.getOrElse{stop();false}
    private fun showStep(frames:List<String>,index:Int){
        if(!allowed()||!content.isAttachedToWindow||index !in frames.indices){stop();return}
        content.removeAllViews();content.visibility=View.VISIBLE
        w.art(content,frames[index],240,"Minh họa tĩnh")
        if(frames.size>1){
            w.add(content,w.text("Bước ${index+1} / ${frames.size}",18),4)
            w.pair(content,w.button("Bước trước","white",enabled=index>0,action={showStep(frames,index-1)}),
                w.button("Bước sau","green",enabled=index<frames.lastIndex,action={showStep(frames,index+1)}))
        }
        w.add(content,w.button("Dừng minh họa","white",action=::stop))
    }
}
