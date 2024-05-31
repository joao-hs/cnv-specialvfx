#!/bin/bash

# Command Line arguments:
# Mandatory:
#   $1: Scene file with the raw content
#   $2: Output file to save the rendered image
# Optional:
#   --ip: IP address of the server. Default is 127.0.0.1
#   --port: Port number of the server. Default is 8000
#   --texmap: Path to the texmap file. Default is not provided

# Verify if the mandatory arguments are provided
if [ $# -lt 2 ]; then
    echo "Usage: $0 <scene_file> <output_file> [--ip <ip_address>] [--port <port_number>] [--texmap <texmap_file>]"
    exit 1
fi

# Assert that scene_file and output_file are the first ones
if [ "${1:0:2}" == "--" ]; then
    echo "The scene file should be the first argument"
    exit 1
fi

if [ "${2:0:2}" == "--" ]; then
    echo "The output file should be the second argument"
    exit 1
fi

# Assert that the scene file exists
if [ ! -f $1 ]; then
    echo "The scene file does not exist"
    exit 1
fi

SCENE_FILE=$1
OUTPUT_FILE=$2

# Default values
IP="127.0.0.1"
PORT="8000"
TEXMAP=""
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
        --texmap)
            TEXMAP="$2"
            shift
            ;;
        *)
            ;;
    esac
    shift
done


# Add the scene file raw content to JSON
cat $SCENE_FILE | jq -sR '{scene: .}' > $_PAYLOAD_FILE

# Add texmap.bmp binary to JSON (optional step, required only for some scenes)
if [ ! -z "$TEXMAP" ]; then
    hexdump -ve '1/1 "%u\n"' $TEXMAP | jq -s --argjson original "$(<$_PAYLOAD_FILE)" '$original * {texmap: .}' > $_PAYLOAD_FILE
fi

# Send the request
curl -sS -X POST http://$IP:$PORT/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"$_PAYLOAD_FILE" > $_TMP_DIR/$OUTPUT_FILE

# while response is empty, retry
ttl=20
while [ ! -s $_TMP_DIR/$OUTPUT_FILE ] && [ $ttl -gt 0 ]; do
    curl -sS -X POST http://$IP:$PORT/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"$_PAYLOAD_FILE" > $_TMP_DIR/$OUTPUT_FILE
    ttl=$((ttl-1))
    sleep 1
done


# Remove a formatting string (remove everything before the comma)
sed -i 's/^[^,]*,//' $_TMP_DIR/$OUTPUT_FILE

# Decode from Base64
base64 -d $_TMP_DIR/$OUTPUT_FILE > $_OUT_DIR/$OUTPUT_FILE

