#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <zip-file>"
    exit 1
fi

OLD_DIR=$(pwd)

$DIR/../scripts/clean.sh
TMP_DIR=$(mktemp -d)

mkdir -p $TMP_DIR/cnv24-g03/aws-infra-scripts
cp -r $DIR $TMP_DIR/cnv24-g03
cp -r $DIR/../imageproc $TMP_DIR/cnv24-g03
cp -r $DIR/../raytracer $TMP_DIR/cnv24-g03
cp -r $DIR/../webserver $TMP_DIR/cnv24-g03
cp -r $DIR/../javassist-wrapper $TMP_DIR/cnv24-g03
cp -r $DIR/../load-balancer $TMP_DIR/cnv24-g03
cp -r $DIR/../scripts $TMP_DIR/cnv24-g03
cp $DIR/../pom.xml $TMP_DIR/cnv24-g03
cd $TMP_DIR
zip -r $1 cnv24-g03
cp $1 $OLD_DIR

cd $OLD_DIR

mv $1 $STATE

rm -rf $TMP_DIR

cd $OLD_DIR