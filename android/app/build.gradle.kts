import java.io.File

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.manga_reader"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.manga_reader"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

tasks.register("renameReleaseApk") {
    doLast {
        val outputDir = layout.buildDirectory.dir("outputs/flutter-apk").get().asFile
        val sourceApk = File(outputDir, "app-release.apk")
        val targetApk = File(outputDir, "Manga Reader.apk")
        val sourceSha1 = File(outputDir, "app-release.apk.sha1")
        val targetSha1 = File(outputDir, "Manga Reader.apk.sha1")
        val legacyNamedApk = File(outputDir, "Manga Reader-release.apk")
        val legacyNamedSha1 = File(outputDir, "Manga Reader-release.apk.sha1")

        if (sourceApk.exists()) {
            if (targetApk.exists()) {
                targetApk.delete()
            }
            sourceApk.copyTo(targetApk, overwrite = true)
        }

        if (sourceSha1.exists()) {
            if (targetSha1.exists()) {
                targetSha1.delete()
            }
            sourceSha1.copyTo(targetSha1, overwrite = true)
        }

        if (legacyNamedApk.exists()) {
            legacyNamedApk.delete()
        }

        if (legacyNamedSha1.exists()) {
            legacyNamedSha1.delete()
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "packageRelease" || name == "bundleRelease") {
        finalizedBy("renameReleaseApk")
    }
}
