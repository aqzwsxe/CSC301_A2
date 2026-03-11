package Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing a network configuration file
 * This class provides methods to manually extract port numbers and IP addresses
 * for various microservices without the need for external JSON parsing libraries.
 *
 */
public class ConfigReader {


    public static List<String> getServicePool(String configFile, String serviceName) throws IOException {
        String content = Files.readString(Paths.get(configFile));
        List<String> pool = new ArrayList<>();
        int serviceIndex = content.indexOf("\"" + serviceName + "\"");
        if (serviceIndex == -1) {
            throw new RuntimeException("Service " + serviceName + " not found in config.");
        }

        int arrayStart = content.indexOf("[", serviceIndex);
        int arrayEnd = content.indexOf("]", arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) {
            throw new RuntimeException("Malformed config: Service pool must be an array [].");
        }

        String arrayContent = content.substring(arrayStart + 1, arrayEnd);

        int searchIdx = 0;
        while ((searchIdx = arrayContent.indexOf("{", searchIdx)) != -1) {
            int blockEnd = arrayContent.indexOf("}", searchIdx);
            if (blockEnd == -1) break;

            String instanceBlock = arrayContent.substring(searchIdx, blockEnd);
            String ip = extractStringValue(instanceBlock, "ip");
            int port = extractIntValue(instanceBlock, "port");
            pool.add("http://" + ip + ":" + port);
            searchIdx = blockEnd + 1;
        }
        return pool;
    }

    private  static String extractStringValue(String blcok1, String key){
        int keyIdx = blcok1.indexOf("\"" + key + "\"");
        int colonIdx = blcok1.indexOf(":", keyIdx);
        int firstQuote = blcok1.indexOf("\"", colonIdx);
        int secondQuote = blcok1.indexOf("\"", firstQuote + 1);
        return blcok1.substring(firstQuote + 1, secondQuote).trim();
    }

    private static int extractIntValue(String block1, String key) {
        int keyIdx = block1.indexOf("\"" + key + "\"");
        int colonIdx = block1.indexOf(":", keyIdx);
        int endIdx = block1.indexOf(",", colonIdx);
        if (endIdx == -1) endIdx = block1.length();

        String val = block1.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "").trim();
        return Integer.parseInt(val);
    }


    /**
     * Parses the specified configuration file to retrieve the port number for a given service.
     * @param configFile The path to the JSON configuration file
     * @param serviceName The name of the service (For example, "UserService")
     * @return The port number as an integer
     * @throws IOException If the file cannot be read
     */
    public static int getPort(String configFile, String serviceName, int instanceIndex) throws IOException {
        String content = Files.readString(Paths.get(configFile));
        int serviceIndex = content.indexOf("\"" + serviceName + "\"");
        if (serviceIndex == -1) throw new RuntimeException("Service not found");

        // Find the start of the array for this service
        int arrayStart = content.indexOf("[", serviceIndex);

        // Jump to the Nth '{' block
        int currentPos = arrayStart;
        for (int i = 0; i <= instanceIndex; i++) {
            currentPos = content.indexOf("{", currentPos + 1);
        }

        // Now find the "port" inside this specific block
        int portKeyIndex = content.indexOf("\"port\"", currentPos);
        int colonIndex = content.indexOf(":", portKeyIndex);

        // Look for either a comma or a closing brace
        int commaIndex = content.indexOf(",", colonIndex);
        int braceIndex = content.indexOf("}", colonIndex);

        // Determine the end of the number correctly
        int endIdx;
        if (commaIndex != -1 && (commaIndex < braceIndex)) {
            endIdx = commaIndex;
        } else {
            endIdx = braceIndex;
        }

        String portValue = content.substring(colonIndex + 1, endIdx).replaceAll("[^0-9]", "").trim();
        return Integer.parseInt(portValue);
    }

    /**
     * Parses the specified configuration file to retrieve the IP address for a given service.
     * @param configFile The path to the JSON configuration file
     * @param serviceName The name of the service
     * @return The IP address as a string
     * @throws IOException If the file cannot be read
     */
    public static String getIp(String configFile, String serviceName, int instanceIndex) throws IOException {
        String content = Files.readString(Paths.get(configFile));
        int serviceIndex = content.indexOf("\"" + serviceName + "\"");
        if (serviceIndex == -1) throw new RuntimeException("Service not found");

        int arrayStart = content.indexOf("[", serviceIndex);

        // Jump to the Nth '{' block
        int currentPos = arrayStart;
        for (int i = 0; i <= instanceIndex; i++) {
            currentPos = content.indexOf("{", currentPos + 1);
            if (currentPos == -1) return null; // Important for the while loop in getServiceCount
        }

        int ipKeyIndex = content.indexOf("\"ip\"", currentPos);
        int colonIndex = content.indexOf(":", ipKeyIndex);
        int firstQuote = content.indexOf("\"", colonIndex);
        int secondQuote = content.indexOf("\"", firstQuote + 1);

        return content.substring(firstQuote + 1, secondQuote);
    }


    public static String getDbUrl(String configFile) throws IOException {
        return getValue(configFile, "Database", "url");
    }

    public static String getDbUser(String dbConfig) throws IOException {
        return getValue(dbConfig, "Database", "user");
    }

    public static String getDbPassword(String dbConfig) throws IOException {
        return getValue(dbConfig, "Database", "pass");
    }

    private static String getValue(String dbConfig, String section, String key) throws IOException {
        String content = Files.readString(Paths.get(dbConfig));
        int sectionIndex = content.indexOf("\"" + section + "\"");
        if (sectionIndex == -1) return null;
        int keyIndex = content.indexOf("\"" + key + "\"", sectionIndex);
        if (keyIndex == -1) return null;
        int valueStart = content.indexOf("\"", keyIndex + ("\"" + key + "\"").length() + 1) + 1;
        int valueEnd = content.indexOf("\"", valueStart);
        return content.substring(valueStart, valueEnd);
    }

    public static void main(String[] args) {
        // 1. Use relative path or argument to work on both Windows and Lab Machines
        String testFile = (args.length > 0) ? args[0] : "C:\\Users\\user\\Desktop\\All courses\\CSC301\\A2_component2\\CSC301_A2\\a2\\config.json";

        try {
            System.out.println("=== Starting ConfigReader A2 Verification ===\n");
            System.out.println("Reading config from: " + testFile);

            // --- TEST 1: Service Pools (For the Load Balancer) ---
            String[] services = {"UserService", "ProductService", "OrderService"};
            for (String service : services) {
                System.out.print("Testing " + service + " Pool: ");
                List<String> pool = getServicePool(testFile, service);
                System.out.println("Found " + pool.size() + " instances.");
                // Print the first and last to verify range
                if (!pool.isEmpty()) {
                    System.out.println("   Start: " + pool.get(0));
                    System.out.println("   End:   " + pool.get(pool.size() - 1));
                }
            }

            System.out.println("\n--- TEST 2: Specific Instance Extraction ---");
            // This simulates what a single UserService instance does
            // It asks for index 0 (port 14001) or index 6 (port 14007)
            int firstUserPort = getPort(testFile, "UserService", 0);
            int lastUserPort = getPort(testFile, "UserService", 6);

            System.out.println("UserService[0] port: " + firstUserPort + " (Expected: 14001)");
            System.out.println("UserService[6] port: " + lastUserPort + " (Expected: 14007)");



        } catch (IOException e) {
            System.err.println("CRITICAL: File not found at " + testFile);
        } catch (Exception e) {
            System.err.println("PARSING ERROR: " + e.getMessage());
            // This will tell you if your substring/indexOf logic is still hitting a "}"
            e.printStackTrace();
        }
    }
    public static int getServiceCount(String configFile, String serviceName) {
        int count = 0;
        while (true) {
            try {
                // Attempt to get the IP for index 'count'
                String test = getIp(configFile, serviceName, count);
                if (test == null || test.isEmpty()) {
                    break;
                }
                count++;
            } catch (Exception e) {
                // Once we hit an index that doesn't exist, we've found the limit
                break;
            }
        }
        return count;
    }

}

