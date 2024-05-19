# AWS Automatic Infrastructure Setup Scripts

This directory has a set of scripts that can be used to automatically setup AWS infrastructure.

## Prerequisites

1. AWS CLI should be installed and configured. Check [here](https://gitlab.rnl.tecnico.ulisboa.pt/cnv/cnv24/-/tree/master/labs/lab-aws#using-the-aws-command-line-interface)

2. `jq` tool should be installed. Check [here](https://gitlab.rnl.tecnico.ulisboa.pt/cnv/cnv24/-/tree/master/labs/lab-aws#using-the-aws-command-line-interface)

3. There should already be created an AWS account, a key pair, and a security group. Check [here](https://gitlab.rnl.tecnico.ulisboa.pt/cnv/cnv24/-/blob/master/labs/lab-aws/res/cnv-aws-guide-23-24.pdf)

4. Set the permissions to your key file to 400, for example:
```bash
chmod 400 <path-to-repo>/aws-infra-scripts/private/cnv-instance.pem
```

5. A `config.sh` file should be created in the directory `aws-infra-scripts/private`:
```bash
#!/bin/bash
export AWS_DEFAULT_REGION="eu-west-3" # Paris
export AWS_ACCOUNT_ID= # is available on your AWS Web Console > IAM Dashboard
export AWS_ACCESS_KEY_ID= # is available on your AWS Web Console > Security Credentials (section available by clicking your name on the top right);
export AWS_SECRET_ACCESS_KEY= # (same as above)
export AWS_EC2_SSH_KEYPAR_PATH= # is the path to the pem file, e.g., <path-to-repo>/aws-infra-scripts/private/cnv-instance.pem
export AWS_SECURITY_GROUP= # is the name of the security group created in the prerequisites
export AWS_KEYPAIR_NAME= # is the registered name of the key pair created in the prerequisites 
```

## Use cases

1. **Create required AMIs**: run the `create-image.sh` script. It will use the local repository, beaware of local changes. Cleaning is automatic. Only done once per version.

2. **Launch deployment**: run the `launch-deployment.sh` script. It will launch the deployment with the required instances.

3. **Terminate deployment**: run the `terminate-deployment.sh` script. It will terminate the deployment. VM storage will be lost. (Don't forget to terminate the deployment after using it, to avoid unnecessary costs)

4. **Deregister AMI**: run the `deregister-image.sh <image-id>` script. It will deregister the AMI created in the first step. You can find the image ids in `aws-infra-scripts/state/*.id` files.