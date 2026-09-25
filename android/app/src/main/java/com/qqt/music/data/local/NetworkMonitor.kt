package com.qqt.music.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网络与降级状态源（ADR 0012、0017）：默认网络有效性与服务器不可达粘性态，
 * 供响应快照拦截器（断网快回放 / 不可达秒回快照）与横幅（AppNavigation 常显）共同消费。
 *
 * 断网以 NET_CAPABILITY_VALIDATED 为准——连上 Wi-Fi 但未通过联网验证
 * （captive portal、需要登录等）同样视为离线；Wi-Fi/蜂窝切换时状态自动跟随。
 *
 * 服务器不可达（ADR 0017）：手机在线但服务端不可用（主用与回退入口均连接层失败，
 * 或任一入口返回 5xx）时的进程内粘性态，由响应快照拦截器按请求结果驱动进出，
 * 冷启动重置；横幅两因并存时显示断网文案（硬信号优先）。
 */
object NetworkMonitor {

    private val _offline = MutableStateFlow(false)

    /** true = 当前默认网络缺失或未通过联网验证 */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    val isOffline: Boolean get() = _offline.value

    private val _serverUnreachable = MutableStateFlow(false)

    /** true = 服务器不可达降级态（ADR 0017），进程内粘性 */
    val serverUnreachable: StateFlow<Boolean> = _serverUnreachable.asStateFlow()

    val isServerUnreachable: Boolean get() = _serverUnreachable.value

    /** 入口请求连接层失败或收到 5xx 时由响应快照拦截器调用 */
    fun enterServerUnreachable() {
        _serverUnreachable.value = true
    }

    /** 任一入口请求获得非 5xx 应答时由响应快照拦截器调用；恢复不打断当前页面（ADR 0017） */
    fun exitServerUnreachable() {
        _serverUnreachable.value = false
    }

    private var registered = false

    /** 须在 Application.onCreate 调用；重复调用无副作用 */
    fun init(context: Context) {
        if (registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        registered = true
        _offline.value = !isValidated(cm)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _offline.value = false
            }

            override fun onLost(network: Network) {
                // 默认网络丢失后 activeNetwork 可能已切到其他网络，重新核实而非直接判离线
                _offline.value = !isValidated(cm)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _offline.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        })
    }

    private fun isValidated(cm: ConnectivityManager): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
