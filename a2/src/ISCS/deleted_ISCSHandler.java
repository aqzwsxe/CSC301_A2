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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ISCSHandler implements the routing logic for the Inter-service Communication Service.
 * It acts as a reverse proxy that receives HTTP requests from clients and forwards them to the appropriate backend
 * microservice (user or product) based on the URL path
 */
public class deleted_ISCSHandler implements HttpHandler {


    /**
     * The HTTP client used to forward intercepted requests to backend services.
     */
    private  final HttpClient client;


    private final List<String> userServicePool;
    private final List<String> productServicePool;
    private final List<String> orderServicePool;
    private final AtomicInteger userCounter= new AtomicInteger(0);
    private final AtomicInteger productCounter = new AtomicInteger(0);



    /**
     * The constructor of ISCSHandler. It constructs an ISCSHandler by reading backend service information from a
     * configuration file (config.json). It also creates the HttpClient used for forwarding requests
     * @param configFile The path to the JSON configuration file
     * @throws IOException If the configuration file cannot be read or parsed
     */
    public deleted_ISCSHandler(String configFile) throws IOException {
        // Load all pools from config.json
        this.userServicePool = ConfigReader.getServicePool(configFile, "UserService");
        this.productServicePool = ConfigReader.getServicePool(configFile, "ProductService");
        this.orderServicePool = ConfigReader.getServicePool(configFile, "OrderService");

        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    private String getNextUserUrl(){
        int index = Math.abs(userCounter.getAndIncrement() % userServicePool.size());
        return userServicePool.get(index);
    }

    private void forwardSignal(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofMillis(500)) // Don't let signals hang
                    .GET()
                    .build();
            // Discarding body handler is best for signals to keep memory usage low
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // During shutdown/clear, some nodes might already be gone; just log and continue
            System.err.println("[ISCS] Signal failed for: " + url);
        }
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
        String finalPath = path;
        if(path.endsWith("/shutdown") || path.endsWith("/restart") || path.endsWith("/clear")){
//            System.out.println("Enter the check block");
            handleInternalSignal(exchange,path);
            return;
        }


        if(path.startsWith("/user")){
            targetBaseUrl = getNextUserUrl();
        }else if (path.startsWith("/product")){
            targetBaseUrl = getNextProductUrl();
        } else if (path.contains("/user/internal/")) {
            targetBaseUrl = getNextUserUrl();
            finalPath = path.replace("/internal", "");
        }else if (path.contains("/product/internal/")) {
            targetBaseUrl = getNextProductUrl();
            finalPath = path.replace("/internal", "");
        } else {
                sendResponse(exchange, 404, "Unknown Service Path".getBytes());
                return;
           }
        URI targetUri = URI.create(targetBaseUrl + finalPath);

        System.out.println("[ISCS] Routing to: " + targetUri);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(targetUri);

            if (method.equalsIgnoreCase("POST")) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
                requestBuilder.header("Content-Type", "application/json");
            } else {
                requestBuilder.GET();
            }

            HttpResponse<byte[]> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            sendResponse(exchange, response.statusCode(), response.body());

        } catch (Exception e) {
            System.err.println("[ISCS] Forwarding Error: " + e.getMessage());
            sendResponse(exchange, 502, "{\"error\": \"Downstream Unavailable\"}".getBytes());
        }
    }

    private String getNextProductUrl(){
        int index = Math.abs(productCounter.getAndIncrement() % productServicePool.size());
        return productServicePool.get(index);
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

    // Only for shutdown, restart and clear
    private void handleInternalSignal(HttpExchange exchange, String path) throws IOException {
        String command = path.substring(path.lastIndexOf("/") + 1);

        // Propagate to ALL services in the cluster
        propagate(userServicePool, "/user/internal/" + command);
        propagate(productServicePool, "/product/internal/" + command);
        propagate(orderServicePool, "/order/internal/" + command);

        System.out.println("[ISCS] Cluster-wide " + command + " initiated.");
        sendResponse(exchange, 200, ("{\"status\": \"" + command + " initiated\"}").getBytes());

        if (command.equals("shutdown")) {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    System.exit(0);
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

    private void propagate(List<String> pool, String subPath) {
        for (String url : pool) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + subPath))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        }
    }


//    private void forwardShutdown(String url){
//        try{
//            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
//            client.send(req, HttpResponse.BodyHandlers.discarding());
//        }catch (Exception e){
//
//        }
//    }
}
