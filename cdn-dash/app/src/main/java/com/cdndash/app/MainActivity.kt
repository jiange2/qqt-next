package com.cdndash.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cdndash.app.ui.lock.AppLockGate
import com.cdndash.app.ui.navigation.CdnDashApp
import com.cdndash.app.ui.theme.CdnDashTheme

/**
 * 入口 Activity 继承 [FragmentActivity]：androidx.biometric 的 BiometricPrompt 以
 * FragmentActivity 为宿主（票据 09）。
 *
 * 生物识别门禁 [AppLockGate] 套在导航根外层：先过门禁，通过后才渲染 [CdnDashApp]。
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CdnDashTheme {
                val app = application as CdnDashApplication
                AppLockGate(accountStore = app.accountStore) {
                    CdnDashApp(
                        accountStore = app.accountStore,
                        aliyunApi = app.aliyunApi,
                    )
                }
            }
        }
    }
}
