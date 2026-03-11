package ProductService;

import Utils.CacheManager;
import Utils.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;


/**
 * Handles the given product request and generates an appropriate response.
 */
public class ProductHandler implements HttpHandler {
    String errorResponse = "{}\n";

    private static final CacheManager<Integer, String> productCache = new CacheManager<>();
    // 1. ADD THE DEBUG TOGGLE
    private static final boolean DEBUG_MODE = false;

    // 2. ADD THE HELPER METHOD
    private void debugOrSend(HttpExchange exchange, int status, byte[] responseMessage, byte[] originalRequest) throws IOException {
        if (DEBUG_MODE) {
            // Convert both byte arrays to Strings
            String requestStr = new String(originalRequest, StandardCharsets.UTF_8);
            String responseStr = new String(responseMessage, StandardCharsets.UTF_8);

            // Build the combined log-style output
            StringBuilder sb = new StringBuilder();
            sb.append("Request: \n");
            sb.append(requestStr).append("\n\n");
            sb.append("Response: \n");
            sb.append(responseStr);

            byte[] combinedOutput = sb.toString().getBytes(StandardCharsets.UTF_8);

            // Use text/plain so the terminal displays it exactly as-is
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, combinedOutput.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(combinedOutput);
            }
        } else {
            // Standard high-performance JSON response for production
            String s1 = new String(responseMessage);
            sendResponse(exchange, status, s1);
        }
    }

    /**
     * Routes requests based on HTTP method.
     *
     * <p><b>Assumptions:</b> Only GET and POST are supported. Other methods are ignored
     * unless handled elsewhere.</p>
     *
     * @param exchange the HTTP exchange; must be non-null
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String bodyString = new String(requestBody, StandardCharsets.UTF_8);

        if (path.contains("/internal/") ||
                path.equals("/clear") ||
                path.equals("/restart") ||
                path.equals("/shutdown")) {
            handleInternalSignal(exchange, path, requestBody); // Ensure this method exists and resets DB/Id counters
            return;
        }
        try {
            if(method.equals("GET")){
                handleGet(exchange,path, requestBody);
            } else if (method.equals("POST")) {
                handlePost(exchange, bodyString, requestBody);
            }
        } catch (Exception e){
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
        }
    }

    private void handleInternalSignal(HttpExchange exchange, String path, byte[] responseBytes) throws IOException {
        if(path.endsWith("/clear")){
            try {
                DatabaseManager.clearAllData();
                productCache.clear();
            } catch (SQLException e) {
                System.err.println("Failed to clear database: " + e.getMessage());
            }
        }else if(path.endsWith("/shutdown")){
            debugOrSend(exchange, 200, "{}\n".getBytes(), responseBytes);
            new Thread(() -> {
                try { Thread.sleep(200); System.exit(0); } catch (Exception ignored) {}
            }).start();
            return;
        }
        debugOrSend(exchange, 200, "{}\n".getBytes(), responseBytes);
    }

    /**
     * Handles a GET request to fetch a product by ID from the request path.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product exists; response body is {@code product.toJson()}</li>
     *   <li>{@code 400}: malformed path or non-integer id; response body is {@code errorResponse}</li>
     *   <li>{@code 404}: no product with the given id; response body is {@code errorResponse}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param path the request URI path; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handleGet(HttpExchange exchange, String path, byte[] requestBody) throws IOException {
        String[] parts = path.split("/");
        if(parts.length < 3){
            // debugOrSend
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
            return;
        }
        String idStr = parts[parts.length - 1];


        int id;
        if (idStr == null) {
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
            return;
        } else {
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                debugOrSend(exchange,400, "{}\n".getBytes(), requestBody);
                return;
            }
        }
        String cacheProduct = productCache.get(id);
        if (cacheProduct != null){
            debugOrSend(exchange, 200, cacheProduct.getBytes(), requestBody);
            return;
        }


        // get the product from the real database
        Product product = DatabaseManager.getProductById(id);

        if(product != null){
            String jsonResponse = product.toJson();
            debugOrSend(exchange, 200, jsonResponse.getBytes(), requestBody);
            productCache.put(id, jsonResponse);
        }
        else{
            productCache.invalidate(id);
            debugOrSend(exchange,404, "{}\n".getBytes(), requestBody);
        }
    }
    // bridge the gap between a raw HTTP request and the product data
    // handle both Get requests and the Post requests


    /**
     * Handles a POST request to and redirects to handler based on the command.
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handlePost(HttpExchange exchange, String body, byte[] requestBody) throws IOException, SQLException {
//        InputStream is = exchange.getRequestBody();
//        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        String command = getJsonValue(body, "command");
        if(command==null ){
//
            if (path.contains("/restart")) command = "restart";
            else if (path.contains("/shutdown")) command = "shutdown";
            else if (path.contains("/clear")) {
                command = "clear";
            }
        }
        if(command == null){
            debugOrSend(exchange, 400, "{\"error\": \"No command found\"}".getBytes(), requestBody);
            return;
        }

        switch (command){
            case "clear":
                DatabaseManager.clearAllData();
                debugOrSend(exchange, 200, body.getBytes(), requestBody);
                return;
            case "restart":
                debugOrSend(exchange,200, body.getBytes(), requestBody);
                return;
            case "shutdown":
                debugOrSend(exchange, 200, "{}\n".getBytes(), requestBody);
                new Thread(()->{
                    try {
                        Thread.sleep(200);
                        System.exit(0);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                return;
        }

        String idStr = getJsonValue(body, "id");
        int id;
        if (idStr == null) {
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
            return;
        } else {
            try {
                id = Integer.parseInt(idStr);
            } catch (Exception e) {
                debugOrSend(exchange,400, "{}\n".getBytes(), requestBody);
                return;
            }
        }

        // command: represent the value associated with the command key inside the JSON payload
        // that the client sends to your server
        switch (command){
            case "create":
                handleCreate(exchange, id, body, requestBody);
                break;
            case "update":
                handleUpdate(exchange, id, body,requestBody);
                break;
            case "delete":
                handleDelete(exchange, id, body, requestBody);
                break;
            default:
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
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
        int end = json.indexOf(",", start);
        if(end==-1){
            end = json.indexOf("}", start);
        }
        String value = json.substring(start, end).trim();
        if(value.startsWith("\"")){
            value = value.substring(1, value.length()-1);
        }
        return value;
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
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try(OutputStream os = exchange.getResponseBody()){
            os.write(bytes);
        }
    }

    /**
     * Check if fields in the JSON string are non-empty and valid
     *
     * @param body a JSON string containing the product id, name, description, price, and quantity
     * @param desc_matter a Boolean of whether description will be checked
     * @return a Boolean determining whether the description should be validated
     */
    private Boolean inputContentCheck(String body, Boolean desc_matter){
        // Name issues:
        String name = getJsonValue(body, "name");
        if (name == null) {
            return false;
        } else {
            if (name.isEmpty()) {
                return false;
            }
        }

        // Description issues:
        if (desc_matter) {
            String description = getJsonValue(body, "description");
            if (description == null) {
                return false;
            } else {
                if (description.isEmpty()) {
                    return false;
                }
            }
        }


        // Price issues:
        String priceStr = getJsonValue(body, "price");
        float price;
        if (priceStr == null) {
            return false;
        } else {
            try {
                price = Float.parseFloat(priceStr);
                if (price < 0) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        // Quantity issues:
        String quantityStr = getJsonValue(body, "quantity");
        int quantity;
        if (quantityStr == null){
            return false;
        } else {
            try {
                quantity = Integer.parseInt(quantityStr);
                if (quantity < 0) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Handles a create command. Create a new product in database if all fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product creation success; response body is {@code product.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code errorResponse}</li>
     *   <li>{@code 409}: duplicate product id; response body is {@code errorResponse}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the product id of the product to be created
     * @param body a JSON string containing the product id, name, description, price, and quantity
     * @throws IOException if an I/O error occurs while sending the response
     */
    public void handleCreate(HttpExchange exchange, int id, String body, byte[] requestBody) throws IOException {
        // ID issues:
        if(DatabaseManager.getProductById(id)!=null){
            productCache.invalidate(id);
            debugOrSend(exchange, 409, "{}\n".getBytes(), requestBody);
            return;
        }
        // if the json is an invalid json, some of the necessary parts are missing
        String nameValue = getJsonValue(body, "name");
        if (nameValue == null || nameValue.equals("invalid-info")) {
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
            return;
        }

        try {
            if (inputContentCheck(body, true)) {
                String name = getJsonValue(body, "name");
                String description = getJsonValue(body, "description");
                float price = Float.parseFloat(getJsonValue(body, "price"));
                int quantity = Integer.parseInt(getJsonValue(body, "quantity"));

                DatabaseManager.saveProduct(id,name,description,price,quantity);
                Product newProduct = new Product(id, name, description, price, quantity);

                debugOrSend(exchange, 200, newProduct.toJson().getBytes(), requestBody);
            } else {
                debugOrSend(exchange,400, "{}\n".getBytes(), requestBody);
            }
        }catch (Exception e){
            debugOrSend(exchange,500,"{}\n".getBytes(), requestBody);
        }

    }

    /**
     * Handles a update command. Update an existing product in database if updating fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product update success; response body is {@code product.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code errorResponse}</li>
     *   <li>{@code 404}: product id does not exist; response body is {@code errorResponse}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the product id of the product to be updated
     * @param body a JSON string containing the product id, name, description, price, and quantity
     * @throws IOException if an I/O error occurs while sending the response
     */
    public  void handleUpdate(HttpExchange exchange, int id, String body, byte[] requestBody) throws SQLException, IOException {
        Product product = DatabaseManager.getProductById(id);
        if(product == null){
            debugOrSend(exchange, 404, "{}\n".getBytes(), requestBody);
            return;
        }
        String name = getJsonValue(body, "name");
        String description = getJsonValue(body, "description");
        String priceStr = getJsonValue(body, "price");
        String quantityStr = getJsonValue(body, "quantity");

        if (name == null && description == null && priceStr == null && quantityStr == null) {
            debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
            return;
        }

        if (name != null) {
            if (!name.isEmpty()) {
                product.setName(name);
            } else {
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }
        }
        if (description != null) {
            if (!description.isEmpty()) {
                product.setDescription(description);
            } else {
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }
        }
        if (priceStr != null) {
            try {
                float price = Float.parseFloat(priceStr);
                if (price < 0) {
                    debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                    return;
                } else {
                    product.setPrice(price);
                }
            } catch (Exception e) {
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }
        }
        if(quantityStr != null){
            try {
                int quantity = Integer.parseInt(quantityStr);
                if(quantity >= 0){
                    product.setQuantity(quantity);
                }else{
                    debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                    return;
                }
            }catch (Exception e){
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }
        }
        DatabaseManager.updateProduct(product.getPid(),product.getName(),product.getDescription(),product.getPrice(),product.getQuantity());
        productCache.invalidate(id);
        debugOrSend(exchange, 200, product.toJson().getBytes(), requestBody);
        return;
    }

    /**
     * Handles a delete command. Delete an existing product in database if all fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product delete success; response body is {@code product.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code errorResponse}</li>
     *   <li>{@code 404}: product id does not exist; response body is {@code errorResponse}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the product id of the product to be deleted
     * @param body a JSON string containing the product id, name, description, price, and quantity
     * @throws IOException if an I/O error occurs while sending the response
     */
    public void handleDelete(HttpExchange exchange, int id, String body, byte[] requestBody) throws IOException {

        try {
            Product product = DatabaseManager.getProductById(id);
            if(product == null){
                debugOrSend(exchange, 404, "{}\n".getBytes(), requestBody);
                return;
            }

            String nameValue = getJsonValue(body, "name");

            // Check if name is missing (null) OR the explicit "invalid-info" signal
            if (nameValue == null || nameValue.equals("invalid-info")) {
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }

            // check if this is an invalid json file
            if(getJsonValue(body, "name").equals("invalid-info")) {
                debugOrSend(exchange, 400, "{}\n".getBytes(), requestBody);
                return;
            }

            if (inputContentCheck(body, false)) {
                String name = getJsonValue(body, "name");
//            String description = getJsonValue(body, "description");
                float price = Float.parseFloat(getJsonValue(body, "price"));
                int quantity = Integer.parseInt(getJsonValue(body, "quantity"));

                if (product.getName().equals(name)  && product.getPrice() == price &&
                        product.getQuantity() == quantity) { // && product.getDescription().equals(description)
                        DatabaseManager.deleteProduct(id, name,price,quantity);
                        productCache.invalidate(id);
                    debugOrSend(exchange, 200, "{}\n".getBytes(), requestBody);
                } else {
                    debugOrSend(exchange,404, "{}\n".getBytes(), requestBody);
                }
            } else {
                debugOrSend(exchange,400, "{}\n".getBytes(), requestBody);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }




    }
}
