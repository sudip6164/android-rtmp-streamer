# GameStream Live

An Android app that streams your phone screen, your games, to YouTube Live over RTMP or RTMPS. It also works with any other RTMP server, for example Twitch, Facebook Live, or a custom server, because the server URL and stream key are editable.

Ready to install APKs are on the [Releases](https://github.com/sudip6164/android-rtmp-streamer/releases) page. They are debug signed builds: copy the APK to your phone, open it, and allow install from that source. Run a private test stream first, as described below.

To rebuild or change the app, open this folder in Android Studio and build from there. The Gradle wrapper scripts are not included, Android Studio generates them on first open. See [SETUP.md](SETUP.md) for a full from-zero guide.

Built with RootEncoder 2.8.1, the standard open source Android streaming library. Video is H.264, audio is AAC, keyframe interval is 2 seconds, which matches what YouTube asks encoders for.

## Features

Destination and keys
- YouTube presets built in: secure RTMPS, plain RTMP, and the YouTube backup server
- Custom server URL field for any RTMP or RTMPS service
- Stream key field with show and hide, saved on the phone only
- One tap Go live, with Android screen capture permission asked each session, as Android requires

Video
- Quality presets: 1080p at 60 or 30 fps, 720p at 60 or 30 fps, 480p at 30 fps
- Preset bitrates match YouTube's recommended H.264 live settings (1080p60 at 17 Mbps, 1080p30 at 14 Mbps, 720p at 8 Mbps, 480p at 4 Mbps), so YouTube does not flag the stream for low quality. They need upload speed above the bitrate, so speed test first
- Custom video bitrate override in kbps
- Adaptive bitrate: drops quality on a weak connection instead of killing the stream
- Stream orientation setting: landscape for games (the default, fills a PC screen), portrait for vertical streams, or auto to follow the phone
- Constant frame rate even on static screens, so menus do not stutter on YouTube

Audio
- Game audio plus microphone mix, for commentary over gameplay, Android 10 and up
- Game audio only, Android 10 and up
- Microphone only, for older phones or quiet games
- Echo canceller and noise suppressor on the microphone
- Mute and unmute the microphone live, from the app or the notification

Staying live
- The stream runs in a foreground service, so it keeps going while you play
- Notification controls while you are in the game: stop the stream, mute the mic
- Auto reconnect, up to 5 tries, if the connection drops
- Live status, current bitrate, stream duration, and recording state on the home screen

Extras
- Record an MP4 copy on the phone while streaming
- Record screen only mode, no stream, for making gameplay videos to edit later
- Text overlay burned into the stream, top left, for a channel name or a short message

## Not in this version, and why

- Facecam picture in picture. Compositing the front camera over a screen capture needs the multi-source pipeline and per phone testing. It is the top item for a later version.
- YouTube live chat inside the app. Reading chat needs YouTube account sign in and the YouTube API, which is a separate permission and review step. For now, open the YouTube app or Studio on a second screen for chat, or use YouTube chat in a browser overlay on the same phone after going live.
- iPhone version. iOS only allows screen broadcast through a separate broadcast extension with its own signing setup on a Mac. The streaming engine has an iOS sibling library, so an iPhone build is possible later, but it cannot be produced from this Android project.

## Before your first stream

1. Turn on live streaming for your channel. In YouTube Studio, open Create, then Go live. The first time, YouTube asks for phone verification and live access takes about 24 hours to switch on.
2. In YouTube Studio, open the Stream tab and copy two things: the stream URL and the stream key. Keep the key private, anyone who has it can stream to your channel.
3. Paste the key into this app. The server URL is already set to the YouTube secure server.

## How to stream, each time

1. Open GameStream Live and check the quality preset. 720p at 60 fps is the safest start on mobile data, 1080p needs a strong upload.
2. Tap Go live.
3. Allow microphone if asked, then allow screen capture. Android shows that permission every time, it cannot be remembered.
4. Switch to your game and play. The stream keeps running in the background.
5. To stop, use the notification or come back to the app and tap Stop stream. Then end the stream in YouTube Studio if it does not end on its own.

## Building the app

You need Android Studio, or just a JDK 17 and the Android SDK.

Command line build, from the project root:

    ./gradlew assembleDebug

The APK appears at:

    app/build/outputs/apk/debug/app-debug.apk

For a signed store build, open the project in Android Studio, use Build, Generate Signed App Bundle, and follow the Play Console steps. Do not commit your signing key or your stream key to any public repository.

## Installing a debug APK on your phone

1. Copy the APK to the phone.
2. Open it on the phone and allow install from that source when Android asks.
3. Open GameStream Live, paste your stream key, and do a private test stream first. Set the test stream to Private in YouTube Studio and check video, game audio, and your voice before going public.

## Troubleshooting

- YouTube says no stream received: the key belongs to a different or finished stream. Create a fresh stream in Studio and copy the new key.
- Stream is live but silent: if the game blocks internal audio capture, some games do, switch audio to microphone or mix and keep the phone volume up.
- Lag or pixelation in game: drop one quality preset, turn adaptive bitrate on, and prefer Wi-Fi or strong 5G. 1080p at 60 fps uses YouTube's recommended 17 Mbps, so it needs upload speed comfortably above that.
- YouTube warns the quality is too low: your outgoing bitrate is below what YouTube recommends for the resolution you send. Use the preset bitrates (they follow YouTube's table), check the orientation is set to landscape for games, and confirm your upload speed is above the preset bitrate, because adaptive bitrate lowers quality when the connection cannot keep up.
- Stream looks small on a PC: the orientation was portrait. Set Stream orientation to Landscape for games.
- Stream stopped when the phone locked or the app closed: keep the app notification visible, and exclude the app from battery optimisation in phone Settings, Apps, GameStream Live, Battery, Unrestricted.
- Internal audio options missing effect on old phones: game audio capture requires Android 10 or newer, this is an Android platform limit.

## Privacy notes

- The stream key is stored only in app private storage on your phone.
- The app has no analytics, no ads, and no account of its own.
- Recordings are saved in the app folder shown on the home screen.

## License

GameStream Live is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for the full text.
