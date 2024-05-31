#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <no_times_per_scene> <load_balancer_address>"
    exit 1
fi

no_times_per_scene=$1
load_balancer_address=$2

handle_request() {
    local scene=$1
    local scene_name=$2
    local bitmap=$3

    start_time=$(($(date +%s%N)))

    # Call the curl_raytracer.sh script with the load balancer address
    ./scripts/curl_raytracer.sh "$scene" "$scene_name.bmp" "$load_balancer_address" --texmap "$bitmap"

    end_time=$(($(date +%s%N)))
    elapsed_time=$(($end_time - $start_time))
    count=$((count + 1))
    echo "$count,$scene_name,$elapsed_time"
}

count=-1
for scene in raytracer/resources/*.txt; do
    scene_name=$(basename "$scene" .txt)
    bitmap=$(echo "$scene" | sed 's/txt$/bmp/')
    for ((i = 0; i < no_times_per_scene; i++)); do
        # Call handle_request function in parallel
        handle_request "$scene" "$scene_name" "$bitmap" &

        # Introduce a non-linear delay (exponential backoff pattern)
        sleep $((2 ** i % 10))
    done
done

# Wait for all background processes to finish
wait
