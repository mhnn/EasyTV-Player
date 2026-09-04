# AGENTS.md

面向老年人的 Android TV 本地电视剧播放器（EasyTV）。

## 项目概览

- 应用名：简易电视播放器，`applicationId = com.easytv.player`
- 目标平台：Android TV（`leanback` required，无触屏），横屏
- 最低 API 21 / 目标 34 / 编译 35，仅 `armeabi-v7a` + `arm64-v8a`
- 定位：本地/外接存储电视剧播放，无网络、无统计、无广告

## 构建与运行

```powershell
./gradlew.bat assembleDebug          # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat testDebugUnitTest
```

- JVM Toolchain 17，Kotlin 2.0.21，AGP 8.7.3
- 单模块 `:app`，rootProject.name = "EasyTV"

## 技术栈（实际实现）

| 关注点 | 实现 |
|--------|------|
| UI | Jetpack Compose + Material3（非 Leanback） |
| 播放器 | Media3 ExoPlayer 1.5.1 |
| 数据层 | 原生 SQLite（`SQLiteOpenHelper`，非 Room） |
| 存储访问 | SAF + `DocumentFile`（目录授权 URI，非全盘权限） |
| 缩略图 | 自研 `PlaybackThumbnailStore`，无 Glide/Coil |
| 异步 | Kotlin coroutines |

## 源码结构

`app/src/main/java/com/easytv/player/`

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 入口 + 路由 |
| `AppDatabase.kt` | SQLite 数据层（`easy_tv.db`，版本 3） |
| `AppRepository.kt` | 仓库层，串联 DB 与扫描 |
| `MediaScanner.kt` | SAF 目录扫描、剧集排序 |
| `Models.kt` | 数据模型 + 设置枚举 |
| `PlayerScreen.kt` | 播放页（ExoPlayer） |
| `SettingsScreens.kt` | 设置页 |
| `PlaybackThumbnailStore.kt` | 缩略图生成与缓存 |

## 数据模型（SQLite 表）

- `directories(uri PK, name, added_at)` — 扫描目录
- `series(id PK, directory_uri UNIQUE, name, poster_uri)` — 电视剧
- `episodes(id PK, series_id, uri UNIQUE, name, episode_number, size)` — 剧集
- `history(series_id PK, episode_id, position_ms, duration_ms, updated_at)` — 播放历史

## 关键约定

- 扫描仅一级子目录，每个子目录视为一部电视剧；视频需 ≥10MB、非隐藏、扩展名受支持。
- 剧集排序：优先解析文件名中的集数数字，否则按字母序。
- 续播阈值：进度 `position/duration >= 0.95` 视为已看完，不再显示继续观看。
- 焦点色 `#FFD700`，深色背景 `#1A1A1A`；大字（≥24sp）适配老年人。
- PRD（`prd.md`）非铁律，以完成应用特性为目标，可自行调整实现方式。
- 提交信息/改动需说明具体文件与行号。
