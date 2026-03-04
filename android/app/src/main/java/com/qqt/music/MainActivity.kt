package com.qqt.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.ui.navigation.AppNavigation
import com.qqt.music.ui.theme.QQTMusicTheme
import com.qqt.music.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            QQTMusicTheme {
                AppNavigation(playerViewModel = playerViewModel)
            }
        }
    }
}
