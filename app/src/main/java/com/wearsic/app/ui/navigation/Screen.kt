package com.wearsic.app.ui.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object NowPlaying : Screen("now_playing")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Queue : Screen("queue")
    object Settings : Screen("settings")
}
