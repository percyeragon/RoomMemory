package com.wilhelmbeiche.roommemory.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "room_memory.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_ITEMS = "items"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_ROOM = "room"
        const val COLUMN_FURNITURE = "furniture"
        const val COLUMN_COMPARTMENT = "compartment"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_IMAGE_URI = "imageUri"
        const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_ITEMS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_ROOM TEXT NOT NULL,
                $COLUMN_FURNITURE TEXT NOT NULL,
                $COLUMN_COMPARTMENT TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_IMAGE_URI TEXT,
                $COLUMN_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The table holds the user's inventory, so never drop it here. When DATABASE_VERSION goes up,
        // migrate step by step, e.g. `if (oldVersion < 2) db.execSQL("ALTER TABLE ... ADD COLUMN ...")`.
    }

    private fun ItemEntity.toContentValues() = ContentValues().apply {
        put(COLUMN_TITLE, title)
        put(COLUMN_ROOM, room)
        put(COLUMN_FURNITURE, furniture)
        put(COLUMN_COMPARTMENT, compartment)
        put(COLUMN_DESCRIPTION, description)
        put(COLUMN_IMAGE_URI, imageUri)
        put(COLUMN_TIMESTAMP, timestamp)
    }

    /** Inserts the item; a non-zero id is kept, so a deleted item can be restored as it was. */
    fun insertItem(item: ItemEntity): Long {
        val values = item.toContentValues()
        if (item.id != 0L) values.put(COLUMN_ID, item.id)
        return writableDatabase.insert(TABLE_ITEMS, null, values)
    }

    fun updateItem(item: ItemEntity) {
        writableDatabase.update(TABLE_ITEMS, item.toContentValues(), "$COLUMN_ID = ?", arrayOf(item.id.toString()))
    }

    fun deleteItem(id: Long) {
        writableDatabase.delete(TABLE_ITEMS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getItem(id: Long): ItemEntity? =
        queryItems("$COLUMN_ID = ?", arrayOf(id.toString())).firstOrNull()

    /** Distinct non-blank values of [column], most frequently used first. */
    fun getDistinctValues(column: String): List<String> {
        val values = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT $column FROM $TABLE_ITEMS WHERE TRIM($column) != '' GROUP BY $column ORDER BY COUNT(*) DESC, $column",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) values.add(cursor.getString(0))
        }
        return values
    }

    /** All items, newest first. Searching happens in [ItemSearch]. */
    fun getItems(): List<ItemEntity> = queryItems(null, null)

    private fun queryItems(selection: String?, selectionArgs: Array<String>?): List<ItemEntity> {
        val items = mutableListOf<ItemEntity>()
        val cursor = readableDatabase.query(
            TABLE_ITEMS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use {
            val idCol = it.getColumnIndex(COLUMN_ID)
            val titleCol = it.getColumnIndex(COLUMN_TITLE)
            val roomCol = it.getColumnIndex(COLUMN_ROOM)
            val furnitureCol = it.getColumnIndex(COLUMN_FURNITURE)
            val compartmentCol = it.getColumnIndex(COLUMN_COMPARTMENT)
            val descCol = it.getColumnIndex(COLUMN_DESCRIPTION)
            val imageCol = it.getColumnIndex(COLUMN_IMAGE_URI)
            val timeCol = it.getColumnIndex(COLUMN_TIMESTAMP)

            while (it.moveToNext()) {
                items.add(
                    ItemEntity(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol),
                        room = it.getString(roomCol),
                        furniture = it.getString(furnitureCol),
                        compartment = it.getString(compartmentCol),
                        description = it.getString(descCol) ?: "",
                        imageUri = it.getString(imageCol) ?: "",
                        timestamp = it.getLong(timeCol)
                    )
                )
            }
        }
        return items
    }
}
