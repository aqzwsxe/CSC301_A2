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
            // 2. Database Initialization
            initializeDatabase(dbConfig);

            // 3. Server Setup
            int instanceIdx = (args.length > 2) ? Integer.parseInt(args[2]) : 0;

            // Use the NEW 3-argument version of getPort
            int port = ConfigReader.getPort(configFile, "OrderService", instanceIdx);

            // Use backlog 1000 to prevent "Connection Refused" during bursts
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 1000);
            System.out.println("Inside the main class of the OrderService; the config file is: " + configFile);
            server.createContext("/", new OrderHandler(configFile));

            // Virtual Threads: Essential for 4,000 req/s concurrency
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // 4. Graceful Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down Order Service...");
                server.stop(0);
            }));

            server.start();
            System.out.println("Order Service is LIVE at http://localhost:" + port);
        }catch (Exception e){
            System.err.println("Critical Failure: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }


    private static void initializeDatabase(String dbConfig) throws IOException, SQLException {
        File dbFile = new File(dbConfig);
        String url;

        if(dbFile.exists()){
            url = ConfigReader.getDbUrl(dbConfig);
            System.out.println("Using DB URL from config: " + url);
        }else {
            url = "jdbc:postgresql://142.1.114.76:5432/mydb";
            System.out.println("dbConfig not found. Defaulting to Remote PostgreSQL Instance");
        }
        DatabaseManager.setup(url);
        if(! DatabaseManager.isDatabaseHealthy()){
            throw new RuntimeException("Critical Remote Database at " + url + " is unreachable");
        }
        DatabaseManager.initializeTables();
        System.out.println("Remote Database initialized.");
    }
}
