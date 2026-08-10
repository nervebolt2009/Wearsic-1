package com.wearsic.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Extension property for DataStore
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manager for app settings using DataStore Preferences
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val YOUTUBE_COOKIE = stringPreferencesKey("youtube_cookie")
        private val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val AUTO_CACHE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("auto_cache_enabled")
        // Last-known-good JSON snapshots so favorites/playlists stay visible
        // when the server is unreachable (offline fallback).
        private val FAVORITES_CACHE = stringPreferencesKey("favorites_cache")
        private val PLAYLISTS_CACHE = stringPreferencesKey("playlists_cache")

        const val DEFAULT_CACHE_SIZE_MB = 256
        const val DEFAULT_AUTO_CACHE_ENABLED = false
    }
    
    /**
     * Get server URL as Flow
     */
    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: ""
    }
    
    /**
     * Get API key as Flow
     */
    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }
    
    /**
     * Save server URL
     */
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }
    
    /**
     * Save API key
     */
    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }
    
    /**
     * Get the saved YouTube browser cookie as a Flow
     */
    val youtubeCookie: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[YOUTUBE_COOKIE] ?: ""
    }
    
    /**
     * Save the YouTube browser cookie
     */
    suspend fun saveYoutubeCookie(cookie: String) {
        context.dataStore.edit { preferences ->
            preferences[YOUTUBE_COOKIE] = cookie.trim()
        }
    }

    /**
     * Offline audio cache size limit in megabytes.
     */
    val cacheSizeMb: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CACHE_SIZE_MB] ?: DEFAULT_CACHE_SIZE_MB
    }    /**
     * Save the offline audio cache size limit in megabytes.
     */
    suspend fun saveCacheSizeMb(mb: Int) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_SIZE_MB] = mb.coerceAtLeast(0)
        }
    }

    /**
     * Whether playback may finish caching the current song automatically.
     */
    val autoCacheEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CACHE_ENABLED] ?: DEFAULT_AUTO_CACHE_ENABLED
    }

    suspend fun saveAutoCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CACHE_ENABLED] = enabled
        }
    }

    /**
     * Clear all settings
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Last-known-good favorites snapshot (JSON-encoded list of tracks).
     */
    suspend fun favoritesCache(): String? = context.dataStore.data.first()[FAVORITES_CACHE]

    suspend fun saveFavoritesCache(json: String) {
        context.dataStore.edit { preferences ->
            preferences[FAVORITES_CACHE] = json
        }
    }

    /**
     * Last-known-good playlists snapshot (JSON-encoded list of playlists).
     */
    suspend fun playlistsCache(): String? = context.dataStore.data.first()[PLAYLISTS_CACHE]

    suspend fun savePlaylistsCache(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PLAYLISTS_CACHE] = json
        }
    }
}
