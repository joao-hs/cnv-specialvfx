#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <blur|enhance> <no_times_per_image>"
    exit 1
fi

count=-1
for image in imageproc/resources/*; do
    image_name=$(basename $image)
    for ((i=0; i<$2; i++)); do
        start_time=$(($(date +%s%N)))
        ./scripts/curl_imageproc.sh $1 $image $image_name
        end_time=$(($(date +%s%N)))
        elapsed_time=$(($end_time-$start_time))
        count=$((count+1))
        echo "$count,$image_name,$elapsed_time"
    done
done