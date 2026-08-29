# 全 App 唯一的 ExoPlayer 实例驻留在播放服务内

整个应用只有一个 `ExoPlayer` 实例，由 `MusicPlayerService`（MediaSessionService）创建并持有；所有 UI（包括 `PlayerViewModel`）通过 `MediaController` 间接控制它，本地不保存任何播放状态副本。

之所以不在 ViewModel 或各页面里直接创建播放器：播放必须跨越页面切换、退到后台、锁屏等场景持续存活，且系统媒体控制（通知栏/锁屏/蓝牙）只能通过 MediaSession 暴露；把播放器放进服务并经由 MediaController 访问，一处实例同时满足了这两点，还顺带让音频缓存（`AudioCache`）与 WiFi 锁的生命周期有唯一归属。代价是 UI 侧的控制是异步的（`MediaController` 连接建立前调用无效，`PlayerViewModel` 用延迟连接兜底），调试时不能假设调用即生效。早期存在一个被弃用的直连封装 `MusicPlayer.kt`，不要误用或复活它。
