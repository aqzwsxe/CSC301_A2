#!/bin/bash
echo "--- Shutting down CSC301 Cluster ---"

# 1. Improved Cleanup: Also look for port 17001 specifically
pkill -9 -f "java.*(UserService|ProductService|OrderService|ISCS)"
# Give the OS a moment to fully release the sockets
sleep 1

# Configuration
PROJECT_ROOT=$(pwd)
BIN_DIR="$PROJECT_ROOT/compiled"
LIB_DIR="$PROJECT_ROOT/lib/*"
CONFIG_PATH="$PROJECT_ROOT/config.json"
DB_CONFIG_PATH="$PROJECT_ROOT/dbConfig.json"
CP_SEP=":"
FULL_CP="$BIN_DIR${CP_SEP}$LIB_DIR${CP_SEP}."

start_service() {
    local className=$1
    local port=$2
    local index=$3
    echo "Starting $className (Instance $index) on port $port..."
    nohup java -Xmx256m -XX:+UseZGC -cp "$FULL_CP" "$className" "$CONFIG_PATH" "$DB_CONFIG_PATH" "$index" > "log_$port.txt" 2>&1 &
}

echo "--- Launching CSC301 A2 Cluster (22 Instances) ---"

for i in {0..6}; do
    start_service "UserService.UserService" $((14001 + i)) $i
done

for i in {0..6}; do
    start_service "ProductService.ProductService" $((15001 + i)) $i
done

for i in {0..6}; do
    start_service "OrderService.OrderService" $((16001 + i)) $i
done

# 4. Start ISCS on 17001
# IMPORTANT: Your Java code for ISCS must use index 0 to find 17001 in config.json
start_service "ISCS.ISCS" 17001 0

echo "-----------------------------------------------"
sleep 5 # Increased sleep to give the 22nd service time to finish its HealthCheck

echo "--- Cluster Status ---"
# Added 17001 to the grep pattern
PORT_COUNT=$(netstat -tuln | grep -E '1400[1-7]|1500[1-7]|1600[1-7]|17001' | wc -l)

echo "Total Active Ports: $PORT_COUNT / 22"
if [ "$PORT_COUNT" -eq 22 ]; then
    echo "READY: You can now run ./runme.sh -w <file>"
else
    echo "WARNING: Check log_17001.txt for DB connection errors."
fi