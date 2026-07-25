import json
from fastapi import WebSocket
from room_service import get_room, is_host, update_host_heartbeat, renew_room_ttl


class ConnectionManager:
    def __init__(self):
        self._connections: dict[str, dict[str, WebSocket]] = {}

    def add(self, room_id: str, user_id: str, ws: WebSocket):
        if room_id not in self._connections:
            self._connections[room_id] = {}
        self._connections[room_id][user_id] = ws

    def remove(self, room_id: str, user_id: str):
        if room_id in self._connections:
            self._connections[room_id].pop(user_id, None)
            if not self._connections[room_id]:
                del self._connections[room_id]

    async def broadcast(self, room_id: str, message: dict, exclude: str | None = None):
        if room_id not in self._connections:
            return
        payload = json.dumps(message)
        for uid, ws in list(self._connections[room_id].items()):
            if uid == exclude:
                continue
            try:
                await ws.send_text(payload)
            except Exception:
                pass

    async def send_to(self, room_id: str, user_id: str, message: dict):
        if room_id in self._connections and user_id in self._connections[room_id]:
            try:
                await self._connections[room_id][user_id].send_text(json.dumps(message))
            except Exception:
                pass

    async def handle_message(self, room_id: str, user_id: str, data: dict):
        msg_type = data.get("type")

        if msg_type == "heartbeat":
            position = float(data.get("payload", {}).get("position", 0))
            is_playing = data.get("payload", {}).get("isPlaying", False)

            if await is_host(room_id, user_id):
                await update_host_heartbeat(room_id, position, is_playing)

            await renew_room_ttl(room_id)

        elif msg_type == "play":
            payload = data.get("payload", {})
            await self.broadcast(
                room_id,
                {"type": "play", "payload": payload},
                exclude=user_id,
            )

        elif msg_type == "pause":
            payload = data.get("payload", {})
            await self.broadcast(
                room_id,
                {"type": "pause", "payload": payload},
                exclude=user_id,
            )

        elif msg_type == "seek":
            payload = data.get("payload", {})
            await self.broadcast(
                room_id,
                {"type": "seek", "payload": payload},
                exclude=user_id,
            )

        elif msg_type == "change_video":
            if not await is_host(room_id, user_id):
                return
            video_url = data.get("payload", {}).get("videoUrl", "")
            await self.broadcast(
                room_id,
                {"type": "video_changed", "payload": {"videoUrl": video_url}},
            )

        elif msg_type == "sync_request":
            room = await get_room(room_id)
            if room:
                server_pos = float(room.get("current_pos", "0"))
                client_pos = float(data.get("payload", {}).get("position", 0))
                offset = server_pos - client_pos
                await self.send_to(
                    room_id,
                    user_id,
                    {
                        "type": "sync_response",
                        "payload": {
                            "serverPosition": server_pos,
                            "offset": offset,
                        },
                    },
                )


manager = ConnectionManager()
