package com.mtg.commander.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.ui.screen.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Players : Screen("players")
    object Decks : Screen("decks")
    object NewGame : Screen("new_game")
    object Leaderboard : Screen("leaderboard")
    object DeckStats : Screen("deck_stats")
    object GameHistory : Screen("game_history")
    object ActiveGame : Screen("active_game/{gameId}") {
        fun createRoute(gameId: Long) = "active_game/$gameId"
    }
    object GameDetail : Screen("game_detail/{gameId}") {
        fun createRoute(gameId: Long) = "game_detail/$gameId"
    }
    object PreconPicker : Screen("precon_picker/{playerId}") {
        fun createRoute(playerId: Long) = "precon_picker/$playerId"
    }
    object RandomOpponentStats : Screen("random_opponent_stats")
    object GlobalDamageStats : Screen("global_damage_stats")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as MTGCommanderApp

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                app = app,
                onNewGame = { navController.navigate(Screen.NewGame.route) },
                onResume = { gameId -> navController.navigate(Screen.ActiveGame.createRoute(gameId)) },
                onPlayers = { navController.navigate(Screen.Players.route) },
                onDecks = { navController.navigate(Screen.Decks.route) },
                onLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
                onDeckStats = { navController.navigate(Screen.DeckStats.route) },
                onHistory = { navController.navigate(Screen.GameHistory.route) },
                onRandomStats = { navController.navigate(Screen.RandomOpponentStats.route) },
                onGlobalDamage = { navController.navigate(Screen.GlobalDamageStats.route) }
            )
        }
        composable(Screen.Players.route) {
            PlayersScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(Screen.Decks.route) { backStackEntry ->
            DecksScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onPickPrecon = { playerId ->
                    navController.navigate(Screen.PreconPicker.createRoute(playerId))
                },
                backStackEntry = backStackEntry
            )
        }
        composable(Screen.NewGame.route) {
            NewGameScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onGameStarted = { gameId ->
                    navController.navigate(Screen.ActiveGame.createRoute(gameId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
        composable(
            route = Screen.ActiveGame.route,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStack ->
            val gameId = backStack.arguments!!.getLong("gameId")
            ActiveGameScreen(
                gameId = gameId,
                app = app,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(Screen.DeckStats.route) {
            DeckStatsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(Screen.GameHistory.route) {
            GameHistoryScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onGameDetail = { gameId -> navController.navigate(Screen.GameDetail.createRoute(gameId)) }
            )
        }
        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStack ->
            val gameId = backStack.arguments!!.getLong("gameId")
            GameDetailScreen(
                gameId = gameId,
                app = app,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.RandomOpponentStats.route) {
            RandomOpponentStatsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(Screen.GlobalDamageStats.route) {
            GlobalDamageStatsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.PreconPicker.route,
            arguments = listOf(navArgument("playerId") { type = NavType.LongType })
        ) { backStack ->
            val playerId = backStack.arguments!!.getLong("playerId")
            PreconPickerScreen(
                repo = app.preconRepository,
                onPicked = { precon ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_precon_name", precon.name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_commander", precon.commanderName)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_colors", precon.colors)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_image_url", precon.displayArtUrl)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_player_id", playerId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
