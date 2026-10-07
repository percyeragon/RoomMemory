package com.wilhelmbeiche.roommemory.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Keeps item photos in app-private storage so they survive restarts.
 * URIs from the photo picker are only readable temporarily, so picked images are copied here.
 */
object ImageStorage {
    private const val DIR_NAME = "images"

    private fun imageDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun newImageFile(context: Context): File =
        File(imageDir(context), "${UUID.randomUUID()}.jpg")

    /** Copies the image behind [source] into app storage and returns the stored image's URI string. */
    fun importImage(context: Context, source: Uri): String? {
        val target = newImageFile(context)
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Uri.fromFile(target).toString()
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /** Stores [bitmap] as JPEG and returns the stored image's URI string. */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        val target = newImageFile(context)
        return try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Uri.fromFile(target).toString()
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /** Creates an empty file for the camera to write into. */
    fun createCameraTarget(context: Context): CameraTarget {
        val file = newImageFile(context)
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraTarget(contentUri = contentUri, storedUri = Uri.fromFile(file).toString())
    }

    /** Deletes the image if it lives in app storage; foreign URIs are left alone. */
    fun delete(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return
        val file = File(uri.path ?: return)
        if (file.parentFile?.canonicalPath == imageDir(context).canonicalPath) {
            file.delete()
        }
    }

    data class CameraTarget(val contentUri: Uri, val storedUri: String)
}
