package com.luke.reelbrain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val BASE = arrayOf(
        "........................",
        ".....OOOOOOOOOOOOOO.....",
        "...OOPLLPPPDDPPPPPPOO...",
        "..OPPLDDDPPDDPPDDDDPPO..",
        ".OPPPPPPPPPDDPPPPPPPPPO.",
        ".OPDDDPPDDPDDPDDPPDDDPO.",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        "OPPPPPPPPPPPPPPPPPPPPPPO",
        ".OPPPPPPPPPPPPPPPPPPPPO.",
        ".OPPPPPPPPPPPPPPPPPPPPO.",
        "..OPPPPPPPPPPPPPPPPPPO..",
        "..OOPDDDDPPPPPPDDDDPOO..",
        "....OPPPPPPPPPPPPPPO....",
        ".....OOOOOOOOOOOOOO.....",
        "........................"
)
private val FACE0 = arrayOf(
        "........................",
        "........................",
        "........................",
        "........................",
        "........................",
        "........................",
        "........................",
        "......WWW......WWW......",
        "......WWW......WWW......",
        "......WKK......KKW......",
        "......WKK......KKW......",
        "........................",
        "....BB...K....K...BB....",
        "..........KKKK..........",
        "........................",
        "........................",
        "........................",
        "........................",
        "........................",
        "........................"
)
private val FACE1 = arrayOf(
        "........................",
        "..............C.........",
        "..............C.........",
        ".............C..........",
        ".............C..........",
        ".......KK....C.KK.......",
        "......K.....C....K......",
        "......WWW......WWW......",
        "......WWK......KWW......",
        "......WWK......KWW......",
        "......WWW......WWW......",
        "........................",
        "....BB....K.K.K...BBC...",
        "...C.....K.K.K......C...",
        "...CC..............C....",
        "....CC..................",
        "........................",
        "........................",
        "........................",
        "........................"
)
private val FACE2 = arrayOf(
        "........................",
        "..............G.........",
        "..............G.........",
        ".......G.....G..........",
        "......G......G..........",
        "........G....G..........",
        "............G...........",
        "......WWW......WWW......",
        "......KWK......KWK......",
        "......WKW......WKW......",
        "......KWK......KWK......",
        "....................G...",
        "...G......KKKK......G...",
        "...G......KRRK......G...",
        "...GG..............G....",
        "....GG..................",
        "........................",
        "........................",
        "........................",
        "........................"
)
private val FACES = arrayOf(FACE0, FACE1, FACE2)

private fun c3(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

private val PALS = arrayOf(
    mapOf('O' to c3(95, 38, 66), 'P' to c3(244, 143, 177), 'D' to c3(214, 98, 140), 'L' to c3(255, 205, 220),
        'W' to Color.WHITE, 'K' to c3(40, 20, 40), 'B' to c3(255, 105, 140)),
    mapOf('O' to c3(70, 52, 62), 'P' to c3(190, 160, 165), 'D' to c3(150, 120, 130), 'L' to c3(220, 200, 205),
        'W' to Color.WHITE, 'K' to c3(40, 20, 40), 'B' to c3(205, 130, 140), 'C' to c3(110, 25, 40)),
    mapOf('O' to c3(10, 8, 10), 'P' to c3(72, 64, 66), 'D' to c3(48, 42, 44), 'L' to c3(100, 92, 94),
        'W' to c3(240, 240, 240), 'K' to c3(10, 8, 10), 'R' to c3(220, 80, 90), 'G' to c3(255, 140, 30))
)
private val RING = intArrayOf(c3(120, 205, 255), c3(120, 205, 255), c3(255, 100, 100))

/** Pixel mascot brain. Stage: integrity >= 70 happy, 50-70 cracked, < 50 fried. */
class BrainView(ctx: Context, private val ring: Boolean = true) : View(ctx) {
    var integrity = 100f
        set(v) { field = v; invalidate() }

    private val p = Paint()
    private val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    private fun stage() = if (integrity >= 70f) 0 else if (integrity >= 50f) 1 else 2

    override fun onDraw(c: Canvas) {
        val st = stage()
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        if (ring) drawRing(c, cx, cy, size, RING[st])
        val box = size * (if (ring) 0.62f else 0.96f)
        val cell = box / 24f
        val ox = cx - box / 2f
        val oy = cy - cell * 20f / 2f
        for (layer in arrayOf(BASE, FACES[st])) {
            for (y in 0 until 20) {
                val row = layer[y]
                for (x in 0 until 24) {
                    val col = PALS[st][row[x]] ?: continue
                    p.color = col
                    c.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell + 0.5f, oy + (y + 1) * cell + 0.5f, p)
                }
            }
        }
    }

    private fun drawRing(c: Canvas, cx: Float, cy: Float, size: Float, col: Int) {
        val r = size * 0.43f
        rp.color = col
        rp.strokeWidth = size * 0.012f
        c.drawCircle(cx, cy, r, rp)
        rp.strokeWidth = 1f
        c.drawCircle(cx, cy, r * 0.92f, rp)
        rp.strokeWidth = size * 0.008f
        for (i in 0 until 48) {
            val a = i * PI.toFloat() / 24f
            val len = r * (if (i % 4 == 0) 0.10f else 0.05f)
            c.drawLine(cx + cos(a) * r, cy + sin(a) * r, cx + cos(a) * (r + len), cy + sin(a) * (r + len), rp)
        }
        rp.strokeWidth = 1f
        for (i in 0 until 6) {
            val a = i * PI.toFloat() / 3f + 0.26f
            val px = cx + cos(a) * r * 0.83f
            val py = cy + sin(a) * r * 0.83f
            val s = size * 0.018f
            path.reset()
            path.moveTo(px, py - s); path.lineTo(px - s, py + s); path.lineTo(px + s, py + s); path.close()
            c.drawPath(path, rp)
        }
    }
}

class IntegrityBar(ctx: Context) : View(ctx) {
    var value = 100f
        set(v) { field = v; invalidate() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14203A") }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = Paint().apply { color = Color.parseColor("#0B0F1A"); strokeWidth = 3f }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(32))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, bg)
        fg.color = if (value >= 70f) Color.parseColor("#4CC3FF")
        else if (value >= 50f) Color.parseColor("#FFB347") else Color.parseColor("#FF5A6A")
        rect.set(0f, 0f, max(h, w * value / 100f), h)
        c.drawRoundRect(rect, r, r, fg)
        c.drawLine(w * 0.5f, 0f, w * 0.5f, h, mark)
        c.drawLine(w * 0.7f, 0f, w * 0.7f, h, mark)
        tp.textSize = context.dp(14).toFloat()
        c.drawText("Mind integrity ${value.toInt()}%", w / 2f, h / 2f + tp.textSize / 3f, tp)
    }
}

class MonthView(ctx: Context, private val ym: YearMonth, private val days: Map<String, Day>) : View(ctx) {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val rect = RectF()
    private val offset = ym.atDay(1).dayOfWeek.value - 1
    private val len = ym.lengthOfMonth()
    private val rows = (offset + len + 6) / 7

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, context.dp(66) + rows * (width / 7))
    }

    override fun onDraw(c: Canvas) {
        val cell = width / 7f
        val top = context.dp(52).toFloat()
        txt.color = Color.parseColor("#C8E6FF")
        txt.textSize = context.dp(15).toFloat()
        txt.typeface = Typeface.DEFAULT_BOLD
        txt.textAlign = Paint.Align.LEFT
        c.drawText("${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}", 0f, context.dp(20).toFloat(), txt)
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = context.dp(11).toFloat()
        txt.typeface = Typeface.DEFAULT
        txt.color = Color.parseColor("#6F8CB0")
        val wd = "MTWTFSS"
        for (i in 0 until 7) c.drawText(wd[i].toString(), cell * (i + 0.5f), context.dp(44).toFloat(), txt)
        val today = LocalDate.now()
        val pad = context.dp(2).toFloat()
        outline.strokeWidth = context.dp(2).toFloat()
        for (d in 1..len) {
            val idx = offset + d - 1
            val col = idx % 7
            val row = idx / 7
            val date = ym.atDay(d)
            rect.set(col * cell + pad, top + row * cell + pad, (col + 1) * cell - pad, top + (row + 1) * cell - pad)
            val rec = days[date.toString()]
            val color = when {
                date.isAfter(today) -> Color.parseColor("#0F1626")
                rec == null -> Color.parseColor("#141C30")
                rec.total <= rec.cap -> Color.parseColor("#3DB7FF")
                rec.total <= rec.cap * 5 / 4 -> Color.parseColor("#FFB347")
                else -> Color.parseColor("#FF5A6A")
            }
            fillP.color = color
            c.drawRoundRect(rect, pad * 2, pad * 2, fillP)
            if (date == today) c.drawRoundRect(rect, pad * 2, pad * 2, outline)
            txt.color = if (rec != null && !date.isAfter(today)) Color.parseColor("#06101F") else Color.parseColor("#4A5F80")
            c.drawText(d.toString(), rect.centerX(), rect.centerY() + txt.textSize / 3f, txt)
        }
    }
}

class BreathView(ctx: Context) : View(ctx) {
    var label = "Breathe in"
    var sub = ""
    var scale = 0.45f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFF5"); textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.8f * scale
        p.color = Color.parseColor("#2A7F7A"); p.alpha = 50; c.drawCircle(cx, cy, r * 1.22f, p)
        p.color = Color.parseColor("#4FB9A8"); p.alpha = 90; c.drawCircle(cx, cy, r * 1.1f, p)
        p.color = Color.parseColor("#7FD6C2"); p.alpha = 170; c.drawCircle(cx, cy, r, p)
        t.typeface = Typeface.DEFAULT_BOLD
        t.textSize = context.dp(22).toFloat()
        c.drawText(label, cx, cy, t)
        t.typeface = Typeface.DEFAULT
        t.textSize = context.dp(14).toFloat()
        c.drawText(sub, cx, cy + context.dp(26), t)
    }
}
