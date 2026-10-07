package com.wilhelmbeiche.roommemory.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class ItemRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: ItemRepository? = null

        /** One shared instance, so every screen sees the same change notifications. */
        fun getInstance(context: Context): ItemRepository =
            instance ?: synchronized(this) {
                instance ?: ItemRepository(context).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val dbHelper = DatabaseHelper(appContext)

    // Bumped on every write. A counter is needed because a StateFlow drops
    // repeated equal values, so re-setting Unit would never notify collectors.
    private val changes = MutableStateFlow(0)

    fun searchItems(query: String): Flow<List<ItemEntity>> =
        changes.map { ItemSearch.filter(dbHelper.getItems(), query) }.flowOn(Dispatchers.IO)

    fun rooms(): Flow<List<String>> =
        changes.map { dbHelper.getDistinctValues(DatabaseHelper.COLUMN_ROOM) }.flowOn(Dispatchers.IO)

    fun furniture(): Flow<List<String>> =
        changes.map { dbHelper.getDistinctValues(DatabaseHelper.COLUMN_FURNITURE) }.flowOn(Dispatchers.IO)

    suspend fun getItem(id: Long): ItemEntity? = withContext(Dispatchers.IO) {
        dbHelper.getItem(id)
    }

    suspend fun insertItem(item: ItemEntity) = write { dbHelper.insertItem(item) }

    /** Stores each item with its photo; returns how many were saved. */
    suspend fun insertItemsWithImages(items: List<Pair<ItemEntity, Bitmap>>): Int {
        var saved = 0
        write {
            for ((item, image) in items) {
                val uri = ImageStorage.saveBitmap(appContext, image) ?: ""
                dbHelper.insertItem(item.copy(imageUri = uri))
                saved++
            }
        }
        return saved
    }

    suspend fun updateItem(item: ItemEntity) = write { dbHelper.updateItem(item) }

    suspend fun deleteItem(item: ItemEntity) = write { dbHelper.deleteItem(item.id) }

    suspend fun importImage(source: Uri): String? = withContext(Dispatchers.IO) {
        ImageStorage.importImage(appContext, source)
    }

    suspend fun shrinkImage(uri: String) = withContext(Dispatchers.IO) {
        ImageStorage.shrink(appContext, uri)
    }

    suspend fun deleteImage(uri: String) = withContext(Dispatchers.IO) {
        ImageStorage.delete(appContext, uri)
    }

    fun createCameraTarget(): ImageStorage.CameraTarget = ImageStorage.createCameraTarget(appContext)

    private suspend fun write(block: () -> Unit) {
        withContext(Dispatchers.IO) { block() }
        changes.update { it + 1 }
    }
}
