package com.securechat.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securechat.features.auth.AuthScreen
import com.securechat.features.chat.ChatScreen

/** Type-safe route constants. */
object Routes {
    const val AUTH  = "auth"
    const val MAIN  = "main"
    const val CHAT  = "chat/{conversationId}/{conversationTitle}"

    fun chat(conversationId: String, title: String) =
        "chat/${conversationId}/${title}"
}

/**
 * Root [NavHost] wiring all screens together.
 *
 * [Routes.AUTH] is the start destination. [AuthViewModel] auto-skips past it
 * if a Supabase session is already active. [Routes.MAIN] hosts the bottom-bar
 * tabbed experience (Chats / Contacts / Settings, see [MainScreen]) — opening
 * a specific conversation or logging out both navigate OUT of MAIN to the
 * appropriate full-screen route.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
    ) {
        // ── Auth (Login / Register) ─────────────────────────────────────────
        composable(route = Routes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        // ── Main (bottom-bar tabs: Chats / Contacts / Settings) ─────────────
        composable(route = Routes.MAIN) {
            MainScreen(
                onOpenConversation = { id, title ->
                    navController.navigate(Routes.chat(id, title))
                },
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }

        // ── Chat Screen (full-screen, outside the bottom bar) ────────────────
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
