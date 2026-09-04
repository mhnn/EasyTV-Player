package com.easytv.player

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "easy_tv.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE directories(uri TEXT PRIMARY KEY, name TEXT NOT NULL, added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE series(id INTEGER PRIMARY KEY AUTOINCREMENT, directory_uri TEXT UNIQUE NOT NULL, name TEXT NOT NULL, poster_uri TEXT)")
        db.execSQL("CREATE TABLE episodes(id INTEGER PRIMARY KEY AUTOINCREMENT, series_id INTEGER NOT NULL, uri TEXT UNIQUE NOT NULL, name TEXT NOT NULL, episode_number INTEGER NOT NULL, size INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(series_id INTEGER PRIMARY KEY, episode_id INTEGER NOT NULL, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun directories(): List<Pair<String, String>> = readableDatabase.rawQuery(
        "SELECT uri,name FROM directories ORDER BY added_at", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) } }

    fun addDirectory(uri: String, name: String) {
        val values = ContentValues().apply { put("uri", uri); put("name", name); put("added_at", System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("directories", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeDirectory(uri: String) = writableDatabase.transaction {
        rawQuery("SELECT id FROM series WHERE directory_uri LIKE ?", arrayOf("$uri%" )).use { cursor ->
            while (cursor.moveToNext()) {
                delete("episodes", "series_id=?", arrayOf(cursor.getLong(0).toString()))
                delete("history", "series_id=?", arrayOf(cursor.getLong(0).toString()))
            }
        }
        delete("series", "directory_uri LIKE ?", arrayOf("$uri%"))
        delete("directories", "uri=?", arrayOf(uri))
    }

    fun replaceSeries(directoryUri: String, name: String, posterUri: String?, files: List<ScannedEpisode>) = writableDatabase.transaction {
        val values = ContentValues().apply { put("directory_uri", directoryUri); put("name", name); put("poster_uri", posterUri) }
        insertWithOnConflict("series", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        update("series", values, "directory_uri=?", arrayOf(directoryUri))
        val id = rawQuery("SELECT id FROM series WHERE directory_uri=?", arrayOf(directoryUri)).use { it.moveToFirst(); it.getLong(0) }
        val savedHistory = rawQuery(
            "SELECT h.position_ms,h.duration_ms,h.updated_at,e.uri FROM history h JOIN episodes e ON h.episode_id=e.id WHERE h.series_id=?",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) SavedHistory(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3)) else null
        }
        delete("episodes", "series_id=?", arrayOf(id.toString()))
        files.forEach { file ->
            val episode = ContentValues().apply {
                put("series_id", id); put("uri", file.uri); put("name", file.name)
                put("episode_number", file.number); put("size", file.size)
            }
            insert("episodes", null, episode)
        }
        savedHistory?.let { history ->
            rawQuery("SELECT id FROM episodes WHERE uri=?", arrayOf(history.uri)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val updated = ContentValues().apply {
                        put("episode_id", cursor.getLong(0)); put("position_ms", history.positionMs)
                        put("duration_ms", history.durationMs); put("updated_at", history.updatedAt)
                    }
                    update("history", updated, "series_id=?", arrayOf(id.toString()))
                }
            }
        }
    }

    fun allSeries(): List<Series> {
        val histories = mutableMapOf<Long, PlayHistory>()
        readableDatabase.rawQuery("SELECT series_id,episode_id,position_ms,duration_ms,updated_at FROM history", null).use { c ->
            while (c.moveToNext()) histories[c.getLong(0)] = PlayHistory(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4))
        }
        return readableDatabase.rawQuery("SELECT id,directory_uri,name,poster_uri FROM series ORDER BY name COLLATE NOCASE", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val episodes = readableDatabase.rawQuery("SELECT id,uri,name,episode_number,size FROM episodes WHERE series_id=? ORDER BY episode_number,name COLLATE NOCASE", arrayOf(id.toString())).use { e ->
                        buildList { while (e.moveToNext()) add(Episode(e.getLong(0), id, e.getString(1), e.getString(2), e.getInt(3), e.getLong(4))) }
                    }
                    if (episodes.isNotEmpty()) add(Series(id, c.getString(1), c.getString(2), c.getStringOrNull(3), episodes, histories[id]))
                }
            }
        }
    }

    fun saveHistory(history: PlayHistory) {
        val values = ContentValues().apply {
            put("series_id", history.seriesId); put("episode_id", history.episodeId); put("position_ms", history.positionMs)
            put("duration_ms", history.durationMs); put("updated_at", history.updatedAt)
        }
        writableDatabase.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearHistory() = writableDatabase.delete("history", null, null)
}

private data class SavedHistory(val positionMs: Long, val durationMs: Long, val updatedAt: Long, val uri: String)

private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try { val result = block(); setTransactionSuccessful(); result } finally { endTransaction() }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
