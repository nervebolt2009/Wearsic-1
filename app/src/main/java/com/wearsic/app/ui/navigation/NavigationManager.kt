package com.wearsic.app.ui.navigation

import androidx.compose.runtime.*

/**
 * Navigation manager for handling screen transitions.
 * Uses state-based navigation without external dependencies.
 *
 * Now Playing is the app root: navigating to it clears the back stack, so the
 * back behavior is always predictable — Back from any other screen returns to
 * Now Playing, and Back from Now Playing exits the app. Other screens never
 * stack duplicates of themselves, which previously made back navigation
 * inconsistent (e.g. landing on the same screen twice or skipping screens).
 */
class NavigationManager {
    private val _currentScreen = mutableStateOf<Screen>(Screen.NowPlaying)
    val currentScreen: State<Screen> = _currentScreen

    private val _navigationHistory = mutableStateListOf<Screen>()
    val navigationHistory: List<Screen> = _navigationHistory

    /**
     * Navigate to a new screen
     */
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value == screen) return
        // The root screen: arriving there resets the stack so every back press
        // from a leaf screen returns to Now Playing.
        if (screen == Screen.NowPlaying) {
            _navigationHistory.clear()
            _currentScreen.value = screen
            return
        }
        // Never leave a duplicate of the target in the stack: remove any prior
        // entry, then push the screen we are leaving.
        _navigationHistory.removeAll { it == screen }
        _navigationHistory.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    /**
     * Navigate back to previous screen
     */
    fun navigateBack(): Boolean {
        if (_navigationHistory.isNotEmpty()) {
            val previousScreen = _navigationHistory[_navigationHistory.lastIndex]
            _navigationHistory.removeAt(_navigationHistory.lastIndex)
            _currentScreen.value = previousScreen
            return true
        }
        return false
    }

    /**
     * Check if can navigate back
     */
    fun canNavigateBack(): Boolean = _navigationHistory.isNotEmpty()

    /**
     * Get current screen
     */
    fun getCurrentScreen(): Screen = _currentScreen.value
}
