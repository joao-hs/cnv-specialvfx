#!/bin/bash

BEFORE_DIR=$(pwd)
REPO_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null && pwd )"

cd $REPO_ROOT
mvn clean
rm -rf $REPO_ROOT/instrumented/target/classes/*
rm -rf $REPO_ROOT/scripts/tmp/*

cd $BEFORE_DIR