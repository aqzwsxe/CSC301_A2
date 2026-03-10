#!/bin/bash

DIR="Desktop/301/A2_component2/CSC301_A2/a2"

machines=(
  dh2026pc06
  dh2026pc07
  dh2026pc09
  dh2026pc10
  dh2026pc11
)

./runme.sh -c

for m in "${machines[@]}"; do
    echo "Launching on $m"
    ssh $m "cd $DIR && ./launch_cluster.sh" &
done

wait
echo "All machines started."