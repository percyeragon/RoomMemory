package com.wilhelmbeiche.roommemory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A thing found on a photo: a suggested name and the cut-out part of the photo showing it. */
data class DetectedItem(val name: String, val image: Bitmap)

/** The (downscaled, upright) photo and what was found on it. */
data class PhotoAnalysis(val photo: Bitmap, val items: List<DetectedItem>)

/**
 * Finds objects on a photo with ML Kit, entirely on the device:
 * the object detector locates up to five objects, the image labeler names each cut-out,
 * and the translator turns the English labels into the device language (once its model is downloaded).
 */
class PhotoAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_LABEL_CONFIDENCE).build()
    )

    private val locale: Locale = Locale.getDefault()

    // ML Kit labels are English; translate only if the device uses another language ML Kit supports.
    private val translator = TranslateLanguage.fromLanguageTag(locale.language)
        ?.takeIf { it != TranslateLanguage.ENGLISH }
        ?.let { target ->
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(target)
                    .build()
            )
        }

    @Volatile
    private var translatorReady = false

    /** Downloads the translation model (~30 MB) if one is needed. Safe to call repeatedly. */
    suspend fun prepareTranslation() {
        val translator = translator ?: return
        if (translatorReady) return
        translatorReady = try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Returns null if the photo cannot be read. */
    suspend fun analyze(photo: Uri): PhotoAnalysis? = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(photo) ?: return@withContext null
        val objects = objectDetector.process(InputImage.fromBitmap(bitmap, 0)).await()

        val items = objects
            .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
            .mapNotNull { obj ->
                val crop = crop(bitmap, obj.boundingBox) ?: return@mapNotNull null
                val name = bestLabel(crop) ?: categoryName(obj) ?: return@mapNotNull null
                DetectedItem(name, crop)
            }
            .distinctBy { it.name.lowercase() }

        // Close-ups of a single thing often yield no detected object; then name the whole photo.
        val result = items.ifEmpty {
            labels(bitmap).take(3).map { DetectedItem(it, bitmap) }
        }
        PhotoAnalysis(bitmap, result.map { it.copy(name = translate(it.name)) })
    }

    fun close() {
        objectDetector.close()
        labeler.close()
        translator?.close()
    }

    private suspend fun labels(bitmap: Bitmap): List<String> =
        labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            .filter { it.text !in GENERIC_LABELS }
            .sortedByDescending { it.confidence }
            .map { it.text }

    private suspend fun bestLabel(bitmap: Bitmap): String? = labels(bitmap).firstOrNull()

    private fun categoryName(obj: DetectedObject): String? =
        obj.labels.maxByOrNull { it.confidence }?.text?.takeIf { it.isNotBlank() }

    private suspend fun translate(text: String): String {
        val translator = translator
        val translated = if (translator != null && translatorReady) {
            try {
                translator.translate(text).await()
            } catch (e: Exception) {
                text
            }
        } else {
            text
        }
        return translated.replaceFirstChar { it.titlecase(locale) }
    }

    /** Cuts out [box] with some margin so the object is not clipped too tightly. */
    private fun crop(bitmap: Bitmap, box: Rect): Bitmap? {
        val marginX = (box.width() * CROP_MARGIN).toInt()
        val marginY = (box.height() * CROP_MARGIN).toInt()
        val left = (box.left - marginX).coerceAtLeast(0)
        val top = (box.top - marginY).coerceAtLeast(0)
        val right = (box.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + marginY).coerceAtMost(bitmap.height)
        if (right - left < MIN_CROP_SIZE || bottom - top < MIN_CROP_SIZE) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Decodes the photo downscaled to at most [MAX_SIZE] px and rotated upright. */
    private fun loadBitmap(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Decoding bounds only always returns null; the result is in [bounds].
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIZE) sampleSize *= 2

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
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private companion object {
        const val MAX_SIZE = 1600
        const val MIN_LABEL_CONFIDENCE = 0.5f
        const val CROP_MARGIN = 0.1f
        const val MIN_CROP_SIZE = 48

        /** Labels that describe the scene rather than a thing someone puts away. */
        val GENERIC_LABELS = setOf(
            "Room", "Interaction", "Smile", "Fun", "Event", "Sky", "Cool", "Pattern",
            "Metal", "Wood", "Plastic", "Paper", "Textile", "Leisure", "Selfie", "Darkness"
        )
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
