package com.easytv.player

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DirectoryScreen(repository: AppRepository, onAdd: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var directories by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var scanning by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableIntStateOf(0) }
    val addFocusRequester = remember { FocusRequester() }
    suspend fun reload() { directories = repository.directories() }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(directories) { if (directories.isEmpty()) addFocusRequester.requestFocus() }
    PageScaffold("管理扫描目录", onBack) {
        Text("已添加目录", fontSize = 24.sp, color = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        if (directories.isEmpty()) Text("暂无目录", fontSize = 26.sp, modifier = Modifier.padding(vertical = 28.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(directories, key = { it.first }) { (uri, name) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = FocusYellow)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(name, fontSize = 25.sp); Text(uri, fontSize = 18.sp, color = Color.Gray, maxLines = 1) }
                    TvButton(onClick = { scope.launch { repository.removeDirectory(uri); reload() } }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("删除") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(onClick = onAdd, modifier = Modifier.focusRequester(addFocusRequester)) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加目录") }
            TvButton(onClick = {
                scope.launch {
                    repository.rescan { name, done, total, count -> scanning = "$name  ($done/$total)"; found = count }
                    scanning = null; reload()
                }
            }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("重新扫描") }
        }
    }
    scanning?.let { ScanDialog(it, found) }
}

@Composable
fun FileBrowserScreen(repository: AppRepository, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<File?>(null) }
    var permissionReady by remember { mutableStateOf(hasStorageAccess()) }
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableIntStateOf(0) }
    var storageRefresh by remember { mutableIntStateOf(0) }
    val scanFocusRequester = remember { FocusRequester() }
    val storageFocusRequester = remember { FocusRequester() }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionReady = it }
    val settingsPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionReady = hasStorageAccess() }
    val storageLocations = remember(permissionReady, storageRefresh) { if (permissionReady) findStorageLocations(context) else emptyList() }
    val folders = remember(current, permissionReady) { if (permissionReady) current?.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.sortedBy { it.name.lowercase() }.orEmpty() else emptyList() }
    LaunchedEffect(current, permissionReady, storageLocations) {
        if (permissionReady) {
            if (current == null && storageLocations.isNotEmpty()) storageFocusRequester.requestFocus()
            else if (current != null) scanFocusRequester.requestFocus()
        }
    }

    PageScaffold("选择扫描目录", onBack) {
        Text(current?.absolutePath ?: "请选择内部存储或外接硬盘", fontSize = 22.sp, color = Color.LightGray)
        Spacer(Modifier.height(14.dp))
        if (!permissionReady) {
            Text("需要存储权限才能浏览本地和外接硬盘", fontSize = 25.sp)
            Spacer(Modifier.height(18.dp))
            TvButton(onClick = {
                if (Build.VERSION.SDK_INT >= 30) settingsPermission.launch(android.content.Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                else legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }) { Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("授予存储权限") }
        } else if (current == null) {
            TvButton(onClick = { storageRefresh++ }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("刷新设备") }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(storageLocations, key = { it.directory.absolutePath }) { location ->
                    TvButton(
                        onClick = { current = location.directory },
                        modifier = Modifier.fillMaxWidth().height(72.dp).then(if (location == storageLocations.first()) Modifier.focusRequester(storageFocusRequester) else Modifier),
                    ) {
                        Icon(if (location.removable) Icons.Default.Usb else Icons.Default.Storage, null, tint = FocusYellow)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(location.name, fontSize = 24.sp)
                            Text(location.directory.absolutePath, fontSize = 18.sp, color = Color.LightGray)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = { current = storageParent(current!!, storageLocations) }) { Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("上一级") }
                TvButton(onClick = {
                    scanning = true
                    scope.launch {
                        val selected = current!!
                        repository.addPath(selected.absolutePath, selected.name.ifBlank { selected.absolutePath }) { _, _, count -> found = count }
                        scanning = false; onDone()
                    }
                }, modifier = Modifier.focusRequester(scanFocusRequester)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("扫描此目录") }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.absolutePath }) { folder ->
                    TvButton(onClick = { current = folder }, modifier = Modifier.fillMaxWidth().height(66.dp)) {
                        Icon(Icons.Default.Folder, null, tint = FocusYellow); Spacer(Modifier.width(14.dp)); Text(folder.name, fontSize = 24.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
    if (scanning) ScanDialog(current?.absolutePath.orEmpty(), found)
}

private fun hasStorageAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

internal data class StorageLocation(val name: String, val directory: File, val removable: Boolean)

internal fun storageParent(current: File, locations: List<StorageLocation>): File? {
    if (locations.any { it.directory.absolutePath == current.absolutePath }) return null
    return current.parentFile
}

@Suppress("DEPRECATION")
private fun findStorageLocations(context: Context): List<StorageLocation> {
    val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val locations = if (Build.VERSION.SDK_INT >= 24) {
        manager.storageVolumes.mapNotNull { volume ->
            val directory = when {
                Build.VERSION.SDK_INT >= 30 -> volume.directory
                volume.isPrimary -> Environment.getExternalStorageDirectory()
                else -> volume.uuid?.let { File("/storage", it) }
            }
            directory?.takeIf { it.exists() }?.let {
                StorageLocation(volume.getDescription(context), it, volume.isRemovable)
            }
        }
    } else {
        listOf(StorageLocation("内部存储", Environment.getExternalStorageDirectory(), false))
    }
    return locations.distinctBy { it.directory.absolutePath }
}

@Composable
fun SettingsScreen(repository: AppRepository, onDirectories: () -> Unit, onBack: () -> Unit) {
    var settings by remember { mutableStateOf(repository.settings()) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun update(value: PlayerSettings) { settings = value; repository.saveSettings(value); android.widget.Toast.makeText(context, "设置已保存", android.widget.Toast.LENGTH_SHORT).show() }

    PageScaffold("设置", onBack) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SettingRow(Icons.Default.Folder, "管理扫描目录", "添加、删除和重新扫描") { onDirectories() } }
            item { SettingRow(Icons.Default.DeleteSweep, "清除播放历史", "清除所有续播进度") { confirmClear = true } }
            item { ChoiceRow("字幕大小", subtitleSizeLabel(settings.subtitleSize), listOf(SubtitleSize.SMALL, SubtitleSize.MEDIUM, SubtitleSize.LARGE, SubtitleSize.EXTRA_LARGE)) { update(settings.copy(subtitleSize = it)) } }
            item { ChoiceRow("字幕颜色", subtitleColorLabel(settings.subtitleColor), listOf(SubtitleColor.WHITE, SubtitleColor.YELLOW, SubtitleColor.GREEN)) { update(settings.copy(subtitleColor = it)) } }
            item { ChoiceRow("快进快退", "${settings.seekSeconds}秒", listOf(5, 10, 30)) { update(settings.copy(seekSeconds = it)) } }
            item {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SkipNext, null, tint = FocusYellow); Spacer(Modifier.width(16.dp)); Text("自动续播", fontSize = 25.sp, modifier = Modifier.weight(1f))
                    Switch(checked = settings.autoPlayNext, onCheckedChange = { update(settings.copy(autoPlayNext = it)) })
                }
            }
            item { SettingRow(Icons.Default.Info, "关于", "简易电视播放器  ${BuildConfig.VERSION_NAME}") {} }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("清除播放历史") },
        text = { Text("确定清除所有播放历史？此操作不可恢复。") },
        confirmButton = { TextButton(onClick = { scope.launch { repository.clearHistory() }; confirmClear = false }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}

@Composable
private fun <T> ChoiceRow(label: String, selected: String, choices: List<T>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(Icons.Default.Tune, label, selected) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choiceLabel(choice), fontSize = 20.sp) }, onClick = { onSelect(choice); open = false }) }
        }
    }
}

private fun subtitleSizeLabel(value: SubtitleSize) = when (value) {
    SubtitleSize.SMALL -> "小"; SubtitleSize.MEDIUM -> "中"; SubtitleSize.LARGE -> "大"; SubtitleSize.EXTRA_LARGE -> "超大"
}

private fun subtitleColorLabel(value: SubtitleColor) = when (value) {
    SubtitleColor.WHITE -> "白色"; SubtitleColor.YELLOW -> "黄色"; SubtitleColor.GREEN -> "绿色"
}

private fun choiceLabel(value: Any?): String = when (value) {
    is SubtitleSize -> subtitleSizeLabel(value)
    is SubtitleColor -> subtitleColorLabel(value)
    is Int -> "${value}秒"
    else -> value.toString()
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    TvButton(onClick, Modifier.fillMaxWidth().height(72.dp)) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(title, fontSize = 25.sp, modifier = Modifier.weight(1f)); Text(subtitle, fontSize = 19.sp, color = Color.LightGray); Spacer(Modifier.width(10.dp)); Icon(Icons.Default.ChevronRight, null)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(onBack, Modifier.size(58.dp)) { Icon(Icons.Default.ArrowBack, "返回") }
            Spacer(Modifier.width(18.dp)); Text(title, fontSize = 34.sp)
        }
        Spacer(Modifier.height(26.dp)); content()
    }
}

@Composable
private fun ScanDialog(path: String, found: Int) {
    AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("正在扫描") }, text = {
        Column { Text(path); Spacer(Modifier.height(16.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); Text("已找到 $found 部电视剧") }
    })
}
