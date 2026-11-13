# GameStream Live: setup from zero

Everything from a fresh computer to a running app on your phone.
Windows is written out in full. Mac and Linux are the same inside Android Studio, only the install step and the SDK folder differ.

## What you need

- A computer with at least 8 GB RAM and about 10 GB of free disk space
- Internet for the first setup, Android Studio downloads several GB
- Your Android phone, Android 8 or newer
- A USB cable, optional, you can also just copy the APK file to the phone

You do not need to download Java or a JDK separately. Android Studio ships with its own JDK and uses it automatically.

## Step 1: Download and install Android Studio

1. Go to the official page: https://developer.android.com/studio
2. Download Android Studio for your system and run the installer.
3. Keep the default install location. Tick Android Studio and Android Virtual Device if asked.
4. Finish the installer and open Android Studio.

## Step 2: First launch setup wizard

1. On first launch choose: Do not import settings, if asked.
2. Choose the Standard setup type.
3. Android Studio now downloads the Android SDK to its default folder:
   - Windows: C:\Users\YOURNAME\AppData\Local\Android\Sdk
   - Mac: /Users/YOURNAME/Library/Android/sdk
   - Linux: /home/YOURNAME/Android/Sdk
4. Accept every licence page and let the download finish. This is the big one, be patient on slow internet.

## Step 3: Install the exact SDK parts this project needs

The app targets Android SDK 35, so this one must be present.

1. In Android Studio open: More Actions (or File, Settings), then SDK Manager.
   Full path inside settings: Languages and Frameworks, Android SDK.
2. SDK Platforms tab: tick Android 15.0, API Level 35.
3. SDK Tools tab: tick these:
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools (latest)
   - Android Emulator, optional, only if you want to test without a phone. Game streaming needs a real phone, the emulator cannot capture a real game properly.
4. Click Apply, accept licences, wait for the downloads, then OK.

## Step 4: Get the project on your computer

Clone the repository:

    git clone https://github.com/sudip6164/android-rtmp-streamer.git

Or download it as a ZIP from the GitHub page (Code, Download ZIP) and unzip it. You get a folder named android-rtmp-streamer.

Put it somewhere simple, for example Documents\android-rtmp-streamer. Avoid very deep folders and folders with spaces on Windows.

Check the folder: inside android-rtmp-streamer you should see settings.gradle.kts, build.gradle.kts, gradle.properties, and the app folder. If you see another android-rtmp-streamer folder inside, use the inner one in the next step.

## Step 5: Open the project in Android Studio

1. Android Studio welcome screen: Open.
2. Select the android-rtmp-streamer folder and confirm.
3. Android Studio starts a Gradle sync. The first sync downloads Gradle 8.13, the Android Gradle Plugin, the Kotlin plugin, and the RootEncoder streaming library from the internet. Ten to thirty minutes on a slow connection is normal the first time.
4. If Android Studio offers to install a missing SDK Platform 35 or Build-Tools, accept.
5. If it asks which JDK Gradle should use, the right choice is the Embedded JDK:
   Settings, Build, Execution, Deployment, Build Tools, Gradle, Gradle JDK: Embedded JDK.

Sync is finished when the progress bar is gone and there is no red error banner. A warning or two is fine.

## Step 6: Build the APK

In the latest Android Studio the full menu bar is hidden in the New UI. Click the main menu icon, three horizontal lines at the very top left of the window, then:

Build, Generate Bundle(s) / APK(s), Generate APK(s).

Faster method that works in every version: press Ctrl+Shift+A, type Generate APKs, press Enter.
Another method: open the Gradle panel on the right edge (elephant icon), then GameStreamLive, app, Tasks, build, and double click assembleDebug.

You can also skip the APK file entirely: connect the phone and press the Run button in the toolbar. Run builds the app and installs it on the phone in one step.

When it finishes, click locate in the popup. The file is:

    android-rtmp-streamer\app\build\outputs\apk\debug\app-debug.apk

That is the file you install on the phone.

## Step 7: Put the app on your phone

Easier way, copy the file:
1. Copy app-debug.apk to the phone by USB, or send it to yourself.
2. Open the file on the phone.
3. Allow install from that source when Android asks, then Install.

Direct way, from Android Studio:
1. On the phone: Settings, About phone, tap Build number 7 times to unlock Developer options.
2. Settings, Developer options: turn on USB debugging.
3. Connect the phone by USB, allow the debugging prompt on the phone.
4. In Android Studio press the green Run button. It builds, installs, and opens the app.

Note: the ready made APKs from the Releases page and an Android Studio debug build are both debug signed, but with different debug keys. If the phone refuses to install one over the other, uninstall GameStream Live first, then install.

## Step 8: First stream

1. YouTube Studio: Create, Go live. First time only, verify your phone number and wait for live access, about 24 hours.
2. In the Stream tab, copy the stream key.
3. In the app: paste the key, pick an audio mode (game plus mic, game only, or mic only), pick 720p 60 for a first test.
4. Set the YouTube stream to Private for the test, tap Go live in the app, allow screen capture, open your game.
5. Check picture and sound on the YouTube preview, then stop from the app notification.

## Common problems

- Gradle sync failed or downloads stall: it is almost always the internet connection. Reopen Android Studio and let sync retry, it resumes where it stopped.
- SDK location not found: open SDK Manager and note the Android SDK Location at the top, then check Settings, Android SDK points to the same folder.
- Missing SDK Platform 35: do Step 3 again and tick API 35, then Apply.
- Phone not seen by Android Studio: unlock the phone screen, replug the cable, accept the USB debugging prompt, and try a cable that carries data, not charge only.
- Install blocked on the phone: allow install from the file manager or browser you used to open the APK, under Settings, Apps, Special access, Install unknown apps.
- Stream connects but YouTube shows nothing: the stream key belongs to an old or finished stream. Create a fresh stream in YouTube Studio and copy the new key.
