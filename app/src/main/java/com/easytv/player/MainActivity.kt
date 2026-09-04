package com.easytv.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.graphics.BitmapFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyTvTheme {
                Surface(Modifier.fillMaxSize(), color = Background, contentColor = TextPrimary) {
                    EasyTvApp(AppRepository(applicationContext))
                }
            }
        }
    }
}

internal val Background = Color(0xFF101112)
private val Surface = Color(0xFF202122)
internal val FocusYellow = Color(0xFFFFD54F)
private val TextPrimary = Color(0xFFF7F7F7)
private val TextSecondary = Color(0xFFCBCBCB)

@Composable
private fun EasyTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = FocusYellow, background = Background, surface = Surface, onBackground = TextPrimary),
        typography = Typography(bodyLarge = LocalTextStyle.current.copy(fontSize = 24.sp)),
        content = content,
    )
}

private sealed interface Screen {
    data object Home : Screen
    data object Directories : Screen
    data object Settings : Screen
    data object Browser : Screen
    data class Player(val series: Series, val index: Int, val resume: Boolean) : Screen
}

@Composable
private fun EasyTvApp(repository: AppRepository) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var series by remember { mutableStateOf(emptyList<Series>()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var startupResumeHandled by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey, screen) {
        if (screen is Screen.Home) {
            val loaded = repository.loadSeries()
            series = loaded
            if (!startupResumeHandled) {
                startupResumeHandled = true
                findLatestResume(loaded)?.let { target ->
                    screen = Screen.Player(target.series, target.episodeIndex, resume = true)
                }
            }
        }
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = Screen.Home
        refreshKey++
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(series, onSettings = { screen = Screen.Settings }, onDirectories = { screen = Screen.Directories }) { item, index, resume ->
            screen = Screen.Player(item, index, resume)
        }
        Screen.Directories -> DirectoryScreen(repository, onAdd = { screen = Screen.Browser }, onBack = { screen = Screen.Home; refreshKey++ })
        Screen.Browser -> FileBrowserScreen(repository, onDone = { screen = Screen.Directories }, onBack = { screen = Screen.Directories })
        Screen.Settings -> SettingsScreen(repository, onDirectories = { screen = Screen.Directories }, onBack = { screen = Screen.Home; refreshKey++ })
        is Screen.Player -> PlayerScreen(repository, current.series, current.index, current.resume) { screen = Screen.Home; refreshKey++ }
    }
}

internal data class ResumeTarget(val series: Series, val episodeIndex: Int)

internal fun findLatestResume(series: List<Series>): ResumeTarget? = series
    .asSequence()
    .mapNotNull { item ->
        val history = item.history?.takeUnless { it.completed } ?: return@mapNotNull null
        val episodeIndex = item.episodes.indexOfFirst { it.id == history.episodeId }
        if (episodeIndex >= 0) ResumeTarget(item, episodeIndex) else null
    }
    .maxByOrNull { it.series.history!!.updatedAt }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    series: List<Series>,
    onSettings: () -> Unit,
    onDirectories: () -> Unit,
    onPlay: (Series, Int, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 36.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("电视剧", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TvButton(onClick = onSettings) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("设置", fontSize = 24.sp) }
        }
        Spacer(Modifier.height(18.dp))
        if (series.isEmpty()) {
            EmptyLibrary(onDirectories)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                items(series, key = { it.id }) { item ->
                    SeriesPanel(item, onPlay)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onDirectories: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary, null, tint = FocusYellow, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("还没有电视剧", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("添加存放电视剧的文件夹即可开始", color = TextSecondary, fontSize = 24.sp)
            Spacer(Modifier.height(28.dp))
            TvButton(onClick = onDirectories, modifier = Modifier.focusRequester(focusRequester)) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加扫描目录") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesPanel(item: Series, onPlay: (Series, Int, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface).padding(14.dp)) {
        Poster(item, onPlay)
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${item.episodes.size} 集", color = TextSecondary, fontSize = 22.sp)
            item.history?.takeUnless { it.completed }?.let { history ->
                Spacer(Modifier.height(10.dp))
                val index = item.episodes.indexOfFirst { it.id == history.episodeId }.coerceAtLeast(0)
                ContinueWatching(item, index, history) { onPlay(item, index, true) }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.episodes.forEachIndexed { index, episode ->
                    val played = item.history?.let { it.episodeId > episode.id || (it.episodeId == episode.id && it.completed) } == true
                    EpisodeButton(index + 1, played, item.history?.episodeId == episode.id) { onPlay(item, index, false) }
                }
            }
        }
    }
}

@Composable
private fun Poster(item: Series, onPlay: (Series, Int, Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var lastClick by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val thumbnailUri = item.posterUri.takeIf { item.history != null }
    val poster by produceState<ImageBitmap?>(null, thumbnailUri) {
        value = thumbnailUri?.let { uri -> withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        } }
    }
    Surface(
        onClick = {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastClick <= 500) onPlay(item, 0, false)
            lastClick = now
        },
        modifier = Modifier.width(240.dp).aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused }.graphicsLayer { scaleX = if (focused) 1.06f else 1f; scaleY = scaleX },
        shape = RoundedCornerShape(6.dp), color = Color(0xFF343536), border = if (focused) androidx.compose.foundation.BorderStroke(3.dp, FocusYellow) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (poster != null) Image(poster!!, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ContinueWatching(item: Series, index: Int, history: PlayHistory, onClick: () -> Unit) {
    TvButton(onClick, modifier = Modifier.fillMaxWidth().height(60.dp)) {
        Text("继续观看  第${index + 1}集", fontSize = 23.sp)
        Spacer(Modifier.width(24.dp))
        LinearProgressIndicator(
            progress = { if (history.durationMs > 0) history.positionMs.toFloat() / history.durationMs else 0f },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)), color = FocusYellow, trackColor = Color(0xFF555657),
        )
        Spacer(Modifier.width(20.dp))
        Text("${formatTime(history.positionMs)} / ${formatTime(history.durationMs)}", fontSize = 20.sp)
    }
}

@Composable
private fun EpisodeButton(number: Int, played: Boolean, current: Boolean, onClick: () -> Unit) {
    TvButton(onClick, Modifier.size(140.dp, 60.dp), current = current, container = if (played) Color(0xFF616161) else Color(0xFF363738)) {
        Text("第${number}集", fontSize = 22.sp)
    }
}

@Composable
internal fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    container: Color = Color(0xFF343536),
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.graphicsLayer { scaleX = if (focused) 1.06f else 1f; scaleY = scaleX },
        shape = RoundedCornerShape(7.dp),
        border = if (focused || current) androidx.compose.foundation.BorderStroke(if (focused) 3.dp else 2.dp, FocusYellow) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) Color(0xFF4A421B) else container, contentColor = TextPrimary),
        content = content,
    )
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
