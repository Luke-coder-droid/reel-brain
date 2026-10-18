package com.luke.reelbrain

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ReelService : AccessibilityService() {
    companion object {
        const val TAG = "ReelBrain"
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        const val DEBOUNCE = 900L
        val MARKERS = listOf("clips_viewer", "reel_recycler", "reel_player")
        val REEL_IDS = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container"
        )
        val MILESTONES = listOf(
            "[SYSTEM] Gate cleared. Mind integrity declining.",
            "[SYSTEM] Warning: mental fatigue detected.",
            "[SYSTEM] Hunter, your focus is being drained.",
            "[SYSTEM] Penalty Zone approaching. Stop scrolling."
        )
    }

    private val store by lazy { Store(this) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())

    private var overlay: LinearLayout? = null
    private var counter: TextView? = null
    private var popupView: TextView? = null
    private var brain: BrainView? = null
    private var block: FrameLayout? = null
    private var lastCount = 0L
    private var lastCheck = 0L
    private var misses = 0

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

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val src = when (pkg) { IG -> 0; YT -> 1; else -> return }
        showOverlay()
        val now = SystemClock.elapsedRealtime()
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val id = e.source?.viewIdResourceName ?: "none"
            val l = "${pkg.substringAfterLast('.')} ${e.className?.toString()?.substringAfterLast('.')} $id"
            Log.d(TAG, l); store.dbg(l)
            if (now - lastCount < DEBOUNCE) return
            if (!(MARKERS.any { id.contains(it) } || inReels())) return
            lastCount = now
            if (store.isBlocked()) { showBlock(); return }
            val worse = store.addReel(src)
            refresh()
            val t = store.total()
            when {
                store.capEnabled && t == store.cap -> showPopup("[SYSTEM] Daily limit reached. Penalty Zone engaged.")
                worse -> showPopup(
                    if (store.stage() == 1) "[SYSTEM] Alert: brain integrity compromised. Cracks detected."
                    else "[SYSTEM] CRITICAL: brain integrity failing."
                )
                t % 25 == 0 -> showPopup(MILESTONES[(t / 25 - 1) % MILESTONES.size])
            }
            if (store.isBlocked()) showBlock()
        } else if (store.isBlocked() && now - lastCheck > 1000) {
            lastCheck = now
            if (inReels()) showBlock()
        }
    }

    private fun inReels(): Boolean {
        val root = rootInActiveWindow ?: return false
        return REEL_IDS.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
    }

    private fun showOverlay() {
        if (overlay != null) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(14), dp(4))
            background = pill()
        }
        brain = BrainView(this)
        row.addView(brain, LinearLayout.LayoutParams(dp(34), dp(34)))
        counter = TextView(this).apply {
            setTextColor(Color.parseColor("#9FD8FF")); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, 0, 0)
        }
        row.addView(counter)
        popupView = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#9FD8FF")); textSize = 12f
            setPadding(dp(12), dp(6), dp(12), dp(6)); background = pill()
        }
        box.addView(row)
        box.addView(popupView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(36) }
        wm.addView(box, lp)
        overlay = box
        misses = 0
        handler.postDelayed(poll, 1000)
        refresh()
    }

    private fun pill() = GradientDrawable().apply {
        setColor(Color.parseColor("#CC0B0F1A")); cornerRadius = dp(20).toFloat()
        setStroke(dp(1), Color.parseColor("#55A8DFFF"))
    }

    private fun refresh() {
        val cap = if (store.capEnabled) "/${store.cap}" else ""
        counter?.text = "IG ${store.ig()}  YT ${store.yt()}  •  ${store.total()}$cap"
        brain?.severity = store.severity()
    }

    private fun showPopup(msg: String) {
        if (!store.popups) return
        val t = popupView ?: return
        t.text = msg; t.visibility = View.VISIBLE
        handler.removeCallbacks(hidePopup)
        handler.postDelayed(hidePopup, 3500)
    }

    private fun showBlock() {
        if (block != null) return
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F00B0F1A")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        col.addView(BrainView(this).apply { severity = 1f }, LinearLayout.LayoutParams(dp(140), dp(140)))
        col.addView(TextView(this).apply {
            text = "[SYSTEM]\nDaily limit reached.\nThe gate is sealed until midnight."
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
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        block?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null; block = null
    }

    override fun onInterrupt() {}
    override fun onDestroy() { hideAll(); super.onDestroy() }
}
