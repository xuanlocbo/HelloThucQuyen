package vn.htq.core

enum class CompletionKind(val wire: String) { GRADED("validated_completion"), OPEN("explicit_participation_done") }
/** A completion ticket cannot be constructed by the Android UI module. */
class CompletionTicket internal constructor(val taskId: String, val kind: CompletionKind)
sealed class Verdict {
    data class Completed(val ticket: CompletionTicket): Verdict()
    object Retry: Verdict()
    object NotSubmitted: Verdict()
    object RequiresInteractionEngine: Verdict()
}
class AssessmentEngine(private val catalog: Catalog) {
    val implementedRules = InteractionRules.types
    private val hotspots by lazy { catalog.open("engine/hotspots.json").bufferedReader(Charsets.UTF_8).use{Json.parse(it.readText()).arr().map{v->v.obj()}} }
    /** Only interaction family, never criteria, IDs of accepted answers, or target polygons. */
    fun responseType(taskId:String):String=catalog.assessment.getValue(taskId).o("rule").s("type")
    fun evaluate(taskId: String, response: Response, explicitlySubmitted: Boolean, listening: ListeningReceipt?=null): Verdict {
        if (!explicitlySubmitted) return Verdict.NotSubmitted
        val spec = catalog.assessment.getValue(taskId)
        if (!spec.b("enabled") || !spec.b("evaluate_only_after_explicit_response") || !spec.b("allow_reward_only_after_valid_completion_or_participation")) return Verdict.RequiresInteractionEngine
        val task=catalog.tasks.getValue(taskId)
        if(task.o("audio").strings("story_before_question").isNotEmpty() && (listening==null||!listening.owner.valid(listening,catalog,taskId)))return Verdict.NotSubmitted
        val rule=spec.o("rule");val type=rule.s("type")
        if(type !in implementedRules)return Verdict.RequiresInteractionEngine
        if(spec.b("no_grade_open") != (type=="participation"))return Verdict.RequiresInteractionEngine
        if(type=="participation")return if(response==Response.ParticipationDone)Verdict.Completed(CompletionTicket(taskId,CompletionKind.OPEN))else Verdict.NotSubmitted
        val accepted=InteractionRules.matches(taskId,rule,task.o("scene"),if(type=="any_hotspot_region")hotspots else emptyList(),response)
        return if(accepted)Verdict.Completed(CompletionTicket(taskId,CompletionKind.GRADED))else Verdict.Retry
    }
    // Deterministic instance IDs for source pools without individual IDs. No answer data.
    fun splitInstanceIds(taskId:String):List<String> = InteractionRules.splitIds(taskId,catalog.tasks.getValue(taskId).o("scene"))
    fun selectionInstances(taskId:String):Map<String,String> = InteractionRules.poolInstances(taskId,catalog.tasks.getValue(taskId).o("scene"))
}
