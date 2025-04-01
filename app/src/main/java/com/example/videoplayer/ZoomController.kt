// ZoomController.kt
package com.example.videoplayer

import android.view.ScaleGestureDetector
import android.view.View

class ZoomController(private val targetView: View) {
    private var scaleFactor = 1.0f
    private val minScale = 1.0f
    private val maxScale = 3.0f

    // Add pivot point tracking for better zoom experience
    private var pivotX = 0f
    private var pivotY = 0f

    val scaleGestureDetector = ScaleGestureDetector(targetView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                // Set pivot point to the center between fingers
                pivotX = detector.focusX
                pivotY = detector.focusY
                targetView.pivotX = pivotX
                targetView.pivotY = pivotY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
                targetView.scaleX = scaleFactor
                targetView.scaleY = scaleFactor
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Optional: Add any cleanup or boundary checks
            }
        })

    fun resetZoom() {
        scaleFactor = 1.0f
        targetView.scaleX = scaleFactor
        targetView.scaleY = scaleFactor
        // Reset pivot to center
        targetView.pivotX = targetView.width / 2f
        targetView.pivotY = targetView.height / 2f
    }
}