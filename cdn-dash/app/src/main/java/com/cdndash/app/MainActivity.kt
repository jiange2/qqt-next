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
                CdnDashApp()
            }
        }
    }
}
