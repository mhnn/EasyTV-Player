package com.easytv.player

data class Episode(
    val id: Long,
    val seriesId: Long,
    val uri: String,
    val name: String,
    val number: Int,
    val size: Long,
)

data class Series(
    val id: Long,
    val directoryUri: String,
    val name: String,
    val posterUri: String?,
    val episodes: List<Episode>,
    val history: PlayHistory?,
)

data class PlayHistory(
    val seriesId: Long,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val completed: Boolean get() = durationMs > 0 && positionMs.toDouble() / durationMs >= 0.95
}

enum class SubtitleSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
enum class SubtitleColor { WHITE, YELLOW, GREEN }

data class PlayerSettings(
    val seekSeconds: Int = 10,
    val autoPlayNext: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
)
