import redis.asyncio as aioredis
from config import REDIS_URL

redis_pool = None


async def get_redis():
    global redis_pool
    if redis_pool is None:
        redis_pool = aioredis.from_url(REDIS_URL, decode_responses=True)
    return redis_pool


async def close_redis():
    global redis_pool
    if redis_pool:
        await redis_pool.close()
        redis_pool = None
