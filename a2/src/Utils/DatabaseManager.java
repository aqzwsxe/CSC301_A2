package Utils;
import OrderService.Order;
import ProductService.Product;
import UserService.User;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private static String dbUrl = "jdbc:sqlite:301A2.db?timeout=5000";
//    private static DBConfig config = DBConfig.load1();
    private static String dbServiceUrl = "http://142.1.114.76:9000/execute";

    private static final HttpClient client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .build();

    private static Connection getConnection() throws SQLException{
        System.out.println("[DB] Connecting to: " + dbUrl);
        Connection connection = DriverManager.getConnection(dbUrl);
        try(Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");        }
        return connection;
    }

    public static boolean isDatabaseHealthy(){
        System.out.println("[HealthCheck] Verifying connection to VM");
        String sql = "SELECT 1;";
        try {
            String response = sendRemoteQuery(sql);
            if(response != null && response.contains("1") && !response.contains("error")){
                System.out.println("[HealthCheck] PASSED: VM and Database are responsive.");
                return true;
            }else {
                System.out.println("[HealthCheck] FAILED: Received unexpected response: " + response);
            }
        }catch (Exception e){
            System.err.println("[HealthCheck] Failed: Could not reach VM: " + e.getMessage());
        }
        return false;
    }

    private static String buildJson(String sql, Object... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"sql\": \"").append(sql.replace("\"", "\\\"")).append("\",");

        sb.append("\"params\": [");
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];

                if (p == null) {
                    sb.append("null");
                } else if (p instanceof String) {
                    sb.append("\"").append(p.toString().replace("\"", "\\\"")).append("\"");
                } else if (p instanceof Boolean || p instanceof Number) {
                    sb.append(p);
                } else {
                    sb.append("\"").append(p.toString().replace("\"", "\\\"")).append("\"");
                }
                if (i < params.length - 1) {
                    sb.append(",");
                }
            }
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private static String sendRemoteQuery(String sql, Object... params) {
        String payload = buildJson(sql, params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dbServiceUrl))
                .header("Content-Type", "application/json")
                .version(HttpClient.Version.HTTP_2)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[RemoteDB Error] " + e.getMessage());
            return null;
        }
    }
//    public static void setUpTables() throws SQLException {
//        String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
//                "id INT PRIMARY KEY, " +
//                "username TEXT, " +
//                "email TEXT, " +
//                "password TEXT);";
//
//        String sqlProducts = "CREATE TABLE IF NOT EXISTS products (" +
//                "id INT PRIMARY KEY, " +
//                "name TEXT, " +
//                "description TEXT, " +
//                "price FLOAT, " +
//                "quantity INT);";
//
//        try (Connection conn = getConnection();
//            Statement statement = conn.createStatement()
//        ) {
//            statement.execute(sqlUsers);
//            statement.execute(sqlProducts);
//        }catch (SQLException e){
//            e.printStackTrace();
//        }
//    }
    public static void setup(String url) throws SQLException{
        if (url.startsWith("http")) {
            dbServiceUrl = url;
        } else {
            dbUrl = url;
        }
    }

    public static void clearAllData() throws SQLException{
        String sql = "BEGIN TRANSACTION; " +
                "DELETE FROM orders; DELETE FROM products; DELETE FROM users; " +
                "DELETE FROM sqlite_sequence WHERE name IN ('orders', 'products', 'users'); " +
                "COMMIT;";
        sendRemoteQuery(sql);
    }


    public static void saveOrder(int prodId, int userId, int qty, String status) throws SQLException{
        String sql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
        sendRemoteQueryAsync(sql, prodId, userId, qty, status);
    }

    private static void sendRemoteQueryAsync(String sql, Object... params) {
        String payload = buildJson(sql, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dbServiceUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }


    public static Order getOrderById(int orderId){
        String response = sendRemoteQuery("SELECT * FROM orders WHERE id = ?", orderId);
        return parseOrderFromJson(response);
    }

    public static Order parseOrderFromJson(String json){
        if(json == null || json.trim().isEmpty() || json.equals("{}") || json.equalsIgnoreCase("null"))
        {
            return null;
        }
        try {
            String idStr = getJsonValue(json, "id");
            String prodIdStr = getJsonValue(json, "product_id");
            String userIdStr = getJsonValue(json, "user_id");
            String qtyStr = getJsonValue(json, "quantity");
            String status = getJsonValue(json, "status");

            if(prodIdStr == null || userIdStr == null){
                return null;
            }
            Order order = new Order(
                    Integer.parseInt(prodIdStr),
                    Integer.parseInt(userIdStr),
                    Integer.parseInt(qtyStr),
                    status
            );

            if(idStr != null){
                order.setId(Integer.parseInt(idStr));
                return order;
            }
        }catch (Exception e){
            System.err.println("[DatabaseManager] Order Parsing error: " + e.getMessage());
            return null;
        }
        return null;
    }



    public static  int  saveUser(String name, String email) throws SQLException{
        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, 'default')";
        String response = sendRemoteQuery(sql, name, email);
        return (response != null && !response.contains("error")) ? 1 : -1;
    }

    public static  boolean saveProduct(int id, String name, String description, float price, int quantity){
        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";

        String response = sendRemoteQuery(sql, id, name, description, price, quantity);

        return response != null && !response.contains("error");
    }

    public static Product  getProductById(int productId){
        String response = sendRemoteQuery("SELECT * FROM products WHERE id = ?", productId);
        if (response == null || response.contains("null") || response.equals("[]")) return null;
        return parseProductFromJson(response);
    }

    public  static void deleteProduct(int id, String name, float price, int quantity){
        String sql = "DELETE FROM products WHERE id = ? AND name = ? AND price = ? AND quantity = ?";
        sendRemoteQuery(sql, id, name, price, quantity);
    }
    public static void updateProductQuantity(int productId, int newQuantity){
        String sql = "UPDATE products SET quantity = ? WHERE id = ?";
        sendRemoteQuery(sql, newQuantity, productId);
    }

    public static void updateProduct(int id, String name, String description, float price, int quantity){
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, quantity = ? WHERE id = ?";
        sendRemoteQuery(sql, name, description, price, quantity, id);
    }

    public static void updateOrderStatus(int orderId, String status){
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        sendRemoteQuery(sql, status, orderId);
    }


    public static Map<Integer, Integer> getUserPurchases(int userId){
        Map<Integer, Integer> purchases = new HashMap<>();
        String sql = "SELECT product_id, SUM(quantity) as total_qty " +
                "FROM orders " +
                "WHERE user_id = ? AND status = 'Success' " +
                "GROUP BY product_id";
        String response = sendRemoteQuery(sql, userId);
        if (response == null || response.length() < 5 || response.equals("[]")) {
            return purchases;
        }
        try {
            String content = response.trim();
            if (content.startsWith("[")) content = content.substring(1);
            if (content.endsWith("]")) content = content.substring(0, content.length() - 1);

            String[] items = content.split("\\},");
            for (String item : items) {
                String cleanItem = item.trim();
                if (!cleanItem.endsWith("}")) cleanItem += "}"; // Ensure it's a valid JSON fragment

                String prodId = getJsonValue(cleanItem, "product_id");
                String totalQty = getJsonValue(cleanItem, "total_qty");

                if (prodId != null && totalQty != null) {
                    purchases.put(Integer.parseInt(prodId), Integer.parseInt(totalQty));
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseManager] Error parsing purchases: " + e.getMessage());
        }
        return purchases;
    }


    public static boolean placeOrder(int prodId, int userId, int qty, int newStock){
        String sql = "BEGIN TRANSACTION; " +
                "UPDATE products SET quantity = ? WHERE id = ?; " +
                "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, 'Success'); " +
                "COMMIT;";

        String response = sendRemoteQuery(sql, newStock, prodId, prodId, userId, qty);

        return response != null && !response.contains("error");
    }

    public static boolean cancelOrder(int orderId, int prodId, int restoredStock)  {
        String sql = "BEGIN TRANSACTION; " +
                "UPDATE products SET quantity = ? WHERE id = ?; " +
                "UPDATE orders SET status = 'Cancelled' WHERE id = ?; " +
                "COMMIT;";
        String response = sendRemoteQuery(sql, restoredStock, prodId, orderId);
        return response != null && !response.contains("error");
    }


    public static String getUserNameById(int userId){
        String response = sendRemoteQuery("SELECT username FROM users WHERE id = ?", userId);
        return getJsonValue(response, "username");
    }

    public static User getUserById(int id) throws SQLException {
        String response = sendRemoteQuery("SELECT * FROM users WHERE id = ?", id);
        if (response == null || response.contains("null")) return null;

        return parseUserFromJson(response);
    }

    public static User parseUserFromJson(String json) {
        String id = getJsonValue(json, "id");
        String username = getJsonValue(json, "username");
        String email = getJsonValue(json, "email");
        String password = getJsonValue(json, "password");

        if (id == null) return null;
        return new User(Integer.parseInt(id), username, email, password);
    }

    public static Product parseProductFromJson(String json) {
        String id = getJsonValue(json, "id");
        String name = getJsonValue(json, "name");
        String desc = getJsonValue(json, "description");
        String price = getJsonValue(json, "price");
        String qty = getJsonValue(json, "quantity");

        if (id == null) return null;
        return new Product(Integer.parseInt(id), name, desc, Float.parseFloat(price), Integer.parseInt(qty));
    }

    /**
     * Extracts the value for a specific key from a JSON string.
     * Supports both String values (removes quotes) and Numeric values.
     */
    private static String getJsonValue(String json, String key) {
        if (json == null || key == null) return null;

        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex + keyPattern.length());
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        int nextComma = json.indexOf(",", valueStart);
        int nextBrace = json.indexOf("}", valueStart);

        int end;
        if (nextComma != -1 && nextBrace != -1) {
            end = Math.min(nextComma, nextBrace);
        } else {
            end = (nextComma != -1) ? nextComma : nextBrace;
        }

        if (end == -1) return null;

        String value = json.substring(valueStart, end).trim();

        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        return value;
    }



    public static void initializeTables() throws SQLException {
        String userTable = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, email TEXT NOT NULL, password TEXT NOT NULL);";
        String productTable = "CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, price REAL NOT NULL, quantity INTEGER NOT NULL);";
        String orderTable = "CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, user_id INTEGER, quantity INTEGER NOT NULL, status TEXT NOT NULL);";

        // Execute these via the REMOTE service, not via getConnection()
        sendRemoteQuery(userTable);
        sendRemoteQuery(productTable);
        sendRemoteQuery(orderTable);
        sendRemoteQuery("CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);");
        sendRemoteQuery("CREATE INDEX IF NOT EXISTS idx_orders_product ON orders(product_id);");

        System.out.println("[DatabaseManager] Remote tables verified/initialized.");
    }


    public static void saveUserFull(int id, String username, String email, String password){
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
        sendRemoteQuery(sql, id, username, email, password);
    }

    public static void updateUser(int id, String username, String email, String password) throws SQLException {
        String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
        sendRemoteQuery(sql, username, email, password, id);
    }

    public static void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        sendRemoteQuery(sql, id);
    }

    static void main() {
        // 1. Check connectivity before starting the server
        if (!DatabaseManager.isDatabaseHealthy()) {
            System.err.println("Critical Error: Database Service is offline. Exiting...");
            System.exit(1);
        }
    }
}
