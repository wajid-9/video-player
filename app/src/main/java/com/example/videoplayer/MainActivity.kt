package com.example.videoplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioManager
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
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.provider.DocumentsContract
import android.text.Spannable
import android.text.SpannableString
import kotlin.math.abs
import kotlin.math.max
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.bumptech.glide.Glide
import com.example.videoplayer.VideoListActivity.VideoItem

import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var playerView: PlayerView
    private lateinit var videoSeekBar: SeekBar
    private lateinit var brightnessSeekBar: VerticalSeekBar
    private lateinit var seekBarVolume: VerticalSeekBar
    private lateinit var lockButton: LinearLayout
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
    private lateinit var playPauseButton: ImageView
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
    private lateinit var playImageView: ImageView
    private var currentVideoUri: Uri? = null
    private var showRemainingTime = false
    private lateinit var brightnessContainer: RelativeLayout
    private lateinit var volumeContainer: RelativeLayout
    private lateinit var speedTextContainer: RelativeLayout
    private lateinit var skipDirectionContainer: RelativeLayout
    private lateinit var brightnessText: TextView
    private lateinit var volumeText: TextView
    private lateinit var infoIcon: ImageView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val minScale = 0.25f
    private val maxScale = 6.0f
    private var isZooming = false
    private var focusX = 0f
    private var focusY = 0f
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isLoudnessEnhancerEnabled = true
    private var loudnessGain = 500
    private var audioSessionId = 0
    private lateinit var orientationLockButton: LinearLayout
    private lateinit var orientationLockIcon: ImageView
    private var isOrientationLocked = false
    private var hasSoughtToSavedPosition = false
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var zoomcontainer: RelativeLayout
    private lateinit var zoomtext: TextView
    private lateinit var back: ImageView
    private var currentTmdbData: TmdbMovie? = null // Store TMDB data for the current video
    private var currentSubtitleUri: Uri? = null
    private var isFullScreen = false
    private var isSpeedIncreased = false
    private var areControlsVisible = false
    private var isLocked = false
    private var baseSubtitleSize = 18f
    private val sensitivityFactor = 1.0f
    private val hideControlsDelay = 3000L
    private lateinit var skipDirectionTextView: TextView
    private val hideSkipDirectionRunnable = Runnable { skipDirectionTextView.visibility = View.GONE }
    private val hideZoomTextRunnable = Runnable {
        zoomtext.animate().alpha(0f).setDuration(200).withEndAction { zoomtext.visibility = View.GONE }.start()
    }
    private var subtitles: List<SubtitleEntry> = emptyList()
    private var isUsingEmbeddedSubtitles = false
    private var currentSubtitleColor = SUBTITLE_COLOR_DEFAULT
    private var currentSubtitleSize = SUBTITLE_SIZE_DEFAULT
    private var currentSubtitleBackground = Color.TRANSPARENT
    private var currentSubtitleShadow = true
    private val minSwipeDistance = 20f
    private var currentSubtitleStrokeColor = Color.BLACK
    private var currentSubtitleStrokeWidth = 10f
    private var currentSubtitleShadowIntensity = 1.0f

    private val PICK_VIDEO_REQUEST = 1
    private val PICK_SUBTITLE_REQUEST = 2
    private val subtitleUris = mutableMapOf<Uri, Uri?>()
    private var videoUri: Uri? = null

    companion object {
        private const val SUBTITLE_COLOR_DEFAULT = Color.YELLOW
        private const val SUBTITLE_SIZE_DEFAULT = 22f
        private const val SUBTITLE_SIZE_LARGE = 30f
        private const val SUBTITLE_SIZE_SMALL = 19f
    }

    private fun loadSubtitleSettings() {
        currentSubtitleColor = sharedPreferences.getInt("subtitle_color", SUBTITLE_COLOR_DEFAULT)
        currentSubtitleSize = sharedPreferences.getFloat("subtitle_size", SUBTITLE_SIZE_DEFAULT)
        currentSubtitleBackground = sharedPreferences.getInt("subtitle_background", Color.TRANSPARENT)
        currentSubtitleShadow = sharedPreferences.getBoolean("subtitle_shadow", true)
        currentSubtitleStrokeColor = sharedPreferences.getInt("subtitle_stroke_color", Color.BLACK)
        currentSubtitleStrokeWidth = sharedPreferences.getFloat("subtitle_stroke_width", 10f)
        currentSubtitleShadowIntensity = sharedPreferences.getInt("subtitle_shadow_intensity", 100) / 100f
        updateSubtitleAppearance()
    }

    private fun saveSubtitleSettings() {
        sharedPreferences.edit().apply {
            putInt("subtitle_color", currentSubtitleColor)
            putFloat("subtitle_size", currentSubtitleSize)
            putInt("subtitle_background", currentSubtitleBackground)
            putBoolean("subtitle_shadow", currentSubtitleShadow)
            putInt("subtitle_stroke_color", currentSubtitleStrokeColor)
            putFloat("subtitle_stroke_width", currentSubtitleStrokeWidth)
            putInt("subtitle_shadow_intensity", (currentSubtitleShadowIntensity * 100).toInt())
            apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                Log.d("SeekBar", "Player state: isPlaying=${player.isPlaying}, playbackState=${player.playbackState}")
                if (player.isPlaying) {
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
            } else {
                Log.w("SeekBar", "Player not initialized")
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
        Log.d("MainActivity", "onCreate called: savedInstanceState=$savedInstanceState, intent=${intent.extras?.keySet()}")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Request runtime permissions
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
        initPlayer()

        val savedSpeed = sharedPreferences.getFloat("playback_speed", 1.0f)
        player.playbackParameters = PlaybackParameters(savedSpeed)
        currentScaleMode = VideoScaleMode.values()[sharedPreferences.getInt("aspect_ratio_mode", VideoScaleMode.FIT.ordinal)]
        applyScaleMode(currentScaleMode)

        initGestureDetectors()
        setupSeekBars()
        setupNewButtons()

        playerView.setBackgroundColor(Color.BLACK)

        val videoUriFromIntent = intent.getStringExtra("VIDEO_URI")?.let { Uri.parse(it) }
        val seriesId = intent.getIntExtra("SERIES_ID", -1)
        val seasonNumber = intent.getIntExtra("SEASON_NUMBER", 1)
        val episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 1)
        Log.d("MainActivity", "Received Intent: seriesId=$seriesId, season=$seasonNumber, episode=$episodeNumber")

        // Handle video playback and title update
        if (videoUriFromIntent != null) {
            videoUri = videoUriFromIntent
            currentVideoUri = videoUri
            playVideoAndUpdateTitle(videoUri!!, seriesId, seasonNumber, episodeNumber)
        } else if (savedInstanceState != null && savedInstanceState.getParcelable<Uri>("videoUri") != null) {
            videoUri = savedInstanceState.getParcelable("videoUri")
            currentVideoUri = videoUri
            if (videoUri != null) {
                Log.d("MainActivity", "Restoring from saved state: videoUri=$videoUri")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission required to play video", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                playVideoAndUpdateTitle(videoUri!!, seriesId, seasonNumber, episodeNumber)
            }
        } else {
            Log.d("MainActivity", "No video URI, launching video list")
            launchVideoList()
        }

        videoUri?.let { currentVideoUri ->
            val savedSubtitleUriString = sharedPreferences.getString("subtitleUri_$currentVideoUri", null)
            if (savedSubtitleUriString != null) {
                try {
                    currentSubtitleUri = Uri.parse(savedSubtitleUriString)
                    playVideoAndUpdateTitle(currentVideoUri, seriesId, seasonNumber, episodeNumber) // Ensure video reloads with subtitle
                    loadSubtitles(currentSubtitleUri!!)
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
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        }
    }
    // New helper function to encapsulate playback and title update
    private fun playVideoAndUpdateTitle(uri: Uri, seriesId: Int, seasonNumber: Int, episodeNumber: Int) {
        playVideo(uri)
        updateVideoTitle(uri, seriesId, seasonNumber, episodeNumber)
    }
    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        videoSeekBar = findViewById(R.id.videoSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        lockButton = findViewById(R.id.lockButton)
        lockIcon = lockButton.findViewById(R.id.lock_ic)
        rewindButton = findViewById(R.id.rewindButton)
        forwardButton = findViewById(R.id.forwardButton)
        speedButton = findViewById(R.id.speedButton)
        audioSubtitleButton = findViewById(R.id.audioSubtitleButton)
        back = findViewById(R.id.back)
        setupBackButton()
        speedTextContainer = findViewById(R.id.speedTextContainer)
        skipDirectionContainer = findViewById(R.id.skipDirectionContainer)
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
        playImageView = findViewById(R.id.playImageView)
        speedText = findViewById(R.id.speedText)
        lockText = findViewById(R.id.lockText)
        audioSubtitleText = findViewById(R.id.audioSubtitleText)
        aspectRatioButton = findViewById(R.id.aspectRatioButton)
        aspectRatioIcon = aspectRatioButton.findViewById(R.id.aspectRatioIcon)
        aspectRatioText = findViewById(R.id.aspectRatioText)
        orientationLockButton = findViewById(R.id.orientationLockButton)
        orientationLockIcon = orientationLockButton.findViewById(R.id.orientationLockIcon)
        orientationLockButton.setOnClickListener { toggleOrientationLock() }
         infoIcon = findViewById(R.id.infoIcon)
        playPauseButton.setOnClickListener { togglePlayPause() }
        bottomControls.visibility = View.GONE
        controlsLayout.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomButtons.visibility = View.GONE
        videoSeekBar.visibility = View.GONE
        subtitleTextView.visibility = View.GONE
        videoTitleTextView.visibility = View.GONE
        brightnessContainer.visibility = View.GONE
        volumeContainer.visibility = View.GONE
        brightnessOverlay.visibility = View.GONE
        tvVolumeValue.visibility = View.GONE
        continueTextView.visibility = View.GONE
        zoomcontainer.visibility = View.VISIBLE
        skipDirectionTextView.visibility = View.GONE
        zoomtext.visibility = View.GONE
        speedText.visibility = View.GONE
        lockButton.visibility = View.GONE
        lockText.visibility = View.GONE
        audioSubtitleText.visibility = View.GONE
        aspectRatioText.visibility = View.GONE
        back.visibility = View.GONE

        brightnessSeekBar.maxValue = 100
        seekBarVolume.maxValue = 200
        val initialSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val initialProgress = if (maxSystemVolume > 0) {
            (initialSystemVolume.toFloat() / maxSystemVolume * 100).toInt()
        } else 0

        seekBarVolume.progress = initialProgress
        brightnessSeekBar.progress = 50
        brightnessText.text = "50%"
        volumeText.text = "$initialProgress%"
        tvVolumeValue.text = "$initialProgress%"
        scaleFactor = sharedPreferences.getFloat("zoom_scale_factor", 1.0f)
        playerView.scaleX = scaleFactor
        playerView.scaleY = scaleFactor

        subtitleTextView.apply {
            setTextColor(currentSubtitleColor)
            textSize = currentSubtitleSize
            maxLines = 3
            setBackgroundColor(currentSubtitleBackground)
            if (currentSubtitleShadow) {
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
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
        infoIcon.setOnClickListener {
            currentVideoUri?.let { uri ->
                val title = getVideoTitleFromUri(uri)
                val videoItem = VideoItem(
                    uri = uri,
                    title = title,
                    id = uri.hashCode().toLong(),
                    duration = player.duration.takeIf { it > 0 } ?: 0L,
                    dateAdded = System.currentTimeMillis(),
                    path = uri.path ?: "",
                    isFavorite = false,
                    isHeader = false,
                    isSeriesHeader = false,
                    groupCount = 0,
                    tmdbData = currentTmdbData
                )
                Log.d("TMDb", "Info button clicked: title='$title', tmdbData=${currentTmdbData?.displayTitle}")
                showTmdbData(videoItem)
            } ?: run {
                Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showTmdbData(video: VideoItem) {
        if (video.isHeader || video.isSeriesHeader) {
            Toast.makeText(this, "Metadata not available for headers", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val tmdbData = video.tmdbData ?: run {
                    val (cleanedName, isTvShow, seasonEpisode) = cleanTitleForTmdb(video.title)
                    TmdbClient.getMediaData(cleanedName, isTvShow)?.also {
                        if (isTvShow && seasonEpisode != null) {
                            val episodeData = TmdbClient.getEpisodeData(it.seriesId ?: return@also, seasonEpisode.first, seasonEpisode.second)
                            if (episodeData != null) {
                                it.season = episodeData.season_number
                                it.episode = episodeData.episode_number
                                it.displayTitle = episodeData.name ?: it.displayTitle
                            }
                        }
                    }
                }

                val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_tmdb_data, null)
                val dialog = AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialog)
                    .setTitle("Metadata")
                    .setView(dialogView)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .create()

                if (tmdbData != null) {
                    dialogView.findViewById<TextView>(R.id.tmdb_title).text = tmdbData.displayTitle
                    dialogView.findViewById<TextView>(R.id.tmdb_type).text =
                        if (tmdbData.media_type == "tv") "TV Show" else "Movie"

                    if (tmdbData.season != null && tmdbData.episode != null) {
                        dialogView.findViewById<TextView>(R.id.tmdb_season_episode).apply {
                            text = "Season ${tmdbData.season}, Episode ${tmdbData.episode}"
                            visibility = View.VISIBLE
                        }
                    } else {
                        dialogView.findViewById<TextView>(R.id.tmdb_season_episode).visibility = View.GONE
                    }

                    dialogView.findViewById<TextView>(R.id.tmdb_release_date).text =
                        "Release Date: ${tmdbData.displayDate ?: "Not available"}"

                    dialogView.findViewById<TextView>(R.id.tmdb_rating).text =
                        tmdbData.vote_average?.let { "${String.format("%.1f", it)}/10" } ?: "Not rated"

                    dialogView.findViewById<TextView>(R.id.tmdb_overview).text =
                        tmdbData.overview?.takeIf { it.isNotBlank() } ?: "No description available"

                    val posterImageView = dialogView.findViewById<ImageView>(R.id.tmdb_poster)
                    if (tmdbData.poster_path?.isNotEmpty() == true) {
                        val imageUrl = "${TmdbClient.IMAGE_BASE_URL}${tmdbData.poster_path}"
                        Log.d("TMDb", "Loading dialog poster for '${tmdbData.displayTitle}': $imageUrl")
                        Glide.with(this@MainActivity)
                            .load(imageUrl)
                            .placeholder(R.drawable.placeholder)
                            .error(R.drawable.placeholder)
                            .dontAnimate()
                            .into(posterImageView)
                    } else {
                        Log.d("TMDb", "No poster for '${tmdbData.displayTitle}', using placeholder")
                        Glide.with(this@MainActivity)
                            .load(R.drawable.placeholder)
                            .dontAnimate()
                            .into(posterImageView)
                    }
                } else {
                    dialogView.findViewById<TextView>(R.id.tmdb_title).text = video.title
                    dialogView.findViewById<TextView>(R.id.tmdb_type).text = "Unknown"
                    dialogView.findViewById<TextView>(R.id.tmdb_season_episode).visibility = View.GONE
                    dialogView.findViewById<TextView>(R.id.tmdb_release_date).text = "Release Date: Not available"
                    dialogView.findViewById<TextView>(R.id.tmdb_rating).text = "Not rated"
                    dialogView.findViewById<TextView>(R.id.tmdb_overview).text = "No metadata available for this video."
                    dialogView.findViewById<ImageView>(R.id.tmdb_poster).setImageResource(R.drawable.placeholder)
                }

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                }
                dialog.show()
            } catch (e: Exception) {
                Log.e("TMDb", "Error showing TMDB data: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error retrieving metadata", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun updateOrientationLockIcon() {
        orientationLockIcon.setImageResource(
            if (isOrientationLocked) android.R.drawable.ic_lock_lock
            else android.R.drawable.ic_lock_idle_lock
        )
    }
    private fun showEpisodeInfoDialog(seriesId: Int, seasonNumber: Int = 1, episodeNumber: Int = 1) {
        Log.d("Dialog", "showEpisodeInfoDialog called with seriesId=$seriesId, season=$seasonNumber, episode=$episodeNumber")
        CoroutineScope(Dispatchers.IO).launch {
            val episode = if (seriesId != -1 && seasonNumber > 0 && episodeNumber > 0) {
                try {
                    TmdbClient.getEpisodeData(seriesId, seasonNumber, episodeNumber)
                } catch (e: Exception) {
                    Log.e("Tmdb", "Failed to fetch episode data: ${e.message}")
                    null
                }
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialog)
                builder.setTitle("Episode Info")
                builder.setMessage(
                    episode?.let {
                        "Title: ${it.name ?: "Unknown"}\n" +
                                "Overview: ${it.overview ?: "No overview available"}\n" +
                                "Air Date: ${it.air_date ?: "N/A"}\n" +
                                "Rating: ${it.vote_average ?: "N/A"}"
                    } ?: "Failed to load episode information or invalid input."
                )
                builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                }
                dialog.show()
            }
        }
    }
    private fun toggleOrientationLock() {
        isOrientationLocked = !isOrientationLocked
        if (isOrientationLocked) {
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = when (currentOrientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            Toast.makeText(this, "Orientation locked", Toast.LENGTH_SHORT).show()
        } else {
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

    private fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            playPauseButton.setImageResource(R.drawable.pause)
            playImageView.visibility = View.GONE
        } else {
            player.play()
            playPauseButton.setImageResource(R.drawable.play)
            playImageView.visibility = View.GONE
            updateVolume(seekBarVolume.progress)
        }
        handler.post(updateSeekBarRunnable)
        updateSubtitles(player.currentPosition)
        resetHideControlsTimer()
    }
    private fun cleanTitleForTmdb(title: String): Triple<String, Boolean, Pair<Int, Int>?> {
        // Remove file extension
        var cleaned = title.substringBeforeLast(".").trim()

        // Remove common video metadata patterns
        cleaned = cleaned.replace(Regex("(?i)(S\\d{1,2}E\\d{1,2}|\\d{3,4}p|BluRay|WEBRip|HDRip|x264|x265|h264|h265|AAC|DDP|DD5\\.1|TrueHD|Atmos|HEVC|AVC|10bit|8bit|1080i|2160p|480p|720p|4K|UHD|SD|HD|Rip|REPACK|PROPER|EXTENDED|UNRATED|REMASTERED|DIRECTORS\\.CUT|DC|\\d+MB|AMZN|NF|DSNP|HMAX|HBO|BBC|ITV|DISNEY|CRITERION|IMAX|MULTI|LiNE|AAC5\\.1|AC3|MP4|MKV|Hi10P|DTS|DTS-HD|MA\\d\\.\\d|SUBBED|DUBBED|\\[.*?\\]|\\(.*?\\)|\\{.*?\\})"), "").trim()

        // Remove release groups and other tags
        cleaned = cleaned.replace(Regex("(?i)(HETeam|YTS|PSA|Pahe|EZTV|EVO|TiGole|GalaxyRG|ELiTE|SPARKS|GOSSIP|DRONES|ION10|MeGusta|AFG|CMRG|FLUX|LAZY|BTX|RMTeam|SSRMovies|KOGi|DiAMOND|CiNEFiLE|HiVE|SHiNiGAMi|BAE|BRRip|WEB-DL|DDP5\\.1)"), "").trim()

        // Remove years (e.g., "(2023)") and extra spaces
        cleaned = cleaned.replace(Regex("\\(\\d{4}\\)|\\b\\d{4}\\b"), "").trim()

        // Replace special characters and normalize spaces
        cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

        // Stop at season/episode if present to avoid including extra text
        val seasonEpisodeMatch = Regex("(?i)S(\\d{1,2})E(\\d{1,2})").find(title)
        if (seasonEpisodeMatch != null) {
            val index = seasonEpisodeMatch.range.first
            cleaned = title.substring(0, index).substringBeforeLast(".").trim()
            // Reapply cleaning steps to the portion before SxxExx
            cleaned = cleaned.replace(Regex("\\(\\d{4}\\)|\\b\\d{4}\\b|\\[.*?\\]|\\{.*?\\}"), "").trim()
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        }

        // Detect if it's a TV show
        val isTvShow = seasonEpisodeMatch != null
        val seasonEpisode = seasonEpisodeMatch?.let {
            Pair(it.groups[1]!!.value.toInt(), it.groups[2]!!.value.toInt())
        }

        // Fallback: If cleaned title is empty, use original title without extension
        val finalCleaned = if (cleaned.isBlank()) {
            title.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        } else {
            cleaned
        }

        Log.d("TMDb", "Cleaned title: raw='$title', cleaned='$finalCleaned', isTvShow=$isTvShow, seasonEpisode=$seasonEpisode")
        return Triple(finalCleaned, isTvShow, seasonEpisode)
    }
    private fun updateVideoTitle(videoUri: Uri, seriesId: Int = -1, seasonNumber: Int = 1, episodeNumber: Int = 1) {
        val title = getVideoTitleFromUri(videoUri)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (cleanedName, isTvShow, seasonEpisode) = cleanTitleForTmdb(title)
                val tmdbData = TmdbClient.getMediaData(cleanedName, isTvShow)?.also {
                    if (isTvShow && (seriesId != -1 || seasonEpisode != null)) {
                        val season = if (seriesId != -1) seasonNumber else seasonEpisode?.first ?: 1
                        val episode = if (seriesId != -1) episodeNumber else seasonEpisode?.second ?: 1
                        val seriesIdToUse = seriesId.takeIf { it != -1 } ?: it.seriesId ?: return@also
                        Log.d("TMDb", "Fetching episode data: seriesId=$seriesIdToUse, season=$season, episode=$episode")
                        val episodeData = TmdbClient.getEpisodeData(seriesIdToUse, season, episode)
                        if (episodeData != null) {
                            it.season = episodeData.season_number
                            it.episode = episodeData.episode_number
                            it.displayTitle = episodeData.name ?: it.displayTitle
                            Log.d("TMDb", "Episode data fetched: name=${episodeData.name}, season=${episodeData.season_number}, episode=${episodeData.episode_number}")
                        } else {
                            Log.w("TMDb", "Episode data not found for seriesId=$seriesIdToUse, season=$season, episode=$episode")
                        }
                    } else {
                        Log.d("TMDb", "Not a TV show or no season/episode data: isTvShow=$isTvShow, seasonEpisode=$seasonEpisode")
                    }
                }
                withContext(Dispatchers.Main) {
                    currentTmdbData = tmdbData
                    val displayText = when {
                        tmdbData != null && tmdbData.media_type == "tv" && tmdbData.season != null && tmdbData.episode != null -> {
                            val seasonStr = String.format("S%02d", tmdbData.season)
                            val episodeStr = String.format("E%02d", tmdbData.episode)
                            "${tmdbData.displayTitle}-$seasonStr$episodeStr"
                        }
                        tmdbData != null -> tmdbData.displayTitle ?: title
                        else -> title
                    }
                    videoTitleTextView.text = displayText
                    videoTitleTextView.visibility = View.VISIBLE
                    Log.d("TMDb", "Title set to: $displayText, tmdbData=$tmdbData")
                }
            } catch (e: Exception) {
                Log.e("TMDb", "Failed to update video title: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    currentTmdbData = null
                    videoTitleTextView.text = title
                    videoTitleTextView.visibility = View.VISIBLE
                }
            }
        }
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

    private fun updateVolume(progress: Int) {
        try {
            val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volumeFraction = if (progress == 0) 0f else (progress / 100f).coerceIn(0f, 2f)
            val adjustedVolume = (volumeFraction * volumeFraction) / 1.5f
            val playerVolume = (adjustedVolume * 1.3f).coerceIn(0f, 1f)
            player.volume = playerVolume
            val systemVolume = (adjustedVolume * maxSystemVolume * 1.5f).toInt().coerceIn(0, maxSystemVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)

            loudnessEnhancer?.let { enhancer ->
                if (isLoudnessEnhancerEnabled && progress > 100 && loudnessGain > 0) {
                    enhancer.setTargetGain(loudnessGain.coerceIn(0, 1000))
                    enhancer.enabled = true
                    Log.d("Audio", "LoudnessEnhancer enabled: gain=$loudnessGain cB")
                } else {
                    enhancer.enabled = false
                    Log.d("Audio", "LoudnessEnhancer disabled: progress=$progress, gain=$loudnessGain")
                }
            } ?: run {
                Log.w("Audio", "LoudnessEnhancer not initialized")
            }

            volumeText.text = "$progress%"
            tvVolumeValue.text = "$progress%"
            Log.d("Audio", "Volume set: progress=$progress, playerVolume=$playerVolume, systemVolume=$systemVolume")
        } catch (e: Exception) {
            Log.e("Audio", "Error updating volume: ${e.message}", e)
            val volumeFraction = (progress / 100f).coerceIn(0f, 2f)
            val adjustedVolume = (volumeFraction * volumeFraction) / 1.5f
            val playerVolume = (adjustedVolume * 1.3f).coerceIn(0f, 1f)
            player.volume = playerVolume
            loudnessEnhancer?.enabled = false
        }
    }
    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekParameters(SeekParameters.EXACT)
            .build()
        playerView.player = player
        playerView.useController = false
        playerView.subtitleView?.visibility = View.GONE

        try {
            audioSessionId = player.audioSessionId
            if (audioSessionId != AudioManager.ERROR) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                loudnessGain = sharedPreferences.getInt("loudness_gain", 500)
                isLoudnessEnhancerEnabled = sharedPreferences.getBoolean("loudness_enhancer_enabled", true)
                loudnessEnhancer?.enabled = isLoudnessEnhancerEnabled
                loudnessEnhancer?.setTargetGain(loudnessGain.coerceIn(0, 3000))
                Log.d("Audio", "LoudnessEnhancer initialized with audioSessionId=$audioSessionId, gain=$loudnessGain")
            } else {
                Log.w("Audio", "Invalid audio session ID, LoudnessEnhancer not initialized")
            }
        } catch (e: Exception) {
            Log.e("Audio", "Failed to initialize LoudnessEnhancer: ${e.message}", e)
            loudnessEnhancer = null
        }

        enhanceVideoDepth()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val currentProgress = seekBarVolume.progress
                        updateVolume(currentProgress)
                        val savedPosition = videoUri?.let { sharedPreferences.getLong(it.toString(), 0L) } ?: 0L
                        if (savedPosition > 0 && !hasSoughtToSavedPosition) {
                            player.seekTo(savedPosition)
                            player.playWhenReady = true
                            hasSoughtToSavedPosition = true
                            Log.d("VideoPlayback", "Player ready, sought to $savedPosition ms (initial seek)")
                            if (!isUsingEmbeddedSubtitles) {
                                updateSubtitles(savedPosition)
                            }
                        } else {
                            Log.d("VideoPlayback", "Player ready, no seek needed (position=${player.currentPosition} ms)")
                            if (!isUsingEmbeddedSubtitles) {
                                updateSubtitles(player.currentPosition)
                            }
                        }
                        handler.post(updateSeekBarRunnable)

                        if (currentSubtitleUri == null && !isUsingEmbeddedSubtitles) {
                            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                            if (textGroups.isNotEmpty()) {
                                val selectedGroup = textGroups[0]
                                enableEmbeddedSubtitle(selectedGroup)
                                isUsingEmbeddedSubtitles = true
                            }
                        } else if (currentSubtitleUri != null && !isUsingEmbeddedSubtitles) {
                            loadSubtitles(currentSubtitleUri!!)
                            Log.d("VideoPlayback", "Reapplied external subtitles on state ready")
                        }

                        configureAudioTrack()
                    }
                    Player.STATE_ENDED -> {
                        handler.removeCallbacks(updateSeekBarRunnable)
                        videoUri?.let {
                            sharedPreferences.edit().remove(it.toString()).apply()
                        }
                        hasSoughtToSavedPosition = false
                    }
                    Player.STATE_BUFFERING -> Log.d("Player", "State BUFFERING")
                    Player.STATE_IDLE -> Log.d("Player", "State IDLE")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    handler.post(updateSeekBarRunnable)
                    playPauseButton.setImageResource(R.drawable.play)
                    playImageView.visibility = View.GONE
                    updateVolume(seekBarVolume.progress)
                } else {
                    handler.removeCallbacks(updateSeekBarRunnable)
                    if (reason != Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                        playImageView.visibility = View.VISIBLE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("Player", "Playback error: ${error.message}", error)
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCues(cues: MutableList<Cue>) {
                if (isUsingEmbeddedSubtitles) {
                    Log.d("Subtitles", "Received ${cues.size} embedded cues at position=${player.currentPosition}")
                    if (cues.isNotEmpty()) {
                        val cueText = cues.joinToString("\n") { it.text?.toString() ?: "" }
                        val cleanText = cleanSubtitleText(cueText)
                        val spannableText = SpannableString(cleanText)
                        spannableText.setSpan(
                            StrokeSpan(currentSubtitleStrokeColor, currentSubtitleStrokeWidth, currentSubtitleColor),
                            0,
                            cleanText.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        runOnUiThread {
                            subtitleTextView.setText(spannableText, TextView.BufferType.SPANNABLE)
                            subtitleTextView.visibility = View.VISIBLE
                            Log.d("Subtitles", "Embedded subtitle set: text='$cleanText', strokeColor=$currentSubtitleStrokeColor, strokeWidth=$currentSubtitleStrokeWidth")
                        }
                    } else {
                        runOnUiThread {
                            subtitleTextView.text = ""
                            subtitleTextView.visibility = View.GONE
                            Log.d("Subtitles", "No embedded subtitle cues, hiding TextView")
                        }
                    }
                }
            }
        })

        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            .setTunnelingEnabled(false)
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

            val selectedGroup = audioGroups.first()
            val format = selectedGroup.mediaTrackGroup.getFormat(0)
            Log.d("AudioTracks", "Selected track: codec=${format.codecs}, sampleRate=${format.sampleRate}, channels=${format.channelCount}")
            enableAudioTrack(selectedGroup)
            updateVolume(seekBarVolume.progress)

            if (player.audioSessionId != audioSessionId) {
                audioSessionId = player.audioSessionId
                loudnessEnhancer?.release()
                try {
                    if (audioSessionId != AudioManager.ERROR) {
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                        loudnessEnhancer?.enabled = isLoudnessEnhancerEnabled && seekBarVolume.progress > 100
                        loudnessEnhancer?.setTargetGain(loudnessGain.coerceIn(0, 1000))
                        Log.d("Audio", "LoudnessEnhancer reattached with audioSessionId=$audioSessionId")
                    } else {
                        Log.w("Audio", "Invalid audio session ID, LoudnessEnhancer not reattached")
                        loudnessEnhancer = null
                    }
                } catch (e: Exception) {
                    Log.e("Audio", "Failed to reattach LoudnessEnhancer: ${e.message}", e)
                    loudnessEnhancer = null
                }
            }
        } else {
            Log.w("AudioTracks", "No audio tracks available")
        }
    }

    private fun updateZoomPercentage() {
        val zoomPercent = (scaleFactor * 100).toInt()
        zoomtext.text = "$zoomPercent%"
        if (isZooming && zoomtext.visibility != View.VISIBLE) {
            zoomtext.visibility = View.VISIBLE
            Log.w("Zoom", "Zoomtext was not visible during zoom, forced to VISIBLE")
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

            @SuppressLint("SuspiciousIndentation")
            override fun onLongPress(e: MotionEvent) {
                if (isLocked || isZooming) return
                isSpeedIncreased = true
                player.playbackParameters = PlaybackParameters(2.0f)
                twoxtimeTextview.text = "2x Speed"
                speedTextContainer.visibility = View.VISIBLE
                twoxtimeTextview.visibility = View.VISIBLE
                twoxtimeTextview.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .withEndAction {
                        twoxtimeTextview.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .setStartDelay(1000)
                            .withEndAction {
                                speedTextContainer.visibility = View.GONE
                            }
                            .start()
                    }
                    .start()
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
                            updateVolume(seekBarVolume.progress)
                            seekTimeTextView.text = formatTime(newPosition / 1000)
                            seekTimeTextView.visibility = View.VISIBLE
                            resetHideControlsTimer()
                        } else {
                            Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
                        }
                    }

                    e1.x < screenWidth / 2 -> {
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
                        if (volumeContainer.visibility != View.VISIBLE) {
                            volumeContainer.alpha = 0f
                            brightnessOverlay.alpha = 0f
                            volumeContainer.visibility = View.VISIBLE
                            brightnessOverlay.visibility = View.VISIBLE
                            volumeContainer.animate().alpha(1f).setDuration(200).start()
                            brightnessOverlay.animate().alpha(1f).setDuration(200).start()
                        }

                        val normalizedDelta = -deltaY / screenHeight
                        val progressChange = (normalizedDelta * 100 * sensitivityFactor * min(1f, abs(normalizedDelta) * 2)).toInt()
                        val newProgress = max(0, min(200, seekBarVolume.progress + progressChange))
                        seekBarVolume.progress = newProgress
                        updateVolume(newProgress)

                        handler.removeCallbacks(hideVolumeRunnable)
                        handler.postDelayed(hideVolumeRunnable, 1000)
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
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (isLocked) return false
                isZooming = true
                focusX = detector.focusX
                focusY = detector.focusY
                zoomtext.animate().cancel()
                zoomtext.alpha = 1f
                zoomtext.visibility = View.VISIBLE
                zoomcontainer.visibility = View.VISIBLE
                updateZoomPercentage()
                Log.d("Zoom", "Zoom started, zoomtext visibility set to VISIBLE")
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(minScale, min(scaleFactor, maxScale))
                playerView.scaleX = scaleFactor
                playerView.scaleY = scaleFactor
                playerView.pivotX = focusX
                playerView.pivotY = focusY
                zoomtext.visibility = View.VISIBLE
                updateZoomPercentage()
                Log.d("Zoom", "Scaling, zoomFactor=$scaleFactor, zoomtext visibility=${zoomtext.visibility}")
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                zoomtext.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        zoomtext.visibility = View.GONE
                        Log.d("Zoom", "Zoom ended, zoomtext faded out and set to GONE")
                    }
                    .start()
                isZooming = false
                sharedPreferences.edit().putFloat("zoom_scale_factor", scaleFactor).apply()
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
                            if (twoxtimeTextview.visibility == View.VISIBLE) {
                                twoxtimeTextview.animate()
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction {
                                        speedTextContainer.visibility = View.GONE
                                    }
                                    .start()
                            }
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
                        updateSubtitles(seekPosition)
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
            if (brightnessContainer.visibility != View.VISIBLE) {
                brightnessContainer.alpha = 0f
                brightnessOverlay.alpha = 0f
                brightnessContainer.visibility = View.VISIBLE
                brightnessOverlay.visibility = View.VISIBLE
                brightnessContainer.animate().alpha(1f).setDuration(200).start()
                brightnessOverlay.animate().alpha(1f).setDuration(200).start()
            }
            brightnessText.text = "$progress%"
            val brightness = progress / 100f
            val lp = window.attributes
            lp.screenBrightness = if (brightness == 0f) 0.01f else brightness
            window.attributes = lp
            handler.removeCallbacks(hideBrightnessOverlayRunnable)
            handler.postDelayed({
                brightnessContainer.animate().alpha(0f).setDuration(200).withEndAction {
                    brightnessContainer.visibility = View.GONE
                }.start()
                brightnessOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                    brightnessOverlay.visibility = View.GONE
                }.start()
            }, 1000)
        }

        seekBarVolume.setOnProgressChangeListener { progress ->
            if (volumeContainer.visibility != View.VISIBLE) {
                volumeContainer.alpha = 0f
                brightnessOverlay.alpha = 0f
                volumeContainer.visibility = View.VISIBLE
                brightnessOverlay.visibility = View.VISIBLE
                volumeContainer.animate().alpha(1f).setDuration(200).start()
                brightnessOverlay.animate().alpha(1f).setDuration(200).start()
            }
            updateVolume(progress)
            handler.removeCallbacks(hideVolumeRunnable)
            handler.postDelayed(hideVolumeRunnable, 1000)
        }
    }

    private fun setupBackButton() {
        back.setOnClickListener {
            Animatoo.animateSlideLeft(this);
            finish()
        }
    }

    private fun setupNewButtons() {
        lockButton.setOnClickListener {
            toggleLock()
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
            unlockIcon.alpha = 0f
            unlockIcon.visibility = View.VISIBLE
            unlockIcon.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            lockButton.visibility = View.VISIBLE
            lockText.visibility = View.VISIBLE
            handler.removeCallbacks(hideLockButtonRunnable)
            handler.postDelayed(hideLockButtonRunnable, hideControlsDelay)
        } else {
            Toast.makeText(this, "Controls unlocked", Toast.LENGTH_SHORT).show()
            showControls()
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
                toggleLock()
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
        playPauseButton.visibility = View.VISIBLE
        aspectRatioButton.visibility = View.VISIBLE
        lockText.visibility = View.VISIBLE
        back.visibility = View.VISIBLE
        infoIcon.visibility = View.VISIBLE // Make info button visible
        audioSubtitleText.visibility = View.VISIBLE
        aspectRatioText.visibility = View.VISIBLE
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
        playPauseButton.visibility = View.GONE
        infoIcon.visibility = View.GONE
        aspectRatioText.visibility = View.GONE
        back.visibility = View.GONE
        if (isLocked) {
            lockButton.visibility = View.VISIBLE
            lockText.visibility = View.VISIBLE
            lockIcon.visibility = View.VISIBLE
            unlockIcon.alpha = 1f
            unlockIcon.visibility = View.VISIBLE
            handler.postDelayed(hideLockButtonRunnable, hideControlsDelay)
        } else {
            lockButton.visibility = View.GONE
            lockText.visibility = View.GONE
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
        options.add("None (Disable subtitles)")
        trackGroups.forEachIndexed { index, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val language = format.language ?: "Unknown ($index)"
            options.add("Track: $language")
        }
        currentVideoUri?.let { videoUri ->
            subtitleUris[videoUri]?.let { subtitleUri ->
                val subtitleName = getVideoTitleFromUri(subtitleUri)
                options.add("External: ${subtitleName.substringAfterLast('.').take(10)}")
            }
        }

        options.add("Load external subtitles")
        options.add("Customize subtitles")
        builder.setItems(options.toTypedArray()) { _, which ->
            when {
                which == 0 -> disableSubtitles()
                which <= trackGroups.size -> {
                    val selectedGroup = trackGroups[which - 1]
                    enableEmbeddedSubtitle(selectedGroup)
                    isUsingEmbeddedSubtitles = true
                }
                which == trackGroups.size + 1 && currentVideoUri?.let { subtitleUris[it] != null } == true -> {
                    currentVideoUri?.let { videoUri ->
                        subtitleUris[videoUri]?.let {
                            loadSubtitles(it)
                            isUsingEmbeddedSubtitles = false
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
            "Change Stroke Color",
            "Adjust Stroke Width"
        )
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showColorSelectionDialog()
                1 -> showSizeSeekBarDialog()
                2 -> toggleSubtitleBackground()
                3 -> showStrokeColorSelectionDialog()
                4 -> showStrokeWidthDialog()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun showStrokeColorSelectionDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_color_picker)
        dialog.setTitle("Select Stroke Color")
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        val colorView = dialog.findViewById<View>(R.id.colorPreview)
        val hueSeekBar = dialog.findViewById<SeekBar>(R.id.hueSeekBar)
        val saturationSeekBar = dialog.findViewById<SeekBar>(R.id.saturationSeekBar)
        val valueSeekBar = dialog.findViewById<SeekBar>(R.id.valueSeekBar)
        val hsv = FloatArray(3)
        Color.colorToHSV(currentSubtitleStrokeColor, hsv)
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
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColor() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColor() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        valueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColor() }
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
                currentSubtitleStrokeColor = Color.HSVToColor(hsv)
                updateSubtitleAppearance()
                saveSubtitleSettings()
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

    private fun showStrokeWidthDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_subtitle_size)
        dialog.setTitle("Adjust Stroke Width")
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        val seekBar = dialog.findViewById<SeekBar>(R.id.sizeSeekBar)
        val sizePreview = dialog.findViewById<TextView>(R.id.sizePreview)
        seekBar.max = 200
        seekBar.progress = (currentSubtitleStrokeWidth * 10).toInt()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newWidth = progress / 10f
                sizePreview.text = "Stroke Width: ${"%.1f".format(newWidth)}"
                currentSubtitleStrokeWidth = newWidth
                updateSubtitleAppearance()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        sizePreview.text = "Stroke Width: ${"%.1f".format(currentSubtitleStrokeWidth)}"
        dialog.findViewById<Button>(R.id.btnOk).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF5722"))
            setOnClickListener {
                currentSubtitleStrokeWidth = seekBar.progress / 10f
                updateSubtitleAppearance()
                saveSubtitleSettings()
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

    private fun enhanceVideoDepth() {
        playerView.videoSurfaceView?.let { surfaceView ->
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(1.5f)
                    val contrast = 2.0f
                    setScale(contrast, contrast, contrast, 1f)
                })
            }
            surfaceView.setLayerPaint(paint)
        }
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
            setTextColor(currentSubtitleColor)
            textSize = currentSubtitleSize
            maxLines = 3
            setBackgroundColor(currentSubtitleBackground)
            if (currentSubtitleShadow) {
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
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
        baseSubtitleSize = currentSubtitleSize
        saveSubtitleSettings()
        Log.d("Subtitles", "Updated appearance: color=$currentSubtitleColor, size=$currentSubtitleSize, bg=$currentSubtitleBackground, shadow=$currentSubtitleShadow")
    }

    internal fun showAudioBoostDialog() {
        val dialog = Dialog(this, R.style.CustomDialog)
        dialog.setContentView(R.layout.dialog_audio_boost)
        dialog.setTitle("Adjust Audio Boost")
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        val seekBar = dialog.findViewById<SeekBar>(R.id.boostSeekBar)
        val boostPreview = dialog.findViewById<TextView>(R.id.boostPreview)
        val warningText = dialog.findViewById<TextView>(R.id.warningText)
        seekBar.max = 4000
        seekBar.progress = loudnessGain
        boostPreview.text = "Boost: ${loudnessGain / 100f}dB"
        warningText.text = "Note: High boost levels may cause distortion. Adjust carefully."
        warningText.setTextColor(Color.YELLOW)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    loudnessGain = progress.coerceIn(0, 4000)
                    boostPreview.text = "Boost: ${loudnessGain / 100f}dB"
                    loudnessEnhancer?.setTargetGain(loudnessGain)
                    loudnessEnhancer?.enabled = isLoudnessEnhancerEnabled && loudnessGain > 0
                    Log.d("Audio", "Audio boost preview: gain=$loudnessGain cB")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        dialog.findViewById<Button>(R.id.btnOk).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF5722"))
            setOnClickListener {
                loudnessGain = seekBar.progress.coerceIn(0, 4000)
                isLoudnessEnhancerEnabled = loudnessGain > 0
                sharedPreferences.edit()
                    .putInt("loudness_gain", loudnessGain)
                    .putBoolean("loudness_enhancer_enabled", isLoudnessEnhancerEnabled)
                    .apply()
                // Ensure loudnessEnhancer is initialized and updated
                if (loudnessEnhancer == null || player.audioSessionId != audioSessionId) {
                    try {
                        audioSessionId = player.audioSessionId
                        if (audioSessionId != AudioManager.ERROR) {
                            loudnessEnhancer?.release()
                            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                            Log.d("Audio", "Reinitialized LoudnessEnhancer with audioSessionId=$audioSessionId")
                        } else {
                            Log.w("Audio", "Invalid audio session ID, cannot apply boost")
                            Toast.makeText(this@MainActivity, "Audio boost unavailable", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            return@setOnClickListener
                        }
                    } catch (e: Exception) {
                        Log.e("Audio", "Failed to reinitialize LoudnessEnhancer: ${e.message}", e)
                        Toast.makeText(this@MainActivity, "Error applying audio boost", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        return@setOnClickListener
                    }
                }
                loudnessEnhancer?.setTargetGain(loudnessGain)
                loudnessEnhancer?.enabled = isLoudnessEnhancerEnabled && seekBarVolume.progress > 100 && loudnessGain > 0
                updateVolume(seekBarVolume.progress)
                Toast.makeText(this@MainActivity, "Audio boost set to ${loudnessGain / 100f}dB", Toast.LENGTH_SHORT).show()
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
        options.add("Adjust Audio Boost")
        options.add("Loudness Boost: ${if (isLoudnessEnhancerEnabled) "On" else "Off"}")
        builder.setItems(options.toTypedArray()) { _, which ->
            if (audioGroups.isNotEmpty() && which < audioGroups.size) {
                val selectedGroup = audioGroups[which]
                enableAudioTrack(selectedGroup)
                val format = selectedGroup.mediaTrackGroup.getFormat(0)
                Toast.makeText(this, "Switched to ${format.language} (${format.channelCount}ch)", Toast.LENGTH_SHORT).show()
                updateVolume(seekBarVolume.progress)
            } else if (which == audioGroups.size) {
                showAudioBoostDialog()
            } else if (which == audioGroups.size + 1) {
                isLoudnessEnhancerEnabled = !isLoudnessEnhancerEnabled
                loudnessEnhancer?.enabled = isLoudnessEnhancerEnabled && seekBarVolume.progress > 100
                sharedPreferences.edit().putBoolean("loudness_enhancer_enabled", isLoudnessEnhancerEnabled).apply()
                Toast.makeText(this, "Loudness Boost ${if (isLoudnessEnhancerEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                updateVolume(seekBarVolume.progress)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        val dialog = builder.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE) }
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
            Log.d("Subtitles", "Cleared subtitle association for video: $currentVideoUri")
        }
        currentSubtitleUri = null
        Log.d("Subtitles", "Subtitles disabled")
    }

    private fun enableEmbeddedSubtitle(group: Tracks.Group) {
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, 0)
            )
            .build()
        subtitleTextView.visibility = View.VISIBLE
        subtitles = emptyList()
        currentSubtitleUri = null
        isUsingEmbeddedSubtitles = true
        Log.d("Subtitles", "Enabled embedded subtitle track: ${group.mediaTrackGroup.getFormat(0).language}")
    }

    private fun enableAudioTrack(group: Tracks.Group) {
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, 0)
            )
            .build()
        Log.d("Audio", "Enabled audio track: ${group.mediaTrackGroup.getFormat(0).language}")
    }

    private fun loadSubtitles(subtitleUri: Uri) {
        try {
            // Check if the URI already has a persisted read permission
            val hasPermission = contentResolver.getPersistedUriPermissions()
                .any { it.uri == subtitleUri && it.isReadPermission() }
            if (!hasPermission) {
                contentResolver.takePersistableUriPermission(subtitleUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Log.d("Subtitles", "Took persistable permission for $subtitleUri")
            }

            val subtitleFormat = subtitleUri.path?.substringAfterLast(".")?.lowercase()
            subtitles = when (subtitleFormat) {
                "srt" -> parseSrt(subtitleUri)
                "vtt" -> parseVtt(subtitleUri)
                else -> {
                    Log.w("Subtitles", "Unsupported subtitle format: $subtitleFormat")
                    emptyList()
                }
            }
            subtitleTextView.visibility = View.VISIBLE
            isUsingEmbeddedSubtitles = false
            trackSelector.parameters = trackSelector.parameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            currentSubtitleUri = subtitleUri
            videoUri?.let { videoUri ->
                subtitleUris[videoUri] = subtitleUri
                sharedPreferences.edit().putString("subtitleUri_$videoUri", subtitleUri.toString()).apply()
            }
            updateSubtitles(player.currentPosition)
            Log.d("Subtitles", "Loaded external subtitles: $subtitleUri, count=${subtitles.size}")
        } catch (e: SecurityException) {
            Log.e("Subtitles", "Permission error for subtitle: ${e.message}", e)
            Toast.makeText(this, "Permission error for subtitle: ${e.message}. Please reselect the file.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Subtitles", "Failed to load subtitles: ${e.message}", e)
            Toast.makeText(this, "Error loading subtitles: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun parseSrt(uri: Uri): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var index = 0
                    var startTime = ""
                    var endTime = ""
                    val textBuilder = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        line = line?.trim()
                        if (line.isNullOrEmpty()) {
                            if (textBuilder.isNotEmpty()) {
                                subtitles.add(
                                    SubtitleEntry(
                                        index++,
                                        parseSrtTime(startTime),
                                        parseSrtTime(endTime),
                                        textBuilder.toString().trim()
                                    )
                                )
                                textBuilder.clear()
                            }
                            continue
                        }

                        when {
                            line!!.matches(Regex("\\d+")) -> continue
                            line!!.contains("-->") -> {
                                val times = line!!.split("-->")
                                startTime = times[0].trim()
                                endTime = times[1].trim()
                            }
                            else -> textBuilder.append(line).append("\n")
                        }
                    }

                    if (textBuilder.isNotEmpty()) {
                        subtitles.add(
                            SubtitleEntry(
                                index,
                                parseSrtTime(startTime),
                                parseSrtTime(endTime),
                                textBuilder.toString().trim()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Subtitles", "Error parsing SRT: ${e.message}", e)
        }
        return subtitles
    }

    private fun parseSrtTime(time: String): Long {
        try {
            val parts = time.replace(",", ".").split(":")
            if (parts.size != 3) return 0L
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toFloatOrNull()?.toLong() ?: 0L
            return (hours * 3600 + minutes * 60 + seconds) * 1000
        } catch (e: Exception) {
            Log.w("Subtitles", "Failed to parse SRT time: $time, error: ${e.message}")
            return 0L
        }
    }

    private fun parseVtt(uri: Uri): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var index = 0
                    var startTime = ""
                    var endTime = ""
                    val textBuilder = StringBuilder()
                    var line: String?
                    var isHeader = true

                    while (reader.readLine().also { line = it } != null) {
                        line = line?.trim()
                        if (isHeader) {
                            if (line.isNullOrEmpty()) isHeader = false
                            continue
                        }
                        if (line.isNullOrEmpty()) {
                            if (textBuilder.isNotEmpty()) {
                                subtitles.add(
                                    SubtitleEntry(
                                        index++,
                                        parseVttTime(startTime),
                                        parseVttTime(endTime),
                                        textBuilder.toString().trim()
                                    )
                                )
                                textBuilder.clear()
                            }
                            continue
                        }

                        when {
                            line!!.contains("-->") -> {
                                val times = line!!.split("-->")
                                startTime = times[0].trim()
                                endTime = times[1].trim()
                            }
                            else -> textBuilder.append(line).append("\n")
                        }
                    }

                    if (textBuilder.isNotEmpty()) {
                        subtitles.add(
                            SubtitleEntry(
                                index,
                                parseVttTime(startTime),
                                parseVttTime(endTime),
                                textBuilder.toString().trim()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Subtitles", "Error parsing VTT: ${e.message}", e)
        }
        return subtitles
    }

    private fun parseVttTime(time: String): Long {
        try {
            val parts = time.replace(",", ".").split(":")
            if (parts.size < 2) return 0L
            val secondsPart = parts.last()
            val minutes = parts[parts.size - 2].toLongOrNull() ?: 0L
            val hours = if (parts.size > 2) parts[parts.size - 3].toLongOrNull() ?: 0L else 0L
            val seconds = secondsPart.toFloatOrNull()?.toLong() ?: 0L
            return (hours * 3600 + minutes * 60 + seconds) * 1000
        } catch (e: Exception) {
            Log.w("Subtitles", "Failed to parse VTT time: $time, error: ${e.message}")
            return 0L
        }
    }

    private fun updateSubtitles(currentPosition: Long) {
        if (isUsingEmbeddedSubtitles || subtitles.isEmpty()) {
            subtitleTextView.text = ""
            subtitleTextView.visibility = View.GONE
            return
        }

        val activeSubtitle = subtitles.find { subtitle ->
            currentPosition in subtitle.startTime..subtitle.endTime
        }

        if (activeSubtitle != null) {
            val cleanText = cleanSubtitleText(activeSubtitle.text)
            val spannableText = SpannableString(cleanText)
            spannableText.setSpan(
                StrokeSpan(currentSubtitleStrokeColor, currentSubtitleStrokeWidth, currentSubtitleColor),
                0,
                cleanText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            subtitleTextView.setText(spannableText, TextView.BufferType.SPANNABLE)
            subtitleTextView.visibility = View.VISIBLE
            Log.d("Subtitles", "External subtitle set: text='${activeSubtitle.text}', position=$currentPosition")
        } else {
            subtitleTextView.text = ""
            subtitleTextView.visibility = View.GONE
            Log.d("Subtitles", "No external subtitle for position=$currentPosition")
        }
    }

    private fun cleanSubtitleText(text: String): String {
        return text.replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\{[^}]+\\}"), "")
            .trim()
    }

    private fun pickSubtitle() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/x-subrip"))
        }
        startActivityForResult(intent, PICK_SUBTITLE_REQUEST)
    }

    private fun launchVideoList() {
        val intent = Intent(this, VideoListActivity::class.java)
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }

    private fun playVideo(uri: Uri) {
        try {
            if (!DocumentsContract.isDocumentUri(this, uri)) {
                // MediaStore URI - check runtime permission instead
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
                        return
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
                        return
                    }
                }
            } else {
                // Document URI - ensure persistable permission
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Proceed with ExoPlayer setup
            val videoSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this))
                .createMediaSource(MediaItem.fromUri(uri))
            player.setMediaSource(videoSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error: ${e.message}. Please reselect the file.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun updateVideoTitle(seriesId: Int, seasonNumber: Int, episodeNumber: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val episode = try {
                TmdbClient.getEpisodeData(seriesId, seasonNumber, episodeNumber)
            } catch (e: Exception) {
                Log.e("TMDb", "Failed to get episode title: ${e.message}")
                null
            }
            withContext(Dispatchers.Main) {
                val displayText = if (episode?.name != null) {
                    val seasonStr = String.format("S%02d", seasonNumber)
                    val episodeStr = String.format("E%02d", episodeNumber)
                    "${episode.name}-$seasonStr$episodeStr"
                } else {
                    "Episode $episodeNumber"
                }
                videoTitleTextView.text = displayText
                videoTitleTextView.visibility = View.VISIBLE
            }
        }
    }
    private fun getVideoTitleFromUri(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) cursor.getString(nameIndex) else uri.lastPathSegment ?: "Unknown"
                } else {
                    uri.lastPathSegment ?: "Unknown"
                }
            } ?: uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            Log.w("VideoPlayback", "Failed to get video title: ${e.message}")
            uri.lastPathSegment ?: "Unknown"
        }
    }
    private fun seekRelative(millis: Long) {
        if (player.duration <= 0) return
        val newPosition = max(0, min(player.duration, player.currentPosition + millis))
        if (player.playbackState == Player.STATE_READY) {
            player.seekTo(newPosition)
            updateVolume(seekBarVolume.progress)
            updateTimeDisplays()
            skipDirectionTextView.text = if (millis > 0) ">> 10s" else "<< 10s"
            skipDirectionTextView.visibility = View.VISIBLE
            skipDirectionContainer.visibility = View.VISIBLE
            skipDirectionContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .withEndAction {
                    skipDirectionContainer.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            skipDirectionContainer.visibility = View.GONE
                        }
                        .start()
                }
                .start()
            updateSubtitles(newPosition)
            Log.d("Player", "Seek relative: millis=$millis, newPosition=$newPosition")
        } else {
            Log.w("Player", "Cannot seek: Player not in READY state (state=${player.playbackState})")
        }
    }
    private fun updateTimeDisplays() {
        val currentPosition = player.currentPosition / 1000
        val duration = player.duration / 1000
        leftTimeTextView.text = formatTime(currentPosition)
        rightTimeTextView.text = if (showRemainingTime && duration > 0) {
            "-${formatTime(duration - currentPosition)}"
        } else {
            formatTime(duration)
        }
    }

    private fun toggleRemainingTime() {
        showRemainingTime = !showRemainingTime
        updateTimeDisplays()
    }

    private fun formatTime(seconds: Long): String {
        if (seconds < 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                PICK_VIDEO_REQUEST -> {
                    data.data?.let { uri ->
                        videoUri = uri
                        currentVideoUri = uri
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            playVideo(uri)
                            val seriesId = data.getIntExtra("SERIES_ID", -1)
                            val seasonNumber = data.getIntExtra("SEASON_NUMBER", 1)
                            val episodeNumber = data.getIntExtra("EPISODE_NUMBER", 1)
                            updateVideoTitle(uri, seriesId, seasonNumber, episodeNumber)
                            Log.d("VideoPlayback", "Video selected: $uri, seriesId=$seriesId, season=$seasonNumber, episode=$episodeNumber")
                        } catch (e: SecurityException) {
                            Log.w("VideoPlayback", "Permission not persistable for video: ${e.message}")
                            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                PICK_SUBTITLE_REQUEST -> {
                    data.data?.let { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            loadSubtitles(uri)
                            Log.d("Subtitles", "Subtitle selected: $uri")
                        } catch (e: SecurityException) {
                            Log.w("Subtitles", "Permission not persistable for subtitle: ${e.message}")
                            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("videoUri", videoUri)
        Log.d("MainActivity", "Saving instance state: videoUri=$videoUri")
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
            playImageView.visibility = View.VISIBLE
        }
        videoUri?.let {
            if (player.currentPosition > 0 && player.playbackState != Player.STATE_ENDED) {
                sharedPreferences.edit().putLong(it.toString(), player.currentPosition).apply()
                Log.d("VideoPlayback", "Paused at ${player.currentPosition} ms for $it")
            }
        }
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && !player.isPlaying && player.playbackState == Player.STATE_READY) {
            player.play()
            playImageView.visibility = View.GONE
            handler.post(updateSeekBarRunnable)
            updateVolume(seekBarVolume.progress)
            Log.d("VideoPlayback", "Resumed playback")
        }
        setFullScreenMode(isFullScreen)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            player.release()
            loudnessEnhancer?.release()
            Log.d("MainActivity", "Player and LoudnessEnhancer released")
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "Configuration changed: orientation=${newConfig.orientation}")
        playerView.post { applyScaleMode(currentScaleMode) }
    }

    inner class SubTitleDragListener : View.OnTouchListener {
        private var initialY = 0f
        private var initialViewY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (isLocked) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialViewY = subtitleTextView.y
                    isDragging = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaY = event.rawY - initialY
                        val newY = initialViewY + deltaY
                        val parentHeight = (subtitleTextView.parent as View).height
                        val maxY = parentHeight - subtitleTextView.height.toFloat()
                        subtitleTextView.y = newY.coerceIn(0f, maxY)
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    return true
                }
            }
            return false
        }
    }

    enum class VideoScaleMode {
        FILL, FIT, ORIGINAL, STRETCH, RATIO_16_9, RATIO_4_3, RATIO_18_9, RATIO_19_5_9, RATIO_20_9, RATIO_21_9
    }

    data class SubtitleEntry(
        val index: Int,
        val startTime: Long,
        val endTime: Long,
        val text: String
    )

}
