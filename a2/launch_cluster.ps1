#!/bin/bash

# Configuration
PROJECT_ROOT=$(pwd)
# 1. FIXED: Point to the 'compiled' folder from runme.sh
BIN_DIR="$PROJECT_ROOT/compiled"
# 2. FIXED: Include Maven dependencies in the Classpath
LIB_DIR="$PROJECT_ROOT/target/dependency/*"
JDBC_JAR="$PROJECT_ROOT/lib/sqlite-jdbc-3.51.2.0.jar"

CONFIG_PATH="$PROJECT_ROOT/config.json"
DB_CONFIG_PATH="$PROJECT_ROOT/dbConfig.json"

# Set Classpath Separator (Linux uses :)
CP_SEP=":"
FULL_CP="$BIN_DIR${CP_SEP}$LIB_DIR${CP_SEP}$JDBC_JAR${CP_SEP}."

# Function to start a service in the background
start_service() {
local className=$1
local port=$2
echo "Starting $className on port $port..."

# Using -XX:+UseZGC for the 4,000 req/s benchmark performance
nohup java -Xmx256m -XX:+UseZGC -cp "$FULL_CP" "$className" "$CONFIG_PATH" "$DB_CONFIG_PATH" "$port" > "log_$port.txt" 2>&1 &
}

echo "--- Launching CSC301 A2 Cluster (22 Instances) ---"

# 1. Start UserServices (14001-14007)
for i in {1..7}; do start_service "UserService.UserService" $((14000 + i)); done

# 2. Start ProductServices (15001-15007)
for i in {1..7}; do start_service "ProductService.ProductService" $((15000 + i)); done

# 3. Start OrderServices (16001-16007)
for i in {1..7}; do start_service "OrderService.OrderService" $((16000 + i)); done

# 4. Start ISCS Load Balancer
echo "Starting ISCS Load Balancer on 14000..."
start_service "ISCS.ISCS" 14000