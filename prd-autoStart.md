# 开机自启动方案

## 目标

设备开机后，在系统允许的前提下自动启动简易电视播放器。播放器本身不需要常驻后台，也不需要保活线程。

## 普通第三方 App 方案

1. 在清单声明 `android.permission.RECEIVE_BOOT_COMPLETED`。
2. 注册导出的 `BroadcastReceiver`，监听 `BOOT_COMPLETED`、`LOCKED_BOOT_COMPLETED`、部分设备使用的 `QUICKBOOT_POWERON` 和 `REBOOT`。
3. 开机广播到达后，等待外接存储挂载；收到 `MEDIA_MOUNTED` 后再检查已授权扫描目录。
4. 使用带 `FLAG_ACTIVITY_NEW_TASK` 的 Intent 尝试启动 `MainActivity`。

该方案只能表达“尝试启动”，不能保证普通安装 App 在所有 Android TV 上自动拉起界面。Android 10 及以上限制后台启动 Activity；电视厂商还可能要求用户把 App 加入自启动白名单。App 被用户强制停止后，系统通常也不会再投递开机广播，直到用户手动打开一次。

## 可靠性分级

- 普通侧载 App：依赖系统版本和厂商策略，可靠性最低。
- 加入厂商自启动白名单：大多数定制电视可用，需用户或设备管理员配置。
- 默认 Launcher、设备所有者或系统预装 App：可控制开机流程，可靠性最高。

## 存储与启动时序

开机广播不代表 USB/外接硬盘已经挂载。启动后应先显示应用界面，再在存储可用时刷新扫描目录；不要因为首次启动时目录不可用而删除数据库记录。

## 参考项目取舍

[AutoStartAndKeepAlive](https://github.com/LiuWeiQiu/AutoStartAndKeepAlive) 的做法包含开机广播和前台保活服务，但项目较旧、未声明许可证，且永久保活不符合本播放器的需求。因此只参考 Android 开机广播方向，不复制其代码。

## 官方依据

- [Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Restrictions on starting activities from the background](https://developer.android.com/guide/components/activities/secure-bal)
