## SpecialVFX@Cloud

This project contains three sub-projects:

1. `raytracer` - the Ray Tracing workload
2. `imageproc` - the BlurImage and EnhanceImage workloads
3. `webserver` - the web server exposing the functionality of the workloads
4. `javassist-wrapper` - a wrapper around the workloads to retrieve metrics about the execution of the workloads

Refer to the `README.md` files of the sub-projects to get more details about each specific sub-project.

### How to build everything

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

### Architecture
The javassist tool stores the metrics in a map, associated with a unique identifier for each request.
The web server MSSWriter class is responsible for fetching this map from time to time and writing the metrics to a file (or any other output stream). Each fetch to this map will remove the fetched metrics from the map to preserve memory.