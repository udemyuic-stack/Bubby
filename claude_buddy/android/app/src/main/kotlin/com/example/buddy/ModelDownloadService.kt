package com.example.buddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadService : Service() {

    companion object {
        @Volatile var cancelRequested = false
        @Volatile var activeId: String? = null
        private const val CHANNEL = "model_download"
        private const val NOTIF_ID = 4711
        private const val MAX_ATTEMPTS = 6
        @Suppress("DEPRECATION") private const val WIFI_FULL = WifiManager.WIFI_MODE_FULL_HIGH_PERF
    }

    private class FatalError(msg: String) : Exception(msg)

    private var worker: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ModelStore.ACTION_CANCEL) {
            cancelRequested = true
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(ModelStore.EXTRA_ID)
        if (id == null || id !in ModelStore.IDS) {
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }
        showForeground(buildNotification(Tr.t(this, "Gemma: подготовка…", "Gemma: preparing…", "Gemma: tayyorlanmoqda…"), 0, 0, true))
        if (worker?.isAlive == true) return START_NOT_STICKY
        cancelRequested = false
        activeId = id
        acquireWifi()
        val t = Thread { runDownload(id) }
        worker = t
        t.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWifi()
        activeId = null
        super.onDestroy()
    }

    private fun showForeground(n: Notification) {
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, Tr.t(this, "Загрузка модели", "Model download", "Modelni yuklash"), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String, done: Long, total: Long, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, ModelDownloadService::class.java).setAction(ModelStore.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(Tr.t(this, "Загрузка модели Gemma", "Downloading Gemma model", "Gemma modeli yuklanmoqda"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, Tr.t(this, "Отмена", "Cancel", "Bekor qilish"), cancel).build())
        if (total > 0 && !indeterminate) {
            b.setProgress(1000, (done * 1000 / total).toInt().coerceIn(0, 1000), false)
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun notify(n: Notification) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
        } catch (e: Exception) {
        }
    }

    private fun acquireWifi() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WIFI_FULL
            val l = wm.createWifiLock(mode, "buddy:model")
            l.setReferenceCounted(false)
            l.acquire()
            wifiLock = l
        } catch (e: Exception) {
        }
    }

    private fun releaseWifi() {
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        wifiLock = null
    }

    private fun finish() {
        activeId = null
        releaseWifi()
        stopForeground(true)
        stopSelf()
    }

    private fun runDownload(id: String) {
        val total = ModelStore.expectedSize(id)
        val out = ModelStore.file(this, id)
        val part = ModelStore.partFile(this, id)
        try {
            if (out.exists() && out.length() == total) {
                ModelStore.setState(id, "ready", total, total)
                return
            }
            out.delete()
            val have = if (part.exists()) part.length() else 0L
            val free = try {
                StatFs(filesDir.absolutePath).availableBytes
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
            val need = total - have + 150L * 1024 * 1024
            if (free < need) {
                val gb = need / 1073741824.0
                throw FatalError(
                    Tr.t(this, "Недостаточно места: нужно ещё около ", "Not enough space: need about ", "Joy yetarli emas: yana taxminan ") + String.format("%.1f", gb) + Tr.t(this, " ГБ свободной памяти.", " GB more free storage.", " GB bo'sh xotira kerak.")
                )
            }
            ModelStore.setState(id, "downloading", have, total)
            var attempt = 0
            while (true) {
                if (cancelRequested) {
                    ModelStore.setState(id, "none", if (part.exists()) part.length() else 0L, total)
                    return
                }
                try {
                    transfer(id, part, total)
                    if (cancelRequested) {
                        ModelStore.setState(id, "none", if (part.exists()) part.length() else 0L, total)
                        return
                    }
                    break
                } catch (e: FatalError) {
                    throw e
                } catch (e: IOException) {
                    if (cancelRequested) continue
                    attempt++
                    val cur = if (part.exists()) part.length() else 0L
                    if (attempt >= MAX_ATTEMPTS) {
                        ModelStore.setState(id, "paused", cur, total, Tr.t(this, "Нет соединения. Загрузка приостановлена.", "No connection. Download paused.", "Aloqa yo'q. Yuklash to'xtatildi."))
                        notify(buildNotification(Tr.t(this, "Приостановлено: нет сети", "Paused: no network", "To'xtatildi: tarmoq yo'q"), cur, total, false))
                        return
                    }
                    ModelStore.setState(id, "paused", cur, total, Tr.t(this, "Нет соединения, повтор…", "No connection, retrying…", "Aloqa yo'q, qayta urinilmoqda…"))
                    var waited = 0L
                    val delayMs = minOf(30000L, 2000L shl (attempt - 1))
                    while (waited < delayMs && !cancelRequested) {
                        Thread.sleep(250)
                        waited += 250
                    }
                    ModelStore.setState(id, "downloading", cur, total)
                }
            }
            if (part.length() != total) {
                part.delete()
                throw FatalError(Tr.t(this, "Файл модели повреждён (неверный размер). Попробуйте скачать заново.", "The model file is corrupted (wrong size). Try downloading again.", "Model fayli buzilgan (hajmi noto'g'ri). Qayta yuklab ko'ring."))
            }
            if (!part.renameTo(out)) {
                throw FatalError(Tr.t(this, "Не удалось сохранить файл модели.", "Could not save the model file.", "Model faylini saqlab bo'lmadi."))
            }
            ModelStore.setState(id, "ready", total, total)
        } catch (e: FatalError) {
            ModelStore.setState(id, "error", if (part.exists()) part.length() else 0L, total, e.message)
        } catch (e: InterruptedException) {
            ModelStore.setState(id, "paused", if (part.exists()) part.length() else 0L, total, Tr.t(this, "Загрузка прервана.", "Download interrupted.", "Yuklash to'xtatildi."))
        } catch (e: Exception) {
            ModelStore.setState(
                id, "error", if (part.exists()) part.length() else 0L, total,
                Tr.t(this, "Ошибка загрузки: ", "Download error: ", "Yuklash xatosi: ") + (e.message ?: e.javaClass.simpleName)
            )
        } finally {
            finish()
        }
    }

    private fun open(start: String, offset: Long): HttpURLConnection {
        var url = start
        for (hop in 0 until 8) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "ClaudeBuddy/1.0")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) c.setRequestProperty("Range", "bytes=$offset-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("redirect without location")
                url = URL(URL(url), loc).toString()
                continue
            }
            return c
        }
        throw IOException("too many redirects")
    }

    private fun transfer(id: String, part: File, total: Long) {
        var offset = if (part.exists()) part.length() else 0L
        if (offset > total) {
            part.delete()
            offset = 0L
        }
        if (offset == total) return
        val c = open(ModelStore.url(id), offset)
        try {
            val code = c.responseCode
            if (code == 416) {
                part.delete()
                throw IOException("range not satisfiable")
            }
            if (code == 401 || code == 403 || code == 404) {
                throw FatalError(Tr.t(this, "Сервер отказал в доступе к модели (код $code).", "The server denied access to the model (code $code).", "Server modelga kirishni rad etdi (kod $code)."))
            }
            if (code != 200 && code != 206) throw IOException("HTTP $code")
            val append = code == 206 && offset > 0
            if (!append) offset = 0L
            val raf = RandomAccessFile(part, "rw")
            try {
                if (append) raf.seek(offset) else raf.setLength(0)
                c.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var done = offset
                    while (true) {
                        if (cancelRequested) {
                            raf.close()
                            ModelStore.setState(id, "none", done, total)
                            return
                        }
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 1000) {
                            lastNotify = now
                            ModelStore.setState(id, "downloading", done, total)
                            val mb = done / 1048576
                            val tmb = total / 1048576
                            notify(buildNotification(Tr.t(this, "$mb из $tmb МБ", "$mb of $tmb MB", "$mb / $tmb MB"), done, total, false))
                        }
                    }
                    if (done < total) throw IOException("connection closed early")
                }
            } catch (e: IOException) {
                if (e.message?.contains("No space", true) == true) {
                    throw FatalError(Tr.t(this, "Недостаточно места на устройстве.", "Not enough storage on the device.", "Qurilmada joy yetarli emas."))
                }
                throw e
            } finally {
                try {
                    raf.close()
                } catch (e: Exception) {
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
