package com.gamestream.live

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists settings in app private storage.
 * The stream key never leaves the device except inside the RTMP handshake
 * with the server the user chose. Treat it like a password all the same:
 * anyone who has it can stream to that channel.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stream_settings", Context.MODE_PRIVATE)

    fun load(): StreamSettings = StreamSettings(
        serverUrl = prefs.getString("server_url", StreamSettings.YOUTUBE_RTMPS_URL)
            ?: StreamSettings.YOUTUBE_RTMPS_URL,
        streamKey = prefs.getString("stream_key", "") ?: "",
        preset = runCatching {
            Preset.valueOf(prefs.getString("preset", Preset.P1080_30.name)!!)
        }.getOrDefault(Preset.P1080_30),
        videoBitrate = prefs.getInt("video_bitrate", 0),
        adaptiveBitrate = prefs.getBoolean("adaptive_bitrate", true),
        orientationMode = runCatching {
            OrientationMode.valueOf(
                prefs.getString("orientation_mode", OrientationMode.LANDSCAPE.name)!!
            )
        }.getOrDefault(OrientationMode.LANDSCAPE),
        autoReconnect = prefs.getBoolean("auto_reconnect", true),
        audioMode = runCatching {
            AudioMode.valueOf(prefs.getString("audio_mode", AudioMode.MIX.name)!!)
        }.getOrDefault(AudioMode.MIX),
        recordWhileStreaming = prefs.getBoolean("record", false),
        overlayText = prefs.getString("overlay_text", "") ?: "",
        showOverlay = prefs.getBoolean("show_overlay", false)
    )

    fun save(settings: StreamSettings) {
        prefs.edit()
            .putString("server_url", settings.serverUrl)
            .putString("stream_key", settings.streamKey)
            .putString("preset", settings.preset.name)
            .putInt("video_bitrate", settings.videoBitrate)
            .putBoolean("adaptive_bitrate", settings.adaptiveBitrate)
            .putString("orientation_mode", settings.orientationMode.name)
            .putBoolean("auto_reconnect", settings.autoReconnect)
            .putString("audio_mode", settings.audioMode.name)
            .putBoolean("record", settings.recordWhileStreaming)
            .putString("overlay_text", settings.overlayText)
            .putBoolean("show_overlay", settings.showOverlay)
            .apply()
    }
}
