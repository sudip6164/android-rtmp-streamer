package com.gamestream.live

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.pedro.common.ConnectChecker
import java.util.Locale

class MainActivity : Activity(), ConnectChecker {

    private enum class PendingAction { STREAM, RECORD }

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_PERMISSIONS = 1002
    }

    private lateinit var store: SettingsStore
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvRecordPath: TextView
    private lateinit var btnLive: Button
    private lateinit var btnRecordOnly: Button
    private lateinit var btnMute: Button
    private lateinit var etServerUrl: EditText
    private lateinit var etStreamKey: EditText
    private lateinit var spinnerPreset: Spinner
    private lateinit var etBitrate: EditText
    private lateinit var switchAdaptive: Switch
    private lateinit var switchReconnect: Switch
    private lateinit var radioAudio: RadioGroup
    private lateinit var radioOrientation: RadioGroup
    private lateinit var switchRecord: Switch
    private lateinit var etOverlay: EditText
    private lateinit var switchOverlay: Switch

    private var pendingAction = PendingAction.STREAM

    // Screen capture result is stored here and only used in onResume.
    // Starting a microphone foreground service from inside onActivityResult
    // crashes on Android 14 and 15, because at that moment the activity is
    // not yet back in the foreground state Android requires for microphone
    // access. That was the v1.1 crash Sudip hit.
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var hasPendingCapture = false

    private val handler = Handler(Looper.getMainLooper())
    private var currentBitrateKbps = 0L

    private val statsTicker = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        store = SettingsStore(this)
        bindViews()
        fillFromSettings(store.load())
        wireButtons()

        if (StreamService.INSTANCE == null) {
            startService(Intent(this, StreamService::class.java))
        }
        waitForService()
        handler.post(statsTicker)
    }

    override fun onResume() {
        super.onResume()
        StreamService.INSTANCE?.setUiCallback(this)
        if (hasPendingCapture) {
            hasPendingCapture = false
            startPendingCapture()
        } else {
            refreshState()
        }
    }

    private fun startPendingCapture() {
        val service = StreamService.INSTANCE
        val data = pendingData
        pendingData = null
        if (service == null || data == null) {
            toast("Stream service is not ready, tap Go live once more")
            refreshState()
            return
        }
        val ok = try {
            when (pendingAction) {
                PendingAction.STREAM -> service.goLive(pendingResultCode, data)
                PendingAction.RECORD -> service.goRecordOnly(pendingResultCode, data)
            }
        } catch (t: Throwable) {
            toast("Could not start: ${t.message}")
            false
        }
        if (!ok) {
            toast(service.lastError.ifBlank { "Could not start, check your settings" })
        }
        refreshState()
    }

    override fun onDestroy() {
        handler.removeCallbacks(statsTicker)
        super.onDestroy()
    }

    @Deprecated("Framework result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (data == null || resultCode != RESULT_OK) {
            toast("Screen capture was not allowed, nothing started")
            refreshState()
            return
        }
        // Do not start the stream here. Store the permission result and let
        // onResume run it, when the app is fully in the foreground again.
        pendingResultCode = resultCode
        pendingData = data
        hasPendingCapture = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micOk) {
            toast("Microphone permission is needed for commentary audio")
        } else {
            launchCapture()
        }
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvRecordPath = findViewById(R.id.tvRecordPath)
        btnLive = findViewById(R.id.btnLive)
        btnRecordOnly = findViewById(R.id.btnRecordOnly)
        btnMute = findViewById(R.id.btnMute)
        etServerUrl = findViewById(R.id.etServerUrl)
        etStreamKey = findViewById(R.id.etStreamKey)
        spinnerPreset = findViewById(R.id.spinnerPreset)
        etBitrate = findViewById(R.id.etBitrate)
        switchAdaptive = findViewById(R.id.switchAdaptive)
        switchReconnect = findViewById(R.id.switchReconnect)
        radioAudio = findViewById(R.id.radioAudio)
        radioOrientation = findViewById(R.id.radioOrientation)
        switchRecord = findViewById(R.id.switchRecord)
        etOverlay = findViewById(R.id.etOverlay)
        switchOverlay = findViewById(R.id.switchOverlay)

        spinnerPreset.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Preset.values().toList()
        )
    }

    private fun fillFromSettings(s: StreamSettings) {
        etServerUrl.setText(s.serverUrl)
        etStreamKey.setText(s.streamKey)
        spinnerPreset.setSelection(s.preset.ordinal)
        etBitrate.setText(if (s.videoBitrate > 0) (s.videoBitrate / 1000).toString() else "")
        switchAdaptive.isChecked = s.adaptiveBitrate
        switchReconnect.isChecked = s.autoReconnect
        radioAudio.check(
            when (s.audioMode) {
                AudioMode.MIX -> R.id.rbMix
                AudioMode.INTERNAL -> R.id.rbInternal
                AudioMode.MICROPHONE -> R.id.rbMic
            }
        )
        radioOrientation.check(
            when (s.orientationMode) {
                OrientationMode.LANDSCAPE -> R.id.rbLandscape
                OrientationMode.PORTRAIT -> R.id.rbPortrait
                OrientationMode.AUTO -> R.id.rbAutoOrientation
            }
        )
        switchRecord.isChecked = s.recordWhileStreaming
        etOverlay.setText(s.overlayText)
        switchOverlay.isChecked = s.showOverlay
    }

    private fun collectSettings(): StreamSettings {
        val kbps = etBitrate.text.toString().trim().toIntOrNull() ?: 0
        return StreamSettings(
            serverUrl = etServerUrl.text.toString().trim()
                .ifBlank { StreamSettings.YOUTUBE_RTMPS_URL },
            streamKey = etStreamKey.text.toString().trim(),
            preset = Preset.values()[spinnerPreset.selectedItemPosition],
            videoBitrate = if (kbps > 0) kbps * 1000 else 0,
            adaptiveBitrate = switchAdaptive.isChecked,
            autoReconnect = switchReconnect.isChecked,
            audioMode = when (radioAudio.checkedRadioButtonId) {
                R.id.rbInternal -> AudioMode.INTERNAL
                R.id.rbMic -> AudioMode.MICROPHONE
                else -> AudioMode.MIX
            },
            orientationMode = when (radioOrientation.checkedRadioButtonId) {
                R.id.rbPortrait -> OrientationMode.PORTRAIT
                R.id.rbAutoOrientation -> OrientationMode.AUTO
                else -> OrientationMode.LANDSCAPE
            },
            recordWhileStreaming = switchRecord.isChecked,
            overlayText = etOverlay.text.toString(),
            showOverlay = switchOverlay.isChecked
        )
    }

    private fun wireButtons() {
        findViewById<Button>(R.id.btnYtSecure).setOnClickListener {
            etServerUrl.setText(StreamSettings.YOUTUBE_RTMPS_URL)
        }
        findViewById<Button>(R.id.btnYtPlain).setOnClickListener {
            etServerUrl.setText(StreamSettings.YOUTUBE_RTMP_URL)
        }
        findViewById<Button>(R.id.btnYtBackup).setOnClickListener {
            etServerUrl.setText(StreamSettings.YOUTUBE_BACKUP_URL)
        }
        findViewById<CheckBox>(R.id.cbShowKey).setOnCheckedChangeListener { _, checked ->
            etStreamKey.inputType = if (checked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etStreamKey.setSelection(etStreamKey.text.length)
        }

        btnLive.setOnClickListener {
            val service = StreamService.INSTANCE
            if (service != null && service.isStreaming()) {
                service.stopStream()
                refreshState()
                return@setOnClickListener
            }
            val settings = collectSettings()
            if (settings.streamKey.isBlank() && settings.serverUrl.contains("youtube")) {
                toast("Paste your YouTube stream key first, it is in YouTube Studio under Go live")
                return@setOnClickListener
            }
            beginWithPermissions(PendingAction.STREAM)
        }

        btnRecordOnly.setOnClickListener {
            val service = StreamService.INSTANCE
            if (service != null && service.isRecording() && !service.isStreaming()) {
                service.stopStream()
                refreshState()
                return@setOnClickListener
            }
            beginWithPermissions(PendingAction.RECORD)
        }

        btnMute.setOnClickListener {
            StreamService.INSTANCE?.toggleMicMute()
            refreshState()
        }
    }

    private fun beginWithPermissions(action: PendingAction) {
        pendingAction = action
        val settings = collectSettings()
        store.save(settings)
        StreamService.INSTANCE?.applySettings(settings)

        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) {
            launchCapture()
        } else {
            requestPermissions(needed.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun launchCapture(attempt: Int = 0) {
        val service = StreamService.INSTANCE
        if (service != null) {
            service.setUiCallback(this)
            @Suppress("DEPRECATION")
            startActivityForResult(service.sendIntent(), REQUEST_CAPTURE)
            return
        }
        // v1.0 stopped here with a toast, so a slow or restarted service made
        // every Go live tap fail the same way. Restart the service and keep
        // retrying for a few seconds instead of asking for another tap.
        startService(Intent(this, StreamService::class.java))
        tvStatus.text = "Status: starting stream service"
        if (attempt < 20) {
            handler.postDelayed({ launchCapture(attempt + 1) }, 300)
        } else {
            val reason = StreamService.startupError
            toast(
                if (reason.isNotBlank()) reason
                else "Stream service could not start. Close the app fully, open it again, and try once more."
            )
            refreshState()
        }
    }

    private fun waitForService() {
        handler.postDelayed({
            val service = StreamService.INSTANCE
            if (service != null) {
                service.setUiCallback(this)
                tvRecordPath.text =
                    "Recordings and stream copies are saved in: ${service.recordingsFolder().absolutePath}"
                refreshState()
            } else {
                waitForService()
            }
        }, 300)
    }

    private fun refreshState() {
        val service = StreamService.INSTANCE
        val live = service?.isStreaming() == true
        val recordingOnly = service?.isRecording() == true && !live
        btnLive.text = if (live) "Stop stream" else "Go live"
        btnRecordOnly.text = if (recordingOnly) "Stop recording" else "Record screen only, no stream"
        btnMute.text = if (service?.micMuted == true) "Unmute microphone" else "Mute microphone"
        tvStatus.text = when {
            live -> "Status: LIVE"
            recordingOnly -> "Status: recording to this phone"
            else -> "Status: not live"
        }
    }

    private fun updateStats() {
        val service = StreamService.INSTANCE
        val started = service?.startedAtMillis ?: 0L
        val active = service?.isStreaming() == true || service?.isRecording() == true
        val duration = if (started > 0 && active) {
            val seconds = (System.currentTimeMillis() - started) / 1000
            String.format(
                Locale.getDefault(), "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60
            )
        } else {
            "00:00:00"
        }
        tvStats.text = "Bitrate: $currentBitrateKbps kbps   Time: $duration" +
            if (service?.isRecording() == true) "   Recording: on" else ""
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ConnectChecker callbacks from the stream service

    override fun onConnectionStarted(url: String) {
        runOnUiThread { tvStatus.text = "Status: connecting" }
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            toast("You are live")
            refreshState()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        currentBitrateKbps = bitrate / 1000
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            toast("Connection failed: $reason")
            refreshState()
        }
    }

    override fun onDisconnect() {
        runOnUiThread {
            currentBitrateKbps = 0
            refreshState()
        }
    }

    override fun onAuthError() {
        runOnUiThread {
            toast("YouTube rejected the stream key. Copy a fresh key from YouTube Studio.")
            refreshState()
        }
    }

    override fun onAuthSuccess() {}
}
