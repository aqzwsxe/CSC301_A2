package OrderService;

import Utils.ConfigReader;
import Utils.DatabaseManager;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.sql.SQLException;
import java.util.HashMap;
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
//    public static Map<Integer, Order> orderDatabase = new ConcurrentHashMap<>();

    /**
     * The Main entry point for Order Service.
     * It reads network configuration (config.json), binds the server to the specified port,
     * and maps the root context to the OrderHandler.
     * @param args Command line arguments. Expects the path to the configuration JSON as the first element.
     * @throws IOException If the server cannot be initialized or the configuration file is inaccessible
     */
    public static void main(String[] args) throws IOException, SQLException {
        if(args.length < 1){
            System.out.println("The config file has some issues");
            return;
        }

        String configFile = args[0];
        String dbConfig = (args.length > 1) ? args[1] : "dbConfig.json";
        File dbFile = new File(dbConfig);
        try {
            if(dbFile.exists()){
            DatabaseManager.setup(ConfigReader.getDbUrl(dbConfig)
                    );}
            else {
                DatabaseManager.setup("jdbc:sqlite:service_data.db");
                System.out.println("Config file not found. Using default Docker credentials.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            DatabaseManager.initializeTables();
            System.out.println("System ready and database initialized");
        }catch (SQLException e){
            System.err.println("Failed to initialize tables: " + e.getMessage());
            return;
        }

        int port = ConfigReader.getPort(configFile, "OrderService");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new OrderHandler(configFile));
        server.setExecutor(Executors.newFixedThreadPool(10));
        System.out.println("Order Service started on port " + port);
        server.start();
    }


}
