# Wearsic - Wear OS 6 App Specification
## Samsung Galaxy Watch 7 44mm (Wearsic App)

---

## 1. Executive Summary

**App Name:** Wearsic  
**Platform:** Wear OS 6 (Android 16, API 36)  
**Target Device:** Samsung Galaxy Watch 7 44mm (SM-L310/SM-L315)  
**App Type:** Music Streaming  
**Primary Use Case:** Mixed use (daily companion, exercise companion, ambient information)  
**Backend:** Self-hosted Ktor server with NewPipe Extractor  
**Design Philosophy:** Samsung One UI style with glassmorphism elements  
**Offline Capability:** Critical (smart caching, offline mode)

---

## 2. Device Specifications & Constraints

### 2.1 Hardware Profile
| Component | Specification |
|-----------|---------------|
| **Display** | 1.47" Super AMOLED, 480 × 480 pixels (327 ppi) |
| **Brightness** | Up to 2,000 nits (outdoor visible) |
| **Processor** | Samsung Exynos W1000 (3nm, 5-core) |
| **RAM** | 2 GB LPDDR5 |
| **Storage** | 32 GB eMMC 5.1 |
| **Battery** | 425 mAh (up to 40 hours mixed usage) |
| **Sensors** | Heart rate, SpO2, ECG, BIA, temperature, accelerometer, gyroscope, barometer, geomagnetic, light |
| **Connectivity** | Bluetooth 5.3, Wi-Fi, NFC, LTE (optional), Dual-frequency GPS (L1+L5) |
| **Durability** | 5 ATM + IP68, MIL-STD-810H, Sapphire Crystal |
| **Form Factor** | Round display, 44.4 × 44.4 × 9.7 mm, 33.8g |

### 2.2 Wear OS 6 Specifics
- **OS Base:** Android 16 (API level 36; 6.1 is API 36.1)
- **UI Framework:** Material 3 Expressive
- **Key Libraries:**
  - `androidx.wear.compose:compose-material3:1.5.0`
  - `androidx.wear.compose:compose-foundation:1.5.0`
  - `androidx.wear.compose:compose-navigation:1.5.0`
  - Horologist (`com.google.android.horologist`) 0.8.x line

### 2.3 Design Constraints
- **Round Display:** Avoid fixed-corner content; use `ScreenScaffold`/`AppScaffold`
- **Touch Targets:** Minimum 48dp for finger interaction
- **Battery Sensitivity:** OLED dark theme required; minimize animations in ambient mode
- **Input Methods:** Touch, rotary bezel/crown, physical buttons, voice

---

## 3. Architecture Overview

### 3.1 High-Level Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                    Wear OS App (Galaxy Watch 7)            │
├─────────────────────────────────────────────────────────────┤
│  Presentation Layer (Compose + Horologist)                  │
│  ├── Now Playing Screen                                     │
│  ├── Search Screen                                          │
│  ├── Favorites/Playlists Screen                             │
│  ├── Playlist/Album Screen                                  │
│  ├── Queue Screen                                           │
│  └── Settings Screen                                        │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer                                               │
│  ├── Repository Pattern                                     │
│  ├── Use Cases                                              │
│  └── State Management (ViewModels)                          │
├─────────────────────────────────────────────────────────────┤
│  Data Layer                                                 │
│  ├── Network (Ktor Client + OkHttp)                         │
│  ├── Local Storage (Room + DataStore)                       │
│  └── Media Session (Media3)                                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Ktor Backend Server (Self-Hosted)              │
│  ├── NewPipe Extractor (YouTube search/streaming)           │
│  ├── SQLite via Exposed ORM                                 │
│  ├── Cloudflare Tunnel (HTTPS)                              │
│  └── Shared Secret Authentication                           │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Key Components

#### 3.2.1 Network Layer
- **Ktor Client** with OkHttp engine (symmetry with server stack)
- **Repository Pattern:** Screens never call network client directly
- **Endpoints:**
  - `GET /health` - Connection test
  - `GET /search?q=` - Search via NewPipe Extractor
  - `GET /suggestions?q=` - Autocomplete suggestions
  - `GET /stream/{videoId}` - Audio proxy with Range support
  - `GET /related/{videoId}` - Related tracks for autoplay
  - `GET /playlist?url=` - External YouTube playlist extraction
  - `GET /channel?url=` - Channel uploads/discography
  - `GET|POST|DELETE /favorites` - User favorites
  - `GET|POST /playlists` + track sub-routes

#### 3.2.2 Local Storage
- **Room Database:** Cache tracks, playlists, search history
- **DataStore Preferences:** Server URL, user settings
- **Smart Caching Algorithm:**
  - Cache frequently played tracks
  - Monitor available storage (32GB total)
  - Auto-evict least recently used content
  - Pre-fetch related tracks during playback

#### 3.2.3 Media Playback
- **Media3 Integration:** Full media session support
- **Audio Format Priority:** M4A/AAC 128kbps (hardware-decoded, battery efficient)
- **Fallback:** Opus/WebM only if no AAC available
- **Range Request Support:** Enable seeking/scrubbing

---

## 4. Screen Specifications

### 4.1 Now Playing Screen
**Purpose:** Primary playback control and track information display

#### Layout
- **Background:** Full album art with blur/glassmorphism effect
- **Track Info:** Title and artist centered, white text with subtle shadow
- **Controls:** Play/pause (center), skip forward/back (sides), shuffle, repeat
- **Progress Bar:** Circular progress around screen edge
- **Volume:** Rotary bezel/crown control
- **Additional:** Queue button, favorite toggle, seek bar

#### Interactions
- **Tap center:** Play/pause
- **Tap sides:** Skip forward/back
- **Long press:** Show queue
- **Rotary:** Volume control (primary), seek (secondary with long press)
- **Swipe up:** Show lyrics (if available from server)

#### Ambient Mode
- **Display:** Current track title and artist (low-power)
- **Icon:** Playback state (play/pause) in monochrome
- **No animations:** Static display only

### 4.2 Search Screen
**Purpose:** Find music from server library

#### Layout
- **Search Bar:** Top of screen with keyboard input
- **Results List:** Scrollable list with track previews
- **Recent Searches:** Quick access to previous queries
- **Categories:** Quick filters (Playlists, Artists, Albums)

#### Interactions
- **Tap search bar:** Show on-screen keyboard
- **Rotary:** Scroll through results
- **Tap result:** Start playback
- **Swipe right:** Add to favorites
- **Swipe left:** Add to queue

#### Smart Features
- **Live Suggestions:** Debounced `/suggestions` endpoint as user types
- **Keyboard:** Custom QWERTY keyboard optimized for round display
- **Voice Input:** Long press search bar for voice search (if available)

### 4.3 Favorites/Playlists Screen
**Purpose:** Access saved music and organized playlists

#### Layout
- **Tabs:** Favorites | Playlists
- **Favorites:** List of saved tracks with thumbnails
- **Playlists:** Grid/list of user-created playlists

#### Interactions
- **Tap track:** Start playback
- **Long press:** Show options (remove, edit, share)
- **Rotary:** Scroll through lists
- **Swipe right:** Add to queue
- **Pull to refresh:** Sync with server

#### Categories
- **Mood-based:** Energetic, Relaxing, Focus, etc.
- **Activity-based:** Workout, Commute, Sleep, etc.
- **Custom:** User-created categories

### 4.4 Playlist/Album Screen
**Purpose:** View and play external YouTube playlists or channel content

#### Layout
- **Header:** Playlist/album art with title
- **Track List:** Scrollable list with track numbers
- **Metadata:** Track count, duration, source

#### Interactions
- **Tap track:** Start playback from that point
- **Play All:** Start from beginning
- **Shuffle Play:** Random order
- **Rotary:** Scroll through tracks

#### Pagination
- **Channel Content:** Paginated via continuation tokens
- **Load More:** Automatic loading as user scrolls
- **Progress Indicator:** Loading state for additional content

### 4.5 Queue Screen
**Purpose:** Manage upcoming tracks and playback order

#### Layout
- **Current Track:** Highlighted at top
- **Up Next:** Scrollable list of queued tracks
- **Controls:** Clear queue, save as playlist, shuffle

#### Interactions
- **Drag to reorder:** Change track position
- **Swipe left:** Remove from queue
- **Tap track:** Play immediately
- **Rotary:** Scroll through queue

#### Autoplay
- **Queue Empty:** Automatically load related tracks from `/related/{videoId}`
- **Based On:** Currently playing or last played track
- **Seamless Transition:** No interruption in playback

### 4.6 Settings Screen
**Purpose:** Configure server connection and app preferences

#### Layout
- **Server URL:** Text field with validation
- **Test Connection:** Button to verify server connectivity
- **Cache Management:** Clear cache, view storage usage
- **Audio Quality:** Preference for streaming quality
- **Haptic Feedback:** Enable/disable vibration
- **About:** App version, licenses

#### Server Configuration
- **URL Input:** Custom keyboard for URL entry
- **Validation:** Real-time URL format checking
- **Test Button:** Calls `/health` endpoint, shows success/failure
- **Persistence:** Save with Preferences DataStore

---

## 5. Design System

### 5.1 Visual Style: Glassmorphism
- **Backgrounds:** Frosted glass effects with transparency
- **Blur:** Gaussian blur on album art and backgrounds
- **Depth:** Layered cards with subtle shadows
- **Opacity:** Semi-transparent elements (0.7-0.9 opacity)

### 5.2 Color Palette
- **Primary:** Dynamic colors from album art or watch face
- **Background:** Dark (#000000 to #1A1A1A)
- **Surface:** Semi-transparent white (rgba(255, 255, 255, 0.1))
- **Text:** White (#FFFFFF) with varying opacity (0.9, 0.7, 0.5)
- **Accents:** Album art dominant colors extracted dynamically

### 5.3 Typography
- **Headlines:** SF Pro Display or Roboto (system font)
- **Body:** SF Pro Text or Roboto
- **Sizes:** Adapt to screen curvature, avoid edges
- **Weight:** Medium for labels, Regular for body text

### 5.4 Iconography
- **Style:** Outlined icons, 2px stroke
- **Size:** Minimum 24dp, touch targets 48dp
- **Color:** White with varying opacity

### 5.5 Animations
- **Transitions:** Smooth 300ms ease-in-out
- **Album Art:** Subtle parallax on scroll
- **Controls:** Scale on tap (0.95x)
- **Loading:** Circular progress with blur effect

---

## 6. Navigation & Input

### 6.1 Navigation Pattern
- **Edge Swipe:** Left edge swipe to go back
- **Rotary:** Bezel/crown for scrolling lists
- **Tap Navigation:** Large buttons at screen edges
- **Physical Buttons:** Home button for main menu

### 6.2 Input Methods

#### Touch
- **Tap:** Select item, toggle control
- **Long Press:** Show context menu, voice input
- **Swipe Right:** Add to favorites/queue
- **Swipe Left:** Remove from queue
- **Swipe Up:** Show additional options

#### Rotary (Bezel/Crown)
- **Primary:** Scroll through lists
- **Secondary:** Volume control on Now Playing
- **Tertiary:** Seek in track (with long press)

#### Voice
- **Search:** Long press search bar
- **Commands:** "Play [artist/song]", "Add to favorites"
- **Fallback:** If voice unavailable, show keyboard

### 6.3 Gesture Recognition
- **Double Pinch:** Play/pause (Samsung-specific gesture)
- **Wrist Tilt:** Show Now Playing (if supported)
- **Custom:** User-configurable shortcuts

---

## 7. Offline & Caching Strategy

### 7.1 Smart Caching Algorithm
```kotlin
class SmartCacheManager(
    private val storageManager: StorageManager,
    private val database: WearsicDatabase
) {
    // Track cache priority based on:
    // - Play count (higher = more cache priority)
    // - Last played (recent = higher priority)
    // - File size (smaller = easier to cache)
    // - Connection quality (poor = cache more)
    
    suspend fun shouldCache(track: Track): Boolean {
        val availableStorage = storageManager.getAvailableSpace()
        val trackSize = track.estimatedSize
        val priority = calculatePriority(track)
        
        return when {
            availableStorage < trackSize * 2 -> false // Keep buffer
            priority > CACHE_THRESHOLD -> true
            else -> false
        }
    }
    
    suspend fun evictIfNeeded() {
        // Remove least recently used tracks
        // Keep minimum cache for offline playback
        // Monitor storage pressure
    }
}
```

### 7.2 Offline Mode
- **Trigger:** Server unreachable for > 5 seconds
- **Behavior:**
  - Show cached content only
  - Indicate offline status with icon
  - Disable search (no server)
  - Allow playback of cached tracks
  - Queue limited to cached content

### 7.3 Sync Strategy
- **Background Sync:** When connected to charging + Wi-Fi
- **Foreground Sync:** Manual refresh pull-to-refresh
- **Conflict Resolution:** Server wins for favorites/playlists
- **Delta Sync:** Only sync changed items

### 7.4 Storage Management
- **Total Available:** ~28GB usable (after OS)
- **Cache Target:** 5-10GB maximum
- **Eviction Policy:** LRU with play count weighting
- **User Control:** Settings to clear cache, set limits

---

## 8. Performance & Optimization

### 8.1 Battery Optimization
- **Dark Theme:** OLED power savings (true black #000000)
- **Ambient Mode:** Minimal rendering, no animations
- **Background Tasks:** Schedule during charging
- **Network:** Batch requests, minimize wake locks
- **Media:** Hardware-decoded AAC preferred

### 8.2 Startup Performance
- **Cold Start:** < 2 seconds to interactive
- **Warm Start:** < 500ms
- **Splash Screen:** Minimal (app icon only)
- **Lazy Loading:** Defer non-critical initialization

### 8.3 Memory Management
- **Image Loading:** Coil with memory/disk cache
- **Media Buffering:** Adaptive buffer size based on connection
- **Database:** Room with efficient queries
- **ViewModel:** Proper scoping, no leaks

### 8.4 Network Optimization
- **Connection Pooling:** Reuse HTTP connections
- **Compression:** Gzip for API responses
- **Retry Logic:** Exponential backoff (1s, 2s, 4s, 8s max)
- **Timeout:** 10s for search, 30s for stream start
- **Cache Headers:** Respect HTTP caching

---

## 9. Error Handling & Resilience

### 9.1 Error Categories
1. **Network Errors:** Timeout, connection refused, DNS failure
2. **Server Errors:** 5xx responses, invalid JSON
3. **Client Errors:** 404 not found, 401 unauthorized
4. **Media Errors:** Decode failure, unsupported format
5. **Storage Errors:** Disk full, database corruption

### 9.2 Error UI Patterns
- **Toast:** Non-critical errors (network timeout)
- **Snackbar:** Recoverable errors (retry option)
- **Dialog:** Critical errors (server unavailable)
- **Inline:** Form validation errors

### 9.3 Recovery Strategies
- **Automatic Retry:** For transient network errors
- **Cached Fallback:** Show cached content when available
- **Graceful Degradation:** Disable features, show what works
- **User Action:** Clear error states, manual retry

### 9.4 Offline Indicators
- **Status Bar:** Icon showing connection state
- **Banner:** "Offline Mode" when no server
- **Disabled States:** Gray out unavailable features
- **Clear Messaging:** "Connect to server to search"

---

## 10. Accessibility

### 10.1 System Integration
- **Follow System Settings:** Respect user's accessibility preferences
- **High Contrast:** Support high contrast mode
- **Large Text:** Scale text with system settings
- **Screen Reader:** Content descriptions for all interactive elements

### 10.2 Touch Target Compliance
- **Minimum Size:** 48dp for all interactive elements
- **Spacing:** Minimum 8dp between targets
- **Visual Feedback:** Clear focus states

### 10.3 Color Contrast
- **Text:** Minimum 4.5:1 contrast ratio
- **Interactive Elements:** Minimum 3:1 contrast
- **Focus States:** High visibility indicators

---

## 11. Haptic Feedback

### 11.1 Minimal Configuration
- **Critical Actions Only:**
  - Error states
  - Long press confirmation
  - Double pinch gesture
- **No Haptics For:**
  - Regular taps
  - Scroll events
  - Navigation

### 11.2 Implementation
```kotlin
object HapticManager {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    
    fun errorFeedback() {
        // Short, sharp vibration for errors
        vibrator.vibrate(VibrationEffect.createOneShot(100, 255))
    }
    
    fun confirmationFeedback() {
        // Double tap pattern for confirmations
        val timings = longArrayOf(0, 50, 100, 50)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
```

---

## 12. Build Configuration

### 12.1 Gradle Setup
```kotlin
// build.gradle.kts (app module)
android {
    compileSdk = 36
    defaultConfig {
        targetSdk = 36
        minSdk = 30 // Wear OS 5 minimum
    }
}

dependencies {
    // Wear Compose M3 (pinned to 1.5.0)
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")
    
    // Horologist (0.8.x line)
    implementation("com.google.android.horologist:horologist-media-ui:0.8.0")
    implementation("com.google.android.horologist:horologist-media3-backend:0.8.0")
    
    // Networking
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    
    // Local Storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Media
    implementation("androidx.media3:media3-session:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

### 12.2 Manifest Requirements
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<uses-feature android:name="android.hardware.type.watch" />
<uses-feature android:name="android.hardware.rotary" android:required="false" />

<service
    android:name=".service.MediaPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

---

## 13. Testing Strategy

### 13.1 Unit Tests
- **Repository Layer:** Mock network, test caching logic
- **ViewModel Layer:** State management, error handling
- **Use Cases:** Business logic validation

### 13.2 Integration Tests
- **API Integration:** Test against mock server
- **Database:** Room queries and migrations
- **Media Playback:** Audio decode, seeking, buffering

### 13.3 UI Tests
- **Compose Previews:** All screens in preview mode
- **Accessibility:** TalkBack navigation
- **Input Methods:** Touch, rotary, voice

### 13.4 Device Testing
- **Physical Device:** Samsung Galaxy Watch 7 44mm
- **Emulator:** Wear OS round/square variants
- **Performance:** Battery drain, startup time, memory

---

## 14. Development Workflow

### 14.1 Screen-by-Screen Implementation
1. **Now Playing** → Build, verify, fix errors
2. **Search** → Build, verify, fix errors
3. **Favorites/Playlists** → Build, verify, fix errors
4. **Playlist/Album** → Build, verify, fix errors
5. **Queue** → Build, verify, fix errors
6. **Settings** → Build, verify, fix errors

### 14.2 Build Verification
- **After Every Change:** Run `./gradlew assembleDebug`
- **Fix Errors Before Proceeding:** Never mark task complete with build errors
- **Type Checking:** Ensure all types are correct
- **Lint:** Clean up warnings

### 14.3 Code Quality
- **Compose:** Use `ScreenScaffold`/`AppScaffold` for all screens
- **No M2.5:** Never import `androidx.compose.material` (Material 2.5)
- **Repository Pattern:** Screens never call network directly
- **State Management:** Unidirectional data flow

---

## 15. Future Considerations

### 15.1 Potential Enhancements
- **Watch Face Complications:** Show current track, quick controls
- **Phone Companion App:** Sync settings, playlists
- **Health Integration:** Workout detection, heart rate during exercise
- **Voice Commands:** "Hey Google, play [artist]"
- **Lyrics Support:** Synced lyrics display
- **Cross-device:** Cast to other speakers

### 15.2 Scalability
- **Multiple Servers:** Support for backup/fallback servers
- **User Accounts:** Multi-user support (future)
- **Social Features:** Shared playlists, activity feed
- **Advanced Analytics:** Listening habits, recommendations

---

## 16. Success Metrics

### 16.1 Performance Targets
- **Startup Time:** < 2 seconds (cold), < 500ms (warm)
- **Battery Impact:** < 5% per hour of active playback
- **Memory Usage:** < 150MB during normal operation
- **Cache Hit Rate:** > 80% for frequently played tracks

### 16.2 User Experience
- **Task Completion:** Search → Play in < 5 seconds
- **Error Recovery:** Clear path to retry on failures
- **Offline Usability:** Full playback of cached content
- **Glanceability:** Understand playback state in < 1 second

### 16.3 Technical Quality
- **Build Success:** 100% compile success rate
- **Test Coverage:** > 70% unit test coverage
- **Accessibility:** Full TalkBack support
- **Performance:** No ANR, no crashes in testing

---

## Appendix A: API Response Formats

### Search Results
```json
{
  "results": [
    {
      "videoId": "dQw4w9WgXcQ",
      "title": "Never Gonna Give You Up",
      "uploader": "Rick Astley",
      "durationMs": 212000,
      "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
    }
  ]
}
```

### Track Details
```json
{
  "videoId": "dQw4w9WgXcQ",
  "title": "Never Gonna Give You Up",
  "artist": "Rick Astley",
  "album": "Whenever You Need Somebody",
  "durationMs": 212000,
  "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
  "streamUrl": "/stream/dQw4w9WgXcQ"
}
```

### Health Check
```json
{
  "status": "ok",
  "version": "1.0.0",
  "uptime": 86400
}
```

---

## Appendix B: Screen Layouts

### Now Playing (Round Watch)
```
┌────────────────────────────┐
│      [Album Art Blur]      │
│  ┌──────────────────────┐  │
│  │   Track Title        │  │
│  │   Artist Name        │  │
│  └──────────────────────┘  │
│                            │
│  ◀ ▶  ⏵  ▶▶  🔀        │
│                            │
│  ──────────────────────●── │
│        (Progress)          │
│                            │
│  ♡  📋  ⚙️               │
└────────────────────────────┘
```

### Search Results (Round Watch)
```
┌────────────────────────────┐
│ 🔍 Search...               │
├────────────────────────────┤
│ 🎵 Track 1 - Artist 1     │
│ 🎵 Track 2 - Artist 2     │
│ 🎵 Track 3 - Artist 3     │
│ 🎵 Track 4 - Artist 4     │
│ 🎵 Track 5 - Artist 5     │
│ 🎵 Track 6 - Artist 6     │
└────────────────────────────┘
```

---

**Document Version:** 1.0  
**Last Updated:** August 6, 2026  
**Author:** Buffy (Freebuff AI Assistant)  
**Target Device:** Samsung Galaxy Watch 7 44mm  
**Platform:** Wear OS 6 (API 36)