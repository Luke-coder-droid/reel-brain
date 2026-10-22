package com.luke.reelbrain

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ReelService : AccessibilityService() {
    companion object {
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        val MARKERS = listOf("clips_viewer", "reel_recycler", "reel_player")
        val IG_IDS = listOf("com.instagram.android:id/clips_viewer_view_pager")
        const val CYCLE = 12_000L
        const val BREATHS = 10
    }

    private class Scan(val sig: String?, val reel: Boolean)

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
    private var lastCount = 0L
    private var lastCheck = 0L
    private var lastSig: String? = null
    private var misses = 0
    private var breathStart = 0L
    private var seenReelIds = false

    private val hidePopup = Runnable { popupView?.visibility = View.GONE }
    private val goHome = Runnable { performGlobalAction(GLOBAL_ACTION_HOME) }
    private val poll = object : Runnable {
        override fun run() {
            val pkg = rootInActiveWindow?.packageName?.toString()
            if (pkg == IG || pkg == YT) misses = 0 else misses++
            if (misses >= 2) { hideAll(); return }
            handler.postDelayed(this, 1000)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val v = bv ?: return
            val el = SystemClock.elapsedRealtime() - breathStart
            if (el >= CYCLE * BREATHS) { hideBreathe(); return }
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
        val src = when (pkg) { IG -> 0; YT -> 1; else -> return }
        if (breathe != null) return
        showPill()
        val now = SystemClock.elapsedRealtime()
        var newReel = false

        if (src == 0) {
            if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val id = e.source?.viewIdResourceName ?: "none"
                store.dbg("IG ${e.className?.toString()?.substringAfterLast('.')} $id")
                if (MARKERS.any { id.contains(it) } || inReels()) newReel = true
            } else if (store.isBlocked() && now - lastCheck > 1000) {
                lastCheck = now
                if (inReels()) { showBlock(); return }
            }
        } else {
            if (now - lastCheck < 300) return
            lastCheck = now
            val s = scan()
            if (!s.reel) { lastSig = null; return }
            if (store.isBlocked()) { showBlock(); return }
            if (s.sig != null) {
                if (s.sig != lastSig) { lastSig = s.sig; newReel = true }
            } else if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                newReel = true
            }
        }

        if (!newReel) return
        val gap = if (src == 1) 1200L else 900L
        if (now - lastCount < gap) return
        lastCount = now
        if (store.isBlocked()) { showBlock(); return }
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

    private fun inReels(): Boolean {
        val root = rootInActiveWindow ?: return false
        return IG_IDS.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
    }

    /** Reads visible text on screen; a changed signature = a new Short. Also detects if we're in Shorts. */
    private fun scan(): Scan {
        val root = rootInActiveWindow ?: return Scan(null, false)
        val sb = StringBuilder()
        var n = 0
        var nodes = 0
        var reel = false
        val ids = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30 || nodes > 700) return
            nodes++
            if (!node.isVisibleToUser) return
            val id = node.viewIdResourceName
            if (id != null && id.contains("reel", true)) {
                reel = true
                if (ids.size < 4 && !ids.contains(id)) ids.add(id.substringAfter(":id/"))
            }
            if (n < 60) {
                val t = node.text?.toString() ?: node.contentDescription?.toString()
                if (!t.isNullOrBlank() && t.length > 3) {
                    sb.append(t.filter { !it.isDigit() }.take(60)).append('|')
                    n++
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        if (reel && !seenReelIds) { seenReelIds = true; store.dbg("YT ids: " + ids.joinToString(",")) }
        return Scan(if (sb.isEmpty()) null else sb.toString(), reel)
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

    // ---------- limit reached block ----------
    private fun showBlock() {
        if (block != null) return
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F00B0F1A")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        col.addView(BrainView(this).apply { integrity = 0f }, LinearLayout.LayoutParams(dp(200), dp(200)))
        col.addView(TextView(this).apply {
            text = "Daily limit reached.\nReels are locked until midnight."
            setTextColor(Color.parseColor("#9FD8FF")); textSize = 20f
            gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(20))
        })
        col.addView(Button(this).apply { text = "Go home"; setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME) } })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wm.addView(
            root,
            WindowManager.LayoutParams(
                MATCH_PARENT, MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        )
        block = root
        handler.postDelayed(goHome, 4000)
    }

    private fun hideAll() {
        handler.removeCallbacks(poll); handler.removeCallbacks(goHome); handler.removeCallbacks(hidePopup)
        pill?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        block?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        pill = null; block = null
    }

    // ---------- breathing gate ----------
    private fun showBreathe() {
        if (breathe != null) return
        hideAll()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F5071B22")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(72), dp(24), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Breathe"; textSize = 36f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#BFF3E3"))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = "Mind integrity is at ${store.integrity().toInt()}%. Ten slow breaths, then you're free."
            textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FCDBD")); setPadding(0, dp(8), 0, 0)
        })
        val v = BreathView(this)
        bv = v
        col.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, dp(300)).apply { topMargin = dp(16) })
        col.addView(TextView(this).apply {
            text = "Or do one of your hobbies instead"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FCDBD")); setPadding(0, dp(8), 0, dp(8))
        })
        val quests = store.quests.take(6)
        if (quests.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Add hobbies in the Reel Brain app and they'll show up here."
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#6FA99A"))
            })
        } else {
            for (q in quests) {
                col.addView(TextView(this).apply {
                    text = q; textSize = 18f; gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#E6FFF5"))
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1F4A47")); cornerRadius = dp(14).toFloat()
                    }
                    setOnClickListener { hideBreathe() }
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            }
        }
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wm.addView(
            root,
            WindowManager.LayoutParams(
                MATCH_PARENT, MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        )
        breathe = root
        breathStart = SystemClock.elapsedRealtime()
        handler.post(tick)
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
