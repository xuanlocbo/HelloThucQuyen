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
import android.widget.CompoundButton
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
        val finalQa=InstrumentationRegistry.getArguments().getString("htqFinalQa")=="true"
        val homeQa=InstrumentationRegistry.getArguments().getString("htqHomeQa")=="true"
        fun qaShell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText().trim()}
        if(finalQa){
            assertEquals("1",qaShell("settings get global airplane_mode_on"))
            @Suppress("DEPRECATION")
            val permissions=ctx.packageManager.getPackageInfo(ctx.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
            val forbidden=setOf("android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE","android.permission.CAMERA","android.permission.RECORD_AUDIO","android.permission.ACCESS_FINE_LOCATION","android.permission.ACCESS_COARSE_LOCATION","com.android.vending.BILLING","com.google.android.gms.permission.AD_ID")
            assertTrue("Unexpected permission: ${permissions.intersect(forbidden)}",permissions.intersect(forbidden).isEmpty())
            android.util.Log.i("HTQ_UI_QA","FINAL_OFFLINE_PASS airplane=on forbidden_permissions=none")
        }
        val catalog=Catalog.load(object:AssetSource{override fun open(relative:String)=ctx.assets.open("htq/"+canonicalPath(relative))})
        val ui=ctx.assets.open("htq_ui/content.json").bufferedReader(Charsets.UTF_8).use{UiContent.parse(it.readText(),catalog.paths)}
        assertEquals(660,ui.tasks.size);assertEquals(39,ui.tasks.values.count{it.prelude});assertTrue(ui.tasks.values.filter{it.prelude}.all{it.groups.isEmpty()})
        for (face in listOf("Regular","Bold")) {
            val p=Paint().apply{typeface=Typeface.createFromAsset(ctx.assets,"htq-font/NotoSans-$face.ttf")}
            for (c in "ĂăÂâĐđÊêÔôƠơƯưẮắẰằẲẳẴẵẶặẤấẦầẨẩẪẫẬậẾếỀềỂểỄễỆệỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỨứỪừỬửỮữỰự")assertTrue("Missing glyph $c",p.hasGlyph(c.toString()))
        }
        val prefs=ctx.getSharedPreferences("htq_ui_preferences",0)
        val had=prefs.contains("welcome_seen");val previous=prefs.getBoolean("welcome_seen",false)
        val originalSettings=ParentPreferences.load(prefs)
        assertTrue(ParentPreferences().save(prefs))
        assertTrue(prefs.edit().remove("welcome_seen").commit())
        ctx.deleteDatabase("htq_progress.db")
        val launchAt=SystemClock.elapsedRealtime()
        var scenario=ActivityScenario.launch<MainActivity>(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var a:MainActivity
        var lastAction="launch"
        try {
            scenario.onActivity{a=it}
            fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
            fun find(label:String,clickable:Boolean=false):View? {
                var out:View?=null
                i.runOnMainSync{out=walk(a.window.decorView).firstOrNull{v->v.visibility==View.VISIBLE && (!clickable || v.isClickable) && ((v is TextView && v.text.toString()==label)||v.contentDescription?.toString()==label)}}
                return out
            }
            fun await(label:String,clickable:Boolean=false):View {
                lastAction="await $label (clickable=$clickable)"
                val deadline=SystemClock.elapsedRealtime()+20000
                do {i.waitForIdleSync();find(label,clickable)?.let{return it};SystemClock.sleep(100)}while(SystemClock.elapsedRealtime()<deadline)
                var labels=emptyList<String>()
                i.runOnMainSync{labels=walk(a.window.decorView).filter{it.visibility==View.VISIBLE}.mapNotNull{v->
                    (if(v is TextView)v.text?.toString()else v.contentDescription?.toString())?.takeIf{it.isNotBlank()}
                }.distinct().take(40)}
                fail("Missing visible view: $label; current labels: "+labels.joinToString(" | "));error("Missing view")
            }
            fun tap(label:String,minimum:Int=56){
                val v=await(label,true);lastAction="tap $label";i.runOnMainSync{
                    assertTrue("Disabled $label",v.isEnabled);assertTrue("Not clickable $label",v.isClickable)
                    val density=a.resources.displayMetrics.density
                    assertTrue("Width $label ${v.width/density}",v.width/density>=minimum-0.5f)
                    assertTrue("Height $label ${v.height/density}",v.height/density>=minimum-0.5f)
                    if(v is CompoundButton){
                        // CompoundButton toggles before delegating to View.performClick();
                        // a checked-change listener is not a View.OnClickListener.
                        val before=v.isChecked
                        v.performClick()
                        assertEquals("Switch did not toggle: $label",!before,v.isChecked)
                    }else assertTrue("Click was not handled: $label",v.performClick())
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
            fun shot(name:String){
                if(!finalQa||homeQa&&name !in setOf("01-home","14-home-tablet","15-home-landscape"))return
                require(name.matches(Regex("[a-z0-9-]+")))
                i.waitForIdleSync();SystemClock.sleep(200)
                val output=qaShell("screencap -p /data/local/tmp/htq-qa/$name.png")
                assertTrue("Screenshot command failed: $output",output.isBlank())
            }
            await("Bắt đầu")
            val welcomeReadyMs=SystemClock.elapsedRealtime()-launchAt
            tap("Bắt đầu")
            assertFullyVisible("Khu vườn");assertFullyVisible("Phụ huynh")
            if(finalQa&&a.resources.configuration.screenWidthDp in 300..359&&a.resources.configuration.fontScale<1.15f)
                ui.areas.forEach{assertFullyVisible(it.card.title)}
            shot("01-home")
            if(homeQa){
                // Dispatch actual screen coordinates to prove all artwork controls have the
                // right hit area and retain their catalog-bound routes, not just performClick.
                fun touchHome(label:String){
                    val v=await(label,true);val rect=Rect()
                    i.runOnMainSync{
                        assertTrue("Home touch target is not visible: $label",v.getGlobalVisibleRect(rect))
                        val d=a.resources.displayMetrics.density
                        assertTrue("Home target too small: $label",rect.width()/d>=55.5f&&rect.height()/d>=55.5f)
                        assertTrue(v is ImageButton)
                        assertTrue(v.contentDescription.toString()==label)
                    }
                    val now=SystemClock.uptimeMillis()
                    for(kind in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                        val event=android.view.MotionEvent.obtain(now,SystemClock.uptimeMillis(),kind,rect.exactCenterX(),rect.exactCenterY(),0)
                        try{i.sendPointerSync(event)}finally{event.recycle()}
                    }
                    i.waitForIdleSync()
                }
                ui.areas.forEach{area->
                    touchHome(area.card.title);await("Trang chủ",true)
                    if(area.card.id=="AREA-04")await(ui.englishGroups.first().title,true)
                    else await(ui.topics.getValue(area.pages.first().first()).card.title,true)
                    tap("Trang chủ")
                }
                touchHome("Khu vườn");await("Túi đồ",true);tap("Trang chủ")
                touchHome("Phụ huynh");await("Kết quả phép tính");assertNull(find("Theo dõi học tập"));tap("Trang chủ")
                android.util.Log.i("HTQ_UI_QA","APPROVED_HOME_ROUTE_PASS actual_touches=8 original_routes=true")
            }
            tap("Khu vườn");await("Túi đồ");tap("Túi đồ");await("Túi đồ");tap("Trang chủ")
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
            // Real touch response, validation, persistence and reward. Source answers are read in tests only.
            fun tapId(id:String){
                lastAction="tap source card"
                var selected:View?=null
                i.runOnMainSync{selected=walk(a.window.decorView).firstOrNull{it.tag==id&&it.isClickable&&it.isEnabled}}
                val v=requireNotNull(selected){"No interactive card $id"}
                i.runOnMainSync{
                    val density=a.resources.displayMetrics.density
                    assertTrue("Card target below 56dp",v.width/density>=55.5f&&v.height/density>=55.5f)
                    assertTrue(v.performClick())
                };i.waitForIdleSync()
            }
            fun completions(id:String):Int{
                val db=LocalProgress(ctx,catalog)
                return try{db.readableDatabase.rawQuery("SELECT COUNT(*) FROM completions WHERE task_id=?",arrayOf(id)).use{c->c.moveToFirst();c.getInt(0)}}finally{db.close()}
            }
            val select=ui.tasks.getValue("01.01-L1-Q01")
            val sourceRules=catalog.open("engine/v52/assessment_internal.json").bufferedReader().use{Json.parse(it.readText()).arr().map{v->v.obj()}.associateBy{it.s("task_id")}}
            val right=sourceRules.getValue(select.id).o("rule").s("accepted_id")
            val wrong=select.choiceSlots.first{it!=right}
            openTask(select.id)
            if(finalQa)assertFullyVisible("Xong")
            shot("02-learning")
            val before=completions(select.id)
            assertFalse(await("Xong").isEnabled);tapId(wrong);tap("Xong");await("Bé thử thêm một lần nhé!");assertEquals(before,completions(select.id))
            tapId(right);scenario.recreate();scenario.onActivity{a=it};await(select.prompt);assertTrue(await("Xong").isEnabled)
            tap("Xong");await("Bé làm tốt lắm!");assertEquals(1,completions(select.id));tap("Tiếp tục");tap("Bỏ qua")
            await("Về bài học");tap("Xem quà của bé");tap("Để sau");tap("Trang chủ")
            tap("Khu vườn");await("Xem quà của bé");tap("Túi đồ");tap("Xem quà của bé")
            scenario.recreate();scenario.onActivity{a=it};await("Mở quà")
            tap("Mở quà");await("Bé đã nhận quà!")
            if(finalQa)assertFullyVisible("Túi đồ")
            shot("03-reward");tap("Túi đồ");await("Trong túi: 1");assertNull(find("Xem quà của bé"));tap("Trang chủ")
            tap("Khu vườn");tap("Đặt vật");tap("Đặt vật 1");tap("Chọn vị trí bằng nút");tap("Chính giữa")
            fun checkGardenPosition(x:Double,y:Double){
                val store=LocalProgress(ctx,catalog)
                try{
                    val placed=store.gardenEntries().single{it.task==select.id}
                    assertEquals(x,placed.x!!,0.000001);assertEquals(y,placed.y!!,0.000001)
                    assertEquals(1,store.inventory().values.sum())
                }finally{store.close()}
            }
            checkGardenPosition(0.5,0.5)
            tap("Di chuyển vật");tap("Di chuyển vật 1");tap("Chọn vị trí bằng nút");tap("Trên trái")
            checkGardenPosition(1.0/6,1.0/6);tap("Hoàn tác");checkGardenPosition(0.5,0.5)
            scenario.recreate();scenario.onActivity{a=it};await("Ngắm vườn");checkGardenPosition(0.5,0.5);shot("04-garden")
            if(finalQa){
                scenario.close()
                scenario=ActivityScenario.launch<MainActivity>(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scenario.onActivity{a=it}
                await("Chơi tiếp nhé?");tap("Trang chủ")
                if(homeQa){tap("Chơi tiếp");await("Chọn bài khác");tap("Trang chủ")}
                tap("Khu vườn")
                checkGardenPosition(0.5,0.5)
                assertEquals(1,completions(select.id))
                android.util.Log.i("HTQ_UI_QA","FINAL_ACTIVITY_REOPEN_PASS completion_and_placement_preserved=true")
            }
            tap("Túi đồ");await("Đã đặt: 1");await("Trong túi: 0");tap("Quay lại")
            tap("Cất vật");tap("Cất vật 1");tap("Túi đồ");await("Trong túi: 1");tap("Quay lại")
            tap("Hoàn tác");tap("Túi đồ");await("Đã đặt: 1");tap("Trang chủ")
            openTask(select.id);tapId(right);tap("Xong");await("Bé làm tốt lắm!");assertEquals(1,completions(select.id));tap("Trang chủ")
            tap("Khu vườn");assertNull(find("Xem quà của bé"));checkGardenPosition(0.5,0.5);tap("Trang chủ")
            android.util.Log.i("HTQ_UI_QA","GARDEN_FLOW_PASS deferred=true move=true undo=true restored=true replay_no_reward=true")
            // Real Android MediaPlayer completion: cancelling must not unlock the question.
            val gated=ui.tasks.getValue("15.04-L1-Q01")
            fun openGated(){
                val l=ui.lessons.getValue(gated.lesson);val topic=ui.topics.getValue(l.topic);val area=ui.areas.single{l.topic in it.topics}
                tap(area.card.title);repeat(area.pages.indexOfFirst{l.topic in it}){tap("Sau")};tap(topic.card.title)
                repeat(topic.lessons.indexOf(l.card.id)/4){tap("Sau")};tap(l.card.title);tap("Chơi");tap(ui.levels.getValue(gated.level).title)
            }
            openGated();await("Nghe trước nhé!");assertNull(find(gated.prompt));assertFalse(await("Tiếp tục").isEnabled);shot("05-listening")
            tap("Nghe câu chuyện");tap("Dừng");assertFalse(await("Tiếp tục").isEnabled);assertNull(find(gated.prompt))
            tap("Nghe câu chuyện")
            val listenDeadline=SystemClock.elapsedRealtime()+20000
            while(!await("Tiếp tục").isEnabled&&SystemClock.elapsedRealtime()<listenDeadline){SystemClock.sleep(100);i.waitForIdleSync()}
            assertTrue("Actual playback did not unlock gate",await("Tiếp tục").isEnabled);assertNull(find(gated.prompt));tap("Tiếp tục");await(gated.prompt)
            scenario.recreate();scenario.onActivity{a=it};await("Nghe trước nhé!");assertNull(find(gated.prompt));assertFalse(await("Tiếp tục").isEnabled);tap("Trang chủ")
            // Actual navigation and measured preview cards.
            openTask("05.03-L2-Q02");assertCards("Thẻ nguồn ",3);assertCards("Thẻ đích ",3);shot("06-match")
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
            // Commit the first choice page, recreate, then undo. A stale page index cannot bypass its gate.
            for(n in 1..3)tapId("04.07-L3-Q02:o0$n")
            i.runOnMainSync{val next=walk(a.window.decorView).filterIsInstance<Button>().single{it.text.toString()=="Sau"&&it.isEnabled};assertTrue(next.performClick())}
            await("Thứ Năm")
            scenario.recreate();scenario.onActivity{a=it};await("Thứ Năm")
            tap("Hoàn tác");await("Thứ Ba");assertNull(find("Thứ Năm"))
            tap("Trang chủ")
            // Representative rendering of the remaining general screens. The private Page
            // constructor is accessed only from instrumentation; no test route is shipped.
            val pageClass=MainActivity::class.java.declaredClasses.single{it.simpleName=="Page"}
            val pageCtor=pageClass.getDeclaredConstructor(String::class.java,String::class.java,Int::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!).apply{isAccessible=true}
            val push=MainActivity::class.java.getDeclaredMethod("push",pageClass).apply{isAccessible=true}
            fun preview(screen:String,ref:String="",label:String){
                scenario.onActivity{a=it;push.invoke(it,pageCtor.newInstance(screen,ref,0,false))};i.waitForIdleSync()
                await(label);if(screen!="SCR-033")assertNull("Rendering failed: $screen",find("Bé thử lại nhé!"))
                when(screen){
                    "SCR-010"->{if(finalQa)assertFullyVisible("Nghe từ");shot("07-vietnamese")}
                    "SCR-011"->{if(finalQa)assertFullyVisible("Nghe số");shot("08-number")}
                    "SCR-013"->{if(finalQa)assertFullyVisible("Nghe tiếng Anh");shot("09-english")}
                    "SCR-016"->{if(finalQa)assertFullyVisible("Nghe kể chuyện");shot("10-story")}
                }
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
            fun enterParent(correct:Boolean=true){
                tap("Phụ huynh",48)
                val adultInput=await("Kết quả phép tính") as EditText
                i.runOnMainSync{
                    val challenge=walk(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}.first{it.matches(Regex("[0-9]+ \\+ [0-9]+ = \\?"))}
                    val total=Regex("[0-9]+").findAll(challenge).sumOf{it.value.toInt()}
                    adultInput.setText((if(correct)total else total+1).toString())
                }
                tap("Xác nhận",48)
                if(correct)await("Theo dõi học tập")else{await("Khu vườn");assertNull(find("Theo dõi học tập"))}
            }
            enterParent(false);enterParent()
            tap("Theo dõi học tập",48);await("Theo dõi học tập")
            val parentStore=LocalProgress(ctx,catalog)
            val openedCount=try{
                assertTrue(select.lesson in parentStore.openedLessons())
                parentStore.openedLessons().size
            }finally{parentStore.close()}
            await("Bài đã mở: $openedCount / ${ui.lessons.size}");shot("11-parent")
            tap("Quay lại")
            // Authorization must not survive recreation/backgrounding.
            scenario.recreate();scenario.onActivity{a=it};await("Khu vườn");assertNull(find("Theo dõi học tập"))
            enterParent()
            for(label in listOf("Cài đặt âm thanh","Thời lượng và nghỉ","Hỗ trợ thao tác")){
                tap(label,48);assertTrue(await("Lưu cài đặt").isEnabled)
                if(label=="Hỗ trợ thao tác")tap("Giảm chuyển động",48)
                if(label=="Cài đặt âm thanh"){
                    val slider=await("Âm lượng nhạc") as android.widget.SeekBar
                    i.runOnMainSync{slider.progress=35}
                }
                if(label=="Thời lượng và nghỉ"){
                    val slider=await("Thời lượng sử dụng đề xuất") as android.widget.SeekBar
                    i.runOnMainSync{slider.progress=0}
                }
                tap("Lưu cài đặt",48);await("Đã lưu cài đặt")
                if(label=="Cài đặt âm thanh")assertEquals(35,prefs.getInt("music_volume",-1))
                if(label=="Thời lượng và nghỉ")assertEquals(5,prefs.getInt("study_minutes",-1))
                if(label=="Hỗ trợ thao tác"){
                    assertTrue("Reduced motion setting was not persisted",prefs.getBoolean("reduced_motion",false))
                    assertTrue("Saved switch did not reflect setting",(await("Giảm chuyển động",true) as CompoundButton).isChecked)
                }
                tap("Quay lại")
            }
            tap("Đặt lại tiến trình",48);tap("Tiếp tục xác nhận",48);assertTrue(await("Xác nhận đặt lại").isEnabled);tap("Hủy",48);tap("Quay lại")
            assertEquals(1,completions(select.id));checkGardenPosition(0.5,0.5)
            tap("Thông tin ứng dụng",48);await("Thông tin ứng dụng")
            tap("Giấy phép chữ Noto Sans",48);await("Noto Sans — SIL Open Font License 1.1");tap("Quay lại")
            tap("Nguồn âm thanh",48);await("Nguồn âm thanh");tap("Quay lại");tap("Trang chủ")
            // Advance the isolated Activity's elapsed clock, not wall time, to check the actual rest boundary.
            val elapsedField=MainActivity::class.java.getDeclaredField("elapsedStudyMs").apply{isAccessible=true}
            scenario.onActivity{a=it;elapsedField.setLong(it,5*60_000L);push.invoke(it,pageCtor.newInstance("SCR-021",sampleLevel.id,0,false))}
            await("Đến giờ nghỉ một chút rồi!");tap("Về nhà nghỉ ngơi");await("Khu vườn")
            scenario.onActivity{assertEquals(0L,elapsedField.getLong(it))}
            enterParent();tap("Đặt lại tiến trình",48);tap("Tiếp tục xác nhận",48);tap("Xác nhận đặt lại",48)
            await("Đã đặt lại tiến trình")
            val resetStore=LocalProgress(ctx,catalog)
            try{assertTrue(resetStore.openedLessons().isEmpty());assertEquals(0,resetStore.completedCount());assertNull(resetStore.loadSession());assertTrue(resetStore.inventory().isEmpty());assertTrue(resetStore.gardenEntries().isEmpty())}finally{resetStore.close()}
            assertEquals(35,prefs.getInt("music_volume",-1));assertTrue(prefs.getBoolean("reduced_motion",false))
            tap("Về nhà",48);assertNull(find("Chơi tiếp"))
            android.util.Log.i("HTQ_UI_QA","PARENT_FLOW_PASS wrong_gate=true recreation_locks=true persisted_settings=true rest_boundary=true reset_cancel_and_confirm=true")
            // One final native rendering sweep of the locked task catalogue. Do not write
            // sessions or simulate grading for these view-only checks. Navigation and
            // persistence are exercised separately above by real user routes.
            val open=MainActivity::class.java.getDeclaredMethod("openTask",String::class.java,Boolean::class.javaPrimitiveType!!).apply{isAccessible=true}
            val replace=MainActivity::class.java.getDeclaredMethod("replace",pageClass).apply{isAccessible=true}
            fun renderApprovedTask(t:UiTask){scenario.onActivity{a=it;open.invoke(it,t.id,true)};i.waitForIdleSync()}
            val rendered=mutableSetOf<String>();val coveredScreens=mutableSetOf<String>()
            for(t in ui.tasks.values){
                scenario.onActivity{a=it;replace.invoke(it,pageCtor.newInstance(t.screen,t.id,0,false))}
                i.waitForIdleSync()
                i.runOnMainSync{
                    val views=walk(a.window.decorView).filter{it.visibility==View.VISIBLE}
                    fun has(label:String)=views.any{(it is TextView&&it.text.toString()==label)||it.contentDescription?.toString()==label}
                    assertFalse("Rendering failed for ${t.id}",has("Bé thử lại nhé!"))
                    assertTrue("Missing task content for ${t.id}",has(if(t.prelude)"Nghe trước nhé!" else t.prompt))
                    if(t.prelude){
                        assertFalse("Question exposed before listening ${t.id}",has(t.prompt))
                        val next=views.filterIsInstance<Button>().single{it.text.toString()=="Tiếp tục"}
                        assertFalse("Unlocked prelude ${t.id}",next.isEnabled)
                        assertFalse(has("Gợi ý"));assertFalse(has("Xong"))
                    }
                    val home=views.single{it is ImageButton&&it.contentDescription=="Trang chủ"};val bounds=Rect()
                    assertTrue("Home offscreen ${t.id}",home.getGlobalVisibleRect(bounds)&&bounds.width()>=home.width-1&&bounds.height()>=home.height-1)
                    val d=a.resources.displayMetrics.density
                    for(v in views.filter{it is Button||it is ImageButton||(it.isClickable&&it.isEnabled&&it is LinearLayout&&it.contentDescription!=null)}){
                        assertTrue("Narrow control ${t.id}: ${v.contentDescription}",v.width/d>=55.5f)
                        assertTrue("Short control ${t.id}: ${v.contentDescription}",v.height/d>=55.5f)
                    }
                }
                assertTrue(rendered.add(t.id));coveredScreens.add(t.screen)
            }
            assertEquals(ui.tasks.keys,rendered);assertEquals(36,coveredScreens.size)
            android.util.Log.i("HTQ_UI_QA","NATIVE_RENDER_PASS tasks=${rendered.size} templates=${coveredScreens.size} preludeLocked=39")
            // Source-provided samples remain separate; all glyphs in a word stay neutral.
            for((id,glyph) in listOf("05.06-L1-Q01" to "M","05.06-L1-Q02" to "H","05.06-L2-Q01" to "Ô","05.06-L3-Q01" to "H")){
                renderApprovedTask(ui.tasks.getValue(id));await("Chữ mẫu");await(glyph)
                i.runOnMainSync{
                    val samples=walk(a.window.decorView).filter{it is LinearLayout && it.contentDescription=="Chữ mẫu"};assertEquals(1,samples.size);assertTrue(walk(samples.single()).filterIsInstance<TextView>().any{it.text.toString()==glyph})
                    val rows=walk(a.window.decorView).filter{it is LinearLayout && it.tag=="htq_word_line"}.map{it as LinearLayout}
                    assertEquals(if(id=="05.06-L3-Q01")2 else 1,rows.size)
                    if(id=="05.06-L2-Q01"){
                        val row=rows.single();assertEquals(3,row.childCount)
                        val extra=(row.getChildAt(1).layoutParams as LinearLayout.LayoutParams).marginStart
                        val normal=(row.getChildAt(2).layoutParams as LinearLayout.LayoutParams).marginStart
                        assertTrue("Source word gap missing",extra>normal)
                    }
                    for(row in rows)for(n in 0 until row.childCount){val v=row.getChildAt(n);val d=a.resources.displayMetrics.density;assertTrue(v.width/d>=55.5f&&v.height/d>=55.5f)}
                }
            }
            renderApprovedTask(ui.tasks.getValue("01.05-L1-Q01"))
            assertFalse(await("Pha màu").isEnabled);assertNull(find("Bé chọn màu"))
            tapId("mix:0");tapId("mix:1");tap("Pha màu");await("Bé chọn màu");assertFalse(await("Xong").isEnabled)
            tap("Hoàn tác");assertNull(find("Bé chọn màu"));tap("Trang chủ")
            renderApprovedTask(ui.tasks.getValue("04.08-L3-Q01"));tap("Giờ +")
            assertTrue(await("Xong").isEnabled);tap("Hoàn tác");assertFalse(await("Xong").isEnabled);tap("Trang chủ")
            renderApprovedTask(ui.tasks.getValue("04.06-L2-Q01"));tap("Que 1");assertFalse(await("Xong").isEnabled);tap("Trang chủ")
            renderApprovedTask(ui.tasks.getValue("01.05-L3-Q01"));await("Bảng vẽ, chạm hoặc kéo ngón tay để tô");tap("Làm lại")
            tap("Hủy");await("Bảng vẽ, chạm hoặc kéo ngón tay để tô");tap("Trang chủ")
            renderApprovedTask(ui.tasks.getValue("04.04-L3-Q02"));tapId("04.04-L3-Q02:o01");tapId("04.04-L3-Q02:o02")
            assertTrue(await("Nghe nhịp").isEnabled);tap("Nghe nhịp");tap("Dừng");tap("Trang chủ")
            // Source simulation must not expose its illustrative result until explicit View.
            // Test actual native media in both settings, using the persisted adult preference.
            fun checkMotion(reduced:Boolean,resumeViewed:Boolean=false){
                renderApprovedTask(ui.tasks.getValue("08.03-L3-Q01"))
                if(resumeViewed){
                    // The first visit committed an explicit result-view event. Restoring that
                    // event is correct; restoring or starting playback automatically is not.
                    await("Xem minh họa")
                    assertFalse("Restored result can be viewed again",await("Xem kết quả").isEnabled)
                    assertNull("Restored session started animated playback",find("Minh họa chuyển động"))
                    assertNull("Restored session started static playback",find("Minh họa tĩnh"))
                    assertNull("Restored session has a playback stop control",find("Dừng minh họa"))
                    // Start a genuinely new attempt using the child's real reset control.
                    tap("Làm lại")
                }
                assertNull("Motion exposed before explicit result; reduced=$reduced",find("Xem minh họa"))
                assertTrue("Fresh result must be viewable; reduced=$reduced",await("Xem kết quả").isEnabled)
                tap("Xem kết quả");tap("Xem minh họa");await("Dừng minh họa")
                val staticOnly=reduced||android.os.Build.VERSION.SDK_INT<28
                val expected=if(staticOnly)"Minh họa tĩnh" else "Minh họa chuyển động"
                val unexpected=if(staticOnly)"Minh họa chuyển động" else "Minh họa tĩnh"
                await(expected);assertNull("Unexpected motion mode",find(unexpected))
                tap("Dừng minh họa");assertNull(find(expected));tap("Trang chủ")
            }
            checkMotion(true)
            enterParent();tap("Hỗ trợ thao tác",48)
            assertTrue((await("Giảm chuyển động",true) as CompoundButton).isChecked)
            tap("Giảm chuyển động",48);tap("Lưu cài đặt",48);await("Đã lưu cài đặt")
            assertFalse("Reduced motion setting was not switched off",prefs.getBoolean("reduced_motion",true))
            assertFalse((await("Giảm chuyển động",true) as CompoundButton).isChecked)
            tap("Trang chủ")
            checkMotion(false,resumeViewed=true)
            android.util.Log.i("HTQ_UI_QA","NATIVE_MOTION_MODES_PASS persisted=on,off restored_result=true auto_playback=false reset_relocks=true")
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
            verifyPair(true);shot("12-landscape")
            if(homeQa){
                tap("Trang chủ");assertFullyVisible("Khu vườn");assertFullyVisible("Phụ huynh")
                shot("15-home-landscape");renderApprovedTask(pairTask);verifyPair(true)
            }
            // Reconfigure only the same CI emulator and restore settings. Final QA captures
            // a bounded representative set, never an exhaustive bulk screenshot collection.
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
                    verifyPair(false);shot("13-tablet")
                    tap("Trang chủ");assertFullyVisible("Khu vườn");assertFullyVisible("Phụ huynh")
                    if(homeQa){
                        ui.areas.forEach{await(it.card.title,true)}
                        shot("14-home-tablet")
                        android.util.Log.i("HTQ_UI_QA","APPROVED_HOME_ADAPTIVE_PASS landscape=true tablet_large_text=true")
                    }
                }finally{
                    shell(if(oldSize==null)"wm size reset" else "wm size $oldSize")
                    shell(if(oldDensity==null)"wm density reset" else "wm density $oldDensity")
                    shell(if(oldFont==null)"settings delete system font_scale" else "settings put system font_scale $oldFont")
                }
            }else tap("Trang chủ")
            if(finalQa){
                val pssKb=android.os.Debug.getPss()
                android.util.Log.i("HTQ_UI_QA","FINAL_UI_METRICS welcome_ready_ms=$welcomeReadyMs pss_kb=$pssKb")
            }

        } catch(failure:Throwable){
            android.util.Log.e("HTQ_UI_QA","UiSmokeTest failed after $lastAction",failure)
            throw AssertionError("UiSmokeTest failed after $lastAction: "+(failure.message?:failure.javaClass.simpleName),failure)
        } finally {scenario.close();i.waitForIdleSync();ctx.deleteDatabase("htq_progress.db");originalSettings.save(prefs);if(had)prefs.edit().putBoolean("welcome_seen",previous).commit()else prefs.edit().remove("welcome_seen").commit()}
    }
}
