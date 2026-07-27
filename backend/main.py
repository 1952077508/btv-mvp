import uuid
import json
import re
import logging
from urllib.parse import urlparse
from contextlib import asynccontextmanager

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("btv")

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from redis_client import get_redis, close_redis
from room_service import (
    create_room,
    join_room,
    check_room,
    get_room,
    remove_member,
    is_host,
    update_video_url,
)
from ws_manager import manager
from sync_engine import sync_engine


@asynccontextmanager
async def lifespan(app: FastAPI):
    await get_redis()
    await sync_engine.start()
    yield
    await sync_engine.stop()
    await close_redis()


app = FastAPI(title="BTV Sync Player", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class CreateRoomResponse(BaseModel):
    roomId: str
    hostId: str
    wsUrl: str


class JoinRoomRequest(BaseModel):
    roomId: str


class JoinRoomResponse(BaseModel):
    roomId: str
    userId: str
    wsUrl: str
    videoUrl: str
    currentPos: float
    isPlaying: bool


class CheckRoomResponse(BaseModel):
    exists: bool
    memberCount: int


class ErrorResponse(BaseModel):
    error: str


VIDEO_URL_REGEX = re.compile(r"^https?://[^\s/$.?#].[^\s]*$", re.IGNORECASE)


def validate_video_url(url: str) -> bool:
    if not url:
        return False
    try:
        parsed = urlparse(url)
        if parsed.scheme not in ("http", "https"):
            return False
        if not parsed.netloc:
            return False
        return True
    except Exception:
        return False


@app.post("/api/room/create", response_model=CreateRoomResponse)
async def api_create_room():
    host_id = uuid.uuid4().hex[:12]
    logger.info(f"create_room host_id={host_id}")
    try:
        room = await create_room(host_id)
        logger.info(f"room {room['roomId']} created")
    except Exception as e:
        logger.error(f"create_room failed: {e}")
        raise HTTPException(status_code=500, detail=f"Redis error: {e}")
    return CreateRoomResponse(
        roomId=room["roomId"],
        hostId=host_id,
        wsUrl=f"ws://localhost:8000/ws/{room['roomId']}",
    )


@app.post("/api/room/check/{room_id}", response_model=CheckRoomResponse)
async def api_check_room(room_id: str):
    room_id = room_id.upper()
    try:
        room = await check_room(room_id)
    except Exception as e:
        logger.error(f"check_room {room_id} failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    if not room:
        return CheckRoomResponse(exists=False, memberCount=0)
    return CheckRoomResponse(**room)


@app.post("/api/room/join", response_model=JoinRoomResponse)
async def api_join_room(body: JoinRoomRequest):
    room_id = body.roomId.upper()
    user_id = uuid.uuid4().hex[:12]
    logger.info(f"join_room room={room_id} user={user_id}")

    try:
        room = await join_room(room_id, user_id)
    except Exception as e:
        logger.error(f"join_room {room_id} failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    if not room:
        raise HTTPException(status_code=400, detail="房间不存在或已满")

    return JoinRoomResponse(
        roomId=room["roomId"],
        userId=user_id,
        wsUrl=f"ws://localhost:8000/ws/{room_id}",
        videoUrl=room.get("videoUrl", ""),
        currentPos=float(room.get("currentPos", "0")),
        isPlaying=room.get("isPlaying", False),
    )


@app.websocket("/ws/{room_id}")
async def ws_endpoint(ws: WebSocket, room_id: str):
    room_id = room_id.upper()

    query = str(ws.url).split("?")[-1] if "?" in str(ws.url) else ""
    user_id = ""
    for param in query.split("&"):
        if param.startswith("userId="):
            user_id = param.split("=", 1)[1]
            break

    if not user_id:
        logger.warning(f"WS {room_id}: missing userId")
        await ws.accept()
        await ws.send_text(json.dumps({"type": "error", "payload": {"message": "缺少userId参数"}}))
        await ws.close()
        return

    room = await get_room(room_id)
    if not room:
        logger.warning(f"WS {room_id}: room not found for user={user_id}")
        await ws.accept()
        await ws.send_text(json.dumps({"type": "error", "payload": {"message": "房间不存在"}}))
        await ws.close()
        return

    host_id = room.get("host_id", "")
    role = "host" if user_id == host_id else "guest"
    logger.info(f"WS connect room={room_id} user={user_id} role={role}")

    await ws.accept()
    manager.add(room_id, user_id, ws)

    await ws.send_text(
        json.dumps(
            {
                "type": "welcome",
                "payload": {"userId": user_id, "role": role},
            }
        )
    )

    await ws.send_text(
        json.dumps(
            {
                "type": "room_state",
                "payload": {
                    "videoUrl": room.get("video_url", ""),
                    "position": float(room.get("current_pos", "0")),
                    "isPlaying": room.get("is_playing", "0") == "1",
                },
            }
        )
    )

    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue

            msg_type = data.get("type", "")

            if msg_type == "change_video":
                video_url = data.get("payload", {}).get("videoUrl", "")
                if not validate_video_url(video_url):
                    await ws.send_text(
                        json.dumps({"type": "error", "payload": {"message": "无效的视频URL"}})
                    )
                    continue
                if not await is_host(room_id, user_id):
                    await ws.send_text(
                        json.dumps(
                            {"type": "error", "payload": {"message": "仅房主可切换视频"}}
                        )
                    )
                    continue
                await update_video_url(room_id, video_url)

            await manager.handle_message(room_id, user_id, data)

    except WebSocketDisconnect:
        pass
    finally:
        room_closed = await remove_member(room_id, user_id)
        manager.remove(room_id, user_id)
        if room_closed:
            await manager.broadcast(room_id, {"type": "room_closed", "payload": {}})
