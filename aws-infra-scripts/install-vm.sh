#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

INSTANCE_DNS=$(cat $INSTANCE_DNS_FILE)
LB_DNS=$(cat $LB_DNS_FILE)

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd

# Install maven.
cmd="curl \"https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip\" -o \"apache-maven-3.9.6-bin.zip\"; unzip apache-maven-3.9.6-bin.zip; rm apache-maven-3.9.6-bin.zip;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd

# Install aws-java-sdk. This might take a couple of minutes.
cmd="curl \"http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip\" -o \"aws-java-sdk.zip\"; unzip -qq aws-java-sdk.zip; rm aws-java-sdk.zip;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd

# Copy local repository to instance.
$DIR/zip_repo.sh $STATE/repo.zip
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $STATE/repo.zip ec2-user@$INSTANCE_DNS:repo.zip
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $STATE/repo.zip ec2-user@$LB_DNS:repo.zip
cmd="unzip repo.zip -d /home/ec2-user; rm repo.zip;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd

# Build the code.
cmd="export MAVEN_OPTS='-Xmx512m'; $AWS_MVN_PATH clean package -f $REMOTE_REPO_ROOT/pom.xml; mkdir $REMOTE_REPO_ROOT/logs; chmod +x $REMOTE_REPO_ROOT/scripts/*.sh"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd

# Setup rc.local files.
worker_script="cd $REMOTE_REPO_ROOT; ./scripts/run_instrumented.sh SpecialVFXTool > logs/\\\"\\\$(date +%Y%m%d%H%M%S)\\\"-worker.log 2> logs/\\\"\\\$(date +%Y%m%d%H%M%S)\\\"-worker.err"
cmd="echo \"$worker_script\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$INSTANCE_DNS $cmd

lb_script="cd $REMOTE_REPO_ROOT; ./scripts/run_lb.sh > logs/\\\"\\\$(date +%Y%m%d%H%M%S)\\\"-lb.log 2> logs/\\\"\\\$(date +%Y%m%d%H%M%S)\\\"-lb.err"
cmd="echo \"$lb_script\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$LB_DNS $cmd