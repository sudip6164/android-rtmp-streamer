plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gamestream.live"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamestream.live"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // RootEncoder 2.8.1: RTMP/RTMPS encoder with screen capture (MediaProjection),
    // internal game audio, microphone and mixed audio sources, MP4 recording.
    // The app deliberately uses framework UI only, so this is the one dependency.
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
}
