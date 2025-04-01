package com.example.videoplayer

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.lukelorusso.verticalseekbar.VerticalSeekBar
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    // Player and UI components
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var playerView: PlayerView
    private lateinit var videoSeekBar: SeekBar
    private lateinit var speedSeekBar: SeekBar
    private lateinit var brightnessSeekBar: VerticalSeekBar
    private lateinit var seekBarVolume: VerticalSeekBar
    private lateinit var fullScreenButton: ImageButton
    private lateinit var subtitleButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var bottomControls: LinearLayout
    private lateinit var controlsLayout: LinearLayout
    private lateinit var leftTimeTextView: TextView
    private lateinit var rightTimeTextView: TextView
    private lateinit var seekTimeTextView: TextView
    private lateinit var twoxtimeTextview: TextView
    private lateinit var subtitleTextView: TextView
    private lateinit var videoTitleTextView: TextView
    private lateinit var brightnessOverlay: View
    private lateinit var playImageView: ImageView
    private lateinit var audioManager: AudioManager
    private lateinit var tvVolumeValue: TextView
    private var currentScaleMode = VideoScaleMode.FIT
    // New UI components for brightness/volume controls
    private lateinit var brightnessContainer: RelativeLayout
    private lateinit var volumeContainer: RelativeLayout
    private lateinit var brightnessText: TextView
    private lateinit var volumeText: TextView

    // Audio effect for volume boost
    private var bassBoost: BassBoost? = null

    // Gesture detectors
    private lateinit var gestureDetector: GestureDetectorCompat

    // State variables
    private var isFullScreen = false
    private var isSpeedIncreased = false
    private var areControlsVisible = false
    private var baseSubtitleSize = 18f
    private val sensitivityFactor = 1.0f
    private val hideControlsDelay = 3000L

    // Subtitle related
    private var subtitles: List<SubtitleEntry> = emptyList()
    private var isUsingEmbeddedSubtitles = false
    private var currentSubtitleColor = SUBTITLE_COLOR_DEFAULT
    private var currentSubtitleSize = SUBTITLE_SIZE_DEFAULT
    private var currentSubtitleBackground = Color.TRANSPARENT
    private var currentSubtitleShadow = true
    private val minSwipeDistance = 20f // Minimum pixels to move before considering it a swipe

    // Request codes
    private val PICK_VIDEO_REQUEST = 1
    private val PICK_SUBTITLE_REQUEST = 2

    // URIs
    private var videoUri: Uri? = null
    private var subtitleUri: Uri? = null

    companion object {
        // Subtitle constants
        private const val SUBTITLE_COLOR_DEFAULT = Color.YELLOW
        private const val SUBTITLE_COLOR_WHITE = Color.WHITE
        private const val SUBTITLE_COLOR_RED = Color.RED
        private const val SUBTITLE_COLOR_GREEN = Color.GREEN
        private const val SUBTITLE_COLOR_BLUE = Color.BLUE
        private const val SUBTITLE_SIZE_DEFAULT = 20f
        private const val SUBTITLE_SIZE_LARGE = 30f
        private const val SUBTITLE_SIZE_SMALL = 19f
    }

    // Handlers and Runnables
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                val currentPosition = player.currentPosition
                val duration = player.duration
                if (duration > 0) {
                    val progress = (currentPosition * 100 / duration).toInt()
                    videoSeekBar.progress = progress
                    updateTimeDisplays()
                    if (!isUsingEmbeddedSubtitles) {
                        updateSubtitles(currentPosition)
                    }
                }
                handler.postDelayed(this, 100)
            }
        }
    }

    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideBrightnessOverlayRunnable = Runnable {
        brightnessOverlay.visibility = View.GONE
        brightnessContainer.visibility = View.GONE
    }
    private val hideVolumeRunnable = Runnable {
        volumeContainer.visibility = View.GONE
        brightnessOverlay.visibility = View.GONE
    }
    private val hideSeekTimeRunnable = Runnable {
        seekTimeTextView.visibility = View.GONE
        twoxtimeTextview.visibility = View.GONE
    }
    private fun applyScaleMode(mode: VideoScaleMode) {
        currentScaleMode = mode

        // Try multiple ways to find the content frame
        val contentFrame = playerView.findViewById<View>(R.id.exo_content_frame)
            ?: playerView.findViewById<View>(com.google.android.exoplayer2.ui.R.id.exo_content_frame)
            ?: playerView.videoSurfaceView?.parent as? ViewGroup

        if (contentFrame is AspectRatioFrameLayout) {
            when (mode) {
                VideoScaleMode.FILL -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    contentFrame.setAspectRatio(0f)
                }
                VideoScaleMode.FIT -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentFrame.setAspectRatio(0f)
                }
                VideoScaleMode.ORIGINAL -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(0f)
                }
                VideoScaleMode.STRETCH -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    contentFrame.setAspectRatio(0f)
                }
                VideoScaleMode.RATIO_16_9 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(16f / 9f)
                }
                VideoScaleMode.RATIO_4_3 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(4f / 3f)
                }
                VideoScaleMode.RATIO_18_9 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(18f / 9f)
                }
                VideoScaleMode.RATIO_19_5_9 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(19.5f / 9f)
                }
                VideoScaleMode.RATIO_20_9 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(20f / 9f)
                }
                VideoScaleMode.RATIO_21_9 -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    contentFrame.setAspectRatio(21f / 9f)
                }
            }
        } else {
            // Fallback to setting resize mode on PlayerView
            playerView.resizeMode = when (mode) {
                VideoScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoScaleMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                VideoScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }

        setContentView(R.layout.activity_main)

        initViews()
        initPlayer()
        initGestureDetectors()
        setupSeekBars()
        setupFullScreenButton()
        setupSubtitleButton()
        setupAudioButton()

        playerView.setBackgroundColor(Color.BLACK)

        val videoUriFromIntent = intent.getParcelableExtra<Uri>("VIDEO_URI")
        if (videoUriFromIntent != null) {
            videoUri = videoUriFromIntent
            try {
                contentResolver.takePersistableUriPermission(videoUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w("MainActivity", "Video permission not persistable: ${e.message}")
            }
            playVideo(videoUri!!)
        } else if (savedInstanceState == null) {
            launchVideoList()
        }

        playerView.post {
            setFullScreenMode(false)
        }
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        videoSeekBar = findViewById(R.id.videoSeekBar)
        playImageView = findViewById(R.id.playImageView)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        brightnessSeekBar = findViewById(R.id.BrightnessSeekBar)
        seekBarVolume = findViewById(R.id.VolumeSeekBar)
        fullScreenButton = findViewById(R.id.fullScreenButton)
        subtitleButton = findViewById(R.id.subtitleButton)
        audioButton = findViewById(R.id.audioButton)
        bottomControls = findViewById(R.id.bottomControls)
        controlsLayout = findViewById(R.id.controlsLayout)
        leftTimeTextView = findViewById(R.id.lefttime)
        rightTimeTextView = findViewById(R.id.righttime)
        seekTimeTextView = findViewById(R.id.seekTimeTextView)
        twoxtimeTextview = findViewById(R.id.twoxTimeTextView)
        subtitleTextView = findViewById(R.id.subtitleTextView)
        videoTitleTextView = findViewById(R.id.videoTitleTextView)
        brightnessOverlay = findViewById(R.id.brightnessOverlay)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val zoomButton: ImageButton = findViewById(R.id.zoomButton)
        zoomButton.setOnClickListener { showZoomOptionsDialog() }
        zoomButton.visibility = View.VISIBLE
        brightnessContainer = findViewById(R.id.brightnessContainer)
        volumeContainer = findViewById(R.id.volumeContainer)
        brightnessText = findViewById(R.id.brightnessText)
        volumeText = findViewById(R.id.volumeText)

        bottomControls.visibility = View.GONE
        fullScreenButton.visibility = View.GONE
        subtitleButton.visibility = View.GONE
        audioButton.visibility = View.GONE
        subtitleTextView.visibility = View.GONE
        videoTitleTextView.visibility = View.GONE
        playImageView.visibility = View.GONE
        brightnessContainer.visibility = View.GONE
        volumeContainer.visibility = View.GONE
        brightnessOverlay.visibility = View.GONE
        tvVolumeValue.visibility = View.GONE

        brightnessSeekBar.maxValue = 100
        seekBarVolume.maxValue = 200
        val initialSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val initialProgress = (initialSystemVolume.toFloat() / maxSystemVolume * 100).toInt()
        brightnessSeekBar.progress = 50
        seekBarVolume.progress = initialProgress
        brightnessText.text = "50%"
        volumeText.text = "$initialProgress%"
        tvVolumeValue.text = "$initialProgress%"

        subtitleTextView.apply {
            setTextColor(currentSubtitleColor)
            textSize = currentSubtitleSize
            maxLines = 3
            setBackgroundColor(currentSubtitleBackground)
            if (currentSubtitleShadow) {
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ABOVE, R.id.bottomControls)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
            }
            bringToFront()
            setOnTouchListener(SubTitleDragListener())
        }

        playerView.visibility = View.VISIBLE
    }
    private fun showZoomOptionsDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Select Zoom/Aspect Ratio")

        val options = arrayOf(
            "Fill",
            "Fit",
            "Original",
            "Stretch",
            "16:9",
            "4:3",
            "18:9",
            "19.5:9",
            "20:9",
            "21:9"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> applyScaleMode(VideoScaleMode.FILL)
                1 -> applyScaleMode(VideoScaleMode.FIT)
                2 -> applyScaleMode(VideoScaleMode.ORIGINAL)
                3 -> applyScaleMode(VideoScaleMode.STRETCH)
                4 -> applyScaleMode(VideoScaleMode.RATIO_16_9)
                5 -> applyScaleMode(VideoScaleMode.RATIO_4_3)
                6 -> applyScaleMode(VideoScaleMode.RATIO_18_9)
                7 -> applyScaleMode(VideoScaleMode.RATIO_19_5_9)
                8 -> applyScaleMode(VideoScaleMode.RATIO_20_9)
                9 -> applyScaleMode(VideoScaleMode.RATIO_21_9)
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
        playerView.player = player
        playerView.useController = false
        playerView.subtitleView?.visibility = View.GONE
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            handler.post(updateSeekBarRunnable)
                            playImageView.visibility = View.GONE
                            subtitleButton.visibility = View.VISIBLE
                            audioButton.visibility = View.VISIBLE
                        }
                    }
                    Player.STATE_ENDED -> {
                        handler.removeCallbacks(updateSeekBarRunnable)
                        playImageView.visibility = View.VISIBLE
                    }
                    Player.STATE_BUFFERING -> Log.d("Player", "State BUFFERING")
                    Player.STATE_IDLE -> Log.d("Player", "State IDLE")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    handler.post(updateSeekBarRunnable)
                    playImageView.visibility = View.GONE
                } else {
                    handler.removeCallbacks(updateSeekBarRunnable)
                    playImageView.visibility = View.VISIBLE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("Player", "Playback error: ${error.message}", error)
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCues(cues: MutableList<Cue>) {
                if (isUsingEmbeddedSubtitles) {
                    val currentCueText = cues.joinToString("\n") { it.text?.toString() ?: "" }
                    runOnUiThread {
                        subtitleTextView.text = currentCueText
                        subtitleTextView.visibility = if (currentCueText.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                try {
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        enabled = true
                        setStrength(0)
                    }
                } catch (e: Exception) {
                    Log.e("BassBoost", "Failed to initialize BassBoost: ${e.message}")
                    Toast.makeText(this@MainActivity, "Volume boost unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        })

        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun initGestureDetectors() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControlsVisibility()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                isSpeedIncreased = true
                player.playbackParameters = PlaybackParameters(2.0f)
                twoxtimeTextview.text = "2x Speed"
                twoxtimeTextview.visibility = View.VISIBLE
                handler.removeCallbacks(hideSeekTimeRunnable)
                handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null || player.duration <= 0) return false

                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                val deltaX = e2.x - e1.x
                val deltaY = e1.y - e2.y

                if (abs(deltaY) < minSwipeDistance && abs(deltaX) < minSwipeDistance) return false

                when {
                    abs(deltaX) > abs(deltaY) -> {
                        val seekDelta = (deltaX / screenWidth * player.duration * 0.1f).toLong()
                        val newPosition = max(0, min(player.duration, player.currentPosition + seekDelta))
                        player.seekTo(newPosition)
                        seekTimeTextView.text = formatTime(newPosition / 1000)
                        seekTimeTextView.visibility = View.VISIBLE
                        handler.removeCallbacks(hideSeekTimeRunnable)
                        handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
                        resetHideControlsTimer()
                    }
                    e1.x < screenWidth / 2 -> {
                        brightnessOverlay.visibility = View.VISIBLE
                        brightnessContainer.visibility = View.VISIBLE
                        val normalizedDelta = -deltaY / screenHeight
                        val progressChange = (normalizedDelta * 50 * sensitivityFactor *
                                min(1f, abs(normalizedDelta) * 2)).toInt()
                        val newProgress = max(0, min(100, brightnessSeekBar.progress + progressChange))
                        brightnessSeekBar.progress = newProgress
                        brightnessText.text = "$newProgress%"
                        val brightness = newProgress / 100f
                        val lp = window.attributes
                        lp.screenBrightness = if (brightness == 0f) 0.01f else brightness
                        window.attributes = lp
                        handler.removeCallbacks(hideBrightnessOverlayRunnable)
                        handler.postDelayed(hideBrightnessOverlayRunnable, 1000)
                        resetHideControlsTimer()
                    }
                    else -> {
                        brightnessOverlay.visibility = View.VISIBLE
                        volumeContainer.visibility = View.VISIBLE
                        val normalizedDelta = -deltaY / screenHeight
                        val progressChange = (normalizedDelta * 100 * sensitivityFactor *
                                min(1f, abs(normalizedDelta) * 2)).toInt()
                        val newProgress = max(0, min(200, seekBarVolume.progress + progressChange))
                        seekBarVolume.progress = newProgress
                        volumeText.text = "$newProgress%"
                        tvVolumeValue.text = "$newProgress%"

                        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (newProgress <= 100) {
                            player.volume = newProgress / 100f
                            val systemVolume = (newProgress / 100f * maxSystemVolume).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                            bassBoost?.setStrength(0)
                        } else {
                            player.volume = 1.0f
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
                            val boostLevel = ((newProgress - 100) / 100f * 1000).toInt()
                            bassBoost?.setStrength(boostLevel.toShort())
                        }

                        handler.removeCallbacks(hideVolumeRunnable)
                        handler.postDelayed(hideVolumeRunnable, 1000)
                        resetHideControlsTimer()
                    }
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (player.duration <= 0) return false
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val middleThirdStart = screenWidth / 3
                val middleThirdEnd = (screenWidth * 2) / 3
                val touchX = e.x
                return when {
                    touchX in middleThirdStart..middleThirdEnd -> {
                        player.playWhenReady = !player.playWhenReady
                        true
                    }
                    touchX < screenWidth / 2 -> {
                        seekRelative(-10000L)
                        true
                    }
                    else -> {
                        seekRelative(10000L)
                        true
                    }
                }
            }

            private fun seekRelative(amount: Long) {
                val newPosition = when {
                    amount < 0 -> max(0, player.currentPosition + amount)
                    else -> min(player.duration, player.currentPosition + amount)
                }
                player.seekTo(newPosition)
                seekTimeTextView.text = formatTime(newPosition / 1000)
                seekTimeTextView.visibility = View.VISIBLE
                handler.removeCallbacks(hideSeekTimeRunnable)
                handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
                resetHideControlsTimer()
            }

            private fun resetHideControlsTimer() {
                handler.removeCallbacks(hideControlsRunnable)
                handler.postDelayed(hideControlsRunnable, hideControlsDelay)
            }
        })



        playerView.setOnTouchListener { _, event ->
            if (event.pointerCount == 1) {
                gestureDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP && isSpeedIncreased) {
                    player.playbackParameters = PlaybackParameters(1.0f)
                    isSpeedIncreased = false
                    twoxtimeTextview.visibility = View.GONE
                    handler.removeCallbacks(hideSeekTimeRunnable)
                    handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
                }
            }
            true
        }
    }


    private fun setupSeekBars() {
        videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    val seekPosition = (player.duration * progress) / 100
                    player.seekTo(seekPosition)
                    updateTimeDisplays()
                    seekTimeTextView.text = formatTime(seekPosition / 1000)
                    seekTimeTextView.visibility = View.VISIBLE
                    handler.removeCallbacks(hideSeekTimeRunnable)
                    handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
                    if (!isUsingEmbeddedSubtitles) {
                        updateSubtitles(seekPosition)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBarRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                handler.post(updateSeekBarRunnable)
            }
        })

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = progress / 10f
                    player.playbackParameters = PlaybackParameters(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightnessSeekBar.setOnProgressChangeListener { progress ->
            brightnessContainer.visibility = View.VISIBLE
            brightnessOverlay.visibility = View.VISIBLE
            brightnessText.text = "$progress%"
            val brightness = progress / 100f
            val lp = window.attributes
            lp.screenBrightness = if (brightness == 0f) 0.01f else brightness
            window.attributes = lp
            handler.postDelayed(hideBrightnessOverlayRunnable, 1000)
        }

        seekBarVolume.setOnProgressChangeListener { progress ->
            volumeContainer.visibility = View.VISIBLE
            brightnessOverlay.visibility = View.VISIBLE
            volumeText.text = "$progress%"
            tvVolumeValue.text = "$progress%"

            val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (progress <= 100) {
                player.volume = progress / 100f
                val systemVolume = (progress / 100f * maxSystemVolume).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                bassBoost?.setStrength(0)
            } else {
                player.volume = 1.0f
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
                val boostLevel = ((progress - 100) / 100f * 1000).toInt()
                bassBoost?.setStrength(boostLevel.toShort())
            }

            handler.postDelayed(hideVolumeRunnable, 1000)
        }
    }

    private fun setupFullScreenButton() {
        fullScreenButton.setOnClickListener { toggleFullScreen() }
    }

    private fun setupSubtitleButton() {
        subtitleButton.setOnClickListener {
            showSubtitleDialog()
        }
    }

    private fun setupAudioButton() {
        audioButton.setOnClickListener {
            showAudioDialog()
        }
    }

    private fun showSubtitleDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Subtitles")

        val options = mutableListOf<String>()
        val trackGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

        options.add("None (Disable subtitles)")
        trackGroups.forEachIndexed { index, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val language = format.language ?: "Unknown ($index)"
            options.add("Track: $language")
        }
        options.add("Load external subtitles")
        options.add("Customize subtitles")

        builder.setItems(options.toTypedArray()) { _, which ->
            when {
                which == 0 -> disableSubtitles()
                which <= trackGroups.size -> {
                    val selectedGroup = trackGroups[which - 1]
                    enableEmbeddedSubtitle(selectedGroup)
                }
                which == options.size - 2 -> pickSubtitle()
                which == options.size - 1 -> showSubtitleCustomizationDialog()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun showSubtitleCustomizationDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Customize Subtitles")

        val options = arrayOf(
            "Change Color",
            "Change Size",
            "Toggle Background",
            "Toggle Shadow"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showColorSelectionDialog()
                1 -> showSizeSeekBarDialog()
                2 -> toggleSubtitleBackground()
                3 -> toggleSubtitleShadow()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun showColorSelectionDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_color_picker)
        dialog.setTitle("Select Subtitle Color")

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        val colorView = dialog.findViewById<View>(R.id.colorPreview)
        val hueSeekBar = dialog.findViewById<SeekBar>(R.id.hueSeekBar)
        val saturationSeekBar = dialog.findViewById<SeekBar>(R.id.saturationSeekBar)
        val valueSeekBar = dialog.findViewById<SeekBar>(R.id.valueSeekBar)

        hueSeekBar.progressDrawable.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
        saturationSeekBar.progressDrawable.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
        valueSeekBar.progressDrawable.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)

        val hsv = FloatArray(3)
        Color.colorToHSV(currentSubtitleColor, hsv)
        hueSeekBar.progress = (hsv[0] * 100 / 360).toInt()
        saturationSeekBar.progress = (hsv[1] * 100).toInt()
        valueSeekBar.progress = (hsv[2] * 100).toInt()

        val updateColor = {
            hsv[0] = hueSeekBar.progress * 360f / 100
            hsv[1] = saturationSeekBar.progress / 100f
            hsv[2] = valueSeekBar.progress / 100f
            val color = Color.HSVToColor(hsv)
            colorView.setBackgroundColor(color)
        }

        hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColor()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColor()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        valueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColor()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.findViewById<Button>(R.id.btnOk).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF5722"))
            setOnClickListener {
                hsv[0] = hueSeekBar.progress * 360f / 100
                hsv[1] = saturationSeekBar.progress / 100f
                hsv[2] = valueSeekBar.progress / 100f
                currentSubtitleColor = Color.HSVToColor(hsv)
                updateSubtitleAppearance()
                dialog.dismiss()
            }
        }

        dialog.findViewById<Button>(R.id.btnCancel).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#607D8B"))
            setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun showSizeSelectionDialog() {
        val sizes = mapOf(
            "Small" to SUBTITLE_SIZE_SMALL,
            "Medium" to SUBTITLE_SIZE_DEFAULT,
            "Large" to SUBTITLE_SIZE_LARGE
        )

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Select Subtitle Size")
            .setItems(sizes.keys.toTypedArray()) { _, which ->
                currentSubtitleSize = sizes.values.elementAt(which)
                updateSubtitleAppearance()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
                }
            }
            .show()
    }

    private fun toggleSubtitleBackground() {
        currentSubtitleBackground = if (currentSubtitleBackground == Color.TRANSPARENT) {
            Color.argb(150, 0, 0, 0)
        } else {
            Color.TRANSPARENT
        }
        updateSubtitleAppearance()
    }

    private fun toggleSubtitleShadow() {
        currentSubtitleShadow = !currentSubtitleShadow
        updateSubtitleAppearance()
    }

    private fun updateSubtitleAppearance() {
        subtitleTextView.apply {
            setTextColor(currentSubtitleColor)
            textSize = currentSubtitleSize
            setBackgroundColor(currentSubtitleBackground)

            if (currentSubtitleShadow) {
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            } else {
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        baseSubtitleSize = currentSubtitleSize
    }
    private fun checkAudioEffectsSupport() {
        if (!isBassBoostSupported()) {
            Toast.makeText(this, "Volume boost not supported on this device", Toast.LENGTH_LONG).show()
            seekBarVolume.maxValue = 100 // Limit to normal volume range
        }
    }

    private fun isBassBoostSupported(): Boolean {
        return BassBoost(0, 0).run {
            val supported = hasControl()
            release()
            supported
        }
    }
    private fun showAudioDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Audio Tracks")

        val options = mutableListOf<String>()
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        if (audioGroups.isEmpty()) {
            options.add("No audio tracks available")
        } else {
            audioGroups.forEachIndexed { index, group ->
                val format = group.mediaTrackGroup.getFormat(0)
                val language = format.language ?: "Unknown"
                val channels = format.channelCount
                options.add("Track ${index + 1}: $language (${channels}ch)")
            }
        }

        builder.setItems(options.toTypedArray()) { _, which ->
            if (audioGroups.isNotEmpty() && which < audioGroups.size) {
                val selectedGroup = audioGroups[which]
                enableAudioTrack(selectedGroup)
                val format = selectedGroup.mediaTrackGroup.getFormat(0)
                Toast.makeText(this, "Switched to ${format.language} (${format.channelCount}ch)", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
    private fun showSizeSeekBarDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_subtitle_size)
        dialog.setTitle("Subtitle Size")

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        val seekBar = dialog.findViewById<SeekBar>(R.id.sizeSeekBar)
        val sizePreview = dialog.findViewById<TextView>(R.id.sizePreview)

        // Set initial progress based on current size
        val initialProgress = when (currentSubtitleSize) {
            SUBTITLE_SIZE_SMALL -> 0
            SUBTITLE_SIZE_DEFAULT -> 50
            SUBTITLE_SIZE_LARGE -> 100
            else -> ((currentSubtitleSize - 10) / 30 * 100).toInt().coerceIn(0, 100)
        }
        seekBar.progress = initialProgress

        // Update preview text size as seekbar changes
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSize = 10 + (progress / 100f * 30)  // Range 10-40sp
                sizePreview.textSize = newSize
                sizePreview.text = "Size: ${"%.1f".format(newSize)}sp"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.findViewById<Button>(R.id.btnOk).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF5722"))
            setOnClickListener {
                val newSize = 10 + (seekBar.progress / 100f * 30)  // Range 10-40sp
                currentSubtitleSize = newSize
                updateSubtitleAppearance()
                dialog.dismiss()
            }
        }

        dialog.findViewById<Button>(R.id.btnCancel).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#607D8B"))
            setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }
    private fun disableSubtitles() {
        subtitleTextView.visibility = View.GONE
        subtitles = emptyList()
        isUsingEmbeddedSubtitles = false
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    private fun enableEmbeddedSubtitle(group: Tracks.Group) {
        val parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()

        trackSelector.parameters = parameters
        isUsingEmbeddedSubtitles = true
        subtitleTextView.visibility = View.VISIBLE
    }

    private fun enableAudioTrack(group: Tracks.Group) {
        val parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()

        trackSelector.parameters = parameters
    }

    private fun launchVideoList() {
        val intent = Intent(this, VideoListActivity::class.java)
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }

    private fun pickSubtitle() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-subrip"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Subtitle File (.srt)"), PICK_SUBTITLE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_VIDEO_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    videoUri = data.data
                    if (videoUri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(videoUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {
                            Log.w("MainActivity", "Video permission not persistable: ${e.message}")
                        }
                        playVideo(videoUri!!)
                    } else {
                        Toast.makeText(this, "Failed to get video URI", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Video selection canceled", Toast.LENGTH_SHORT).show()
                }
            }
            PICK_SUBTITLE_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    subtitleUri = data.data
                    if (subtitleUri != null) {
                        loadSubtitles(subtitleUri!!)
                    } else {
                        Toast.makeText(this, "Failed to get subtitle URI", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Subtitle selection canceled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playVideo(uri: Uri) {
        try {
            Log.d("VideoURI", "Playing video from URI: $uri")
            val dataSourceFactory = DefaultDataSource.Factory(this)
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            player.setMediaSource(videoSource)
            player.prepare()
            player.playWhenReady = true
            applyScaleMode(VideoScaleMode.FIT)

            player.volume = 1.0f
            updateTimeDisplays()
            val videoTitle = getVideoTitleFromUri(uri)
            videoTitleTextView.text = videoTitle
            showControls()
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing video: ${e.message}", e)
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSubtitles(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "Failed to open subtitle file", Toast.LENGTH_SHORT).show()
                return
            }
            val reader = BufferedReader(InputStreamReader(inputStream))
            val subtitleList = mutableListOf<SubtitleEntry>()
            var index = 0
            var startTime = 0L
            var endTime = 0L
            val textBuilder = StringBuilder()

            reader.useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.trim().toIntOrNull() != null -> {
                            if (index > 0 && textBuilder.isNotEmpty()) {
                                subtitleList.add(SubtitleEntry(startTime, endTime, textBuilder.toString().trim()))
                                textBuilder.clear()
                            }
                            index = line.trim().toInt()
                        }
                        line.contains("-->") -> {
                            val times = line.split("-->").map { it.trim() }
                            if (times.size == 2) {
                                startTime = parseSrtTime(times[0])
                                endTime = parseSrtTime(times[1])
                            }
                        }
                        line.isNotBlank() -> textBuilder.append(line).append("\n")
                    }
                }
                if (textBuilder.isNotEmpty()) {
                    subtitleList.add(SubtitleEntry(startTime, endTime, textBuilder.toString().trim()))
                }
            }
            subtitles = subtitleList
            inputStream.close()
            isUsingEmbeddedSubtitles = false
            subtitleTextView.visibility = View.VISIBLE
            trackSelector.parameters = trackSelector.parameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            updateSubtitles(player.currentPosition)
            Toast.makeText(this, "Subtitles loaded (${subtitleList.size} entries)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Subtitles", "Failed to load subtitles: ${e.message}", e)
            Toast.makeText(this, "Failed to load subtitles: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseSrtTime(timeStr: String): Long {
        try {
            val parts = timeStr.split(":", ",")
            if (parts.size != 4) return 0L
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toLongOrNull() ?: 0L
            val milliseconds = parts[3].toLongOrNull() ?: 0L
            return (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + milliseconds
        } catch (e: Exception) {
            return 0L
        }
    }

    private fun updateSubtitles(position: Long) {
        if (!isUsingEmbeddedSubtitles && subtitles.isNotEmpty()) {
            val currentSubtitle = subtitles.find { position in it.startTime..it.endTime }
            subtitleTextView.text = currentSubtitle?.text ?: ""
            subtitleTextView.visibility = if (currentSubtitle != null) View.VISIBLE else View.GONE
        }
    }

    private fun getVideoTitleFromUri(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return cursor.getString(displayNameIndex) ?: "Unknown Video"
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown Video"
    }

    private fun setFullScreenMode(isFullScreen: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isFullScreen) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            fullScreenButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            fullScreenButton.setImageResource(android.R.drawable.ic_menu_zoom)


            playerView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(200)
                .start()

            subtitleTextView.textSize = baseSubtitleSize
        }
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        setFullScreenMode(isFullScreen)
    }

    private fun toggleControlsVisibility() {
        if (areControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        areControlsVisible = true
        bottomControls.visibility = View.VISIBLE
        fullScreenButton.visibility = View.VISIBLE
        subtitleButton.visibility = View.VISIBLE
        audioButton.visibility = View.VISIBLE
        videoTitleTextView.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, hideControlsDelay)
    }

    private fun hideControls() {
        areControlsVisible = false
        bottomControls.visibility = View.GONE
        fullScreenButton.visibility = View.GONE
        subtitleButton.visibility = View.GONE
        audioButton.visibility = View.GONE
        videoTitleTextView.visibility = View.GONE
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun updateTimeDisplays() {
        if (::player.isInitialized) {
            val currentPosition = player.currentPosition / 1000
            val totalDuration = player.duration / 1000
            leftTimeTextView.text = formatTime(currentPosition)
            rightTimeTextView.text = if (totalDuration > 0) formatTime(totalDuration) else "0:00"
        }
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.playWhenReady = false
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(hideSeekTimeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        bassBoost?.release()
        if (::player.isInitialized) {
            player.release()
        }
    }

    inner class SubTitleDragListener : View.OnTouchListener {
        private var initialX = 0f
        private var initialY = 0f
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val rootView = findViewById<View>(android.R.id.content)
            val screenWidth = rootView.width.toFloat()
            val screenHeight = rootView.height.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.measure(
                        View.MeasureSpec.makeMeasureSpec(screenWidth.toInt(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    v.x = initialX + (event.rawX - initialTouchX)
                    v.y = initialY + (event.rawY - initialTouchY)
                    v.x = max(0f, min(v.x, screenWidth - v.measuredWidth))
                    v.y = max(0f, min(v.y, screenHeight - v.measuredHeight))
                    v.visibility = View.VISIBLE
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    return true
                }
            }
            return false
        }
    }
    enum class VideoScaleMode {
        FILL, FIT, ORIGINAL, STRETCH,
        RATIO_16_9, RATIO_4_3, RATIO_18_9,
        RATIO_19_5_9, RATIO_20_9, RATIO_21_9
    }
    data class SubtitleEntry(val startTime: Long, val endTime: Long, val text: String)
}