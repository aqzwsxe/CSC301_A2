package ISCS_delete;

import Utils.ConfigReader;
import Utils.DatabaseManager;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.Executors;

/**
 *  ISCS acts as the central middleware of the architecture. It facilitates communication between
 *  the Order, User, and Product services
 */
public class deleted_ISCS {
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
        try {
            String dbUrl = ConfigReader.getDbUrl(dbConfig);
            DatabaseManager.setup(dbUrl);
            if (DatabaseManager.isDatabaseHealthy()) {
                System.out.println("ISCS: Verified DB connectivity at " + dbUrl);
            }
        } catch (Exception e) {
            System.err.println("ISCS: DB Connectivity Warning: " + e.getMessage());
        }

        // 2. Start Server on pc10 (142.1.46.13)
        int port = ConfigReader.getPort(configFile, "InterServiceCommunication", instanceIdx);

        // Binds to all interfaces so other lab machines can connect
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // The Handler must now perform dynamic routing based on config.json
        server.createContext("/", new deleted_ISCSHandler(configFile));

        // Virtual threads are perfect for a high-traffic bridge
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        System.out.println("ISCS Internal Bridge started on port " + port + " (pc10)");
        server.start();
    }
}
