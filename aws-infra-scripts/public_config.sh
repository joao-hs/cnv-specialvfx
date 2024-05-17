#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PRIVATE=$DIR/private
mkdir -p $PRIVATE
STATE=$DIR/state
mkdir -p $STATE

source $PRIVATE/config.sh

export REMOTE_REPO_ROOT="/home/ec2-user/cnv24-g03"

export INSTANCE_ID_FILE=$STATE/instance.id
export INSTANCE_DNS_FILE=$STATE/instance.dns

export LB_ID_FILE=$STATE/lb.id
export LB_DNS_FILE=$STATE/lb.dns

export IMAGE_ID_FILE=$STATE/image.id
export LB_IMAGE_ID_FILE=$STATE/lb-image.id