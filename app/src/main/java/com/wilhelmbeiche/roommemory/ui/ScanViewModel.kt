package com.wilhelmbeiche.roommemory.ui

import com.wilhelmbeiche.roommemory.R
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wilhelmbeiche.roommemory.data.ImageStorage
import com.wilhelmbeiche.roommemory.data.ItemEntity
import com.wilhelmbeiche.roommemory.data.ItemRepository
import com.wilhelmbeiche.roommemory.data.PhotoAnalyzer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One entry suggested from a photo; the user can rename it or leave it out. */
class ScanCandidate(name: String, val image: Bitmap) {
    var name by mutableStateOf(name)
    var selected by mutableStateOf(true)
}

class ScanViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = ItemRepository.getInstance(application)
    private val analyzer = PhotoAnalyzer(application)

    val rooms: StateFlow<List<String>> = repository.rooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val furniture: StateFlow<List<String>> = repository.furniture()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var isAnalyzing by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    /** Info for the user, as a string resource id. */
    var message by mutableStateOf<Int?>(null)
        private set
    val candidates = mutableStateListOf<ScanCandidate>()

    init {
        // Fetch the German model early so it is ready when the first photo comes in.
        viewModelScope.launch { analyzer.prepareTranslation() }
    }

    /** Camera output file; kept in saved state because the process may die while the camera app is open. */
    fun createCameraTarget(): Uri {
        val target = repository.createCameraTarget()
        savedState[KEY_CAMERA_FILE] = target.storedUri
        return target.contentUri
    }

    fun onCameraResult(success: Boolean) {
        val stored = savedState.get<String>(KEY_CAMERA_FILE) ?: return
        savedState.remove<String>(KEY_CAMERA_FILE)
        viewModelScope.launch {
            if (success) analyze(Uri.parse(stored))
            // The cut-outs are stored per item, so the full photo is no longer needed.
            repository.deleteImage(stored)
        }
    }

    fun onCameraUnavailable() {
        savedState.remove<String>(KEY_CAMERA_FILE)
        message = R.string.no_camera_app
    }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch { analyze(uri) }
    }

    private suspend fun analyze(uri: Uri) {
        isAnalyzing = true
        message = null
        candidates.clear()
        photo = null
        try {
            // Wait briefly for the translation model; if it is not there yet, names stay English.
            withTimeoutOrNull(TRANSLATION_WAIT_MS) { analyzer.prepareTranslation() }
            val analysis = analyzer.analyze(uri)
            if (analysis == null) {
                message = R.string.photo_load_failed
                return
            }
            photo = analysis.photo
            candidates.addAll(analysis.items.map { ScanCandidate(it.name, it.image) })
            if (analysis.items.isEmpty()) message = R.string.nothing_detected
        } catch (e: Exception) {
            message = R.string.photo_analyze_failed
        } finally {
            isAnalyzing = false
        }
    }

    /** Adds an empty entry that uses the whole photo as its picture. */
    fun addManualCandidate() {
        val image = photo ?: return
        candidates.add(ScanCandidate("", image))
    }

    fun removeCandidate(candidate: ScanCandidate) {
        candidates.remove(candidate)
    }

    fun save(room: String, furniture: String, compartment: String, onComplete: (Int) -> Unit) {
        val chosen = candidates.filter { it.selected && it.name.isNotBlank() }
        if (chosen.isEmpty() || room.isBlank()) return
        isSaving = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val saved = repository.insertItemsWithImages(
                chosen.mapIndexed { index, candidate ->
                    ItemEntity(
                        title = candidate.name.trim(),
                        room = room.trim(),
                        furniture = furniture.trim(),
                        compartment = compartment.trim(),
                        // Keep the detection order in the list, which is sorted by newest first.
                        timestamp = now - index
                    ) to candidate.image
                }
            )
            isSaving = false
            onComplete(saved)
        }
    }

    override fun onCleared() {
        savedState.get<String>(KEY_CAMERA_FILE)?.let { ImageStorage.delete(getApplication(), it) }
        analyzer.close()
    }

    private companion object {
        const val KEY_CAMERA_FILE = "camera_file"
        const val TRANSLATION_WAIT_MS = 15_000L
    }
}
