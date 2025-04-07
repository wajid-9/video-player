package com.example.videoplayer

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
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
import android.content.SharedPreferences
import android.content.res.Configuration
import android.text.Spannable
import android.text.SpannableString
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    // Player and UI components
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var playerView: PlayerView
    private lateinit var videoSeekBar: SeekBar
    private lateinit var brightnessSeekBar: VerticalSeekBar
    private lateinit var seekBarVolume: VerticalSeekBar
    private lateinit var lockButton: LinearLayout
    private lateinit var playPauseButton: ImageButton
    private lateinit var rewindButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var speedButton: LinearLayout
    private lateinit var audioSubtitleButton: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var controlsLayout: LinearLayout
    private lateinit var centerControls: LinearLayout
    private lateinit var bottomButtons: LinearLayout
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
    private lateinit var aspectRatioButton: LinearLayout
    private lateinit var aspectRatioIcon: ImageView
    private lateinit var aspectRatioText: TextView
    private lateinit var speedText: TextView
    private lateinit var lockText: TextView
    private lateinit var lockIcon: ImageView
    private lateinit var audioSubtitleText: TextView
    private var currentScaleMode = VideoScaleMode.FIT
    private lateinit var continueTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var unlockIcon: ImageView
    private var currentVideoUri: Uri? = null
    private var showRemainingTime = false // Track whether to show remaining time
    // New UI components for brightness/volume controls
    private lateinit var brightnessContainer: RelativeLayout
    private lateinit var volumeContainer: RelativeLayout
    private lateinit var brightnessText: TextView
    private lateinit var volumeText: TextView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val minScale = 0.25f
    private val maxScale = 6.0f
    private var isZooming = false
    private var focusX = 0f
    private var focusY = 0f
    private lateinit var orientationLockButton: LinearLayout
    private lateinit var orientationLockIcon: ImageView
    private var isOrientationLocked = false
    // Gesture detectors
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private lateinit var gestureDetector: GestureDetectorCompat
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var zoomcontainer: RelativeLayout
    private lateinit var zoomtext: TextView
    private lateinit var back: ImageView

    private var currentSubtitleUri: Uri? = null
    // State variables
    private var isFullScreen = false
    private var isSpeedIncreased = false
    private var areControlsVisible = false
    private var isLocked = false
    private var baseSubtitleSize = 18f
    private val sensitivityFactor = 1.0f
    private val hideControlsDelay = 3000L
    private lateinit var skipDirectionTextView: TextView
    private val hideSkipDirectionRunnable = Runnable { skipDirectionTextView.visibility = View.GONE }
    private val hideZoomTextRunnable = Runnable { zoomcontainer.visibility = View.GONE }

    // Subtitle related
    private var subtitles: List<SubtitleEntry> = emptyList()
    private var isUsingEmbeddedSubtitles = false
    private var currentSubtitleColor = SUBTITLE_COLOR_DEFAULT
    private var currentSubtitleSize = SUBTITLE_SIZE_DEFAULT
    private var currentSubtitleBackground = Color.TRANSPARENT
    private var currentSubtitleShadow = true
    private val minSwipeDistance = 20f

    // Audio effect for volume boost
    private var bassBoost: BassBoost? = null

    // Request codes
    private val PICK_VIDEO_REQUEST = 1
    private val PICK_SUBTITLE_REQUEST = 2
    private val subtitleUris = mutableMapOf<Uri, Uri?>()
    // URIs
    private var videoUri: Uri? = null
    private var subtitleUri: Uri? = null

    companion object {
        private const val SUBTITLE_COLOR_DEFAULT = Color.YELLOW
        private const val SUBTITLE_COLOR_WHITE = Color.WHITE
        private const val SUBTITLE_COLOR_RED = Color.RED
        private const val SUBTITLE_COLOR_GREEN = Color.GREEN
        private const val SUBTITLE_COLOR_BLUE = Color.BLUE
        private const val SUBTITLE_SIZE_DEFAULT = 22f
        private const val SUBTITLE_SIZE_LARGE = 30f
        private const val SUBTITLE_SIZE_SMALL = 19f
    }

    private fun loadSubtitleSettings() {
        currentSubtitleColor = sharedPreferences.getInt("subtitle_color", SUBTITLE_COLOR_DEFAULT)
        currentSubtitleSize = sharedPreferences.getFloat("subtitle_size", SUBTITLE_SIZE_DEFAULT)
        currentSubtitleBackground = sharedPreferences.getInt("subtitle_background", Color.TRANSPARENT)
        currentSubtitleShadow = sharedPreferences.getBoolean("subtitle_shadow", true)
        currentSubtitleShadowIntensity = sharedPreferences.getInt("subtitle_shadow_intensity", 100) / 100f
        updateSubtitleAppearance()
    }

    private fun saveSubtitleSettings() {
        sharedPreferences.edit().apply {
            putInt("subtitle_color", currentSubtitleColor)
            putFloat("subtitle_size", currentSubtitleSize)
            putInt("subtitle_background", currentSubtitleBackground)
            putBoolean("subtitle_shadow", currentSubtitleShadow)
            putInt("subtitle_shadow_intensity", (currentSubtitleShadowIntensity * 100).toInt())
            apply()
        }
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
    private val hideContinueTextRunnable = Runnable {
        continueTextView.visibility = View.GONE
    }

    private fun resetHideControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, hideControlsDelay)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Set initial orientation to landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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

        sharedPreferences = getSharedPreferences("VideoPlaybackPrefs", MODE_PRIVATE)
        val savedSubtitleUris = sharedPreferences.all
            .filter { it.key.startsWith("subtitleUri_") }
            .mapNotNull { (key, value) ->
                try {
                    val videoUri = Uri.parse(key.removePrefix("subtitleUri_"))
                    val subtitleUri = Uri.parse(value.toString())
                    videoUri to subtitleUri
                } catch (e: Exception) {
                    null
                }
            }

        subtitleUris.putAll(savedSubtitleUris)
        initViews()
        loadSubtitleSettings()

        initPlayer() // Initialize player first

        // Load persisted speed and aspect ratio after player is initialized
        val savedSpeed = sharedPreferences.getFloat("playback_speed", 1.0f)
        player.playbackParameters = PlaybackParameters(savedSpeed)
        currentScaleMode = VideoScaleMode.values()[sharedPreferences.getInt("aspect_ratio_mode", VideoScaleMode.FIT.ordinal)]
        applyScaleMode(currentScaleMode)

        initGestureDetectors()
        setupSeekBars()
        setupNewButtons()

        playerView.setBackgroundColor(Color.BLACK)

        val videoUriFromIntent = intent.getStringExtra("VIDEO_URI")?.let { Uri.parse(it) }
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

        // Load saved subtitle URI for the current video if available
        videoUri?.let { currentVideoUri ->
            val savedSubtitleUriString = sharedPreferences.getString("subtitleUri_$currentVideoUri", null)
            if (savedSubtitleUriString != null) {
                try {
                    currentSubtitleUri = Uri.parse(savedSubtitleUriString)
                    contentResolver.takePersistableUriPermission(currentSubtitleUri!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    subtitleUris[currentVideoUri] = currentSubtitleUri
                    Log.d("MainActivity", "Loaded saved subtitle URI: $currentSubtitleUri")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to load saved subtitle URI", e)
                    sharedPreferences.edit().remove("subtitleUri_$currentVideoUri").apply()
                    subtitleUris.remove(currentVideoUri)
                    currentSubtitleUri = null
                }
            }
        }

        playerView.post {
            setFullScreenMode(true)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }
    }
    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        videoSeekBar = findViewById(R.id.videoSeekBar)
        playImageView = findViewById(R.id.playImageView)
        lockButton = findViewById(R.id.lockButton)
        lockIcon = lockButton.findViewById(R.id.lock_ic)
        playPauseButton = findViewById(R.id.playPauseButton)
        rewindButton = findViewById(R.id.rewindButton)
        forwardButton = findViewById(R.id.forwardButton)
        speedButton = findViewById(R.id.speedButton)
        audioSubtitleButton = findViewById(R.id.audioSubtitleButton)
        back=findViewById(R.id.back)
        setupBackButton()
        unlockIcon = findViewById(R.id.unlockIcon)
        rightTimeTextView = findViewById(R.id.righttime)
        setupUnlockIcon()
        rightTimeTextView.setOnClickListener { toggleRemainingTime() }
        unlockIcon.visibility = View.GONE
        skipDirectionTextView = findViewById(R.id.skipDirectionTextView)
        brightnessSeekBar = findViewById(R.id.BrightnessSeekBar)
        seekBarVolume = findViewById(R.id.VolumeSeekBar)
        bottomControls = findViewById(R.id.bottomControls)
        controlsLayout = findViewById(R.id.controlsLayout)
        centerControls = findViewById(R.id.centerControls)
        bottomButtons = findViewById(R.id.bottomButtons)
        leftTimeTextView = findViewById(R.id.lefttime)
        seekTimeTextView = findViewById(R.id.seekTimeTextView)
        twoxtimeTextview = findViewById(R.id.twoxTimeTextView)
        subtitleTextView = findViewById(R.id.subtitleTextView)
        videoTitleTextView = findViewById(R.id.videoTitleTextView)
        brightnessOverlay = findViewById(R.id.brightnessOverlay)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        brightnessContainer = findViewById(R.id.brightnessContainer)
        volumeContainer = findViewById(R.id.volumeContainer)
        brightnessText = findViewById(R.id.brightnessText)
        volumeText = findViewById(R.id.volumeText)
        zoomcontainer = findViewById(R.id.zoomContainer)
        zoomtext = findViewById(R.id.zoomText)
        continueTextView = findViewById(R.id.continueTextView)
        speedText = findViewById(R.id.speedText)
        lockText = findViewById(R.id.lockText)
        audioSubtitleText = findViewById(R.id.audioSubtitleText)
        aspectRatioButton = findViewById(R.id.aspectRatioButton)
        aspectRatioIcon = aspectRatioButton.findViewById(R.id.aspectRatioIcon)
        aspectRatioText = findViewById(R.id.aspectRatioText)
        orientationLockButton = findViewById(R.id.orientationLockButton)
        orientationLockIcon = orientationLockButton.findViewById(R.id.orientationLockIcon)
        orientationLockButton.setOnClickListener { toggleOrientationLock() }
        // Initial visibility setup
        bottomControls.visibility = View.GONE
        controlsLayout.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomButtons.visibility = View.GONE
        videoSeekBar.visibility = View.GONE
        subtitleTextView.visibility = View.GONE
        videoTitleTextView.visibility = View.GONE
        playImageView.visibility = View.GONE
        brightnessContainer.visibility = View.GONE
        volumeContainer.visibility = View.GONE
        brightnessOverlay.visibility = View.GONE
        tvVolumeValue.visibility = View.GONE
        continueTextView.visibility = View.GONE
        zoomcontainer.visibility = View.GONE
        skipDirectionTextView.visibility = View.GONE
        speedText.visibility = View.GONE
        lockButton.visibility = View.GONE
        lockText.visibility = View.GONE
        back.visibility=View.GONE
        audioSubtitleText.visibility = View.GONE
        aspectRatioText.visibility = View.GONE
        lockText.visibility = View.GONE
        // Initialize seek bars
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

        // Subtitle TextView setup
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
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.subtitle_bottom_margin)
            }
            bringToFront()
            setOnTouchListener(SubTitleDragListener())
        }

        playerView.visibility = View.VISIBLE
    }
    private fun updateOrientationLockIcon() {
        // Assuming you have an ImageView for the orientation lock icon
        orientationLockIcon.setImageResource(
            if (isOrientationLocked) android.R.drawable.ic_lock_lock
            else android.R.drawable.ic_lock_idle_lock
        )
    }

    private fun toggleOrientationLock() {
        isOrientationLocked = !isOrientationLocked
        if (isOrientationLocked) {
            // Lock to current orientation
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = when (currentOrientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            Toast.makeText(this, "Orientation locked", Toast.LENGTH_SHORT).show()
        } else {
            // Unlock orientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            Toast.makeText(this, "Orientation unlocked", Toast.LENGTH_SHORT).show()
        }
        updateOrientationLockIcon()
    }

    private fun showAspectRatioDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Select Aspect Ratio")

        val options = arrayOf(
            "Fill", "Fit", "Original", "Stretch",
            "16:9", "4:3", "18:9", "19.5:9", "20:9", "21:9"
        )

        builder.setItems(options) { _, which ->
            val mode = when (which) {
                0 -> VideoScaleMode.FILL
                1 -> VideoScaleMode.FIT
                2 -> VideoScaleMode.ORIGINAL
                3 -> VideoScaleMode.STRETCH
                4 -> VideoScaleMode.RATIO_16_9
                5 -> VideoScaleMode.RATIO_4_3
                6 -> VideoScaleMode.RATIO_18_9
                7 -> VideoScaleMode.RATIO_19_5_9
                8 -> VideoScaleMode.RATIO_20_9
                9 -> VideoScaleMode.RATIO_21_9
                else -> VideoScaleMode.FIT
            }
            applyScaleMode(mode)
            sharedPreferences.edit().putInt("aspect_ratio_mode", mode.ordinal).apply()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
    private fun applyScaleMode(mode: VideoScaleMode) {
        currentScaleMode = mode

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
                    contentFrame.setAspectRatio(16f / 9f)
                }
                VideoScaleMode.ORIGINAL -> {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val videoWidth = player.videoFormat?.width ?: 0
                    val videoHeight = player.videoFormat?.height ?: 0
                    if (videoWidth > 0 && videoHeight > 0) {
                        contentFrame.setAspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
                    }
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
            playerView.resizeMode = when (mode) {
                VideoScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoScaleMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                VideoScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekParameters(SeekParameters.EXACT)
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
                            playPauseButton.setImageResource(R.drawable.play)
                        } else {
                            playImageView.visibility = View.VISIBLE
                            playPauseButton.setImageResource(R.drawable.pause)
                        }

                        // Automatically enable the first embedded subtitle if available
                        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        if (textGroups.isNotEmpty() && !isUsingEmbeddedSubtitles) {
                            val selectedGroup = textGroups[0]
                            enableEmbeddedSubtitle(selectedGroup)
                            Toast.makeText(this@MainActivity, "Embedded subtitles enabled", Toast.LENGTH_SHORT).show()
                        }

                        // Log and configure audio tracks
                        configureAudioTrack()
                    }
                    Player.STATE_ENDED -> {
                        handler.removeCallbacks(updateSeekBarRunnable)
                        playImageView.visibility = View.VISIBLE
                        playPauseButton.setImageResource(R.drawable.pause)
                        videoUri?.let {
                            sharedPreferences.edit().remove(it.toString()).apply()
                        }
                    }
                    Player.STATE_BUFFERING -> Log.d("Player", "State BUFFERING")
                    Player.STATE_IDLE -> Log.d("Player", "State IDLE")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    handler.post(updateSeekBarRunnable)
                    playImageView.visibility = View.GONE
                    playPauseButton.setImageResource(R.drawable.play)
                } else {
                    handler.removeCallbacks(updateSeekBarRunnable)
                    playImageView.visibility = View.VISIBLE
                    playPauseButton.setImageResource(R.drawable.pause)
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
                    bassBoost?.release()
                    loudnessEnhancer?.release()
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        enabled = true
                        setStrength(0) // Start with minimal bass boost
                    }
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(1000) // Initial gain in millibels
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.e("BassBoost", "Failed to initialize audio effects: ${e.message}")
                    Toast.makeText(this@MainActivity, "Volume boost unavailable", Toast.LENGTH_SHORT).show()
                    player.volume = 1.5f
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d("Player", "Position discontinuity: $reason, oldPos=${oldPosition.positionMs}, newPos=${newPosition.positionMs}")
            }
        })

        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false) // Allow text tracks
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN) // Prefer main audio
            .setTunnelingEnabled(false) // Disable tunneling for better audio handling
            .build()
    }

    private fun configureAudioTrack() {
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isNotEmpty()) {
            Log.d("AudioTracks", "Available audio tracks: ${audioGroups.size}")
            audioGroups.forEachIndexed { index, group ->
                val format = group.mediaTrackGroup.getFormat(0)
                Log.d("AudioTracks", "Track $index: Language=${format.language}, Channels=${format.channelCount}, Role=${format.roleFlags}")
            }

            // Select the first audio track with downmixing enabled
            val selectedGroup = audioGroups.firstOrNull { group ->
                val format = group.mediaTrackGroup.getFormat(0)
                format.channelCount > 2 // Assume 5.1 if more than stereo
            } ?: audioGroups.first()

            enableAudioTrack(selectedGroup)


            val format = selectedGroup.mediaTrackGroup.getFormat(0)
            if (format.channelCount > 2) {
                loudnessEnhancer?.setTargetGain(2000)
            } else {
                loudnessEnhancer?.setTargetGain(1000)
            }
        } else {
            Log.w("AudioTracks", "No audio tracks available")
        }
    }
    private fun initGestureDetectors() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isLocked) {
                    toggleControlsVisibility()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isLocked || isZooming) return
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
                if (isLocked || e1 == null || player.duration <= 0) return false

                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) < minSwipeDistance && abs(deltaY) < minSwipeDistance) return false

                when {
                    abs(deltaX) > abs(deltaY) -> {
                        val seekDelta = (deltaX / screenWidth * player.duration * 0.1f).toLong()
                        val newPosition = max(0, min(player.duration, player.currentPosition + seekDelta))
                        if (player.playbackState == Player.STATE_READY) {
                            player.seekTo(newPosition)
                            seekTimeTextView.text = formatTime(newPosition / 1000)
                            seekTimeTextView.visibility = View.VISIBLE
                            resetHideControlsTimer()
                        } else {
                            Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
                        }
                    }
                    e1.x < screenWidth / 2 -> {
                        // Fade in if not already visible
                        if (brightnessContainer.visibility != View.VISIBLE) {
                            brightnessContainer.alpha = 0f
                            brightnessOverlay.alpha = 0f
                            brightnessContainer.visibility = View.VISIBLE
                            brightnessOverlay.visibility = View.VISIBLE
                            brightnessContainer.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                            brightnessOverlay.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                        }

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

                        // Schedule fade out
                        handler.removeCallbacks(hideBrightnessOverlayRunnable)
                        handler.postDelayed({
                            brightnessContainer.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    brightnessContainer.visibility = View.GONE
                                }
                                .start()
                            brightnessOverlay.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    brightnessOverlay.visibility = View.GONE
                                }
                                .start()
                        }, 1000)

                        resetHideControlsTimer()
                    }
                    else -> {
                        // Volume adjustment (apply similar fade animations if desired)
                        if (volumeContainer.visibility != View.VISIBLE) {
                            volumeContainer.alpha = 0f
                            brightnessOverlay.alpha = 0f
                            volumeContainer.visibility = View.VISIBLE
                            brightnessOverlay.visibility = View.VISIBLE
                            volumeContainer.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                            brightnessOverlay.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                        }

                        val normalizedDelta = -deltaY / screenHeight
                        val progressChange = (normalizedDelta * 100 * sensitivityFactor *
                                min(1f, abs(normalizedDelta) * 2)).toInt()
                        val newProgress = max(0, min(200, seekBarVolume.progress + progressChange))
                        seekBarVolume.progress = newProgress
                        volumeText.text = "$newProgress%"
                        tvVolumeValue.text = "$newProgress%"

                        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (newProgress <= 100) {
                            val volumeFraction = if (newProgress == 0) 0f else Math.pow(newProgress / 100.0, 2.0).toFloat()
                            player.volume = volumeFraction
                            val systemVolume = (volumeFraction * maxSystemVolume).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                            loudnessEnhancer?.setTargetGain(1000)
                            loudnessEnhancer?.enabled = true
                        } else {
                            player.volume = 1.0f
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
                            loudnessEnhancer?.setTargetGain(1500)
                            loudnessEnhancer?.enabled = true
                        }

                        handler.removeCallbacks(hideVolumeRunnable)
                        handler.postDelayed({
                            volumeContainer.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    volumeContainer.visibility = View.GONE
                                }
                                .start()
                            brightnessOverlay.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    brightnessOverlay.visibility = View.GONE
                                }
                                .start()
                        }, 1000)

                        resetHideControlsTimer()
                    }
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked || isZooming || player.duration <= 0) return false
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
                if (::player.isInitialized && player.duration > 0) {
                    val newPosition = max(0, min(player.duration, player.currentPosition + amount))
                    if (player.playbackState == Player.STATE_READY) {
                        player.seekTo(newPosition)
                        skipDirectionTextView.text = if (amount < 0) "-10s" else "+10s"
                        val params = skipDirectionTextView.layoutParams as RelativeLayout.LayoutParams
                        if (amount < 0) {
                            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                        } else {
                            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT)
                        }
                        skipDirectionTextView.layoutParams = params
                        skipDirectionTextView.visibility = View.VISIBLE

                        handler.removeCallbacks(hideSkipDirectionRunnable)
                        handler.postDelayed(hideSkipDirectionRunnable, 1000)
                    } else {
                        Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
                    }
                }
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (isLocked) return false
                isZooming = true
                focusX = detector.focusX
                focusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(minScale, min(scaleFactor, maxScale))

                playerView.videoSurfaceView?.apply {
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }

                updateZoomPercentage()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isZooming = false
            }

            private fun updateZoomPercentage() {
                val zoomPercent = (scaleFactor * 100).toInt()
                zoomtext.text = "$zoomPercent%"
                zoomcontainer.visibility = View.VISIBLE
                handler.removeCallbacks(hideZoomTextRunnable)
                handler.postDelayed(hideZoomTextRunnable, 1000)
            }
        })

        playerView.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener true

            scaleGestureDetector.onTouchEvent(event)

            if (event.pointerCount == 1) {
                gestureDetector.onTouchEvent(event)

                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        seekTimeTextView.visibility = View.GONE

                        if (isSpeedIncreased) {
                            player.playbackParameters = PlaybackParameters(1.0f)
                            isSpeedIncreased = false
                            twoxtimeTextview.visibility = View.GONE
                            handler.removeCallbacks(hideSeekTimeRunnable)
                        }
                    }
                }
            }
            true
        }
    }

    private fun setupSeekBars() {
        videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::player.isInitialized && player.duration > 0) {
                    val seekPosition = (player.duration * progress) / 100
                    if (player.playbackState == Player.STATE_READY) {
                        player.seekTo(seekPosition)
                        updateTimeDisplays()
                        seekTimeTextView.text = formatTime(seekPosition / 1000)
                        seekTimeTextView.visibility = View.VISIBLE
                        handler.removeCallbacks(hideSeekTimeRunnable)
                        handler.postDelayed(hideSeekTimeRunnable, hideControlsDelay)
                        if (!isUsingEmbeddedSubtitles) {
                            updateSubtitles(seekPosition)
                        }
                    } else {
                        Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
                        val currentProgress = if (player.duration > 0) {
                            (player.currentPosition * 100 / player.duration).toInt()
                        } else 0
                        videoSeekBar.progress = currentProgress
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                resetHideControlsTimer()
            }
        })

        brightnessSeekBar.setOnProgressChangeListener { progress ->
            // Fade in if not already visible
            if (brightnessContainer.visibility != View.VISIBLE) {
                brightnessContainer.alpha = 0f
                brightnessOverlay.alpha = 0f
                brightnessContainer.visibility = View.VISIBLE
                brightnessOverlay.visibility = View.VISIBLE
                brightnessContainer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
                brightnessOverlay.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }

            brightnessText.text = "$progress%"
            val brightness = progress / 100f
            val lp = window.attributes
            lp.screenBrightness = if (brightness == 0f) 0.01f else brightness
            window.attributes = lp

            // Schedule fade out
            handler.removeCallbacks(hideBrightnessOverlayRunnable)
            handler.postDelayed({
                brightnessContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        brightnessContainer.visibility = View.GONE
                    }
                    .start()
                brightnessOverlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        brightnessOverlay.visibility = View.GONE
                    }
                    .start()
            }, 1000)
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
                    loudnessEnhancer?.setTargetGain(1500)
                    loudnessEnhancer?.enabled = true
                }
            }
        })

        seekBarVolume.setOnProgressChangeListener { progress ->
            volumeContainer.visibility = View.VISIBLE
            brightnessOverlay.visibility = View.VISIBLE
            volumeText.text = "$progress%"
            tvVolumeValue.text = "$progress%"

            val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volumeFraction = progress / 100f

            if (progress <= 100) {
                player.volume = volumeFraction * 1.2f
                val systemVolume = (volumeFraction * maxSystemVolume).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                loudnessEnhancer?.setTargetGain(1000)
                loudnessEnhancer?.enabled = true
            } else {
                player.volume = 1.0f
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
                loudnessEnhancer?.setTargetGain(1500)
                loudnessEnhancer?.enabled = true
            }

            handler.postDelayed(hideVolumeRunnable, 1000)
        }
    }
    private fun setupBackButton() {
        back.setOnClickListener {
            val intent = Intent(this, VideoListActivity::class.java)
            // Optional: Add flags to clear the activity stack if you don't want to return to this activity
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            // Optional: Finish the current activity to remove it from the back stack
            finish()
        }
    }
    private fun setupNewButtons() {
        lockButton.setOnClickListener {
            toggleLock()
        }

        playPauseButton.setOnClickListener {
            player.playWhenReady = !player.playWhenReady
            resetHideControlsTimer()
        }

        rewindButton.setOnClickListener {
            seekRelative(-10000L)
            resetHideControlsTimer()
        }

        forwardButton.setOnClickListener {
            seekRelative(10000L)
            resetHideControlsTimer()
        }

        speedButton.setOnClickListener {
            showSpeedDialog()
            resetHideControlsTimer()
        }

        audioSubtitleButton.setOnClickListener {
            showAudioSubtitleDialog()
            resetHideControlsTimer()
        }

        aspectRatioButton.setOnClickListener {
            showAspectRatioDialog()
            resetHideControlsTimer()
        }
    }


    private fun toggleLock() {
        isLocked = !isLocked
        lockIcon.setImageResource(
            if (isLocked) android.R.drawable.ic_lock_lock
            else android.R.drawable.ic_lock_idle_lock
        )
        if (isLocked) {
            Toast.makeText(this, "Controls locked", Toast.LENGTH_SHORT).show()
            hideControls()
            // Fade in the unlock icon
            unlockIcon.alpha = 0f
            unlockIcon.visibility = View.VISIBLE
            unlockIcon.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            // Ensure lockButton is visible initially
            lockButton.visibility = View.VISIBLE
            lockText.visibility = View.VISIBLE
            handler.removeCallbacks(hideLockButtonRunnable)
            handler.postDelayed(hideLockButtonRunnable, hideControlsDelay)
        } else {
            Toast.makeText(this, "Controls unlocked", Toast.LENGTH_SHORT).show()
            showControls()
            // Fade out the unlock icon
            unlockIcon.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    unlockIcon.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showSpeedDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Select Playback Speed")

        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.40", "1.5x", "1.60", "1.75x", "2.0x")
        builder.setItems(speeds) { _, which ->
            val speed = when (which) {
                0 -> 0.25f
                1 -> 0.5f
                2 -> 0.75f
                3 -> 1.0f
                4 -> 1.25f
                5 -> 1.40f
                6 -> 1.5f
                7 -> 1.6f
                8 -> 1.75f
                9 -> 2.0f
                else -> 1.0f
            }
            player.playbackParameters = PlaybackParameters(speed)
            speedText.text = "Speed (${speeds[which]})"
            sharedPreferences.edit().putFloat("playback_speed", speed).apply()
            Toast.makeText(this, "Speed set to ${speeds[which]}", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
    private fun showAudioSubtitleDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Audio & Subtitles")

        val options = arrayOf("Audio Tracks", "Subtitles")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showAudioDialog()
                1 -> showSubtitleDialog()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun setFullScreenMode(isFullScreen: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, playerView)
        if (isFullScreen) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        this.isFullScreen = isFullScreen

        // Reset zoom and subtitle size when exiting full-screen mode
        if (!isFullScreen) {
            playerView.videoSurfaceView?.apply {
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
            }
            subtitleTextView.textSize = baseSubtitleSize
        }
    }
    private fun setupUnlockIcon() {
        unlockIcon.setOnClickListener {
            if (isLocked) {
                toggleLock() // Unlock the screen
            }
        }
    }
    private fun toggleControlsVisibility() {
        if (areControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }
    private val hideLockButtonRunnable = Runnable {
        lockButton.visibility = View.GONE
        lockText.visibility = View.GONE
    }
    private fun showControls() {
        areControlsVisible = true
        bottomControls.visibility = View.VISIBLE
        controlsLayout.visibility = View.VISIBLE
        centerControls.visibility = View.VISIBLE
        bottomButtons.visibility = View.VISIBLE
        videoSeekBar.visibility = View.VISIBLE
        videoTitleTextView.visibility = View.VISIBLE
        speedButton.visibility = View.VISIBLE
        lockButton.visibility = View.VISIBLE
        audioSubtitleButton.visibility = View.VISIBLE
        speedText.visibility = View.VISIBLE
        aspectRatioButton.visibility = View.VISIBLE
        lockText.visibility = View.VISIBLE
        back.visibility = View.VISIBLE
        audioSubtitleText.visibility = View.VISIBLE
        aspectRatioText.visibility = View.VISIBLE
        // Fade out the unlock icon
        unlockIcon.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                unlockIcon.visibility = View.GONE
            }
            .start()
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(hideLockButtonRunnable)
        handler.postDelayed(hideControlsRunnable, hideControlsDelay)
    }

    private fun hideControls() {
        areControlsVisible = false
        bottomControls.visibility = View.GONE
        controlsLayout.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomButtons.visibility = View.GONE
        videoSeekBar.visibility = View.GONE
        videoTitleTextView.visibility = View.GONE
        speedButton.visibility = View.GONE
        audioSubtitleButton.visibility = View.GONE
        speedText.visibility = View.GONE
        aspectRatioButton.visibility = View.GONE
        audioSubtitleText.visibility = View.GONE
        aspectRatioText.visibility = View.GONE
        back.visibility = View.GONE
        if (isLocked) {
            lockButton.visibility = View.VISIBLE
            lockText.visibility = View.VISIBLE
            lockIcon.visibility = View.VISIBLE
            unlockIcon.alpha = 1f // Ensure alpha is 1 in case an animation was interrupted
            unlockIcon.visibility = View.VISIBLE // Ensure unlock icon stays visible
            handler.postDelayed(hideLockButtonRunnable, hideControlsDelay)
        } else {
            lockButton.visibility = View.GONE
            lockText.visibility = View.GONE
            // Fade out the unlock icon
            unlockIcon.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    unlockIcon.visibility = View.GONE
                }
                .start()
        }
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun showSubtitleDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Subtitles")

        val options = mutableListOf<String>()
        val trackGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

        // Add "None" option first
        options.add("None (Disable subtitles)")

        // Add embedded tracks
        trackGroups.forEachIndexed { index, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val language = format.language ?: "Unknown ($index)"
            options.add("Embedded: $language")
        }

        // Add external subtitle option if available
        currentVideoUri?.let { videoUri ->
            subtitleUris[videoUri]?.let { subtitleUri ->
                val subtitleName = getVideoTitleFromUri(subtitleUri)
                options.add("External: ${subtitleName.substringAfterLast('.').take(10)}")
            }
        }

        // Always show "Load external subtitles" option
        options.add("Load external subtitles")
        options.add("Customize subtitles")

        builder.setItems(options.toTypedArray()) { _, which ->
            when {
                which == 0 -> disableSubtitles()
                which <= trackGroups.size -> {
                    val selectedGroup = trackGroups[which - 1]
                    enableEmbeddedSubtitle(selectedGroup)
                    isUsingEmbeddedSubtitles = true
                    Toast.makeText(this, "Embedded subtitles enabled", Toast.LENGTH_SHORT).show()
                }
                which == trackGroups.size + 1 && currentVideoUri?.let { subtitleUris[it] != null } == true -> {
                    currentVideoUri?.let { videoUri ->
                        subtitleUris[videoUri]?.let {
                            loadSubtitles(it)
                            isUsingEmbeddedSubtitles = false
                            Toast.makeText(this, "External subtitles enabled", Toast.LENGTH_SHORT).show()
                        }
                    }
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
            "Adjust Shadow Intensity"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showColorSelectionDialog()
                1 -> showSizeSeekBarDialog()
                2 -> toggleSubtitleBackground()
                3 -> showShadowIntensityDialog()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
    private fun showShadowIntensityDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_shadow_intensity)
        dialog.setTitle("Adjust Shadow Intensity")

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        val seekBar = dialog.findViewById<SeekBar>(R.id.shadowIntensitySeekBar) ?: return
        val intensityPreview = dialog.findViewById<TextView>(R.id.intensityPreview) ?: return

        // Load saved shadow intensity (default to 100% if not set)
        val savedShadowIntensity = sharedPreferences.getInt("subtitle_shadow_intensity", 100)
        seekBar.progress = savedShadowIntensity

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intensity = progress / 100f // Convert to 0.0 to 1.0
                intensityPreview.text = "Shadow Intensity: ${progress}%"
                currentSubtitleShadowIntensity = intensity
                updateSubtitleAppearance()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save the shadow intensity globally
                sharedPreferences.edit().putInt("subtitle_shadow_intensity", seekBar?.progress ?: 100).apply()
            }
        })

        // Initialize preview
        intensityPreview.text = "Shadow Intensity: ${savedShadowIntensity}%"

        dialog.findViewById<Button>(R.id.btnOk)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF5722"))
            setOnClickListener {
                sharedPreferences.edit().putInt("subtitle_shadow_intensity", seekBar.progress).apply()
                dialog.dismiss()
            }
        }

        dialog.findViewById<Button>(R.id.btnCancel)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#607D8B"))
            setOnClickListener { dialog.dismiss() }
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
    private var currentSubtitleShadowIntensity = 1.0f // Default to full intensity


    private fun showSizeSeekBarDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_subtitle_size)
        dialog.setTitle("Subtitle Size")

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        val seekBar = dialog.findViewById<SeekBar>(R.id.sizeSeekBar)
        val sizePreview = dialog.findViewById<TextView>(R.id.sizePreview)

        val initialProgress = when (currentSubtitleSize) {
            SUBTITLE_SIZE_SMALL -> 0
            SUBTITLE_SIZE_DEFAULT -> 50
            SUBTITLE_SIZE_LARGE -> 100
            else -> ((currentSubtitleSize - 10) / 30 * 100).toInt().coerceIn(0, 100)
        }
        seekBar.progress = initialProgress

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSize = 10 + (progress / 100f * 30)
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
                val newSize = 10 + (seekBar.progress / 100f * 30)
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

    private fun toggleSubtitleBackground() {
        currentSubtitleBackground = if (currentSubtitleBackground == Color.TRANSPARENT) {
            Color.argb(150, 0, 0, 0)
        } else {
            Color.TRANSPARENT
        }
        updateSubtitleAppearance()
    }



    private fun updateSubtitleAppearance() {
        subtitleTextView.apply {
            // Remove any existing shadow
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

            // Create a text border (stroke) effect
            val strokeWidth = 2f // Adjust border thickness as needed
            val strokeColor = Color.BLACK

            // Create a spannable string with stroke effect
            val text = text?.toString() ?: ""
            val spannable = SpannableString(text)
            spannable.setSpan(
                StrokeSpan(strokeColor, strokeWidth),
                0, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            setText(spannable, TextView.BufferType.SPANNABLE)
            setTextColor(currentSubtitleColor)
            textSize = currentSubtitleSize
            setBackgroundColor(currentSubtitleBackground)
        }
        baseSubtitleSize = currentSubtitleSize
        saveSubtitleSettings()
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

    private fun disableSubtitles() {
        subtitleTextView.visibility = View.GONE
        subtitles = emptyList()
        isUsingEmbeddedSubtitles = false
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        videoUri?.let { currentVideoUri ->
            subtitleUris.remove(currentVideoUri)
            sharedPreferences.edit().remove("subtitleUri_$currentVideoUri").apply()
        }
        currentSubtitleUri = null

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
                    val intent = Intent(this, VideoListActivity::class.java)
                    startActivity(intent)
                }
            }
            PICK_SUBTITLE_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    currentSubtitleUri = data.data
                    if (currentSubtitleUri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(currentSubtitleUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            videoUri?.let { currentVideoUri ->
                                subtitleUris[currentVideoUri] = currentSubtitleUri
                                sharedPreferences.edit().putString("subtitleUri_$currentVideoUri", currentSubtitleUri.toString()).apply()
                                Log.d("VideoPlayback", "Saved subtitle URI for $currentVideoUri: $currentSubtitleUri")
                                loadSubtitles(currentSubtitleUri!!)
                            }
                        } catch (e: SecurityException) {
                            Log.e("MainActivity", "Failed to take persistable permission for subtitle: ${e.message}")
                            Toast.makeText(this, "Failed to access subtitle: Permission denied", Toast.LENGTH_LONG).show()
                        }
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
            videoUri = uri
            currentVideoUri = uri
            Log.d("VideoPlayback", "Playing video: $uri")
            Log.d("VideoPlayback", "Checking subtitle for URI: $uri, subtitleUris: $subtitleUris")

            // Clear previous subtitles only if the video URI has changed
            if (currentVideoUri != uri) {
                subtitles = emptyList()
                subtitleTextView.visibility = View.GONE
                Log.d("VideoPlayback", "Cleared subtitles for new video")
            }

            currentSubtitleUri = subtitleUris[uri]
            if (currentSubtitleUri != null) {
                try {
                    loadSubtitles(currentSubtitleUri!!)
                    isUsingEmbeddedSubtitles = false
                    Log.d("VideoPlayback", "Loaded saved subtitles for $uri")
                } catch (e: Exception) {
                    Log.e("VideoPlayback", "Failed to load saved subtitles", e)
                    subtitleUris.remove(uri)
                    currentSubtitleUri = null
                }
            } else {
                // Disable subtitles if no saved ones found
                disableSubtitles()
            }
            val savedPosition = sharedPreferences.getLong(uri.toString(), 0L)
            if (savedPosition > 0) {
                continueTextView.text = "Continue from ${formatTime(savedPosition / 1000)}?"
                continueTextView.visibility = View.VISIBLE
                continueTextView.setOnClickListener {
                    player.seekTo(savedPosition)
                    player.playWhenReady = true
                    continueTextView.visibility = View.GONE
                }
                handler.postDelayed(hideContinueTextRunnable, 5000)
            }

            val dataSourceFactory = DefaultDataSource.Factory(this)
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .build()
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(videoSource)
            player.prepare()
            if (savedPosition <= 0) {
                player.playWhenReady = true
            }

            // Apply global speed and aspect ratio
            val savedSpeed = sharedPreferences.getFloat("playback_speed", 1.0f)
            player.playbackParameters = PlaybackParameters(savedSpeed)
            currentScaleMode = VideoScaleMode.values()[sharedPreferences.getInt("aspect_ratio_mode", VideoScaleMode.FIT.ordinal)]
            applyScaleMode(currentScaleMode)

            // Automatically load subtitles for this specific video if available
            currentSubtitleUri = subtitleUris[uri]
            Log.d("VideoPlayback", "Current subtitle URI for $uri: $currentSubtitleUri")
            if (currentSubtitleUri != null) {
                loadSubtitles(currentSubtitleUri!!)
                Log.d("VideoPlayback", "Loaded subtitles from $currentSubtitleUri")
            } else {
                disableSubtitles()
                Log.d("VideoPlayback", "No subtitle found for $uri, disabling subtitles")
            }

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
            videoUri?.let { currentVideoUri ->
                currentSubtitleUri = uri
                subtitleUris[currentVideoUri] = currentSubtitleUri
                sharedPreferences.edit()
                    .putString("subtitleUri_$currentVideoUri", uri.toString())
                    .apply()
                Log.d("Subtitles", "Saved subtitle URI for video $currentVideoUri")
            }
            updateSubtitles(player.currentPosition)
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
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        return cursor.getString(displayNameIndex) ?: "Unknown Subtitle"
                    }
                }
            }
            uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown Subtitle"
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission denied accessing URI: ${e.message}")
            "Unknown Subtitle (Permission Denied)"
        }
    }
    private fun updateTimeDisplays() {
        if (::player.isInitialized && player.duration > 0) {
            val currentPosition = player.currentPosition
            val duration = player.duration

            // Format time based on duration
            val currentTimeText = formatTime(currentPosition / 1000)
            val durationTimeText = formatTime(duration / 1000)

            leftTimeTextView.text = currentTimeText
            rightTimeTextView.text = if (showRemainingTime) {
                formatTime((duration - currentPosition) / 1000)
            } else {
                durationTimeText
            }
        }
    }

    private fun formatTime(timeInSeconds: Long): String {
        return if (timeInSeconds * 1000 > 60 * 60 * 1000) { // Greater than 60 minutes
            String.format("%d:%02d:%02d", timeInSeconds / 3600, (timeInSeconds % 3600) / 60, timeInSeconds % 60)
        } else {
            String.format("%02d:%02d", (timeInSeconds % 3600) / 60, timeInSeconds % 60)
        }
    }
    private fun toggleRemainingTime() {
        showRemainingTime = !showRemainingTime
        updateTimeDisplays()
    }
    private fun seekRelative(amount: Long) {
        val newPosition = max(0, min(player.duration, player.currentPosition + amount))
        if (player.playbackState == Player.STATE_READY) {
            player.seekTo(newPosition)
            skipDirectionTextView.text = if (amount < 0) "-10s" else "+10s"
            val params = skipDirectionTextView.layoutParams as RelativeLayout.LayoutParams
            if (amount < 0) {
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT)
            }
            skipDirectionTextView.layoutParams = params
            skipDirectionTextView.visibility = View.VISIBLE

            handler.removeCallbacks(hideSkipDirectionRunnable)
            handler.postDelayed(hideSkipDirectionRunnable, 1000)
        } else {
            Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            videoUri?.let {
                if (player.currentPosition > 0 && player.playbackState != Player.STATE_ENDED) {
                    sharedPreferences.edit()
                        .putLong(it.toString(), player.currentPosition)
                        .apply()
                }
            }
            player.playWhenReady = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(hideSeekTimeRunnable)
        handler.removeCallbacks(hideContinueTextRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && player.playWhenReady) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setFullScreenMode(isFullScreen)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(hideSkipDirectionRunnable)
        handler.removeCallbacks(hideContinueTextRunnable)
        bassBoost?.release()
        loudnessEnhancer?.release()
        if (::player.isInitialized) {
            player.release()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle orientation changes
        setFullScreenMode(isFullScreen)
        // Reapply the aspect ratio to ensure the video scales correctly
        applyScaleMode(currentScaleMode)
        // Update subtitle position if needed
        subtitleTextView.post {
            val params = subtitleTextView.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            subtitleTextView.layoutParams = params
        }
        unlockIcon.visibility = if (isLocked) View.VISIBLE else View.GONE
    }

    inner class SubTitleDragListener : View.OnTouchListener {
        private var initialX = 0f
        private var initialY = 0f
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (isLocked) return false

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
                    v.y = max(screenHeight * 0.5f, min(v.y, screenHeight - v.measuredHeight))
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