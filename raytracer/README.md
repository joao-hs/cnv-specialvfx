## Ray Tracing

This project contains functionality to render 3D scenes with ray tracing.

### How to build

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

### How to run locally

To run RayTracer locally in CLI, execute this command:

```
java -cp target/raytracer-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.raytracer.Main <input-scene-file.txt> <output-file.bmp> 400 300 400 300 0 0 [-tm=<texmap-file.bmp>] [-aa]
```

Arguments in brackets are optional.

Some input scene files (the .txt ones) require you to provide a texture file with the `[-tm=<texmap-file.bmp>]` argument. This texture file should be a valid image.

You can find some input scene files in the `resources` directory.

For more details regarding the arguments just run this command:

```
java -cp target/raytracer-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.raytracer.Main
```



### Additional remarks
The scene description file should have the following format:
```
<eye: Point>
<center: Point>
<up: Vector>
<fov: Double>

<total number of lights: Integer>
<ambient ligth: Light>
<light 2: Light>
...
<light N: Light>

<number of pigments: Integer>
<pigment 1: Pigment>
...
<pigment N: Pigment>

<number of surface finishes: Integer>
<surface finish 1: SurfaceFinish>
...
<surface finish N: SurfaceFinish>

<number of shapes: Integer>
<shape 1: Shape>
...
<shape N: Shape>
```

Where:

```
Point = <x: Double> <y: Double> <z: Double>

Vector = <x: Double> <y: Double> <z: Double>

Color = <r: Float> <g: Float> <b: Float>

Light = <location: Point> <color: Color> <a: float> <b: float> <c: float>

Pigment = 
    solid <color: Color> | 
    checker <color1: Color> <color2: Color> <scale: Double> | 
    gradient <origin: Point> <vector: Vector> <start: Color> <end: Color>
    texmap <_: String> <bmpTexmap: byte[]> <sa: Double> <sb: Double> <sc: Double> <sd: Double> <ta: Double> <tb: Double> <tc: Double> <td: Double>

SurfaceFinish = <pigNum: Integer> <finishNum: Integer> <_: shapeProperties>

Polygon = <point1: Point> <point2: Point> <point3: Point> <point4: Point>

ShapeProperties = 
    sphere <center: Point> <radius: Double> |
    plane <a: Point> <b: Point> <c: Point> <d: Point> | 
    cylinder <base: Point> <axis: Vector> <radius: Double> |
    cone <apex: Point> <axis: Vector> <radius: Double> |
    disc <center: Point> <normal: Vector> <radius: Double> |
    polyhedron <number of faces: Integer> <face1: Polygon> ... <faceN: Polygon> |
    triangle <point1: Point> <point2: Point> <point3: Point> |
    parallelogram <point1: Point> <point2: Point> <point3: Point> |
    bezier <point1: Point> ... <point16: Point>
```
