package com.qqt.music.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 断网检测（ADR 0012）：默认网络有效性的全局状态源，供响应快照拦截器
 * （断网快回放，省去白等超时）与离线横幅（AppNavigation 常显）共同消费。
 *
 * 以 NET_CAPABILITY_VALIDATED 为准——连上 Wi-Fi 但未通过联网验证
 * （captive portal、需要登录等）同样视为离线；Wi-Fi/蜂窝切换时状态自动跟随。
 */
object NetworkMonitor {

    private val _offline = MutableStateFlow(false)

    /** true = 当前默认网络缺失或未通过联网验证 */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    val isOffline: Boolean get() = _offline.value

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
