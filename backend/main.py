import uuid
import json
import re
import logging
from urllib.parse import urlparse
from contextlib import asynccontextmanager

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("btv")

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional

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
from auth_service import (
    init_db,
    register_user,
    login_user,
    get_user_id_from_token,
    get_user_info,
    is_admin,
    get_admin_stats,
    record_room_join,
    get_room_history,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await get_redis()
    init_db()
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


# --- helpers ---

async def require_auth(authorization: Optional[str] = Header(None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未登录")
    token = authorization[7:]
    user_id = await get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="Token 已过期")
    return user_id


async def require_admin(user_id: str):
    if not await is_admin(user_id):
        raise HTTPException(status_code=403, detail="需要管理员权限")


# --- models ---

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

class RegisterRequest(BaseModel):
    username: str
    password: str

class LoginRequest(BaseModel):
    username: str
    password: str

class AuthResponse(BaseModel):
    token: str
    userId: str
    username: str
    isAdmin: bool = False

class HistoryResponse(BaseModel):
    roomId: str
    role: str
    joinedAt: int


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


# --- auth ---

@app.post("/api/auth/register", response_model=AuthResponse)
async def api_register(body: RegisterRequest):
    if len(body.username) < 2 or len(body.password) < 3:
        raise HTTPException(status_code=400, detail="用户名至少2位，密码至少3位")
    result = await register_user(body.username, body.password)
    if not result:
        raise HTTPException(status_code=409, detail="用户名已存在")
    logger.info(f"register user={body.username} id={result['userId']}")
    return AuthResponse(**result)


@app.post("/api/auth/login", response_model=AuthResponse)
async def api_login(body: LoginRequest):
    result = await login_user(body.username, body.password)
    if not result:
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    logger.info(f"login user={body.username} id={result['userId']}")
    return AuthResponse(**result)


@app.get("/api/auth/me")
async def api_me(user_id: str = Header(None, alias="X-User-Id")):
    info = await get_user_info(user_id)
    if not info:
        raise HTTPException(status_code=404, detail="用户不存在")
    return info


# --- rooms ---

@app.post("/api/room/create", response_model=CreateRoomResponse)
async def api_create_room(user_id: str = Header(None, alias="X-User-Id")):
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录")
    logger.info(f"create_room by user={user_id}")
    try:
        room = await create_room(user_id)
        logger.info(f"room {room['roomId']} created")
        await record_room_join(user_id, room["roomId"], "host")
    except Exception as e:
        logger.error(f"create_room failed: {e}")
        raise HTTPException(status_code=500, detail=f"Redis error: {e}")
    return CreateRoomResponse(
        roomId=room["roomId"],
        hostId=user_id,
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
async def api_join_room(body: JoinRoomRequest, user_id: str = Header(None, alias="X-User-Id")):
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录")
    room_id = body.roomId.upper()
    logger.info(f"join_room room={room_id} user={user_id}")

    try:
        room = await join_room(room_id, user_id)
    except Exception as e:
        logger.error(f"join_room {room_id} failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    if not room:
        raise HTTPException(status_code=400, detail="房间不存在或已满")

    await record_room_join(user_id, room["roomId"], "guest")
    return JoinRoomResponse(
        roomId=room["roomId"],
        userId=user_id,
        wsUrl=f"ws://localhost:8000/ws/{room_id}",
        videoUrl=room.get("videoUrl", ""),
        currentPos=float(room.get("currentPos", "0")),
        isPlaying=room.get("isPlaying", False),
    )


@app.get("/api/user/history")
async def api_history(user_id: str = Header(None, alias="X-User-Id")):
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录")
    return await get_room_history(user_id)


# --- admin ---

@app.get("/api/health")
async def api_health():
    return {"status": "ok"}


@app.get("/api/admin/stats")
async def api_admin_stats(user_id: str = Header(None, alias="X-User-Id")):
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录")
    await require_admin(user_id)
    return await get_admin_stats()


# --- ws ---

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
        json.dumps({"type": "welcome", "payload": {"userId": user_id, "role": role}})
    )
    await ws.send_text(
        json.dumps({
            "type": "room_state",
            "payload": {
                "videoUrl": room.get("video_url", ""),
                "position": float(room.get("current_pos", "0")),
                "isPlaying": room.get("is_playing", "0") == "1",
            },
        })
    )

    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue

            if data.get("type") == "change_video":
                video_url = data.get("payload", {}).get("videoUrl", "")
                if not validate_video_url(video_url):
                    await ws.send_text(
                        json.dumps({"type": "error", "payload": {"message": "无效的视频URL"}})
                    )
                    continue
                if not await is_host(room_id, user_id):
                    await ws.send_text(
                        json.dumps({"type": "error", "payload": {"message": "仅房主可切换视频"}})
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
