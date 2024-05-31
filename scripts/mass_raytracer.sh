#!/bin/bash

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <no_times_per_scene> <ip> <port>"
    exit 1
fi

count=-1
for scene in raytracer/resources/*.txt; do
    scene_name=$(basename $scene .txt)
    bitmap=$(echo $scene | sed 's/txt$/bmp/')
    for ((i=0; i<$1; i++)); do
        start_time=$(($(date +%s%N)))
        ./scripts/curl_raytracer.sh $scene $scene_name.bmp --texmap $bitmap --ip $2 --port $3
        end_time=$(($(date +%s%N)))
        elapsed_time=$(($end_time-$start_time))
        count=$((count+1))
        echo "$count,$scene_name,$elapsed_time"
    done
done