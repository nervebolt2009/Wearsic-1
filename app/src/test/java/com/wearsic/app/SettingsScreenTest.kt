package com.wearsic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.ui.screens.SettingsScreen
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric test for SettingsScreen
 * Verifies UI elements are present and correctly displayed
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testSettingsScreenDisplaysHeader() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify header is displayed
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
    
    @Test
    fun testSettingsScreenDisplaysServerUrlField() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify server URL is displayed
        composeTestRule.onNodeWithText("https://example.com").assertIsDisplayed()
    }
    
    @Test
    fun testSettingsScreenDisplaysTestConnectionButton() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify test connection button exists
        composeTestRule.onNodeWithText("Test Connection").assertExists()
    }
    
    @Test
    fun testSettingsScreenDisplaysConnectedStatus() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = true,
                    isLoading = false
                )
            }
        }
        
        // Verify connected status is displayed
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server is online").assertIsDisplayed()
    }
    
    @Test
    fun testSettingsScreenDisplaysOfflineStatus() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify offline status is displayed
        composeTestRule.onNodeWithText("Server is offline").assertIsDisplayed()
    }
    
    @Test
    fun testSettingsScreenDisplaysLoadingState() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = true
                )
            }
        }
        
        // Verify loading state is displayed
        composeTestRule.onNodeWithText("Testing...").assertIsDisplayed()
    }
    
    @Test
    fun testSettingsScreenDisplaysEmptyServerUrl() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify no server configured message
        composeTestRule.onNodeWithText("No server configured").assertIsDisplayed()
    }
    
    @Test
    fun testTestConnectionButtonClickable() {
        var testConnectionClicked = false
        
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = { testConnectionClicked = true },
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Click test connection button
        composeTestRule.onNodeWithText("Test Connection").performClick()
        
        // Verify callback was called
        assert(testConnectionClicked) { "Test connection callback should be called" }
    }
    
    @Test
    fun testYoutubeCookieFieldSavesValue() {
        var changedCookie = ""
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false,
                    youtubeCookie = "",
                    onYoutubeCookieChange = { changedCookie = it }
                )
            }
        }

        // The toggle sits below the lazy-list fold, so scroll the list until the
        // button is composed before clicking it.
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Add YouTube cookie (fixes playback)"))
        composeTestRule.onNodeWithText("Add YouTube cookie (fixes playback)").performClick()
        composeTestRule.onNodeWithTag("youtube-cookie-field")
            .performScrollTo()
            .performTextInput("SID=abc123")

        assert(changedCookie.contains("SID=abc123")) { "Cookie callback should receive typed text" }
    }

    @Test
    fun testTestConnectionButtonDisabledWhenEmptyUrl() {
        composeTestRule.setContent {
            WearsicTheme {
                SettingsScreen(
                    serverUrl = "",
                    onServerUrlChange = {},
                    onTestConnection = {},
                    isConnected = false,
                    isLoading = false
                )
            }
        }
        
        // Verify test connection button is disabled
        composeTestRule.onNodeWithText("Test Connection").assertIsNotEnabled()
    }
}
