#!/bin/bash
echo "User table"
docker exec -t my-postgres psql -U postgres -d postgres -c "SELECT * FROM users;"

echo -e "\n Product table"
docker exec -t my-postgres psql -U postgres -d postgres -c "SELECT * FROM products;"

echo -e "\n Order tables"
docker exec -t my-postgres psql -U postgres -d postgres -c "SELECT * FROM orders;"