package vn.htq.core

sealed class MotionDecision {
    object Blocked: MotionDecision()
    object NativeState: MotionDecision()
    data class Media(val path: String, val reducedSteps: List<String>): MotionDecision()
}
class MotionPolicy(private val catalog: Catalog) {
    fun decide(taskId: String, motionId: String, variant: String?, event: String, proven: Set<String>, reduced: Boolean): MotionDecision {
        val task = catalog.tasks[taskId] ?: return MotionDecision.Blocked
        if (motionId !in task.strings("motion_ids")) return MotionDecision.Blocked
        val gate = catalog.motionPermissions[motionId]?.obj() ?: return MotionDecision.Blocked
        if (gate.s("event") != event || !proven.containsAll(gate.strings("requires"))) return MotionDecision.Blocked
        if (gate.b("render_native_state_for_interface")) return MotionDecision.NativeState
        val record = catalog.motionRecords.map { it.obj() }.firstOrNull { it.s("id") == motionId && it.optional("variant") == variant } ?: return MotionDecision.Blocked
        if (taskId !in record.strings("task_ids")) return MotionDecision.Blocked
        val variants = catalog.manifestRecords.getValue(motionId).a("variants")
        val asset = variants.map { it.obj() }.firstOrNull { it.optional("variant") == variant } ?: return MotionDecision.Blocked
        val path = asset.o("files").optional(if (reduced) "reduced" else "animated_webp") ?: return MotionDecision.NativeState
        return MotionDecision.Media(path, if (reduced) asset.strings("reduced_step_files") else emptyList())
    }
}
