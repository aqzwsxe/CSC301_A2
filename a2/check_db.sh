#!/bin/bash

# Define the database file name used in your DatabaseManager
DB_FILE="service_data.db"

echo "--- User Table ---"
sqlite3 $DB_FILE "SELECT * FROM users;" -header -column

echo -e "\n--- Product Table ---"
sqlite3 $DB_FILE "SELECT * FROM products;" -header -column

echo -e "\n--- Order Table ---"
sqlite3 $DB_FILE "SELECT * FROM orders;" -header -column