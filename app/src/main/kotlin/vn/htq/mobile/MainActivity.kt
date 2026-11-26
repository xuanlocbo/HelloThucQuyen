package vn.htq.mobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import vn.htq.core.*

/** Technical foundation only. The approved child-facing screens belong to step 6.2. */
class MainActivity : Activity() {
    private var foundation: Foundation? = null
    private lateinit var column: LinearLayout
    private var resumed = false
    private var closed = false
    private class Foundation(val catalog: Catalog, val progress: LocalProgress, val backend: AndroidAudio) {
        val audio = AudioArbiter(backend)
        val audioPolicy = TaskAudioPolicy(catalog)
        val navigator = Navigator(catalog, audio::stop)
        val assessment = AssessmentEngine(catalog)
        val motion = MotionPolicy(catalog)
        val sessions = SessionPolicy(catalog)
        val parent = ParentGate { SystemClock.elapsedRealtime() }
        init { backend.onFocusLost = audio::stop }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(20); setPadding(p, p, p, p)
        }
        scroll.addView(column)
        // Keep controls out of system bars on target SDK 35.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        text(getString(R.string.app_name), 26f)
        text("6.1 - Loading local content", 16f)
        Thread {
            val result = runCatching {
                Catalog.load(object : AssetSource {
                    override fun open(relative: String) = assets.open("htq/" + canonicalPath(relative))
                })
            }
            runOnUiThread {
                if (closed || isFinishing) return@runOnUiThread
                result.fold(onSuccess = { c ->
                    val f = Foundation(c, LocalProgress(applicationContext, c), AndroidAudio(this))
                    foundation = f
                    f.progress.loadSession()?.let { saved -> f.navigator.go(Route.TaskRoute(saved.s("task_id"))) }
                    render(f)
                }, onFailure = {
                    column.removeAllViews(); text(getString(R.string.app_name), 26f)
                    text("Local content unavailable. No learning or reward action is enabled.", 16f)
                })
            }
        }.start()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float) {
        column.addView(TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(36, 71, 58)); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) })
    }
    private fun control(label: String, action: () -> Unit) {
        column.addView(Button(this).apply { text = label; minHeight = dp(HitPolicy().targetDp(56)); isAllCaps = false; setOnClickListener { if (resumed && !closed) action() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun render(f: Foundation) {
        column.removeAllViews(); text(getString(R.string.app_name), 26f)
        text("6.1 - Native technical foundation", 16f)
        fun route(label: String, r: Route) = control(label) { f.navigator.go(r); if (r is Route.TaskRoute) f.progress.saveSession(f.sessions.snapshotForTask(r.id)); render(f) }
        when (val r = f.navigator.route) {
            Route.Home -> { text("${f.catalog.taskCount} activities / ${f.catalog.assets.size} resources", 16f); f.catalog.areas.forEach { route(it.title, Route.AreaRoute(it.id)) } }
            is Route.AreaRoute -> f.catalog.areas.single { it.id == r.id }.topicIds.forEach { id -> route(f.catalog.topics.getValue(id).title, Route.TopicRoute(id)) }
            is Route.TopicRoute -> f.catalog.topics.getValue(r.id).lessonIds.forEach { id -> route(f.catalog.lessons.getValue(id).title, Route.LessonRoute(id)) }
            is Route.LessonRoute -> f.catalog.lessons.getValue(r.id).levelIds.forEach { id -> route(f.catalog.activities.getValue(id).title, Route.LevelRoute(id)) }
            is Route.LevelRoute -> f.catalog.activities.getValue(r.id).taskIds.forEachIndexed { index, id -> route("Task ${index + 1}", Route.TaskRoute(id)) }
            is Route.TaskRoute -> {
                text(f.catalog.childTask(r.id).prompt, 18f)
                control("Play instruction") { f.audioPolicy.request(r.id, AudioEvent.PROMPT, true)?.let(f.audio::request) }
                control("Stop audio", f.audio::stop)
                text("Interaction is not enabled until step 6.3. No grade or reward is issued.", 14f)
            }
        }
        if (f.navigator.route != Route.Home) control("Back") { f.navigator.back(); render(f) }
    }
    override fun onResume() { super.onResume(); resumed = true }
    override fun onPause() { resumed = false; foundation?.audio?.stop(); foundation?.parent?.close(); super.onPause() }
    override fun onStop() { foundation?.audio?.stop(); super.onStop() }
    override fun onDestroy() { closed = true; foundation?.audio?.stop(); foundation?.progress?.close(); foundation = null; super.onDestroy() }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { val f = foundation; if (f != null && f.navigator.route != Route.Home) { f.navigator.back(); render(f) } else super.onBackPressed() }
}
