package com.easytv.player

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    var selectedControl by remember { mutableIntStateOf(1) }
    var keyStartedWithOverlay by remember { mutableStateOf(false) }
    var longSeek by remember { mutableStateOf(false) }
    var longOk by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val player = remember { ExoPlayer.Builder(context).build() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    fun openEpisode(target: Int, startAt: Long = 0L) {
        index = target
        selectedControl = 1
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
    fun showControls() {
        controlsVisible = true
        controlsInteraction++
    }
    fun activateControl() {
        when (selectedControl) {
            0 -> if (index > 0) openEpisode(index - 1)
            1 -> if (player.isPlaying) player.pause() else player.play()
            2 -> if (index < series.episodes.lastIndex) openEpisode(index + 1)
        }
        showControls()
    }
    fun renderedFrame() = (playerView?.videoSurfaceView as? TextureView)
        ?.takeIf { it.isAvailable && player.playbackState == Player.STATE_READY && player.videoSize.width > 0 }
        ?.let { runCatching { it.getBitmap(640, 360) }.getOrNull() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
            override fun onCues(cues: List<androidx.media3.common.text.Cue>) {
                playerView?.subtitleView?.setCues(filterSubtitleCues(cues))
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && settings.autoPlayNext && index < series.episodes.lastIndex) openEpisode(index + 1)
            }
        }
        player.addListener(listener)
        val start = if (resume) series.history?.takeIf { it.episodeId == series.episodes[startIndex].id }?.positionMs ?: 0L else 0L
        openEpisode(startIndex, start)
        onDispose {
            val episode = series.episodes[index]
            repository.saveProgressAsync(PlayHistory(series.id, episode.id, player.currentPosition, player.duration.coerceAtLeast(0), System.currentTimeMillis()), renderedFrame())
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
            repository.saveProgress(PlayHistory(series.id, episode.id, player.currentPosition, player.duration.coerceAtLeast(0), System.currentTimeMillis()), renderedFrame())
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
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focusRequester).focusable().onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val handled = key.keyCode in PLAYER_KEYS
            if (!handled) return@onPreviewKeyEvent false
            when (key.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (key.repeatCount == 0) {
                        keyStartedWithOverlay = controlsVisible
                        longSeek = false
                        longOk = false
                        if (key.keyCode != KeyEvent.KEYCODE_BACK && key.keyCode !in OK_KEYS) showControls()
                    } else when (key.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            longSeek = true
                            seek(-settings.seekSeconds * 1_000L)
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            longSeek = true
                            seek(settings.seekSeconds * 1_000L)
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> if (!longOk) {
                            longOk = true
                            player.setPlaybackSpeed(2f)
                            if (!player.isPlaying) player.play()
                            feedback = "2.0x 倍速"
                        }
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    when (key.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (!longSeek) {
                            if (keyStartedWithOverlay) selectedControl = movePlayerControl(selectedControl, -1, index > 0, index < series.episodes.lastIndex)
                            else seek(-settings.seekSeconds * 1_000L)
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (!longSeek) {
                            if (keyStartedWithOverlay) selectedControl = movePlayerControl(selectedControl, 1, index > 0, index < series.episodes.lastIndex)
                            else seek(settings.seekSeconds * 1_000L)
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> if (keyStartedWithOverlay) selectedControl = movePlayerControl(selectedControl, -1, index > 0, index < series.episodes.lastIndex)
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (keyStartedWithOverlay) selectedControl = movePlayerControl(selectedControl, 1, index > 0, index < series.episodes.lastIndex)
                        KeyEvent.KEYCODE_MEDIA_REWIND -> if (!longSeek) seek(-settings.seekSeconds * 1_000L)
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!longSeek) seek(settings.seekSeconds * 1_000L)
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            if (longOk) {
                                player.setPlaybackSpeed(1f)
                                feedback = "1.0x 倍速"
                            } else if (keyStartedWithOverlay) {
                                activateControl()
                            } else {
                                selectedControl = 1
                                if (player.isPlaying) player.pause() else player.play()
                                showControls()
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                        KeyEvent.KEYCODE_BACK -> if (keyStartedWithOverlay) {
                            controlsVisible = false
                            feedback = null
                        } else {
                            onBack()
                        }
                    }
                    true
                }
                else -> true
            }
        }
    ) {
        AndroidView(factory = { LayoutInflater.from(it).inflate(R.layout.player_view, null).also { view -> (view as PlayerView).apply { isFocusable = false; this.player = player; playerView = this } } }, modifier = Modifier.fillMaxSize())
        if (controlsVisible) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC101112)).padding(28.dp)) {
                Text("${series.name}  ·  第${index + 1}集", fontSize = 26.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { if (duration > 0) position.toFloat() / duration else 0f }, modifier = Modifier.fillMaxWidth().height(9.dp), color = FocusYellow)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(92.dp)) {
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        PlayerControl(Icons.Default.SkipPrevious, "上一集", selectedControl == 0, index > 0)
                        PlayerControl(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "暂停" else "播放", selectedControl == 1, true)
                        PlayerControl(Icons.Default.SkipNext, "下一集", selectedControl == 2, index < series.episodes.lastIndex)
                    }
                    Text("${formatPlayerTime(position)} / ${formatPlayerTime(duration)}", fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }
        }
        feedback?.let { Text(it, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xCC202122), MaterialTheme.shapes.medium).padding(24.dp)) }
    }
}

@Composable
private fun PlayerControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean) {
    Row(
        Modifier.size(156.dp, 76.dp).background(if (selected) Color(0xFF4A421B) else Color(0xFF303132), RoundedCornerShape(7.dp))
            .then(if (selected) Modifier.border(3.dp, FocusYellow, RoundedCornerShape(7.dp)) else Modifier).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else Color.Gray)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 20.sp, color = if (enabled) Color.White else Color.Gray)
    }
}

internal fun movePlayerControl(current: Int, direction: Int, hasPrevious: Boolean, hasNext: Boolean): Int {
    val enabled = listOfNotNull(0.takeIf { hasPrevious }, 1, 2.takeIf { hasNext })
    val position = enabled.indexOf(current).takeIf { it >= 0 } ?: enabled.indexOf(1)
    return enabled[(position + direction).coerceIn(0, enabled.lastIndex)]
}

private val PLAYER_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_BACK,
)

private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A)

private fun AppRepository.saveProgressAsync(history: PlayHistory, frame: android.graphics.Bitmap?) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { saveProgress(history, frame) }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
