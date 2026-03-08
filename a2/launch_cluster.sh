#!/bin/bash
echo "--- Shutting down CSC301 Cluster before ---"

# Kill all Java processes started by this user
# -9 is a forceful kill to ensure ports are released immediately
pkill -9 -f "java.*(UserService|ProductService|OrderService|ISCS)"

echo "Cleanup complete. Verification (should be empty):"
netstat -tuln | grep -E '1400|1500|1600'

# Configuration
PROJECT_ROOT=$(pwd)
BIN_DIR="$PROJECT_ROOT/compiled"
LIB_DIR="$PROJECT_ROOT/lib/*" # Point to your direct JAR folder

CONFIG_PATH="$PROJECT_ROOT/config.json"
DB_CONFIG_PATH="$PROJECT_ROOT/dbConfig.json"

# Set Classpath (Linux uses : separator)
CP_SEP=":"
FULL_CP="$BIN_DIR${CP_SEP}$LIB_DIR${CP_SEP}."

# Function to start a service
start_service() {
    local className=$1
    local port=$2
    local index=$3 # The 0-based index for the JSON array
    echo "Starting $className (Instance $index) on port $port..."

    # We pass 'index' as the 3rd argument (args[2] in Java)
    nohup java -Xmx256m -XX:+UseZGC -cp "$FULL_CP" "$className" "$CONFIG_PATH" "$DB_CONFIG_PATH" "$index" > "log_$port.txt" 2>&1 &
}

echo "--- Launching CSC301 A2 Cluster (22 Instances) ---"

# 1. Start UserServices (Indices 0-6, Ports 14001-14007)
for i in {0..6}; do
    start_service "UserService.UserService" $((14001 + i)) $i
done

# 2. Start ProductServices (Indices 0-6, Ports 15001-15007)
for i in {0..6}; do
    start_service "ProductService.ProductService" $((15001 + i)) $i
done

# 3. Start OrderServices (Indices 0-6, Ports 16001-16007)
for i in {0..6}; do
    start_service "OrderService.OrderService" $((16001 + i)) $i
done

# 4. Start ISCS Load Balancer (Index 0, Port 14000)
echo "Starting ISCS Load Balancer on 14000..."
start_service "ISCS.ISCS" 14000 0

echo "-----------------------------------------------"
echo "Cluster launch initiated. Logs: log_*.txt"
echo "Use 'netstat -tuln | grep 1' to verify ports."

echo "Cluster is booting up..."
sleep 3
echo "--- Cluster Status ---"
PORT_COUNT=$(netstat -tuln | grep -E '1400[0-7]|1500[1-7]|1600[1-7]' | wc -l)
echo "Total Active Ports: $PORT_COUNT / 22"
if [ "$PORT_COUNT" -eq 22 ]; then
    echo "READY: You can now run ./runme.sh -w <file>"
else
    echo "WARNING: Some services failed to start. Check log_*.txt"
fi