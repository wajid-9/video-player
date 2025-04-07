package com.example.videoplayer
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

class StrokeSpan(
    private val strokeColor: Int,
    private val strokeWidth: Float
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // Draw the stroke (border)
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = strokeColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // Draw the fill (main text)
        paint.style = Paint.Style.FILL
        paint.color = originalColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // Restore original paint settings
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
        paint.color = originalColor
    }
}