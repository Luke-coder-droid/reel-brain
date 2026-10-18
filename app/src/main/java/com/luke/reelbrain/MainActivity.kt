package com.luke.reelbrain

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var brain: BrainView
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var debug: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val light = Color.parseColor("#C8E6FF")
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F1A")); addView(col)
        })
        fun tv(size: Float = 15f) = TextView(this).apply {
            setTextColor(light); textSize = size; setPadding(0, dp(8), 0, dp(8))
        }

        status = tv(); col.addView(status)
        col.addView(Button(this).apply {
            text = "1. Allow restricted settings (App info > top-right menu)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        col.addView(Button(this).apply {
            text = "2. Turn on Reel Brain in Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        brain = BrainView(this)
        col.addView(brain, LinearLayout.LayoutParams(dp(160), dp(160)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16)
        })
        stats = tv(); col.addView(stats)

        col.addView(CheckBox(this).apply {
            text = "System popups"; setTextColor(light); isChecked = store.popups
            setOnCheckedChangeListener { _, c -> store.popups = c }
        })
        col.addView(CheckBox(this).apply {
            text = "Block screen at daily cap"; setTextColor(light); isChecked = store.capEnabled
            setOnCheckedChangeListener { _, c -> store.capEnabled = c }
        })
        col.addView(tv().apply { text = "Daily cap (also your streak goal):" })
        col.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(store.cap.toString()); setTextColor(light)
            doAfterTextChanged { store.cap = it.toString().toIntOrNull()?.coerceAtLeast(1) ?: 100 }
        })
        col.addView(Button(this).apply {
            text = "Unlock block for today (10s wait)"
            setOnClickListener {
                isEnabled = false
                object : CountDownTimer(10_000, 1000) {
                    override fun onTick(ms: Long) { text = "Wait ${ms / 1000 + 1}s..." }
                    override fun onFinish() {
                        store.unlockedDay = LocalDate.now().toString()
                        text = "Unlocked for today"
                    }
                }.start()
            }
        })
        debug = tv(11f); col.addView(debug)
    }

    override fun onResume() {
        super.onResume()
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = if (on) "Counter service: ON" else "Counter service: OFF (do steps 1 and 2)"
        brain.severity = store.severity()
        val names = listOf("Healthy", "Cracked", "Fried")
        val week = store.week()
        stats.text = "Brain: ${names[store.stage()]}\n" +
            "Today: IG ${store.ig()} | YT ${store.yt()} | total ${store.total()}\n" +
            "Streak: ${store.streak()} days under cap\n\nLast days:\n" +
            week.joinToString("\n") { "${it.date.substring(5)}  IG ${it.ig}  YT ${it.yt}  = ${it.total}" } +
            "\nAvg: ${week.sumOf { it.total } / week.size}/day"
        debug.text = "Debug (last scroll events seen):\n" + store.dbgText()
    }
}
