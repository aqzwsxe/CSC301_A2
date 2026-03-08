#!/bin/bash

# Configuration
PROJECT_ROOT=$(pwd)
BIN_DIR="$PROJECT_ROOT/out/production/CSC301_A2"
CONFIG_PATH="$PROJECT_ROOT/a2/config.json"
DB_CONFIG_PATH="$PROJECT_ROOT/a2/dbConfig.json"

# Function to start a service in the background
start_service() {
local className=$1
local port=$2
echo "Starting $className on port $port..."
# 'nohup' and '&' keep it running in the background
nohup java -Xmx256m -cp "$BIN_DIR" "$className" "$CONFIG_PATH" "$DB_CONFIG_PATH" "$port" > "log_$port.txt" 2>&1 &
}

echo "--- Launching CSC301 A2 Cluster ---"

# 1. Start UserServices (14001-14007)
for i in {1..7}; do start_service "UserService.UserServiceMain" $((14000 + i)); done

# 2. Start ProductServices (15001-15007)
for i in {1..7}; do start_service "ProductService.ProductServiceMain" $((15000 + i)); done

# 3. Start OrderServices (16001-16007)
for i in {1..7}; do start_service "OrderService.OrderServiceMain" $((16000 + i)); done

# 4. Start ISCS
echo "Starting ISCS Load Balancer on 14000..."
start_service "ISCS.ISCS" 14000

echo "Cluster is up! Check logs with 'tail -f log_16001.txt'"