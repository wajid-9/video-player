package com.example.videoplayer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.bumptech.glide.Glide
import com.example.videoplayer.VideoListActivity.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class SeriesCollectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SeriesCollectionActivity"
        private const val TMDB_API_KEY = "f8a4820def9b2c491b5526997a764aa3"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w1280" // Backdrop size
        private const val TMDB_POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500" // Poster size
    }

    private var recyclerView: RecyclerView? = null
    private var noVideosText: TextView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var titleText: TextView? = null
    private var seriesInfo: TextView? = null
    private var releaseDate: TextView? = null
    private var rating: TextView? = null
    private var headerBackground: ImageView? = null
    private lateinit var videoAdapter: VideoListActivity.VideoAdapter
    private val videoList = mutableListOf<VideoItem>()
    private var isRefreshing = false
    private val originalEpisodes = mutableListOf<VideoItem>()

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Episode deleted successfully", Toast.LENGTH_SHORT).show()
            loadEpisodes()
        } else {
            Toast.makeText(this, "Deletion cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    // TMDB API Interface
    interface TmdbApi {
        @GET("search/tv")
        suspend fun searchTvShow(
            @Query("api_key") apiKey: String,
            @Query("query") query: String
        ): TmdbResponse

        @GET("tv/{tv_id}")
        suspend fun getTvShowDetails(
            @Path("tv_id") tvId: Int,
            @Query("api_key") apiKey: String
        ): TmdbTvShowDetails
    }

    data class TmdbResponse(val results: List<TmdbTvShow>)
    data class TmdbTvShow(val id: Int, val name: String, val backdrop_path: String?, val poster_path: String?)
    data class TmdbTvShowDetails(val backdrop_path: String?, val poster_path: String?, val first_air_date: String?, val vote_average: Double?)

    private val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_collection)

        recyclerView = findViewById(R.id.recyclerView1)
        noVideosText = findViewById(R.id.noVideosText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        titleText = findViewById(R.id.titleText)
        seriesInfo = findViewById(R.id.seriesInfo)
        releaseDate = findViewById(R.id.releaseDate)
        rating = findViewById(R.id.rating)
        headerBackground = findViewById(R.id.headerBackground)

        if (titleText == null || recyclerView == null || noVideosText == null || swipeRefreshLayout == null ||
            seriesInfo == null || releaseDate == null || rating == null || headerBackground == null) {
            Log.e(TAG, "One or more UI components are null")
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_LONG).show()
            return
        }

        // Remove violet status bar
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        recyclerView?.visibility = View.VISIBLE
        noVideosText?.visibility = View.GONE

        val seriesTitle = intent.getStringExtra("SERIES_TITLE") ?: "Unknown Series"
        val episodes = intent.getParcelableArrayListExtra<VideoItem>("EPISODES") ?: emptyList()
        Log.d(TAG, "Received series: $seriesTitle, episodes: ${episodes.size}")

        // Dynamically load background from TMDB API
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tmdbData = fetchTmdbData(seriesTitle)
                withContext(Dispatchers.Main) {
                    if (tmdbData?.backdrop_path != null) {
                        Glide.with(this@SeriesCollectionActivity)
                            .load("${TMDB_IMAGE_BASE_URL}${tmdbData.backdrop_path}")
                            .placeholder(R.drawable.placeholder)
                            .error(R.drawable.placeholder)
                            .into(headerBackground!!)
                    } else {
                        headerBackground?.setImageResource(R.drawable.placeholder)
                    }
                    // Update header data with TMDB data
                    titleText?.text = seriesTitle
                    seriesInfo?.text = "${episodes.size} videos"
                    releaseDate?.text = tmdbData?.first_air_date ?: "N/A"
                    rating?.text = tmdbData?.vote_average?.let { "Rating: $it/10" } ?: "Rating: N/A"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching TMDB data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    headerBackground?.setImageResource(R.drawable.placeholder)
                    titleText?.text = seriesTitle
                    seriesInfo?.text = "${episodes.size} videos"
                    releaseDate?.text = "N/A"
                    rating?.text = "Rating: N/A"
                }
            }
        }

        recyclerView?.layoutManager = LinearLayoutManager(this)

        videoAdapter = VideoListActivity.VideoAdapter(
            context = this,
            videos = episodes as MutableList<VideoItem>,
            onClick = { video ->
                if (!video.isSeriesHeader) {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("VIDEO_URI", video.uri.toString())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                    Animatoo.animateSlideLeft(this) // Add slide-left animation for MainActivity start
                }

            },
            updateOriginalList = { newList ->
                videoList.clear()
                videoList.addAll(newList)
                Log.d(TAG, "Updated videoList with ${videoList.size} items")
            },
            updateUiVisibility = { isListEmpty ->
                updateUiVisibility(isListEmpty)
            },
            onDeleteRequested = { video ->
                showDeleteConfirmation(video)
            },
            onRenameRequested = { video, position ->
                showRenameDialog(video, position)
            }
        )
        recyclerView?.adapter = videoAdapter

        if (episodes.isNotEmpty()) {
            originalEpisodes.clear()
            originalEpisodes.addAll(episodes)
            groupEpisodesBySeason(episodes)
            videoAdapter.updateList(videoList)
            updateUiVisibility(videoList.isEmpty())
        } else {
            updateUiVisibility(true)
            Log.w(TAG, "No episodes received")
            Toast.makeText(this, "No episodes found for $seriesTitle", Toast.LENGTH_SHORT).show()
        }

        swipeRefreshLayout?.setOnRefreshListener {
            Log.d(TAG, "Swipe refresh triggered")
            if (!isRefreshing) {
                loadEpisodes()
            }
        }
    }

    override fun onBackPressed() {
        Animatoo.animateSlideLeft(this)
        super.onBackPressed()
    }

    private suspend fun fetchTmdbData(seriesTitle: String): TmdbTvShowDetails? {
        return try {
            // Clean the title for better TMDB matching
            val cleanedTitle = seriesTitle.replace(Regex("[^a-zA-Z0-9\\s/]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .replace("Sex Life", "Sex/Life") // Special case for Sex/Life
            Log.d(TAG, "Searching TMDB for cleaned title: '$cleanedTitle'")

            val response = tmdbApi.searchTvShow(TMDB_API_KEY, cleanedTitle)
            val tvShow = response.results.firstOrNull {
                it.name.equals(cleanedTitle, ignoreCase = true) ||
                        it.name.replace("/", "").equals(cleanedTitle.replace("/", ""), ignoreCase = true)
            }
            if (tvShow != null) {
                Log.d(TAG, "TMDB match found: id=${tvShow.id}, name=${tvShow.name}, backdrop=${tvShow.backdrop_path}")
                tmdbApi.getTvShowDetails(tvShow.id, TMDB_API_KEY)
            } else {
                Log.w(TAG, "No TMDB match for '$cleanedTitle', trying broader search")
                // Fallback to broader search by removing year or special characters
                val broaderTitle = cleanedTitle.replace(Regex("\\b\\d{4}\\b"), "").trim()
                val broaderResponse = tmdbApi.searchTvShow(TMDB_API_KEY, broaderTitle)
                val fallbackShow = broaderResponse.results.firstOrNull {
                    it.name.contains("Sex/Life", ignoreCase = true) ||
                            it.name.replace("/", "").contains("Sex Life", ignoreCase = true)
                }
                if (fallbackShow != null) {
                    Log.d(TAG, "Fallback TMDB match: id=${fallbackShow.id}, name=${fallbackShow.name}")
                    tmdbApi.getTvShowDetails(fallbackShow.id, TMDB_API_KEY)
                } else {
                    Log.w(TAG, "No TMDB match after fallback for '$broaderTitle'")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDB search failed: ${e.message}", e)
            null
        }
    }
    private fun loadEpisodes() {
        Log.d(TAG, "loadEpisodes started")
        if (isRefreshing) {
            Log.d(TAG, "Already refreshing, ignoring loadEpisodes")
            return
        }
        isRefreshing = true
        swipeRefreshLayout?.isRefreshing = true

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val episodes = intent.getParcelableArrayListExtra<VideoItem>("EPISODES") ?: emptyList()
                Log.d(TAG, "Received episodes: ${episodes.size} items")
                if (episodes.isEmpty()) {
                    Log.w(TAG, "EPISODES extra is null or empty")
                    withContext(Dispatchers.Main) {
                        videoList.clear()
                        videoAdapter.updateList(videoList)
                        updateUiVisibility(true)
                        Toast.makeText(this@SeriesCollectionActivity, "No episodes found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    originalEpisodes.clear()
                    originalEpisodes.addAll(episodes)
                    val sortedEpisodes = episodes.sortedWith(compareBy(
                        { it.tmdbData?.season ?: Int.MAX_VALUE },
                        { it.tmdbData?.episode ?: Int.MAX_VALUE }
                    ))
                    groupEpisodesBySeason(sortedEpisodes)
                    videoAdapter.updateList(videoList)
                    // Refresh header data
                    val seriesTitle = intent.getStringExtra("SERIES_TITLE") ?: "Unknown Series"
                    val tmdbData = fetchTmdbData(seriesTitle)
                    withContext(Dispatchers.Main) {
                        if (tmdbData?.backdrop_path != null) {
                            Glide.with(this@SeriesCollectionActivity)
                                .load("${TMDB_IMAGE_BASE_URL}${tmdbData.backdrop_path}")
                                .placeholder(R.drawable.placeholder)
                                .error(R.drawable.placeholder)
                                .into(headerBackground!!)
                        } else {
                            headerBackground?.setImageResource(R.drawable.placeholder)
                        }
                        seriesInfo?.text = "${sortedEpisodes.size} videos"
                        releaseDate?.text = tmdbData?.first_air_date ?: "N/A"
                        rating?.text = tmdbData?.vote_average?.let { "Rating: $it/10" } ?: "Rating: N/A"
                        Log.d(TAG, "Updated adapter with ${videoList.size} episodes")
                    }
                }

                withContext(Dispatchers.Main) {
                    swipeRefreshLayout?.isRefreshing = false
                    isRefreshing = false
                    updateUiVisibility(videoList.isEmpty())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadEpisodes: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateUiVisibility(true)
                    noVideosText?.text = "Error loading episodes: ${e.message}"
                    swipeRefreshLayout?.isRefreshing = false
                    isRefreshing = false
                    Toast.makeText(this@SeriesCollectionActivity, "Error loading episodes", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun groupEpisodesBySeason(episodes: List<VideoItem>) {
        val seasonMap = episodes
            .filter { it.tmdbData?.season != null && it.tmdbData?.episode != null } // Exclude series summaries
            .groupBy { it.tmdbData?.season ?: 0 }
        videoList.clear()

        seasonMap.entries.sortedBy { it.key }.forEach { (season, seasonEpisodes) ->
            if (seasonEpisodes.isNotEmpty()) {
                // Sort episodes by episode number in ascending order
                val sortedEpisodes = seasonEpisodes.sortedBy { it.tmdbData?.episode ?: Int.MAX_VALUE }
                // Add only the sorted episodes
                videoList.addAll(sortedEpisodes)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private fun showDeleteConfirmation(video: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Episode")
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
                            videoList.remove(video)
                            originalEpisodes.remove(video)
                            videoAdapter.updateList(videoList)
                            updateUiVisibility(videoList.isEmpty())
                            Toast.makeText(this@SeriesCollectionActivity, "Episode deleted", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SeriesCollectionActivity, "Failed to delete episode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SeriesCollectionActivity, "Error deleting episode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showRenameDialog(video: VideoItem, position: Int) {
        val input = EditText(this).apply {
            setText(video.tmdbData?.displayTitle ?: video.title.substringBeforeLast("."))
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Episode")
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
                if (updated) {
                    withContext(Dispatchers.Main) {
                        val updatedVideo = video.copy(title = newFileName)
                        videoList[position] = updatedVideo
                        originalEpisodes[originalEpisodes.indexOfFirst { it.id == video.id }] = updatedVideo
                        videoAdapter.updateList(videoList)
                        updateUiVisibility(videoList.isEmpty())
                        Toast.makeText(this@SeriesCollectionActivity, "Episode renamed successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SeriesCollectionActivity, "Failed to rename episode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming episode: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SeriesCollectionActivity, "Error renaming episode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUiVisibility(isListEmpty: Boolean) {
        Log.d(TAG, "Updating UI visibility: isListEmpty=$isListEmpty")
        recyclerView?.visibility = if (isListEmpty) View.GONE else View.VISIBLE
        noVideosText?.apply {
            text = if (isListEmpty) "No episodes found for ${titleText?.text}" else ""
            visibility = if (isListEmpty) View.VISIBLE else View.GONE
        }
    }
}
