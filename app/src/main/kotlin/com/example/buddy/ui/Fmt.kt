package com.example.buddy.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import com.example.buddy.Tr
import java.util.Calendar

fun two(n: Int) = n.toString().padStart(2, '0')

private val MONTHS_RU = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val MONTHS_UZ = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")
private val DAYS_RU = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
private val DAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val DAYS_UZ = listOf("dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba")

fun monthName(ctx: Context, month1: Int): String = when (Tr.lang(ctx)) { "en" -> MONTHS_EN; "uz" -> MONTHS_UZ; else -> MONTHS_RU }[month1 - 1]

/** [dow1] = Calendar.DAY_OF_WEEK-style number turned into Monday = 1 .. Sunday = 7. */
fun weekdayName(ctx: Context, mon1: Int): String = when (Tr.lang(ctx)) { "en" -> DAYS_EN; "uz" -> DAYS_UZ; else -> DAYS_RU }[mon1 - 1]

fun weekdaysShort(ctx: Context): List<String> = when (Tr.lang(ctx)) {
    "en" -> listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    "uz" -> listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")
    else -> listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
}

/** Monday = 1 .. Sunday = 7 from a Calendar. */
fun Calendar.mondayDow(): Int = ((get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

fun fmtTime(c: Calendar) = "${two(c.get(Calendar.HOUR_OF_DAY))}:${two(c.get(Calendar.MINUTE))}"
fun fmtDate(c: Calendar) = "${two(c.get(Calendar.DAY_OF_MONTH))}.${two(c.get(Calendar.MONTH) + 1)}.${c.get(Calendar.YEAR)}"
fun fmtDateTime(millis: Long): String = Calendar.getInstance().apply { timeInMillis = millis }.let { "${fmtDate(it)} ${fmtTime(it)}" }

fun cal(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis }

private fun dayDiff(millis: Long): Int {
    fun midnight(m: Long) = Calendar.getInstance().apply {
        timeInMillis = m; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.round((midnight(millis) - midnight(System.currentTimeMillis())) / 86_400_000.0).toInt()
}

/** Short human date for due chips: "Today, 18:00", "Tomorrow, 09:30", "05.11, 12:00". */
fun fmtDue(ctx: Context, millis: Long): String {
    val c = cal(millis)
    val time = fmtTime(c)
    return when (dayDiff(millis)) {
        0 -> Tr.t(ctx, "Сегодня, $time", "Today, $time", "Bugun, $time")
        1 -> Tr.t(ctx, "Завтра, $time", "Tomorrow, $time", "Ertaga, $time")
        -1 -> Tr.t(ctx, "Вчера, $time", "Yesterday, $time", "Kecha, $time")
        else -> "${two(c.get(Calendar.DAY_OF_MONTH))}.${two(c.get(Calendar.MONTH) + 1)}, $time"
    }
}

fun dayMonth(ctx: Context, c: Calendar): String {
    val m = monthName(ctx, c.get(Calendar.MONTH) + 1)
    return if (Tr.lang(ctx) == "uz") "${c.get(Calendar.DAY_OF_MONTH)}-$m" else "${c.get(Calendar.DAY_OF_MONTH)} $m"
}

fun fmtDayHeader(ctx: Context, millis: Long): String {
    val c = cal(millis)
    val wd = weekdayName(ctx, c.mondayDow()).let { if (Tr.lang(ctx) == "en") it else it.replaceFirstChar { ch -> ch.uppercase() } }
    val base = "${dayMonth(ctx, c)}, $wd"
    return when (dayDiff(millis)) {
        0 -> Tr.t(ctx, "Сегодня, $base", "Today, $base", "Bugun, $base")
        1 -> Tr.t(ctx, "Завтра, $base", "Tomorrow, $base", "Ertaga, $base")
        else -> base
    }
}

/** Date picker followed by a time picker; calls [done] with the chosen moment. */
fun Context.pickDateTime(initial: Long, done: (Long) -> Unit) {
    val c = cal(initial)
    val theme = if (P.dark) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog
    DatePickerDialog(this, theme, { _, y, m, d ->
        TimePickerDialog(this, theme, { _, hh, mm ->
            done(Calendar.getInstance().apply { set(y, m, d, hh, mm, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}
