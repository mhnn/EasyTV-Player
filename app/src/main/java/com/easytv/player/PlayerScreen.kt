package com.easytv.player

import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(repository: AppRepository, series: Series, startIndex: Int, resume: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { repository.settings() }
    var index by remember { mutableIntStateOf(startIndex) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableIntStateOf(0) }
    val speeds = remember { listOf(1f, 1.25f, 1.5f, 2f) }
    val player = remember { ExoPlayer.Builder(context).build() }

    fun openEpisode(target: Int, startAt: Long = 0L) {
        index = target
        player.setMediaItem(MediaItem.fromUri(series.episodes[target].uri))
        player.prepare(); player.seekTo(startAt); player.play()
    }
    fun seek(delta: Long) {
        val target = (player.currentPosition + delta).coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        feedback = if (delta > 0) "快进 +${delta / 1000}秒" else "快退 ${delta / 1000}秒"
        controlsVisible = true
        controlsInteraction++
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && settings.autoPlayNext && index < series.episodes.lastIndex) openEpisode(index + 1)
            }
        }
        player.addListener(listener)
        val start = if (resume) series.history?.takeIf { it.episodeId == series.episodes[startIndex].id }?.positionMs ?: 0L else 0L
        openEpisode(startIndex, start)
        onDispose {
            val episode = series.episodes[index]
            repository.saveProgressAsync(PlayHistory(series.id, episode.id, player.currentPosition, player.duration.coerceAtLeast(0), System.currentTimeMillis()), episode)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(250)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val episode = series.episodes[index]
            repository.saveProgress(PlayHistory(series.id, episode.id, player.currentPosition, player.duration.coerceAtLeast(0), System.currentTimeMillis()), episode)
            delay(5_000)
        }
    }
    LaunchedEffect(controlsInteraction, playing) {
        if (playing) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(feedback) { if (feedback != null) { delay(3_000); feedback = null } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(if (event.nativeKeyEvent.isLongPress) -30_000 else -settings.seekSeconds * 1000L); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(if (event.nativeKeyEvent.isLongPress) 30_000 else settings.seekSeconds * 1000L); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (event.nativeKeyEvent.isLongPress) { speedIndex = (speedIndex + 1) % speeds.size; player.setPlaybackSpeed(speeds[speedIndex]); feedback = "${speeds[speedIndex]}x 倍速" }
                    else if (player.isPlaying) player.pause() else player.play()
                    controlsVisible = true; controlsInteraction++; true
                }
                KeyEvent.KEYCODE_BACK -> { onBack(); true }
                else -> false
            }
        }
    ) {
        AndroidView(factory = { LayoutInflater.from(it).inflate(R.layout.player_view, null).also { view -> (view as PlayerView).player = player } }, modifier = Modifier.fillMaxSize())
        if (controlsVisible) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC101112)).padding(28.dp)) {
                Text("${series.name}  ·  第${index + 1}集", fontSize = 26.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { if (duration > 0) position.toFloat() / duration else 0f }, modifier = Modifier.fillMaxWidth().height(9.dp), color = FocusYellow)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastRewind, null); Spacer(Modifier.width(18.dp)); Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(18.dp)); Icon(Icons.Default.FastForward, null); Spacer(Modifier.weight(1f)); Text("${formatPlayerTime(position)} / ${formatPlayerTime(duration)}", fontSize = 22.sp)
                }
            }
        }
        feedback?.let { Text(it, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xCC202122), MaterialTheme.shapes.medium).padding(24.dp)) }
    }
}

private fun AppRepository.saveProgressAsync(history: PlayHistory, episode: Episode) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { saveProgress(history, episode) }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
