package com.smartscanner.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object FileStorageManager {

    private const val TAG = "FileStorageManager"

    suspend fun saveImageToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        val directory = context.filesDir
        val file = File(directory, if (fileName.endsWith(".jpg")) fileName else "$fileName.jpg")
        
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error saving image", e)
            null
        } finally {
            fos?.close()
        }
    }

    suspend fun saveFileFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri) ?: "imported_${System.currentTimeMillis()}"
        val file = File(context.filesDir, fileName)
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file from Uri", e)
            null
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) name = name?.substring(cut + 1)
        }
        return name
    }

    suspend fun deletePhysicalFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }
}
