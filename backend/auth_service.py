import uuid
import hashlib
import sqlite3
import asyncio
import os
from redis_client import get_redis

DB_PATH = os.path.join(os.path.dirname(__file__), "users.db")

TOKEN_TTL = 86400  # 24h
ADMIN_PASSWORD_HASH = hashlib.sha256("admin123".encode()).hexdigest()  # default


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    conn = get_db()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            is_admin INTEGER DEFAULT 0,
            created_at INTEGER NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS room_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            room_id TEXT NOT NULL,
            role TEXT NOT NULL,
            joined_at INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        )
    """)
    try:
        conn.execute(
            "INSERT INTO users (id, username, password_hash, is_admin, created_at) VALUES (?, ?, ?, 1, ?)",
            (uuid.uuid4().hex[:12], "admin", ADMIN_PASSWORD_HASH, int(asyncio.get_event_loop().time())),
        )
    except sqlite3.IntegrityError:
        pass
    conn.commit()
    conn.close()


async def register_user(username: str, password: str) -> dict | None:
    user_id = uuid.uuid4().hex[:12]
    password_hash = hashlib.sha256(password.encode()).hexdigest()
    now = int(asyncio.get_event_loop().time())

    conn = get_db()
    try:
        conn.execute(
            "INSERT INTO users (id, username, password_hash, is_admin, created_at) VALUES (?, ?, ?, 0, ?)",
            (user_id, username, password_hash, now),
        )
        conn.commit()
    except sqlite3.IntegrityError:
        return None
    finally:
        conn.close()

    token = uuid.uuid4().hex
    redis = await get_redis()
    await redis.setex(f"token:{token}", TOKEN_TTL, user_id)

    return {"token": token, "userId": user_id, "username": username, "isAdmin": False}


async def login_user(username: str, password: str) -> dict | None:
    password_hash = hashlib.sha256(password.encode()).hexdigest()
    conn = get_db()
    row = conn.execute(
        "SELECT id, is_admin FROM users WHERE username = ? AND password_hash = ?",
        (username, password_hash),
    ).fetchone()
    conn.close()

    if not row:
        return None

    user_id = row["id"]
    is_admin = bool(row["is_admin"])

    token = uuid.uuid4().hex
    redis = await get_redis()
    await redis.setex(f"token:{token}", TOKEN_TTL, user_id)

    return {"token": token, "userId": user_id, "username": username, "isAdmin": is_admin}


async def get_user_id_from_token(token: str) -> str | None:
    redis = await get_redis()
    return await redis.get(f"token:{token}")


async def get_user_info(user_id: str) -> dict | None:
    conn = get_db()
    row = conn.execute(
        "SELECT id, username, is_admin FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    conn.close()
    if not row:
        return None
    return {
        "userId": row["id"],
        "username": row["username"],
        "isAdmin": bool(row["is_admin"]),
    }


async def is_admin(user_id: str) -> bool:
    info = await get_user_info(user_id)
    return info["isAdmin"] if info else False


async def get_admin_stats() -> dict:
    redis = await get_redis()
    conn = get_db()

    user_count = conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]
    conn.close()

    room_keys = await redis.keys("room:*")
    active_rooms = [k for k in room_keys if ":members" not in k]

    rooms = []
    for key in active_rooms:
        room_data = await redis.hgetall(key)
        room_id = key.split(":")[1]
        members = await redis.smembers(f"room:{room_id}:members")
        rooms.append({
            "roomId": room_id,
            "hostId": room_data.get("host_id", ""),
            "videoUrl": room_data.get("video_url", "")[:80],
            "isPlaying": room_data.get("is_playing", "0") == "1",
            "memberCount": len(members),
        })

    return {
        "totalUsers": user_count,
        "activeRooms": len(rooms),
        "rooms": rooms,
    }


async def record_room_join(user_id: str, room_id: str, role: str):
    conn = get_db()
    now = int(asyncio.get_event_loop().time())
    conn.execute(
        "INSERT INTO room_history (user_id, room_id, role, joined_at) VALUES (?, ?, ?, ?)",
        (user_id, room_id, role, now),
    )
    conn.commit()
    conn.close()


async def get_room_history(user_id: str) -> list:
    conn = get_db()
    rows = conn.execute(
        "SELECT room_id, role, joined_at FROM room_history WHERE user_id = ? ORDER BY joined_at DESC LIMIT 20",
        (user_id,),
    ).fetchall()
    conn.close()
    return [
        {"roomId": row["room_id"], "role": row["role"], "joinedAt": row["joined_at"]}
        for row in rows
    ]
