#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <image-id>"
    exit 1
fi

aws ec2 deregister-image --image-id $1
