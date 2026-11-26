package vn.htq.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import vn.htq.core.AudioBackend
import vn.htq.core.canonicalPath

/** All public methods and callback dispatch run on the main thread. */
class AndroidAudio(private val context: Context): AudioBackend {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focused = false
    var onFocusLost: (() -> Unit)? = null
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val listener = AudioManager.OnAudioFocusChangeListener { change -> if (change < 0) handler.post { onFocusLost?.invoke() ?: stop() } }
    @Suppress("DEPRECATION")
    private fun acquire(): Boolean {
        if (focused) return true
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(listener, handler).setWillPauseWhenDucked(true).build()
            focusRequest = req; manager.requestAudioFocus(req)
        } else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focused
    }
    @Suppress("DEPRECATION")
    override fun stop() {
        val old = player; player = null; old?.release()
        if (focused) { if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { manager.abandonAudioFocusRequest(it) } else manager.abandonAudioFocus(listener) }
        focused = false; focusRequest = null
    }
    override fun play(path: String, onComplete: (Boolean) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        stop(); if (!acquire()) { onComplete(false); return }
        val p = MediaPlayer(); player = p
        fun done(ok: Boolean) { if (player !== p) return; stop(); onComplete(ok) }
        try {
            p.setAudioAttributes(attrs)
            context.assets.openFd("htq/" + canonicalPath(path)).use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            p.setOnPreparedListener { if (player === p) it.start() }
            p.setOnCompletionListener { done(true) }
            p.setOnErrorListener { _, _, _ -> done(false); true }
            p.prepareAsync()
        } catch (_: Exception) { done(false) }
    }
}
