package com.example.videoplayer

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {
    fun getPath(context: Context, uri: Uri): String {
        // Try querying MediaStore first
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        var path: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        if (path != null) return path!!

        // If that fails, copy the file to cache and return its path
        val fileName = getFileName(context, uri)
        val tempFile = File(context.cacheDir, fileName)
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(tempFile)

        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        return tempFile.absolutePath
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "temp_file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }
}
