package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.*
import com.wearsic.app.R
import com.wearsic.app.data.preferences.SettingsManager
import java.util.Locale

private val Accent = Color(0xFFB7F397)

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10) {
        "${mb.toInt()} MB"
    } else {
        String.format(Locale.ROOT, "%.1f MB", mb)
    }
}

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
    onYoutubeCookieChange: (String) -> Unit = {},
    onBack: () -> Unit = {},
    cacheSizeMb: Int = SettingsManager.DEFAULT_CACHE_SIZE_MB,
    onCacheSizeMbChange: (Int) -> Unit = {},
    cacheUsageBytes: Long = 0L,
    onClearCache: () -> Unit = {},
    autoCacheEnabled: Boolean = SettingsManager.DEFAULT_AUTO_CACHE_ENABLED,
    onAutoCacheEnabledChange: (Boolean) -> Unit = {}
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
            // Header with back navigation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack)
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 40.dp)
                    )
                }
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
            
            // Offline Cache Section
            item {
                Text(
                    text = "Offline Cache",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 24.dp, bottom = 8.dp)
                )
            }

            // Cache usage and size limit
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = "Cached audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytes(cacheUsageBytes)} used · limit ${cacheSizeMb} MB",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Color.White,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(128, 256, 512, 1024).forEach { mb ->
                            SizeChip(
                                label = if (mb >= 1024) "1GB" else "${mb}MB",
                                selected = cacheSizeMb == mb,
                                onClick = { onCacheSizeMbChange(mb) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = "Songs you play are stored on the watch. The limit applies from the next playback session.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            // Automatic completion is limited by the service to charging or Wi-Fi.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-cache played songs",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color.White
                        )
                        Text(
                            text = "Finish the current song on Wi-Fi or while charging",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.48f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (autoCacheEnabled) Accent.copy(alpha = 0.28f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable { onAutoCacheEnabledChange(!autoCacheEnabled) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (autoCacheEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (autoCacheEnabled) Accent else Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // Clear cache
            item {
                Button(
                    onClick = onClearCache,
                    enabled = cacheUsageBytes > 0L,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63).copy(alpha = 0.2f),
                        contentColor = Color(0xFFE91E63),
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (cacheUsageBytes > 0L) "Clear Cache" else "Cache is empty",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )
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

@Composable
private fun SizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f),
            contentColor = if (selected) Accent else Color.White.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * Compact back button used at the top of secondary screens.
 */
@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.85f)
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = "Back",
            modifier = Modifier.size(20.dp)
        )
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
