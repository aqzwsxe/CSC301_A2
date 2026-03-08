#!/bin/bash

#First line: tell the computer to use the Bash shell to execute these commands
# Configuration
CONFIG="config.json"
OUT_DIR="compiled"
SRC_DIR="src"
LIB_DIR="lib"
JDBC_JAR="$LIB_DIR/sqlite-jdbc-3.51.2.0.jar"
JAVALIN_JAR="$LIB_DIR/javalin-6.1.3.jar"

# Set Classpath Separator
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    CP_SEP=";"
else
    CP_SEP=":"
fi

# Define the Classpath (Includes compiled classes and Maven dependencies)
FULL_CP="$OUT_DIR${CP_SEP}$JDBC_JAR${CP_SEP}$JAVALIN_JAR${CP_SEP}$LIB_DIR/*${CP_SEP}target/dependency/*${CP_SEP}."

compile_service(){
    local service=$1
    echo "Compiling $service..."
    mkdir -p "$OUT_DIR"
    # Compile with access to all dependencies in target/dependency
    javac -cp "$FULL_CP" -d "$OUT_DIR" -sourcepath "$SRC_DIR" "$SRC_DIR/$service"/*.java
}

case "$1" in
      -c)
            echo "--- Starting Build Process ---"

            # 1. Clean up
            rm -rf "$OUT_DIR"
            mkdir -p "$OUT_DIR"

            # 2. Extract dependencies from Maven
            echo "Fetching dependencies via Maven..."
            mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -q

            # 3. Compile in order
            compile_service "Utils"
            compile_service "UserService"
            compile_service "OrderService"
            compile_service "ProductService"
            compile_service "ISCS"

            echo "--- Compilation Complete ---"
            echo "Remote DB on VM (142.1.114.76) will be used at runtime."
            ;;

#    -u)
#            echo "Starting User Service"
#            java -cp "$OUT_DIR${CP_SEP}$JDBC_JAR" UserService.UserService "$CONFIG"
#            echo  "Press enter to close"
#            read
#            ;;
#
#    -p)
#            echo "Starting Product Service"
#            java -cp "$OUT_DIR${CP_SEP}$JDBC_JAR" ProductService.ProductService "$CONFIG"
#            echo  "Press enter to close"
#            read
#            ;;
#    -i)
#            echo "Starting Inter-Service Communication Service (ISCS)"
#            java -cp "$OUT_DIR${CP_SEP}$JDBC_JAR" ISCS.ISCS "$CONFIG"
#            echo  "Press enter to close"
#            read
#            ;;
#    -o)
#            echo "Starting Order Service"
#            java -cp "$OUT_DIR${CP_SEP}$JDBC_JAR" OrderService.OrderService "$CONFIG"
#            echo  "Press enter to close"
#            read
#            ;;

    -w)
            # Check if the workload file was provided
            if [ -z "$2" ]; then
                echo "Error: Please provide a workload file"
            else
                echo "Starting Workload Parser with file: $2"
                java -cp "$FULL_CP" Utils.WorkloadParser "$2"
            fi
            ;;
    *)
            echo "used a wrong command"
            exit  1;
            ;;

esac