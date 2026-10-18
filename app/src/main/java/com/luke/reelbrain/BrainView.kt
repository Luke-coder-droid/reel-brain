package com.luke.reelbrain

import android.content.Context
import android.graphics.*
import android.view.View

class BrainView(ctx: Context) : View(ctx) {
    var severity = 0f
        set(v) { field = v; invalidate() }

    private val bmps = listOf(R.drawable.brain_0, R.drawable.brain_1, R.drawable.brain_2)
        .map { BitmapFactory.decodeResource(resources, it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(c: Canvas) {
        val p = (severity * 2f).coerceIn(0f, 2f)
        val i = p.toInt().coerceAtMost(1)
        val f = p - i
        val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.alpha = 255
        c.drawBitmap(bmps[i], null, dst, paint)
        if (f > 0f) {
            paint.alpha = (f * 255).toInt()
            c.drawBitmap(bmps[i + 1], null, dst, paint)
        }
    }
}
