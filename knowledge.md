# Project Context

## App name
Wearsic

## Backend
The app talks to a self-hosted Ktor (Kotlin) server, run at home by the developer — NOT a cloud service. It sources audio via NewPipe Extractor (YouTube search + streaming), so there's no fixed album library — screens are search-driven, with favorites/playlists saved server-side. Implications:
- The app must never hardcode a server URL. It's user-configured.
- Settings screen must include a text field for the server base URL (e.g. `https://your-tunnel.example.com`) plus a "Test Connection" button that calls a `/health` endpoint and shows success/failure. The server is reached over HTTPS via a Cloudflare Tunnel, so no cleartext-traffic manifest exceptions are needed.
- Save the URL with Preferences DataStore.
- Networking: Ktor Client (OkHttp engine) for symmetry with the server stack. Wrap calls in a Repository layer — screens should never call the network client directly.
- Endpoints to expect: `GET /health`, `GET /search?q=`, `GET /stream/{videoId}` (proxied audio, supports Range for seeking), `GET|POST|DELETE /favorites`, `GET|POST /playlists` + track sub-routes. Search/stream calls can take a couple seconds (live scraping server-side) — show loading states, don't assume instant responses.
- The backend is a separate project/repo from this app and has its own knowledge.md — don't assume backend code lives in this repo.

## What this is
A music streaming app for Wear OS (Android smartwatch), starting from a blank project. Priority is a polished, native-feeling watch UI — not a shrunk-down phone app.

## Stack (non-negotiable) — target Wear OS 6
- Target Wear OS 6 (based on Android 16, API level 36; 6.1 is API 36.1). Set `compileSdk`/`targetSdk` to 36 in `build.gradle`.
- Design system: Wear OS 6 shipped **Material 3 Expressive**. Use the `androidx.wear.compose:compose-material3` artifact, pinned to **1.5.0** (the current stable release — this is safe to lock, not a moving alpha). Do NOT use the older `androidx.wear.compose:compose-material` (Material 2.5) package, and never mix M2.5 and M3 in the same app.
- Also pull in matching `androidx.wear.compose:compose-foundation` and `androidx.wear.compose:compose-navigation` at **1.5.0** — they need to stay in lockstep with compose-material3.
- Take advantage of M3 Expressive-specific components where they fit: `AppScaffold`/`ScreenScaffold` for layout, `EdgeButton` for bottom-of-screen actions, shape-morphing `IconButton`/`TextButton`, and dynamic color theming (auto-generates a scheme from the watch face).
- Horologist library (`com.google.android.horologist`) for media controls, volume, and now-playing UI — use the latest release (currently the 0.8.x line, which has Material 3 updates for auth/media) rather than the legacy Material 2 audio-ui components. Use its `media-ui`/`media3-backend` modules instead of hand-rolling playback UI.
- Build with Gradle; after every change, run the build and fix any resulting errors before considering the task done. Never report a task complete without a successful build.

## Screen shape
Devices are both round and square. Every screen must:
- Use `ScreenScaffold` / `AppScaffold` from Wear Compose so content adapts to both shapes automatically.
- Avoid fixed-corner content (text or controls anchored to corners) since round screens clip corners.
- Use curved text (`CurvedLayout`) for labels that sit near the top edge on round screens, where appropriate (e.g. screen titles).

## Ambient mode
This is a watch, not a phone — screens must support ambient (always-on) mode:
- Use `AmbientAware` / the Horologist ambient utilities.
- In ambient mode: switch to a low-power, mostly-monochrome rendering, pause animations, stop any looping visual effects (e.g. animated album art).

## Design direction
- Dark theme by default (watches are worn in varied light; dark reduces battery draw on OLED).
- Album art is the dominant visual element on the Now Playing screen — let it fill/blur into the background rather than sitting in a small square.
- Large touch targets (min 48dp) — fingers on a small screen, not a mouse cursor.
- Minimal chrome: prioritize glanceability. A user should understand playback state in under a second.
- Rotary input (bezel/crown) should scroll lists and can be wired to volume on the Now Playing screen.

> Customize this section with your own palette/mood if you have one in mind (e.g. specific accent color, a reference app's aesthetic) — Freebuff will follow whatever's written here over its own defaults.

## Screens (build and verify one at a time — do not start the next until the current one builds and matches spec)
1. Now Playing — album art background, track/artist, play/pause/skip, progress, rotary volume
2. Search — text search against `/search`, with live suggestions from `/suggestions` as the user types (debounce the calls); results list scrollable via rotary input; swipe-to-reveal (Wear Compose M3's `SwipeToReveal`) a "save to favorites" action per result
3. Favorites/Playlists — saved tracks and playlists from the server, same list pattern as Search
4. Playlist/Album — view an external YouTube playlist's tracks (from `/playlist?url=`) or a channel's uploads/"discography" (from `/channel?url=`, paginated) — same list UI as Search/Favorites, reused rather than rebuilt
5. Queue — up-next list, reorderable. When the queue empties, auto-fill from `/related/{videoId}` for the currently/last-played track rather than stopping — this related-stream autoplay is core behavior, not optional polish
6. (Add more only once 1–5 are solid)

No trending/popular/discovery screen — explicitly out of scope.

## Workflow rules for the agent
- Work on exactly one screen per session/prompt. Do not touch files for other screens unless asked.
- After generating UI, run the build and fix compile errors before returning.
- If asked to make something "match a reference," treat the described layout/spacing as literal spec, not inspiration — don't substitute your own layout ideas without flagging the deviation.
- Do not introduce regular `androidx.compose.material3` imports anywhere in this project.
