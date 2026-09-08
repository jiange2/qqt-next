# 播放期前台服务保活取代双层常驻保活

废止 ADR 0001（双层前台服务保活）：删除 `KeepAliveService` 与 `BootReceiver`，保活只由 `MusicPlayerService` 一个 mediaPlayback 前台服务承担，生命周期交 Media3 默认——app 打开即经 MediaController 绑定连接（绑定自动拉起服务），播放开始才进入前台发布媒体通知，空闲且无控制器连接时自动停止；`onTaskRemoved` 与 `START_STICKY` 还原默认，手动 WiFi 锁删除（`setWakeMode(C.WAKE_MODE_NETWORK)` 已覆盖其用途），`FOREGROUND_SERVICE_SPECIAL_USE`、`RECEIVE_BOOT_COMPLETED`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE` 权限随之移除。

动因：双层常驻在旧系统上制造了"守护有效"的假象，而 Android 14+ 不允许从 BOOT_COMPLETED 启动 specialUse 前台服务，`BootReceiver` 的 try-catch 又把 `ForegroundServiceStartNotAllowedException` 静默吞掉——保活路径在新系统上悄悄失效，行为跨版本不一致且难察觉；"打开未播放也挂一条常驻通知"本身就是双层结构的副作用。代价与补偿：空闲时进程不再有前台服务托底，进程被杀后由播放进度恢复（ADR 0010）兜底；国产 ROM 的激进查杀由白名单引导补偿（App 打开时引导加入电池优化白名单；厂商自启动页跳转工具保留但不主动提醒）。权限申请时机刻意定为 app 打开而非首次播放：`POST_NOTIFICATIONS`（API 33+ 运行时申请，连续拒绝 2 次后系统自限不再弹窗）与电池优化引导尽早完成，避免播放前被弹窗打断。不要因为"空闲时服务会退出"而重新加回守护服务、手动锁或心跳。
