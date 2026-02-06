package ISCS;

import Utils.ConfigReader;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 *  ISCS acts as the central middleware of the architecture. It facilitates communication between
 *  the Order, User, and Product services
 */
public class ISCS {
    /**
     * The entry point for the ISCS service. Initializes the HTTP server, sets up the request contexts,
     * and configures an executor to handle concurrent service requests
     * @param args command-line arguments. The first argument must be the path to the file
     * @throws IOException If the server cannot be created or the port is unavailable
     */
    public static void main(String[] args) throws IOException {
        if(args.length < 1){
            System.out.println("ISCS: cannot find the config.json");
            return;
        }

        String configFile = args[0];
        int port = ConfigReader.getPort(configFile,"InterServiceCommunication");
        // Uses a server to listen to the order service
        // It waits for an incoming connection, parses the request, and sends back a response
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // This line tells the server which path prefix should trigger handler
        server.createContext("/", new ISCSHandler(configFile));
        server.setExecutor(Executors.newFixedThreadPool(10));
        System.out.println("ISCS Service started on port "+ port);
        server.start();
    }
}
