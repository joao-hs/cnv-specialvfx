#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

if  [ -f "$IMAGE_ID_FILE" ]; then
	_WORKER_IMAGE_ID=$(cat $IMAGE_ID_FILE)
else
	_WORKER_IMAGE_ID="resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
fi

if [ -f "$LB_IMAGE_ID_FILE" ]; then
	_LB_IMAGE_ID=$(cat $LB_IMAGE_ID_FILE)
else
	_LB_IMAGE_ID="resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
fi


# Run new instance (worker)
aws ec2 run-instances \
  	--image-id $_WORKER_IMAGE_ID \
	--instance-type t2.micro \
	--tag-specifications "ResourceType=instance,Tags=[{Key=type,Value=worker}]" \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > $INSTANCE_ID_FILE
INSTANCE_ID=$(cat $INSTANCE_ID_FILE)
echo "New instance (worker) with id $INSTANCE_ID."

# Run new instance (load balancer)
aws ec2 run-instances \
	--image-id $_LB_IMAGE_ID \
	--instance-type t2.micro \
	--tag-specifications "ResourceType=instance,Tags=[{Key=type,Value=load-balancer}]" \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > $LB_ID_FILE
LB_ID=$(cat $LB_ID_FILE)
echo "New instance (load balancer) with id $LB_ID."

# Wait for instance to be running (worker).
aws ec2 wait instance-running --instance-ids $INSTANCE_ID
echo "New instance with id $INSTANCE_ID is now running."

# Extract DNS nane (worker).
aws ec2 describe-instances \
	--instance-ids $INSTANCE_ID | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > $INSTANCE_DNS_FILE
INSTANCE_DNS=$(cat $INSTANCE_DNS_FILE)
echo "New instance with id $INSTANCE_ID has address $INSTANCE_DNS."

# Wait for instance to be running (load balancer).
aws ec2 wait instance-running --instance-ids $LB_ID
echo "New instance with id $LB_ID is now running."

# Extract DNS nane.
aws ec2 describe-instances \
	--instance-ids $LB_ID | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > $LB_DNS_FILE
LB_DNS=$(cat $LB_DNS_FILE)
echo "New instance with id $LB_ID has address $LB_DNS."

# Wait for instance to have SSH ready.
while ! nc -z $INSTANCE_DNS 22; do
	echo "Waiting for $INSTANCE_DNS:22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $INSTANCE_ID is ready for SSH access."

# Wait for instance to have SSH ready.
while ! nc -z $LB_DNS 22; do
	echo "Waiting for $LB_DNS:22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $LB_ID is ready for SSH access."
