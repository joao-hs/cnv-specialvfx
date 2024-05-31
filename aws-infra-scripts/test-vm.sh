#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat $INSTANCE_ID_FILE)
aws ec2 reboot-instances --instance-ids $(cat $LB_ID_FILE)
echo "Rebooting instance to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 8000 to become available.
INSTANCE_DNS=$(cat $INSTANCE_DNS_FILE)
while ! nc -z $INSTANCE_DNS 8000; do
	echo "Waiting for $INSTANCE_DNS:8000..."
	sleep 0.5
done

# Smoke test.
echo "Smoke testing..."
$DIR/../scripts/smoke_test.sh --ip $INSTANCE_DNS --port 8000

LB_DNS=$(cat $LB_DNS_FILE)
while ! nc -z $LB_DNS 8000; do
	echo "Waiting for $LB_DNS:8000..."
	sleep 0.5
done

# echo "Smoke testing LB..."
$DIR/../scripts/smoke_test.sh --ip $LB_DNS --port 8000
