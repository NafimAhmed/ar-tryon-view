plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.nafim.ar_tryon_view_example"

    // ✅ NDK mismatch fix (26.3 -> 27.0.12077973)
    ndkVersion = "27.0.12077973"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.nafim.ar_tryon_view_example"

        // ✅ minSdk mismatch fix (plugin needs 24)
        minSdk = 24

        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}
