#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

mvn clean package

echo "Deleting previous lambda functions..."
aws lambda delete-function --function-name imageproc-lambda
aws lambda delete-function --function-name raytracer-lambda


echo "Creating imageproc-lambda"
aws lambda create-function \
        --function-name imageproc-lambda \
        --zip-file fileb://imageproc/target/imageproc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.imageproc.ImageProcessingHandler \
        --runtime java11 \
        --timeout 5 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

echo "Creating raytracer-lambda"
aws lambda create-function \
        --function-name raytracer-lambda \
        --zip-file fileb://raytracer/target/raytracer-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler \
        --runtime java11 \
        --timeout 40 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role
