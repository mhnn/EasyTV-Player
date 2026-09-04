package com.easytv.player

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class ScannedEpisode(val uri: String, val name: String, val number: Int, val size: Long)

class MediaScanner(private val context: Context, private val database: AppDatabase) {
    private val extensions = setOf("mp4", "mkv", "avi", "mov", "flv", "ts", "rmvb")

    fun scan(rootUri: String, onProgress: (Int, Int, Int) -> Unit = { _, _, _ -> }) {
        val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(rootUri)) ?: return
        val seriesFolders = root.listFiles().filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
        var found = 0
        seriesFolders.forEachIndexed { index, folder ->
            val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
            val episodes = children.asSequence()
                .filter { it.isFile && !it.name.orEmpty().startsWith('.') && it.length() >= MIN_VIDEO_BYTES }
                .filter { it.name.orEmpty().substringAfterLast('.', "").lowercase() in extensions }
                .map { ScannedEpisode(it.uri.toString(), it.name.orEmpty(), episodeNumber(it.name.orEmpty()), it.length()) }
                .sortedWith(compareBy<ScannedEpisode> { it.number }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
            if (episodes.isNotEmpty()) {
                database.replaceSeries(folder.uri.toString(), folder.name ?: "未命名电视剧", episodes)
                found++
            }
            onProgress(index + 1, seriesFolders.size, found)
        }
    }

    fun scanPath(rootPath: String, onProgress: (Int, Int, Int) -> Unit = { _, _, _ -> }) {
        val folders = File(rootPath).listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }.orEmpty()
        var found = 0
        folders.forEachIndexed { index, folder ->
            val children = folder.listFiles()?.toList().orEmpty()
            val episodes = children.asSequence()
                .filter { it.isFile && !it.name.startsWith('.') && it.length() >= MIN_VIDEO_BYTES }
                .filter { it.extension.lowercase() in extensions }
                .map { ScannedEpisode(android.net.Uri.fromFile(it).toString(), it.name, episodeNumber(it.name), it.length()) }
                .sortedWith(compareBy<ScannedEpisode> { it.number }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
            if (episodes.isNotEmpty()) {
                database.replaceSeries(folder.absolutePath, folder.name, episodes)
                found++
            }
            onProgress(index + 1, folders.size, found)
        }
    }

    companion object {
        private const val MIN_VIDEO_BYTES = 10L * 1024 * 1024
        private val patterns = listOf(Regex("第\\s*(\\d+)\\s*集", RegexOption.IGNORE_CASE), Regex("(?:EP|E)\\s*0*(\\d+)", RegexOption.IGNORE_CASE), Regex("(\\d+)"))
        fun episodeNumber(name: String): Int {
            val baseName = name.substringBeforeLast('.', name)
            return patterns.firstNotNullOfOrNull { it.find(baseName)?.groupValues?.get(1)?.toIntOrNull() } ?: Int.MAX_VALUE
        }
    }
}
