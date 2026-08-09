package com.wearsic.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
     * Clear all settings
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
