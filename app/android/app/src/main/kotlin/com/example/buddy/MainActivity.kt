package com.example.buddy

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // older versions could download a 2.6-3.7 GB on-device model; it is gone now, so free that space
        val app = applicationContext
        val prefs = app.getSharedPreferences("buddy", MODE_PRIVATE)
        if (!prefs.getBoolean("gemma_cleaned", false)) Thread {
            try { File(app.filesDir, "models").deleteRecursively() } catch (_: Exception) { }
            prefs.edit().remove("provider").remove("gemma_model").remove("gemma_backend").putBoolean("gemma_cleaned", true).apply()
        }.start()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "buddy/native")
            .setMethodCallHandler(NativeChannel(this))
    }
}
