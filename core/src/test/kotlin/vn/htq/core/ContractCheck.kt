package vn.htq.core

import java.io.File

fun main(args: Array<String>) {
    val root = File(args.single()).canonicalFile
    val source = object : AssetSource { override fun open(relative: String) = File(root, canonicalPath(relative)).inputStream() }
    val c = Catalog.load(source)
    check(c.assets.size == 3897 && c.taskCount == 660 && c.choiceCount == 816 && c.gradedCount == 544 && c.openCount == 116)
    check(c.rewardItemCount == 32)
    println("CATALOG_OK assets=${c.assets.size} tasks=${c.taskCount} choices=${c.choiceCount} graded=${c.gradedCount} open=${c.openCount}")
    // All indexed assets have actual files; no media decoding or listening is repeated.
    check(c.paths.all { File(root, it).isFile }); check(c.paths.size == 4664)
    println("FILES_OK ${c.paths.size}")
    val rewards = RewardPolicy(c)
    val memoryLedger = linkedMapOf<String, RewardEntitlement>()
    AssessmentChecks.run(c) { ticket ->
        val entitlement = rewards.entitlement(ticket)
        check(entitlement.taskId == ticket.taskId && entitlement.quantity == 1)
        check(memoryLedger.putIfAbsent(ticket.taskId, entitlement) == null)
        check(memoryLedger.putIfAbsent(ticket.taskId, entitlement) == entitlement)
    }
    check(memoryLedger.size == 660)
    val ui = vn.htq.core.ui.UiContent.parse(File(root.parentFile,"htq_ui/content.json").readText(),c.paths)
    TaskInteractionChecks.run(c,ui)
    val audioPolicy = TaskAudioPolicy(c); var promptCount = 0; var listeningCount = 0
    for (id in c.tasks.keys) {
        check(audioPolicy.request(id, AudioEvent.PROMPT, false) == null)
        check(audioPolicy.request(id, AudioEvent.HINT, true, attempted = false) == null)
        check(audioPolicy.request(id, AudioEvent.CORRECT, true) == null)
        check(audioPolicy.request(id, AudioEvent.EXPLORE_LABEL, true, objectId="unknown") == null)
        audioPolicy.request(id, AudioEvent.PROMPT, true)?.let { check(it.paths.all { p -> p in c.paths }); promptCount++ }
        audioPolicy.request(id, AudioEvent.LISTEN, true)?.let { check(it.paths.all { p -> p in c.paths }); listeningCount++ }
    }
    class Fake : AudioBackend {
        var playing = false; var maxConcurrent = 0; val history = mutableListOf<String>(); var callback: ((Boolean) -> Unit)? = null
        override fun play(path: String, onComplete: (Boolean) -> Unit) { check(!playing); playing = true; maxConcurrent = maxOf(maxConcurrent,1); history.add(path); callback=onComplete }
        override fun stop() { playing=false; callback=null }
        fun finish() { val f=callback; callback=null; playing=false; f?.invoke(true) }
    }
    val fake = Fake(); val audio = AudioArbiter(fake); val first = requireNotNull(audioPolicy.request(c.tasks.keys.first(), AudioEvent.PROMPT, true));
    audio.request(first); val stale = fake.callback; audio.request(first); stale?.invoke(true); check(audio.active && fake.playing); audio.stop(); stale?.invoke(true); check(!audio.active && !fake.playing)
    val multi = AudioRequest(listOf(first.paths.first(), first.paths.first())); audio.request(multi); val before=fake.history.size; fake.finish(); check(fake.history.size==before+1); fake.finish(); check(!audio.active && !fake.playing)
    println("AUDIO_POLICY_OK prompts=$promptCount listen_sequences=$listeningCount max_concurrent=${fake.maxConcurrent}")
    val session = SessionPolicy(c); var restoreCount=0
    for (id in c.tasks.keys) {
        val snapshot = session.snapshotForTask(id); val contaminated = snapshot + mapOf("playing_audio_token" to "stale", "reward_ledger" to listOf("untrusted"), "pending_audio_queue" to listOf("x"))
        val restored = requireNotNull(session.sanitize(Json.parse(Json.encode(contaminated)).obj())); check(restored.s("task_id")==id && session.forbidden.none { it in restored } && "reward_ledger" !in restored)
        check(session.sanitize(snapshot + ("lesson_id" to "missing")) == null); restoreCount++
    }
    var stops=0; val nav = Navigator(c) { stops++ }; for (area in c.areas) { nav.go(Route.AreaRoute(area.id)); for (topic in area.topicIds) { nav.go(Route.TopicRoute(topic)); for (lesson in c.topics.getValue(topic).lessonIds) { nav.go(Route.LessonRoute(lesson)); for (level in c.lessons.getValue(lesson).levelIds) { nav.go(Route.LevelRoute(level)); for (task in c.activities.getValue(level).taskIds) { nav.go(Route.TaskRoute(task)); check(!nav.advance(null,false,false)); nav.back(); check(nav.route==Route.LevelRoute(level)) } } } } }
    println("NAV_RESTORE_OK routes_and_task_restores=$restoreCount stop_callbacks=$stops")
    val motion=MotionPolicy(c); var denied=0; var reducedPaths=0; var nativeStates=0
    for ((id,t) in c.tasks) for (mid in t.strings("motion_ids")) {
        check(motion.decide(id,mid,null,"not_an_event",emptySet(),true)==MotionDecision.Blocked); denied++
        val gate=c.motionPermissions.getValue(mid).obj(); if (gate.strings("requires").isNotEmpty()) check(motion.decide(id,mid,null,gate.s("event"),emptySet(),true)==MotionDecision.Blocked)
        when(val d=motion.decide(id,mid,null,gate.s("event"),gate.strings("requires").toSet(),true)) {
            is MotionDecision.Media -> {check(d.path in c.paths && d.reducedSteps.all { it in c.paths }); reducedPaths++}
            MotionDecision.NativeState -> nativeStates++
            MotionDecision.Blocked -> Unit
        }
    }
    check(HitPolicy().targetDp(32)==56 && HitPolicy().targetDp(32,true)==48 && HitPolicy().pages((1..9).map{it.toString()},99).map{it.size}==listOf(6,3))
    println("MOTION_LAYOUT_OK blocked_unauthorized=$denied reduced_paths=$reducedPaths native_state=$nativeStates")
    var clock=1000L; val parent=ParentGate{clock};check(!parent.allowed());check(!parent.verifyAdultChallenge(25,23,24));check(parent.verifyAdultChallenge(47,23,24)&&parent.allowed());clock+=60_001;check(!parent.allowed())
    println("PARENT_GATE_OK")
    println("HTQ_CORE_CONTRACT_CHECK_PASS")
}
