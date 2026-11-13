package com.gamestream.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.TextFilterRender
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.BitrateAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns the encoder. It keeps streaming while the user
 * is inside a game, which is the whole point of the app.
 *
 * Pipeline: MediaProjection screen capture, plus microphone, internal game
 * audio, or both mixed, encoded to H.264 and AAC and pushed over RTMP or
 * RTMPS by RootEncoder. Keyframe interval is fixed at 2 seconds, the value
 * YouTube asks for.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class StreamService : Service(), ConnectChecker {

    companion object {
        private const val TAG = "StreamService"
        private const val CHANNEL_ID = "game_stream_live"
        const val NOTIFY_ID = 4242
        const val ACTION_STOP = "com.gamestream.live.STOP"
        const val ACTION_TOGGLE_MUTE = "com.gamestream.live.TOGGLE_MUTE"
        var INSTANCE: StreamService? = null

        /** Set when service startup itself failed, so the UI can say why. */
        @Volatile
        var startupError: String = ""
    }

    private lateinit var genericStream: GenericStream
    private lateinit var settings: StreamSettings
    private var mediaProjection: MediaProjection? = null
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var uiCallback: ConnectChecker? = null

    private val bitrateAdapter = BitrateAdapter { adapted ->
        if (::genericStream.isInitialized && genericStream.isStreaming) {
            genericStream.setVideoBitrateOnFly(adapted)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wantLive = false
    private var reconnectAttempts = 0
    private var recordPath = ""

    var startedAtMillis: Long = 0L
        private set
    var micMuted: Boolean = false
        private set
    var lastError: String = ""
        private set

    private var streamCreated = false

    override fun onCreate() {
        super.onCreate()
        // Register first, before any heavy work. In v1.0 the instance was only
        // published after the encoder was fully prepared, so any hiccup in
        // encoder setup left the UI permanently seeing "service still
        // starting" even though the service process was alive.
        INSTANCE = this
        startupError = ""
        settings = try {
            SettingsStore(this).load()
        } catch (t: Throwable) {
            Log.e(TAG, "Settings load failed, using defaults", t)
            StreamSettings()
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live stream", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /**
     * Builds the encoder on first use, not at service startup, and reports
     * failure through lastError instead of leaving the service half alive.
     */
    private fun ensureStream(): Boolean {
        if (streamCreated) return true
        return try {
            genericStream = GenericStream(baseContext, this, NoVideoSource(), MicrophoneSource())
            // MediaProjection only produces frames when the screen changes.
            // Forcing render keeps a constant frame rate in menus and static scenes.
            genericStream.getGlInterface().setForceRender(true, maxOf(10, settings.preset.fps / 2))
            prepareEncoders()
            streamCreated = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Encoder creation failed", t)
            lastError = "Encoder could not start on this phone: ${t.message}"
            startupError = lastError
            false
        }
    }

    private fun prepareEncoders() {
        val preset = settings.preset
        val isPortrait = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        // The outgoing shape comes from the orientation setting, not from how
        // the phone happens to be held during setup. With rotation 90 the
        // encoder swaps width and height itself, so the long side is always
        // passed as width. Landscape is the default because games are played
        // landscape: following the upright setup screen sent a tall, narrow
        // video that looked tiny on a PC.
        val rotation = when (settings.orientationMode) {
            OrientationMode.LANDSCAPE -> 0
            OrientationMode.PORTRAIT -> 90
            OrientationMode.AUTO -> if (isPortrait) 90 else 0
        }
        val okVideo = try {
            genericStream.prepareVideo(
                width = preset.width,
                height = preset.height,
                bitrate = settings.effectiveVideoBitrate,
                fps = preset.fps,
                iFrameInterval = 2,
                rotation = rotation
            )
        } catch (e: Throwable) {
            Log.e(TAG, "prepareVideo failed", e)
            false
        }
        val okAudio = try {
            genericStream.prepareAudio(
                44_100, true, settings.audioBitrate,
                echoCanceler = true,
                noiseSuppressor = true
            )
        } catch (e: Throwable) {
            Log.e(TAG, "prepareAudio failed", e)
            false
        }
        if (!okVideo || !okAudio) {
            lastError = "Encoder setup failed for ${preset.label}. Try a lower quality."
        }
        bitrateAdapter.setMaxBitrate(settings.effectiveVideoBitrate + settings.audioBitrate)
    }

    /** Called by the activity whenever the user changes settings while idle. */
    fun applySettings(newSettings: StreamSettings) {
        if (::genericStream.isInitialized &&
            (genericStream.isStreaming || genericStream.isRecording)
        ) return
        settings = newSettings
        if (streamCreated && ::genericStream.isInitialized) {
            try {
                genericStream.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Encoder release failed", t)
            }
        }
        // The encoder is rebuilt lazily with the new settings on Go live.
        streamCreated = false
    }

    fun setUiCallback(callback: ConnectChecker?) {
        uiCallback = callback
    }

    fun sendIntent(): Intent = mediaProjectionManager.createScreenCaptureIntent()

    fun isStreaming(): Boolean = ::genericStream.isInitialized && genericStream.isStreaming
    fun isRecording(): Boolean = ::genericStream.isInitialized && genericStream.isRecording

    /**
     * Takes the screen capture permission result, points the encoder at the
     * screen and the chosen audio source, then goes live.
     */
    fun goLive(resultCode: Int, data: Intent): Boolean {
        if (!ensureStream()) return false
        startForegroundWithNotification("Connecting")
        mediaProjection?.stop()
        val projection = try {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Throwable) {
            Log.e(TAG, "getMediaProjection failed", e)
            null
        } ?: return false
        mediaProjection = projection

        genericStream.changeVideoSource(ScreenSource(applicationContext, projection))
        if (!applyAudioSource()) return false
        applyOverlay()
        wantLive = true
        reconnectAttempts = 0
        genericStream.startStream(settings.endpoint())
        if (settings.recordWhileStreaming) startRecording()
        return true
    }

    private fun applyAudioSource(): Boolean {
        return try {
            when (settings.audioMode) {
                AudioMode.MICROPHONE -> genericStream.changeAudioSource(MicrophoneSource())
                AudioMode.INTERNAL -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        lastError = "Game audio needs Android 10 or newer. Use microphone or mix on newer phones."
                        return false
                    }
                    val mp = mediaProjection ?: return false
                    genericStream.changeAudioSource(InternalAudioSource(mp))
                }
                AudioMode.MIX -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        genericStream.changeAudioSource(MicrophoneSource())
                    } else {
                        val mp = mediaProjection ?: return false
                        genericStream.changeAudioSource(MixAudioSource(mp))
                    }
                }
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "changeAudioSource failed", e)
            lastError = "Audio source failed: ${e.message}"
            false
        }
    }

    private fun applyOverlay() {
        if (!settings.showOverlay || settings.overlayText.isBlank()) return
        try {
            val filter = TextFilterRender()
            filter.setText(
                settings.overlayText.trim(), 34f, Color.WHITE,
                Color.argb(140, 0, 0, 0)
            )
            filter.setScale(38f, 38f)
            filter.setPosition(TranslateTo.TOP_LEFT)
            genericStream.getGlInterface().addFilter(filter)
        } catch (e: Throwable) {
            Log.w(TAG, "Overlay failed, streaming without it", e)
        }
    }

    /**
     * Local recording without going live. Uses the same capture permission
     * and sources, then records the screen to an MP4 on the device.
     */
    fun goRecordOnly(resultCode: Int, data: Intent): Boolean {
        if (!ensureStream()) return false
        startForegroundWithNotification("Recording")
        mediaProjection?.stop()
        val projection = try {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Throwable) {
            Log.e(TAG, "getMediaProjection failed", e)
            null
        } ?: return false
        mediaProjection = projection
        genericStream.changeVideoSource(ScreenSource(applicationContext, projection))
        if (!applyAudioSource()) return false
        applyOverlay()
        startedAtMillis = System.currentTimeMillis()
        startRecording()
        return true
    }

    fun stopStream() {
        wantLive = false
        mainHandler.removeCallbacksAndMessages(null)
        if (!::genericStream.isInitialized) return
        if (genericStream.isRecording) genericStream.stopRecord()
        if (genericStream.isStreaming) genericStream.stopStream()
        startedAtMillis = 0L
        updateNotification("Ready")
    }

    fun toggleMicMute() {
        if (!streamCreated || !::genericStream.isInitialized) return
        micMuted = !micMuted
        try {
            when (val source = genericStream.audioSource) {
                is MicrophoneSource -> if (micMuted) source.mute() else source.unMute()
                is MixAudioSource -> if (micMuted) source.mute() else source.unMute()
                else -> {
                    // Internal game audio has no microphone to mute.
                    micMuted = false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Mute toggle failed", e)
        }
        updateNotification(if (isStreaming()) "Live" else "Ready")
    }

    fun startRecording() {
        if (!::genericStream.isInitialized || genericStream.isRecording) return
        val folder = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameStreamLive")
        if (!folder.exists()) folder.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        recordPath = File(folder, "stream_$stamp.mp4").absolutePath
        genericStream.startRecord(recordPath) { }
    }

    fun recordingsFolder(): File =
        File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameStreamLive")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStream()
                uiCallback?.onDisconnect()
            }
            ACTION_TOGGLE_MUTE -> toggleMicMute()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStream()
        if (::genericStream.isInitialized) genericStream.release()
        mediaProjection?.stop()
        mediaProjection = null
        INSTANCE = null
        super.onDestroy()
    }

    // Notification

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val muteIntent = PendingIntent.getService(
            this, 2,
            Intent(this, StreamService::class.java).setAction(ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        @Suppress("DEPRECATION")
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("GameStream Live")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(isStreaming())
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_lock_silent_mode_off,
                    if (micMuted) "Unmute mic" else "Mute mic", muteIntent
                ).build()
            )
            .build()
    }

    private fun startForegroundWithNotification(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (settings.audioMode != AudioMode.INTERNAL) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            // Android ties microphone foreground services to the app being in
            // a foreground eligible state, and rejects the call with a
            // SecurityException otherwise. Never let that kill the stream:
            // fall back to projection only, the microphone permission itself
            // is separate and still held.
            try {
                startForeground(NOTIFY_ID, notification, types)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Typed foreground start rejected, retrying projection only", e)
            }
            try {
                startForeground(
                    NOTIFY_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Projection foreground start rejected, using plain start", e)
            }
        }
        try {
            startForeground(NOTIFY_ID, notification)
        } catch (t: Throwable) {
            Log.e(TAG, "Foreground start failed completely", t)
            lastError = "Could not start the foreground service on this phone."
        }
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_ID, buildNotification(status))
    }

    // ConnectChecker: encoder events, forwarded to the UI

    override fun onConnectionStarted(url: String) {
        uiCallback?.onConnectionStarted(url)
    }

    override fun onConnectionSuccess() {
        reconnectAttempts = 0
        startedAtMillis = System.currentTimeMillis()
        updateNotification("Live")
        uiCallback?.onConnectionSuccess()
    }

    override fun onNewBitrate(bitrate: Long) {
        if (settings.adaptiveBitrate) bitrateAdapter.adaptBitrate(bitrate)
        uiCallback?.onNewBitrate(bitrate)
    }

    override fun onConnectionFailed(reason: String) {
        lastError = reason
        uiCallback?.onConnectionFailed(reason)
        if (wantLive && settings.autoReconnect && reconnectAttempts < 5) {
            reconnectAttempts++
            updateNotification("Reconnecting, attempt $reconnectAttempts")
            mainHandler.postDelayed({
                if (wantLive && !genericStream.isStreaming) {
                    genericStream.startStream(settings.endpoint())
                }
            }, 5_000)
        } else if (wantLive) {
            wantLive = false
            updateNotification("Connection failed")
        }
    }

    override fun onDisconnect() {
        startedAtMillis = 0L
        uiCallback?.onDisconnect()
    }

    override fun onAuthError() {
        lastError = "Server rejected the stream key. Check the key in YouTube Studio."
        wantLive = false
        uiCallback?.onAuthError()
    }

    override fun onAuthSuccess() {
        uiCallback?.onAuthSuccess()
    }
}
