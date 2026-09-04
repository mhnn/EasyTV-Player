package com.easytv.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class PlaybackThumbnailStore(private val context: Context) {
    private val directory = File(context.cacheDir, "thumbnails")

    fun saveRenderedFrame(seriesId: Long, frame: Bitmap): String? = runCatching {
            val thumbnail = Bitmap.createScaledBitmap(frame, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true)
            directory.mkdirs()
            val target = File(directory, "$seriesId.jpg")
            val temporary = File(directory, "$seriesId-${System.nanoTime()}.tmp")
            FileOutputStream(temporary).use { thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            Uri.fromFile(target).toString()
    }.getOrNull()

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val THUMBNAIL_WIDTH = 640
        private const val THUMBNAIL_HEIGHT = 360
    }
}
