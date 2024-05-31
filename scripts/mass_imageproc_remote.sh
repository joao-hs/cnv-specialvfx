#!/bin/bash

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <blur|enhance> <no_times_per_image> <load_balancer_address>"
    exit 1
fi

operation=$1
no_times_per_image=$2
load_balancer_address=$3

handle_request() {
    local operation=$1
    local image=$2
    local image_name=$3
    local load_balancer_address=$4

    start_time=$(($(date +%s%N)))

    # Call curl_imageproc.sh with the load balancer address
    ./scripts/curl_imageproc.sh $operation $image $image_name --ip $load_balancer_address

    end_time=$(($(date +%s%N)))
    elapsed_time=$(($end_time - $start_time))
    count=$((count + 1))
    echo "$count,$image_name,$elapsed_time"
}

count=-1
for ((i = 0; i < no_times_per_image; i++)); do
    for image in imageproc/resources/*; do
    image_name=$(basename "$image")
    
        handle_request $operation $image $image_name $load_balancer_address &

        #non-linear delay
#        sleep_time=$(echo "scale=2; $RANDOM % (2^$i % 10 + 1)" | bc)
#        sleep $sleep_time
    done
done

# Wait for background processes to finish
wait
