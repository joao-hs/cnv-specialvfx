#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

# Step 1: delete auto scaling group.
aws autoscaling delete-auto-scaling-group \
	--auto-scaling-group-name CNV-AutoScalingGroup \
	--force-delete

# Step 2: delete launch configuration.
aws autoscaling delete-launch-configuration \
	--launch-configuration-name CNV-LaunchConfiguration

# Step 3: delete load balancer.
aws elb delete-load-balancer \
	--load-balancer-name CNV-LoadBalancer

rm $STATE/infra.ready