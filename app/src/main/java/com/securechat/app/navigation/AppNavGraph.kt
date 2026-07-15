package com.securechat.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securechat.features.chat.ChatScreen
import com.securechat.features.conversations.ConversationListScreen

/** Type-safe route constants. */
object Routes {
    const val CONVERSATION_LIST = "conversations"
    const val CHAT              = "chat/{conversationId}/{conversationTitle}"

    fun chat(conversationId: String, title: String) =
        "chat/${conversationId}/${title}"
}

/**
 * Root [NavHost] wiring all screens together.
 *
 * Screens are registered as named destinations. The back stack is fully
 * managed by Compose Navigation — no FragmentManager involved.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATION_LIST,
    ) {
        // ── Conversation List ───────────────────────────────────────────────
        composable(route = Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onOpenConversation = { id, title ->
                    navController.navigate(Routes.chat(id, title))
                },
            )
        }

        // ── Chat Screen ─────────────────────────────────────────────────────
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("conversationTitle") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val conversationId    = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val conversationTitle = backStackEntry.arguments?.getString("conversationTitle") ?: ""

            ChatScreen(
                conversationId    = conversationId,
                conversationTitle = conversationTitle,
                onNavigateBack    = { navController.popBackStack() },
            )
        }
    }
}
