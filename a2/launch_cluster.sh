#!/bin/bash
echo "--- Managing CSC301 Distributed Cluster ---"

# 1. Improved Cleanup: Added LoadBalancer to the list
#pkill -9 -f "java.*(UserService|ProductService|OrderService|ISCS|LoadBalancer)"
#sleep 1

# 2. Configuration
PROJECT_ROOT=$(pwd)
BIN_DIR="${PROJECT_ROOT}/target/classes"
# Use a colon-separated list and ensure paths are absolute
FULL_CP="${BIN_DIR}:${PROJECT_ROOT}/lib/*:."
CONFIG_PATH="$PROJECT_ROOT/config.json"
DB_CONFIG_PATH="$PROJECT_ROOT/dbConfig.json"
CP_SEP=":"
# This FULL_CP will now correctly find your code AND the new jar files
FULL_CP="${BIN_DIR}:${PROJECT_ROOT}/lib/*:."
HOSTNAME=$(hostname)

# 3. Helper Function (REQUIRED)
start_service() {
    local service_class=$1
    local port=$2
    local id=$3

    echo "Restarting $service_class on port $port (ID: $id)..."

    # 1. Kill the specific process on this port
    fuser -k ${port}/tcp > /dev/null 2>&1 || true
    sleep 0.2

    # 2. Start the service in the background
    # We pass the port, config path, and ID as arguments to your Main method
    nohup java -cp "$FULL_CP" "$service_class" "$port" "$CONFIG_PATH" "$id" > "logs_${port}.log" 2>&1 &

    echo "Service $id started on $port."
}

# 4. Host-Specific Logic
case $HOSTNAME in
  "dh2026pc06")
    echo "Host pc06: Starting UserServices..."
    for i in {0..6}; do
        start_service "UserService.UserService" $((14001 + i)) $i
    done
    ;;
  "dh2026pc07")
    echo "Host pc07: Starting ProductServices..."
    for i in {0..6}; do
        start_service "ProductService.ProductService" $((15001 + i)) $i
    done
    ;;
  "dh2026pc09")
    echo "Host pc09: Starting OrderServices..."
    for i in {0..6}; do
        start_service "OrderService.OrderService" $((16001 + i)) $i
    done
    ;;
  "dh2026pc10")
    echo "Host pc10: Starting ISCS Bridge..."
    start_service "ISCS.ISCS" 17001 0
    ;;
  "dh2026pc11")
    echo "Host pc11: Starting Load Balancer..."
    start_service "Utils.LoadBalancer" 18001 0
    ;;
  *)
    echo "WARNING: Unknown host ($HOSTNAME). No services started."
    ;;
esac

echo "-----------------------------------------------"
echo "Launch sequence complete for $HOSTNAME."