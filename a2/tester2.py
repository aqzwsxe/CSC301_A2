import asyncio
import aiohttp
import json
import random
import time

# --- CONFIGURATION ---
LB_BASE = "http://142.1.46.14:18001"
CONCURRENCY = 100  # Number of concurrent worker tasks
PRINT_INTERVAL = 3  # Seconds between RPS reports
SETUP_COUNT = 200   # Number of users/products to pre-create

# Endpoints
USER_URL = f"{LB_BASE}/user"
PRODUCT_URL = f"{LB_BASE}/product"
ORDER_URL = f"{LB_BASE}/order"

# State Tracking
VALID_USERS = []
VALID_PRODUCTS = []
rps_counter = {"total": 0, "success": 0, "latency": 0.0}

async def clear_database(session):
    """Resets the environment before testing."""
    print("Clearing database...")
    try:
        # Assuming your POST /user or /product with 'clear' command resets the system
        async with session.post(f"{USER_URL}", json={"command": "clear"}) as resp:
            return resp.status == 200
    except Exception as e:
        print(f"Clear failed: {e}")
        return False

async def setup_data(session):
    """Phase 1: Warm up the database with valid users and products."""
    print(f"Starting Setup: Creating {SETUP_COUNT} users and products...")

    # Create Users
    for i in range(1, SETUP_COUNT + 1):
        payload = {"command": "create", "id": i, "username": f"user{i}", "email": f"u{i}@test.com"}
        async with session.post(USER_URL, json=payload) as resp:
            if resp.status in [200, 201]:
                VALID_USERS.append(i)

    # Create Products with high stock
    for i in range(1, SETUP_COUNT + 1):
        payload = {
            "command": "create", "id": i, "name": f"item{i}",
            "description": "benchmark_item", "price": 10.0, "quantity": 100000
        }
        async with session.post(PRODUCT_URL, json=payload) as resp:
            if resp.status in [200, 201]:
                VALID_PRODUCTS.append(i)

    print(f"Setup Complete. Valid Users: {len(VALID_USERS)}, Valid Products: {len(VALID_PRODUCTS)}")

async def perform_request(session):
    """Logic for a single request pick."""
    # Weighting: 70% Orders, 20% GET Product, 10% GET Order
    choice = random.random()
    start_time = time.perf_counter()
    status = 0

    try:
        if choice < 0.70:
            # POST: Place Order
            payload = {
                "command": "place order",
                "user_id": random.choice(VALID_USERS),
                "product_id": random.choice(VALID_PRODUCTS),
                "quantity": random.randint(1, 2)
            }
            async with session.post(ORDER_URL, json=payload) as resp:
                status = resp.status

        elif choice < 0.90:
            # GET: Product Info
            pid = random.choice(VALID_PRODUCTS)
            async with session.get(f"{PRODUCT_URL}/{pid}") as resp:
                status = resp.status

        else:
            # GET: Order Info (Using a likely valid ID)
            oid = random.randint(1, 100)
            async with session.get(f"{ORDER_URL}/{oid}") as resp:
                status = resp.status

    except Exception:
        status = 500

    # Record metrics
    latency = time.perf_counter() - start_time
    rps_counter["total"] += 1
    rps_counter["latency"] += latency
    if status in [200, 201]:
        rps_counter["success"] += 1

async def worker(session):
    """Continuous loop for workers."""
    while True:
        await perform_request(session)

async def monitor():
    """Prints the stats every PRINT_INTERVAL."""
    while True:
        await asyncio.sleep(PRINT_INTERVAL)
        total = rps_counter["total"]
        success = rps_counter["success"]
        avg_lat = (rps_counter["latency"] / total * 1000) if total > 0 else 0

        print(f"[{time.strftime('%H:%M:%S')}] RPS: {total/PRINT_INTERVAL:.2f} | "
              f"Success: {success} | Total: {total} | Avg Latency: {avg_lat:.2f}ms")

        # Reset interval counters
        rps_counter["total"] = 0
        rps_counter["success"] = 0
        rps_counter["latency"] = 0.0

async def main():
    # Use a high-limit connector for maximum concurrency
    connector = aiohttp.TCPConnector(limit=CONCURRENCY, keepalive_timeout=60)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 1. Cleanup
        await clear_database(session)

        # 2. Warm up
        await setup_data(session)

        if not VALID_USERS or not VALID_PRODUCTS:
            print("Setup failed to create data. Check your services!")
            return

        print(f"Launching Stress Test with {CONCURRENCY} workers...")
        # 3. Stress Test
        workers = [asyncio.create_task(worker(session)) for _ in range(CONCURRENCY)]
        monitor_task = asyncio.create_task(monitor())

        await asyncio.gather(monitor_task, *workers)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nStopping tester...")