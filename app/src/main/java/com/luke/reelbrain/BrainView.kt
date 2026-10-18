package com.luke.reelbrain

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.random.Random

class BrainView(ctx: Context) : View(ctx) {
    var severity = 0f
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ev = ArgbEvaluator()

    private fun mix(a: Int, b: Int, t: Float) = ev.evaluate(t.coerceIn(0f, 1f), a, b) as Int

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val healthy = Color.parseColor("#F48FB1")
        val sick = Color.parseColor("#9BA37A")
        val dead = Color.parseColor("#3B3B3B")
        val base = if (severity < 0.5f) mix(healthy, sick, severity * 2) else mix(sick, dead, (severity - 0.5f) * 2)
        val dark = mix(base, Color.BLACK, 0.4f)

        fill.color = base
        val left = RectF(w * 0.06f, h * 0.15f, w * 0.53f, h * 0.85f)
        val right = RectF(w * 0.47f, h * 0.15f, w * 0.94f, h * 0.85f)
        c.drawOval(left, fill); c.drawOval(right, fill)

        line.color = dark
        line.strokeWidth = w * 0.025f
        c.drawLine(w / 2, h * 0.18f, w / 2, h * 0.82f, line)
        for (mirror in listOf(false, true)) {
            fun x(v: Float) = if (mirror) w * (1 - v) else w * v
            val path = Path()
            path.moveTo(x(0.2f), h * 0.38f)
            path.quadTo(x(0.32f), h * 0.28f, x(0.4f), h * 0.4f)
            path.quadTo(x(0.3f), h * 0.5f, x(0.22f), h * 0.6f)
            path.quadTo(x(0.3f), h * 0.72f, x(0.4f), h * 0.66f)
            c.drawPath(path, line)
        }

        if (severity > 0.25f) {
            val n = ((severity - 0.2f) * 8).toInt().coerceAtLeast(1)
            val rnd = Random(7)
            line.color = Color.parseColor("#111111")
            line.strokeWidth = w * 0.03f
            for (i in 0 until n) {
                var cx = w * (0.2f + 0.6f * rnd.nextFloat())
                var cy = h * 0.17f
                val p = Path(); p.moveTo(cx, cy)
                for (s in 0 until 4) {
                    cx += (rnd.nextFloat() - 0.5f) * w * 0.14f
                    cy += h * 0.15f
                    p.lineTo(cx, cy)
                }
                c.drawPath(p, line)
            }
        }
        if (severity > 0.75f) {
            fill.color = Color.parseColor("#55FF3B30")
            c.drawOval(left, fill); c.drawOval(right, fill)
            fill.color = Color.parseColor("#CC111111")
            val rnd = Random(3)
            for (i in 0 until 6) c.drawCircle(w * (0.2f + 0.6f * rnd.nextFloat()), h * (0.3f + 0.4f * rnd.nextFloat()), w * 0.04f, fill)
        }
    }
}
