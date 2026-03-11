#!/bin/bash
echo "--- Managing CSC301 Distributed Cluster ---"

# 1. Improved Cleanup: Added LoadBalancer to the list
pkill -9 -f "java.*(UserService|ProductService|OrderService|ISCS|LoadBalancer)"
sleep 1

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
    local className=$1
    local port=$2
    local index=$3
    local jarPath="$PROJECT_ROOT/target/A2_Project-1.0-SNAPSHOT-jar-with-dependencies.jar"

    echo "Launching $className on port $port..."

    # We use the Fat JAR as the classpath.
    # This includes all dependencies (Kotlin, Javalin, etc.)
    nohup java -Xmx256m -XX:+UseZGC -cp "$jarPath" "$className" "$CONFIG_PATH" "$DB_CONFIG_PATH" "$index" > "log_${HOSTNAME}_${port}.txt" 2>&1 &

    sleep 0.5 # Give it a moment to start
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
  #The Order Service
  "dh2026pc09")
      echo "Host pc09 (142.1.46.12): Starting OrderServices 16001-16002..."
      start_service "OrderService.OrderService" 16001 0
      start_service "OrderService.OrderService" 16002 1
      ;;
  "dh2026pc12")
      echo "Host pc12 (142.1.46.15): Starting OrderServices 16003-16004..."
      start_service "OrderService.OrderService" 16003 2
      start_service "OrderService.OrderService" 16004 3
      ;;

  "dh2026pc13")
    echo "Host pc13 (142.1.46.16): Starting OrderServices 16005-16007..."
    start_service "OrderService.OrderService" 16005 4
    start_service "OrderService.OrderService" 16006 5
    start_service "OrderService.OrderService" 16007 6
    ;;
  # The Order Service

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