package com.wilhelmbeiche.roommemory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Keeps item photos in app-private storage so they survive restarts.
 * URIs from the photo picker are only readable temporarily, so picked images are copied here.
 * Stored photos are downscaled: full-resolution camera shots take several MB each.
 */
object ImageStorage {
    private const val DIR_NAME = "images"
    private const val STORED_MAX_SIZE = 2048
    private const val JPEG_QUALITY = 90

    private fun imageDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun newImageFile(context: Context): File =
        File(imageDir(context), "${UUID.randomUUID()}.jpg")

    /** Copies the image behind [source] into app storage (downscaled) and returns the stored image's URI string. */
    fun importImage(context: Context, source: Uri): String? {
        val bitmap = try {
            loadScaled(context, source, STORED_MAX_SIZE)
        } catch (e: Exception) {
            null
        } ?: return null
        return saveBitmap(context, bitmap)
    }

    /** Stores [bitmap] as JPEG and returns the stored image's URI string. */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        val target = newImageFile(context)
        return try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
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

    /** Re-encodes a stored photo (e.g. straight from the camera) downscaled and upright, in place. */
    fun shrink(context: Context, uriString: String) {
        val file = storedFile(context, uriString) ?: return
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            val bitmap = loadScaled(context, Uri.fromFile(file), STORED_MAX_SIZE) ?: return
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            // The photo may have been discarded meanwhile; don't bring it back.
            if (file.exists()) temp.renameTo(file)
        } catch (e: Exception) {
            // Keep the original; it is just bigger than needed.
        } finally {
            temp.delete()
        }
    }

    /** Deletes the image if it lives in app storage; foreign URIs are left alone. */
    fun delete(context: Context, uriString: String) {
        storedFile(context, uriString)?.delete()
    }

    /** The file behind [uriString] if it is one of ours. */
    private fun storedFile(context: Context, uriString: String): File? {
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        return file.takeIf { it.parentFile?.canonicalPath == imageDir(context).canonicalPath }
    }

    /** Decodes the image at most [maxSize] px on its longer side, rotated upright. Null if it can't be read. */
    fun loadScaled(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Decoding bounds only always returns null; the result is in [bounds].
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Subsample by powers of two while staying above maxSize, then scale exactly below.
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSize) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxSize.toFloat() / maxOf(decoded.width, decoded.height))
        if (rotation == 0f && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    data class CameraTarget(val contentUri: Uri, val storedUri: String)
}
