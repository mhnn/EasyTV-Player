package com.easytv.player

import androidx.media3.common.text.Cue

private val assDrawingCommand = Regex("(?i)(?:^|\\s)[mnlbspc](?=\\s|$)")
private val assDrawingNumber = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
private val assDrawingTag = Regex("\\\\p[1-4]")

internal fun filterSubtitleCues(cues: List<Cue>): List<Cue> = cues.filterNot { isAssDrawingCue(it) }

internal fun isAssDrawingCue(cue: Cue): Boolean = isAssDrawingText(cue.text)

internal fun isAssDrawingText(text: CharSequence?): Boolean {
    val value = text?.toString()?.trim().orEmpty()
    if (value.isEmpty()) return false
    if (assDrawingTag.containsMatchIn(value)) return true
    return value.length > 20 && assDrawingCommand.findAll(value).count() >= 2 && assDrawingNumber.findAll(value).count() >= 6
}
