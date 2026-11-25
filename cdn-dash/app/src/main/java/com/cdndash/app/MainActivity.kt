package com.cdndash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cdndash.app.ui.navigation.CdnDashApp
import com.cdndash.app.ui.theme.CdnDashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CdnDashTheme {
                val app = application as CdnDashApplication
                CdnDashApp(
                    accountStore = app.accountStore,
                    aliyunApi = app.aliyunApi,
                )
            }
        }
    }
}
