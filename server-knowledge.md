# Project Context

## What this is
The Ktor backend for Wearsic, a personal Wear OS music app. Hosted at home, exposed to the internet via a Cloudflare Tunnel. This is a separate repo from the watch app (`wearsic-app`), which has its own `knowledge.md` — don't assume any watch/Compose code lives here.

## Stack
- Kotlin + Ktor.
- NewPipe Extractor (`org.schabi.newpipe:extractor`, available via JitPack) for searching YouTube and resolving audio-only stream URLs. This is a scraping library, not an official API — expect it to occasionally need updates when YouTube changes its site.
- SQLite via the Exposed ORM for persistence (favorites and playlists). Single user, so no need for anything heavier.
- Runs plain HTTP locally — Cloudflare Tunnel handles HTTPS termination, so the server itself doesn't need its own TLS cert.

## Endpoints
- `GET /health` — used by the watch app's "Test Connection" button in Settings.
- `GET /search?q=` — search via NewPipe Extractor. Return a list of `{ videoId, title, uploader, durationMs, thumbnailUrl }`.
- `GET /suggestions?q=` — search-as-you-type autocomplete via NewPipe's suggestion extractor. Returns a list of suggested query strings. This should be fast — don't route it through the slower full search path.
- `GET /stream/{videoId}` — resolve audio-only streams via NewPipe Extractor and pick deliberately, not just "highest bitrate": prefer an M4A/AAC stream around 128kbps if available (hardware-decoded on Wear OS chips, better for battery) and fall back to Opus/WebM only if no AAC option exists. Proxy the audio bytes through this server — do not redirect the client to the raw googlevideo.com URL, since it requires specific headers and expires in a few hours. Must support HTTP `Range` headers so the watch can seek/scrub.
- `GET /related/{videoId}` — related/up-next tracks for a given video via NewPipe Extractor, same shape as search results. This is the backbone of autoplay/"radio" — when the app's queue runs out, it pulls from here. Treat this as a core endpoint, not an add-on.
- `GET /playlist?url=` — extract an external YouTube playlist ("album") given its URL: playlist metadata (name, thumbnail, uploader) plus its track list.
- `GET /channel?url=` — extract an artist/channel's uploads ("discography"): channel metadata plus a paginated list of their videos. NewPipe Extractor paginates channel uploads via continuation tokens — the endpoint needs a `?page=` or cursor param to support loading more.
- `GET /favorites`, `POST /favorites`, `DELETE /favorites/{videoId}` — individually saved tracks.
- `GET /playlists`, `POST /playlists`, `GET /playlists/{id}`, `POST /playlists/{id}/tracks`, `DELETE /playlists/{id}/tracks/{videoId}` — user-created playlists (distinct from imported YouTube playlists above — importing one can populate a new local playlist via this same mechanism).
- Explicitly OUT of scope: no trending/kiosk/popular endpoint. Don't add one even as a convenience.
- Thumbnails: pass through the YouTube thumbnail URL as-is in API responses (`i.ytimg.com` links work without auth) — no need to proxy or cache these like audio streams.

## Notes
- Single user — no accounts, no multi-tenancy.
- Worth adding a simple shared-secret header (a static API key checked on every route except `/health`) since the Cloudflare Tunnel exposes this to the public internet, not just the home LAN.
- NewPipe Extractor calls are live scraping, not a fast lookup — a search or stream-resolve can take a couple of seconds. Don't assume instant responses; the app side should show a loading state.

## Workflow rules for the agent
- Work on one milestone at a time (see the project's milestone plan — e.g. search + stream proxy first, favorites/playlists persistence after).
- After generating or changing code, run the server and verify behavior with `curl` before considering the task done.
