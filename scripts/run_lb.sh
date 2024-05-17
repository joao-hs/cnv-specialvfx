#!/bin/bash

# Command Line arguments:
# Optional:
#   --compile: Compiles the maven modules before running the webserver

# Verify if the mandatory arguments are provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 [--compile]"
    exit 1
fi

# Default values
_COMPILE="false"

# Parse the command line arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --compile)
            _COMPILE="true"
            ;;
        *)
            ;;
    esac
    shift
done

# -------------------------------------

_LB_JAR="load-balancer/target/load-balancer-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
_LB_CLASS="pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer"

# -------------------------------------

# Step 1: Clean and build all maven modules
if [ "$_COMPILE" == "true" ]; then
    mvn clean package
fi

# Step 2: Run
java -cp $_LB_JAR $_LB_CLASS
