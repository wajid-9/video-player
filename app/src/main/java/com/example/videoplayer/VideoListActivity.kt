package com.example.videoplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoListActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "VideoPlayerPrefs"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val VIEW_MODE_LIST = 0
        private const val VIEW_MODE_GRID = 1
        private const val REQUEST_CODE_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var noVideosText: TextView
    private lateinit var viewModeSpinner: Spinner
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var sharedPref: SharedPreferences
    private var videoList = mutableListOf<VideoItem>()
    private var originalVideoList = mutableListOf<VideoItem>()
    private var isGrouped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_list)

        recyclerView = findViewById(R.id.recyclerView)
        noVideosText = findViewById(R.id.noVideosText)
        viewModeSpinner = findViewById(R.id.viewModeSpinner)
        sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Check and request permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        } else {
            loadVideos()
        }

        // Set up spinner options
        val spinnerOptions = arrayOf(
            "Display in list",
            "Display in grid",
            "Show only favourites",
            "Group videos",
            "Sort by name A→Z",
            "Sort by name Z→A",
            "Sort by length Shortest first",
            "Sort by length Longest first",
            "Sort by date Newest first",
            "Sort by date Oldest first"
        )

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerOptions
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewModeSpinner.adapter = spinnerAdapter

        // Restore saved view mode
        val savedViewMode = sharedPref.getInt(KEY_VIEW_MODE, VIEW_MODE_LIST)
        when (savedViewMode) {
            VIEW_MODE_GRID -> {
                recyclerView.layoutManager = GridLayoutManager(this, 2)
                viewModeSpinner.setSelection(1)
            }
            else -> {
                recyclerView.layoutManager = LinearLayoutManager(this)
                viewModeSpinner.setSelection(0)
            }
        }

        viewModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                with(sharedPref.edit()) {
                    when (position) {
                        0 -> { // Display in list
                            recyclerView.layoutManager = LinearLayoutManager(this@VideoListActivity)
                            videoAdapter.setGridMode(false)
                            isGrouped = false
                            videoAdapter.updateList(originalVideoList)
                            putInt(KEY_VIEW_MODE, VIEW_MODE_LIST)
                        }
                        1 -> { // Display in grid
                            recyclerView.layoutManager = GridLayoutManager(this@VideoListActivity, 2)
                            videoAdapter.setGridMode(true)
                            isGrouped = false
                            videoAdapter.updateList(originalVideoList)
                            putInt(KEY_VIEW_MODE, VIEW_MODE_GRID)
                        }
                        2 -> filterFavorites()
                        3 -> groupVideos()
                        4 -> sortVideosByName(ascending = true)
                        5 -> sortVideosByName(ascending = false)
                        6 -> sortVideosByLength(ascending = true)
                        7 -> sortVideosByLength(ascending = false)
                        8 -> sortVideosByDate(ascending = false)
                        9 -> sortVideosByDate(ascending = true)
                    }
                    apply()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos()
            } else {
                Toast.makeText(this, "Permissions denied. Cannot load videos.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadVideos() {
        videoList.clear()
        originalVideoList.clear()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )

        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val data = it.getString(dataColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val duration = it.getLong(durationColumn)
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                videoList.add(VideoItem(uri, name, id, duration, dateAdded, data))
                originalVideoList.add(VideoItem(uri, name, id, duration, dateAdded, data))
            }
        }

        if (videoList.isEmpty()) {
            noVideosText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noVideosText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            videoAdapter = VideoAdapter(
                context = this,
                videos = videoList,
                onClick = { uri ->
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("VIDEO_URI", uri.toString()) // Ensure URI is passed as String
                    })
                },
                updateOriginalList = { newList ->
                    originalVideoList = newList.toMutableList()
                },
                updateUiVisibility = { isListEmpty ->
                    if (isListEmpty) {
                        recyclerView.visibility = View.GONE
                        noVideosText.visibility = View.VISIBLE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        noVideosText.visibility = View.GONE
                    }
                }
            )
            recyclerView.adapter = videoAdapter
        }
    }

    private fun filterFavorites() {
        val filteredList = originalVideoList.filter { it.isFavorite }
        if (filteredList.isEmpty()) {
            noVideosText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noVideosText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            videoAdapter.updateList(filteredList)
        }
    }

    private fun groupVideos() {
        isGrouped = true
        val groupedMap = originalVideoList.groupBy { it.title.substring(0, 1).uppercase() }
        val groupedList = mutableListOf<VideoItem>()

        groupedMap.entries.sortedBy { it.key }.forEach { entry ->
            groupedList.add(VideoItem(
                uri = Uri.EMPTY,
                title = entry.key,
                id = -1,
                isHeader = true,
                groupCount = entry.value.size
            ))
            groupedList.addAll(entry.value.sortedBy { it.title })
        }

        videoAdapter.updateList(groupedList)
    }

    private fun sortVideosByName(ascending: Boolean) {
        isGrouped = false
        videoAdapter.updateList(if (ascending) originalVideoList.sortedBy { it.title }
        else originalVideoList.sortedByDescending { it.title })
    }

    private fun sortVideosByLength(ascending: Boolean) {
        isGrouped = false
        videoAdapter.updateList(if (ascending) originalVideoList.sortedBy { it.duration }
        else originalVideoList.sortedByDescending { it.duration })
    }

    private fun sortVideosByDate(ascending: Boolean) {
        isGrouped = false
        videoAdapter.updateList(if (ascending) originalVideoList.sortedBy { it.dateAdded }
        else originalVideoList.sortedByDescending { it.dateAdded })
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
        val groupCount: Int = 0
    )

    class VideoAdapter(
        private val context: Context,
        private var videos: List<VideoItem>,
        private val onClick: (Uri) -> Unit,
        private val updateOriginalList: (List<VideoItem>) -> Unit,
        private val updateUiVisibility: (Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var isGridMode = false

        companion object {
            private const val VIEW_TYPE_LIST = 0
            private const val VIEW_TYPE_GRID = 1
            private const val VIEW_TYPE_HEADER = 2
        }

        fun setGridMode(isGrid: Boolean) {
            isGridMode = isGrid
            notifyDataSetChanged()
        }

        fun updateList(newList: List<VideoItem>) {
            videos = newList
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when {
            videos[position].isHeader -> VIEW_TYPE_HEADER
            isGridMode -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_group_header, parent, false)
            )
            VIEW_TYPE_GRID -> GridViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_grid, parent, false)
            )
            else -> ListViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_video, parent, false)
            )
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val video = videos[position]
            when (holder) {
                is HeaderViewHolder -> {
                    holder.title.text = video.title
                    holder.count.text = "${video.groupCount} videos in group"
                }
                is ListViewHolder -> {
                    holder.title.text = video.title
                    holder.duration.text = formatDuration(video.duration)
                    loadThumbnail(holder.thumbnail, video.id)
                    holder.itemView.setOnClickListener { onClick(video.uri) }
                    setupMoreIcon(holder.moreIcon, video, position)
                }
                is GridViewHolder -> {
                    holder.title.text = video.title
                    loadThumbnail(holder.thumbnail, video.id)
                    holder.itemView.setOnClickListener { onClick(video.uri) }
                    setupMoreIcon(holder.moreIcon, video, position)
                }
            }
        }

        private fun setupMoreIcon(moreIcon: ImageView, video: VideoItem, position: Int) {
            moreIcon.setOnClickListener { view ->
                val popupMenu = PopupMenu(context, view)
                popupMenu.menuInflater.inflate(R.menu.video_actions_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_rename -> {
                            renameVideo(video, position)
                            true
                        }
                        R.id.action_share -> {
                            shareVideo(video)
                            true
                        }
                        R.id.action_delete -> {
                            deleteVideo(video, position)
                            true
                        }
                        R.id.action_properties -> {
                            showProperties(video)
                            true
                        }
                        else -> false
                    }
                }
                popupMenu.show()
            }
        }

        private fun renameVideo(video: VideoItem, position: Int) {
            val dialog = AlertDialog.Builder(context)
            dialog.setTitle("Rename Video")

            val input = EditText(context)
            input.setText(video.title)
            dialog.setView(input)

            dialog.setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val file = File(video.path)
                    val parentDir = file.parentFile
                    val newFile = File(parentDir, "$newName${file.extension}")
                    if (file.renameTo(newFile)) {
                        val updatedVideo = video.copy(
                            title = newName,
                            path = newFile.absolutePath
                        )
                        videos = videos.toMutableList().apply { set(position, updatedVideo) }
                        val updatedOriginalList = videos.map { item ->
                            if (item.id == video.id) updatedVideo else item
                        }
                        updateOriginalList(updatedOriginalList)
                        notifyItemChanged(position)
                        Toast.makeText(context, "Video renamed successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to rename video", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            dialog.show()
        }

        private fun shareVideo(video: VideoItem) {
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
        }

        private fun deleteVideo(video: VideoItem, position: Int) {
            AlertDialog.Builder(context)
                .setTitle("Delete Video")
                .setMessage("Are you sure you want to delete ${video.title}?")
                .setPositiveButton("Yes") { _, _ ->
                    val file = File(video.path)
                    if (file.delete()) {
                        videos = videos.toMutableList().apply { removeAt(position) }
                        val updatedOriginalList = videos.filter { it.id != video.id }
                        updateOriginalList(updatedOriginalList)
                        notifyItemRemoved(position)
                        updateUiVisibility(videos.isEmpty())
                        Toast.makeText(context, "Video deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete video", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        private fun showProperties(video: VideoItem) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateAdded = dateFormat.format(Date(video.dateAdded * 1000))
            val file = File(video.path)
            val sizeInMB = file.length() / (1024.0 * 1024.0)
            val properties = """
            Title: ${video.title}
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
        }

        private fun formatDuration(duration: Long): String {
            val seconds = (duration / 1000) % 60
            val minutes = (duration / (1000 * 60)) % 60
            val hours = duration / (1000 * 60 * 60)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun loadThumbnail(imageView: ImageView, videoId: Long) {
            if (videoId == -1L) {
                imageView.setImageResource(R.drawable.folder)
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                val thumbnail = try {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        imageView.context.contentResolver,
                        videoId,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    thumbnail?.let {
                        imageView.setImageBitmap(it)
                    } ?: run {
                        imageView.setImageDrawable(
                            ContextCompat.getDrawable(imageView.context, R.drawable.play)
                        )
                    }
                }
            }
        }

        override fun getItemCount() = videos.size

        class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
            val duration: TextView = itemView.findViewById(R.id.videoDuration)
            val moreIcon: ImageView = itemView.findViewById(R.id.moreIcon)
        }

        class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
            val title: TextView = itemView.findViewById(R.id.videoTitle)
            val moreIcon: ImageView = itemView.findViewById(R.id.moreIcon)
        }

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.groupTitle)
            val count: TextView = itemView.findViewById(R.id.groupCount)
        }
    }
}