package com.luke.reelbrain

import android.content.Context
import java.time.LocalDate
import kotlin.math.max

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

data class Day(val date: String, val ig: Int, val yt: Int, val cap: Int) {
    val total get() = ig + yt
}

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("rb2", Context.MODE_PRIVATE)

    var cap: Int
        get() = p.getInt("cap", 60)
        set(v) { p.edit().putInt("cap", v.coerceAtLeast(1)).apply() }
    var popups: Boolean
        get() = p.getBoolean("popups", true)
        set(v) { p.edit().putBoolean("popups", v).apply() }
    var unlockedDay: String
        get() = p.getString("unlocked", "") ?: ""
        set(v) { p.edit().putString("unlocked", v).apply() }
    var quests: List<String>
        get() = (p.getString("quests", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { p.edit().putString("quests", v.joinToString("\n")).apply() }

    fun ig(): Int { ensureToday(); return p.getInt("ig", 0) }
    fun yt(): Int { ensureToday(); return p.getInt("yt", 0) }
    fun total() = ig() + yt()

    // Damage: +1 per reel, +1.5 if swiped within 10s of the last one, heals 1 per 15 min idle
    fun damage(): Float {
        val last = p.getLong("lastTs", 0L)
        val d = p.getFloat("damage", 0f)
        if (last == 0L) return d
        val mins = (System.currentTimeMillis() - last) / 60000f
        return max(0f, d - mins / 15f)
    }

    fun integrity(): Float {
        ensureToday()
        return (100f * (1f - damage() / cap)).coerceIn(0f, 100f)
    }

    fun isBlocked() = integrity() <= 0f && unlockedDay != LocalDate.now().toString()

    /** returns 70 or 50 if a breathing gate should trigger, else 0 */
    fun addReel(source: Int): Int {
        ensureToday()
        val pre = integrity()
        var g = p.getInt("gate", 100)
        if (pre >= 75f) g = 100 else if (pre >= 55f && g == 50) g = 70
        val now = System.currentTimeMillis()
        val last = p.getLong("lastTs", 0L)
        val fast = last != 0L && now - last < 10_000
        val nd = damage() + if (fast) 1.5f else 1f
        val key = if (source == 0) "ig" else "yt"
        p.edit().putInt(key, p.getInt(key, 0) + 1).putFloat("damage", nd).putLong("lastTs", now).apply()
        val post = integrity()
        var trig = 0
        if (post < 50f && g > 50) { trig = 50; g = 50 }
        else if (post < 70f && g > 70) { trig = 70; g = 70 }
        p.edit().putInt("gate", g).apply()
        return trig
    }

    private fun hist(): MutableList<Day> =
        (p.getString("hist", "") ?: "").split(";").filter { it.isNotBlank() }.mapNotNull {
            val a = it.split(",")
            if (a.size < 4) null else Day(a[0], a[1].toInt(), a[2].toInt(), a[3].toInt())
        }.toMutableList()

    private fun saveHist(l: List<Day>) {
        p.edit().putString("hist", l.takeLast(400).joinToString(";") { "${it.date},${it.ig},${it.yt},${it.cap}" }).apply()
    }

    fun ensureToday() {
        val t = LocalDate.now()
        val last = p.getString("day", null)?.let { LocalDate.parse(it) }
        if (last == null) {
            p.edit().putString("day", t.toString()).putString("first", t.toString()).apply()
            return
        }
        if (last == t) return
        val h = hist()
        h.add(Day(last.toString(), p.getInt("ig", 0), p.getInt("yt", 0), cap))
        var d = last.plusDays(1)
        var n = 0
        while (d.isBefore(t) && n < 400) { h.add(Day(d.toString(), 0, 0, cap)); d = d.plusDays(1); n++ }
        p.edit().putInt("ig", 0).putInt("yt", 0).putString("day", t.toString())
            .putFloat("damage", 0f).putLong("lastTs", 0L).putInt("gate", 100).apply()
        saveHist(h)
    }

    fun firstDate(): LocalDate = LocalDate.parse(p.getString("first", null) ?: LocalDate.now().toString())

    fun allDays(): List<Day> {
        ensureToday()
        return hist() + Day(LocalDate.now().toString(), ig(), yt(), cap)
    }

    fun streak(): Int {
        var n = 0
        for (d in allDays().asReversed()) { if (d.total <= d.cap) n++ else break }
        return n
    }

    fun bestStreak(): Int {
        var best = 0
        var run = 0
        for (d in allDays()) { if (d.total <= d.cap) { run++; best = max(best, run) } else run = 0 }
        return best
    }

    fun dbg(line: String) {
        val old = (p.getString("dbg", "") ?: "").split("\n").filter { it.isNotBlank() }
        p.edit().putString("dbg", (listOf(line) + old).take(6).joinToString("\n")).apply()
    }
    fun dbgText(): String = p.getString("dbg", "") ?: ""
}
