#!/bin/bash

# Command Line arguments:
# Mandatory:
#   $1: Processing type ("blurimage" or "enhanceimage")
#   $2: Image file (.jpg) to process
#   $3: Output file to save the processed image
# Optional:
#   --ip: IP address of the server. Default is 127.0.0.1
#   --port: Port number of the server. Default is 8000

# Verify if the mandatory arguments are provided
if [ $# -lt 3 ]; then
    echo "Usage: $0 <processing_type> <image_file> <output_file> [--ip <ip_address>] [--port <port_number>]"
    exit 1
fi

# Assert that mandatory arguments are the first ones
if [ "${1:0:2}" == "--" ]; then
    echo "The processing type should be the third argument"
    exit 1
fi

if [ "${2:0:2}" == "--" ]; then
    echo "The image file should be the first argument"
    exit 1
fi

if [ "${3:0:2}" == "--" ]; then
    echo "The output file should be the second argument"
    exit 1
fi

# Assert that the processing type is valid
if [ "$1" != "blurimage" ] && [ "$1" != "enhanceimage" ]; then
    echo "The processing type should be either 'blurimage' or 'enhanceimage'"
    exit 1
fi

# Assert that the image file exists
if [ ! -f $2 ]; then
    echo "The image file does not exist"
    exit 1
fi

PROCESSING_TYPE=$1
IMAGE_FILE=$2
OUTPUT_FILE=$3

# Default values
IP="127.0.0.1"
PORT="8000"
_DIRNAME=$(dirname $0)
_TMP_DIR="$_DIRNAME/tmp/"
_OUT_DIR="$_DIRNAME/output/"
_PAYLOAD_FILE="$_TMP_DIR/payload.json"

# Create the temporary directory
mkdir -p $_TMP_DIR

# Create the output directory
mkdir -p $_OUT_DIR

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


# Encode in Base64
base64 $IMAGE_FILE > $_PAYLOAD_FILE

image_extension="${IMAGE_FILE##*.}"

# Append a formatting string
echo -e "data:image/$image_extension;base64,$(cat $_PAYLOAD_FILE)" > $_PAYLOAD_FILE

# Send the request
curl -sS -X POST http://$IP:$PORT/$PROCESSING_TYPE --data @"$_PAYLOAD_FILE" > $_TMP_DIR/$OUTPUT_FILE

# Remove a formatting string (remove everything before the comma)
sed -i 's/^[^,]*,//' $_TMP_DIR/$OUTPUT_FILE

# Decode from Base64
base64 -d $_TMP_DIR/$OUTPUT_FILE > $_OUT_DIR/$OUTPUT_FILE
