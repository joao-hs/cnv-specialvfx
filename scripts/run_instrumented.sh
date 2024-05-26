#!/bin/bash

# Command Line arguments:
# Mandatory:
#   $1: Tool to be used for instrumentation
# Optional:
#   --output: Output path for the instrumented classes. Default is "instrumented/target/classes"
#   --compile: Compiles the maven modules before running the webserver

# Verify if the mandatory arguments are provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <tool> [--output <output_path>] [--compile]"
    exit 1
fi

# Assert that the tool is the first argument
if [ "${1:0:2}" == "--" ]; then
    echo "The tool should be the first argument"
    exit 1
fi

# Default values
TOOL="$1"
OUTPUT_PATH="instrumented/target/classes"
_COMPILE="false"

# Parse the command line arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --output)
            OUTPUT_PATH="$2"
            shift
            ;;
        --compile)
            _COMPILE="true"
            ;;
        *)
            ;;
    esac
    shift
done

# -------------------------------------

_PACKAGES_TO_INSTRUMENT="pt.ulisboa.tecnico.cnv.raytracer"

_JAVAAGENT="javassist-wrapper/target/javassist-wrapper-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

_WEBSERVER_JAR="webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
_WEBSERVER_CLASS="pt.ulisboa.tecnico.cnv.webserver.WebServer"

# -------------------------------------

# Step 1: Clean and build all maven modules
rm -rf $OUTPUT_PATH # always clean instrumented classes
if [ "$_COMPILE" == "true" ]; then
    mvn clean package
fi

# Step 2: Run
java -cp $_WEBSERVER_JAR -javaagent:$_JAVAAGENT=$TOOL:$_PACKAGES_TO_INSTRUMENT:$OUTPUT_PATH $_WEBSERVER_CLASS
