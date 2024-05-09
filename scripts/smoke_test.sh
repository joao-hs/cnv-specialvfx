#!/bin/bash

# This script is used to test the main functionality of the system

# Arguments:
#   --ip: IP address of the server. Defaults to "127.0.0.1"
#   --port: Port number of the server. Defaults to "8000"


function compare_and_print() {
    if diff $1 $2; then
        echo -e "\e[32m\xE2\x9C\x94\e[0m" # green checkmark
    else
        echo -e "\e[31m\xE2\x9C\x98\e[0m" # red X
    fi
}


# Default values
IP="127.0.0.1"
PORT="8000"
_DIRNAME=$(dirname $0)

# Parse the command line arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --ip)
            IP="$2"
            shift
            ;;
        --port)
            PORT="$2"
            shift
            ;;
        *)
            ;;
    esac
    shift
done

# Test the main functionality of the system

echo "Testing $IP:$PORT/imageproc/blurimage"
start=$(date +%s)
$_DIRNAME/curl_imageproc.sh blurimage $_DIRNAME/resources/pre_blur.jpg blur.jpg --ip $IP --port $PORT
end=$(date +%s)
echo -n "Done: $((end-start))s "
compare_and_print $_DIRNAME/resources/post_blur.jpg $_DIRNAME/output/blur.jpg

echo "Testing $IP:$PORT/imageproc/enhanceimage"
start=$(date +%s)
$_DIRNAME/curl_imageproc.sh enhanceimage $_DIRNAME/resources/pre_enhance.jpg enhance.jpg --ip $IP --port $PORT
end=$(date +%s)
echo -n "Done: $((end-start))s "
compare_and_print $_DIRNAME/resources/post_enhance.jpg $_DIRNAME/output/enhance.jpg

echo "Testing $IP:$PORT/raytracer"
start=$(date +%s)
$_DIRNAME/curl_raytracer.sh $_DIRNAME/resources/pre_raytrace.txt raytrace.bmp --texmap $_DIRNAME/resources/pre_raytrace.bmp --ip $IP --port $PORT
end=$(date +%s)
echo -n "Done: $((end-start))s "
compare_and_print $_DIRNAME/resources/post_raytrace.bmp $_DIRNAME/output/raytrace.bmp