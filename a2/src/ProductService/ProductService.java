package ProductService;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import Utils.ConfigReader;

/**
 * The ProductService class serves as the host for the inventory management Product microservice.
 * It initializes an HTTP server to manage inventory, including product creation, updates, and stock levels.
 */
public class ProductService {
    /**
     * This is the database to store all the products
     */
    public static Map<Integer, Product> productDatabase = new ConcurrentHashMap<>();

    /**
     * The main execution point for the Product microservice
     * The method performs the following initialization steps:
     * 1: Validates command-line arguments for configuration files
     * 2: Parses network settings using the ConfigReader
     * 3: Initializes the HttpServer and binds it to the service port
     * 4: Registers a context listener for all URLs starting with /product
     * @param args The command line arguments. Expects a path to a JSON configuration file
     * @throws IOException If the server cannot be started or bound to the network port
     */
    public static void main(String[] args) throws IOException {
        if(args.length < 1){
            System.err.println("Miss the config.json");
            System.exit(1);
        }

        String configPath = args[0];
        try{
            int port = ConfigReader.getPort(configPath, "ProductService");
            // new InetSocketAddress(port): combine the IP address and the port number
            // Don't really need to specify the ip address
            HttpServer server = HttpServer.create(new InetSocketAddress(port),0);
            // Handle everything start with /product.
            // routing logic of the microservice. Acts as a filter;
            // Whenever an Http request comes in with a path that starts with /product, hand
            // it over to the ProductHandler object to deal with it.
            server.createContext("/product", new ProductHandler());
            // Determines how the ProductServer handle concurrent requests;
            // Executor: decide
            server.setExecutor(null);
            server.start();
            System.out.println("ProductService is listening on port " + port);
        } catch (IOException e){
            System.err.println("Failed to start server: " + e.getMessage());

        }catch (Exception e){
            System.err.println("Error reading config: " + e.getMessage());
        }

    }
}
