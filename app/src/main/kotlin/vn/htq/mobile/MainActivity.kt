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

/** Native presentation with source-bound interaction controllers. Unsupported mechanics fail closed. */
class MainActivity : Activity() {
    private data class Page(val screen:String,val ref:String="",val index:Int=0,val extra:Boolean=false)
    private class Foundation(val catalog:Catalog,val ui:UiContent,val progress:LocalProgress,val backend:AndroidAudio) {
        val audio=AudioArbiter(backend)
        val audioPolicy=TaskAudioPolicy(catalog)
        val taskMotion=TaskMotion(catalog)
        val listening=ListeningGate(catalog,audio)
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
    private var interaction:TaskInteraction?=null
    private var motionPreview:MotionPreview?=null
    private fun stopMotion(){motionPreview?.stop();motionPreview=null}
    private var clearConfirmationFor:String?=null
    private var preludeOpenedFor:String?=null
    private var listeningFailed=false
    private var gardenSelection:String?=null
    private val groupPages=mutableMapOf<String,Int>()
    private var pendingStack:ArrayList<String>?=null
    private var pendingGroupPages:Bundle?=null
    private val prefs by lazy { getSharedPreferences("htq_ui_preferences",MODE_PRIVATE) }
    private var settings=ParentPreferences()
    private var elapsedStudyMs=0L
    private var foregroundBegan:Long?=null
    private fun studyTimeMs()=elapsedStudyMs+(foregroundBegan?.let{SystemClock.elapsedRealtime()-it}?:0L)
    private fun applySettings(){
        w.largeText=settings.largeText;w.reducedMotion=settings.reducedMotion;f?.backend?.settings=settings
    }
    private fun menuMusic(base:Foundation,p:Page):String?{
        if(!settings.music||settings.musicVolume==0)return null
        val n=when(p.screen){
            "SCR-003","SCR-004"->1
            "SCR-023","SCR-024"->8
            "SCR-005","SCR-006","SCR-008"->{
                val topic=if(p.screen=="SCR-005")p.ref else base.ui.lessons[p.ref]?.topic?:return null
                when(topic.take(2)){"01","15"->5;"02","03","04"->3;"05"->4;"06","07","08","09","10","11","12","13","14"->2;"16"->6;"17"->7;else->return null}
            }
            else->return null
        }
        return base.catalog.assetPath("AUD-MUS-"+n.toString().padStart(4,'0'),"audio")
    }
    private val parentScreens=setOf("SCR-025","SCR-026","SCR-027","SCR-028","SCR-029","SCR-030","SCR-031","SCR-032")
    private val page get()=stack.lastOrNull() ?: Page("SCR-003")
    private val appTitle get()=getString(R.string.app_name)

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);w=GardenWidgets(this)
        settings=ParentPreferences.load(prefs);applySettings()
        elapsedStudyMs=savedInstanceState?.getLong("study_elapsed_ms",0L)?:0L
        seed=if(prefs.contains("visual_seed"))prefs.getLong("visual_seed",0L) else Random.nextLong().also{prefs.edit().putLong("visual_seed",it).apply()}
        pendingStack=savedInstanceState?.getStringArrayList("ui_stack")
        pendingGroupPages=savedInstanceState?.getBundle("ui_group_pages")
        load()
    }
    private fun mount(v:View){setContentView(v);v.requestApplyInsets()}
    private fun load(){
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
                        f=base;applySettings();stack.clear();stack.add(Page("SCR-003"))
                        val restored=pendingStack
                        val decoded=restored?.mapNotNull(::decodePage).orEmpty()
                        groupPages.clear()
                        if(decoded.isNotEmpty() && decoded.size==restored?.size && decoded.first().screen=="SCR-003" && decoded.none{it.screen in parentScreens} && decoded.all{validPage(base.ui,it)}){
                            stack.clear();stack.addAll(decoded)
                            pendingGroupPages?.let{saved->
                                groupStateLimits(ui,stack).forEach{(key,max)->
                                    val n=saved.getInt(key,0)
                                    if(n in 1..max)groupPages[key]=n
                                }
                                saved.keySet().filter{validInteractionPageKey(ui,stack,it)}.take(80).forEach{key->
                                    val n=saved.getInt(key,0);if(n in 1..660)groupPages[key]=n
                                }
                            }
                        }else if(!prefs.getBoolean("welcome_seen",false)) stack.add(Page("SCR-002"))
                        else runCatching{base.progress.loadSession()?.s("task_id")}.getOrNull()?.takeIf{it in ui.tasks}?.let{stack.add(Page("SCR-020",it))}
                        pendingStack=null;pendingGroupPages=null
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
    private fun groupKey(t:UiTask,index:Int,g:UiGroup)="${t.id}:${g.pager ?: index.toString()}"
    // Persist only presentation state for tasks on the restored child stack. No answers or parent authorization.
    private fun groupStateLimits(ui:UiContent,pages:List<Page>):Map<String,Int> {
        val limits=linkedMapOf<String,Int>()
        pages.mapNotNull{ui.tasks[it.ref]}.distinctBy{it.id}.forEach{t->
            (if(t.prelude)t.afterPrelude else t.groups).forEachIndexed{index,g->
                val key=groupKey(t,index,g)
                // Saved paging is only a hint; renderGroup revalidates the committed response.
                val max=g.items.lastIndex
                limits[key]=minOf(limits[key]?:max,max)
                if(g.role=="hotspot")limits["zoom:$key"]=1
            }
        }
        return limits
    }
    private fun validInteractionPageKey(ui:UiContent,pages:List<Page>,key:String):Boolean{
        val parts=key.split(':')
        return parts.size==3&&parts[0] in setOf("advanced","creative")&&parts[1] in ui.tasks&&
            pages.any{it.ref==parts[1]}&&parts[2].matches(Regex("[a-z0-9_]{1,30}"))
    }
    override fun onSaveInstanceState(outState:Bundle){
        super.onSaveInstanceState(outState)
        outState.putLong("study_elapsed_ms",studyTimeMs())
        val base=f
        if(base==null){
            pendingStack?.let{outState.putStringArrayList("ui_stack",it)}
            pendingGroupPages?.let{outState.putBundle("ui_group_pages",it)}
            return
        }
        val safe=if(stack.any{it.screen in parentScreens})listOf(Page("SCR-003")) else stack
        outState.putStringArrayList("ui_stack",ArrayList(safe.map(::encodePage)))
        val state=Bundle()
        groupStateLimits(base.ui,safe).forEach{(key,max)->
            val n=groupPages[key]?:0
            if(n in 1..max)state.putInt(key,n)
        }
        groupPages.entries.filter{validInteractionPageKey(base.ui,safe,it.key)&&it.value in 1..660}.take(80).forEach{state.putInt(it.key,it.value)}
        outState.putBundle("ui_group_pages",state)
    }
    private fun stopAudio(){f?.audio?.stop();f?.backend?.settings=settings}
    private fun active(action:()->Unit):()->Unit={if(resumed&&!closed)action()}
    private fun playNonTask(path:String?){
        if(!resumed||closed||path==null)return
        val base=f?:return;require(path in base.catalog.paths && path.startsWith("audio/") && path.endsWith(".ogg"))
        base.audioPolicy.libraryRequest(path,true)?.let(base.audio::request)
    }
    private fun taskVoice(task:UiTask,event:AudioEvent){
        motionPreview?.stop()
        val base=f?:return;val state=interaction?.takeIf{it.task.id==task.id}
        base.audioPolicy.request(task.id,event,true,attempted=state?.attempted==true,verdict=state?.verdict)?.let(base.audio::request)
    }
    private fun leaveListening(){f?.listening?.leave();preludeOpenedFor=null;listeningFailed=false}
    private fun depart(){stopMotion();clearConfirmationFor=null;if(f?.listening?.taskId!=null)leaveListening()else stopAudio();f?.backend?.settings=settings;interaction?.discardTransient()}
    private fun push(p:Page){depart();stack.add(p);render()}
    private fun replace(p:Page){depart();if(stack.isNotEmpty())stack[stack.lastIndex]=p else stack.add(p);render()}
    private fun back(){depart();val wasParent=page.screen in parentScreens;if(stack.size>1){stack.removeAt(stack.lastIndex);if(wasParent && page.screen !in parentScreens)f?.parent?.close();if(page.screen in parentScreens && page.screen!="SCR-025" && f?.parent?.allowed()!=true)home() else render()}else home()}
    private fun home(){depart();interaction=null;gardenSelection=null;f?.parent?.close();stack.clear();stack.add(Page("SCR-003"));f?.navigator?.go(Route.Home);render()}
    private fun controller(base:Foundation,t:UiTask):TaskInteraction{
        interaction?.takeIf{it.task.id==t.id}?.let{return it}
        val result=TaskInteraction(base.catalog,t)
        base.progress.loadSession()?.takeIf{it.optional("task_id")==t.id}?.let{result.restoreFields(it)}
        interaction=result;return result
    }
    private fun saveInteraction(state:TaskInteraction){
        val base=f?:return
        base.progress.saveSession(base.sessions.snapshotForTask(state.task.id)+state.saveFields())
    }
    private fun refreshTask(){
        fun scrolls(v:View):List<android.widget.ScrollView> = if(v is android.widget.ScrollView)listOf(v) else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{scrolls(v.getChildAt(it))}else emptyList()
        val positions=scrolls(window.decorView).map{it.scrollY};val before=page
        render()
        val now=scrolls(window.decorView)
        now.forEachIndexed{i,sc->sc.post{if(page==before)sc.scrollTo(0,positions.getOrElse(i){0})}}
    }
    private fun changeResponse(state:TaskInteraction,change:()->Boolean){
        if(page.ref!=state.task.id||interaction!==state||state.finished||!resumed||closed)return
        if(change())try{state.responseChanged();saveInteraction(state);refreshTask()}catch(e:Exception){reportRenderFailure(page,e);showError(page)}
    }
    private fun submitTask(base:Foundation,state:TaskInteraction){
        if(page.ref!=state.task.id||!resumed||closed)return
        val result=state.submit(base.listening.receipt(state.task.id))
        if(result is Verdict.Completed){
            try{base.progress.commit(result.ticket)}catch(e:Exception){reportRenderFailure(page,e);state.discardVerdict();showError(page);return}
        }
        refreshTask()
        val event=when(result){is Verdict.Completed->AudioEvent.CORRECT;Verdict.Retry->AudioEvent.RETRY;else->null}
        if(event!=null){
            val request=base.audioPolicy.request(state.task.id,event,true,attempted=state.attempted,verdict=result)
            if(request!=null&&request.paths.all{settings.playback(it)!=null})base.audio.request(request)
            else if(!state.task.listen&&!state.task.prelude&&settings.effects&&
                (result==Verdict.Retry||result is Verdict.Completed&&result.ticket.kind==CompletionKind.GRADED)){
                val id=if(result==Verdict.Retry)"AUD-FX-0010" else "AUD-FX-0009"
                base.audioPolicy.libraryRequest(base.catalog.assetPath(id,"audio"),true)?.let(base.audio::request)
            }
        }
    }
    private fun welcomeDone(){prefs.edit().putBoolean("welcome_seen",true).apply();home()}
    private fun openTask(id:String,restore:Boolean=false){
        val base=f?:return;val task=base.ui.tasks.getValue(id)
        depart();interaction=null
        if(!restore){
            if(task.ready||task.prelude){base.navigator.go(Route.TaskRoute(id));base.progress.saveSession(base.sessions.snapshotForTask(id))}
            push(Page(task.screen,id))
        }else{
            val lesson=base.ui.lessonForTask(id);val topic=base.ui.topics.getValue(lesson.topic);val area=base.ui.areas.single{topic.card.id in it.topics}
            stack.clear();stack.addAll(listOf(Page("SCR-003"),Page("SCR-004",area.card.id),Page("SCR-005",topic.card.id),Page("SCR-006",lesson.card.id),Page("SCR-007",lesson.card.id),Page(task.screen,id)))
            if(task.ready||task.prelude){base.navigator.go(Route.TaskRoute(id));if(base.progress.loadSession()?.optional("task_id")!=id)base.progress.saveSession(base.sessions.snapshotForTask(id))};render()
        }
    }
    private fun nextWithoutReward(task:UiTask){
        val base=f?:return;val level=base.ui.levels.getValue(task.level);val next=level.tasks.getOrNull(level.tasks.indexOf(task.id)+1)
        interaction=null
        if(next==null){replace(Page("SCR-021",task.level));return}
        val t=base.ui.tasks.getValue(next);if(t.ready||t.prelude){base.navigator.go(Route.TaskRoute(next));base.progress.saveSession(base.sessions.snapshotForTask(next))}
        replace(Page(t.screen,next))
    }
    private fun fixedBackground(ui:UiContent,id:String)=ui.screenArt(id).firstOrNull{it.startsWith("art/img_bg_")}
    private fun fixedFigure(ui:UiContent,id:String)=ui.screenArt(id).firstOrNull{!it.startsWith("art/img_bg_")}
    private fun reportRenderFailure(p:Page?,error:Throwable){
        // Development diagnostics contain screen/task identifiers only, never progress payloads.
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
            android.util.Log.e("HTQ_RENDER","Native screen failure: "+(p?.screen?:"load")+" "+(p?.ref?:""),error)
    }
    private fun showError(previous:Page?){
        errorReturn=previous
        mount(w.shell(appTitle,null,null,null){body->
            runCatching{w.art(body,"art/img_chr_0074.webp",180)}
            w.add(body,w.text("Bé thử lại nhé!",24,true))
            w.add(body,w.button("Thử lại","teal",action=active{if(f==null)load() else{val p=errorReturn;if(p!=null && p.screen!="SCR-033")replace(p) else home()}}))
            if(f!=null)w.add(body,w.button("Về nhà",action=active(::home)))
        })
    }
    private fun frame(ui:UiContent,p:Page,heading:String,body:(LinearLayout)->Unit,background:String?=null,top:Boolean=true,footer:((LinearLayout)->Unit)?=null){
        val isHome=p.screen=="SCR-003";val isWelcome=p.screen=="SCR-002"
        val backdrop=isHome || isWelcome || ui.tasks[p.ref]?.screen==p.screen
        mount(w.shell(heading,background,if(!isHome&&!isWelcome&&top)active(::back)else null,if(!isHome&&!isWelcome&&top)active(::home)else null,backdrop=backdrop,footer=footer){b->
            body(b)
            f?.let{base->menuMusic(base,p)?.let{path->w.pair(b,w.button("Nhạc nền","white",action=active{playNonTask(path)}),w.button("Dừng nhạc","white",action=active(::stopAudio)))}}
        })
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
        stopMotion()
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
        }catch(e:Exception){reportRenderFailure(p,e);showError(p)}
    }
    private fun homeScreen(base:Foundation,p:Page){val ui=base.ui
        frame(ui,p,appTitle,{b->
            val perPage=when{w.viewport.wide->2;w.viewport.fontScale>=1.25f && w.viewport.width<400->4;else->6}
            paginated(ui.areas.map{it.card},perPage,p,b){cards->
                val fills=listOf("green","blue","peach","lilac","yellow","white")
                w.grid(b,cards.map{c->w.card(c.title,c.image,fills[ui.areas.indexOfFirst{it.card.id==c.id}],if(w.viewport.wide)76 else if(w.viewport.width<360)74 else 90,active{if(c.id=="AREA-04")push(Page("SCR-012"))else push(Page("SCR-004",c.id))})},2,112)
            }
            val resume=runCatching{base.progress.loadSession()?.s("task_id")}.getOrNull()?.takeIf{it in ui.tasks}
            if(resume!=null)action(b,"Chơi tiếp","teal",fn={push(Page("SCR-020",resume))})
        },fixedBackground(ui,p.screen),footer={b->
            w.pair(b,w.button("Khu vườn","green",action=active{push(Page("SCR-023"))}),w.button("Phụ huynh","white",adult=true,action=active{challengeA=Random.nextInt(10,50);challengeB=Random.nextInt(10,50);push(Page("SCR-025"))}))
        })
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
            paginated(list,1,p,b){items->val c=items.single();c.images.firstOrNull()?.let{w.art(b,it,230,"Tranh tiếng Anh")};w.add(b,w.text(c.text,if(phrases)36 else 44,true));listen(b,c.audio,"Nghe tiếng Anh")
                if(phrases)c.meaning?.let{meaning->if(p.extra)w.add(b,w.text(meaning,20))else action(b,"Xem nghĩa","white",fn={replace(p.copy(extra=true))})}
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
            if(settings.remind&&studyTimeMs()>=settings.minutes*60_000L){
                w.add(b,w.text("Đến giờ nghỉ một chút rồi!",22,true))
                action(b,"Về nhà nghỉ ngơi","teal",fn={elapsedStudyMs=0L;foregroundBegan=SystemClock.elapsedRealtime();home()})
            }
            action(b,"Về bài học","white",fn=::back);action(b,"Về nhà","green",fn=::home)
        })
    }
    private fun rewardScreen(base:Foundation,p:Page){val ui=base.ui;val rewards=earned(base,p.ref)
        val level=ui.levels.getValue(p.ref)
        val claimed=if(p.extra)level.tasks.getOrNull(p.index)?.let{task->base.progress.readableDatabase.rawQuery("SELECT item_id FROM reward_ledger WHERE task_id=? AND claimed=1",arrayOf(task)).use{c->if(c.moveToFirst())c.getString(0)else null}}else null
        frame(ui,p,"Quà của bé",{b->
            if(claimed!=null){
                ui.screenArt(p.screen).firstOrNull{it.endsWith("0026.webp")}?.let{w.art(b,it,150,"Hộp quà đã mở")}
                ui.rewardArt[claimed]?.let{w.art(b,it,180,"Vật phẩm của bé")}
                heading(b,"Bé đã nhận quà!")
                if(rewards.isNotEmpty())action(b,"Quà tiếp theo","teal",fn={replace(p.copy(index=0,extra=false))})
            }else if(rewards.isEmpty()){w.add(b,w.text("Chưa có quà mới",24,true))}
            else {ui.screenArt(p.screen).firstOrNull{it.endsWith("0027.webp")}?.let{w.art(b,it,200,"Hộp quà chưa mở")}
                action(b,"Mở quà","teal",fn={
                    val entry=rewards.first()
                    if(base.progress.claim(entry.task))replace(p.copy(index=level.tasks.indexOf(entry.task),extra=true))else refreshTask()
                })
            }
            action(b,"Để sau","white",fn=::back);action(b,"Túi đồ","green",fn={push(Page("SCR-024"))})
        })
    }
    private fun parentSettings(base:Foundation,p:Page){
        var draft=settings
        fun preview(path:String){
            if(!base.parent.allowed()||page!=p)return
            base.audio.stop();base.backend.settings=draft
            base.audioPolicy.libraryRequest(path,true)?.let{request->base.audio.request(request){base.backend.settings=settings}}
        }
        frame(base.ui,p,base.ui.screenNames.getValue(p.screen),{b->
            if(p.extra)w.add(b,w.text("Đã lưu cài đặt",20,true))
            fun toggle(label:String,checked:Boolean,update:(Boolean)->Unit)=w.add(b,w.adultSwitch(label,checked,update))
            fun slider(label:String,initial:Int,max:Int,format:(Int)->String,update:(Int)->Unit)=w.add(b,w.adultSlider(label,initial,max,format,update))
            val percent:(Int)->String={"${it}%"}
            when(p.screen){
                "SCR-028"->{
                    toggle("Giọng cô",draft.voice){draft=draft.copy(voice=it)}
                    slider("Âm lượng giọng nói",draft.speechVolume,100,percent){draft=draft.copy(speechVolume=it)}
                    toggle("Tiếng Anh",draft.english){draft=draft.copy(english=it)}
                    toggle("Nhạc nền",draft.music){draft=draft.copy(music=it)}
                    slider("Âm lượng nhạc",draft.musicVolume,100,percent){draft=draft.copy(musicVolume=it)}
                    toggle("Hiệu ứng",draft.effects){draft=draft.copy(effects=it)}
                    slider("Âm lượng hiệu ứng",draft.effectVolume,100,percent){draft=draft.copy(effectVolume=it)}
                    slider("Tốc độ nghe",draft.speedIndex,4,{listOf("0,75×","0,85×","1×","1,15×","1,25×")[it]}){draft=draft.copy(speedIndex=it)}
                    w.add(b,w.text("Bài nghe bắt buộc cần bật âm thanh phù hợp. Nhạc nền chỉ phát khi bé chọn trong menu.",18,center=false))
                    action(b,"Nghe mẫu","white",adult=true,fn={preview(base.ui.welcomeAudio?:base.ui.letters.first().audio)})
                    action(b,"Thử tiếng Anh","white",adult=true,fn={preview(base.ui.vocabulary.first().audio)})
                    action(b,"Thử nhạc","white",adult=true,fn={preview(base.catalog.assetPath("AUD-MUS-0001","audio"))})
                    action(b,"Thử hiệu ứng","white",adult=true,fn={preview(base.catalog.assetPath("AUD-FX-0001","audio"))})
                    action(b,"Dừng","white",adult=true,fn=::stopAudio)
                }
                "SCR-029"->{
                    slider("Thời lượng sử dụng đề xuất",draft.minutes-5,55,{"${it+5} phút"}){draft=draft.copy(minutes=it+5)}
                    toggle("Nhắc nghỉ giữa các hoạt động",draft.remind){draft=draft.copy(remind=it)}
                    w.add(b,w.text("Ứng dụng nhắc nghỉ ở cuối hoạt động, không tự khóa bài học.",18,center=false))
                }
                else->{
                    w.add(b,w.text("Chạm thay kéo luôn được hỗ trợ: chạm vật rồi chạm nơi muốn đặt.",18,center=false))
                    toggle("Chữ lớn",draft.largeText){draft=draft.copy(largeText=it)}
                    toggle("Giảm chuyển động",draft.reducedMotion){draft=draft.copy(reducedMotion=it)}
                    toggle("Nhạc nền",draft.music){draft=draft.copy(music=it)}
                    w.add(b,w.text("Giảm chuyển động tắt hiệu ứng chạm và dùng tranh tĩnh hoặc từng bước thay cho minh họa động đã hỗ trợ. Nội dung và đáp án không thay đổi.",18,center=false))
                }
            }
            action(b,"Lưu cài đặt","teal",adult=true,fn={
                if(!base.parent.allowed()){home()}else{
                    stopAudio()
                    if(draft.save(prefs)){settings=draft;applySettings();replace(p.copy(extra=true))}else showError(page)
                }
            })
        })
    }
    private fun parentReset(base:Foundation,p:Page){
        frame(base.ui,p,"Đặt lại tiến trình",{b->
            w.add(b,w.text("Thao tác đặt lại sẽ xóa tiến trình học và khu vườn. Kho bài học không bị xóa.",20,center=false))
            if(!p.extra){
                action(b,"Hủy","white",adult=true,fn=::back)
                action(b,"Tiếp tục xác nhận","peach",adult=true,fn={replace(p.copy(extra=true))})
            }else{
                w.add(b,w.text("Xác nhận lần hai: thao tác không thể hoàn tác.",18,center=false))
                action(b,"Hủy","white",adult=true,fn={replace(p.copy(extra=false))})
                action(b,"Xác nhận đặt lại","peach",adult=true,fn={
                    if(!base.parent.allowed()){home();return@action}
                    base.progress.resetProgress();interaction=null;gardenUndo=null;gardenSelection=null;groupPages.clear();home()
                })
            }
        })
    }
    private data class GardenEntry(val task:String,val item:String,val x:Double?,val y:Double?)
    private var gardenUndo:GardenEntry?=null
    private fun garden(base:Foundation,p:Page){val ui=base.ui
        val owned=mutableListOf<GardenEntry>()
        base.progress.readableDatabase.rawQuery("SELECT l.task_id,l.item_id,p.x,p.y FROM reward_ledger l LEFT JOIN garden_placements p ON p.task_id=l.task_id WHERE l.claimed=1 ORDER BY l.task_id",null).use{c->while(c.moveToNext()){
            owned.add(GardenEntry(c.getString(0),c.getString(1),if(c.isNull(2))null else c.getDouble(2),if(c.isNull(3))null else c.getDouble(3)))
        }}
        val placed=owned.filter{it.x!=null&&it.y!=null};val available=owned.filter{it.x==null&&it.y==null}
        val items=placed.mapNotNull{e->ui.rewardArt[e.item]?.let{GardenWidgets.GardenItem(it,e.x!!.toFloat(),e.y!!.toFloat())}}
        val selected=available.firstOrNull{it.task==gardenSelection}
        fun put(e:GardenEntry,x:Double,y:Double){
            if(base.progress.place(e.task,x,y)){gardenUndo=e;gardenSelection=null;if(p.ref=="positions")replace(Page("SCR-023"))else refreshTask()}
        }
        frame(ui,p,"Khu vườn của bé",{b->
            if(p.ref=="place"||p.ref=="away"){
                val choosing=p.ref=="place";val list=if(choosing)available else placed
                heading(b,if(choosing)"Chọn vật để đặt" else "Chọn vật để cất")
                if(list.isEmpty())w.add(b,w.text("Không có vật phẩm",22))else paginated(list,w.viewport.pageSize(6),p,b){rows->
                    w.grid(b,rows.mapIndexed{i,e->w.card(if(choosing)"Đặt vật ${i+1}" else "Cất vật ${i+1}",ui.rewardArt.getValue(e.item),"white",action=active{
                        if(choosing){gardenSelection=e.task;replace(Page("SCR-023"))}
                        else{base.progress.putAway(e.task);gardenUndo=e;replace(Page("SCR-023"))}
                    })},2)
                }
                action(b,"Hủy","white",fn={gardenSelection=null;replace(Page("SCR-023"))})
            }else{
                val onPlace:((Double,Double)->Unit)?=selected?.let{e->{x,y->if(resumed&&!closed&&page.screen=="SCR-023")put(e,x,y)}}
                ui.screenArt(p.screen).firstOrNull()?.let{w.add(b,w.gardenScene(it,items,onPlace),8,300)}
                if(selected!=null){
                    heading(b,"Chạm vào chỗ bé muốn đặt")
                    // Equivalent accessible alternative to a precise pointer gesture.
                    action(b,"Chọn vị trí bằng nút","white",fn={replace(Page("SCR-023","positions"))})
                    if(p.ref=="positions"){
                        val labels=listOf("Trên trái","Trên giữa","Trên phải","Giữa trái","Chính giữa","Giữa phải","Dưới trái","Dưới giữa","Dưới phải")
                        w.grid(b,labels.mapIndexed{i,label->w.button(label,"white",action=active{put(selected,(i%3+0.5)/3,(i/3+0.5)/3)})},3,80)
                    }
                    action(b,"Hủy","white",fn={gardenSelection=null;replace(Page("SCR-023"))})
                }else{
                    w.add(b,w.text("Ngắm vườn",20,true))
                    action(b,"Túi đồ","teal",fn={push(Page("SCR-024"))})
                    if(available.isNotEmpty())action(b,"Đặt vật","white",fn={replace(Page("SCR-023","place"))})
                    if(placed.isNotEmpty())action(b,"Cất vật","white",fn={replace(Page("SCR-023","away"))})
                    if(gardenUndo!=null)action(b,"Hoàn tác","white",fn={gardenUndo?.let{e->if(e.x==null||e.y==null)base.progress.putAway(e.task)else base.progress.place(e.task,e.x,e.y);gardenUndo=null;refreshTask()}})
                }
            }
        })
    }
    private fun inventory(base:Foundation,p:Page){val ui=base.ui;val owned=base.progress.inventoryBreakdown();frame(ui,p,"Túi đồ",{b->
        if(owned.isEmpty())w.add(b,w.text("Túi đồ đang trống",24))else paginated(owned,w.viewport.pageSize(6),p,b){items->
            w.grid(b,items.map{e->w.column().apply{
                background=w.rounded("white");setPadding(w.dp(8),w.dp(8),w.dp(8),w.dp(8))
                ui.rewardArt[e.itemId]?.let{w.art(this,it,120,"Vật phẩm")}
                w.add(this,w.text("× ${e.total}",24,true))
                w.add(this,w.text("Trong túi: ${e.inBag}",18),4)
                w.add(this,w.text("Đã đặt: ${e.placed}",18),4)
            }},2)
        }
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
    private fun parentProgress(base:Foundation,p:Page){
        val completed=linkedMapOf<String,String>()
        base.progress.readableDatabase.rawQuery("SELECT task_id,kind FROM completions",null).use{c->while(c.moveToNext())completed[c.getString(0)]=c.getString(1)}
        frame(base.ui,p,"Theo dõi học tập",{b->
            w.add(b,w.text("Nhiệm vụ đã ghi nhận: ${completed.size}",20,true))
            paginated(base.ui.topics.values.toList(),4,p,b){items->items.forEach{t->
                val ids=t.lessons.flatMap{base.ui.lessons.getValue(it).levels}.flatMap{base.ui.levels.getValue(it).tasks}
                val finished=ids.count{completed[it]=="validated_completion"}
                val participated=ids.count{completed[it]=="explicit_participation_done"}
                w.add(b,w.column().apply{
                    background=w.rounded("white");setPadding(w.dp(12),w.dp(8),w.dp(12),w.dp(8))
                    w.add(this,w.text(t.card.title,20,true,center=false),0)
                    w.add(this,w.text("Đã hoàn thành: $finished · Đã tham gia: $participated",18,center=false),4)
                    w.add(this,w.text("Tổng nhiệm vụ: ${ids.size}",18,center=false),4)
                })
            }}
            w.add(b,w.text("Chưa có thống kê lịch sử mở bài. Có thể cùng bé ôn lại bài đã học.",18,center=false))
        })
    }
    private fun parentAbout(base:Foundation,p:Page){frame(base.ui,p,"Thông tin ứng dụng",{b->
        when(p.ref){
            "font"->{
                heading(b,"Noto Sans — SIL Open Font License 1.1")
                val notice=assets.open("htq-font/LICENSE-NotoSans.txt").bufferedReader(Charsets.UTF_8).use{it.readText()}
                w.add(b,w.text(notice,18,center=false))
            }
            "audio"->{
                heading(b,"Nguồn âm thanh")
                w.add(b,w.text("Ghi nhận theo hồ sơ tài nguyên gốc. Một số nguồn vẫn cần xác minh trước khi phát hành.",18,center=false))
                val credits=base.catalog.open("credits/audio_attributions.json").bufferedReader(Charsets.UTF_8).use{Json.parse(it.readText()).arr()}
                    .map{it.obj()}.filter{it.o("attribution").isNotEmpty()}
                paginated(credits,4,p,b){rows->rows.forEach{r->
                    val a=r.o("attribution")
                    val fields=listOf("title","artist","voice","generator","license","license_url","source_url","changes","attribution").mapNotNull{k->a.optional(k)?.takeIf{it.isNotBlank()}}.distinct()
                    w.add(b,w.text(fields.joinToString("\n"),18,center=false))
                    if(r.s("license_review_status")!="retain_prior_record_evidence")w.add(b,w.text("Cần rà soát trước khi phát hành",18,true,center=false),4)
                }}
            }
            else->{
                w.add(b,w.text(appTitle,28,true))
                w.add(b,w.text("Ứng dụng học tập ngoại tuyến, không tài khoản, không quảng cáo và không thu âm hoặc ghi hình.",18,center=false))
                w.add(b,w.text("Bộ nội dung Hello Thục Quyên: 17 chủ đề, 110 bài học. Phiên bản nội dung: ${base.catalog.version}.",18,center=false))
                w.add(b,w.text("Bản phát triển chưa phát hành chính thức. Không phải chứng nhận chuyên môn; một số nguồn âm thanh còn phải xác minh quyền sử dụng.",18,center=false))
                action(b,"Giấy phép chữ Noto Sans","white",adult=true,fn={push(Page("SCR-032","font"))})
                action(b,"Nguồn âm thanh","white",adult=true,fn={push(Page("SCR-032","audio"))})
            }
        }
        action(b,"Quay lại","white",adult=true,fn=::back)
    })}
    private fun taskScreen(base:Foundation,p:Page,source:UiTask){val ui=base.ui;val state=controller(base,source)
        if(source.prelude && base.listening.taskId!=source.id){base.listening.enter(source.id);preludeOpenedFor=null;listeningFailed=false}
        if(source.prelude && (base.listening.receipt(source.id)==null||preludeOpenedFor!=source.id)){
            frame(ui,p,"Nghe trước nhé!",{b->
                w.taskColumns(b,{lead->
                    action(lead,"Nghe câu chuyện","teal",fn={
                        listeningFailed=false
                        base.listening.start(source.id){ok->if(!closed&&resumed&&page.ref==source.id){listeningFailed=!ok;refreshTask()}}
                        refreshTask()
                    })
                    action(lead,"Dừng","white",fn={base.listening.leave();base.listening.enter(source.id);listeningFailed=false;refreshTask()})
                    if(listeningFailed)w.add(lead,w.text("Bé nghe lại nhé!",20))
                    else if(base.audio.active)w.add(lead,w.text("Đang nghe",20))
                },{scene->source.preludeImage?.let{w.art(scene,it,if(w.taskSplit)180 else 240,"Cảnh mở đầu")}})
                action(b,"Tiếp tục","green",enabled=base.listening.receipt(source.id)!=null,fn={
                    if(base.listening.receipt(source.id)!=null){preludeOpenedFor=source.id;refreshTask()}
                })
                w.pair(b,w.button("Nghỉ","white",action=active{push(Page("SCR-019",source.id))}),w.button("Bỏ qua","green",action=active{nextWithoutReward(source)}))
            },source.background);return
        }
        val t=if(source.prelude)source.copy(ready=true,groups=source.afterPrelude,prelude=false)else source
        if(!t.ready){frame(ui,p,"Bé chơi nhé!",{b->w.add(b,w.text("Bé chọn hoạt động khác nhé.",24,true));action(b,"Quay lại","white",fn=::back)});return}
        val level=ui.levels.getValue(t.level);val current=level.tasks.indexOf(t.id)+1
        frame(ui,p,"$current / ${level.tasks.size}",{b->
            w.taskColumns(b,{lead->
                heading(lead,t.prompt)
                if(w.taskSplit){action(lead,"Nghe lại","teal",fn={taskVoice(t,AudioEvent.PROMPT)});action(lead,"Dừng","white",fn=::stopAudio)}else w.pair(lead,w.button("Nghe lại","teal",action=active{taskVoice(t,AudioEvent.PROMPT)}),w.button("Dừng","white",action=active(::stopAudio)))
                if(t.listen && !source.prelude && t.groups.none{it.role=="listen_options"||it.role=="sources"&&it.items.any{e->e==UiElement.Speaker}})action(lead,"Nghe mẫu","blue",fn={taskVoice(t,AudioEvent.LISTEN)})
            },{scene->
                val advanced=state.advanced;val creative=state.creative
                val commit:(()->Boolean)->Unit={change->changeResponse(state,change)}
                val refresh:()->Unit={if(resumed&&!closed&&page==p)refreshTask()}
                when{
                    advanced!=null->AdvancedTaskViews(w,advanced,state.finished,groupPages,seed,commit,refresh,
                        active{if(page==p)base.audioPolicy.resultRequest(advanced,true)?.let(base.audio::request)}).draw(scene)
                    creative!=null->CreativeTaskViews(w,creative,state.finished,groupPages,commit,refresh,
                        {id->if(resumed&&!closed&&page==p)base.audioPolicy.objectRequest(t.id,id,true)?.let(base.audio::request)},
                        {ids->if(resumed&&!closed&&page==p)base.audioPolicy.rhythmRequest(t.id,ids,true)?.let(base.audio::request)},
                        active(::stopAudio)).draw(scene)
                    else->t.groups.forEachIndexed{index,g->renderGroup(scene,p,t,index,g,w.taskBoardDp,state)}
                }
            })
            if(state.supported){
                if(base.taskMotion.available(state)){
                    val preview=MotionPreview(w,settings.reducedMotion,
                        allowed={resumed&&!closed&&page==p&&interaction===state&&
                            (!source.prelude||base.listening.receipt(source.id)!=null)&&base.taskMotion.available(state)},
                        request={reduced->base.taskMotion.request(state,true,reduced)},
                        beforePlayback={stopAudio()})
                    motionPreview=preview;preview.attach(b)
                }
                when(val v=state.verdict){
                    is Verdict.Completed -> w.add(b,w.text(if(v.ticket.kind==CompletionKind.OPEN)"Cảm ơn bé đã tham gia!" else "Bé làm tốt lắm!",24,true))
                    Verdict.Retry -> w.add(b,w.text("Bé thử thêm một lần nhé!",22,true))
                    else->Unit
                }
                if(!state.finished){
                    if(state.isMemory)action(b,"Úp thẻ","white",enabled=state.faceUpIds.size>state.matchedPairs.size*2,fn={changeResponse(state,state::closeFlipped)})
                    else if(t.screen!="SCR-069")w.pair(b,w.button("Hoàn tác","white",action=active{changeResponse(state,state::undo)}),w.button("Làm lại","white",action=active{
                        if(state.creative!=null){clearConfirmationFor=state.task.id;refreshTask()}
                        else changeResponse(state,state::clear)
                    }))
                    if(clearConfirmationFor==state.task.id&&state.creative!=null){
                        w.add(b,w.text("Xóa phần bé đã làm?",22,true))
                        w.pair(b,w.button("Hủy","white",action=active{clearConfirmationFor=null;refreshTask()}),
                            w.button("Xóa","peach",action=active{clearConfirmationFor=null;changeResponse(state,state::clear)}))
                    }
                    if(state.type!="participation")action(b,"Gợi ý","white",enabled=state.attempted,fn={taskVoice(t,AudioEvent.HINT)})
                    action(b,"Xong","teal",enabled=state.response()!=null,fn={submitTask(base,state)})
                }else action(b,"Tiếp tục","teal",fn={nextWithoutReward(t)})
            }else{
                // Do not grant credit for an unwired mechanic. Its approved scene stays visible.
                when(t.screen){
                    "SCR-061"->action(b,"Úp thẻ","white",enabled=false)
                    "SCR-062"->w.pair(b,w.button("Hoàn tác","white",enabled=false),w.button("Làm lại bước","white",enabled=false))
                    "SCR-063"->w.pair(b,w.button("Hoàn tác","white",enabled=false),w.button("Tẩy","white",enabled=false))
                    "SCR-064"->w.pair(b,w.button("Hoàn tác","white",enabled=false),w.button("Chọn màu","white",enabled=false))
                    "SCR-068"->action(b,"Xem kết quả","white",enabled=false)
                    else->if(t.screen.removePrefix("SCR-").toInt() in 34..60)action(b,"Gợi ý","white",enabled=false)
                }
                if(t.screen!="SCR-061" && t.screen!="SCR-068")action(b,"Xong","white",enabled=false)
            }
            if(state.finished)action(b,"Nghỉ","white",fn={push(Page("SCR-019",t.id))})
            else w.pair(b,w.button("Nghỉ","white",action=active{push(Page("SCR-019",t.id))}),w.button("Bỏ qua","green",action=active{nextWithoutReward(t)}))
        },t.background)
    }
    private fun renderGroup(b:LinearLayout,p:Page,t:UiTask,index:Int,g:UiGroup,availableDp:Int,state:TaskInteraction){
        val items=if(g.shuffle)g.items.shuffled(Random(seed xor t.id.hashCode().toLong() xor index.toLong()))else g.items
        if(g.role.startsWith("compare_")){
            // Same-size source art, quantity unchanged. Close/wide spacing differs, not scale.
            val r=w.row().apply{gravity=android.view.Gravity.CENTER};val cell=minOf(44,((availableDp-12*(items.size-1))/items.size).coerceAtLeast(1))
            items.forEachIndexed{i,e->if(e is UiElement.Image){r.addView(w.image(e.path),LinearLayout.LayoutParams(w.dp(cell),w.dp(cell)).apply{if(i>0)marginStart=w.dp(if(g.role=="compare_wide")minOf(12,(availableDp-cell*items.size)/(items.size.coerceAtLeast(2)-1))else 2)})}}
            w.add(b,r);return
        }
        val key=groupKey(t,index,g)
        val partitions=g.pageIndices(w.viewport)
        val chunks=partitions.mapIndexed{n,indices->
            val ordered=if(g.shuffle)indices.shuffled(Random(seed xor t.id.hashCode().toLong() xor index.toLong() xor (n.toLong() shl 32)))else indices
            ordered
        }
        fun committed(n:Int):Boolean=!g.advanceRequiresCommit ||
            if(g.role=="sequence_slots")state.selectedIds.size>=partitions.take(n+1).sumOf{it.size}
            else state.pageCommitted(partitions[n].mapNotNull{g.objectIds.getOrNull(it)})
        var maxPage=0
        while(maxPage<chunks.lastIndex&&committed(maxPage))maxPage++
        val pageIndex=(groupPages[key]?:0).coerceIn(0,maxPage)
        groupPages[key]=pageIndex
        val offset=partitions.take(pageIndex).sumOf{it.size}
        val context=g.role in setOf("context","trace","hotspot","workbench","board")
        val heading=when(g.role){"sources"->"Thẻ hình";"destinations"->"Nhóm ghép";"reserve"->"Bên ngoài";"workbench"->"Sân chơi";"palette"->"Chọn hình hoặc màu";"decorations"->"Họa tiết";"sequence_slots"->"Sắp xếp";"mix_sources"->"Màu để pha";"mix_result"->"Pha màu";"listen_options"->"Nghe từng mẫu";"sample_glyph"->"Chữ mẫu";else->null};heading?.let{w.add(b,w.text(it,18,true),8)}
        val views=chunks[pageIndex].mapIndexed{i,sourceIndex->
            val id=g.objectIds.getOrNull(sourceIndex)
            val initial=g.items[sourceIndex]
            val slot=if(state.isSequence && g.role in setOf("sequence_slots","pattern") && initial==UiElement.Blank)g.items.take(sourceIndex).count{it==UiElement.Blank}else -1
            val filled=if(slot>=0)state.selectedIds.getOrNull(slot)?.let(state::chosenVisual)else null
            val e=if(id!=null && g.role=="memory")state.memoryFace(id)?:initial else filled?:initial
            val label=when(g.role){"choices"->"Lựa chọn ${offset+i+1}";"memory"->"Thẻ ${i+1}";"sources"->"Thẻ nguồn ${i+1}";"destinations"->"Thẻ đích ${i+1}";"sample_glyph"->"Chữ mẫu";"word"->"Chữ ${(e as UiElement.Glyph).text}, vị trí ${offset+i+1}";"direction_endpoints"->if(sourceIndex==0)"Bên trái" else "Bên phải";else->"Tranh ${i+1}"}
            val cell=w.column().apply{background=w.rounded("white");setPadding(w.dp(6),w.dp(6),w.dp(6),w.dp(6));minimumHeight=w.dp(64);contentDescription=label;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
            when(e){
                is UiElement.Image->{
                    val zoom=g.role=="hotspot" && groupPages["zoom:$key"]==1
                    if(g.role=="hotspot"){
                        w.add(cell,w.zoomArt(e.path,zoom),0,if(w.viewport.wide)220 else 320)
                        w.add(cell,w.button(if(zoom)"Thu nhỏ" else "Xem lớn","white",action=active{groupPages["zoom:$key"]=if(zoom)0 else 1;render()}))
                    }else w.art(cell,e.path,if(context)220 else if(w.viewport.wide)96 else 120)
                }
                is UiElement.Glyph->w.add(cell,w.text(e.text,when{g.role=="sample_glyph"||t.screen=="SCR-044"->80;g.role=="word"->52;else->48},true),0)
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
            }
            if(id!=null && state.supported){
                cell.tag=id
                val marked=state.isSelected(id)||state.number(id)!=null||state.linkedNumbers(id).isNotEmpty()
                if(marked){cell.background=w.rounded("blue","navy");cell.isSelected=true}
                if(g.role=="direction_endpoints")w.add(cell,w.text(label,20,true),0)
                if(state.isMemory&&id in state.faceUpIds && state.matchedPairs.any{id==it.first||id==it.second})w.add(cell,w.text("Đã ghép",18,true),0)
                if(state.isPairing){
                    state.number(id)?.let{w.add(cell,w.text("$it",22,true),0)}
                    val labels=state.linkedNumbers(id);if(labels.isNotEmpty())w.add(cell,w.text(labels.joinToString(" · "),22,true),0)
                }
                if(state.canTap(g.role,id)){cell.isFocusable=true;cell.setOnClickListener{changeResponse(state){state.tap(g.role,id)}}}
                val request=f!!.audioPolicy.objectRequest(t.id,id,true)
                if(request!=null)w.add(cell,w.button("Nghe mẫu","white",action=active{f?.audio?.request(request)}),4)
            }
            cell
        }
        if(g.role=="word"){
            // One source line per group; spaces are layout, never selectable fake glyphs.
            val gap=ApprovedUi.gapDp
            val needed=views.size*80+(views.size-1)*gap+g.wordBreaks.size*16
            if(availableDp>=needed){
                val line=w.row().apply{tag="htq_word_line"}
                views.forEachIndexed{i,v->line.addView(v,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{
                    if(i>0)marginStart=w.dp(gap+if(i in g.wordBreaks)16 else 0)
                })}
                w.add(b,line)
            }else{
                // If a narrow window cannot hold the full line, wrap at source spaces first.
                val cuts=listOf(0)+g.wordBreaks+listOf(views.size)
                for(j in 0 until cuts.lastIndex)w.grid(b,views.subList(cuts[j],cuts[j+1]),cuts[j+1]-cuts[j],80,availableDp)
            }
        }else w.grid(b,views,if(context)1 else g.columns,if(t.screen=="SCR-044"&&g.items.any{it is UiElement.Glyph})120 else 100,availableDp)
        // Shared pair pages have one pager after both groups. Ordered trays advance only after committed input.
        val showPager=g.pager==null || t.groups.drop(index+1).none{it.pager==g.pager}
        if(chunks.size>1 && showPager){w.add(b,w.text("${pageIndex+1} / ${chunks.size}",18),4);w.pair(b,w.button("Trước","white",enabled=pageIndex>0,action=active{groupPages[key]=pageIndex-1;stopAudio();render()}),w.button("Sau","green",enabled=pageIndex<chunks.lastIndex && committed(pageIndex),action=active{groupPages[key]=pageIndex+1;stopAudio();refreshTask()}))}
    }
    override fun onResume(){super.onResume();resumed=true;foregroundBegan=SystemClock.elapsedRealtime();if(adultInvalidated){adultInvalidated=false;home()}else if(f?.ui?.tasks?.get(page.ref)?.screen==page.screen)render()}
    override fun onPause(){elapsedStudyMs=studyTimeMs();foregroundBegan=null;resumed=false;depart();f?.parent?.close();if(page.screen in parentScreens)adultInvalidated=true;super.onPause()}
    override fun onStop(){stopAudio();super.onStop()}
    override fun onDestroy(){closed=true;stopMotion();stopAudio();f?.progress?.close();f=null;if(::w.isInitialized)w.clear();super.onDestroy()}
    @Suppress("DEPRECATION") override fun onBackPressed(){if(stack.size>1)back()else super.onBackPressed()}
}
