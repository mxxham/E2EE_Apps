package com.securechat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.securechat.app.navigation.AppNavGraph
import com.securechat.app.ui.theme.SecureChatTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host for the entire app.
 *
 * All navigation is handled by Jetpack Compose Navigation via [AppNavGraph].
 * Edge-to-edge rendering is enabled so the chat screen occupies full screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureChatTheme {
                AppNavGraph()
            }
        }
    }
}
