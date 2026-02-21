package Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class for parsing a network configuration file
 * This class provides methods to manually extract port numbers and IP addresses
 * for various microservices without the need for external JSON parsing libraries.
 *
 */
public class ConfigReader {
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
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dbConfig)));
        int sectionIndex = content.indexOf("\"" + section + "\"");
        if (sectionIndex == -1) return null;
        int keyIndex = content.indexOf("\"" + key + "\"", sectionIndex);
        if (keyIndex == -1) return null;
        int valueStart = content.indexOf("\"", keyIndex + ("\"" + key + "\"").length() + 1) + 1;
        int valueEnd = content.indexOf("\"", valueStart);
        return content.substring(valueStart, valueEnd);
    }

//    static void main() throws IOException {
////        System.out.println(getPort("config.json", "UserService"));
//        System.out.println(getIp("config.json", "UserService"));
//    }
}

