package com.easytv.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(context: Context) {
    private val database = AppDatabase(context)
    private val scanner = MediaScanner(context, database)
    private val thumbnails = PlaybackThumbnailStore(context)
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    suspend fun loadSeries() = withContext(Dispatchers.IO) { database.allSeries() }
    suspend fun directories() = withContext(Dispatchers.IO) { database.directories() }
    suspend fun addDirectory(uri: String, name: String, progress: (Int, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        database.addDirectory(uri, name); scanner.scan(uri, progress)
    }
    suspend fun addPath(path: String, name: String, progress: (Int, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        database.addDirectory(path, name); scanner.scanPath(path, progress)
    }
    suspend fun rescan(progress: (String, Int, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        database.directories().forEach { (uri, name) ->
            val callback = { done: Int, total: Int, found: Int -> progress(name, done, total, found) }
            if (uri.startsWith("content:")) scanner.scan(uri, callback) else scanner.scanPath(uri, callback)
        }
    }
    suspend fun removeDirectory(uri: String) = withContext(Dispatchers.IO) { database.removeDirectory(uri) }
    suspend fun saveProgress(history: PlayHistory, episode: Episode) = withContext(Dispatchers.IO) {
        database.saveHistory(history)
        thumbnails.capture(history.seriesId, episode.uri, history.positionMs)?.let { database.updateThumbnail(history.seriesId, it) }
    }
    suspend fun clearHistory() = withContext(Dispatchers.IO) { database.clearHistory(); thumbnails.clear() }

    fun settings() = PlayerSettings(
        preferences.getInt("seek", 10), preferences.getBoolean("next", true),
        SubtitleSize.valueOf(preferences.getString("subtitle_size", "MEDIUM")!!),
        SubtitleColor.valueOf(preferences.getString("subtitle_color", "WHITE")!!),
    )
    fun saveSettings(value: PlayerSettings) = preferences.edit().putInt("seek", value.seekSeconds).putBoolean("next", value.autoPlayNext)
        .putString("subtitle_size", value.subtitleSize.name).putString("subtitle_color", value.subtitleColor.name).apply()
}
