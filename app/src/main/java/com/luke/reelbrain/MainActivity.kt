package com.luke.reelbrain

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var brain: BrainView
    private lateinit var bar: IntegrityBar
    private lateinit var status: TextView
    private lateinit var todayTv: TextView
    private lateinit var streakTv: TextView
    private lateinit var questBox: LinearLayout
    private lateinit var calBox: LinearLayout
    private lateinit var monthsBox: LinearLayout
    private lateinit var debug: TextView

    private val light = Color.parseColor("#C8E6FF")
    private val blue = Color.parseColor("#4CC3FF")
    private val dim = Color.parseColor("#6F8CB0")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun btn(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(blue); gravity = Gravity.CENTER
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#101B33")); setStroke(dp(1), blue); cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun section(title: String, content: android.view.View, open: Boolean): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = btn(if (open) "$title  ▴" else "$title  ▾") {}
        content.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
        head.setOnClickListener {
            val show = content.visibility != android.view.View.VISIBLE
            content.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            head.text = if (show) "$title  ▴" else "$title  ▾"
        }
        box.addView(head)
        box.addView(content)
        return box
    }

    private fun serviceOn() = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(packageName) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0F1A")); addView(col) })

        col.addView(label("Reel Brain", 26f, light, true))
        status = label("", 14f, dim)
        col.addView(status)

        // hidden setup
        val setup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setup.addView(btn("1. Allow restricted settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        setup.addView(btn("2. Turn on Reel Brain in Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        setup.addView(CheckBox(this).apply {
            text = "Popup every 25 reels"; setTextColor(light); isChecked = store.popups
            setOnCheckedChangeListener { _, c -> store.popups = c }
        })
        val unlock = btn("Unlock reels for today (10s wait)") {}
        unlock.setOnClickListener {
            unlock.isEnabled = false
            object : CountDownTimer(10_000, 1000) {
                override fun onTick(ms: Long) { unlock.text = "Wait ${ms / 1000 + 1}s..." }
                override fun onFinish() {
                    store.unlockedDay = LocalDate.now().toString()
                    unlock.text = "Unlocked for today"
                }
            }.start()
        }
        setup.addView(unlock)
        col.addView(section("Setup & options", setup, !serviceOn()))

        brain = BrainView(this)
        col.addView(brain, LinearLayout.LayoutParams(dp(250), dp(250)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8)
        })
        bar = IntegrityBar(this)
        col.addView(bar, LinearLayout.LayoutParams(MP, WC))
        todayTv = label("", 14f, light)
        todayTv.gravity = Gravity.CENTER
        col.addView(todayTv)

        col.addView(label("Max reels per day (your limit)", 14f, dim))
        col.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(store.cap.toString()); setTextColor(light)
            doAfterTextChanged {
                val v = it.toString().toIntOrNull()
                if (v != null && v >= 1) { store.cap = v; refresh() }
            }
        })

        col.addView(label("Hobbies", 16f, light, true))
        questBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(questBox)
        col.addView(btn("+ Add hobby") { addQuestDialog() })

        streakTv = label("", 15f, light, true)
        streakTv.setPadding(0, dp(22), 0, dp(6))
        col.addView(streakTv)
        calBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(calBox, LinearLayout.LayoutParams(MP, WC))
        val legend = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((txt, colr) in listOf("■ under limit" to "#3DB7FF", "■ over limit" to "#FFB347", "■ 125%+" to "#FF5A6A")) {
            legend.addView(label(txt, 12f, Color.parseColor(colr)).apply { setPadding(0, dp(6), dp(14), dp(6)) })
        }
        col.addView(legend)
        monthsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(section("Last 12 months", monthsBox, false))

        debug = label("", 11f, dim)
        col.addView(debug)

        renderQuests()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val i = store.integrity()
        brain.integrity = i
        bar.value = i
        todayTv.text = "Today: ${store.total()} / ${store.cap}   (IG ${store.ig()} · YT ${store.yt()})"
        streakTv.text = "Streak: ${store.streak()} days under limit  ·  Best: ${store.bestStreak()}"
        val map = store.allDays().associateBy { it.date }
        calBox.removeAllViews()
        calBox.addView(MonthView(this, YearMonth.now(), map), LinearLayout.LayoutParams(MP, WC))
        monthsBox.removeAllViews()
        val firstMonth = YearMonth.from(store.firstDate())
        var m = YearMonth.now().minusMonths(1)
        var count = 0
        while (!m.isBefore(firstMonth) && count < 11) {
            monthsBox.addView(MonthView(this, m, map), LinearLayout.LayoutParams(MP, WC))
            m = m.minusMonths(1)
            count++
        }
        if (count == 0) monthsBox.addView(label("Only this month so far.", 13f, dim))
        status.text = if (serviceOn()) "Counter service: ON" else "Counter service: OFF - open Setup below"
        debug.text = "Debug (recent events):\n" + store.dbgText()
    }

    private fun renderQuests() {
        questBox.removeAllViews()
        val qs = store.quests
        if (qs.isEmpty()) questBox.addView(label("No hobbies yet. Add what you'd rather do than scroll.", 13f, dim))
        qs.forEachIndexed { idx, q ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#101B33")); cornerRadius = dp(10).toFloat()
                }
            }
            row.addView(TextView(this).apply { text = q; textSize = 16f; setTextColor(light) }, LinearLayout.LayoutParams(0, WC, 1f))
            row.addView(TextView(this).apply {
                text = "✕"; textSize = 16f; setTextColor(dim); setPadding(dp(14), dp(4), dp(14), dp(4))
                setOnClickListener {
                    store.quests = store.quests.filterIndexed { i, _ -> i != idx }
                    renderQuests()
                }
            })
            questBox.addView(row, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        }
    }

    private fun addQuestDialog() {
        val et = EditText(this).apply { hint = "e.g. Chess, Drawing, Walk"; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("Add hobby")
            .setView(et)
            .setPositiveButton("Add") { _, _ ->
                val t = et.text.toString().trim().replace("\n", " ")
                if (t.isNotEmpty()) { store.quests = store.quests + t; renderQuests() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
