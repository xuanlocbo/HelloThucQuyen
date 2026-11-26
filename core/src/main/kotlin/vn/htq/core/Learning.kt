package vn.htq.core

/** Routes are derived from the official hierarchy; no topic is locked. */
sealed class Route {
    object Home: Route()
    data class AreaRoute(val id: String): Route()
    data class TopicRoute(val id: String): Route()
    data class LessonRoute(val id: String): Route()
    data class LevelRoute(val id: String): Route()
    data class TaskRoute(val id: String): Route()
}
class Navigator(private val catalog: Catalog, private val stopAudio: () -> Unit) {
    var route: Route = Route.Home; private set
    fun go(next: Route) {
        when (next) {
            Route.Home -> Unit
            is Route.AreaRoute -> require(catalog.areas.any { it.id == next.id })
            is Route.TopicRoute -> require(next.id in catalog.topics)
            is Route.LessonRoute -> require(next.id in catalog.lessons)
            is Route.LevelRoute -> require(next.id in catalog.activities)
            is Route.TaskRoute -> require(catalog.hasTask(next.id))
        }
        stopAudio(); route = next
    }
    fun back() = go(when (val current = route) {
        Route.Home -> Route.Home
        is Route.AreaRoute -> Route.Home
        is Route.TopicRoute -> Route.AreaRoute(catalog.areas.single { current.id in it.topicIds }.id)
        is Route.LessonRoute -> Route.TopicRoute(catalog.lessons.getValue(current.id).topicId)
        is Route.LevelRoute -> Route.LessonRoute(catalog.activities.getValue(current.id).lessonId)
        is Route.TaskRoute -> Route.LevelRoute(catalog.childTask(current.id).levelId)
    })
    // Caller supplies a validated completion or a separate explicit skip. Never called by audio/motion callbacks.
    fun advance(ticket: CompletionTicket?, explicitSkip: Boolean, childRequested: Boolean): Boolean {
        val current = route as? Route.TaskRoute ?: return false
        if (!childRequested || (ticket?.taskId != current.id && !explicitSkip)) return false
        val level = catalog.activities.getValue(catalog.childTask(current.id).levelId)
        val next = level.taskIds.getOrNull(level.taskIds.indexOf(current.id) + 1)
        go(if (next == null) Route.LevelRoute(level.id) else Route.TaskRoute(next)); return true
    }
}
sealed class Response {
    data class Single(val id: String): Response()
    data class Selection(val ids: List<String>): Response()
    data class Sequence(val ids: List<String>): Response()
    object ParticipationDone: Response()
}
enum class CompletionKind(val wire: String) { GRADED("validated_completion"), OPEN("explicit_participation_done") }
/** A completion ticket cannot be constructed by the Android UI module. */
class CompletionTicket internal constructor(val taskId: String, val kind: CompletionKind)
sealed class Verdict {
    data class Completed(val ticket: CompletionTicket): Verdict()
    object Retry: Verdict()
    object NotSubmitted: Verdict()
    object RequiresInteractionEngine: Verdict()
}
/** Foundation only: complex evaluators are implemented with their renderer in 6.3. Unknown rules fail closed. */
class AssessmentEngine(private val catalog: Catalog) {
    val implementedRules = setOf("single_object", "set_equals", "ordered_sequence", "participation")
    fun evaluate(taskId: String, response: Response, explicitlySubmitted: Boolean): Verdict {
        if (!explicitlySubmitted) return Verdict.NotSubmitted
        val spec = catalog.assessment.getValue(taskId)
        if (!spec.b("enabled") || !spec.b("evaluate_only_after_explicit_response")) return Verdict.RequiresInteractionEngine
        val rule = spec.o("rule")
        if (rule.s("type") == "participation") return if (response == Response.ParticipationDone)
            Verdict.Completed(CompletionTicket(taskId, CompletionKind.OPEN)) else Verdict.NotSubmitted
        if (spec.b("no_grade_open")) return Verdict.RequiresInteractionEngine
        val accepted = when (rule.s("type")) {
            "single_object" -> (response as? Response.Single)?.id == rule.s("accepted_id")
            "set_equals" -> (response as? Response.Selection)?.ids?.let { ids -> ids.size == ids.toSet().size && ids.toSet() == rule.strings("accepted_ids").toSet() } == true
            "ordered_sequence" -> (response as? Response.Sequence)?.ids?.let { ids -> rule.a("accepted_sequences").any { seq -> seq.arr().map { it.str() } == ids } } == true
            else -> return Verdict.RequiresInteractionEngine
        }
        return if (accepted) Verdict.Completed(CompletionTicket(taskId, CompletionKind.GRADED)) else Verdict.Retry
    }
}
data class RewardEntitlement(val taskId: String, val itemId: String, val quantity: Int, val completionKind: String)
class RewardPolicy(private val catalog: Catalog) {
    fun entitlement(ticket: CompletionTicket): RewardEntitlement {
        val r = catalog.rewardRows.getValue(ticket.taskId)
        require(r.s("completion_kind") == ticket.kind.wire && r.s("idempotency_key") == ticket.taskId)
        return RewardEntitlement(ticket.taskId, r.s("garden_item_asset_id"), r.n("quantity"), ticket.kind.wire)
    }
}
interface ProgressStore {
    fun saveSession(snapshot: Obj)
    fun loadSession(): Obj?
    fun commit(ticket: CompletionTicket): Boolean
    fun claim(taskId: String): Boolean
    fun inventory(): Map<String, Int>
    fun completedCount(): Int
}
/** Only committed, allowlisted session state is stored. The 6.3 renderer must validate richer scene payloads before saving. */
class SessionPolicy(private val catalog: Catalog) {
    val fields = catalog.session.strings("resume_fields").toSet()
    val forbidden = catalog.session.strings("never_persist").toSet()
    fun snapshotForTask(taskId: String, phase: String = "ready"): Obj {
        val t = catalog.childTask(taskId)
        return mapOf("content_version" to catalog.version, "schema_version" to 1, "lesson_id" to t.lessonId, "level_id" to t.levelId, "task_id" to t.id, "phase" to phase)
    }
    fun sanitize(state: Obj): Obj? = runCatching {
        require(state.s("content_version") == catalog.version && state.n("schema_version") == 1)
        val task = catalog.childTask(state.s("task_id")); require(task.lessonId == state.s("lesson_id") && task.levelId == state.s("level_id"))
        val result = state.filterKeys { it in fields || it in setOf("content_version", "schema_version") }.filterKeys { it !in forbidden }
        result["stable_shuffled_object_ids"]?.let { raw -> val ids = raw.arr().map { it.str() }; require(ids.size == ids.toSet().size && ids.all { it in catalog.objectIds(task.id) }) }
        result["object_positions"]?.let { raw -> raw.obj().forEach { (id, pos) -> require(id in catalog.objectIds(task.id)); pos.arr().let { a -> require(a.size == 2 && a.all { it is Number && it.toDouble().isFinite() && it.toDouble() in 0.0..1.0 }) } } }
        // Entitlements and inventory are authoritative in their own transaction tables, never imported from a session blob.
        result.filterKeys { it !in setOf("completed_task_keys", "reward_ledger", "garden_inventory", "garden_placements") }
    }.getOrNull()
}
data class HitPolicy(val childMinDp: Int = 56, val preferredDp: Int = 64, val parentMinDp: Int = 48, val maxActiveChoices: Int = 6) {
    fun pages(ids: List<String>, availableSlots: Int): List<List<String>> = ids.chunked(availableSlots.coerceIn(1, maxActiveChoices))
    fun targetDp(requested: Int, parent: Boolean = false): Int = maxOf(requested, if (parent) parentMinDp else childMinDp)
}
/** Gate for internal parent controls; no child profile, PIN, microphone, camera or location is collected. */
class ParentGate(private val nowMs: () -> Long) {
    private var until = Long.MIN_VALUE
    fun verifyAdultChallenge(answer: Int, a: Int, b: Int): Boolean { val ok = a in 10..49 && b in 10..49 && answer == a + b; until = if (ok) nowMs() + 60_000 else Long.MIN_VALUE; return ok }
    fun allowed(): Boolean = nowMs() < until
    fun close() { until = Long.MIN_VALUE }
}
