#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

export INSTANCE_AMI_ID=$(cat $IMAGE_ID_FILE)
export INSTANCE_KEY_NAME=$AWS_KEYPAIR_NAME
export INSTANCE_SEC_GROUP_ID=$AWS_SECURITY_GROUP