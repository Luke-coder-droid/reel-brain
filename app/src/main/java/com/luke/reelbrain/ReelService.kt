package com.luke.reelbrain

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ReelService : AccessibilityService() {
    companion object {
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        val IG_IDS = listOf("com.instagram.android:id/clips_viewer_view_pager")
        val LEAVE = setOf("home", "subscriptions", "you", "library", "search", "explore", "trending", "create", "notifications")
        const val CYCLE = 12_000L
        const val BREATHS = 10
    }

    private val store by lazy { Store(this) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())

    private var pill: LinearLayout? = null
    private var counter: TextView? = null
    private var popupView: TextView? = null
    private var pillBrain: BrainView? = null
    private var block: FrameLayout? = null
    private var breathe: FrameLayout? = null
    private var bv: BreathView? = null
    private var lastPkg = IG
    private var igOn = false
    private var ytOn = false
    private var lastCount = 0L
    private var lastVis = 0L
    private var lastLog = 0L
    private var lastIdx = -1
    private var ytIdx = -1
    private var ytEnterAt = 0L
    private var ytBaselineUntil = 0L
    private var ytTapEntry = false
    private var ytGone = 0
    private var commentsUntil = 0L
    private var misses = 0
    private var breathStart = 0L

    private val hidePopup = Runnable { popupView?.visibility = View.GONE }
    private val autoLeave = Runnable { if (block != null) leaveBlock() }
    private val poll = object : Runnable {
        override fun run() {
            val pkg = rootInActiveWindow?.packageName?.toString()
            if (pkg == IG || pkg == YT) misses = 0 else misses++
            if (misses >= 2) { hideAll(); return }
            if (pkg == YT && ytOn && SystemClock.elapsedRealtime() - ytEnterAt > 3000) {
                if (reelNodeVisible()) ytGone = 0
                else if (++ytGone >= 2) { leaveShorts("no reel view on screen"); hidePill(); return }
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val v = bv ?: return
            val el = SystemClock.elapsedRealtime() - breathStart
            if (el >= CYCLE * BREATHS) { showChoices(); return }
            val n = (el / CYCLE).toInt() + 1
            val t = el % CYCLE
            v.sub = "Breath $n of $BREATHS"
            when {
                t < 4000 -> { v.label = "Breathe in"; v.scale = 0.45f + 0.55f * (t / 4000f) }
                t < 6000 -> { v.label = "Hold"; v.scale = 1f }
                else -> { v.label = "Breathe out"; v.scale = 1f - 0.55f * ((t - 6000) / 6000f) }
            }
            handler.postDelayed(this, 40)
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg != IG && pkg != YT) return
        if (breathe != null) return
        lastPkg = pkg
        val now = SystemClock.elapsedRealtime()
        val counted = if (pkg == IG) handleIg(e, now) else handleYt(e, now)
        syncPill()
        if (counted) registerReel(if (pkg == IG) 0 else 1, now)
    }

    private fun syncPill() {
        val on = if (lastPkg == IG) igOn else ytOn
        if (on) showPill() else hidePill()
    }

    private fun registerReel(src: Int, now: Long) {
        lastCount = now
        val trig = store.addReel(src)
        refreshPill()
        val t = store.total()
        store.dbg("${if (src == 0) "IG" else "YT"} counted -> $t")
        when {
            trig != 0 -> { performGlobalAction(GLOBAL_ACTION_HOME); showBreathe() }
            store.isBlocked() -> showBlock()
            t % 25 == 0 -> showPopup("$t reels today · mind integrity ${store.integrity().toInt()}%")
        }
    }

    // ---------- Instagram: count when the reel pager moves ----------
    private fun handleIg(e: AccessibilityEvent, now: Long): Boolean {
        val scroll = e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val gap = if (scroll && !igOn) 300L else 1000L
        if (now - lastVis >= gap) {
            lastVis = now
            igOn = inReels()
            if (!igOn) lastIdx = -1
            if (igOn && store.isBlocked()) { showBlock(); return false }
        }
        if (!scroll || !igOn) return false

        val id = e.source?.viewIdResourceName ?: "none"
        if (now < commentsUntil || id.contains("comment", true)) return false
        if (store.isBlocked()) { showBlock(); return false }
        if (now - lastCount < 900) return false
        val idx = e.fromIndex
        if (idx >= 0 && idx == lastIdx) return false
        if (commentsOpen()) { commentsUntil = now + 2500; store.dbg("IG skipped: comments"); return false }
        if (idx >= 0) lastIdx = idx
        return true
    }

    private fun inReels(): Boolean {
        val root = rootInActiveWindow ?: return false
        return IG_IDS.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
    }

    // ---------- YouTube: explicit enter/exit + full-screen single-page swipes ----------
    private fun handleYt(e: AccessibilityEvent, now: Long): Boolean {
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { ytClick(e, now); return false }
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) return ytScroll(e, now)
        return false
    }

    private fun labelsOf(e: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        var n: AccessibilityNodeInfo? = e.source
        var d = 0
        while (n != null && d < 5) {
            n.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it.trim()) }
            n.text?.toString()?.let { if (it.isNotBlank()) out.add(it.trim()) }
            n = n.parent
            d++
        }
        e.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it.trim()) }
        return out
    }

    private fun ytClick(e: AccessibilityEvent, now: Long) {
        val labels = labelsOf(e)
        if (labels.isEmpty()) return
        val shorts = labels.firstOrNull { it.equals("Shorts", true) || it.contains("play short", true) }
        if (shorts != null) {
            if (!ytOn) enterShorts(now, "tap '${shorts.take(20)}'", true)
            return
        }
        val leave = labels.firstOrNull { it.lowercase() in LEAVE || it.contains("play video", true) }
        if (leave != null && ytOn) leaveShorts("tap '${leave.take(20)}'")
    }

    private fun enterShorts(now: Long, why: String, tap: Boolean) {
        ytOn = true
        ytIdx = -1
        ytEnterAt = now
        ytTapEntry = tap
        ytBaselineUntil = if (tap) now + 1500 else 0L
        ytGone = 0
        store.dbg("YT enter: $why")
    }

    private fun leaveShorts(why: String) {
        if (ytOn) store.dbg("YT exit: $why")
        ytOn = false
        ytIdx = -1
        ytGone = 0
    }

    private fun logIgnored(now: Long, s: String) {
        if (now - lastLog > 1500) { lastLog = now; store.dbg("YT ignored: $s") }
    }

    private fun ytScroll(e: AccessibilityEvent, now: Long): Boolean {
        val src = e.source ?: return false
        val dm = resources.displayMetrics
        val r = Rect()
        src.getBoundsInScreen(r)
        val from = e.fromIndex
        val span = if (from >= 0 && e.toIndex >= 0) e.toIndex - from else -1
        val cls = e.className?.toString()?.substringAfterLast('.') ?: "?"
        val full = r.width() >= dm.widthPixels * 0.9f && r.height() >= dm.heightPixels * 0.75f
        if (!full) {
            if (ytOn) logIgnored(now, "small $cls ${r.width()}x${r.height()}")
            return false
        }
        if (span >= 2) {
            if (ytOn) leaveShorts("list scroll $cls span=$span")
            return false
        }
        if (!ytOn) enterShorts(now, "swipe $cls", false)
        if (store.isBlocked()) { showBlock(); return false }
        if (now < commentsUntil) return false
        if (from >= 0) {
            if (from == ytIdx) return false
            if (ytIdx == -1 && (now < ytBaselineUntil || !ytTapEntry)) { ytIdx = from; return false }
            if (commentsOpen()) { commentsUntil = now + 2500; store.dbg("YT skipped: comments"); return false }
            ytIdx = from
            return now - lastCount >= 600
        }
        if (now - ytEnterAt < 1500 || now - lastCount < 1000) return false
        if (commentsOpen()) { commentsUntil = now + 2500; store.dbg("YT skipped: comments"); return false }
        return true
    }

    private fun reelNodeVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        var found = false
        var nodes = 0
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (found || node == null || depth > 25 || nodes > 400) return
            nodes++
            if (!node.isVisibleToUser) return
            val id = node.viewIdResourceName
            if (id != null && id.contains("reel", true)) { found = true; return }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    /** The comment sheet has a text box; reels/shorts themselves don't. */
    private fun commentsOpen(): Boolean {
        val root = rootInActiveWindow ?: return false
        var found = false
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (found || node == null || depth > 25) return
            if (node.isVisibleToUser && (node.isEditable || node.className?.toString()?.endsWith("EditText") == true)) {
                found = true
                return
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    // ---------- small counter pill ----------
    private fun pillBg() = GradientDrawable().apply {
        setColor(Color.parseColor("#CC0B0F1A")); cornerRadius = dp(20).toFloat()
        setStroke(dp(1), Color.parseColor("#55A8DFFF"))
    }

    private fun showPill() {
        if (pill != null) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(14), dp(4))
            background = pillBg()
        }
        val pb = BrainView(this, false)
        pillBrain = pb
        row.addView(pb, LinearLayout.LayoutParams(dp(34), dp(34)))
        val ct = TextView(this).apply {
            setTextColor(Color.parseColor("#9FD8FF")); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, 0, 0)
        }
        counter = ct
        row.addView(ct)
        val pv = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#9FD8FF")); textSize = 12f
            setPadding(dp(12), dp(6), dp(12), dp(6)); background = pillBg()
        }
        popupView = pv
        box.addView(row)
        box.addView(pv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(36) }
        wm.addView(box, lp)
        pill = box
        misses = 0
        handler.postDelayed(poll, 1000)
        refreshPill()
    }

    private fun hidePill() {
        handler.removeCallbacks(poll); handler.removeCallbacks(hidePopup)
        pill?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        pill = null
    }

    private fun refreshPill() {
        counter?.text = "IG ${store.ig()}  YT ${store.yt()}  •  ${store.total()}/${store.cap}"
        pillBrain?.integrity = store.integrity()
    }

    private fun showPopup(msg: String) {
        if (!store.popups) return
        val t = popupView ?: return
        t.text = msg
        t.visibility = View.VISIBLE
        handler.removeCallbacks(hidePopup)
        handler.postDelayed(hidePopup, 3500)
    }

    // ---------- shared screen pieces ----------
    private fun fullParams() = WindowManager.LayoutParams(
        MATCH_PARENT, MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private fun hobbyRow(q: String, bg: String = "#1F4A47", onTap: () -> Unit) = TextView(this).apply {
        text = q; textSize = 18f; gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#E6FFF5"))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bg)); cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onTap() }
    }

    private fun addHobbies(col: LinearLayout, onTap: () -> Unit) {
        val quests = store.quests.take(6)
        if (quests.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Add hobbies in the Reel Brain app and they'll show up here."
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#6FA99A"))
            })
            return
        }
        for (q in quests) {
            col.addView(hobbyRow(q) { onTap() }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }
    }

    private fun peaceText(t: String, size: Float, color: String, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; gravity = Gravity.CENTER
        setTextColor(Color.parseColor(color)); setPadding(0, dp(top), 0, 0)
    }

    private fun peaceColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(72), dp(24), dp(24))
    }

    // ---------- limit reached screen (hobbies + go home only) ----------
    private fun showBlock() {
        if (block != null) return
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F5071B22")) }
        val col = peaceColumn()
        col.addView(peaceText("Limit reached", 34f, "#BFF3E3"))
        col.addView(peaceText("You've hit ${store.cap} reels today. Reels stay locked until midnight.", 15f, "#8FCDBD", 8))
        col.addView(BrainView(this).apply { integrity = 0f }, LinearLayout.LayoutParams(dp(200), dp(200)).apply { topMargin = dp(16) })
        col.addView(peaceText("Do one of your hobbies instead", 14f, "#8FCDBD", 8))
        addHobbies(col) { leaveBlock() }
        col.addView(hobbyRow("Go home") { leaveBlock() }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wm.addView(root, fullParams())
        block = root
        handler.postDelayed(autoLeave, 20_000)
    }

    private fun leaveBlock() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        hideBlock()
    }

    private fun hideBlock() {
        handler.removeCallbacks(autoLeave)
        block?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        block = null
    }

    private fun hideAll() {
        hidePill()
        hideBlock()
        igOn = false
        ytOn = false
        lastIdx = -1
        ytIdx = -1
        ytGone = 0
    }

    // ---------- breathing gate ----------
    private fun showBreathe() {
        if (breathe != null) return
        hideAll()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F5071B22")) }
        val col = peaceColumn()
        col.addView(peaceText("Breathe", 36f, "#BFF3E3"))
        col.addView(peaceText("Mind integrity is at ${store.integrity().toInt()}%. Ten slow breaths first.", 15f, "#8FCDBD", 8))
        val v = BreathView(this)
        bv = v
        col.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, dp(320)).apply { topMargin = dp(24) })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wm.addView(root, fullParams())
        breathe = root
        breathStart = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    private fun showChoices() {
        val root = breathe ?: return
        handler.removeCallbacks(tick)
        bv = null
        root.removeAllViews()
        val appName = if (lastPkg == YT) "YouTube" else "Instagram"
        val pk = lastPkg
        val col = peaceColumn()
        col.addView(peaceText("Well done", 34f, "#BFF3E3"))
        col.addView(peaceText("Ten breaths done. What's next?", 15f, "#8FCDBD", 8))
        col.addView(hobbyRow("Continue with $appName", "#2F7F73") { launchApp(pk); hideBreathe() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(28) })
        col.addView(peaceText("or do one of your hobbies", 14f, "#8FCDBD", 22))
        addHobbies(col) { hideBreathe() }
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun launchApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun hideBreathe() {
        handler.removeCallbacks(tick)
        breathe?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        breathe = null
        bv = null
    }

    override fun onInterrupt() {}
    override fun onDestroy() { hideAll(); hideBreathe(); super.onDestroy() }
}
