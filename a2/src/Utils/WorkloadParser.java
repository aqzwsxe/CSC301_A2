package Utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * WorkloadParser serves as the client-side simulation tool for the microservices
 * It reads service commands from a workload text file, translates them into JSON-formatted
 * HTTP requests, and dispatches them to the OrderService (act as a gateway)
 */
public class WorkloadParser {
    /**
     * The base URL of the Order Service gateway, initialized from config.json
     */
    private static String orderUrl;
    /**
     * Shared HTTP client used to dispatch requests to the gateway
     */
    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * Entry point for the workload simulation
     * Parse the command line for the workload file path, initializes network coordinates, and enters a processing
     * loop to execute commands line by line
     * @param args Command line arguments. arg[0] should be the path to the workload.txt file
     * @throws IOException If the workload file or configuration cannot read
     * @throws InterruptedException If the HTTP request process is interrupted
     * @throws URISyntaxException If the generated URLs are invalid
     */
    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        // when run the program via terminal through runme.sh, pass the path to the text file
        // java WorkloadParser workload3u20c.txt
        // args[0] becomes workload3u20c.txt
        File file = new File(args[0]);
        // Use the scanner to read the content of the workload file
        Scanner sc = new Scanner(file, StandardCharsets.UTF_8);

        String configPath = "config.json";
        int port = ConfigReader.getPort(configPath, "OrderService");
        String ip = ConfigReader.getIp(configPath, "OrderService");
        ip = ip.replace("\"","").trim();
        orderUrl = "http://" + ip + ":" + port;
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            if(line.startsWith("[")){
                line = line.substring(line.indexOf("]")+1).trim();
            }
            String[] parts = line.split("\\s+"); // split by any whitespace
            String service = parts[0]; // USER, PRODUCT, or ORDER
            String command = parts[1]; // create get, update, delete or place order

            // Although all the request will be sent to the OrderService first
            // Still need to differ them here, because we need to build the path
            if (service.equals("USER")) {
                handleUser(command,parts);
            } else if (service.equals("ORDER")) {
                handleOrder(parts);
            } else if (service.equals("PRODUCT")) {
                handleProduct(command,parts);
            }
        }
    }


    /**
     * Parses USER commands (create, get, update and delete) and generates the appropriate
     * JSON payload for the gateway.
     * @param command The specific action to perform (for example, 'create')
     * @param parts The full array of command arguments from the workload file
     * @throws IOException If the request dispatch fails
     * @throws URISyntaxException If the URI is invalid
     * @throws InterruptedException If the thread is interrupted
     */
    public static void handleUser(String command, String[] parts) throws IOException, URISyntaxException, InterruptedException {
        //build JSON and send to Order Service
//        System.out.println("The handleUser method is called inside the parser");
        if (command.equals("get")) {
            sendGetRequest("/user/"+parts[2]);
        } else {
            String jsonBody = "";
            if (command.equals("create")) {
                // A valid create user process need
                if(parts.length!=6){
//                    System.out.println("The length of the creat user command is less than 6");
                    jsonBody = String.format("{\"command\":\"%s\",\"id\":%s,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                            command, "invalid-info", "invalid-info", "invalid-info", "invalid-info");
                }
                else{
                    jsonBody = String.format("{\"command\":\"%s\",\"id\":%s,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                            command, parts[2], parts[3], parts[4], parts[5]);
                }
//                System.out.println("Running the sendRequest");
                sendPostRequest("/user", jsonBody);
            }else if (command.equals("delete")){
                    if(parts.length != 6){
                        jsonBody = String.format("{\"command\":\"%s\",\"id\":%s,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                                command, "invalid-info", "invalid-info", "invalid-info", "invalid-info");
                    }
                    else{
                        jsonBody = String.format("{\"command\":\"%s\",\"id\":%s,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                                command, parts[2], parts[3], parts[4], parts[5]);
                    }
                sendPostRequest("/user", jsonBody);
            }
            else if (command.equals("update")) {
                try {
                    // If the id is invalid, return 400
                    Integer.parseInt(parts[2]);
                    StringBuilder json_builder = new StringBuilder(String.format("{\"command\":\"update\",\"id\":%s", parts[2]));
                    for(int i = 3; i< parts.length; i++){
                        String[] key_val = parts[i].split(":");
                        if(key_val.length == 2){
                            json_builder.append(String.format(",\"%s\":\"%s\"", key_val[0], key_val[1]));
                        }
                    }
                    json_builder.append("}");
                    sendPostRequest("/user", json_builder.toString());
                } catch (NumberFormatException e) {
                    // If the ID does not exist or invalid, return
                    StringBuilder json_builder = new StringBuilder(String.format("{\"command\":\"update\",\"id\":%s", "invalid-info"));
                    json_builder.append("}");
                    sendPostRequest("/user", json_builder.toString());
                }

            }

        }
    }

    /**
     * Processes order specific workload commands and maps them to appropriate HTTP methods
     * The method supports the following command structures
     * 1: Triggers a POST request to create a new order (product_id, user_id and quantity)
     * 2: Triggers a GET request to retrieve existing order details
     * 3: Triggers a DELETE request to remove an order from the system
     * @param parts An array of strings parsed from the workload file line.
     *              Expected format: [service, action, arg1, arg2, arg3]
     * @throws IOException If the connection to the OrderService fails
     * @throws URISyntaxException If the constructed URI is malformed
     * @throws InterruptedException If the thread is interrupted while waiting for a response
     */
    public static void handleOrder(String[] parts) throws IOException, URISyntaxException, InterruptedException {
        if (parts.length < 3) {
            String jsonBody = "{\"command\":\"invalid-order\"}";
            sendPostRequest("/order", jsonBody);
            return;
        }
        String action = parts[1].toLowerCase();
        switch (action){
            case "place":
                if(parts.length != 5){
                    String jsonBody = String.format(
                            "{\"command\":\"place order\",\"user_id\":%s,\"product_id\":%s,\"quantity\":%s}",
                            "invalid-info", "invalid-info", "invalid-info");
                    sendPostRequest("/order", jsonBody);
                    return;
                }
                else{
                    String jsonBody = String.format(
                            "{\"command\":\"place order\",\"user_id\":%s,\"product_id\":%s,\"quantity\":%s}",
                            parts[3], parts[2], parts[4]);
                    sendPostRequest("/order", jsonBody);
                }
                break;
            case "info":
                sendGetRequest("/order/" + parts[2]);
                break;
            case "cancel":
                sendDeleteRequest("/order/" + parts[2]);
                break;
            default:
                System.out.println("Invalid order action");
                return;
        }

    }

    /**
     * Executes a HTTP DELETE request to a specific endpoint on the OrderService.
     * This method is primarily used for canceling existing orders. It constructs the target URI,
     * removes whitespace, and dispatches the request using the shared HttpClient
     * @param endpoint The relative path for the deletion target (for example: "/order/1000")
     */
    public static void sendDeleteRequest(String endpoint){
        try {
            String fullUrl = (orderUrl + endpoint).replaceAll("\\s", "");
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fullUrl))
                    .DELETE().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("DELETE " + endpoint + " | Status: " + response.statusCode());
            System.out.println("Data: " + response.body());
        }catch (Exception e){
            System.out.println("Error when deleting an Order");
        }
    }
    /*
    * Build the jsonBody that will be sent to the OrderService. Eventually, the jsonBody will be sent to product service
    * */
    public static void handleProduct(String command, String[] parts) throws IOException, URISyntaxException, InterruptedException {
        if (command.equalsIgnoreCase("info")) {
            sendGetRequest("/product/" + parts[2]);
        } else if (command.equalsIgnoreCase("create")) {
            // Hanlde the case that some fields are missing when create new product
            if(parts.length!=7){
                String json1 = String.format(
                        "{\"command\":\"create\"," +
                                "\"id\":%s," +
                                "\"name\":\"%s\"," +
                                "\"description\":\"%s\"," +
                                "\"price\":%s," +
                                "\"quantity\":%s}",
                        "invalid-info", "invalid-info", "invalid-info", "invalid-info", "invalid-info"
                );
                sendPostRequest("/product", json1);
                return;
            }


            String id = parts[2];
            String name = parts[3];
            String description = parts[4];
            String price = parts[5];
            String quantity = parts[6];
            String json = String.format(
                    "{\"command\":\"create\"," +
                            "\"id\":%s," +
                            "\"name\":\"%s\"," +
                            "\"description\":\"%s\"," +
                            "\"price\":%s," +
                            "\"quantity\":%s}",
                    id, name, description, price, quantity
            );
            sendPostRequest("/product", json);
        } else if (command.equalsIgnoreCase("update")) {
            StringBuilder json_builder = new StringBuilder(String.format("{\"command\":\"update\",\"id\":%s", parts[2]));
            for(int i = 3; i < parts.length; i++){
                String[] key_val = parts[i].split(":");
                if(key_val.length == 2){
                    if(key_val[0].equals("price")||key_val[0].equals("quantity")){
                        json_builder.append(String.format(",\"%s\":%s", key_val[0], key_val[1]));
                    }else{
                        json_builder.append(String.format(",\"%s\":\"%s\"", key_val[0], key_val[1]));
                    }
                }
            }
            json_builder.append("}");
            sendPostRequest("/product", json_builder.toString());
        } else if (command.equalsIgnoreCase("delete")) {
            if(parts.length != 6){
                String json1 = String.format(
                        "{\"command\":\"delete\",\"id\":%s,\"name\":\"%s\",\"price\":%s,\"quantity\":%s}",
                        "invalid-info", "invalid-info", "invalid-info", "invalid-info"
                );
                return;
            }
            String json = String.format(
                    "{\"command\":\"delete\",\"id\":%s,\"name\":\"%s\",\"price\":%s,\"quantity\":%s}",
                    parts[2], parts[3], parts[4], parts[5]
            );
            sendPostRequest("/product", json);
        }
    }


    /**
     * Send an HTTP GET request to the OrderService gateway
     * This method waits for the response string and logs both the status code and the body to the console
     *
     * @param endpoint endpoint the resource (For example, /user/1009)
     * @throws IOException If a network error occurs during transmission
     * @throws InterruptedException If the thread is interrupted while waiting for the response
     * @throws URISyntaxException If the concatenated URL creates an invalid URI structure
     */
    public static void sendGetRequest(String endpoint) throws IOException, InterruptedException, URISyntaxException {
        try {
            // when the code executes
            // 1: WorkloadParser (client) sends the Get request to the orderSErvice
            // 2: OrderService: (Gateway) receives it and forwards it to the ISCS
            // 3: ISCS (router) identifies the path (/user/2) and route it to the correct UserService instance
            // 4: User service returns the json, which travels back through the ISCS and OrderService to the parser
            //URI (the identifier): the general name for any string that identifies a resource
            //URL (The name): A spacific type of URI that identifies a resource by a persistent unique name (like book's ISBN),
            // even if its location changes
            // Structure of URI
            // 1: Scheme: http (specifies the protocol/method of access)
            // 2: Authority: the IP address and port; For example, 127.0.0.1(IP):14000(port);
            String fullUrl = (orderUrl + endpoint).replaceAll("\\s", "");
            HttpRequest request1 = HttpRequest.newBuilder().
                    uri(URI.create(fullUrl)).
                    GET().build();
            // Execution phase; HttpRequest defines what to do; this line actually does it
            // client.send(): synchronous; the program pauses on this line until the order service
            // response or the connection times out.
            HttpResponse<String> response = client.send(request1, HttpResponse.BodyHandlers.ofString());
            System.out.println("GET " + endpoint + " | Status: " + response.statusCode());
            System.out.println("Data: " + response.body());
        } catch (Exception e) {
            System.err.println("Error sending GET request: "+ e.getMessage());
        }
    }


    /*
    * Specify the orderUrl. The request will be sent to the orderService
    * */

    /**
     * Executes an HTTP POST request to the OrderService gateway with a JSON payload
     * This method set the Content-Type header to application/json, ensuring that the receiving
     * service correctly interprets the body as a JSON object. It logs the target endpoint, the resulting HTTP
     * status code, and the response body to the standard output
     *
     * @param endpoint endpoint the relative API path (for example, "/user" or "/order")
     * @param jsonBody The string which represent a JSON object containing the command and data
     */
    public static void sendPostRequest(String endpoint, String jsonBody){
        try {
            //System.out.println("run the sendPostRequest");
            String fullUrl = (orderUrl + endpoint).replaceAll("\\s", "");

            HttpRequest request  = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
            //System.out.println("After the HTTPRequest");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("POST " + endpoint + " | Status: " + response.statusCode());
            System.out.println("Data: " + response.body());
//            if(response.statusCode() != 200){
//                System.out.println("Response Body: " + response.body());
//            }
        } catch (Exception e) {
            System.out.println("Encounter an exception: " + e.getMessage());
        }
    }
}
