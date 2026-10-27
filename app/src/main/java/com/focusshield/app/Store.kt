package com.focusshield.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Put your own Ko-fi / Buy Me a Coffee / payment page link here.
const val DONATE_URL = "https://ko-fi.com/yourname"
const val DAY_MS = 24L * 60 * 60 * 1000

fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

fun inWindow(start: Int, end: Int, now: Int): Boolean =
    if (start <= end) now in start until end else (now >= start || now < end)

fun fmtTime(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

class Store(context: Context) {
    private val p = context.applicationContext
        .getSharedPreferences("focus_shield", Context.MODE_PRIVATE)

    var locked: Set<String>
        get() = p.getStringSet("locked", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("locked", HashSet(v)).apply() }

    var trading: Set<String>
        get() = p.getStringSet("trading", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("trading", HashSet(v)).apply() }

    var tradeOn: Boolean
        get() = p.getBoolean("tradeOn", false)
        set(v) { p.edit().putBoolean("tradeOn", v).apply() }
    var tradeStart: Int
        get() = p.getInt("tradeStart", 9 * 60 + 15)
        set(v) { p.edit().putInt("tradeStart", v).apply() }
    var tradeEnd: Int
        get() = p.getInt("tradeEnd", 11 * 60)
        set(v) { p.edit().putInt("tradeEnd", v).apply() }

    var sleepOn: Boolean
        get() = p.getBoolean("sleepOn", false)
        set(v) { p.edit().putBoolean("sleepOn", v).apply() }
    var sleepStart: Int
        get() = p.getInt("sleepStart", 22 * 60 + 30)
        set(v) { p.edit().putInt("sleepStart", v).apply() }
    var sleepEnd: Int
        get() = p.getInt("sleepEnd", 6 * 60)
        set(v) { p.edit().putInt("sleepEnd", v).apply() }

    var strictOn: Boolean
        get() = p.getBoolean("strictOn", false)
        set(v) { p.edit().putBoolean("strictOn", v).apply() }
    var disableRequestedAt: Long
        get() = p.getLong("disableReq", 0L)
        set(v) { p.edit().putLong("disableReq", v).apply() }

    fun canEdit(): Boolean {
        if (!strictOn) return true
        val r = disableRequestedAt
        return r > 0 && System.currentTimeMillis() - r >= DAY_MS
    }

    fun until(pkg: String): Long = p.getLong("until_$pkg", 0L)
    fun setUntil(pkg: String, t: Long) { p.edit().putLong("until_$pkg", t).apply() }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun opensToday(): Int =
        if (p.getString("day", "") == today()) p.getInt("opens", 0) else 0

    fun addOpen() {
        val n = opensToday()
        p.edit().putString("day", today()).putInt("opens", n + 1).apply()
    }
}
