package com.cpsoverlay.counter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ClickHistoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply {
        color = Color.parseColor("#39FF14")
        style = Paint.Style.FILL
    }

    private var history: List<Int> = emptyList()

    fun updateHistory(values: List<Int>) {
        history = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (history.isEmpty()) return

        val maxVal = (history.maxOrNull() ?: 1).coerceAtLeast(1)
        val barWidth = width.toFloat() / history.size
        val h = height.toFloat()

        history.forEachIndexed { i, v ->
            val barHeight = (v.toFloat() / maxVal) * h
            val left = i * barWidth
            canvas.drawRect(left, h - barHeight, left + barWidth * 0.8f, h, barPaint)
        }
    }
}
