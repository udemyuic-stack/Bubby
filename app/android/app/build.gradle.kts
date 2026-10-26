plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// One ABI per APK keeps the download small. Default is 64-bit ARM. For 32-bit ARM run
// `flutter build apk --release --target-platform android-arm` (Flutter passes that as the
// `target-platform` property), or force it with `-Pbuddy.abi=armeabi-v7a`.
val buddyAbi: String = (project.findProperty("buddy.abi") as String?)
    ?: if ((project.findProperty("target-platform") as String?)?.split(",")?.map { it.trim() } == listOf("android-arm")) "armeabi-v7a" else "arm64-v8a"
require(buddyAbi in listOf("arm64-v8a", "armeabi-v7a")) { "buddy.abi must be arm64-v8a or armeabi-v7a, got $buddyAbi" }

android {
    namespace = "com.example.buddy"
    compileSdk = flutter.compileSdkVersion

    packaging {
        // real phones only: drop every native library that is not for the chosen ABI
        jniLibs {
            excludes += listOf("**/x86_64/*.so", "**/x86/*.so") +
                listOf("arm64-v8a", "armeabi-v7a").filter { it != buddyAbi }.map { "**/$it/*.so" }
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/DebugProbesKt.bin",
                "/kotlin/**",
                "/**/*.kotlin_builtins",
                "/**/*.kotlin_metadata"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.buddy"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        // phones only: x86 libraries from dependencies (LiteRT-LM) would add ~26 MB
        ndk { abiFilters += listOf(buddyAbi) }
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
}

flutter {
    source = "../.."
}
