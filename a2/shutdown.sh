#!/bin/bash

machines=(
  dh2026pc06
  dh2026pc07
  dh2026pc09
  dh2026pc10
  dh2026pc11
)

for m in "${machines[@]}"
do
    echo "Stopping services on $m"
    ssh @$m "pkill -f java"
done