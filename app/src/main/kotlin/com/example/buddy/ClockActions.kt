package com.example.buddy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import org.json.JSONObject

object ClockActions {
    /** Set by the service: called later (main thread not guaranteed) with null when a background step worked, or the problem text. */
    @Volatile var report: ((String?) -> Unit)? = null

    private fun s(a: JSONObject, k: String): String = if (a.isNull(k)) "" else a.optString(k, "").trim()

    fun exec(ctx: Context, a: JSONObject): String? {
        fun tr(ru: String, en: String, uz: String) = Tr.t(ctx, ru, en, uz)
        val noApp = tr("Нет приложения часов", "No clock app found", "Soat ilovasi topilmadi")
        val denied = tr("Нет доступа к часам", "Clock access denied", "Soatga ruxsat yo'q")

        fun start(i: Intent): String? {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                ctx.startActivity(i); null
            } catch (_: ActivityNotFoundException) {
                noApp
            } catch (_: SecurityException) {
                denied
            } catch (_: Exception) {
                tr("Не получилось выполнить", "Could not do that", "Bajarib bo'lmadi")
            }
        }

        val label = s(a, "label")
        if (label.length > 60) return tr("Слишком длинное название", "Label is too long", "Nom juda uzun")

        return when (s(a, "do").ifEmpty { s(a, "action") }) {
            "alarm" -> {
                val h = a.optInt("hour", -1); val m = a.optInt("minute", 0)
                if (h !in 0..23 || m !in 0..59) return tr("Неверное время будильника", "Invalid alarm time", "Budilnik vaqti noto'g'ri")
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h)
                    .putExtra(AlarmClock.EXTRA_MINUTES, m)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                val arr = a.optJSONArray("days")
                if (arr != null && arr.length() > 0) {
                    val days = ArrayList<Int>()
                    for (k in 0 until arr.length()) {
                        val d = arr.optInt(k, 0)
                        if (d in 1..7) {
                            val c = if (d == 7) java.util.Calendar.SUNDAY else d + 1
                            if (c !in days) days.add(c)
                        }
                    }
                    if (days.isNotEmpty()) i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days)
                }
                if (a.has("vibrate") && !a.isNull("vibrate")) i.putExtra(AlarmClock.EXTRA_VIBRATE, a.optBoolean("vibrate", true))
                start(i)
            }
            "alarm_list" -> start(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            "alarm_cancel" -> {
                // Android has no way to switch off another app's saved alarms, and ACTION_DISMISS_ALARM only silences one that is
                // ringing now: it used to "succeed" without doing anything. With screen control on, open the alarm list and
                // switch the alarm off there, then check that it is really off; without it, say so honestly.
                if (BuddyAccess.enabled) {
                    val h = if (a.has("hour") && !a.isNull("hour")) a.optInt("hour", -1) else -1
                    val m = a.optInt("minute", 0)
                    if (h != -1 && (h !in 0..23 || m !in 0..59)) return tr("Неверное время будильника", "Invalid alarm time", "Budilnik vaqti noto'g'ri")
                    start(Intent(AlarmClock.ACTION_SHOW_ALARMS))?.let { return it }
                    Thread {
                        var res = -1
                        // the clock app needs a moment to open and draw its list
                        for (k in 0 until 6) {
                            Thread.sleep(if (k == 0) 1400L else 800L)
                            res = BuddyAccess.disableAlarm(h, m)
                            if (res == 1) break
                        }
                        val msg = when (res) {
                            1 -> null
                            0 -> tr("Не нашёл включённый будильник на это время", "I could not find an alarm that is on at that time", "Bu vaqtda yoqilgan budilnik topilmadi")
                            else -> tr("Не смог выключить будильник: приложение часов не дало прочитать список", "I could not switch the alarm off: the clock app list could not be read", "Budilnikni o'chira olmadim: soat ilovasi ro'yxatini o'qib bo'lmadi")
                        }
                        report?.invoke(msg)
                    }.start()
                    return null
                }
                return tr("Чтобы выключать будильники, включите «Управление экраном голосом» в разрешениях. Я открыл часы: выключите вручную",
                    "To switch alarms off, turn on “Control the screen by voice” in Permissions. I opened the clock: switch it off yourself",
                    "Budilniklarni o'chirish uchun Ruxsatlarda «Ekranni ovoz bilan boshqarish»ni yoqing. Soatni ochdim: o'zingiz o'chiring").also { start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            }
            "timer" -> {
                val sec = a.optInt("seconds", 0)
                if (sec !in 1..86400) return tr("Неверное время таймера", "Invalid timer time", "Taymer vaqti noto'g'ri")
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (label.isNotEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
                start(i)
            }
            "timer_cancel" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) start(Intent(AlarmClock.ACTION_DISMISS_TIMER)) else noApp
            "timer_list" -> start(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            else -> tr("Неизвестное действие", "Unknown action", "Noma'lum amal")
        }
    }
}
