package com.gamestream.live

/**
 * All user facing stream configuration in one place.
 * Defaults are tuned for YouTube Live over a typical mobile connection.
 */
data class StreamSettings(
    val serverUrl: String = YOUTUBE_RTMPS_URL,
    val streamKey: String = "",
    val preset: Preset = Preset.P1080_30,
    /** Video bitrate in bits per second. 0 means use the preset value. */
    val videoBitrate: Int = 0,
    val adaptiveBitrate: Boolean = true,
    val orientationMode: OrientationMode = OrientationMode.LANDSCAPE,
    val autoReconnect: Boolean = true,
    val audioMode: AudioMode = AudioMode.MIX,
    val audioBitrate: Int = 128_000,
    val recordWhileStreaming: Boolean = false,
    val overlayText: String = "",
    val showOverlay: Boolean = false
) {
    val effectiveVideoBitrate: Int
        get() = if (videoBitrate > 0) videoBitrate else preset.videoBitrate

    fun endpoint(): String {
        val base = serverUrl.trim().trimEnd('/')
        val key = streamKey.trim()
        return if (key.isEmpty() || base.endsWith(key)) base else "$base/$key"
    }

    companion object {
        const val YOUTUBE_RTMP_URL = "rtmp://a.rtmp.youtube.com/live2"
        const val YOUTUBE_RTMPS_URL = "rtmps://a.rtmp.youtube.com/live2"
        const val YOUTUBE_BACKUP_URL = "rtmp://b.rtmp.youtube.com/live2?backup=1"
    }
}

enum class AudioMode {
    /** Microphone only. */
    MICROPHONE,

    /** Game / device internal audio only. Needs Android 10 or newer. */
    INTERNAL,

    /** Game audio mixed with microphone commentary. Needs Android 10 or newer. */
    MIX
}

/**
 * Which shape the outgoing video has. Games are landscape, so landscape is
 * the default: the stream is built wide even though all the setup happens
 * with the phone held upright. Following the phone instead was what made
 * streams appear small on a PC screen.
 */
enum class OrientationMode {
    /** Wide video, right for games. */
    LANDSCAPE,

    /** Tall video, for portrait streams. */
    PORTRAIT,

    /** Follow how the phone is held when the stream starts. */
    AUTO
}

/**
 * Resolution, frame rate and video bitrate for each quality. Bitrates match
 * YouTube's recommended H.264 live settings from its own help page. The
 * earlier presets sat near YouTube's minimum bitrates, which is exactly what
 * triggered YouTube's stream quality warning. They need an upload connection
 * faster than the bitrate, so run a speed test and pick a preset below your
 * upload speed. Keyframe interval is fixed at 2 seconds in the service, as
 * YouTube requires.
 */
enum class Preset(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrate: Int
) {
    P1080_60("1080p, 60 fps, 17 Mbps", 1920, 1080, 60, 17_000_000),
    P1080_30("1080p, 30 fps, 14 Mbps", 1920, 1080, 30, 14_000_000),
    P720_60("720p, 60 fps, 8 Mbps", 1280, 720, 60, 8_000_000),
    P720_30("720p, 30 fps, 8 Mbps", 1280, 720, 30, 8_000_000),
    P480_30("480p, 30 fps, 4 Mbps", 854, 480, 30, 4_000_000);

    override fun toString(): String = label
}
