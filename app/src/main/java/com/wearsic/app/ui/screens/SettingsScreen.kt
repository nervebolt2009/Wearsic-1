package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.*
import com.wearsic.app.R

/**
 * Settings screen with server URL configuration and app preferences
 */
@Composable
fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    isConnected: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    apiKey: String = "",
    onApiKeyChange: (String) -> Unit = {},
    youtubeCookie: String = "",
    onYoutubeCookieChange: (String) -> Unit = {}
) {
    var editingUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var showApiKey by remember(apiKey) { mutableStateOf(apiKey.isNotBlank()) }
    var showYoutubeCookie by remember(youtubeCookie) { mutableStateOf(youtubeCookie.isNotBlank()) }
    ScreenScaffold(
        modifier = modifier
    ) {
        val listState = rememberScalingLazyListState()
        val rotaryBehavior = RotaryScrollableDefaults.behavior(listState)
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = rotaryBehavior,
            contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // Server Configuration Section
            item {
                Text(
                    text = "Server Configuration",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 8.dp)
                )
            }
            
            // Editable Server URL
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = "Server URL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    BasicTextField(
                        value = editingUrl,
                        onValueChange = {
                            editingUrl = it
                            onServerUrlChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontSize = 12.sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (editingUrl.isEmpty()) {
                                Text(
                                    text = "No server configured",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            // Test Connection Button
            item {
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 8.dp),
                    // Use the field value here, not the asynchronously persisted
                    // DataStore value. This keeps the button usable immediately
                    // after the user finishes editing a URL.
                    enabled = editingUrl.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isConnected -> Color(0xFF1DB954).copy(alpha = 0.3f)
                            isLoading -> Color.White.copy(alpha = 0.1f)
                            else -> Color.White.copy(alpha = 0.15f)
                        },
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (isConnected) R.drawable.ic_favorite_filled
                                else R.drawable.ic_search
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = when {
                            isLoading -> "Testing..."
                            isConnected -> "Connected"
                            else -> "Test Connection"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        )
                    )
                }
            }
            
            // Connection Status
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Color(0xFF1DB954)
                                else Color(0xFFE91E63)
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = if (isConnected) "Server is online" else "Server is offline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Optional API key stays below the primary connection status to preserve glanceability.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = { showApiKey = !showApiKey },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showApiKey) "Hide API key" else "Add API key (optional)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.58f)
                        )
                    }
                    if (showApiKey) {
                        BasicTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            decorationBox = { innerTextField ->
                                if (apiKey.isEmpty()) {
                                    Text(
                                        text = "Paste server key",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // YouTube session cookie lets the server pass YouTube bot checks.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "YouTube Cookie (fixes playback)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    TextButton(
                        onClick = { showYoutubeCookie = !showYoutubeCookie },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                showYoutubeCookie -> "Hide YouTube cookie"
                                youtubeCookie.isNotBlank() -> "YouTube cookie saved — tap to edit"
                                else -> "Add YouTube cookie (fixes playback)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (youtubeCookie.isNotBlank() && !showYoutubeCookie) {
                                Color(0xFFB7F397)
                            } else {
                                Color.White.copy(alpha = 0.58f)
                            }
                        )
                    }
                    if (showYoutubeCookie) {
                        BasicTextField(
                            value = youtubeCookie,
                            onValueChange = onYoutubeCookieChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("youtube-cookie-field"),
                            decorationBox = { innerTextField ->
                                if (youtubeCookie.isEmpty()) {
                                    Text(
                                        text = "Paste cookie from YouTube",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        Text(
                            text = "Only needed when streams fail with a bot check. Paste the full cookie line from your logged-in browser.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp)
                        )
                    }
                }
            }
            
            // About Section
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 24.dp, bottom = 8.dp)
                )
            }
            
            // App Version
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wearsic",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "v1.0.0",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Music streaming for Wear OS",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Preview function for SettingsScreen
 */
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        serverUrl = "https://your-tunnel.example.com",
        onServerUrlChange = {},
        onTestConnection = {},
        isConnected = true,
        isLoading = false
    )
}
