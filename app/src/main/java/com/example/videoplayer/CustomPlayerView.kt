package com.example.videoplayer

import android.content.Context
import android.util.AttributeSet
import com.example.videoplayer.MainActivity.VideoScaleMode
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView

class CustomPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    fun setCustomAspectRatio(ratio: Float) {
        val contentFrame = findViewById<AspectRatioFrameLayout>(R.id.exo_content_frame)
        contentFrame.setAspectRatio(ratio)
        contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }

    fun setScaleMode(mode: VideoScaleMode) {
        val contentFrame = findViewById<AspectRatioFrameLayout>(R.id.exo_content_frame)
        when (mode) {
            VideoScaleMode.FILL -> contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            VideoScaleMode.FIT -> contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoScaleMode.ORIGINAL -> contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            VideoScaleMode.STRETCH -> contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> {
                val ratio = when (mode) {
                    VideoScaleMode.RATIO_16_9 -> 16f/9f
                    VideoScaleMode.RATIO_4_3 -> 4f/3f
                    VideoScaleMode.RATIO_18_9 -> 18f/9f
                    VideoScaleMode.RATIO_19_5_9 -> 19.5f/9f
                    VideoScaleMode.RATIO_20_9 -> 20f/9f
                    VideoScaleMode.RATIO_21_9 -> 21f/9f
                    else -> 16f/9f
                }
                setCustomAspectRatio(ratio)
            }
        }
    }
}