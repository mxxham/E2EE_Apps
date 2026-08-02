package com.securechat.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.securechat.features.conversations.ConversationListScreen
import com.securechat.features.conversations.NewChatScreen
import com.securechat.features.settings.SettingsScreen

/** Bottom-bar tab destinations, nested inside [Routes.MAIN]. */
private object MainTabs {
    const val CHATS    = "main/chats"
    const val CONTACTS = "main/contacts"
    const val SETTINGS = "main/settings"
}

/**
 * Hosts the 3-tab bottom navigation bar (Chats / Contacts / Settings).
 * Tapping into a specific conversation, or starting a new one from Contacts,
 * navigates to full-screen routes OUTSIDE this Scaffold (via [onOpenConversation]),
 * which hides the bottom bar while chatting — matching standard messaging-app UX.
 */
@Composable
fun MainScreen(
    onOpenConversation: (id: String, title: String) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by tabNavController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == MainTabs.CHATS,
                    onClick = { navigateToTab(tabNavController, MainTabs.CHATS) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                )
                NavigationBarItem(
                    selected = currentRoute == MainTabs.CONTACTS,
                    onClick = { navigateToTab(tabNavController, MainTabs.CONTACTS) },
                    icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
                    label = { Text("Contacts") },
                )
                NavigationBarItem(
                    selected = currentRoute == MainTabs.SETTINGS,
                    onClick = { navigateToTab(tabNavController, MainTabs.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTabs.CHATS,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainTabs.CHATS) {
                ConversationListScreen(onOpenConversation = onOpenConversation)
            }
            composable(MainTabs.CONTACTS) {
                NewChatScreen(onConversationCreated = onOpenConversation)
            }
            composable(MainTabs.SETTINGS) {
                SettingsScreen(onLoggedOut = onLoggedOut)
            }
        }
    }
}

/** Standard "switch tabs" nav pattern — preserves each tab's back stack/state. */
private fun navigateToTab(navController: androidx.navigation.NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
