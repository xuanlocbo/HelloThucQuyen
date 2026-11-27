package vn.htq.core

/** A playback request is assembled only from a task's canonical audio object. */
class AudioRequest internal constructor(val paths: List<String>, internal val cues: List<String?> = paths, internal val beatMs: Long = 0L)
enum class AudioEvent { PROMPT, LISTEN, HINT, CORRECT, RETRY, CHARACTER_DIALOGUE, EXPLORE_LABEL }
class TaskAudioPolicy(private val catalog: Catalog) {
    fun request(taskId: String, event: AudioEvent, childRequested: Boolean, attempted: Boolean = false,
                verdict: Verdict? = null, objectId: String? = null, exploring: Boolean = false): AudioRequest? {
        if (!childRequested) return null
        val a = catalog.tasks.getValue(taskId).o("audio")
        val ids = when (event) {
            AudioEvent.PROMPT -> listOfNotNull(a.optional("prompt_id"))
            AudioEvent.LISTEN -> a.a("stimulus_segments").map { it.obj().s("audio_id") }.ifEmpty { a.strings("story_before_question") }
            AudioEvent.HINT -> if (attempted) listOfNotNull(a.optional("hint_on_request_id")) else emptyList()
            AudioEvent.CORRECT -> if (verdict is Verdict.Completed && verdict.ticket.taskId == taskId && verdict.ticket.kind == CompletionKind.GRADED) listOfNotNull(a.optional("feedback_after_response_id")) else emptyList()
            AudioEvent.RETRY -> if (verdict == Verdict.Retry) listOfNotNull(a.optional("retry_after_response_id")) else emptyList()
            AudioEvent.CHARACTER_DIALOGUE -> a.a("character_dialogues_on_request").map { it.obj() }.filter { it.optional("speaker_art_id") == objectId && it.optional("status") == "available" }.map { it.s("audio_id") }
            AudioEvent.EXPLORE_LABEL -> if (exploring || verdict is Verdict.Completed && verdict.ticket.taskId == taskId) a.a("art_labels_explore_or_after_response").map { it.obj() }.filter { it.optional("art_id") == objectId }.map { it.s("audio_id") } else emptyList()
        }
        if (ids.isEmpty()) return null
        require(ids.size <= 64)
        return AudioRequest(ids.map { catalog.assetPath(it, "audio") })
    }
    /** Explicit listening for an individual canonical object, not a hidden answer label. */
    fun objectRequest(taskId:String,objectId:String,childRequested:Boolean):AudioRequest? {
        if(!childRequested)return null
        val task=catalog.tasks.getValue(taskId)
        val o=task.o("scene").a("objects").map{it.obj()}.firstOrNull{it.optional("object_id")==objectId}?:return null
        if(o.s("role")=="speaker")return request(taskId,AudioEvent.CHARACTER_DIALOGUE,true,objectId=o.optional("art_id"))
        val a=task.o("audio")
        val segments=a.a("stimulus_segments").map{it.obj().s("audio_id")}
        val id=when(o.s("role")){
            "audio_source" -> o.optional("audio_id")?.takeIf{it in segments}
            "choice" -> o.optional("on_request_audio_id")?.takeIf{o.optional("audio_permission")=="explicit_choice_listen_only"&&it in segments}
            "calendar_or_page" -> o.optional("on_request_audio_id")
            "sound_pad","sign_card","story_card" -> if(catalog.childTask(taskId).participation&&o.optional("play_event")=="explicit_tap_only")o.optional("on_request_audio_id")else null
            else -> null
        }?:return null
        return AudioRequest(listOf(catalog.assetPath(id,"audio")))
    }
    fun rhythmRequest(taskId:String,ids:List<String>,childRequested:Boolean):AudioRequest? {
        if(!childRequested)return null
        val scene=catalog.tasks.getValue(taskId).o("scene");val native=scene["native_binding"]?.obj()?:return null
        if(native.optional("scene_type")!="participation_rhythm"||!catalog.childTask(taskId).participation)return null
        val sequence=native.o("sequence");if(ids.isEmpty()||ids.size>sequence.n("max_sequence_length"))return null
        val objects=scene.a("objects").map{it.obj()}.associateBy{it.s("object_id")}
        val cues=ids.map{id->val o=objects[id]?:return null;when(o.s("role")){"rest"->null;"sound_pad"->catalog.assetPath(o.optional("on_request_audio_id")?:return null,"audio");else->return null}}
        val beat=((sequence["timing_s"] as Number).toDouble()*1000).toLong();require(beat in 100..3000)
        return AudioRequest(cues.filterNotNull(),cues,beat)
    }
    fun resultRequest(state:AdvancedInteraction,childRequested:Boolean):AudioRequest? {
        if(!childRequested||!state.isSimulation||!state.resultViewed)return null
        val t=catalog.tasks.getValue(state.task.id);val ids=t.o("audio").strings("story_after_response")
        return if(ids.isEmpty())null else AudioRequest(ids.map{catalog.assetPath(it,"audio")})
    }
    /** The caller supplies an already-approved non-task presentation asset. */
    fun libraryRequest(path: String, childRequested: Boolean): AudioRequest? =
        if(childRequested && path in catalog.paths && path.startsWith("audio/") && path.endsWith(".ogg")) AudioRequest(listOf(path)) else null
}
interface AudioBackend {
    /** completion callback must be delivered on the same serial dispatch thread as play/stop. */
    fun play(path: String, onComplete: (Boolean) -> Unit)
    fun stop()
    fun nowMs():Long=System.nanoTime()/1_000_000
    /** A silent beat is cancellable and runs on the same dispatch thread. */
    fun waitFor(ms:Long,onComplete:(Boolean)->Unit){onComplete(false)}
}
/** One primary stream. Stale/duplicate clip callbacks cannot finish a newer queue. */
class AudioArbiter(private val backend: AudioBackend) {
    private var epoch = 0L
    private var clip = 0L
    private var queue: List<String?> = emptyList()
    private var beatMs=0L
    private var index = 0
    private var finished: ((Boolean)->Unit)? = null
    var active = false; private set
    fun request(request: AudioRequest) = request(request,null)
    fun request(request: AudioRequest, onFinished: ((Boolean)->Unit)?) {
        stop()
        if(request.cues.isEmpty()){onFinished?.invoke(false);return}
        beatMs=request.beatMs;queue=request.cues.toList();index=0;finished=onFinished;active=true;next(epoch)
    }
    private fun complete(token:Long,ok:Boolean){
        if(token!=epoch||!active)return
        val cb=finished
        stop()
        cb?.invoke(ok)
    }
    private fun next(token: Long) {
        if (token != epoch || !active) return
        if (index >= queue.size) { complete(token,true); return }
        val path = queue[index++];val thisClip=++clip;val began=backend.nowMs()
        fun delay(ms:Long){
            val waitClip=++clip
            backend.waitFor(ms){ok->if(token==epoch&&active&&waitClip==clip){if(ok)next(token)else complete(token,false)}}
        }
        try {
            if(path==null)delay(beatMs)
            else backend.play(path){ok->if(token==epoch&&active&&thisClip==clip){
                if(!ok)complete(token,false)
                else {val remaining=beatMs-(backend.nowMs()-began).coerceAtLeast(0);if(remaining>0)delay(remaining)else next(token)}
            }}
        }catch(_:Exception){if(token==epoch&&active)complete(token,false)}
    }

    /** Cancellation discards the completion callback. No autoplay or success on stop. */
    fun stop() { epoch++;clip++;active=false;queue=emptyList();index=0;beatMs=0L;finished=null;backend.stop() }
}
/** Valid only for its original active listening session. No persistence or public constructor. */
class ListeningReceipt internal constructor(internal val owner: ListeningGate, internal val generation: Long, val taskId:String)
class ListeningGate(private val catalog:Catalog,private val audio:AudioArbiter){
    private val policy=TaskAudioPolicy(catalog)
    private var generation=0L
    private var proof:ListeningReceipt?=null
    var taskId:String?=null;private set
    fun required(id:String)=catalog.tasks.getValue(id).o("audio").strings("story_before_question").isNotEmpty()
    fun enter(id:String){require(required(id));leave();taskId=id}
    fun leave(){generation++;proof=null;taskId=null;audio.stop()}
    fun receipt(id:String):ListeningReceipt?=proof?.takeIf{valid(it,catalog,id)}
    internal fun valid(value:ListeningReceipt,source:Catalog,id:String)=source===catalog&&value.owner===this&&value.generation==generation&&value.taskId==id&&taskId==id&&proof===value
    fun start(id:String,onFinished:(Boolean)->Unit):Boolean{
        if(taskId!=id||!required(id))return false
        proof=null;val token=++generation
        val request=policy.request(id,AudioEvent.LISTEN,true)?:return false
        audio.request(request){ok->
            if(generation==token&&taskId==id){
                if(ok)proof=ListeningReceipt(this,token,id)
                onFinished(ok)
            }
        }
        return true
    }
}
