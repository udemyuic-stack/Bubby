plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The app is plain Kotlin on the Android framework: no native libraries, so one APK runs on 32-bit ARM,
// 64-bit ARM and x86 alike. `-Pbuddy.version=1.2.3 -Pbuddy.code=45` set the version (CI passes the run number).
val buddyCode = (project.findProperty("buddy.code") as String?)?.toInt() ?: 1
val buddyVersion = (project.findProperty("buddy.version") as String?) ?: "1.0.0"

android {
    namespace = "com.example.buddy"
    compileSdk = 36

    packaging {
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
        applicationId = "com.example.buddy"
        minSdk = 26
        targetSdk = 36
        versionCode = buddyCode
        versionName = buddyVersion
    }

    buildTypes {
        release {
            // Signed with the debug key so the APK installs directly; a store release needs its own key.
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
        // No Java callers pass nulls into this app's Kotlin code, so the generated null checks (and the parameter-name
        // strings they carry) are dead weight in the dex and on every call.
        freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}
