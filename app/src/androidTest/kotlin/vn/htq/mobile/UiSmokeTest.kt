package vn.htq.mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.htq.core.*
import vn.htq.core.ui.*

/** Real Android View smoke checks, not a claim of the complete 69-screen visual audit. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @Test fun launchAndMeasureNativeViews() {
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext
        val catalog=Catalog.load(object:AssetSource{override fun open(relative:String)=ctx.assets.open("htq/"+canonicalPath(relative))})
        val ui=ctx.assets.open("htq_ui/content.json").bufferedReader(Charsets.UTF_8).use{UiContent.parse(it.readText(),catalog.paths)}
        assertEquals(660,ui.tasks.size);assertEquals(39,ui.tasks.values.count{it.prelude});assertTrue(ui.tasks.values.filter{it.prelude}.all{it.groups.isEmpty()})
        for (face in listOf("Regular","Bold")) {
            val p=Paint().apply{typeface=Typeface.createFromAsset(ctx.assets,"htq-font/NotoSans-$face.ttf")}
            for (c in "ĂăÂâĐđÊêÔôƠơƯưẮắẰằẲẳẴẵẶặẤấẦầẨẩẪẫẬậẾếỀềỂểỄễỆệỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỨứỪừỬửỮữỰự")assertTrue("Missing glyph $c",p.hasGlyph(c.toString()))
        }
        val prefs=ctx.getSharedPreferences("htq_ui_preferences",0)
        val had=prefs.contains("welcome_seen");val previous=prefs.getBoolean("welcome_seen",false)
        assertTrue(prefs.edit().remove("welcome_seen").commit())
        val a=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
            fun find(label:String):View? {
                var out:View?=null
                i.runOnMainSync{out=walk(a.window.decorView).firstOrNull{v->v.visibility==View.VISIBLE && ((v is TextView && v.text.toString()==label)||v.contentDescription?.toString()==label)}}
                return out
            }
            fun await(label:String):View {
                val deadline=SystemClock.elapsedRealtime()+20000
                do {i.waitForIdleSync();find(label)?.let{return it};SystemClock.sleep(100)}while(SystemClock.elapsedRealtime()<deadline)
                fail("Missing visible view: $label");error("Missing view")
            }
            fun tap(label:String,minimum:Int=56){
                val v=await(label);i.runOnMainSync{
                    assertTrue("Disabled $label",v.isEnabled);assertTrue("Not clickable $label",v.isClickable)
                    val density=a.resources.displayMetrics.density
                    assertTrue("Width $label ${v.width/density}",v.width/density>=minimum-0.5f)
                    assertTrue("Height $label ${v.height/density}",v.height/density>=minimum-0.5f)
                    assertTrue(v.performClick())
                };i.waitForIdleSync()
            }
            fun screenshot(name:String){
                i.waitForIdleSync();SystemClock.sleep(300)
                val b=requireNotNull(i.uiAutomation.takeScreenshot())
                try{val folder=requireNotNull(ctx.getExternalFilesDir("qa"));assertTrue(folder.exists()||folder.mkdirs());File(folder,"$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{b.recycle()}
            }
            await("Bắt đầu");screenshot("welcome");tap("Bắt đầu")
            await("Khu vườn");screenshot("home");tap("Khu vườn");await("Túi đồ");screenshot("garden");tap("Túi đồ");await("Túi đồ");screenshot("inventory");tap("Trang chủ")
            await("Khu vườn");assertNull(find("6.1 - Native technical foundation"));assertNull(find("6.1 - Loading local content"))
        } finally {i.runOnMainSync{a.finish()};i.waitForIdleSync();if(had)prefs.edit().putBoolean("welcome_seen",previous).commit()else prefs.edit().remove("welcome_seen").commit()}
    }
}
