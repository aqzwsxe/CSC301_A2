package UserService;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import Utils.ConfigReader;

/**
 * The UserService class is the entry point for the user management microservice.
 * It initializes an HTTP server dedicated to handling user related operations
 * such as creation, retrieval, updates and deletions
 */
public class UserService {
//    Use ConcurrentHashMap instead rather than HashMap, because server is multi-threaded
//    create an interface or super-class, the map is the placeholder; when receive the new requirement; create
    // the actual subclass for the database
    /**
     * This is the database to store all the Users
     */
    public static Map<Integer, User> userDatabase = new ConcurrentHashMap<>();

    /**
     * The main execution point that starts the User microservice
     * This method performs the following operations
     * 1: reads network configuration from the provided JSON file path
     * 2: Binds the HttpServer to the configured port.
     * 3: Registers a context listener for all URLs starting with
     * @param args The command line arguments; expects the config file path at index 0.
     * @throws IOException If the server cannot be started or bound to the network port.
     */
    public static void main(String[] args) throws IOException {
        // Get port from config
//        int port = Utils.ConfigReader.getPort(args[0], "UserService");
        if(args.length < 1){
            System.err.println("Miss the config.json");
            System.exit(1);
        }

        String configPath = args[0];
        try{
            int port = ConfigReader.getPort(configPath, "UserService");
            // new InetSocketAddress(port): combine the IP address and the port number
            // Don't really need to specify the ip address
            HttpServer server = HttpServer.create(new InetSocketAddress(port),0);
            // Handle everything start with /user.
            // routing logic of the microservice. Acts as a filter;
            // Whenever an Http request comes in with a path that starts with /user, hand
            // it over to the UserHandler object to deal with it.
            server.createContext("/user", new UserHandler());
            // Determines how the UserServer handle concurrent requests;
            // pass Executors.newFixedThreadPool(21) to it; it now maintains a pool of 21 dedicated worker threads
            // it now maintains a pool of 21 dedicated worker thread
            // Executor:
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            System.out.println("UserService is listening on port " + port);
        } catch (IOException e){
            System.err.println("Failed to start server: " + e.getMessage());

        }catch (Exception e){
            System.err.println("Error reading config: " + e.getMessage());
        }

    }
}
