package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.imageproc.BlurImageHandler;
import pt.ulisboa.tecnico.cnv.imageproc.EnhanceImageHandler;
import pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler;

public class WebServer {
    public static boolean LOCALHOST = false;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if ("--local".equals(args[0])) {
                WebServer.LOCALHOST = true;
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        MSSWriter retriever = MSSWriter.getInstance();
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/raytracer", new RaytracerHandler());
        server.createContext("/blurimage", new BlurImageHandler());
        server.createContext("/enhanceimage", new EnhanceImageHandler());
        server.createContext("/health", new HealthCheckHandler());
        server.start();
        retriever.start();
    }
}
