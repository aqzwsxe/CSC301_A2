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
            .build();

    private static Connection getConnection() throws SQLException{
        System.out.println("[DB] Connecting to: " + dbUrl);
        Connection connection = DriverManager.getConnection(dbUrl);
        try(Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");        }
        return connection;
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
                    .get();
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
        dbUrl = url;
    }

    public static void clearAllData() throws SQLException{
        try(Connection connection=getConnection();
            Statement stat = connection.createStatement();
        ) {
            stat.executeUpdate("DELETE FROM orders;");
            stat.executeUpdate("DELETE FROM products;");
            stat.executeUpdate("DELETE FROM users;");

            try {
                stat.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('orders', 'products', 'users');");
            }catch (SQLException e){

            }

        }
    }


    public static synchronized void saveOrder(int prodId, int userId, int qty, String status) throws SQLException{
        // Synchronization signature: ensures that only one thread can execute that method for a specific instance of the class
        String json = String.format("{\"sql\": \"INSERT...\", \"params\": [%d, %d]}", prodId, userId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dbServiceUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Send the request over the network to the VM
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }



    public static Order getOrderById(int orderId){
        String sql = "SELECT * FROM orders WHERE id = ?";
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
        ) {
            preparedStatement.setInt(1, orderId);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()){
                    Order order = new Order(rs.getInt("product_id"),
                                        rs.getInt("user_id"),
                                        rs.getInt("quantity"),
                                        rs.getString("status")
                            );
                    order.setId(rs.getInt("id"));
                    return order;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching order " + orderId + ": " + e.getMessage());
        }
        return null;
    }



    public static synchronized int  saveUser(String name, String email) throws SQLException{
        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, 'default')";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ){
            preparedStatement.setString(1,name);
            preparedStatement.setString(2,email);
            preparedStatement.executeUpdate();

            try(ResultSet rs = preparedStatement.getGeneratedKeys()){
                if(rs.next()){
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public static synchronized boolean saveProduct(int id, String name, String description, float price, int quantity){
        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";

        String response = sendRemoteQuery(sql, id, name, description, price, quantity);

        return response != null && !response.contains("error");
    }

    public static Product  getProductById(int productId){
        String sql = "SELECT * FROM products WHERE id = ?";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setInt(1,productId);
            try(ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()){
                    return new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getFloat("price"),
                            rs.getInt("quantity")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public  static void deleteProduct(int id, String name, float price, int quantity){
        String sql = "DELETE FROM products WHERE id = ? AND name = ? AND price = ? AND quantity = ?";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setInt(1,id);
            preparedStatement.setString(2, name);
            preparedStatement.setFloat(3, price);
            preparedStatement.setInt(4, quantity);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public static void updateProductQuantity(int productId, int newQuantity){
        String sql = "UPDATE products SET quantity = ? WHERE id = ?";
        sendRemoteQuery(sql, newQuantity, productId);
    }

    public static void updateProduct(int id, String name, String description, float price, int quantity){
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, quantity = ? WHERE id = ?";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
           preparedStatement.setString(1, name);
           preparedStatement.setString(2, description);
           preparedStatement.setFloat(3, price);
           preparedStatement.setInt(4, quantity);
           preparedStatement.setInt(5, id);
           preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateOrderStatus(int orderId, String status){
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
        ) {
            preparedStatement.setString(1, status);
            preparedStatement.setInt(2, orderId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating order status: " + e.getMessage());
        }
    }


    public static Map<Integer, Integer> getUserPurchases(int userId){
        Map<Integer, Integer> purchases = new HashMap<>();
        String sql = "SELECT product_id, SUM(quantity) as total_qty " +
                "FROM orders " +
                "WHERE user_id = ? AND status = 'Success' " +
                "GROUP BY product_id";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ){
            preparedStatement.setInt(1,userId);
            try (ResultSet rs = preparedStatement.executeQuery();) {
                while (rs.next()){
                    purchases.put(rs.getInt("product_id"),rs.getInt("total_qty"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return purchases;
    }


    public static synchronized boolean placeOrder(int prodId, int userId, int qty, int newStock){
        String sql = "BEGIN TRANSACTION; " +
                "UPDATE products SET quantity = ? WHERE id = ?; " +
                "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, 'Success'); " +
                "COMMIT;";

        String response = sendRemoteQuery(sql, newStock, prodId, prodId, userId, qty);

        return response != null && !response.contains("error");
    }

    public static boolean cancelOrder(int orderId, int prodId, int restoredStock)  {
        String updateStockSql = "UPDATE products SET quantity = ? WHERE id = ?";
        String updateOrderSql = "UPDATE orders SET status = 'Cancelled' WHERE id = ?";


        try(Connection conn = getConnection();) {
              conn.setAutoCommit(false);

              try(PreparedStatement ps1 = conn.prepareStatement(updateStockSql);
                  PreparedStatement ps2 = conn.prepareStatement(updateOrderSql)
              ) {
                  ps1.setInt(1, restoredStock);
                  ps1.setInt(2, prodId);
                  ps1.executeUpdate();

                  ps2.setInt(1, orderId);
                  ps2.executeUpdate();
                  conn.commit();
                  return true;
              }catch (SQLException e){
                  try {
                      // 1. Log the specific database error for debugging
                      System.err.println("Transaction failed, rolling back. Reason: " + e.getMessage());

                      // 2. Perform the rollback
                      conn.rollback();
                  } catch (SQLException rollbackEx) {
                      // 3. Handle cases where the rollback itself fails
                      System.err.println("Rollback failed: " + rollbackEx.getMessage());
                  }
                  return false;
              }



        }catch (SQLException e){

                return false;

        }

    }


    public static String getUserNameById(int userId){
        String sql = "SELECT username FROM users WHERE id = ?";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setInt(1,userId);
            try(ResultSet rs = preparedStatement.executeQuery()) {
                if(rs.next()){
                    return  rs.getString("username");
                }
            }
        }catch (SQLException e){
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public static User getUserById(int id) throws SQLException {
        String response = sendRemoteQuery("SELECT * FROM users WHERE id = ?", id);
        if (response == null || response.contains("null")) return null;

        // Parse the JSON string from the VM back into a User object
        // (Assuming your VM returns something like {"id": 1, "username": "...", ...})
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
        //Enable WAL model for multiple threads to read simultaneously
        String userTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL, " +
                "email TEXT NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        String productTable = "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "price REAL NOT NULL, " + // FLOAT -> REAL
                "quantity INTEGER NOT NULL" +
                ");";

        String orderTable = "CREATE TABLE IF NOT EXISTS orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER, " + // Removed NOT NULL
                "user_id INTEGER, " +    // Removed NOT NULL
                "quantity INTEGER NOT NULL, " +
                "status TEXT NOT NULL, " +
                "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL" +
                ");";
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()
        ){
            statement.execute("PRAGMA foreign_keys = ON;");
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute(userTable);
            statement.execute(productTable);
            statement.execute(orderTable);
            // prevent the full table scan
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_product ON orders(product_id);");
            System.out.println("[DatabaseManager] Tables initialized successfully.");

        } catch (SQLException e){
            System.err.println("[DatabaseManager] Error initializing tables: " + e.getMessage());
        }
    }


    public static synchronized int saveUserFull(int id, String username, String email, String password){
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateUser(int id, String username, String email, String password) throws SQLException {
        String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
        }
    }

    public static void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }


}
