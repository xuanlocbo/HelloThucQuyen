package vn.htq.core

/** Explicit educational playback; only real controller state authorizes result media. */
class TaskMotion(private val catalog:Catalog){
    private data class Cue(val id:String,val variant:String?=null)
    private val policy=MotionPolicy(catalog)
    private fun cue(state:TaskInteraction):Cue? {
        val task=catalog.tasks[state.task.id]?:return null
        val scene=task.o("scene")
        val advanced=state.advanced
        if(advanced!=null&&advanced.isSimulation){
            if(!advanced.resultViewed)return null
            val binding=scene.o("native_binding")
            val motion=binding.o("motion")
            val id=motion.optional("motion_id")?:return null
            if(motion.optional("trigger")!="explicit_view_result_only"||id !in scene.strings("result_motion_ids"))return null
            val variant=if(id=="MOT-091"&&binding.o("result_stage").optional("operation")=="seedling_emerges")"seed-story" else null
            return Cue(id,variant)
        }
        val completed=state.verdict as? Verdict.Completed?:return null
        if(completed.ticket.taskId!=state.task.id||completed.ticket.kind!=CompletionKind.GRADED)return null
        // Source-bound demonstrations after validation, never sample states for the clock.
        return when(state.task.id){
            "06.06-L1-Q01"->Cue("MOT-098")
            "06.06-L1-Q02"->Cue("MOT-098","butterfly")
            "08.05-L2-Q01"->Cue("MOT-099")
            "09.05-L2-Q02"->Cue("MOT-095")
            "10.02-L2-Q01"->Cue("MOT-097","run")
            "13.04-L2-Q01"->Cue("MOT-100")
            else->null
        }?.takeIf{it.id in task.strings("motion_ids")}
    }
    fun available(state:TaskInteraction):Boolean=cue(state)!=null
    fun request(state:TaskInteraction,childRequested:Boolean,reduced:Boolean):MotionDecision {
        if(!childRequested)return MotionDecision.Blocked
        val c=cue(state)?:return MotionDecision.Blocked
        return policy.decide(state.task.id,c.id,c.variant,"explicit_scripted_education_step",
            setOf("task_in_variant_scope","source_script_step_matches","phase_allows_result","no_answer_preview"),reduced)
    }
}
