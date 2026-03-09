package ISCS;

import Utils.ConfigReader;
import Utils.DatabaseManager;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
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
    public static void main(String[] args) throws IOException, SQLException {
        if(args.length < 2){
            System.out.println("ISCS: Missing arguments. Usage: <config.json> <dbConfig.json> [index]");
            return;
        }

        String configFile = args[0];
        String dbConfig = args[1];
        int instanceIdx = (args.length > 2) ? Integer.parseInt(args[2]) : 0;

        // 1. Determine the URL
        File dbFile = new File(dbConfig);
        String url;
        if(dbFile.exists()){
            url = ConfigReader.getDbUrl(dbConfig);
            System.out.println("Using DB URL from config: " + url);
        } else {
            url = "jdbc:postgresql://142.1.114.76:5432/mydb";
            System.out.println("dbConfig not found. Defaulting to: " + url);
        }

        // 2. Initialize Database ONCE
        try {
            DatabaseManager.setup(url);
            if(!DatabaseManager.isDatabaseHealthy()){
                System.err.println("ISCS: Database at " + url + " is unreachable.");
                return;
            }
            DatabaseManager.initializeTables();
            System.out.println("ISCS: Database connection verified and tables initialized.");
        } catch (Exception e) {
            System.err.println("ISCS: Critical failure during DB setup: " + e.getMessage());
            return;
        }

        // 3. Start Server
        int port = ConfigReader.getPort(configFile, "InterServiceCommunication", instanceIdx);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ISCSHandler(configFile));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        System.out.println("ISCS Service started on port " + port);
        server.start();
    }
}
