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
    public static int getPort(String configFile, String serviceName) throws IOException {
        String content = Files.readString(Paths.get(configFile));
        // Find the start of the service block
        int serviceIndex = content.indexOf("\""+serviceName+"\"");
        if(serviceIndex==-1){
            throw new RuntimeException("The service is not found");
        }
        // Find the port key inside that block
        //1: string; 2: From index
        int portKeyIndex = content.indexOf("\"port\"", serviceIndex);

        int colonIndex = content.indexOf(":", portKeyIndex);
        int commaIndex = content.indexOf(",",colonIndex);
        if(commaIndex==-1){
            commaIndex = content.indexOf("}",colonIndex);
        }

        String portValue = content.substring(colonIndex+1,commaIndex).trim();
        return Integer.parseInt(portValue);
    }

    /**
     * Parses the specified configuration file to retrieve the IP address for a given service.
     * @param configFile The path to the JSON configuration file
     * @param serviceName The name of the service
     * @return The IP address as a string
     * @throws IOException If the file cannot be read
     */
    public static String getIp(String configFile, String serviceName) throws IOException {
        String content = Files.readString(Paths.get(configFile));
        // Find the start of the service block
        int serviceIndex = content.indexOf("\""+serviceName+"\"");
        if(serviceIndex==-1){
            throw new RuntimeException("The service is not found");
        }
        int ipKeyIndex = content.indexOf("\"ip\"", serviceIndex);

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
        String testFile = "C:\\Users\\user\\Desktop\\All courses\\CSC301\\A2_component2\\CSC301_A2\\a2\\config.json"; // Ensure this file exists in your root directory

        try {
            System.out.println("=== Starting ConfigReader Test ===\n");

            // 1. Test Service Pools (L7 Load Balancer support)
            String[] services = {"UserService", "ProductService", "OrderService"};

            for (String service : services) {
                System.out.println("Testing Pool for: " + service);
                List<String> pool = getServicePool(testFile, service);

                if (pool.isEmpty()) {
                    System.out.println("Warning: Pool is empty.");
                } else {
                    System.out.println("Found " + pool.size() + " instances:");
                    for (String url : pool) {
                        System.out.println("      -> " + url);
                    }
                }
                System.out.println();
            }

            // 2. Test Single Value extraction (Legacy/Direct support)
            System.out.println("Testing Single Port Extraction (ISCS):");
            int iscsPort = getPort(testFile, "InterServiceCommunication");
            System.out.println("ISCS Port: " + iscsPort);

        } catch (IOException e) {
            System.err.println("Error: Could not read " + testFile + ". Make sure the file exists.");
        } catch (Exception e) {
            System.err.println("Parsing Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

