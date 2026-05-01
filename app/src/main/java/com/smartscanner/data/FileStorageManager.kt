package com.smartscanner.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

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

    suspend fun renamePhysicalFile(context: Context, currentPath: String, newName: String): String? = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(currentPath)
            if (!oldFile.exists()) return@withContext null
            
            val newFile = File(oldFile.parent, newName)
            if (oldFile.renameTo(newFile)) {
                scanFile(context, oldFile.absolutePath)
                scanFile(context, newFile.absolutePath)
                newFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun scanFile(context: Context, filePath: String): Uri? = suspendCancellableCoroutine { continuation ->
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            null
        ) { _, uri ->
            continuation.resume(uri)
        }
    }

    fun getContentUriFromPath(context: Context, filePath: String): Uri? {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = MediaStore.Files.FileColumns.DATA + "=?"
        val selectionArgs = arrayOf(filePath)

        return try {
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    ContentUris.withAppendedId(uri, id)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
            null
        }
    }

    suspend fun convertImagesToPdf(context: Context, imageUris: List<Uri>, outputFileName: String): String? = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        
        try {
            imageUris.forEachIndexed { index, uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas: Canvas = page.canvas
                        canvas.drawBitmap(bitmap, 0f, 0f, paint)
                        pdfDocument.finishPage(page)
                        bitmap.recycle()
                    }
                }
            }
            
            val finalName = if (outputFileName.lowercase().endsWith(".pdf")) outputFileName else "$outputFileName.pdf"
            val file = File(context.filesDir, finalName)
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error creating PDF", e)
            null
        } finally {
            pdfDocument.close()
        }
    }
}
