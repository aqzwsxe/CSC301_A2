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
        int index1 = content.indexOf("}",colonIndex);
        String ip = content.substring(colonIndex+1,index1).trim();
        return ip;
    }


//    static void main() throws IOException {
////        System.out.println(getPort("config.json", "UserService"));
//        System.out.println(getIp("config.json", "UserService"));
//    }
}

