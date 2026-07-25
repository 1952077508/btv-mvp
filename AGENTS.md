# BTV MVP — Agent Instructions

Two-person remote sync video player. Android + FastAPI/WebSocket + Redis.

## Services (docker compose up -d)

| Service | Port | Purpose |
|---------|------|---------|
| redis | 6379 | Room state + member sets |
| backend | 8000 | FastAPI HTTP + WebSocket |
| apk-server | 8080 | Nginx, serves `apk-server/apk/` with autoindex |

## Key command

```bash
docker compose up -d          # start all three services
./build_apk.sh                # assembleDebug + copy APK to apk-server/apk/
```

No test suite exists yet. Backend syntax check: `python3 -c "import ast; ..."` against each `.py`.

## Architecture

**Sync model**: Host position is the single source of truth. Only the host's heartbeat writes `current_pos` to Redis. Guests are corrected toward it. There is no peer-to-peer sync.

**WebSocket URL format**: `ws://host:8000/ws/{ROOM_ID}?userId={USER_ID}`. The `?userId=` query param is **required** — the server extracts it from the raw URL query string, not from the message body. Omitting it causes an immediate close.

**Cooldown**: 2-second grace period after local user actions (`play/pause/seek`). Incoming `correct` and `seek` messages are ignored during this window to prevent rubber-banding.

**Room lifecycle**: TTL 1h, renewed on heartbeat. Host silence > 30s → room destroyed + `room_closed` broadcast.

## Backend files

- `main.py` — FastAPI app, lifespan, HTTP endpoints, WS endpoint with handshake
- `room_service.py` — all Redis operations (room CRUD, member add/remove, heartbeat update)
- `ws_manager.py` — in-memory connection tracking, message broadcast, heartbeat handler
- `sync_engine.py` — background asyncio loop that pushes `correct` to guests every 5s
- `config.py` — all tunables (thresholds, TTLs, intervals)
- `redis_client.py` — single global `redis.asyncio` pool

Redis keys: `room:{roomId}` (Hash), `room:{roomId}:members` (Set).

## Android

- Kotlin + Jetpack Compose + Material 3, minSdk 24, targetSdk 35
- ExoPlayer via `androidx.media3:media3-exoplayer:1.5.0`
- WebSocket via `com.squareup.okhttp3:okhttp:4.12.0`
- Two screens: HomeScreen → PlayerScreen (Navigation Compose)
- `AndroidManifest.xml` sets `usesCleartextTraffic=true` (required for http dev servers)
- Backend URL defaults to `http://10.0.2.2:8000` (emulator→host). Users can change it via the server-address dialog on the home screen.
- `ExoPlayerManager.getPlayer()` exposes the raw `ExoPlayer` instance for `PlayerView` composable binding.

## Gotchas

- Room codes are **uppercase**. Server uppercases input on join/check. Client does the same.
- The `change_video` message type requires host role; server silently drops it from guests.
- `ws_manager.py` accesses `manager._connections` directly in `sync_engine.py` — this is intentional for the background loop reading the connection map.
- No authentication or rate limiting. MVP scope.
