package com.easytv.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class PlaybackThumbnailStore(private val context: Context) {
    private val directory = File(context.cacheDir, "thumbnails")

    fun capture(seriesId: Long, videoUri: String, positionMs: Long): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(videoUri))
            val frame = frameAt(retriever, positionMs * 1_000) ?: return null
            val poster = cropToPoster(frame)
            directory.mkdirs()
            val target = File(directory, "$seriesId.jpg")
            val temporary = File(directory, "$seriesId.tmp")
            FileOutputStream(temporary).use { poster.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            Uri.fromFile(target).toString()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        if (Build.VERSION.SDK_INT < 27) return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 640
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 360
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val sourceWidth = if (rotation == 90 || rotation == 270) height else width
        val sourceHeight = if (rotation == 90 || rotation == 270) width else height
        val scale = max(POSTER_WIDTH.toFloat() / sourceWidth, POSTER_HEIGHT.toFloat() / sourceHeight)
        return retriever.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            (sourceWidth * scale).roundToInt().coerceAtLeast(POSTER_WIDTH),
            (sourceHeight * scale).roundToInt().coerceAtLeast(POSTER_HEIGHT),
        )
    }

    private fun cropToPoster(frame: Bitmap): Bitmap {
        val scale = max(POSTER_WIDTH.toFloat() / frame.width, POSTER_HEIGHT.toFloat() / frame.height)
        val scaled = Bitmap.createScaledBitmap(frame, (frame.width * scale).roundToInt(), (frame.height * scale).roundToInt(), true)
        return Bitmap.createBitmap(scaled, (scaled.width - POSTER_WIDTH) / 2, (scaled.height - POSTER_HEIGHT) / 2, POSTER_WIDTH, POSTER_HEIGHT)
    }

    companion object {
        private const val POSTER_WIDTH = 640
        private const val POSTER_HEIGHT = 360
    }
}
