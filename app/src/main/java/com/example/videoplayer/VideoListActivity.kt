package com.example.videoplayer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoListActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "VideoPlayerPrefs"
        private const val KEY_VIEW_MODE = "isGridMode" // Key to store view mode
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val TAG = "VideoListActivity"

        fun cleanTitleForTmdb(name: String): Triple<String, Boolean, Pair<Int, Int>?> {
            val original = name.substringBeforeLast(".", name)
            val seasonEpisodeRegex = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE)
            val yearRegex = Regex("\\(\\d{4}\\)|\\b(19|20)\\d{2}\\b")
            val noiseWordsRegex = Regex(
                "(?i)\\b(720p|1080p|2160p|4k|ds4k|webrip|web[- ]dl|bluray|x264|x265|hevc|" +
                        "amzn|nf|ddp?|5\\.1|10bit|eac3|aac|hdr|hdtv|dts|truehd|atmos|esub|dual|" +
                        "latino|english|hindi|multi|heteam|pahe|budgetbits|hdhub4u|iboxtv|\\[.*?\\])\\b"
            )

            val match = seasonEpisodeRegex.find(original)
            val isTvShow = match != null
            val seasonEpisode = match?.let {
                val season = it.groupValues[1].toIntOrNull() ?: 1
                val episode = it.groupValues[2].toIntOrNull() ?: 1
                season to episode
            }

            val yearMatch = yearRegex.find(original)
            val year = yearMatch?.value?.replace("(", "")?.replace(")", "")?.toIntOrNull()

            var cleaned = original
                .replace(yearRegex, "")
                .replace(noiseWordsRegex, "")
                .replace("[^a-zA-Z0-9\\s]".toRegex(), " ")
                .replace("\\s+", " ")
                .trim()

            if (isTvShow && match != null) {
                val seasonIndex = match.range.first
                cleaned = original.substring(0, seasonIndex)
                    .replace(yearRegex, "")
                    .replace(noiseWordsRegex, "")
                    .replace("[^a-zA-Z0-9\\s]".toRegex(), " ")
                    .replace("\\s+", " ")
                    .trim()

                val separators = listOf(" - ", ": ", " episode ", " ep ")
                for (separator in separators) {
                    val index = cleaned.indexOf(separator, ignoreCase = true)
                    if (index != -1) {
                        cleaned = cleaned.substring(0, index).trim()
                        break
                    }
                }

            }

            if (cleaned.length < 3 || cleaned.isBlank()) {
                cleaned = original
                    .replace(seasonEpisodeRegex, "")
                    .replace(yearRegex, "")
                    .replace(noiseWordsRegex, "")
                    .replace("[^a-zA-Z0-9\\s]".toRegex(), " ")
                    .replace("\\s+", " ")
                    .trim()
            }

            if (cleaned.isBlank()) {
                cleaned = original.split(" ")
                    .filter { it !in noiseWordsRegex.toString() && !it.matches(yearRegex) }
                    .take(3)
                    .joinToString(" ")
                    .replace("[^a-zA-Z0-9\\s]".toRegex(), " ")
                    .replace("\\s+", " ")
                    .trim()
            }

            Log.d(TAG, "Raw: '$name' → Cleaned: '$cleaned', TV Show: $isTvShow, Season/Episode: $seasonEpisode, Year: $year")
            return Triple(cleaned, isTvShow, seasonEpisode)
        }
    }

    private var recyclerView: RecyclerView? = null
    private var noVideosText: TextView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var titleText: TextView? = null
    private var sortSpinner: Spinner? = null
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var sharedPref: SharedPreferences
    private val videoList = mutableListOf<VideoItem>()
    private val originalVideoList = mutableListOf<VideoItem>()
    private var isRefreshing = false
    private var isRegrouping = false // Flag to prevent recursive regrouping

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Video deleted successfully", Toast.LENGTH_SHORT).show()
            loadVideos()
        } else {
            Toast.makeText(this, "Deletion cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied for renaming", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        if (permissions.all { it.value }) {
            Log.d(TAG, "All permissions granted, loading videos")
            loadVideos()
        } else {
            Log.w(TAG, "Permissions denied: $permissions")
            Toast.makeText(this, "Storage permission required to load videos", Toast.LENGTH_LONG).show()
            noVideosText?.apply {
                text = "Please grant storage permission to view videos"
                visibility = View.VISIBLE
            }
            recyclerView?.visibility = View.GONE
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    private val REQUIRED_PERMISSIONS: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        try {
            setContentView(R.layout.activity_video_list)
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            Log.d(TAG, "setContentView completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setContentView: ${e.message}", e)
            Toast.makeText(this, "Error loading layout: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            titleText = findViewById(R.id.titleText)
            recyclerView = findViewById(R.id.recyclerView)
            noVideosText = findViewById(R.id.noVideosText)
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
            sortSpinner = findViewById(R.id.viewModeSpinner)
            sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            Log.d(TAG, "UI components: titleText=$titleText, recyclerView=$recyclerView, noVideosText=$noVideosText, swipeRefreshLayout=$swipeRefreshLayout")
            if (titleText == null || recyclerView == null || noVideosText == null || swipeRefreshLayout == null || sortSpinner == null) {
                Log.e(TAG, "One or more UI components are null")
                Toast.makeText(this, "UI initialization error", Toast.LENGTH_LONG).show()
                return
            }

            recyclerView?.visibility = View.VISIBLE
            noVideosText?.visibility = View.GONE
            titleText?.text = "Videos"

            // Set initial view mode from SharedPreferences
            val isGridMode = sharedPref.getBoolean(KEY_VIEW_MODE, true)
            if (isGridMode) {
                videoAdapter = VideoAdapter(
                    context = this,
                    videos = mutableListOf<VideoItem>().apply { addAll(emptyList()) },
                    onClick = { video ->
                        if (video.isSeriesHeader) {
                            val cleanedTitle = cleanTitleForTmdb(video.title).first
                            val episodes = originalVideoList.filter {
                                val episodeCleanedTitle = cleanTitleForTmdb(it.title).first
                                Log.d(TAG, "Comparing episode title: '$episodeCleanedTitle' with series title: '$cleanedTitle'")
                                episodeCleanedTitle == cleanedTitle
                            }
                            if (episodes.isEmpty()) {
                                Log.w(TAG, "No episodes found for series '$cleanedTitle'")
                                Toast.makeText(this, "No episodes found for $cleanedTitle", Toast.LENGTH_SHORT).show()
                                return@VideoAdapter
                            }
                            // Validate TMDB titles
                            val mismatchedEpisodes = episodes.filter {
                                !it.tmdbData?.tvTitle.isNullOrBlank() && it.tmdbData?.tvTitle != cleanedTitle
                            }
                            if (mismatchedEpisodes.isNotEmpty()) {
                                Log.w(TAG, "Mismatched TMDB titles for '$cleanedTitle': ${mismatchedEpisodes.map { it.tmdbData?.tvTitle }}")
                            }
                            val intent = Intent(this, SeriesCollectionActivity::class.java).apply {
                                putExtra("SERIES_TITLE", cleanedTitle)
                                putParcelableArrayListExtra("EPISODES", ArrayList(episodes))
                            }
                            startActivity(intent)
                            Animatoo.animateCard(this)
                        } else {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("VIDEO_URI", video.uri.toString())
                                putExtra("SERIES_ID", video.tmdbData?.seriesId ?: -1)
                                putExtra("SEASON_NUMBER", video.tmdbData?.season ?: 1)
                                putExtra("EPISODE_NUMBER", video.tmdbData?.episode ?: 1)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                            Animatoo.animateCard(this)
                        }
                    },
                    updateOriginalList = { newList ->
                        originalVideoList.clear()
                        originalVideoList.addAll(newList)
                        Log.d(TAG, "Updated originalVideoList with ${originalVideoList.size} items")
                    },
                    updateUiVisibility = { isListEmpty ->
                        Log.d(TAG, "Updating UI visibility: isListEmpty=$isListEmpty")
                        recyclerView?.visibility = if (isListEmpty) View.GONE else View.VISIBLE
                        noVideosText?.apply {
                            text = if (isListEmpty) "No videos found" else ""
                            visibility = if (isListEmpty) View.VISIBLE else View.GONE
                        }
                    },
                    onDeleteRequested = { video ->
                        showDeleteConfirmation(video)
                    },
                    onRenameRequested = { video, position ->
                        showRenameDialog(video, position)
                    }
                )
                recyclerView?.layoutManager = GridLayoutManager(this, 2)
                videoAdapter.setGridMode(true)
            } else {
                videoAdapter = VideoAdapter(
                    context = this,
                    videos = mutableListOf<VideoItem>().apply { addAll(emptyList()) },
                    onClick = { video ->
                        if (video.isSeriesHeader) {
                            val cleanedTitle = video.tmdbData?.tvTitle ?: cleanTitleForTmdb(video.title).first
                            val episodes = originalVideoList.filter {
                                cleanTitleForTmdb(it.title).first == cleanedTitle
                            }
                            val intent = Intent(this, SeriesCollectionActivity::class.java).apply {
                                putExtra("SERIES_TITLE", video.title)
                                putParcelableArrayListExtra("EPISODES", ArrayList(episodes))
                            }
                            startActivity(intent)
                            Animatoo.animateCard(this)
                        }
                        else {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("VIDEO_URI", video.uri.toString())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                            Animatoo.animateSwipeLeft(this) // Add swipe left animation
                        }
                    },
                    updateOriginalList = { newList ->
                        originalVideoList.clear()
                        originalVideoList.addAll(newList)
                        Log.d(TAG, "Updated originalVideoList with ${originalVideoList.size} items")
                    },
                    updateUiVisibility = { isListEmpty ->
                        Log.d(TAG, "Updating UI visibility: isListEmpty=$isListEmpty")
                        recyclerView?.visibility = if (isListEmpty) View.GONE else View.VISIBLE
                        noVideosText?.apply {
                            text = if (isListEmpty) "No videos found" else ""
                            visibility = if (isListEmpty) View.VISIBLE else View.GONE
                        }
                    },
                    onDeleteRequested = { video ->
                        showDeleteConfirmation(video)
                    },
                    onRenameRequested = { video, position ->
                        showRenameDialog(video, position)
                    }
                )
                recyclerView?.layoutManager = LinearLayoutManager(this)
                videoAdapter.setGridMode(false)
            }
            recyclerView?.adapter = videoAdapter
            recyclerView?.adapter?.notifyDataSetChanged()
            Log.d(TAG, "RecyclerView adapter set with mode: $isGridMode")

            setupSpinners()

            swipeRefreshLayout?.setOnRefreshListener {
                Log.d(TAG, "Swipe refresh triggered")
                if (!isRefreshing) {
                    loadVideos()
                }
            }

            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Initialization error: ${e.message}", Toast.LENGTH_LONG).show()
            noVideosText?.apply {
                text = "Error initializing: ${e.message}"
                visibility = View.VISIBLE
            }
            recyclerView?.visibility = View.GONE
        }
        Log.d(TAG, "onCreate completed")
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity() // Closes the app by finishing all activities
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions")
        if (!hasPermissions()) {
            Log.d(TAG, "Permissions not granted, requesting: ${REQUIRED_PERMISSIONS.joinToString()}")
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            Log.d(TAG, "Permissions already granted, loading videos")
            loadVideos()
        }
    }

    private fun setupSpinners() {
        val combinedOptions = arrayOf("Sort by Name", "Sort by Length", "Sort by Date", "Filter Favorites", "List View", "Grid View")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, combinedOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner?.adapter = adapter
        sortSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (combinedOptions[position]) {
                    "List View" -> {
                        videoAdapter.setGridMode(false)
                        recyclerView?.layoutManager = LinearLayoutManager(this@VideoListActivity)
                        sharedPref.edit().putBoolean(KEY_VIEW_MODE, false).apply() // Save list mode
                    }
                    "Grid View" -> {
                        videoAdapter.setGridMode(true)
                        recyclerView?.layoutManager = GridLayoutManager(this@VideoListActivity, 2)
                        sharedPref.edit().putBoolean(KEY_VIEW_MODE, true).apply() // Save grid mode
                    }
                    else -> applyFiltersAndSort()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFiltersAndSort() {
        if (isRegrouping) return
        isRegrouping = true
        try {
            val sortedList = originalVideoList.toMutableList()
            when (sortSpinner?.selectedItem.toString()) {
                "Sort by Name" -> sortedList.sortBy { it.title }
                "Sort by Length" -> sortedList.sortBy { it.duration }
                "Sort by Date" -> sortedList.sortByDescending { it.dateAdded }
                "Filter Favorites" -> sortedList.removeAll { !it.isFavorite }
            }
            groupVideosBySeries(sortedList)
            videoAdapter.updateList(videoList)
        } finally {
            isRegrouping = false
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permissions granted via onRequestPermissionsResult")
                loadVideos()
            } else {
                Log.w(TAG, "Permissions denied via onRequestPermissionsResult")
                Toast.makeText(this, "Storage permission required to load videos", Toast.LENGTH_LONG).show()
                noVideosText?.apply {
                    text = "Please grant storage permission to view videos"
                    visibility = View.VISIBLE
                }
                recyclerView?.visibility = View.GONE
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val hasAllPermissions = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "hasPermissions: $hasAllPermissions")
        return hasAllPermissions
    }

    suspend fun retryTmdbQuery(
        query: String,
        isTvShow: Boolean,
        seasonEpisode: Pair<Int, Int>? = null,
        maxRetries: Int = 2
    ): TmdbMovie? {
        var attempt = 0
        while (attempt <= maxRetries) {
            try {
                if (isTvShow && seasonEpisode != null) {
                    Log.d(TAG, "Querying series: '$query', Season ${seasonEpisode.first}, Episode ${seasonEpisode.second}")
                    val seriesData = TmdbClient.getMediaData(query, true)
                    if (seriesData == null || seriesData.id == 0) {
                        Log.w(TAG, "No series found for '$query'")
                        attempt++
                        continue
                    }
                    // Log folder name
                    val folderName = File(query).parentFile?.name
                    Log.d(TAG, "Folder name for '$query': $folderName")
                    if (folderName != null && folderName != query && seriesData.tvTitle != query) {
                        Log.w(TAG, "TMDB title '${seriesData.tvTitle}' differs from query '$query', folder: '$folderName'")
                    }
                    Log.d(TAG, "Series found: title=${seriesData.tvTitle}, id=${seriesData.id}")
                    val episodeData = TmdbClient.getEpisodeData(
                        seriesId = seriesData.id,
                        seasonNumber = seasonEpisode.first,
                        episodeNumber = seasonEpisode.second
                    )
                    if (episodeData != null && episodeData.name != null && episodeData.name.isNotBlank()) {
                        Log.d(TAG, "Episode found: name=${episodeData.name}, air_date=${episodeData.air_date}")
                        val seasonData = TmdbClient.getSeasonData(seriesData.id, seasonEpisode.first)
                        val posterPath = seasonData?.poster_path ?: seriesData.poster_path
                        return TmdbMovie(
                            movieTitle = episodeData.name,
                            tvTitle = query, // Use cleaned title
                            overview = episodeData.overview,
                            tvAirDate = episodeData.air_date,
                            vote_average = episodeData.vote_average,
                            poster_path = posterPath,
                            id = seriesData.id,
                            media_type = "tv",
                            season = seasonEpisode.first,
                            episode = seasonEpisode.second
                        )
                    } else {
                        Log.w(TAG, "Episode data missing for '$query' S${seasonEpisode.first}E${seasonEpisode.second}")
                        attempt++
                        continue
                    }
                } else {
                    val result = TmdbClient.getMediaData(query, isTvShow)
                    if (result != null) {
                        Log.d(TAG, "Non-episode query succeeded: title=${result.tvTitle ?: result.movieTitle}")
                        return result
                    }
                    attempt++
                }
            } catch (e: Exception) {
                Log.e(TAG, "TMDB attempt $attempt failed for '$query': ${e.message}")
                attempt++
            }
            if (attempt <= maxRetries) {
                delay(1000L * attempt)
            }
        }
        Log.w(TAG, "All TMDB queries failed for '$query', isTvShow=$isTvShow, seasonEpisode=$seasonEpisode")
        if (isTvShow && seasonEpisode != null) {
            val seriesData = TmdbClient.getMediaData(query, true)
            if (seriesData != null) {
                Log.d(TAG, "Falling back to series data for '$query'")
                val seasonData = TmdbClient.getSeasonData(seriesData.id, seasonEpisode.first)
                val posterPath = seasonData?.poster_path ?: seriesData.poster_path
                return TmdbMovie(
                    movieTitle = query,
                    tvTitle = query, // Use cleaned title
                    overview = seriesData.overview,
                    tvAirDate = seriesData.tvAirDate,
                    vote_average = seriesData.vote_average,
                    poster_path = posterPath,
                    id = seriesData.id,
                    media_type = "tv",
                    season = seasonEpisode.first,
                    episode = seasonEpisode.second
                )
            }
        }
        return null
    }

    private fun loadVideos() {
        Log.d(TAG, "loadVideos started")
        if (isRefreshing) {
            Log.d(TAG, "Already refreshing, ignoring loadVideos")
            return
        }
        isRefreshing = true
        swipeRefreshLayout?.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.DURATION
                )

                val currentVideoIds = originalVideoList.map { it.id }.toSet()
                val newVideoIds = mutableSetOf<Long>()
                val updatedVideos = mutableListOf<VideoItem>()
                var hasChanges = false

                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val dateAddedColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val durationColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                    Log.d(TAG, "MediaStore query returned ${cursor.count} videos")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        newVideoIds.add(id)
                        val name = cursor.getString(nameColumn)?.replace("_", " ") ?: continue
                        val data = cursor.getString(dataColumn) ?: continue
                        val dateAdded = cursor.getLong(dateAddedColumn)
                        val duration = cursor.getLong(durationColumn)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        Log.d(TAG, "Loaded video: id=$id, name=$name, uri=$uri")

                        val existingVideo = originalVideoList.find { it.id == id }
                        if (existingVideo == null) {
                            // New video
                            val videoItem = VideoItem(uri, name, id, duration, dateAdded, data)
                            updatedVideos.add(videoItem)
                            hasChanges = true
                        } else if (existingVideo.title != name || existingVideo.path != data || existingVideo.duration != duration) {
                            // Updated video
                            val updatedVideo = existingVideo.copy(
                                title = name,
                                path = data,
                                duration = duration,
                                dateAdded = dateAdded
                            )
                            updatedVideos.add(updatedVideo)
                            hasChanges = true
                        }
                    }
                } ?: run {
                    Log.w(TAG, "MediaStore query returned null cursor")
                }

                // Check for deleted videos
                val deletedVideos = originalVideoList.filter { !newVideoIds.contains(it.id) }
                if (deletedVideos.isNotEmpty()) {
                    hasChanges = true
                    withContext(Dispatchers.Main) {
                        deletedVideos.forEach { video ->
                            originalVideoList.remove(video)
                            videoList.remove(video)
                        }
                        videoAdapter.updateList(videoList)
                        Log.d(TAG, "Removed ${deletedVideos.size} deleted videos")
                    }
                }

                if (hasChanges) {
                    if (updatedVideos.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            originalVideoList.addAll(updatedVideos.filter { originalVideoList.none { existing -> existing.id == it.id } })
                            updatedVideos.forEach { video ->
                                val index = originalVideoList.indexOfFirst { it.id == video.id }
                                if (index != -1) originalVideoList[index] = video
                            }
                            applyFiltersAndSort()
                            Log.d(TAG, "Updated ${updatedVideos.size} videos")
                        }

                        // Fetch TMDB data for new or updated videos
                        val updateJobs = updatedVideos.map { videoItem ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val (cleanedName, isTvShow, seasonEpisode) = cleanTitleForTmdb(
                                    videoItem.title
                                )
                                Log.d(
                                    TAG,
                                    "Processing video: ${videoItem.title}, Cleaned: '$cleanedName', isTvShow: $isTvShow, Season/Episode: $seasonEpisode"
                                )
                                if (cleanedName.isNotBlank()) {
                                    val tmdbData =
                                        retryTmdbQuery(cleanedName, isTvShow, seasonEpisode)
                                    if (tmdbData != null) {
                                        withContext(Dispatchers.Main) {
                                            val index =
                                                originalVideoList.indexOfFirst { it.id == videoItem.id }
                                            if (index != -1) {
                                                val updatedVideo =
                                                    videoItem.copy(tmdbData = tmdbData)
                                                originalVideoList[index] = updatedVideo
                                                Log.d(
                                                    TAG,
                                                    "Updated TMDB data for '${videoItem.title}' at index $index"
                                                )
                                                applyFiltersAndSort() // Re-group after TMDB update
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        updateJobs.forEach { it.join() }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "No changes detected, skipping refresh")
                    }
                }

                withContext(Dispatchers.Main) {
                    swipeRefreshLayout?.isRefreshing = false
                    isRefreshing = false
                    if (videoList.isEmpty()) {
                        noVideosText?.apply {
                            text = "No series found on device"
                            visibility = View.VISIBLE
                        }
                        recyclerView?.visibility = View.GONE
                        titleText?.visibility = View.VISIBLE
                        Toast.makeText(
                            this@VideoListActivity,
                            "No series found",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        noVideosText?.visibility = View.GONE
                        recyclerView?.visibility = View.VISIBLE
                        titleText?.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadVideos: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    noVideosText?.apply {
                        text = "Error loading videos: ${e.message}"
                        visibility = View.VISIBLE
                    }
                    recyclerView?.visibility = View.GONE
                    titleText?.visibility = View.VISIBLE
                    swipeRefreshLayout?.isRefreshing = false
                    isRefreshing = false
                    Toast.makeText(
                        this@VideoListActivity,
                        "Error loading videos",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun groupVideosBySeries(inputList: MutableList<VideoItem>) {
        videoList.clear()
        val seriesMap = inputList.groupBy {
            it.tmdbData?.let { data ->
                if (data.media_type == "tv") data.id.toString()
                else null
            } ?: run {
                val (cleanedTitle, isTvShow, _) = cleanTitleForTmdb(it.title)
                if (isTvShow) cleanedTitle else it.title
            }
        }

        seriesMap.entries.sortedBy { it.key }.forEach { (key, episodes) ->
            if (episodes.isNotEmpty()) {
                val isSeries = cleanTitleForTmdb(episodes.first().title).second
                if (isSeries) {
                    val cleanedTitle = cleanTitleForTmdb(episodes.first().title).first
                    // Validate episode titles
                    val mismatchedEpisodes = episodes.filter {
                        cleanTitleForTmdb(it.title).first != cleanedTitle
                    }
                    if (mismatchedEpisodes.isNotEmpty()) {
                        Log.w(TAG, "Mismatched episodes for series '$cleanedTitle': ${mismatchedEpisodes.map { it.title }}")
                    }
                    // Use folder name as fallback if TMDB data is missing
                    val folderName = File(episodes.first().path).parentFile?.name
                    val seriesTitle = episodes.first().tmdbData?.tvTitle?.takeIf { it == cleanedTitle } ?: cleanedTitle
                    videoList.add(
                        VideoItem(
                            uri = Uri.EMPTY,
                            title = seriesTitle,
                            id = -1,
                            isSeriesHeader = true,
                            groupCount = episodes.size,
                            tmdbData = episodes.first().tmdbData?.copy(season = null, episode = null)
                        )
                    )
                    Log.d(TAG, "Added series header: '$seriesTitle', folder: '$folderName', episodes: ${episodes.size}")
                } else {
                    videoList.addAll(episodes)
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private fun showDeleteConfirmation(video: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Video")
            .setMessage("Delete ${video.tmdbData?.displayTitle ?: video.title}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteVideo(video)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteVideo(video: VideoItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                    }
                } else {
                    val deleted = contentResolver.delete(uri, null, null) > 0
                    if (deleted) {
                        File(video.path).delete()
                        withContext(Dispatchers.Main) {
                            removeVideoFromList(video)
                            Toast.makeText(this@VideoListActivity, "Video deleted", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@VideoListActivity, "Failed to delete video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoListActivity, "Error deleting video", Toast.LENGTH_SHORT).show()
                }
                Log.e(TAG, "Delete error: ${e.message}", e)
            }
        }
    }

    private fun removeVideoFromList(video: VideoItem) {
        videoList.remove(video)
        originalVideoList.remove(video)
        videoAdapter.updateList(videoList)
        recyclerView?.adapter?.notifyDataSetChanged()
        Log.d(TAG, "Removed video from list: ${video.title}")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showRenameDialog(video: VideoItem, position: Int) {
        val input = EditText(this).apply {
            setText(video.tmdbData?.displayTitle ?: video.title.substringBeforeLast("."))
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Video")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameVideo(video, position, newName)
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun renameVideo(video: VideoItem, position: Int, newName: String) {
        val newFileName = if (video.title.substringAfterLast(".", "").isNotEmpty()) {
            "$newName.${video.title.substringAfterLast(".")}"
        } else {
            newName
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, newFileName)
                }

                val updated = contentResolver.update(videoUri, values, null, null) > 0
                if (!updated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val editIntentSender = MediaStore.createWriteRequest(contentResolver, listOf(videoUri)).intentSender
                        withContext(Dispatchers.Main) {
                            editLauncher.launch(IntentSenderRequest.Builder(editIntentSender).build())
                        }
                        return@launch
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating write request", e)
                    }
                }

                if (updated) {
                    withContext(Dispatchers.Main) {
                        updateVideoInList(position, newFileName)
                        Toast.makeText(this@VideoListActivity, "Video renamed successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoListActivity, "Failed to rename video", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming video", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateVideoInList(position: Int, newFileName: String) {
        val updatedVideo = videoList[position].copy(title = newFileName)
        videoList[position] = updatedVideo
        originalVideoList.replaceAll { if (it.id == updatedVideo.id) updatedVideo else it }
        videoAdapter.updateList(videoList)
        recyclerView?.adapter?.notifyDataSetChanged()
        Log.d(TAG, "Updated video in list: $newFileName")
    }

    data class VideoItem(
        val uri: Uri,
        val title: String,
        val id: Long,
        val duration: Long = 0,
        val dateAdded: Long = 0,
        val path: String = "",
        val isFavorite: Boolean = false,
        val isHeader: Boolean = false,
        val isSeriesHeader: Boolean = false,
        val groupCount: Int = 0,
        val tmdbData: TmdbMovie? = null
    ) :
        Parcelable {
        constructor(parcel: Parcel) : this(
            uri = parcel.readParcelable(Uri::class.java.classLoader)!!,
            title = parcel.readString()!!,
            id = parcel.readLong(),
            duration = parcel.readLong(),
            dateAdded = parcel.readLong(),
            path = parcel.readString()!!,
            isFavorite = parcel.readByte() != 0.toByte(),
            isHeader = parcel.readByte() != 0.toByte(),
            isSeriesHeader = parcel.readByte() != 0.toByte(),
            groupCount = parcel.readInt(),
            tmdbData = parcel.readParcelable(TmdbMovie::class.java.classLoader)
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(uri, flags)
            parcel.writeString(title)
            parcel.writeLong(id)
            parcel.writeLong(duration)
            parcel.writeLong(dateAdded)
            parcel.writeString(path)
            parcel.writeByte(if (isFavorite) 1 else 0)
            parcel.writeByte(if (isHeader) 1 else 0)
            parcel.writeByte(if (isSeriesHeader) 1 else 0)
            parcel.writeInt(groupCount)
            parcel.writeParcelable(tmdbData as Parcelable?, flags)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<VideoItem> {
            override fun createFromParcel(parcel: Parcel): VideoItem = VideoItem(parcel)
            override fun newArray(size: Int): Array<VideoItem?> = arrayOfNulls(size)
        }
    }

    class VideoAdapter(
        private val context: Context,
        private var videos: MutableList<VideoItem>,
        private val onClick: (VideoItem) -> Unit,
        private val updateOriginalList: (List<VideoItem>) -> Unit,
        private val updateUiVisibility: (Boolean) -> Unit,
        private val onDeleteRequested: (VideoItem) -> Unit,
        private val onRenameRequested: (VideoItem, Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var isGridMode = false

        companion object {
            private const val VIEW_TYPE_LIST = 0
            private const val VIEW_TYPE_GRID = 1
            internal const val VIEW_TYPE_HEADER = 2
            private const val TAG = "VideoAdapter"
        }

        fun setGridMode(isGrid: Boolean) {
            isGridMode = isGrid
            notifyDataSetChanged()
            Log.d(TAG, "Set grid mode: $isGrid")
        }

        fun updateList(newList: List<VideoItem>) {
            Log.d("AdapterDebug", "Updating list with ${newList.size} items")
            videos.clear()
            videos.addAll(newList)
            updateUiVisibility(videos.isEmpty())
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when {
            videos[position].isSeriesHeader -> VIEW_TYPE_GRID
            isGridMode -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            Log.d(TAG, "Creating view holder for viewType=$viewType")
            try {
                return when (viewType) {
                    VIEW_TYPE_HEADER -> HeaderViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
                    )
                    VIEW_TYPE_GRID -> GridViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.item_view_grid, parent, false)
                    )
                    else -> ListViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating view holder: ${e.message}", e)
                throw e
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val video = videos[position]
            Log.d(TAG, "Binding view holder at position=$position, title=${video.title}, poster_path=${video.tmdbData?.poster_path}")
            try {
                when (holder) {
                    is HeaderViewHolder -> {
                        holder.title.text = video.title
                        holder.count.text = "${video.groupCount} videos"
                        loadTmdbPoster(holder.thumbnail, video.tmdbData?.poster_path, video.title)
                        holder.itemView.setOnClickListener { onClick(video) }
                    }

                    is ListViewHolder -> {
                        val title = buildString {
                            val baseTitle = if (context is SeriesCollectionActivity && video.tmdbData?.media_type == "tv" && video.tmdbData.episode != null) {
                                video.tmdbData.movieTitle ?: video.title.replace("_", " ") // Use episode name
                            } else {
                                video.tmdbData?.displayTitle?.replace("_", " ") ?: video.title.replace("_", " ")
                            }
                            append(baseTitle)
                            if (video.tmdbData?.season != null && video.tmdbData.episode != null) {
                                append(" – S%02dE%02d".format(video.tmdbData.season, video.tmdbData.episode))
                            }
                        }
                        holder.title.text = title
                        holder.duration.text = formatDuration(video.duration)
                        holder.releaseDate.text = video.tmdbData?.displayDate ?: "N/A"
                        holder.rating.text = video.tmdbData?.vote_average?.let { "Rating: $it/10" } ?: "Rating: N/A"
                        loadTmdbPoster(holder.thumbnail, video.tmdbData?.poster_path, video.tmdbData?.displayTitle ?: video.title)
                        holder.itemView.setOnClickListener { if (!video.isHeader && !video.isSeriesHeader) onClick(video) }
                        setupMoreIcon(holder.moreIcon, video, position)
                    }
                    is GridViewHolder -> {
                        val title = buildString {
                            val baseTitle = if (context is SeriesCollectionActivity && video.tmdbData?.media_type == "tv" && video.tmdbData.episode != null) {
                                video.tmdbData.movieTitle ?: video.title.replace("_", " ") // Use episode name
                            } else {
                                video.tmdbData?.displayTitle?.replace("_", " ") ?: video.title.replace("_", " ")
                            }
                            append(baseTitle)
                            if (video.tmdbData?.season != null && video.tmdbData.episode != null) {
                                append(" – S%02dE%02d".format(video.tmdbData.season, video.tmdbData.episode))
                            }
                            if (video.isSeriesHeader) {
                                append(" (${video.groupCount} videos)")
                            }
                        }
                        holder.title.text = title
                        holder.releaseDate.text = video.tmdbData?.displayDate ?: "N/A"
                        holder.rating.text = video.tmdbData?.vote_average?.let { "Rating: $it/10" } ?: "Rating: N/A"
                        loadTmdbPoster(holder.thumbnail, video.tmdbData?.poster_path, video.tmdbData?.displayTitle ?: video.title)
                        holder.itemView.setOnClickListener { onClick(video) }
                        setupMoreIcon(holder.moreIcon, video, position)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding view holder at position=$position: ${e.message}", e)
            }
        }

        private fun loadTmdbPoster(imageView: ImageView, posterPath: String?, videoTitle: String) {
            try {
                if (posterPath != null && posterPath.isNotEmpty()) {
                    val imageUrl = "${TmdbClient.IMAGE_BASE_URL}$posterPath"
                    Log.d(TAG, "Loading poster for '$videoTitle': $imageUrl")
                    Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                        .apply(RequestOptions()
                            .override(300, 450)
                            .centerCrop())
                        .dontAnimate()
                        .into(imageView)
                } else {
                    Log.d(TAG, "No poster path for '$videoTitle', using placeholder")
                    Glide.with(context)
                        .load(R.drawable.placeholder)
                        .apply(RequestOptions()
                            .override(300, 450)
                            .centerCrop())
                        .dontAnimate()
                        .into(imageView)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading poster for '$videoTitle': ${e.message}", e)
                Glide.with(context)
                    .load(R.drawable.placeholder)
                    .apply(RequestOptions()
                        .override(300, 450)
                        .centerCrop())
                    .dontAnimate()
                    .into(imageView)
            }
        }

        private fun setupMoreIcon(moreIcon: ImageView, video: VideoItem, position: Int) {
            moreIcon.setOnClickListener { view ->
                val popupMenu = PopupMenu(context, view)
                popupMenu.menuInflater.inflate(R.menu.video_actions_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_rename -> {
                            onRenameRequested(video, position)
                            true
                        }
                        R.id.action_share -> {
                            shareVideo(video)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteRequested(video)
                            true
                        }
                        R.id.action_properties -> {
                            showProperties(video)
                            true
                        }
                        R.id.action_data -> {
                            showTmdbData(video)
                            true
                        }
                        else -> false
                    }
                }
                popupMenu.show()
            }
        }

        private fun shareVideo(video: VideoItem) {
            if (video.isHeader || video.isSeriesHeader) return
            try {
                val file = File(video.path)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing video: ${e.message}", e)
                Toast.makeText(context, "Error sharing video", Toast.LENGTH_SHORT).show()
            }
        }

        private fun showProperties(video: VideoItem) {
            if (video.isHeader || video.isSeriesHeader) {
                Toast.makeText(context, "Properties not available for headers", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val dateAdded = dateFormat.format(Date(video.dateAdded * 1000))
                val file = File(video.path)
                val sizeInMB = file.length() / (1024.0 * 1024.0)
                val properties = """
                    Title: ${video.tmdbData?.displayTitle ?: video.title}
                    Duration: ${formatDuration(video.duration)}
                    Date Added: $dateAdded
                    Path: ${video.path}
                    Size: ${"%.2f".format(sizeInMB)} MB
                """.trimIndent()

                AlertDialog.Builder(context)
                    .setTitle("Video Properties")
                    .setMessage(properties)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing properties: ${e.message}", e)
                Toast.makeText(context, "Error showing properties", Toast.LENGTH_SHORT).show()
            }
        }

        private fun showTmdbData(video: VideoItem) {
            if (video.isHeader || video.isSeriesHeader) {
                Toast.makeText(context, "Metadata not available for headers", Toast.LENGTH_SHORT).show()
                return
            }
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val tmdbData = video.tmdbData ?: run {
                        if (context is VideoListActivity) {
                            val (cleanedName, isTvShow, seasonEpisode) = cleanTitleForTmdb(video.title)
                            context.retryTmdbQuery(cleanedName, isTvShow, seasonEpisode)
                        } else {
                            Log.e(TAG, "Context is not VideoListActivity, cannot retry TMDB query")
                            null
                        }
                    }

                    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tmdb_data, null)
                    val dialog = AlertDialog.Builder(context)
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
                            Log.d(TAG, "Loading dialog poster for '${tmdbData.displayTitle}': $imageUrl")
                            Glide.with(context)
                                .load(imageUrl)
                                .placeholder(R.drawable.placeholder)
                                .error(R.drawable.placeholder)
                                .dontAnimate()
                                .into(posterImageView)
                        } else {
                            Log.d(TAG, "No poster for '${tmdbData.displayTitle}', using placeholder")
                            Glide.with(context)
                                .load(R.drawable.placeholder)
                                .dontAnimate()
                                .into(posterImageView)
                        }
                    } else {
                        dialogView.findViewById<TextView>(R.id.tmdb_title).text =
                            video.tmdbData?.displayTitle ?: video.title
                        dialogView.findViewById<TextView>(R.id.tmdb_type).text = "Unknown"
                        dialogView.findViewById<TextView>(R.id.tmdb_release_date).text = "Release Date: Not available"
                        dialogView.findViewById<TextView>(R.id.tmdb_rating).text = "Not rated"
                        dialogView.findViewById<TextView>(R.id.tmdb_overview).text =
                            "No metadata available for this video."
                        dialogView.findViewById<ImageView>(R.id.tmdb_poster).setImageResource(R.drawable.placeholder)
                    }

                    dialog.show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing TMDB data: ${e.message}", e)
                    Toast.makeText(context, "Error retrieving metadata", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun formatDuration(duration: Long): String {
            try {
                val seconds = (duration / 1000) % 60
                val minutes = (duration / (1000 * 60)) % 60
                val hours = duration / (1000 * 60 * 60)
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting duration: ${e.message}", e)
                return "00:00:00"
            }
        }

        override fun getItemCount() = videos.size

        class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
            val duration: TextView = itemView.findViewById(R.id.videoDuration)
            val releaseDate: TextView = itemView.findViewById(R.id.tmdbReleaseDate)
            val rating: TextView = itemView.findViewById(R.id.tmdbRating)
            val moreIcon: ImageView = itemView.findViewById(R.id.moreIcon)
        }

        class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
            val releaseDate: TextView = itemView.findViewById(R.id.tmdbReleaseDate)
            val rating: TextView = itemView.findViewById(R.id.tmdbRating)
            val moreIcon: ImageView = itemView.findViewById(R.id.moreIcon)
        }

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.groupThumbnail)
            val title: TextView = itemView.findViewById(R.id.groupTitle)
            val count: TextView = itemView.findViewById(R.id.groupCount)
        }
    }
}