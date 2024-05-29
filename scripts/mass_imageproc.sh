#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <no_times_per_image>"
    exit 1
fi

count=-1
type="blurimage"
for ((i=0; i<$1; i++)); do
    for image in resources/pictures/*; do
        image_name=$(basename $image)
        sleep 0.5
        start_time=$(($(date +%s%N)))
        ./scripts/curl_imageproc.sh $type $image $image_name-blurred --port 8080
        end_time=$(($(date +%s%N)))
        elapsed_time=$(($end_time-$start_time))
        count=$((count+1))
        echo "$count,$image_name,$elapsed_time"
    done
done

type="enhanceimage"
for ((i=0; i<$1; i++)); do
    for image in resources/pictures/*; do
        image_name=$(basename $image)
        sleep 0.5
        start_time=$(($(date +%s%N)))
        ./scripts/curl_imageproc.sh $type $image $image_name-enhanced --port 8080
        end_time=$(($(date +%s%N)))
        elapsed_time=$(($end_time-$start_time))
        count=$((count+1))
        echo "$count,$image_name,$elapsed_time"
    done
done