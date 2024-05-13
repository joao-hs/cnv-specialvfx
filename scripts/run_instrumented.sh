#!/bin/bash

# Command Line arguments:
# Mandatory:
#   $1: Tool to be used for instrumentation
# Optional:
#   --output: Output path for the instrumented classes. Default is "instrumented"

# Verify if the mandatory arguments are provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <tool> [--output <output_path>]"
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

# Parse the command line arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --output)
            OUTPUT_PATH="$2"
            shift
            ;;
        *)
            break
            ;;
    esac
    shift
done

# -------------------------------------

_PACKAGES_TO_INSTRUMENT="boofcv.alg.enhance"                                            # Dependency of imageproc: EnhanceImageOps.equalizeLocal(...)
_PACKAGES_TO_INSTRUMENT="$_PACKAGES_TO_INSTRUMENT,boofcv.alg.filter.blur"               # Dependency of imageproc: GBlurImageOps.guassian(...)
_PACKAGES_TO_INSTRUMENT="$_PACKAGES_TO_INSTRUMENT,boofcv.io.image"                      # Dependency of imageproc: ConvertBufferedImage.convertFrom(...)/ConvertBufferedImage.convertTo(...)/UtilImageIO.loadImageNotNull(...)/UtilImageIO.saveImage(...)
_PACKAGES_TO_INSTRUMENT="$_PACKAGES_TO_INSTRUMENT,pt.ulisboa.tecnico.cnv.imageproc"
# imageproc has more dependencies, but those probably do not contribute to the overall complexity of the program
_PACKAGES_TO_INSTRUMENT="$_PACKAGES_TO_INSTRUMENT,pt.ulisboa.tecnico.cnv.raytracer"
# raytracer has more dependencies, but those probably do not contribute to the overall complexity of the program

_JAVAAGENT="javassist-wrapper/target/javassist-wrapper-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

_WEBSERVER_JAR="webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
_WEBSERVER_CLASS="pt.ulisboa.tecnico.cnv.webserver.WebServer"

# -------------------------------------

# Step 1: Clean and build all maven modules
mvn clean package

# Step 2: Run
java -cp $_WEBSERVER_JAR -javaagent:$_JAVAAGENT=$TOOL:$_PACKAGES_TO_INSTRUMENT:$OUTPUT_PATH $_WEBSERVER_CLASS
