package vn.htq.mobile

import android.content.Intent
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
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
        val scenario=ActivityScenario.launch<MainActivity>(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var a:MainActivity
        try {
            scenario.onActivity{a=it}
            fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
            fun find(label:String,clickable:Boolean=false):View? {
                var out:View?=null
                i.runOnMainSync{out=walk(a.window.decorView).firstOrNull{v->v.visibility==View.VISIBLE && (!clickable || v.isClickable) && ((v is TextView && v.text.toString()==label)||v.contentDescription?.toString()==label)}}
                return out
            }
            fun await(label:String,clickable:Boolean=false):View {
                val deadline=SystemClock.elapsedRealtime()+20000
                do {i.waitForIdleSync();find(label,clickable)?.let{return it};SystemClock.sleep(100)}while(SystemClock.elapsedRealtime()<deadline)
                fail("Missing visible view: $label");error("Missing view")
            }
            fun tap(label:String,minimum:Int=56){
                val v=await(label,true);i.runOnMainSync{
                    assertTrue("Disabled $label",v.isEnabled);assertTrue("Not clickable $label",v.isClickable)
                    val density=a.resources.displayMetrics.density
                    assertTrue("Width $label ${v.width/density}",v.width/density>=minimum-0.5f)
                    assertTrue("Height $label ${v.height/density}",v.height/density>=minimum-0.5f)
                    assertTrue(v.performClick())
                };i.waitForIdleSync()
            }
            fun assertFullyVisible(label:String){
                val v=await(label,true)
                i.runOnMainSync{
                    val bounds=Rect()
                    assertTrue("Offscreen $label",v.getGlobalVisibleRect(bounds))
                    assertTrue("Clipped $label",bounds.width()>=v.width-1 && bounds.height()>=v.height-1)
                }
            }
            await("Bắt đầu");tap("Bắt đầu")
            assertFullyVisible("Khu vườn");assertFullyVisible("Phụ huynh");tap("Khu vườn");await("Túi đồ");tap("Túi đồ");await("Túi đồ");tap("Trang chủ")
            await("Khu vườn");assertNull(find("6.1 - Native technical foundation"));assertNull(find("6.1 - Loading local content"))
            fun openTask(id:String){
                val task=ui.tasks.getValue(id);val l=ui.lessons.getValue(task.lesson);val topic=ui.topics.getValue(l.topic)
                val area=ui.areas.single{l.topic in it.topics}
                tap(area.card.title);repeat(area.pages.indexOfFirst{l.topic in it}){tap("Sau")};tap(topic.card.title)
                repeat(topic.lessons.indexOf(l.card.id)/4){tap("Sau")};tap(l.card.title);tap("Chơi");tap(ui.levels.getValue(task.level).title)
                repeat(ui.levels.getValue(task.level).tasks.indexOf(id)){tap("Bỏ qua")}
                await(task.prompt)
            }
            fun assertCards(prefix:String,expected:Int){
                i.runOnMainSync {
                    val views=walk(a.window.decorView).filter{it.contentDescription?.toString()?.startsWith(prefix)==true}
                    assertEquals(prefix,expected,views.size)
                    val d=a.resources.displayMetrics.density
                    for(v in views){assertTrue("Card width $prefix",v.width/d>=55.5f);assertTrue("Card height $prefix",v.height/d>=55.5f)}
                }
            }
            // Actual navigation and measured preview cards, not interaction-engine grading.
            openTask("05.03-L2-Q02");assertCards("Thẻ nguồn ",3);assertCards("Thẻ đích ",3)
            i.runOnMainSync{
                val density=a.resources.displayMetrics.density
                val visible=walk(a.window.decorView).filter{it.contentDescription?.toString()?.startsWith("Thẻ nguồn ")==true}.any{v->val r=Rect();v.getGlobalVisibleRect(r)&&r.height()/density>=56f}
                assertTrue("Learning content is pushed out of viewport",visible)
            }
            tap("Sau");assertCards("Thẻ nguồn ",3);assertCards("Thẻ đích ",3)
            assertTrue(await("Trước").isEnabled);assertFalse(await("Sau").isEnabled)
            // A real Activity recreation must retain the shared pair page without unlocking any task gate.
            scenario.recreate();scenario.onActivity{a=it}
            await(ui.tasks.getValue("05.03-L2-Q02").prompt)
            assertCards("Thẻ nguồn ",3);assertCards("Thẻ đích ",3)
            assertTrue(await("Trước").isEnabled);assertFalse(await("Sau").isEnabled)
            tap("Trang chủ")
            openTask("04.02-L3-Q02");assertCards("Thẻ ",4);tap("Sau");assertCards("Thẻ ",4)
            assertFalse(await("Sau").isEnabled);tap("Trang chủ")
            openTask("04.07-L3-Q02");await("1 / 3");await("Thứ Hai");await("Thứ Ba");await("Thứ Tư")
            for(day in listOf("Thứ Năm","Thứ Sáu","Thứ Bảy","Chủ nhật"))assertNull(find(day))
            i.runOnMainSync{val next=walk(a.window.decorView).filter{it is TextView && it.text.toString()=="Sau"};assertEquals(2,next.size);assertTrue(next.none{it.isEnabled})}
            tap("Trang chủ")

        } finally {scenario.close();i.waitForIdleSync();if(had)prefs.edit().putBoolean("welcome_seen",previous).commit()else prefs.edit().remove("welcome_seen").commit()}
    }
}
