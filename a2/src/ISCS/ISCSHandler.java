package ISCS;

import Utils.ConfigReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * ISCSHandler implements the routing logic for the Inter-service Communication Service.
 * It acts as a reverse proxy that receives HTTP requests from clients and forwards them to the appropriate backend
 * microservice (user or product) based on the URL path
 */
public class ISCSHandler implements HttpHandler {
    /**
     * The user service url
     */
    private final String userServiceUrl;
    /**
     * The product service url
     */
    private final String productServiceUrl;
    /**
     * The HTTP client used to forward intercepted requests to backend services.
     */
    private  final HttpClient client;

    /**
     * The constructor of ISCSHandler. It constructs an ISCSHandler by reading backend service information from a
     * configuration file (config.json). It also creates the HttpClient used for forwarding requests
     * @param configFile The path to the JSON configuration file
     * @throws IOException If the configuration file cannot be read or parsed
     */
    public ISCSHandler(String configFile) throws IOException {
        String userIP = ConfigReader.getIp(configFile, "UserService").replace("\"", "").trim();
        int userPort = ConfigReader.getPort(configFile,"UserService");
        this.userServiceUrl = "http://"+userIP+":"+userPort;

        String productIp = ConfigReader.getIp(configFile, "ProductService").replace("\"", "").trim();
        int productPort = ConfigReader.getPort(configFile, "ProductService");
        this.productServiceUrl = "http://"+productIp + ":" + productPort;

        // A thread-safe; Allows the client to manage a pool of connections and handle the threads
        // When the service needs to talk to another service
        this.client = HttpClient.newHttpClient();


    }

    /**
     * Handle incoming HTTP exchanges by determining the target service, forwarding the request, and returning the
     * backend's response to the original caller
     * @param exchange the exchange containing the request from the client; it is also used to dispatch the response.
     * @throws IOException If an I/O error occurs during request processing or response delivery
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // HttpExchange: the object that represents the entire conversation between the client and the server
        // the only parameter passed to the handle method because it contains both the Request (coming in) and
        // Response (send back)

        // After running the server.start(), the server begins listening on the part we assgined
        // 1: incoming requestion from the workload
        // 2: Matching the url
        // 3: The context map: match the context of the path
        // 4: The trigger: the server graphs one of the threads from the ThreadPool and execuate handle()
        // this is a callback function; the server will call the code when an event happen
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String targetBaseUrl;


        if(path.startsWith("/user")){
            targetBaseUrl = userServiceUrl;
        }else if (path.startsWith("/product")){
            targetBaseUrl = productServiceUrl;
        } else {
                sendResponse(exchange, 404, "Unknown Service Path".getBytes());
                return;
           }
        URI targetUri = URI.create(targetBaseUrl + path);

        System.out.println("[ISCS] Routing to: " + targetUri);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(targetUri);

            if(method.equalsIgnoreCase("POST")){
                byte[] body = exchange.getRequestBody().readAllBytes();
                requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
                requestBuilder.header("Content-Type", "application/json");
            }else{
                requestBuilder.GET();
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            sendResponse(exchange, response.statusCode(), response.body());
        } catch (Exception e) {

            byte[] error = "{}".getBytes();
            sendResponse(exchange, 400, error);
            throw new RuntimeException(e);
        }
    }

    /**
     * Send an HTTP response back to the requester.
     * Sets the Content-Type to application/json and writes the provided byte array to the response body
     * @param exchange The current HTTP exchange
     * @param statusCode The HTTP status code to return (For example: 200, 400 and 404)
     * @param response The byte array representing the JSON response body
     * @throws IOException If the response cannot be written to the stream
     */
    private void sendResponse(HttpExchange exchange, int statusCode, byte[] response) throws IOException {
        // Telling the client (workload generator or the orderservice)
        // what kind of package it is about to receive
        // By setting the Content-Type to application/json ensure that the receiver knows to
        // treat the bytes you send back ask JSON obj rather than plain text or binary data
        exchange.getResponseHeaders().set("Content-Type","application/json");
        // set the status (like 200) and how much is coming
        exchange.sendResponseHeaders(statusCode, response.length);
        // This opens the pipe that connect the service back to the requester (the order service or the workload generator)
        try (OutputStream os = exchange.getResponseBody()){
            // This pushes the byte array (the actual JSON data) through that pipe
            os.write(response);
            // Becuase it is inside the try block, os.close() is auto called
        }
    }
}
