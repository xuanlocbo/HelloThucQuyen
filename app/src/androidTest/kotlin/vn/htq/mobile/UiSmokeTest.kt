package vn.htq.mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.os.ParcelFileDescriptor
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
            // Representative rendering of the remaining general screens. The private Page
            // constructor is accessed only from instrumentation; no test route is shipped.
            val pageClass=MainActivity::class.java.declaredClasses.single{it.simpleName=="Page"}
            val pageCtor=pageClass.getDeclaredConstructor(String::class.java,String::class.java,Int::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!).apply{isAccessible=true}
            val push=MainActivity::class.java.getDeclaredMethod("push",pageClass).apply{isAccessible=true}
            fun preview(screen:String,ref:String="",label:String){
                scenario.onActivity{a=it;push.invoke(it,pageCtor.newInstance(screen,ref,0,false))};i.waitForIdleSync()
                await(label);if(screen!="SCR-033")assertNull("Rendering failed: $screen",find("Bé thử lại nhé!"))
                if(screen=="SCR-033")tap("Về nhà") else tap("Trang chủ")
            }
            val sampleLesson=ui.lessons.values.first{it.intro.isNotEmpty()};val sampleTopic=ui.topics.getValue(sampleLesson.topic)
            val sampleArea=ui.areas.single{sampleTopic.card.id in it.topics};val sampleLevel=ui.levels.getValue(sampleLesson.levels.first());val sampleTask=sampleLevel.tasks.first()
            val letter=ui.letters.first();val word=ui.vocabulary.first{!it.optional};val phrase=ui.phrases.first()
            val story=ui.stories.values.first{it.screen=="SCR-016"};val song=ui.stories.values.first{it.screen=="SCR-017"}
            preview("SCR-004",sampleArea.card.id,sampleArea.card.title)
            preview("SCR-005",sampleTopic.card.id,sampleTopic.card.title)
            preview("SCR-006",sampleLesson.card.id,"Chơi")
            preview("SCR-007",sampleLesson.card.id,sampleLevel.title)
            preview("SCR-008",sampleLesson.card.id,"Tranh khám phá")
            preview("SCR-009",label="Chữ cái")
            preview("SCR-010",letter.id,letter.word)
            preview("SCR-011",label=ui.numbers.first().label)
            preview("SCR-012",label=ui.englishGroups.first().title)
            preview("SCR-013",word.lesson,word.text)
            preview("SCR-014",phrase.lesson,phrase.text)
            preview("SCR-015",label="Truyện, thơ và bài hát")
            preview("SCR-016",story.id,story.scenes.first().text)
            preview("SCR-017",song.id,song.scenes.first().text)
            preview("SCR-018",label="Bé vui chơi")
            preview("SCR-019",sampleTask,"Chơi tiếp")
            preview("SCR-020",sampleTask,"Chọn bài khác")
            preview("SCR-021",sampleLevel.id,"Về bài học")
            preview("SCR-022",sampleLevel.id,"Để sau")
            preview("SCR-033",label="Bé thử lại nhé!")
            // Parent screens are tested through the actual adult gate, not bypassed.
            tap("Phụ huynh",48)
            val adultInput=await("Kết quả phép tính") as EditText
            i.runOnMainSync{
                val challenge=walk(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}.first{it.matches(Regex("[0-9]+ \\+ [0-9]+ = \\?"))}
                adultInput.setText(Regex("[0-9]+").findAll(challenge).sumOf{it.value.toInt()}.toString())
            }
            tap("Xác nhận",48);await("Theo dõi học tập")
            tap("Theo dõi học tập",48);await("Theo dõi học tập");tap("Quay lại")
            for(label in listOf("Cài đặt âm thanh","Thời lượng và nghỉ","Hỗ trợ thao tác")){
                tap(label,48);assertFalse(await("Lưu cài đặt").isEnabled);tap("Quay lại")
            }
            tap("Đặt lại tiến trình",48);tap("Tiếp tục xác nhận",48);assertFalse(await("Xác nhận đặt lại").isEnabled);tap("Hủy",48);tap("Quay lại")
            tap("Thông tin ứng dụng",48);await("Thông tin ứng dụng");tap("Trang chủ")
            // Render one source-selected task from every distinct task-screen template. This
            // checks the native renderer, not scoring or end-to-end navigation for all 660 tasks.
            val open=MainActivity::class.java.getDeclaredMethod("openTask",String::class.java,Boolean::class.javaPrimitiveType!!).apply{isAccessible=true}
            fun renderApprovedTask(t:UiTask){scenario.onActivity{a=it;open.invoke(it,t.id,true)};i.waitForIdleSync()}
            val samples=ui.tasks.values.groupBy{it.screen}.values.map{group->group.firstOrNull{it.ready}?:group.first()}
            assertEquals(36,samples.size)
            for(t in samples){
                renderApprovedTask(t)
                if(t.prelude)await("Nghe trước nhé!") else await(t.prompt)
                assertNull("Rendering failed for ${t.id}",find("Bé thử lại nhé!"))
                assertFullyVisible("Trang chủ")
                i.runOnMainSync{val d=a.resources.displayMetrics.density;for(v in walk(a.window.decorView).filter{it.isClickable&&it.isEnabled&&it.visibility==View.VISIBLE&&(it is Button||it is ImageButton||(it is LinearLayout&&it.contentDescription!=null))}){
                    assertTrue("Narrow control ${t.id}: ${v.contentDescription}",v.width/d>=55.5f)
                    assertTrue("Short control ${t.id}: ${v.contentDescription}",v.height/d>=55.5f)
                }}
            }
            // All prelude tasks must remain locked without a playback-complete event.
            for(t in ui.tasks.values.filter{it.prelude}){
                renderApprovedTask(t);await("Nghe trước nhé!");assertFalse(await("Tiếp tục").isEnabled)
                assertNull("Question exposed before listening ${t.id}",find(t.prompt))
                assertNull(find("Gợi ý"));assertNull(find("Xong"))
            }
            val pairTask=ui.tasks.getValue("05.03-L2-Q02")
            renderApprovedTask(pairTask)
            fun waitProfile(condition:(Configuration)->Boolean){
                val deadline=SystemClock.elapsedRealtime()+30000
                do{scenario.onActivity{a=it};i.waitForIdleSync();if(condition(a.resources.configuration)&&find(pairTask.prompt)!=null)return;SystemClock.sleep(100)}while(SystemClock.elapsedRealtime()<deadline)
                fail("Requested Android configuration did not apply: ${a.resources.configuration}")
            }
            fun verifyPair(split:Boolean){
                val prompt=await(pairTask.prompt)
                i.runOnMainSync{
                    val scene=walk(a.window.decorView).first{it.contentDescription?.toString()?.startsWith("Thẻ nguồn ")==true}
                    val left=IntArray(2);val right=IntArray(2);prompt.getLocationOnScreen(left);scene.getLocationOnScreen(right)
                    if(split)assertTrue("Instruction and scene not split on landscape",right[0]>=left[0]+prompt.width)
                    else assertTrue("Portrait scene must follow instruction",right[1]>=left[1]+prompt.height)
                }
                assertCards("Thẻ nguồn ",3);assertCards("Thẻ đích ",3);assertFullyVisible("Trang chủ")
            }
            scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            waitProfile{it.orientation==Configuration.ORIENTATION_LANDSCAPE&&it.screenWidthDp>=540}
            verifyPair(true)
            // Reconfigure only the same CI emulator, and restore settings. Do not boot another
            // emulator, rerun the suite, or collect screenshots for these targeted checks.
            if(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk",ignoreCase=true)){
                fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText().trim()}
                val oldFont=shell("settings get system font_scale").takeIf{it.matches(Regex("[0-9.]+"))}
                val oldSize=Regex("Override size: ([0-9]+x[0-9]+)").find(shell("wm size"))?.groupValues?.get(1)
                val oldDensity=Regex("Override density: ([0-9]+)").find(shell("wm density"))?.groupValues?.get(1)
                try{
                    shell("settings put system font_scale 1.3")
                    waitProfile{it.orientation==Configuration.ORIENTATION_LANDSCAPE&&it.screenWidthDp>=540&&it.fontScale>=1.25f}
                    verifyPair(true)
                    scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                    waitProfile{it.orientation==Configuration.ORIENTATION_PORTRAIT&&it.fontScale>=1.25f}
                    verifyPair(false)
                    shell("wm density 160");shell("wm size 800x1024")
                    waitProfile{it.orientation==Configuration.ORIENTATION_PORTRAIT&&it.screenWidthDp>=760&&it.fontScale>=1.25f}
                    verifyPair(false)
                    tap("Trang chủ");assertFullyVisible("Khu vườn");assertFullyVisible("Phụ huynh")
                }finally{
                    shell(if(oldSize==null)"wm size reset" else "wm size $oldSize")
                    shell(if(oldDensity==null)"wm density reset" else "wm density $oldDensity")
                    shell(if(oldFont==null)"settings delete system font_scale" else "settings put system font_scale $oldFont")
                }
            }else tap("Trang chủ")

        } finally {scenario.close();i.waitForIdleSync();if(had)prefs.edit().putBoolean("welcome_seen",previous).commit()else prefs.edit().remove("welcome_seen").commit()}
    }
}
