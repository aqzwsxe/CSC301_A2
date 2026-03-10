package OrderService;

import Utils.CacheManager;
import Utils.ConfigReader;
import Utils.DatabaseManager;
import Utils.PersistenceManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the given order request and generates an appropriate response.
 */
public class OrderHandler implements HttpHandler {
    /**
     * The base URL for the ISCS
     * All requests that require data from the User or Product services are
     * forwarded to this address for routing
     */
    private final java.util.List<String> iscsUrls;
    private final java.util.concurrent.atomic.AtomicInteger iscsCounter = new java.util.concurrent.atomic.AtomicInteger(0);
//    private final String configFile; // Store this to reload if needed
    /**
     * The HTTP client used for backend.
     */
    private final HttpClient client;

    private static final CacheManager<Integer, String> orderCache= new CacheManager<>();

    /**
     * For thread safety
     */
    private static AtomicBoolean isFirstRequest = new AtomicBoolean(true);

    private static final boolean DEBUG_MODE = false;

    private void debugOrSend(HttpExchange exchange, int status, byte[] message) throws IOException {
        if (DEBUG_MODE) {
            System.out.println("-------------------------------------");
            System.out.println("The DEBUG_MODE is: " + DEBUG_MODE);
            System.out.println("Run if block of debugOrSend in OrderHandler");
            System.out.println("-------------------------------------");
            // Convert the byte[] message to a String to include in the debug JSON
            String messageStr = new String(message, StandardCharsets.UTF_8);

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            // Escape quotes to prevent breaking the debug JSON structure
            sb.append("  \"debug_msg\": \"").append(messageStr.replace("\"", "\\\"")).append("\",\n");
            sb.append("  \"method\": \"").append(exchange.getRequestMethod()).append("\",\n");
            sb.append("  \"path\": \"").append(exchange.getRequestURI().getPath()).append("\"\n");
            sb.append("}");

            byte[] debugBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, status, debugBytes);
        } else {
            System.out.println("-------------------------------------");
            System.out.println("The DEBUG_MODE is: " + DEBUG_MODE);
            System.out.println("Run the else block of debugOrSend in OrderHandler");
            System.out.println("-------------------------------------");

            // Send the raw byte array directly
            sendResponse(exchange, status, message);
        }
    }


//    private void debugExchange(HttpExchange exchange) throws IOException {
//        StringBuilder sb = new StringBuilder();
//        sb.append("{\n");
//        sb.append("  \"method\": \"").append(exchange.getRequestMethod()).append("\",\n");
//        sb.append("  \"path\": \"").append(exchange.getRequestURI().getPath()).append("\",\n");
//        sb.append("  \"headers\": \"").append(exchange.getRequestHeaders().entrySet().toString()).append("\"\n");
//        sb.append("}");
//
//        byte[] debugBytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
//
//        sendResponse(exchange, 10101, debugBytes);
//    }

    /**
     * The constructor of OrderHandler. It constructs an Orderhandler by resolving the
     * location of the ISCS. This constructor parses the configuration file to obtain the network coordinates (IP and Port)
     * for the ISCS. It then initializes an HttpClient that will be used for all upstream communication during the
     * order process

     * @param configFile the path to the configuration JSON file containing service network settings.
     * @throws IOException If the configuration file cannot be accessed or parsed
     */
    public OrderHandler(String configFile) throws IOException {
        this.iscsUrls = ConfigReader.getServicePool(configFile, "InterServiceCommunication");

        if (this.iscsUrls.isEmpty()) {
            throw new IOException("No ISCS nodes found in configuration!");
        }

        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();

        System.out.println("OrderHandler initialized with " + iscsUrls.size() + " ISCS nodes.");
    }


    private String getNextIscsUrl(){
        int index = Math.abs(iscsCounter.getAndIncrement() % iscsUrls.size());
        return iscsUrls.get(index);
    }
    /**
     * Main request dispatcher for the OrderService
     * This method intercepts all incoming HTTP traffic and routes it based on
     * the HTTP verb and the URI path. Requests specific to the order lifecycle
     * (GET, DELETE, or 'place order' POSTs) are handled locally, while all other traffic
     * if forwarded to the ISCS for further routing
     * @param exchange the exchange containing the request from the
     *                 client and used to send the response
     * @throws IOException If an I/O error occurs during request reading or response delivery
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
//        debugExchange(exchange);
        String method = exchange.getRequestMethod();
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String bodyString = new String(requestBody, StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        String temp_path = path.toLowerCase();
        System.out.println("The Order handle method: " );
        System.out.println("method "+ method);
        System.out.println("The bodyString: "+ bodyString);
        try {
            if (isFirstRequest.getAndSet(false)){
                if (temp_path.equals("/restart")){
                    // Keep the database
                    System.out.println("OrderService: First is Restart. Persisting data.");
                    signalInternalServices("restart");
                    debugOrSend(exchange, 200, requestBody);
                    return;
                }else if(temp_path.equals("/clear")){
                    System.out.println("OrderService: First request is " + path + ". Wiping DB.");
                    DatabaseManager.clearAllData();
                    signalInternalServices("clear");
                    debugOrSend(exchange, 200, requestBody);
                    return;
                }
                //else {
                    //System.out.println("OrderService: First request is " + path + ". Defaulting to WIPE.");
                    //DatabaseManager.clearAllData();
                    //signalInternalServices("clear");
                //}
            }

            // if it is not the first request, we still signal others
            // do nothing to the database
            if(temp_path.equals("/restart")){
                signalInternalServices("restart");
                debugOrSend(exchange, 200, requestBody);
                return;

            }

            if (temp_path.equals("/clear")) {
                DatabaseManager.clearAllData();
                signalInternalServices("clear");
                debugOrSend(exchange, 200, requestBody);
                return;
            }

            if (temp_path.equals("/shutdown")){
                System.out.println("OrderService: Shutting down all services");
                signalInternalServices("shutdown");
                debugOrSend(exchange, 200, requestBody);
                new Thread(() -> {
                    try { Thread.sleep(500); System.exit(0); }
                    catch (Exception ignored) {}
                }).start();
                return;
            }



            if(method.equalsIgnoreCase("GET") ){
                if(path.startsWith("/user/purchased/")){
                    handleUserPurchased(exchange,path, requestBody);
                    return;
                }else if(path.startsWith("/order/")){
                    handleGetOrder(exchange, path, requestBody);
                    return;
                }else if(path.startsWith("/user/")){
                    handleGetUser(exchange, method, path, requestBody);
                    return;
                }
                else if(path.startsWith("/product/")){
                    handleGetProduct(exchange, method, path, requestBody);
                    return;
                }
            }else if(method.equalsIgnoreCase("DELETE")&& path.startsWith("/order/")){
                handleCancelOrder(exchange, path, requestBody);
            } else if(method.equalsIgnoreCase("POST") && path.startsWith("/order")  && bodyString.contains("place order")){
                handlePlaceOrder(exchange, bodyString,requestBody);
            }else{
                forwardToISCS(exchange,method,path,requestBody);
            }
        }catch (Exception e){
            try {
                sendError(exchange, 400, "Invalid Request", requestBody);
            }catch (Exception e1){}

        }
    }

    private void handleGetUser(HttpExchange exchange, String method, String path, byte[] requestBody) throws IOException {
        try {
            // The assignment says OrderService handles user functionality by forwarding.
            forwardToISCS(exchange, method, path, requestBody);
        } catch (InterruptedException e) {
            sendError(exchange, 500, "Internal Server Error", requestBody);
        }
    }

    private void handleGetProduct(HttpExchange exchange, String method, String path, byte[] requestBody) throws IOException {
        try {
            forwardToISCS(exchange, method, path, requestBody);
        } catch (InterruptedException e) {
            sendError(exchange, 500, "Internal Server Error", requestBody);
        }
    }
    /**
     * Get order information based on order id
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: order information found success; response body is {@code order.toJson().getBytes()}</li>
     *   <li>{@code 400}: missing fields or invalid field type/value; response body is {@code {}}</li>
     *   <li>{@code 404}: order not found; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param path the request URI path; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handleGetOrder(HttpExchange exchange, String path, byte[] requestBody) throws IOException {
        String[] parts = path.split("/");
        if (parts.length < 3){
            String s1 = new String(requestBody);
            sendError(exchange,400, s1, requestBody);
            return;
        }
        try {
            int orderId = Integer.parseInt(parts[2]);

            String cached = orderCache.get(orderId);
            if (cached != null){
                debugOrSend(exchange, 200, requestBody);
                return;
            }

            Order order = DatabaseManager.getOrderById(orderId);
            if(order != null){
                String json1 = order.toJson();
                orderCache.put(orderId, json1);
                debugOrSend(exchange, 200, requestBody);
            }else{
                String s1 = new String(requestBody);
                sendError(exchange, 404, s1, requestBody);
            }
        }catch (NumberFormatException e){
            String s1 = new String(requestBody);
            sendError(exchange, 400, s1, requestBody);
            return;
        }
    }

    /**
     * Cancel order based on order id
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: order cancellation success; response body is {@code order.toJson().getBytes()}</li>
     *   <li>{@code 400}: missing fields or invalid field type/value; response body is {@code {}}</li>
     *   <li>{@code 404}: order not found; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param path the request URI path; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handleCancelOrder(HttpExchange exchange, String path, byte[] requestBody) throws IOException {
        String[] parts = path.split("/");
        if(parts.length < 3){
            sendError(exchange, 400, "Invalid Order ID", requestBody);
        }

        try {
            int orderId = Integer.parseInt(parts[2]);
            Order order = DatabaseManager.getOrderById(orderId);
            if(order==null){
                sendError(exchange, 404, "Order not found", requestBody);
                return;
            }

            if ("Cancelled".equalsIgnoreCase(order.getStatus())){
                sendError(exchange, 400, "Order already cancelled", requestBody);
                return;
            }
            HttpResponse<String> prodRes = client.send(
                    HttpRequest.newBuilder().uri(URI.create(getNextIscsUrl() + "/product/internal/" + order.getProduct_id())).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if(prodRes.statusCode() == 200){
                int currentStock = Integer.parseInt(getJsonValue(prodRes.body(), "quantity"));
                int restoredStock = currentStock + order.getQuantity();

                boolean success = DatabaseManager.cancelOrder(orderId, order.getProduct_id(), restoredStock);
                if(success){
                    orderCache.invalidate(orderId);
                    debugOrSend(exchange, 200, requestBody);
                    return;
                }else {
                    sendError(exchange, 500, "Database Transaction Failed", requestBody);
                }
            } else {
                sendError(exchange, 404, "Product associated with order no longer exists", requestBody);
            }
        }catch (Exception e){
            String s1 = new String(requestBody);
            sendError(exchange, 400, s1, requestBody);
        }
    }

    /**
     * Place an order based on user id and product id if corresponding product has sufficient quantity
     * in stock
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: order place success; response body is {@code successJson.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type/value; response body is {@code {}}</li>
     *   <li>{@code 404}: invalid order request; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param body a JSON string containing the product id, user id, and quantity
     * @throws IOException if an I/O error occurs while sending the response
     */
    private  void  handlePlaceOrder(HttpExchange exchange, String body, byte[] requestBody) throws IOException, InterruptedException {
        try {
            String userId = getJsonValue(body, "user_id");
            String productId = getJsonValue(body, "product_id");
            // the quantity the order want
            String quantityStr = getJsonValue(body, "quantity");

            if(userId==null || productId == null || quantityStr == null ||
                    userId.equals("invalid-info") || productId.equals("invalid-info") || quantityStr.equals("invalid-info")){
                System.out.println("Enter the if statement; something is null");
                sendError(exchange,400, "Invalid Request", requestBody);
                return;
            }

            int quantity = Integer.parseInt(quantityStr);
            if(quantity <= 0){
                sendError(exchange, 400, "Invalid Request", requestBody);
                return;
            }

            // Fire both requests in parallel
            var userFuture = client.sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(getNextIscsUrl() + "/user/internal/" + userId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            var prodFuture = client.sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(getNextIscsUrl() + "/product/internal/" + productId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // Wait for both (virtual threads will yield here)
            HttpResponse<String> userRes = userFuture.join();
            HttpResponse<String> prodRes = prodFuture.join();

            if (userRes.statusCode() == 404 || prodRes.statusCode() == 404) {
                sendError(exchange, 404, "Invalid Request",requestBody);
                return;
            }

            // Check stock
            int availableQuantity = Integer.parseInt(getJsonValue(prodRes.body(), "quantity"));
            if (quantity > availableQuantity) {
                sendError(exchange, 400, "Exceeded quantity limit",requestBody);
                return;
            }

            int newStock = availableQuantity - quantity;

            boolean transactionSuccess = DatabaseManager.placeOrder(
                    Integer.parseInt(productId),
                    Integer.parseInt(userId),
                    quantity,
                    newStock
            );
            if (transactionSuccess) {
                String successJson = String.format(
                        "{\"product_id\": %s, \"user_id\": %s, \"quantity\": %d, \"status\": \"Success\"}",
                        productId, userId, quantity);
                debugOrSend(exchange, 200, requestBody);
            } else {
                sendError(exchange, 500, "Database Transaction Failed",requestBody);
            }


        }catch (Exception e){
            sendError(exchange, 400, "Invalid Request",requestBody);
        }


    }


    /**
     * Forwards an incoming HTTP request to the ISCS Service.
     * This method acts as a reverse proxy. It reconstructs the original request
     * (method, path, and body) and dispatches it to the ISCS. Once the ISCS returns
     * a response from the destination microservice, this method relays that response including
     * the status code and data back to the original client
     * @param exchange The original HttpExchange from the client
     * @param method The HTTP verb (GET, POST, etc.) to reuse
     * @param path The URI path to append to the ISCS base URL
     * @param requestBody The raw bytes of the original request body
     * @throws IOException If network communication with the ISCS fails
     * @throws InterruptedException If the forwarding process is interrupted
     */
    private void forwardToISCS(HttpExchange exchange, String method, String path, byte[] requestBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(getNextIscsUrl() + path));
        if(method.equalsIgnoreCase("POST")){
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
        }else{
            builder.GET();
        }
        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(res -> {
                    try {
                        debugOrSend(exchange, res.statusCode(), res.body());
                    } catch (IOException e) {
                        // Connection likely closed by client
                    }
                })
                .exceptionally(ex -> {
                    try { sendError(exchange, 502, "Bad Gateway", requestBody); } catch (IOException ignored) {}
                    return null;
                });
    }

    /**
     * Sends an HTTP response with a JSON body.
     *
     * <p><b>Assumptions:</b> {@code response} is a valid JSON string and {@code exchange} is open.</p>
     *
     * @param exchange the HTTP exchange used to send the response; must be non-null
     * @param statusCode the HTTP status code to send
     * @param response the response body to send; must be non-null
     * @throws IOException if an I/O error occurs while sending headers or writing the body
     */
    private void sendResponse(HttpExchange exchange, int statusCode, byte[] response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends an HTTP response with a error status code and error message.
     *
     * <p><b>Assumptions:</b> {@code response} is a valid JSON string and {@code exchange} is open.</p>
     *
     * @param exchange the HTTP exchange used to send the response; must be non-null
     * @param code the HTTP error code
     * @param message the error message
     * @throws IOException if an I/O error occurs while sending headers or writing the body
     */
    private void sendError(HttpExchange exchange, int code, String message, byte[] requestBody) throws IOException {
        String json = String.format("{\"status\": \"%s\"}\n", message);
        // Passing requestBody here allows debugOrSend to show what caused the error
        debugOrSend(exchange, code, requestBody);
    }

    /**
     * Gets the value from the JSON string with corresponding key.
     *
     * @param json a JSON string
     * @param key the key of the value searching for
     * @return the value found or null if key not found
     */
    private String getJsonValue(String json, String key){
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if(start==-1){
            return null;
        }

        start += pattern.length();
        // Trim potential spaces between : and value
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        String value;
        if (json.charAt(start) == '\"') {
            // Handle String values: find the closing quote
            start++; // skip opening quote
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            value = json.substring(start, end);
        } else {
            // Handle number/boolean values: find comma or brace
            int end = json.indexOf(",", start);
            if (end == -1) {
                end = json.indexOf("}", start);
            }
            if (end == -1) return null;
            value = json.substring(start, end).trim();
        }

        return value;
    }


    private void handleUserPurchased(HttpExchange exchange, String path, byte[] requestBody) throws IOException {
        String[] parts = path.split("/");
        if(parts.length < 4){
            sendError(exchange, 400, "Invalid User ID", requestBody);
            return;
        }

        try {
            String userIdStr = parts[3];
            int userId = Integer.parseInt(userIdStr);

            if(!userExists(userIdStr)){
                sendError(exchange, 404, "User Not Found", requestBody);
                return;
            }
            // Aggregate purchases
            Map<Integer, Integer> purchases = DatabaseManager.getUserPurchases(userId);
            String jsonResponse = mapToJson(purchases);
            debugOrSend(exchange, 200, requestBody);
        }catch (NumberFormatException e){
            String s1 = new String(requestBody);
            sendError(exchange, 400, s1, requestBody);
        }catch (Exception e){
            String s1 = new String(requestBody);
            sendError(exchange, 500, "Internal Server Error",requestBody);
        }
    }

    private boolean userExists(String userId){
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getNextIscsUrl() + "/user/internal/" + userId)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void signalInternalServices(String command){
        String final_command = command;
        if(command.equalsIgnoreCase("restart")|| command.equalsIgnoreCase("shutdown")){
            final_command = command.toLowerCase();
        }
        String[] internalRoutes = {"/user/internal/" + final_command, "/product/internal/"+ final_command};

        for(String route: internalRoutes){
            try {
                // Don't necessarily need to wait for a complex response, just ensure the message is sent
                HttpRequest request = HttpRequest.newBuilder().
                        uri(URI.create(getNextIscsUrl() + route)).
                        POST(HttpRequest.BodyPublishers.noBody()).build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
                System.out.println("Signaled " + route + " with command: " + command );
            } catch (Exception e) {
                System.err.println("Failed to signal " + route + ": " + e.getMessage());
            }
        }
    }



    public String mapToJson(Map<Integer, Integer> map){
        if(map.isEmpty()){
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for(Map.Entry<Integer, Integer> entry: map.entrySet()){
            if(!first){
                builder.append(", ");
            }
            builder.append(String.format("\"%d\": %d", entry.getKey(), entry.getValue()));
            first = false;
        }
        builder.append("}");
        return builder.toString();

    }


}
