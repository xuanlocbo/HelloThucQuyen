package vn.htq.mobile

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import vn.htq.core.*
import vn.htq.core.ui.*
import kotlin.random.Random

/** 6.2 candidate. Only verified presentation fields reach child views.
 *  Evaluation, reward grants and the remaining interaction engines are not simulated here.
 */
class MainActivity : Activity() {
    private data class Page(val screen:String,val ref:String="",val index:Int=0,val extra:Boolean=false)
    private class Foundation(val catalog:Catalog,val ui:UiContent,val progress:LocalProgress,val backend:AndroidAudio) {
        val audio=AudioArbiter(backend)
        val audioPolicy=TaskAudioPolicy(catalog)
        val navigator=Navigator(catalog,audio::stop)
        val sessions=SessionPolicy(catalog)
        val parent=ParentGate{SystemClock.elapsedRealtime()}
        init{backend.onFocusLost=audio::stop}
    }
    private lateinit var w:GardenWidgets
    private var f:Foundation?=null
    private val stack=ArrayList<Page>()
    private var closed=false
    private var resumed=false
    private var loading=false
    private var adultInvalidated=false
    private var errorReturn:Page?=null
    private var challengeA=0
    private var challengeB=0
    private var seed=0L
    private val groupPages=mutableMapOf<String,Int>()
    private val prefs by lazy { getSharedPreferences("htq_ui_preferences",MODE_PRIVATE) }
    private val parentScreens=setOf("SCR-025","SCR-026","SCR-027","SCR-028","SCR-029","SCR-030","SCR-031","SCR-032")
    private val page get()=stack.lastOrNull() ?: Page("SCR-003")
    private val appTitle get()=getString(R.string.app_name)

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);w=GardenWidgets(this)
        seed=if(prefs.contains("visual_seed"))prefs.getLong("visual_seed",0L) else Random.nextLong().also{prefs.edit().putLong("visual_seed",it).apply()}
        load(savedInstanceState?.getStringArrayList("ui_stack"))
    }
    private fun mount(v:View){setContentView(v);v.requestApplyInsets()}
    private fun load(restored:ArrayList<String>?=null){
        if(loading||closed)return
        loading=true
        mount(w.shell(appTitle,"art/img_bg_0026.webp",null,null){body->
            w.art(body,"art/img_chr_0066.webp",200)
            w.add(body,w.text("Chào bé!",24,true))
        })
        Thread {
            val result=runCatching{
                val catalog=Catalog.load(object:AssetSource{override fun open(relative:String)=assets.open("htq/"+canonicalPath(relative))})
                val ui=assets.open("htq_ui/content.json").bufferedReader(Charsets.UTF_8).use{UiContent.parse(it.readText(),catalog.paths)}
                require(ui.tasks.keys.all(catalog::hasTask));catalog to ui
            }
            runOnUiThread {
                loading=false
                if(closed||isFinishing)return@runOnUiThread
                result.fold(onSuccess={ (catalog,ui)->
                    val opened=runCatching{Foundation(catalog,ui,LocalProgress(applicationContext,catalog),AndroidAudio(this))}
                    opened.fold(onSuccess={base->
                        f=base;stack.clear();stack.add(Page("SCR-003"))
                        val decoded=restored?.mapNotNull(::decodePage).orEmpty()
                        if(decoded.isNotEmpty() && decoded.first().screen=="SCR-003" && decoded.none{it.screen in parentScreens} && decoded.all{validPage(base.ui,it)}){
                            stack.clear();stack.addAll(decoded)
                        }else if(!prefs.getBoolean("welcome_seen",false)) stack.add(Page("SCR-002"))
                        else runCatching{base.progress.loadSession()?.s("task_id")}.getOrNull()?.takeIf{it in ui.tasks}?.let{stack.add(Page("SCR-020",it))}
                        render()
                    },onFailure={showError(null)})
                },onFailure={showError(null)})
            }
        }.start()
    }
    private fun validPage(ui:UiContent,p:Page):Boolean=when(p.screen){
        "SCR-002","SCR-003","SCR-009","SCR-011","SCR-012","SCR-015","SCR-023","SCR-024","SCR-033"->true
        "SCR-018"->p.ref.isEmpty() || ui.tasks.values.any{it.screen==p.ref && (it.ready||it.prelude)}
        "SCR-004"->ui.areas.any{it.card.id==p.ref}
        "SCR-005"->p.ref in ui.topics
        "SCR-006","SCR-007","SCR-008","SCR-013","SCR-014"->p.ref in ui.lessons
        "SCR-010"->ui.letters.any{it.id==p.ref}
        "SCR-016","SCR-017"->ui.stories[p.ref]?.screen==p.screen
        "SCR-019","SCR-020"->p.ref in ui.tasks
        "SCR-021","SCR-022"->p.ref in ui.levels
        else->ui.tasks[p.ref]?.screen==p.screen
    }
    private fun decodePage(raw:String):Page?=runCatching{val x=raw.split('|');require(x.size==4);val p=Page(x[0],x[1],x[2].toInt(),x[3]=="1");require(p.index in 0..660);p}.getOrNull()
    private fun encodePage(p:Page)="${p.screen}|${p.ref}|${p.index}|${if(p.extra)1 else 0}"
    override fun onSaveInstanceState(outState:Bundle){
        super.onSaveInstanceState(outState)
        val safe=if(stack.any{it.screen in parentScreens})listOf(Page("SCR-003")) else stack
        outState.putStringArrayList("ui_stack",ArrayList(safe.map(::encodePage)))
    }
    private fun stopAudio(){f?.audio?.stop()}
    private fun active(action:()->Unit):()->Unit={if(resumed&&!closed)action()}
    private fun playNonTask(path:String?){
        if(!resumed||closed||path==null)return
        val base=f?:return;require(path in base.catalog.paths && path.startsWith("audio/") && path.endsWith(".ogg"))
        base.audio.stop();base.backend.play(path){}
    }
    private fun taskVoice(task:UiTask,event:AudioEvent){val base=f?:return;base.audioPolicy.request(task.id,event,true)?.let(base.audio::request)}
    private fun push(p:Page){stopAudio();stack.add(p);render()}
    private fun replace(p:Page){stopAudio();if(stack.isNotEmpty())stack[stack.lastIndex]=p else stack.add(p);render()}
    private fun back(){stopAudio();val wasParent=page.screen in parentScreens;if(stack.size>1){stack.removeAt(stack.lastIndex);if(wasParent && page.screen !in parentScreens)f?.parent?.close();if(page.screen in parentScreens && page.screen!="SCR-025" && f?.parent?.allowed()!=true)home() else render()}else home()}
    private fun home(){stopAudio();f?.parent?.close();stack.clear();stack.add(Page("SCR-003"));f?.navigator?.go(Route.Home);render()}
    private fun welcomeDone(){prefs.edit().putBoolean("welcome_seen",true).apply();home()}
    private fun openTask(id:String,restore:Boolean=false){
        val base=f?:return;val task=base.ui.tasks.getValue(id)
        if(!restore){
            if(task.ready||task.prelude){base.navigator.go(Route.TaskRoute(id));base.progress.saveSession(base.sessions.snapshotForTask(id))}
            push(Page(task.screen,id))
        }else{
            val lesson=base.ui.lessonForTask(id);val topic=base.ui.topics.getValue(lesson.topic);val area=base.ui.areas.single{topic.card.id in it.topics}
            stack.clear();stack.addAll(listOf(Page("SCR-003"),Page("SCR-004",area.card.id),Page("SCR-005",topic.card.id),Page("SCR-006",lesson.card.id),Page("SCR-007",lesson.card.id),Page(task.screen,id)))
            if(task.ready||task.prelude){base.navigator.go(Route.TaskRoute(id));base.progress.saveSession(base.sessions.snapshotForTask(id))};render()
        }
    }
    private fun nextWithoutReward(task:UiTask){
        val base=f?:return;val level=base.ui.levels.getValue(task.level);val next=level.tasks.getOrNull(level.tasks.indexOf(task.id)+1)
        if(next==null){replace(Page("SCR-021",task.level));return}
        val t=base.ui.tasks.getValue(next);if(t.ready||t.prelude){base.navigator.go(Route.TaskRoute(next));base.progress.saveSession(base.sessions.snapshotForTask(next))}
        replace(Page(t.screen,next))
    }
    private fun fixedBackground(ui:UiContent,id:String)=ui.screenArt(id).firstOrNull{it.startsWith("art/img_bg_")}
    private fun fixedFigure(ui:UiContent,id:String)=ui.screenArt(id).firstOrNull{!it.startsWith("art/img_bg_")}
    private fun showError(previous:Page?){
        errorReturn=previous
        mount(w.shell(appTitle,null,null,null){body->
            runCatching{w.art(body,"art/img_chr_0074.webp",180)}
            w.add(body,w.text("Bé thử lại nhé!",24,true))
            w.add(body,w.button("Thử lại","teal",action=active{if(f==null)load() else{val p=errorReturn;if(p!=null && p.screen!="SCR-033")replace(p) else home()}}))
            if(f!=null)w.add(body,w.button("Về nhà",action=active(::home)))
        })
    }
    private fun frame(ui:UiContent,p:Page,heading:String,body:(LinearLayout)->Unit,background:String?=null,top:Boolean=true){
        val isHome=p.screen=="SCR-003";val isWelcome=p.screen=="SCR-002"
        mount(w.shell(heading,background,if(!isHome&&!isWelcome&&top)active(::back)else null,if(!isHome&&!isWelcome&&top)active(::home)else null,body))
    }
    private fun action(body:LinearLayout,label:String,fill:String="green",adult:Boolean=false,enabled:Boolean=true,fn:(()->Unit)?=null){w.add(body,w.button(label,fill,adult,enabled,fn?.let{active(it)}))}
    private fun heading(body:LinearLayout,text:String){w.add(body,w.text(text,24,true),8)}
    private fun cardViews(cards:List<UiCard>,onSelect:(UiCard)->Unit):List<View>{val fills=listOf("green","blue","peach","lilac","yellow","white");return cards.mapIndexed{i,c->w.card(c.title,c.image,fills[i%fills.size],if(w.viewport.wide)76 else 90,active{onSelect(c)})}}
    private fun pageControls(body:LinearLayout,p:Page,count:Int){
        if(count<=1)return
        val n=p.index.coerceIn(0,count-1)
        w.add(body,w.text(if(count>10)"${n+1} / $count" else (1..count).joinToString(" "){if(it==n+1)"●" else "○"},18),8)
        w.pair(body,w.button("Trước","white",enabled=n>0,action=active{replace(p.copy(index=n-1))}),w.button("Sau","green",enabled=n+1<count,action=active{replace(p.copy(index=n+1))}))
    }
    private fun <T> paginated(list:List<T>,pageSize:Int,p:Page,body:LinearLayout,render:(List<T>)->Unit){
        if(list.isEmpty())return
        val chunks=list.chunked(pageSize);val n=p.index.coerceIn(0,chunks.lastIndex)
        render(chunks[n]);pageControls(body,p.copy(index=n),chunks.size)
    }
    private fun listen(body:LinearLayout,path:String?,label:String="Nghe lại"){
        if(path!=null)w.pair(body,w.button(label,"teal",action=active{playNonTask(path)}),w.button("Dừng","white",action=active(::stopAudio)))
    }

    private fun render(){
        val base=f?:return;val ui=base.ui;val p=page
        try{
            if(p.screen in parentScreens && p.screen!="SCR-025" && !base.parent.allowed()){home();return}
            when(p.screen){
                "SCR-002"->frame(ui,p,appTitle,{b->fixedFigure(ui,p.screen)?.let{w.art(b,it,210)};heading(b,"Chào bé!");action(b,"Bắt đầu","teal",fn=::welcomeDone);listen(b,ui.welcomeAudio);action(b,"Bỏ qua","white",fn=::welcomeDone)},fixedBackground(ui,p.screen))
                "SCR-003"->homeScreen(base,p)
                "SCR-004"->areaScreen(ui,p)
                "SCR-005"->topicScreen(ui,p)
                "SCR-006"->lessonScreen(ui,p)
                "SCR-007"->levelsScreen(ui,p)
                "SCR-008"->exploreScreen(ui,p)
                "SCR-009"->letterLibrary(ui,p)
                "SCR-010"->letterDetail(ui,p)
                "SCR-011"->numberCard(ui,p)
                "SCR-012"->englishHome(ui,p)
                "SCR-013"->englishCard(ui,p,false)
                "SCR-014"->englishCard(ui,p,true)
                "SCR-015"->storyLibrary(ui,p)
                "SCR-016","SCR-017"->storyReader(ui,p)
                "SCR-018"->playLibrary(ui,p)
                "SCR-019"->frame(ui,p,"Bé nghỉ một chút",{b->fixedFigure(ui,p.screen)?.let{w.art(b,it,210)};action(b,"Chơi tiếp","teal",fn=::back);action(b,"Về nhà","white",fn=::home)})
                "SCR-020"->frame(ui,p,"Chơi tiếp nhé?",{b->val l=ui.lessonForTask(p.ref);w.art(b,l.card.image,200);heading(b,l.card.title);action(b,"Chơi tiếp","teal",fn={openTask(p.ref,true)});action(b,"Chọn bài khác","white",fn=::home)})
                "SCR-021"->activityEnd(base,p)
                "SCR-022"->rewardScreen(base,p)
                "SCR-023"->garden(base,p)
                "SCR-024"->inventory(base,p)
                "SCR-025"->parentGate(base,p)
                "SCR-026"->parentHome(base,p)
                "SCR-027"->parentProgress(base,p)
                "SCR-028","SCR-029","SCR-030"->parentSettings(base,p)
                "SCR-031"->parentReset(base,p)
                "SCR-032"->parentAbout(base,p)
                "SCR-033"->showError(null)
                else->ui.tasks[p.ref]?.takeIf{it.screen==p.screen}?.let{taskScreen(base,p,it)}?:showError(p)
            }
        }catch(_:Exception){showError(p)}
    }
    private fun homeScreen(base:Foundation,p:Page){val ui=base.ui
        frame(ui,p,appTitle,{b->
            w.grid(b,cardViews(ui.areas.map{it.card}){c->if(c.id=="AREA-04")push(Page("SCR-012"))else push(Page("SCR-004",c.id))},2,112)
            val resume=runCatching{base.progress.loadSession()?.s("task_id")}.getOrNull()?.takeIf{it in ui.tasks}
            if(resume!=null)action(b,"Chơi tiếp","teal",fn={push(Page("SCR-020",resume))})
            w.pair(b,w.button("Khu vườn","green",action=active{push(Page("SCR-023"))}),w.button("Phụ huynh","white",adult=true,action=active{challengeA=Random.nextInt(10,50);challengeB=Random.nextInt(10,50);push(Page("SCR-025"))}))
        },fixedBackground(ui,p.screen))
    }
    private fun areaScreen(ui:UiContent,p:Page){val a=ui.areas.single{it.card.id==p.ref};val pages=a.pages;val i=p.index.coerceIn(0,pages.lastIndex)
        frame(ui,p,a.card.title,{b->
            w.grid(b,cardViews(pages[i].map{ui.topics.getValue(it).card}){c->push(Page("SCR-005",c.id))},2)
            pageControls(b,p.copy(index=i),pages.size)
            when(p.ref){"AREA-02"->action(b,"Thẻ số",fn={push(Page("SCR-011"))});"AREA-03"->action(b,"Thư viện chữ cái",fn={push(Page("SCR-009"))});"AREA-06"->{action(b,"Truyện, thơ và bài hát",fn={push(Page("SCR-015"))});action(b,"Trò chơi",fn={push(Page("SCR-018"))})}}
        })
    }
    private fun topicScreen(ui:UiContent,p:Page){val t=ui.topics.getValue(p.ref);frame(ui,p,t.card.title,{b->
        paginated(t.lessons.map{ui.lessons.getValue(it).card},4,p,b){cards->w.grid(b,cardViews(cards){c->push(Page("SCR-006",c.id))},2)}
    },t.background)}
    private fun lessonScreen(ui:UiContent,p:Page){val l=ui.lessons.getValue(p.ref);frame(ui,p,l.card.title,{b->
        w.art(b,l.card.image,210);listen(b,l.voice.firstOrNull(),"Nghe lời cô")
        action(b,"Khám phá","green",enabled=l.intro.isNotEmpty(),fn={push(Page("SCR-008",p.ref))})
        if(ui.vocabulary.any{it.lesson==p.ref&&!it.optional})action(b,"Nghe từ tiếng Anh",fn={push(Page("SCR-013",p.ref))})
        if(ui.phrases.any{it.lesson==p.ref})action(b,"Mẫu câu giao tiếp",fn={push(Page("SCR-014",p.ref))})
        l.stories.forEach{id->val s=ui.stories.getValue(id);action(b,s.title,"yellow",fn={push(Page(s.screen,s.id))})}
        action(b,"Chơi","teal",fn={push(Page("SCR-007",p.ref))})
    })}
    private fun levelsScreen(ui:UiContent,p:Page){val l=ui.lessons.getValue(p.ref);frame(ui,p,l.card.title,{b->
        val levels=l.levels.map{ui.levels.getValue(it)};fixedFigure(ui,p.screen)?.let{w.art(b,it,112)}
        levels.forEachIndexed{i,a->w.add(b,w.card(a.title,l.card.image,listOf("green","blue","yellow")[i%3],72,active{openTask(a.tasks.first())}))}
    })}
    private fun exploreScreen(ui:UiContent,p:Page){val l=ui.lessons.getValue(p.ref);frame(ui,p,l.card.title,{b->
        paginated(l.intro,1,p,b){items->val a=items.single();w.art(b,a.image,240,"Tranh khám phá");listen(b,a.audio?:l.voice.firstOrNull())}
        action(b,"Chơi","teal",fn={push(Page("SCR-007",p.ref))})
    },fixedBackground(ui,p.screen))}
    private fun letterLibrary(ui:UiContent,p:Page){frame(ui,p,"Chữ cái",{b->paginated(ui.letters,w.viewport.pageSize(6),p,b){letters->w.grid(b,letters.map{l->w.card("${l.upper}  ${l.lower}",null,"white",action=active{push(Page("SCR-010",l.id))})},3,90)}},fixedBackground(ui,p.screen))}
    private fun letterDetail(ui:UiContent,p:Page){val l=ui.letters.single{it.id==p.ref};frame(ui,p,"Chữ cái",{b->
        w.add(b,w.text("${l.upper}  ${l.lower}",80,true));l.images.lastOrNull()?.let{w.art(b,it,220,"Tranh liên tưởng")};heading(b,l.word);listen(b,l.audio,"Nghe từ")
        val i=ui.letters.indexOf(l);w.pair(b,w.button("Trước","white",enabled=i>0,action=active{replace(Page("SCR-010",ui.letters[i-1].id))}),w.button("Sau",enabled=i<ui.letters.lastIndex,action=active{replace(Page("SCR-010",ui.letters[i+1].id))}))
    },fixedBackground(ui,p.screen))}
    private fun numberCard(ui:UiContent,p:Page){frame(ui,p,"Bé học đếm",{b->paginated(ui.numbers,1,p,b){items->val n=items.single();w.add(b,w.text(n.number.toString(),84,true));heading(b,n.label)
        // The number library is exploration, not a graded count task. Dots reflect the approved number.
        w.grid(b,List(n.number){w.text("●",40,true,"teal")},5,48);listen(b,n.audio,"Nghe số")}},fixedBackground(ui,p.screen))}
    private fun englishHome(ui:UiContent,p:Page){frame(ui,p,"Hello English",{b->paginated(ui.englishGroups,4,p,b){cards->w.grid(b,cardViews(cards){c->push(Page("SCR-006",c.id))},2)}},fixedBackground(ui,p.screen))}
    private fun englishCard(ui:UiContent,p:Page,phrases:Boolean){val list=if(phrases)ui.phrases.filter{it.lesson==p.ref}else ui.vocabulary.filter{it.lesson==p.ref && it.optional==p.extra}
        frame(ui,p,if(phrases)"Hello!" else "Nghe và học",{b->
            paginated(list,1,p,b){items->val c=items.single();c.images.firstOrNull()?.let{w.art(b,it,230,"Tranh tiếng Anh")};w.add(b,w.text(c.text,36,true));listen(b,c.audio,"Nghe tiếng Anh")
                if(phrases && c.meaning!=null){if(p.extra)w.add(b,w.text(c.meaning,20))else action(b,"Xem nghĩa","white",fn={replace(p.copy(extra=true))})}
            }
            if(!phrases && ui.vocabulary.any{it.lesson==p.ref&&it.optional})action(b,if(p.extra)"Từ cơ bản" else "Mở rộng","white",fn={replace(p.copy(index=0,extra=!p.extra))})
        },fixedBackground(ui,p.screen))
    }
    private fun storyLibrary(ui:UiContent,p:Page){frame(ui,p,"Truyện, thơ và bài hát",{b->paginated(ui.stories.values.toList(),4,p,b){items->w.grid(b,items.map{s->w.card(s.title,s.scenes.first().image,if(s.language=="en")"blue" else "yellow",action=active{push(Page(s.screen,s.id))})},2)}},fixedBackground(ui,p.screen))}
    private fun storyReader(ui:UiContent,p:Page){val s=ui.stories.getValue(p.ref);frame(ui,p,s.title,{b->paginated(s.scenes,1,p,b){items->val a=items.single();w.art(b,a.image,240,"Cảnh truyện");w.add(b,w.text(a.text,24));listen(b,a.audio,if(s.screen=="SCR-017")"Nghe đoạn này" else "Nghe kể chuyện")}},fixedBackground(ui,p.screen))}
    private fun playLibrary(ui:UiContent,p:Page){val types=ApprovedUi.screens.filter{it.taskCount>0 && ui.tasks.values.any{t->t.screen==it.id&&(t.ready||t.prelude)}}
        frame(ui,p,"Bé vui chơi",{b->
            if(p.ref.isEmpty())paginated(types,4,p,b){items->w.grid(b,items.map{s->val first=ui.tasks.values.first{it.screen==s.id&&(it.ready||it.prelude)};w.card(ui.screenNames.getValue(s.id),ui.lessonForTask(first.id).card.image,"blue",action=active{push(Page("SCR-018",s.id))})},2)}
            else paginated(ui.tasks.values.filter{it.screen==p.ref&&(it.ready||it.prelude)},4,p,b){items->w.grid(b,items.map{t->val lesson=ui.lessonForTask(t.id);w.card(lesson.card.title,lesson.card.image,"green",action=active{openTask(t.id)})},2)}
        },fixedBackground(ui,p.screen))
    }
    private data class Earned(val task:String,val item:String)
    private fun earned(base:Foundation,level:String):List<Earned>{
        val allowed=base.ui.levels.getValue(level).tasks.toSet();val result=mutableListOf<Earned>()
        base.progress.readableDatabase.rawQuery("SELECT task_id,item_id FROM reward_ledger WHERE claimed=0",null).use{c->while(c.moveToNext()){
            val task=c.getString(0);val item=c.getString(1);if(task in allowed && item in base.ui.rewardArt)result.add(Earned(task,item))
        }};return result
    }
    private fun activityEnd(base:Foundation,p:Page){
        val ui=base.ui;val level=ui.levels.getValue(p.ref);val completed=mutableSetOf<String>()
        base.progress.readableDatabase.rawQuery("SELECT task_id FROM completions",null).use{c->while(c.moveToNext())completed.add(c.getString(0))}
        val done=level.tasks.count{it in completed};val rewards=earned(base,p.ref)
        frame(ui,p,if(done==level.tasks.size)"Bé đã hoàn thành!" else "Mình nghỉ nhé!",{b->
            ui.screenArt("SCR-021").firstOrNull()?.let{w.art(b,it,200,"Bạn nhỏ")}
            w.add(b,w.text(if(done>0)"Bé đã tham gia $done hoạt động." else "Bé có thể chơi tiếp vào lúc khác.",22))
            if(rewards.isNotEmpty())action(b,"Xem quà của bé","teal",fn={push(Page("SCR-022",p.ref))})
            action(b,"Về bài học","white",fn=::back);action(b,"Về nhà","green",fn=::home)
        })
    }
    private fun rewardScreen(base:Foundation,p:Page){val ui=base.ui;val rewards=earned(base,p.ref)
        frame(ui,p,"Quà của bé",{b->
            if(rewards.isEmpty()){w.add(b,w.text("Chưa có quà mới",24,true))}
            else {ui.screenArt(p.screen).firstOrNull{it.endsWith("0027.webp")}?.let{w.art(b,it,200,"Hộp quà chưa mở")}
                // Presentation only. Claiming and new completion events remain in phase 6.3.
                action(b,"Mở quà","teal",enabled=false)
            }
            action(b,"Để sau","white",fn=::back);action(b,"Túi đồ","green",fn={push(Page("SCR-024"))})
        })
    }
    private fun parentSettings(base:Foundation,p:Page){
        frame(base.ui,p,base.ui.screenNames.getValue(p.screen),{b->
            w.add(b,w.text("Các tùy chọn chưa được kích hoạt.",18,center=false))
            fun toggle(label:String)=w.add(b,w.adultSwitch(label))
            fun slider(label:String)=w.add(b,w.adultSlider(label))
            when(p.screen){
                "SCR-028"->{toggle("Giọng cô");slider("Âm lượng giọng nói");toggle("Tiếng Anh");toggle("Nhạc nền");slider("Âm lượng nhạc");toggle("Hiệu ứng");slider("Âm lượng hiệu ứng");slider("Tốc độ nghe");action(b,"Nghe mẫu","white",adult=true,enabled=false)}
                "SCR-029"->{slider("Thời lượng sử dụng đề xuất");toggle("Nhắc nghỉ giữa các hoạt động")}
                else->{toggle("Chạm thay kéo");toggle("Chữ lớn");toggle("Giảm chuyển động");toggle("Nhạc nền")}
            }
            action(b,"Lưu cài đặt","teal",adult=true,enabled=false)
        })
    }
    private fun parentReset(base:Foundation,p:Page){
        frame(base.ui,p,"Đặt lại tiến trình",{b->
            w.add(b,w.text("Thao tác đặt lại sẽ xóa tiến trình học và khu vườn. Kho bài học không bị xóa.",20,center=false))
            if(!p.extra){
                action(b,"Hủy","white",adult=true,fn=::back)
                action(b,"Tiếp tục xác nhận","peach",adult=true,fn={replace(p.copy(extra=true))})
            }else{
                w.add(b,w.text("Xác nhận lần hai: thao tác không thể hoàn tác. Dữ liệu hiện vẫn được giữ nguyên.",18,center=false))
                action(b,"Hủy","white",adult=true,fn={replace(p.copy(extra=false))})
                action(b,"Xác nhận đặt lại","peach",adult=true,enabled=false)
            }
        })
    }
    private fun garden(base:Foundation,p:Page){val ui=base.ui
        val items=mutableListOf<GardenWidgets.GardenItem>()
        base.progress.readableDatabase.rawQuery("SELECT l.item_id,p.x,p.y FROM garden_placements p JOIN reward_ledger l ON l.task_id=p.task_id WHERE l.claimed=1 ORDER BY p.task_id",null).use{c->while(c.moveToNext()){
            val x=c.getFloat(1);val y=c.getFloat(2);val art=ui.rewardArt[c.getString(0)]
            if(art!=null&&x in 0f..1f&&y in 0f..1f)items.add(GardenWidgets.GardenItem(art,x,y))
        }}
        frame(ui,p,"Khu vườn của bé",{b->
            ui.screenArt(p.screen).firstOrNull()?.let{w.add(b,w.gardenScene(it,items),8,300)}
            action(b,"Túi đồ","teal",fn={push(Page("SCR-024"))})
        })
    }
    private fun inventory(base:Foundation,p:Page){val ui=base.ui;val owned=base.progress.inventory().entries.filter{it.value>0};frame(ui,p,"Túi đồ",{b->
        if(owned.isEmpty())w.add(b,w.text("Túi đồ đang trống",24))else paginated(owned,w.viewport.pageSize(6),p,b){items->w.grid(b,items.map{e->w.column().apply{background=w.rounded("white");ui.rewardArt[e.key]?.let{w.art(this,it,120,"Vật phẩm")};w.add(this,w.text("× ${e.value}",24,true))}},2)}
    },fixedBackground(ui,p.screen))}
    private fun parentGate(base:Foundation,p:Page){if(challengeA !in 10..49){challengeA=Random.nextInt(10,50);challengeB=Random.nextInt(10,50)}
        frame(base.ui,p,"Dành cho phụ huynh",{b->w.add(b,w.text("Vui lòng để người lớn xác nhận.",20));heading(b,"$challengeA + $challengeB = ?")
            val edit=w.adultInput("Kết quả phép tính")
            w.add(b,edit);action(b,"Xác nhận","teal",adult=true,fn={val answer=edit.text.toString().toIntOrNull();if(answer!=null&&base.parent.verifyAdultChallenge(answer,challengeA,challengeB)){replace(Page("SCR-026"))}else{base.parent.close();home()}})
            action(b,"Về nhà","white",adult=true,fn=::home)
        })
    }
    private fun parentHome(base:Foundation,p:Page){frame(base.ui,p,"Phụ huynh",{b->
        action(b,"Theo dõi học tập","green",adult=true,fn={push(Page("SCR-027"))})
        action(b,"Cài đặt âm thanh","white",adult=true,fn={push(Page("SCR-028"))})
        action(b,"Thời lượng và nghỉ","white",adult=true,fn={push(Page("SCR-029"))})
        action(b,"Hỗ trợ thao tác","white",adult=true,fn={push(Page("SCR-030"))})
        action(b,"Đặt lại tiến trình","white",adult=true,fn={push(Page("SCR-031"))})
        action(b,"Thông tin ứng dụng","white",adult=true,fn={push(Page("SCR-032"))})
        action(b,"Về nhà","white",adult=true,fn=::home)
    })}
    private fun parentProgress(base:Foundation,p:Page){val completed=linkedSetOf<String>();base.progress.readableDatabase.rawQuery("SELECT task_id FROM completions",null).use{c->while(c.moveToNext())completed.add(c.getString(0))}
        frame(base.ui,p,"Theo dõi học tập",{b->w.add(b,w.text("Nhiệm vụ đã ghi nhận: ${completed.size}",20,true));paginated(base.ui.topics.values.toList(),4,p,b){items->items.forEach{t->val ids=t.lessons.flatMap{base.ui.lessons.getValue(it).levels}.flatMap{base.ui.levels.getValue(it).tasks};w.add(b,w.text("${t.card.title}: ${ids.count{it in completed}}/${ids.size}",20,center=false))}}})
    }
    private fun parentAbout(base:Foundation,p:Page){frame(base.ui,p,"Thông tin ứng dụng",{b->
        w.add(b,w.text(appTitle,28,true));w.add(b,w.text("Ứng dụng học tập ngoại tuyến, không tài khoản, không quảng cáo và không thu âm hoặc ghi hình.",18,center=false));w.add(b,w.text("Phiên bản nội dung: ${base.catalog.version}\nBản phát triển chưa phát hành chính thức.",18,center=false))
        action(b,"Quay lại","white",adult=true,fn=::back)
    })}
    private fun taskScreen(base:Foundation,p:Page,t:UiTask){val ui=base.ui
        if(t.prelude){frame(ui,p,"Nghe trước nhé!",{b->
            t.preludeImage?.let{w.art(b,it,240,"Cảnh mở đầu")}
            action(b,"Nghe câu chuyện","teal",fn={taskVoice(t,AudioEvent.LISTEN)})
            action(b,"Dừng","white",fn=::stopAudio)
            // No question is exposed until an actual playback-complete event is integrated in 6.3.
            action(b,"Tiếp tục","white",enabled=false)
            w.pair(b,w.button("Nghỉ","white",action=active{push(Page("SCR-019",t.id))}),w.button("Bỏ qua","green",action=active{nextWithoutReward(t)}))
        },t.background);return}
        if(!t.ready){frame(ui,p,"Bé chơi nhé!",{b->w.add(b,w.text("Bé chọn hoạt động khác nhé.",24,true));action(b,"Quay lại","white",fn=::back)});return}
        val level=ui.levels.getValue(t.level);val current=level.tasks.indexOf(t.id)+1
        frame(ui,p,"$current / ${level.tasks.size}",{b->
            heading(b,t.prompt)
            w.pair(b,w.button("Nghe lại","teal",action=active{taskVoice(t,AudioEvent.PROMPT)}),w.button("Dừng","white",action=active(::stopAudio)))
            if(t.listen && t.groups.none{it.role=="listen_options"})action(b,"Nghe mẫu","blue",fn={taskVoice(t,AudioEvent.LISTEN)})
            t.groups.forEachIndexed{index,g->renderGroup(b,p,t,index,g)}
            // Deliberately no response evaluation, completion or reward button until its engine is connected.
            action(b,"Xong","white",enabled=false)
            w.pair(b,w.button("Nghỉ","white",action=active{push(Page("SCR-019",t.id))}),w.button("Bỏ qua","green",action=active{nextWithoutReward(t)}))
        },t.background)
    }
    private fun renderGroup(b:LinearLayout,p:Page,t:UiTask,index:Int,g:UiGroup){
        val items=if(g.shuffle)g.items.shuffled(Random(seed xor t.id.hashCode().toLong() xor index.toLong()))else g.items
        if(g.role.startsWith("compare_")){
            // Same-size source art, quantity unchanged. Close/wide spacing differs, not scale.
            val r=w.row().apply{gravity=android.view.Gravity.CENTER};val cell=minOf(44,(w.viewport.available-12)/items.size)
            items.forEachIndexed{i,e->if(e is UiElement.Image){r.addView(w.image(e.path),LinearLayout.LayoutParams(w.dp(cell),w.dp(cell)).apply{if(i>0)marginStart=w.dp(if(g.role=="compare_wide")minOf(12,(w.viewport.available-cell*items.size)/(items.size.coerceAtLeast(2)-1))else 2)})}}
            w.add(b,r);return
        }
        val maxItems=when(g.role){"memory"->items.size;"pattern","context","countables","count_groups","reserve","sequence_slots","sources","destinations","bins"->items.size;else->w.viewport.pageSize(6)}
        val key="${t.id}:$index";val chunks=items.chunked(maxItems);val pageIndex=(groupPages[key]?:0).coerceIn(0,chunks.lastIndex)
        val context=g.role in setOf("context","trace","hotspot","workbench","board")
        val heading=when(g.role){"sources"->"Thẻ hình";"destinations"->"Nhóm ghép";"reserve"->"Bên ngoài";"workbench"->"Sân chơi";"palette"->"Chọn hình hoặc màu";"decorations"->"Họa tiết";"sequence_slots"->"Sắp xếp";"mix_sources"->"Màu để pha";"mix_result"->"Pha màu";"listen_options"->"Nghe từng mẫu";else->null};heading?.let{w.add(b,w.text(it,18,true),8)}
        val views=chunks[pageIndex].mapIndexed{i,e->
            val label=when(g.role){"choices"->"Lựa chọn ${pageIndex*maxItems+i+1}";"memory"->"Thẻ ${i+1}";"sources"->"Thẻ nguồn ${i+1}";"destinations"->"Thẻ đích ${i+1}";else->"Tranh ${i+1}"}
            val cell=w.column().apply{background=w.rounded("white");setPadding(w.dp(6),w.dp(6),w.dp(6),w.dp(6));minimumHeight=w.dp(64);contentDescription=label;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
            when(e){
                is UiElement.Image->{
                    val zoom=g.role=="hotspot" && groupPages["zoom:$key"]==1
                    if(g.role=="hotspot"){
                        w.add(cell,w.zoomArt(e.path,zoom),0,if(w.viewport.wide)220 else 320)
                        w.add(cell,w.button(if(zoom)"Thu nhỏ" else "Xem lớn","white",action=active{groupPages["zoom:$key"]=if(zoom)0 else 1;render()}))
                    }else w.art(cell,e.path,if(context)220 else if(w.viewport.wide)96 else 120)
                }
                is UiElement.Glyph->w.add(cell,w.text(e.text,if(g.role=="word")52 else 48,true),0)
                is UiElement.Clock->w.add(cell,w.clock(e.hour),0,240)
                is UiElement.Crop->w.add(cell,w.croppedImage(e.path,e.rect,e.mirror),0,if(context)220 else 120)
                is UiElement.Count->w.add(cell,w.countedImage(e.path,e.count),0,120)
                is UiElement.Numbered->{w.art(cell,e.path,100);w.add(cell,w.text(e.text,36,true),0)}
                is UiElement.PolygonCrop->w.add(cell,w.polygonImage(e),0,120)
                is UiElement.Scaled->w.add(cell,w.scaledImage(e),0,120)
                is UiElement.LabelCard->{w.add(cell,w.text(if(e.style=="calendar")"▦" else if(e.style=="page")"▤" else "♫",28,true),0);w.add(cell,w.text(e.text,22,true),4)}
                is UiElement.Annulus->w.add(cell,w.annulus(e),0,140)
                is UiElement.ClockBlank->w.add(cell,w.clock(null),0,240)
                is UiElement.Spatial->w.add(cell,w.spatial(e),0,280)
                is UiElement.MeasureLength->w.add(cell,w.measureLength(e),0,220)
                is UiElement.Vessel->{w.add(cell,w.vessel(e),0,180);w.add(cell,w.text("Bình ${e.label}",22,true),0)}
                is UiElement.Board->w.add(cell,w.outlineBoard(e.path,e.shapes),0,280)
                UiElement.Speaker->w.art(cell,f!!.ui.icons.getValue("ICO-007"),72)
                UiElement.Blank->if(g.role in setOf("workbench","sequence_slots","mix_result","direction_endpoints"))w.add(cell,View(this).apply{background=w.rounded("cream","edge",16);contentDescription="Ô trống"},0,if(g.role=="workbench")200 else 90)else w.add(cell,w.text("?",36,true),0)
            };cell
        }
        w.grid(b,views,if(context)1 else g.columns,if(g.role=="word")80 else 100)
        if(chunks.size>1){w.add(b,w.text("${pageIndex+1} / ${chunks.size}",18),4);w.pair(b,w.button("Trước","white",enabled=pageIndex>0,action=active{groupPages[key]=pageIndex-1;stopAudio();render()}),w.button("Sau","green",enabled=pageIndex<chunks.lastIndex,action=active{groupPages[key]=pageIndex+1;stopAudio();render()}))}
    }
    override fun onResume(){super.onResume();resumed=true;if(adultInvalidated){adultInvalidated=false;home()}}
    override fun onPause(){resumed=false;stopAudio();f?.parent?.close();if(page.screen in parentScreens)adultInvalidated=true;super.onPause()}
    override fun onStop(){stopAudio();super.onStop()}
    override fun onDestroy(){closed=true;stopAudio();f?.progress?.close();f=null;if(::w.isInitialized)w.clear();super.onDestroy()}
    @Suppress("DEPRECATION") override fun onBackPressed(){if(stack.size>1)back()else super.onBackPressed()}
}
