package com.cpsoverlay.counter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class KeyHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pressed = mutableSetOf<Char>()

    private val offPaint = Paint().apply { color = Color.parseColor("#332B2B2B") }
    private val onPaint = Paint().apply { color = Color.parseColor("#FF39FF14") }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun setKeyState(key: Char, isDown: Boolean) {
        if (isDown) pressed.add(key) else pressed.remove(key)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = width / 3f
        drawKey(canvas, 'W', cell, cell, 0f)
        drawKey(canvas, 'A', 0f, cell, cell)
        drawKey(canvas, 'S', cell, cell, cell)
        drawKey(canvas, 'D', cell * 2, cell, cell)
    }

    private fun drawKey(canvas: Canvas, key: Char, x: Float, w: Float, y: Float) {
        val paint = if (pressed.contains(key)) onPaint else offPaint
        canvas.drawRect(x, y, x + w - 2f, y + w - 2f, paint)
        canvas.drawText(key.toString(), x + w / 2f, y + w / 2f + 6f, textPaint)
    }
}
