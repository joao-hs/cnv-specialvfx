## SpecialVFX@Cloud

This project contains three sub-projects:

1. `raytracer` - the Ray Tracing workload
2. `imageproc` - the BlurImage and EnhanceImage workloads
3. `webserver` - the web server exposing the functionality of the workloads
4. `javassist-wrapper` - a wrapper around the workloads to retrieve metrics about the execution of the workloads
5. `load-balancer` - a load balancer to distribute the requests between the web servers (auto-scaler included)

Refer to the `README.md` files of the sub-projects to get more details about each specific sub-project.

### How to build everything

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

### Architecture
The `SpecialVFXTool` is a Javassist Tool that is used to write instrumentation metrics in the Metric Storage System (MSS). These will be read by each `CostEstimator` to better fit the regression model.
The `FeatureExtractor` is responsible for looking at the request parameters and extracting relevant information that may indicate the cost of the request (a priori to its execution)
The `Supervisor` is responsible for checking the health and real-time usage of the workers. With this information, it will rearrange the workers in theoretical worker pools, that will be useful in the balancing algorithm.