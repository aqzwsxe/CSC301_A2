import asyncio
import aiohttp
import json
import random
import time

# This tester randomly takes testcases from user, product, and order payloads in CSC301_A1_testcases
# and outputs the request per second every 3 seconds

# Load balancer base URL
LB_BASE = "http://142.1.46.14:18001"

# Endpoints
USER_URL = f"{LB_BASE}/user"
ORDER_URL = f"{LB_BASE}/order"
PRODUCT_URL = f"{LB_BASE}/product"

# Concurrency
CONCURRENCY = 50  # number of concurrent workers
PRINT_INTERVAL = 3  # seconds

# Load payloads from JSON files
with open("CSC301_A1_testcases/payloads/user_testcases.json") as f:
    user_payloads = list(json.load(f).values())

with open("CSC301_A1_testcases/payloads/product_testcases.json") as f:
    product_payloads = list(json.load(f).values())

with open("CSC301_A1_testcases/payloads/order_testcases.json") as f:
    order_payloads = list(json.load(f).values())

# Counters for RPS
rps_counter = {"total": 0, "success": 0}

# Async HTTP functions
async def send_post(session, url, payload):
    try:
        async with session.post(url, json=payload) as resp:
            await resp.text()
            return resp.status
    except Exception:
        return None

async def send_get(session, url, payload):
    # Some GET payloads are sent via query or JSON depending on your service
    # Here we just simulate sending them as POST to keep it simple
    try:
        async with session.post(url, json=payload) as resp:
            await resp.text()
            return resp.status
    except Exception:
        return None

# Worker: continuously send requests
async def worker(session):
    while True:
        # Randomly pick a service to hit
        service = random.choice(["user", "product", "order"])
        if service == "user":
            payload = random.choice(user_payloads)
            status = await send_post(session, USER_URL, payload)
        elif service == "product":
            payload = random.choice(product_payloads)
            status = await send_post(session, PRODUCT_URL, payload)
        elif service == "order":
            payload = random.choice(order_payloads)
            status = await send_post(session, ORDER_URL, payload)

        # Update counters
        rps_counter["total"] += 1
        if status in [200, 201]:
            rps_counter["success"] += 1

# Monitor: print RPS every interval
async def monitor():
    while True:
        await asyncio.sleep(PRINT_INTERVAL)
        total = rps_counter["total"]
        success = rps_counter["success"]
        print(f"Last {PRINT_INTERVAL}s - Total Requests: {total}, Success: {success}, Request Per Second: {total/PRINT_INTERVAL:.2f}")
        # reset counters
        rps_counter["total"] = 0
        rps_counter["success"] = 0

# Main async function
async def main():
    connector = aiohttp.TCPConnector(limit=CONCURRENCY)
    async with aiohttp.ClientSession(connector=connector) as session:
        workers = [worker(session) for _ in range(CONCURRENCY)]
        tasks = workers + [monitor()]
        await asyncio.gather(*tasks)

if __name__ == "__main__":
    asyncio.run(main())