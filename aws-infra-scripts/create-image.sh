#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

# Step 1: launch a new VM instance.
$DIR/launch-vms.sh

# Step 2: install software in the VM instance.
$DIR/install-vm.sh

# Step 3: test VM instance.
$DIR/test-vm.sh

# Step 4.1: create VM image (AIM) - Worker.
INSTANCE_ID=$(cat $INSTANCE_ID_FILE)
aws ec2 create-image --instance-id $INSTANCE_ID --name CNV-Worker-Image | jq -r .ImageId > $IMAGE_ID_FILE
IMAGE_ID=$(cat $IMAGE_ID_FILE)
echo "New VM image with id $IMAGE_ID."

# Step 4.2: create VM image (AIM) - LB.
LB_ID=$(cat $LB_ID_FILE)
aws ec2 create-image --instance-id $LB_ID --name CNV-LB-Image | jq -r .ImageId > $LB_IMAGE_ID_FILE
LB_IMAGE_ID=$(cat $LB_IMAGE_ID_FILE)
echo "New VM image with id $LB_IMAGE_ID."

# Step 5: Wait for image to become available.
echo "Waiting for worker image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=CNV-Worker-Image
echo "Waiting for image to be ready... done! \o/"

# Step 5: Wait for image to become available.
echo "Waiting for LB image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=CNV-LB-Image
echo "Waiting for image to be ready... done! \o/"

# Step 6: terminate the vm instance.
aws ec2 terminate-instances --instance-ids $INSTANCE_ID
aws ec2 terminate-instances --instance-ids $LB_ID
