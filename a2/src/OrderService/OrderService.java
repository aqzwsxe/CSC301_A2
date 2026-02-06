package OrderService;

import Utils.ConfigReader;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * The OrderService class initializes and manages the HTTP server response for
 * processing order transactions. It serves as the entry point for order creation,
 * validation, and retrieval by using microservices architecture.
 *
 */
public class OrderService {
    /**
     * A database to store all the orders
     */
    public static final Map<Integer, Order> orderDatabase = new ConcurrentHashMap<>();

    /**
     * The Main entry point for Order Service.
     * It reads network configuration (config.json), binds the server to the specified port,
     * and maps the root context to the OrderHandler.
     * @param args Command line arguments. Expects the path to the configuration JSON as the first element.
     * @throws IOException If the server cannot be initialized or the configuration file is inaccessible
     */
    public static void main(String[] args) throws IOException {
        if(args.length < 1){
            System.out.println("The config file has some issues");
            return;
        }
        String configFile = args[0];
        int port = ConfigReader.getPort(configFile, "OrderService");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new OrderHandler(configFile));
        server.setExecutor(Executors.newFixedThreadPool(10));
        System.out.println("Order Service started on port " + port);
        server.start();
    }


}
