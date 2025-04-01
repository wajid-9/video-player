package com.example.videoplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noVideosText: TextView
    private lateinit var viewModeSpinner: Spinner
    private lateinit var videoAdapter: VideoAdapter
    private var videoList = mutableListOf<VideoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_list)

        recyclerView = findViewById(R.id.recyclerView)
        noVideosText = findViewById(R.id.noVideosText)
        viewModeSpinner = findViewById(R.id.viewModeSpinner)

        // Set default layout manager to LinearLayoutManager (List)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadVideos()

        // Spinner listener to switch between List and Grid
        viewModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // List
                        recyclerView.layoutManager = LinearLayoutManager(this@VideoListActivity)
                        videoAdapter.setGridMode(false)
                    }
                    1 -> { // Grid
                        recyclerView.layoutManager = GridLayoutManager(this@VideoListActivity, 2)
                        videoAdapter.setGridMode(true)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadVideos() {
        videoList.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val data = it.getString(dataColumn)
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                videoList.add(VideoItem(uri, name, id))
            }
        }

        if (videoList.isEmpty()) {
            noVideosText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noVideosText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            videoAdapter = VideoAdapter(videoList) { uri ->
                // Start MainActivity with the selected video URI
                val intent = Intent(this@VideoListActivity, MainActivity::class.java).apply {
                    putExtra("VIDEO_URI", uri)
                }
                startActivity(intent)
                finish() // Optional: finish VideoListActivity to prevent going back
            }
            recyclerView.adapter = videoAdapter
        }
    }

    data class VideoItem(val uri: Uri, val title: String, val id: Long)

    class VideoAdapter(
        private val videos: List<VideoItem>,
        private val onClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var isGridMode = false

        companion object {
            private const val VIEW_TYPE_LIST = 0
            private const val VIEW_TYPE_GRID = 1
        }

        fun setGridMode(isGrid: Boolean) {
            isGridMode = isGrid
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_GRID) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_grid, parent, false)
                GridViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_video, parent, false)
                ListViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val video = videos[position]

            when (holder) {
                is ListViewHolder -> {
                    holder.title.text = video.title
                    loadThumbnail(holder.thumbnail, video.id)
                    holder.itemView.setOnClickListener { onClick(video.uri) }
                }
                is GridViewHolder -> {
                    holder.title.text = video.title
                    loadThumbnail(holder.thumbnail, video.id)
                    holder.itemView.setOnClickListener { onClick(video.uri) }
                }
            }
        }

        private fun loadThumbnail(imageView: ImageView, videoId: Long) {
            CoroutineScope(Dispatchers.IO).launch {
                val thumbnail = getVideoThumbnail(imageView.context, videoId)
                withContext(Dispatchers.Main) {
                    thumbnail?.let {
                        imageView.setImageBitmap(it)
                    } ?: run {
                        imageView.setImageResource(R.drawable.play)
                    }
                }
            }
        }

        override fun getItemCount(): Int = videos.size

        class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
        }

        class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
        }

        private fun getVideoThumbnail(context: Context, videoId: Long): Bitmap? {
            return try {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}