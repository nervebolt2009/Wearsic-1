package com.wearsic.app.ui.navigation

import androidx.compose.runtime.*

/**
 * Navigation manager for handling screen transitions
 * Uses state-based navigation without external dependencies
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
