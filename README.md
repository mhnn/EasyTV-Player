# 简易电视播放器

面向 Android TV 的本地电视剧播放器原型。支持 SAF 目录授权、一级目录扫描、集数排序、SQLite 播放历史、续播、Media3 播放、快进快退、倍速和设置页面。

## 构建

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。应用最低 API 21，目标 API 34，默认使用 ARM/ARM64 ABI 的 Android TV 运行环境。

## 实现取舍

- 使用 Android Storage Access Framework 取得用户明确授权的目录 URI，兼容 Android 5+，避免依赖全盘存储权限。
- 使用平台 SQLite 保持数据层轻量；表结构覆盖扫描目录、电视剧、剧集和播放历史。
- 使用 Media3 ExoPlayer 播放本地 URI；不包含网络、统计或广告 SDK。
- 扫描只读取授权目录的直接子目录；视频文件要求扩展名受支持、非隐藏且至少 10 MB。

## 开源检索记录

检索关键词：`android tv local video player kotlin`、`android tv media3 exoplayer leanback`、`android tv file browser room compose`。

- [OwnTV](https://github.com/ahXN00/OwnTV)：377 stars，GPL-3.0；产品方向相关，但许可证不适合直接纳入。
- [DangoPlayer](https://github.com/brunochanrio/DangoPlayer)：有 Android/TV 播放器参考价值，但未声明许可证，不复制代码。
- [local-tv](https://github.com/outmanwt/local-tv)：MIT，本地 USB 电视思路相关；规模较小，仅作为产品方向参考。

本项目未复制上述仓库代码，UI、扫描、数据模型和交互均按 PRD 独立实现。
