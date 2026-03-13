import asyncio
import aiohttp
import json
import random
import time

# --- CONFIGURATION ---
# Load Balancer Address
LB_BASE = "http://142.1.46.14:18001"
CONCURRENCY = 100
PRINT_INTERVAL = 3
SETUP_COUNT = 200

# Endpoints
USER_URL = f"{LB_BASE}/user"
PRODUCT_URL = f"{LB_BASE}/product"
ORDER_URL = f"{LB_BASE}/order"

# State Tracking
VALID_USERS = []
VALID_PRODUCTS = []
rps_counter = {"total": 0, "success": 0, "latency": 0.0}

async def clear_database(session):
    print("Action: Clearing database...")
    try:
        async with session.post(USER_URL, json={"command": "clear"}, timeout=10) as resp:
            return resp.status == 200
    except Exception as e:
        print(f"Clear failed: {e}")
        return False

async def setup_data(session):
    """Parallelized setup using the correct JSON schema for your handlers."""
    print(f"Action: Starting Setup (Creating {SETUP_COUNT} users and products)...")
    sem = asyncio.Semaphore(10)

    async def create_user(i):
        async with sem:
            payload = {
                "command": "create",
                "id": i,
                "username": f"tester{i}",
                "email": f"test{i}@test.com",
                "password": f"pass{i}"
            }
            try:
                async with session.post(USER_URL, json=payload, timeout=5) as resp:
                    if resp.status in [200, 201]:
                        VALID_USERS.append(i)
                    else:
                        print(f"User {i} failed: {resp.status}")
            except Exception as e:
                print(f"User {i} error: {e}")

    async def create_product(i):
        async with sem:
            payload = {
                "command": "create",
                "id": i,
                "name": f"prod{i}",
                "description": "benchmark_item",
                "price": 50.0,
                "quantity": 100000
            }
            try:
                async with session.post(PRODUCT_URL, json=payload, timeout=5) as resp:
                    if resp.status in [200, 201]:
                        VALID_PRODUCTS.append(i)
                    else:
                        print(f"Product {i} failed: {resp.status}")
            except Exception as e:
                print(f"Product {i} error: {e}")

    # Launch creation tasks
    tasks = [create_user(i) for i in range(1, SETUP_COUNT + 1)] + \
            [create_product(i) for i in range(1, SETUP_COUNT + 1)]
    await asyncio.gather(*tasks)
    print(f"Status: Setup Complete. Users: {len(VALID_USERS)}, Products: {len(VALID_PRODUCTS)}")

async def perform_request(session):
    """Mix of 70% Orders, 20% Product Gets, 10% User Gets."""
    choice = random.random()
    start_time = time.perf_counter()
    status = 0

    try:
        if choice < 0.70 and VALID_USERS and VALID_PRODUCTS:
            payload = {
                "command": "place order",
                "user_id": random.choice(VALID_USERS),
                "product_id": random.choice(VALID_PRODUCTS),
                "quantity": 1
            }
            async with session.post(ORDER_URL, json=payload, timeout=5) as resp:
                status = resp.status
        elif choice < 0.90 and VALID_PRODUCTS:
            pid = random.choice(VALID_PRODUCTS)
            async with session.get(f"{PRODUCT_URL}/{pid}", timeout=5) as resp:
                status = resp.status
        else:
            uid = random.choice(VALID_USERS) if VALID_USERS else 1
            async with session.get(f"{USER_URL}/{uid}", timeout=5) as resp:
                status = resp.status
    except Exception:
        status = 500

    latency = time.perf_counter() - start_time
    rps_counter["total"] += 1
    rps_counter["latency"] += latency
    if status in [200, 201]:
        rps_counter["success"] += 1

async def worker(session):
    while True:
        await perform_request(session)

async def monitor():
    while True:
        await asyncio.sleep(PRINT_INTERVAL)
        total = rps_counter["total"]
        success = rps_counter["success"]
        avg_lat = (rps_counter["latency"] / total * 1000) if total > 0 else 0
        print(f"[{time.strftime('%H:%M:%S')}] RPS: {total/PRINT_INTERVAL:.2f} | "
              f"Success: {success}/{total} | Latency: {avg_lat:.2f}ms")
        rps_counter.update({"total": 0, "success": 0, "latency": 0.0})

async def main():
    connector = aiohttp.TCPConnector(limit=CONCURRENCY, keepalive_timeout=60)
    headers = {"Content-Type": "application/json"}
    async with aiohttp.ClientSession(connector=connector, headers=headers) as session:
        # Step 1: Prep
        await clear_database(session