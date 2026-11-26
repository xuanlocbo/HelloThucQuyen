package vn.htq.core

/** A playback request is assembled only from a task's canonical audio object. */
class AudioRequest internal constructor(val paths: List<String>)
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
}
interface AudioBackend {
    /** completion callback must be delivered on the same serial dispatch thread as play/stop. */
    fun play(path: String, onComplete: (Boolean) -> Unit)
    fun stop()
}
/** Single primary stream. New explicit requests invalidate stale callbacks; lifecycle stop never auto-resumes. */
class AudioArbiter(private val backend: AudioBackend) {
    private var epoch = 0L
    private var queue: List<String> = emptyList()
    private var index = 0
    var active = false; private set
    fun request(request: AudioRequest) { stop(); if (request.paths.isEmpty()) return; queue = request.paths.toList(); index = 0; active = true; next(epoch) }
    private fun next(token: Long) {
        if (token != epoch || !active) return
        if (index >= queue.size) { active = false; queue = emptyList(); return }
        val path = queue[index++]
        try { backend.play(path) { ok -> if (token == epoch && active) { if (ok) next(token) else stop() } } } catch (_: Exception) { stop() }
    }
    fun stop() { epoch++; active = false; queue = emptyList(); index = 0; backend.stop() }
}
