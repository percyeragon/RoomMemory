package com.wilhelmbeiche.roommemory.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wilhelmbeiche.roommemory.data.ImageStorage
import com.wilhelmbeiche.roommemory.data.ItemEntity
import com.wilhelmbeiche.roommemory.data.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ItemRepository = ItemRepository.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ItemEntity>?> = _searchQuery
        .flatMapLatest { query ->
            repository.searchItems(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val rooms: StateFlow<List<String>> = repository.rooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val furniture: StateFlow<List<String>> = repository.furniture()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Item that was just deleted and can still be restored via "undo". */
    private val _recentlyDeleted = MutableStateFlow<ItemEntity?>(null)
    val recentlyDeleted: StateFlow<ItemEntity?> = _recentlyDeleted.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getItem(id: Long): ItemEntity? = repository.getItem(id)

    /**
     * Inserts a new item ([existing] == null) or updates [existing].
     * A replaced photo of an existing item is removed from storage.
     */
    fun saveItem(
        existing: ItemEntity?,
        title: String,
        room: String,
        furniture: String,
        compartment: String,
        description: String,
        imageUri: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val item = ItemEntity(
                id = existing?.id ?: 0L,
                title = title.trim(),
                room = room.trim(),
                furniture = furniture.trim(),
                compartment = compartment.trim(),
                description = description.trim(),
                imageUri = imageUri
            )
            if (existing == null) {
                repository.insertItem(item)
            } else {
                repository.updateItem(item)
                if (existing.imageUri != imageUri) repository.deleteImage(existing.imageUri)
            }
            onComplete()
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            // Only one deletion can be undone at a time; finalize the previous one.
            _recentlyDeleted.value?.let { repository.deleteImage(it.imageUri) }
            repository.deleteItem(item)
            _recentlyDeleted.value = item
        }
    }

    fun undoDelete() {
        val item = _recentlyDeleted.value ?: return
        _recentlyDeleted.value = null
        viewModelScope.launch { repository.insertItem(item) }
    }

    fun confirmDelete() {
        val item = _recentlyDeleted.value ?: return
        _recentlyDeleted.value = null
        viewModelScope.launch { repository.deleteImage(item.imageUri) }
    }

    fun importImage(source: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(repository.importImage(source)) }
    }

    fun createCameraTarget(): ImageStorage.CameraTarget = repository.createCameraTarget()

    /** Removes an image that was added in the editor but not saved. */
    fun discardImage(uri: String) {
        viewModelScope.launch { repository.deleteImage(uri) }
    }

    override fun onCleared() {
        _recentlyDeleted.value?.let { ImageStorage.delete(getApplication(), it.imageUri) }
    }
}
