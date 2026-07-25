import asyncio
from room_service import get_room, get_members, check_host_timeout
from ws_manager import manager
from config import DEVIATION_THRESHOLD, HEARTBEAT_INTERVAL


class SyncEngine:
    def __init__(self):
        self._running = False
        self._task = None

    async def start(self):
        if self._running:
            return
        self._running = True
        self._task = asyncio.create_task(self._loop())

    async def stop(self):
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

    async def _loop(self):
        while self._running:
            await asyncio.sleep(HEARTBEAT_INTERVAL)
            try:
                await self._tick()
            except Exception:
                pass

    async def _tick(self):
        rooms = manager._connections.copy()
        for room_id in rooms:
            room = await get_room(room_id)
            if not room:
                continue

            host_timeout = await check_host_timeout(room_id)
            if host_timeout:
                await manager.broadcast(room_id, {"type": "room_closed", "payload": {}})
                for uid in list(rooms.get(room_id, {}).keys()):
                    manager.remove(room_id, uid)
                continue

            server_pos = float(room.get("current_pos", "0"))
            is_playing = room.get("is_playing", "0") == "1"
            host_id = room.get("host_id", "")

            if not is_playing:
                continue

            members = await get_members(room_id)
            for member_id in members:
                if member_id == host_id:
                    continue
                await manager.send_to(
                    room_id,
                    member_id,
                    {
                        "type": "correct",
                        "payload": {
                            "targetPosition": server_pos,
                            "isPlaying": is_playing,
                        },
                    },
                )


sync_engine = SyncEngine()
