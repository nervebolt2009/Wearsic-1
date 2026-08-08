# Wearsic Server

Standalone Ktor + NewPipe Extractor backend for the Wearsic Wear OS app. This project is intentionally separate from the Android app and can run on an old Android phone through Termux.

## Requirements

- Java 17
- Termux packages: `pkg install openjdk-17 git`
- A Cloudflare Tunnel pointed at the server port

## Build and run

From the repository root:

```bash
./gradlew -p server build
./gradlew -p server installDist
cd server
PORT=8080 WEARSIC_DB_PATH="$PWD/wearsic.db" ./run-termux.sh
```

`run-termux.sh` uses a small heap and Serial GC by default. Override `JAVA_OPTS` when the phone has more memory.

The `installDist` task creates `build/install/wearsic-server/` with all runtime dependencies, and `run-termux.sh` launches that distribution. The server itself uses CIO instead of Netty to keep the Termux footprint small.

## Environment

- `PORT` — defaults to `8080`
- `WEARSIC_DB_PATH` — defaults to `wearsic.db`
- `WEARSIC_API_KEY` — optional. If set, every `/api/*` request must include `X-Wearsic-Key`; `/health` remains public.

## API

Public:

- `GET /health`

Authenticated when `WEARSIC_API_KEY` is set:

- `GET /api/search?q=` — maximum 10 results
- `GET /api/suggestions?q=` — maximum 5 suggestions
- `GET /api/related/{videoId}` — maximum 10 results
- `GET /api/stream/{videoId}` — proxied audio with Range forwarding; prefers M4A/AAC near 128 kbps
- `GET|POST|DELETE /api/favorites[/{videoId}]`
- `GET|POST /api/playlists`
- `GET /api/playlists/{id}`
- `POST|DELETE /api/playlists/{id}/tracks[/{videoId}]`
- `GET /api/playlist?url=` — maximum 10 tracks
- `GET /api/channel?url=` — maximum 10 tracks from the first channel tab

The server caches search results and resolved stream targets in small bounded in-memory caches. SQLite uses WAL mode and `synchronous=NORMAL` for good performance on a phone.

## Cloudflare Tunnel

Keep the tunnel URL out of source code. In the watch app Settings screen, enter the public HTTPS URL, for example:

```text
https://your-tunnel.trycloudflare.com
```

If you set `WEARSIC_API_KEY`, the Android client must also be extended to send `X-Wearsic-Key` on all `/api` calls.
