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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the given order request and generates an appropriate response.
 */
public class OrderHandler implements HttpHandler {
    /**
     * All requests that require data from the User or Product services are
     * forwarded to this address for routing
     */
    private final List<String> userServicePool;
    private final List<String> productServicePool;
    private final List<String> orderServicePool;

    private final AtomicInteger userCounter = new AtomicInteger(0);
    private final AtomicInteger productCounter = new AtomicInteger(0);
    private final AtomicInteger orderCounter = new AtomicInteger(0);

    private final HttpClient client;
    private static final CacheManager<Integer, String> orderCache = new CacheManager<>();
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
     * @param configFile the path to the configuration JSON file containing service network settings.
     * @throws IOException If the configuration file cannot be accessed or parsed
     */
    public OrderHandler(String configFile) throws IOException {
        // Load actual service pools directly from config
        this.userServicePool = ConfigReader.getServicePool(configFile, "UserService");
        this.productServicePool = ConfigReader.getServicePool(configFile, "ProductService");
        this.orderServicePool = ConfigReader.getServicePool(configFile, "OrderService");

        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        System.out.println("OrderHandler: Direct Routing Mode. Users: " + userServicePool.size() + " Products: " + productServicePool.size());
    }


    private String getNextUrl(List<String> pool, AtomicInteger counter){
        int index = Math.abs(counter.getAndIncrement() % pool.size());
        return pool.get(index);
    }
    /**
     *
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

        try {
            // First Request Logic (Initialization)
            if (isFirstRequest.getAndSet(false)) {
                if (temp_path.equals("/restart")) {
                    propagateSignal("restart");
                    debugOrSend(exchange, 200, requestBody);
                    return;
                } else if (temp_path.equals("/clear")) {
                    DatabaseManager.clearAllData();
                    propagateSignal("clear");
                    debugOrSend(exchange, 200, requestBody);
                    return;
                }
            }

            if (temp_path.equals("/restart") || temp_path.equals("/clear")) {
                if (temp_path.equals("/clear")) DatabaseManager.clearAllData();
                propagateSignal(temp_path.substring(1));
                debugOrSend(exchange, 200, requestBody);
                return;
            }

            if (temp_path.equals("/shutdown")) {
                propagateSignal("shutdown");
                debugOrSend(exchange, 200, requestBody);
                new Thread(() -> {
                    try { Thread.sleep(800); System.exit(0); } catch (Exception ignored) {}
                }).start();
                return;
            }

            // Routing Logic
            if (method.equalsIgnoreCase("GET")) {
                if (path.startsWith("/user/purchased/")) {
                    handleUserPurchased(exchange, path, requestBody);
                } else if (path.startsWith("/order/")) {
                    handleGetOrder(exchange, path, requestBody);
                } else if (path.startsWith("/user/")) {
                    // Forward directly to User Service
                    forwardToService(exchange, getNextUrl(userServicePool, userCounter), path, requestBody);
                } else if (path.startsWith("/product/")) {
                    // Forward directly to Product Service
                    forwardToService(exchange, getNextUrl(productServicePool, productCounter), path, requestBody);
                }
            } else if (method.equalsIgnoreCase("DELETE") && path.startsWith("/order/")) {
                handleCancelOrder(exchange, path, requestBody);
            } else if (method.equalsIgnoreCase("POST") && path.startsWith("/order") && bodyString.contains("place order")) {
                handlePlaceOrder(exchange, bodyString, requestBody);
            } else {
                // Determine destination based on path prefix
                String target = path.startsWith("/user") ? getNextUrl(userServicePool, userCounter) : getNextUrl(productServicePool, productCounter);
                forwardToService(exchange, target, path, requestBody);
            }
        } catch (Exception e) {
            sendError(exchange, 400, "{}\n", requestBody);
        }
    }

    // 3. Updated forwardToService (Replaces forwardToISCS)
    private void forwardToService(HttpExchange exchange, String targetBaseUrl, String path, byte[] requestBody) {
        String fullUrl = targetBaseUrl + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(fullUrl));

        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
        } else {
            builder.GET();
        }

        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(res -> {
                    try { debugOrSend(exchange, res.statusCode(), res.body()); } catch (IOException ignored) {}
                })
                .exceptionally(ex -> {
                    try { sendError(exchange, 502, "Bad Gateway", requestBody); } catch (IOException ignored) {}
                    return null;
                });
    }
    private void propagateSignal(String command) {
        String subPath = "/internal/" + command;
        for (String url : userServicePool) sendSignal(url + "/user" + subPath);
        for (String url : productServicePool) sendSignal(url + "/product" + subPath);
        for (String url : orderServicePool) sendSignal(url + "/order" + subPath);
    }

    private void sendSignal(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(500))
                .GET().build();
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }

    private void handleGetUser(HttpExchange exchange, String method, String path, byte[] requestBody) throws IOException {
        forwardToService(exchange, getNextUrl(userServicePool, userCounter), path, requestBody);
    }

    private void handleGetProduct(HttpExchange exchange, String method, String path, byte[] requestBody) throws IOException {
        forwardToService(exchange, getNextUrl(productServicePool, productCounter), path, requestBody);
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
            sendError(exchange, 400, "{}\n", requestBody);
            return;
        }

        try {
            // Use parts.length - 1 to get the ID from the end of the URL (/order/1)
            int orderId = Integer.parseInt(parts[parts.length - 1]);

            // 1. Check Cache
            String cached = orderCache.get(orderId);
            if (cached != null){
                // Pass the CACHED string bytes as the response
                debugOrSend(exchange, 200, cached.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 2. Check Database
            Order order = DatabaseManager.getOrderById(orderId);
            if (order != null) {
                String json1 = order.toJson();
                orderCache.put(orderId, json1);
                // Pass the ORDER JSON bytes as the response
                debugOrSend(exchange, 200, json1.getBytes(StandardCharsets.UTF_8));
            } else {
                sendError(exchange, 404, "{}\n", requestBody);
            }
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "{}\n", requestBody);
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
            sendError(exchange, 400, "{}\n", requestBody);
        }

        try {
            int orderId = Integer.parseInt(parts[2]);
            Order order = DatabaseManager.getOrderById(orderId);
            if(order==null){
                sendError(exchange, 404, "{}\n", requestBody);
                return;
            }

            if ("Cancelled".equalsIgnoreCase(order.getStatus())){
                sendError(exchange, 400, "{}\n", requestBody);
                return;
            }
            HttpResponse<String> prodRes = client.send(
                    HttpRequest.newBuilder().uri(URI.create(getNextUrl(productServicePool, productCounter) + "/product/internal/" + order.getProduct_id())).GET().build(),
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
                sendError(exchange, 404, "{}\n", requestBody);
            }
        }catch (Exception e){
            String s1 = new String(requestBody);
            sendError(exchange, 400, "{}\n", requestBody);
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
    private void handlePlaceOrder(HttpExchange exchange, String body, byte[] requestBody) throws IOException, InterruptedException {
        try {
            String userId = getJsonValue(body, "user_id");
            String productId = getJsonValue(body, "product_id");
            String quantityStr = getJsonValue(body, "quantity");

            if (userId == null || productId == null || quantityStr == null) {
                sendError(exchange, 400, "{}\n", requestBody);
                return;
            }

            int quantity = Integer.parseInt(quantityStr);

            // DIRECT CALLS to User and Product Services
            System.out.println("Step 1: Starting Order Create");
            var userFuture = client.sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(getNextUrl(userServicePool, userCounter) + "/user/internal/" + userId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            var prodFuture = client.sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(getNextUrl(productServicePool, productCounter) + "/product/internal/" + productId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Step 2: Waiting for User/Product services...");
            HttpResponse<String> userRes = userFuture.join();
            System.out.println("Step 3: User Service responded with: " + userRes.statusCode());
            HttpResponse<String> prodRes = prodFuture.join();
            System.out.println("Step 4: Product Service responded with: " + prodRes.statusCode());

            if (userRes.statusCode() == 404 || prodRes.statusCode() == 404) {
                sendError(exchange, 404, "{}\n", requestBody);
                return;
            }
            // 2. Extract and Validate the Quantity String
            String availStr = getJsonValue(prodRes.body(), "quantity");
            if (availStr == null) {
                System.err.println("FAILED: Missing 'quantity' in Product Service response body: " + prodRes.body());
                sendError(exchange, 500, "Upstream Format Error", requestBody);
                return;
            }
            // 3
            int available;
            try {
                available = Integer.parseInt(availStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("FAILED: Could not parse quantity string: '" + availStr + "'");
                sendError(exchange, 500, "Upstream Data Error", requestBody);
                return;
            }
            // 4. Business Logic: Stock Check
            if (quantity > available) {
                System.out.println("DEBUG: Insufficient stock. Requested: " + quantity + ", Available: " + available);
                sendError(exchange, 400, "{}\n", requestBody);
                return;
            }
            // 5. Database Persistence
            boolean success = DatabaseManager.placeOrder(
                    Integer.parseInt(productId),
                    Integer.parseInt(userId),
                    quantity,
                    available - quantity
            );

            if (success) {
                debugOrSend(exchange, 200, requestBody);
            } else {
                sendError(exchange, 500, "Database Transaction Failed", requestBody);
            }
        } catch (Exception e) {
            System.err.println("!!! PlaceOrder Logic Failed !!!");
            e.printStackTrace(); // This will tell you EXACTLY which line crashed
            sendError(exchange, 400, "{}\n", requestBody);        }

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
        try {
            String json = String.format("{\"status\": \"error\", \"message\": \"%s\"}\n", message);
            byte[] response = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (IOException e) {
            // If we fail here, just log it and stop. Do NOT call sendError again.
            System.err.println("Fatal: Could not even send error response: " + e.getMessage());
        }
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
            sendError(exchange, 400, "{}\n", requestBody);
            return;
        }

        try {
            String userIdStr = parts[3];
            int userId = Integer.parseInt(userIdStr);

            if(!userExists(userIdStr)){
                sendError(exchange, 404, "{}\n", requestBody);
                return;
            }
            // Aggregate purchases
            Map<Integer, Integer> purchases = DatabaseManager.getUserPurchases(userId);
            String jsonResponse = mapToJson(purchases);
            debugOrSend(exchange, 200, jsonResponse.getBytes(StandardCharsets.UTF_8));
        }catch (NumberFormatException e){
            String s1 = new String(requestBody);
            sendError(exchange, 400, "{}\n", requestBody);
        }catch (Exception e){
            String s1 = new String(requestBody);
            sendError(exchange, 500, "Internal Server Error",requestBody);
        }
    }

    private boolean userExists(String userId){
        try {
            String url = getNextUrl(userServicePool, userCounter) + "/user/internal/" + userId;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
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
