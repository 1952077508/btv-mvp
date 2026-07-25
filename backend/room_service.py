import random
import string
import asyncio
from redis_client import get_redis
from config import ROOM_TTL_SECONDS, HOST_TIMEOUT_SECONDS


def generate_room_id(length: int = 6) -> str:
    return "".join(random.choices(string.ascii_uppercase + string.digits, k=length))


async def create_room(host_id: str) -> dict:
    redis = await get_redis()
    room_id = generate_room_id()

    while await redis.exists(f"room:{room_id}"):
        room_id = generate_room_id()

    pipe = redis.pipeline()
    pipe.hset(
        f"room:{room_id}",
        mapping={
            "host_id": host_id,
            "video_url": "",
            "current_pos": "0",
            "is_playing": "0",
            "host_last_heartbeat": str(int(asyncio.get_event_loop().time())),
            "member_count": "1",
        },
    )
    pipe.sadd(f"room:{room_id}:members", host_id)
    pipe.expire(f"room:{room_id}", ROOM_TTL_SECONDS)
    pipe.expire(f"room:{room_id}:members", ROOM_TTL_SECONDS)
    await pipe.execute()

    return {
        "roomId": room_id,
        "hostId": host_id,
        "videoUrl": "",
        "memberCount": 1,
    }


async def join_room(room_id: str, user_id: str) -> dict | None:
    redis = await get_redis()
    room_key = f"room:{room_id}"

    if not await redis.exists(room_key):
        return None

    room = await redis.hgetall(room_key)
    member_count = int(room.get("member_count", "0"))

    if member_count >= 2:
        return None

    host_id = room["host_id"]
    if user_id == host_id:
        return None

    pipe = redis.pipeline()
    pipe.sadd(f"room:{room_id}:members", user_id)
    pipe.hincrby(room_key, "member_count", 1)
    pipe.expire(room_key, ROOM_TTL_SECONDS)
    pipe.expire(f"room:{room_id}:members", ROOM_TTL_SECONDS)
    await pipe.execute()

    room = await redis.hgetall(room_key)
    return {
        "roomId": room_id,
        "hostId": room["host_id"],
        "videoUrl": room.get("video_url", ""),
        "currentPos": float(room.get("current_pos", "0")),
        "isPlaying": room.get("is_playing", "0") == "1",
        "memberCount": int(room.get("member_count", "0")),
    }


async def check_room(room_id: str) -> dict | None:
    redis = await get_redis()
    room_key = f"room:{room_id}"

    if not await redis.exists(room_key):
        return None

    room = await redis.hgetall(room_key)
    return {
        "exists": True,
        "memberCount": int(room.get("member_count", "0")),
    }


async def get_room(room_id: str) -> dict | None:
    redis = await get_redis()
    room_key = f"room:{room_id}"

    if not await redis.exists(room_key):
        return None

    return await redis.hgetall(room_key)


async def update_host_heartbeat(room_id: str, position: float, is_playing: bool):
    redis = await get_redis()
    room_key = f"room:{room_id}"

    pipe = redis.pipeline()
    pipe.hset(
        room_key,
        mapping={
            "current_pos": str(position),
            "is_playing": "1" if is_playing else "0",
            "host_last_heartbeat": str(int(asyncio.get_event_loop().time())),
        },
    )
    pipe.expire(room_key, ROOM_TTL_SECONDS)
    pipe.expire(f"room:{room_id}:members", ROOM_TTL_SECONDS)
    await pipe.execute()


async def update_video_url(room_id: str, video_url: str):
    redis = await get_redis()
    room_key = f"room:{room_id}"
    await redis.hset(room_key, mapping={"video_url": video_url, "current_pos": "0"})
    await redis.expire(room_key, ROOM_TTL_SECONDS)


async def remove_member(room_id: str, user_id: str) -> bool:
    redis = await get_redis()
    room_key = f"room:{room_id}"
    members_key = f"room:{room_id}:members"

    if not await redis.exists(room_key):
        return False

    room = await redis.hgetall(room_key)
    host_id = room.get("host_id")

    pipe = redis.pipeline()
    pipe.srem(members_key, user_id)
    pipe.hincrby(room_key, "member_count", -1)

    if user_id == host_id:
        pipe.delete(room_key)
        pipe.delete(members_key)
    else:
        pipe.expire(room_key, ROOM_TTL_SECONDS)
        pipe.expire(members_key, ROOM_TTL_SECONDS)

    await pipe.execute()
    return user_id == host_id


async def get_members(room_id: str) -> set:
    redis = await get_redis()
    return await redis.smembers(f"room:{room_id}:members")


async def is_host(room_id: str, user_id: str) -> bool:
    redis = await get_redis()
    host_id = await redis.hget(f"room:{room_id}", "host_id")
    return host_id == user_id


async def renew_room_ttl(room_id: str):
    redis = await get_redis()
    await redis.expire(f"room:{room_id}", ROOM_TTL_SECONDS)
    await redis.expire(f"room:{room_id}:members", ROOM_TTL_SECONDS)


async def check_host_timeout(room_id: str) -> bool:
    redis = await get_redis()
    room_key = f"room:{room_id}"

    room = await redis.hgetall(room_key)
    if not room:
        return False

    last_heartbeat = int(room.get("host_last_heartbeat", "0"))
    now = int(asyncio.get_event_loop().time())

    if now - last_heartbeat > HOST_TIMEOUT_SECONDS:
        pipe = redis.pipeline()
        pipe.delete(room_key)
        pipe.delete(f"room:{room_id}:members")
        await pipe.execute()
        return True

    return False
