package vn.htq.core

import java.io.InputStream

interface AssetSource { fun open(relative: String): InputStream }
fun canonicalPath(path: String): String {
    require(path.isNotEmpty() && path.matches(Regex("[a-z0-9_./-]+"))) { "Invalid asset path" }
    require(path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "Invalid path segments" }
    return path
}
private fun rows(value: Any?, key: String): Map<String, Obj> {
    val result = linkedMapOf<String, Obj>()
    for (v in value.arr()) { val o = v.obj(); require(result.put(o.s(key), o) == null) { "Duplicate $key" } }
    return result
}
data class AssetRef(val id: String, val type: String, val path: String?)
data class Area(val id: String, val title: String, val topicIds: List<String>, val artId: String)
data class Topic(val id: String, val title: String, val lessonIds: List<String>, val artId: String)
data class Lesson(val id: String, val title: String, val topicId: String, val levelIds: List<String>)
data class ActivityRoute(val id: String, val title: String, val lessonId: String, val taskIds: List<String>)
/** Only this projection, not assessment or authoring context, reaches child-facing UI. */
data class ChildTask(val id: String, val lessonId: String, val levelId: String, val screenId: String, val prompt: String, val backgroundId: String?, val participation: Boolean)
class Catalog private constructor(private val source: AssetSource, val version: String, val assets: Map<String, AssetRef>,
    internal val manifestRecords: Map<String, Obj>, internal val tasks: Map<String, Obj>, internal val assessment: Map<String, Obj>,
    internal val rewardRows: Map<String, Obj>, internal val motionPermissions: Obj, internal val motionRecords: Arr,
    internal val session: Obj, val areas: List<Area>, val topics: Map<String, Topic>, val lessons: Map<String, Lesson>,
    val activities: Map<String, ActivityRoute>, val choiceCount: Int, val paths: Set<String>) {
    val taskCount get() = tasks.size
    val gradedCount get() = assessment.values.count { !it.b("no_grade_open") }
    val openCount get() = assessment.values.count { it.b("no_grade_open") }
    val ruleTypes get() = assessment.values.map { it.o("rule").s("type") }.toSet()
    val rewardItemCount get() = rewardRows.values.map { it.s("garden_item_asset_id") }.toSet().size
    fun open(path: String): InputStream { require(canonicalPath(path) in paths); return source.open(path) }
    fun assetPath(id: String, type: String): String = assets.getValue(id).let { require(it.type == type); requireNotNull(it.path) }
    fun childTask(id: String): ChildTask { val t = tasks.getValue(id); return ChildTask(id, t.s("lesson_id"), t.s("level_id"), t.s("screen_id"), t.s("child_prompt"), t.o("scene").optional("background_id"), assessment.getValue(id).b("no_grade_open")) }
    fun hasTask(id: String) = id in tasks
    internal fun objectIds(id: String): Set<String> = tasks.getValue(id).o("scene").a("objects").mapNotNull { it.obj().optional("object_id") }.toSet()
    companion object {
        private const val VERSION = "5.3-official-1"
        fun load(source: AssetSource): Catalog {
            fun read(p: String): Any? = source.open(canonicalPath(p)).bufferedReader(Charsets.UTF_8).use { Json.parse(it.readText()) }
            val pkg = read("manifest/package.json").obj()
            require(pkg.s("content_version") == VERSION && pkg.n("schema_version") == 1 && pkg.b("native_only"))
            require(pkg.s("runtime_root") == "htq/" && pkg.s("reward_status") == "approved_active" && !pkg.b("release_ready"))
            val entries = pkg.o("entrypoints")
            fun entry(key: String): Any? = read(entries.s(key))
            val index = entry("file_index").obj()
            val prefix = "app/src/main/assets/htq/"
            val paths = index.keys.map { require(it.startsWith(prefix)); canonicalPath(it.removePrefix(prefix)) }.toSet()
            require(paths.size == index.size && "manifest/package.json" in paths)
            entries.values.forEach { val path = it.str(); require(path == entries.s("file_index") || path in paths) }
            val rawAssets = entry("assets").obj().mapValues { it.value.obj() }
            val assets = rawAssets.mapValues { (id, v) ->
                require(v.s("id") == id)
                val path = v.optional("path")?.also { require(canonicalPath(it) in paths) }
                v["graphics"]?.arr()?.forEach { require(it.str() in paths) }
                v["variants"]?.arr()?.forEach { variant ->
                    val o = variant.obj(); o.o("files").values.filterIsInstance<String>().forEach { require(it in paths) }
                    o.a("reduced_step_files").forEach { require(it.str() in paths) }
                }
                AssetRef(id, v.s("type"), path)
            }
            val counts = pkg.o("counts")
            require(assets.size == counts.n("total_ids") && assets.size == 3897)
            require(assets.values.count { it.type == "image" } == 1179 && assets.values.count { it.type == "audio" } == 2320)
            require(assets.values.count { it.type == "native_spec" } == 290 && assets.values.count { it.type == "motion" } == 108)
            val t = rows(entry("tasks"), "task_id"); val a = rows(entry("evaluation"), "task_id")
            require(t.size == 660 && t.keys == a.keys)
            require(a.values.count { it.b("no_grade_open") } == 116)
            val rewards = entry("rewards").obj(); require(rewards.b("approved") && rewards.s("status") == "APPROVED_ACTIVE")
            val r = rows(rewards.a("task_allocations"), "task_id"); require(r.keys == t.keys)
            val session = entry("session").obj(); require(session.s("content_version") == VERSION && session.s("storage") == "local_only")
            require(session.o("audio_arbiter").n("primary_max") == 1 && !session.o("audio_arbiter").b("auto_replay_on_restore"))
            require(session.o("reward").b("ledger_and_completion_atomic") && session.o("reward").b("no_repeat_reward_on_replay"))
            val nav = entry("navigation").obj(); require(nav.b("all_unlocked") && nav.b("parent_area_requires_gate") && nav.b("no_automatic_task_advance"))
            val topics = rows(nav.a("topic_routes"), "topic_id").mapValues { (_, v) -> Topic(v.s("topic_id"), v.s("name"), v.strings("lesson_ids"), v.s("subject_art_id")) }
            val lessons = rows(nav.a("lesson_routes"), "lesson_id").mapValues { (_, v) -> Lesson(v.s("lesson_id"), v.s("title"), v.s("topic_id"), v.strings("level_ids")) }
            val activities = rows(entry("activities"), "level_id").mapValues { (_, v) -> ActivityRoute(v.s("level_id"), v.s("name"), v.s("lesson_id"), v.strings("task_ids")) }
            val areas = nav.a("areas").map { val v = it.obj(); Area(v.s("id"), v.s("name"), v.strings("topic_ids"), v.s("cover_art")) }
            require(topics.size == 17 && lessons.size == 110 && activities.size == 330 && areas.size == 6)
            require(areas.flatMap { it.topicIds }.sorted() == topics.keys.sorted())
            require(topics.values.flatMap { it.lessonIds }.sorted() == lessons.keys.sorted())
            require(lessons.values.flatMap { it.levelIds }.sorted() == activities.keys.sorted())
            require(activities.values.flatMap { it.taskIds }.sorted() == t.keys.sorted())
            val choices = rows(entry("choice_index"), "slot_id"); require(choices.size == 816)
            choices.values.forEach { require(it.s("task_id") in t && it.s("visual_path") in paths && !it.b("preanswer_autoplay")) }
            val motion = entry("motion_permissions").obj(); require(motion.size == 108 && motion.keys.all { assets[it]?.type == "motion" })
            val motionRecords = entry("motion_records").arr(); require(motionRecords.size == 115)
            t.forEach { (id, v) ->
                val rule = a.getValue(id); require(rule.b("enabled") && rule.b("evaluate_only_after_explicit_response"))
                require(activities.getValue(v.s("level_id")).taskIds.contains(id))
                require(activities.getValue(v.s("level_id")).lessonId == v.s("lesson_id") && lessons.getValue(v.s("lesson_id")).topicId == v.s("topic_id"))
                val safe = v.o("ready_mode"); require(!safe.b("show_answer_text") && !safe.b("show_correctness") && !safe.b("autoplay_option_labels"))
                require(v.o("manual_advance").b("manual_only") && v.s("runtime_gate") == "data_contract_only_native_implementation_pending")
                require(assets[v.s("screen_id")]?.type == "native_spec")
                v.strings("choice_slot_ids").forEach { require(choices.getValue(it).s("task_id") == id) }
                val rr = r.getValue(id); require(rr.s("idempotency_key") == id && rr.n("quantity") == 1)
                require(rr.s("completion_kind") == if (rule.b("no_grade_open")) "explicit_participation_done" else "validated_completion")
                require(assets[rr.s("garden_item_asset_id")]?.type == "image")
            }
            return Catalog(source, VERSION, assets, rawAssets, t, a, r, motion, motionRecords, session, areas, topics, lessons, activities, choices.size, paths + entries.s("file_index"))
        }
    }
}
