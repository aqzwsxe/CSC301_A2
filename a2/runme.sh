#!/bin/bash

#First line: tell the computer to use the Bash shell to execute these commands
# Configuration
CONFIG="config.json"
OUT_DIR="compiled"
SRC_DIR="src"

# Function to compile a service
compile_service(){
local service=$1
    echo "Compiling $service..."
    mkdir -p "$OUT_DIR"
    javac -cp "$OUT_DIR" -d "$OUT_DIR" -sourcepath "$SRC_DIR" "$SRC_DIR/$service"/*.java
}

case "$1" in
    -c)
            echo "Cleaning and Compiling all services..."
            rm -rf "$OUT_DIR"
            mkdir -p "$OUT_DIR"

            # 1. Compile Utils first so others can find ConfigReader
            compile_service "Utils"

            # 2. Compile UserService (now it can find Utils in the $OUT_DIR)
            compile_service "UserService"
            compile_service "OrderService"
            compile_service "ProductService"
            compile_service "ISCS"
            compile_service "Utils"

            echo "Done."
            echo  "Press enter to close"
            read
            ;;

    -u)
            echo "Starting User Service"
            java -cp "$OUT_DIR" UserService.UserService "$CONFIG"
            echo  "Press enter to close"
            read
            ;;

    -p)
            echo "Starting Product Service"
            java -cp "$OUT_DIR" ProductService.ProductService "$CONFIG"
            echo  "Press enter to close"
            read
            ;;
    -i)
            echo "Starting Inter-Service Communication Service (ISCS)"
            java -cp "$OUT_DIR" ISCS.ISCS "$CONFIG"
            echo  "Press enter to close"
            read
            ;;
    -o)
            echo "Starting Order Service"
            java -cp "$OUT_DIR" OrderService.OrderService "$CONFIG"
            echo  "Press enter to close"
            read
            ;;

    -w)
            # Check if the workload file was provided
            if [ -z "$2" ]; then
                echo "Error: Please provide a workload file"
            fi
            echo "Starting Workload Parser with file: $2"
            java -cp "$OUT_DIR" Utils.WorkloadParser "$2"
            ;;
    *)
            echo "used a wrong command"
            exit  1;
            ;;

esac