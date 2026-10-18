package com.luke.reelbrain

import android.content.Context
import java.time.LocalDate
import kotlin.math.max

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

data class Day(val date: String, val ig: Int, val yt: Int) {
    val total get() = ig + yt
}

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("rb", Context.MODE_PRIVATE)

    var cap: Int
        get() = p.getInt("cap", 100)
        set(v) { p.edit().putInt("cap", v).apply() }
    var capEnabled: Boolean
        get() = p.getBoolean("capOn", false)
        set(v) { p.edit().putBoolean("capOn", v).apply() }
    var popups: Boolean
        get() = p.getBoolean("popups", true)
        set(v) { p.edit().putBoolean("popups", v).apply() }
    var unlockedDay: String
        get() = p.getString("unlocked", "") ?: ""
        set(v) { p.edit().putString("unlocked", v).apply() }

    fun ig(): Int { ensureToday(); return p.getInt("ig", 0) }
    fun yt(): Int { ensureToday(); return p.getInt("yt", 0) }
    fun total() = ig() + yt()
    fun streak(): Int { ensureToday(); return p.getInt("streak", 0) }
    fun isBlocked() = capEnabled && total() >= cap && unlockedDay != LocalDate.now().toString()

    // Damage: +1 per reel, +1.5 if swiped within 10s of the last one, heals 1 per 10 min idle
    fun damage(): Float {
        val last = p.getLong("lastTs", 0L)
        val d = p.getFloat("damage", 0f)
        if (last == 0L) return d
        val mins = (System.currentTimeMillis() - last) / 60000f
        return max(0f, d - mins / 10f)
    }
    fun severity() = (damage() / 80f).coerceIn(0f, 1f)
    fun stage(sev: Float = severity()) = if (sev < 0.35f) 0 else if (sev < 0.75f) 1 else 2

    /** returns true if the brain stage got worse */
    fun addReel(source: Int): Boolean {
        ensureToday()
        val before = stage()
        val now = System.currentTimeMillis()
        val last = p.getLong("lastTs", 0L)
        val fast = last != 0L && now - last < 10_000
        val nd = (damage() + if (fast) 1.5f else 1f).coerceAtMost(100f)
        val key = if (source == 0) "ig" else "yt"
        p.edit().putInt(key, p.getInt(key, 0) + 1).putFloat("damage", nd).putLong("lastTs", now).apply()
        return stage() > before
    }

    private fun hist(): MutableList<Day> =
        (p.getString("hist", "") ?: "").split(";").filter { it.isNotBlank() }.map {
            val a = it.split(","); Day(a[0], a[1].toInt(), a[2].toInt())
        }.toMutableList()

    private fun saveHist(l: List<Day>) {
        p.edit().putString("hist", l.takeLast(14).joinToString(";") { "${it.date},${it.ig},${it.yt}" }).apply()
    }

    fun ensureToday() {
        val t = LocalDate.now()
        val last = p.getString("day", null)?.let { LocalDate.parse(it) }
        if (last == null) { p.edit().putString("day", t.toString()).apply(); return }
        if (last == t) return
        val h = hist()
        var streak = p.getInt("streak", 0)
        val done = Day(last.toString(), p.getInt("ig", 0), p.getInt("yt", 0))
        h.add(done)
        streak = if (done.total <= cap) streak + 1 else 0
        var d = last.plusDays(1)
        var n = 0
        while (d.isBefore(t) && n < 14) { h.add(Day(d.toString(), 0, 0)); streak++; d = d.plusDays(1); n++ }
        p.edit().putInt("ig", 0).putInt("yt", 0).putString("day", t.toString()).putInt("streak", streak).apply()
        saveHist(h)
    }

    fun week(): List<Day> {
        ensureToday()
        return hist().takeLast(6) + Day(LocalDate.now().toString(), p.getInt("ig", 0), p.getInt("yt", 0))
    }

    // tiny local debug trail so you don't need logcat
    fun dbg(line: String) {
        val old = (p.getString("dbg", "") ?: "").split("\n").filter { it.isNotBlank() }
        p.edit().putString("dbg", (listOf(line) + old).take(6).joinToString("\n")).apply()
    }
    fun dbgText(): String = p.getString("dbg", "") ?: ""
}
