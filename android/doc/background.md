# 后台保活与自启动

> **何时阅读**：当你要修改进程保活策略、双前台服务、WiFi 锁管理、或设备重启自启动逻辑时，阅读此文件。

## 功能概述

为确保音乐播放不被国产 ROM 随意杀死，采用**双层前台保活 + WiFi 锁 + 自启动恢复**的方案：

1. **KeepAliveService** — 独立的轻量级前台服务，纯维持进程优先级
2. **MusicPlayerService** — 主播放前台服务，持有 ExoPlayer + MediaSession
3. **WiFi Lock** — 播放时自动获取，防止 WiFi 进入省电导致网络中断
4. **BootReceiver + ManufacturerCompat** — 设备重启后自动启动，兼容国产 ROM

## 关键文件

| 文件 | 职责 |
|------|------|
| `service/KeepAliveService.kt` | 前台服务，IMPORTANCE_MIN，与 MusicPlayerService 实现双层保活 |
| `player/MusicPlayerService.kt` | 前台服务，IMPORTANCE_HIGH，播放 + MediaSession + WiFi 锁 |
| `receiver/BootReceiver.kt` | BroadcastReceiver，BOOT_COMPLETED 后启动 KeepAliveService |
| `receiver/ManufacturerCompat.kt` | 工具类，获取小米/华为/OPPO/VIVO 自启动管理 Intent |

## 双层前台保活

### 为什么需要两个前台服务？

国产 ROM（小米、OPPO、Vivo、华为）对后台进程的限制极其严格。单个前台服务有被优化为后台的风险。通过两个独立的前台服务，即使一个被降级，另一个仍然维持进程优先级。

### 架构

```
KeepAliveService (前台)
├─ NotificationManager.IMPORTANCE_MIN
├─ 通知栏优先级最低
├─ 无 UI 操作，纯保活
└─ onTaskRemoved() 不停止

+

MusicPlayerService (前台)
├─ NotificationManager.IMPORTANCE_LOW
├─ 播放时显示正常通知
├─ START_STICKY 自重启
└─ onTaskRemoved() 不停止

==> 双重保护，进程优先级不下降
```

### 初始化流程

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 启动两个前台服务
    ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    ContextCompat.startForegroundService(this, Intent(this, MusicPlayerService::class.java))
}
```

### KeepAliveService 实现

```kotlin
class KeepAliveService : Service() {
    override fun onCreate() {
        // 初始化通知栏
        startForeground(NOTIFICATION_ID, buildNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // 系统杀死后自动重启
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 不调用 stopSelf()，保持服务运行
    }
}
```

## WiFi Lock 管理

### 目的

播放流媒体时，WiFi 可能进入省电模式（WiFi suspend），导致网络中断。WiFi Lock 防止这种情况。

### 实现

```kotlin
// MusicPlayerService.kt
wifiLock = (getSystemService(Context.WIFI_SERVICE) as? WifiManager)
    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioPlayer:WifiLock")

player.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            wifiLock?.acquire()
        } else {
            wifiLock?.release()
        }
    }
})
```

**必须的权限**：
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

## 自启动恢复

### BootReceiver

设备重启后，系统发送 `BOOT_COMPLETED` 广播，BootReceiver 捕捉并启动 KeepAliveService。

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
        }
    }
}
```

**必须的权限**：
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

**Manifest 注册**：
```xml
<receiver android:name=".receiver.BootReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

### ManufacturerCompat - 国产 ROM 自启动

某些国产 ROM 在系统设置中有「应用自启动管理」，需要手动允许应用自启。ManufacturerCompat 可跳转到该管理界面。

```kotlin
object ManufacturerCompat {
    fun getAutoStartIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("huawei") -> { /* ... */ }
            manufacturer.contains("oppo") -> { /* ... */ }
            manufacturer.contains("vivo") -> { /* ... */ }
            else -> null
        }
    }
}
```

**使用场景**：
- App 首次启动时，检测是否为国产 ROM
- 提示用户「需要在系统设置中允许应用自启动」
- 显示按钮跳转到该界面

## 缓存管理 - AudioCache

虽然缓存逻辑在 player.md 已提及，但后台保活与缓存的关系值得注意：

- **缓存位置**：`context.cacheDir/audio_cache/`
- **大小限制**：设备可用磁盘空间的 60%
- **驱逐策略**：LRU（最近最少使用）
- **集成时机**：MusicPlayerService 启动时创建 CacheDataSource

当进程被系统恢复后，AudioCache 的数据仍然保留在磁盘，无需重新下载。

## 进度保存与恢复 - LastPlayedStore

进程被杀死前，或定期（每 5 秒），PlayerViewModel 会保存当前播放进度：

```kotlin
LastPlayedStore.save(
    context,
    albumId = currentAlbumId,
    trackIndex = player.currentMediaItemIndex,
    positionMs = player.currentPosition
)
```

App 重启时，MainActivityzhu 的 `tryRestoreLastPlayed()` 加载进度：

```kotlin
val lastPlayed = LastPlayedStore.load(context)
if (lastPlayed.albumId != -1) {
    // 异步加载该专辑歌曲列表，然后恢复播放位置
    playerViewModel.restoreLastPlayed(tracks, lastPlayed.trackIndex, lastPlayed.positionMs)
}
```

## 测试与验证

### 测试后台保活

1. **正常后台播放**：
   - 开始播放任意歌曲
   - 按 Home 键或切到其他应用
   - 音乐应继续播放，5 秒后按 Power 唤醒屏幕再次确认

2. **杀进程重启**：
   - 开始播放，记住当前歌曲和进度
   - `adb shell am force-stop com.qqt.music`
   - 重新打开应用，应显示进度恢复提示（可选）
   - 音乐从上次位置继续播放

3. **设备重启**：
   - 开始播放
   - 重启设备
   - 重新打开应用，音乐应从上次位置继续播放

### logcat 日志标记

- `MusicPlayerService` → 后台服务、WiFi 锁
- `KeepAliveService` → 保活服务
- `BootReceiver` → 重启恢复
- `PlayerViewModel` → 播放控制、进度保存

## 已知问题与限制

1. **某些极端 ROM**
   - 极少数定制度极高的 ROM（如某些企业专用版）可能有更激进的应用杀死策略
   - 此时即使双前台服务也无法完全阻止

2. **国产 ROM 自启动需手动配置**
   - ManufacturerCompat 只能跳转到设置页面，无法自动勾选
   - 用户必须手动操作

3. **电池优化白名单**
   - 某些 ROM 的电池优化会忽略前台服务
   - 可建议用户在系统设置 → 电池 → 省电模式中添加应用白名单

## 权限汇总

完整的 AndroidManifest.xml 权限声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```
