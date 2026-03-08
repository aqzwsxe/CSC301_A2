package Utils;
import OrderService.Order;
import ProductService.Product;
import UserService.User;


import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DatabaseManager {
    private static String dbUrl = "jdbc:postgresql://142.1.46.8:5432/mydb";
    private static Connection connection;

    private static synchronized Connection getConnection() throws SQLException{
        if (connection == null || connection.isClosed()) {
            Properties props = new Properties();
            props.setProperty("user", "postgres");
            props.setProperty("password", "password123");

            connection = DriverManager.getConnection(dbUrl, props);
        }
        return connection;
    }

    public static boolean isDatabaseHealthy(){
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeQuery("SELECT 1").next();
        } catch (Exception e) {
            System.err.println("[HealthCheck] FAILED: " + e.getMessage());
            return false;
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
        String sql = "TRUNCATE TABLE orders, products, users RESTART IDENTITY CASCADE;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] All data cleared and sequences reset.");
        }
    }


    public static void saveOrder(int prodId, int userId, int qty, String status) throws SQLException{
        String sql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
        Thread.ofVirtual().start(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, prodId);
                pstmt.setInt(2, userId);
                pstmt.setInt(3, qty);
                pstmt.setString(4, status);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[DB Error] Failed to save order: " + e.getMessage());
            }
        });
    }


    public static Order getOrderById(int orderId){
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, orderId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Order order = new Order(
                            rs.getInt("product_id"),
                            rs.getInt("user_id"),
                            rs.getInt("quantity"),
                            rs.getString("status")
                    );
                    order.setId(rs.getInt("id"));
                    return order;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Error] getOrderById failed: " + e.getMessage());
        }
        return null;
    }





    public static  int  saveUser(String name, String email) throws SQLException{
        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, 'default')";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);

            // executeUpdate returns the number of rows affected
            int affectedRows = pstmt.executeUpdate();
            return (affectedRows > 0) ? 1 : -1;
        } catch (SQLException e) {
            System.err.println("[DB Error] saveUser failed: " + e.getMessage());
            return -1;
        }
    }

    public static  boolean saveProduct(int id, String name, String description, float price, int quantity){
        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, description);
            pstmt.setFloat(4, price);
            pstmt.setInt(5, quantity);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB Error] saveProduct failed: " + e.getMessage());
            return false;
        }
    }

    public static Product  getProductById(int productId){
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(rs.getInt("id"), rs.getString("name"),
                            rs.getString("description"), rs.getFloat("price"),
                            rs.getInt("quantity"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public  static void deleteProduct(int id, String name, float price, int quantity){
        String sql = "DELETE FROM products WHERE id = ? AND name = ? AND price = ? AND quantity = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, name);
            pstmt.setFloat(3, price);
            pstmt.setInt(4, quantity);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB Error] deleteProduct failed: " + e.getMessage());
        }
    }
    public static void updateProductQuantity(int productId, int newQuantity){
        String sql = "UPDATE products SET quantity = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, productId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB Error] updateProductQuantity failed: " + e.getMessage());
        }
    }

    public static void updateProduct(int id, String name, String description, float price, int quantity){
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, quantity = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setFloat(3, price);
            pstmt.setInt(4, quantity);
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB Error] updateProduct failed: " + e.getMessage());
        }
    }

    public static void updateOrderStatus(int orderId, String status){
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, orderId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB Error] updateOrderStatus failed: " + e.getMessage());
        }
    }


    public static Map<Integer, Integer> getUserPurchases(int userId){
        Map<Integer, Integer> purchases = new HashMap<>();
        // PostgreSQL handles aggregation much faster than manual JSON parsing
        String sql = "SELECT product_id, SUM(quantity) as total_qty " +
                "FROM orders " +
                "WHERE user_id = ? AND status = 'Success' " +
                "GROUP BY product_id";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    purchases.put(rs.getInt("product_id"), rs.getInt("total_qty"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return purchases;
    }


    public static boolean placeOrder(int prodId, int userId, int qty, int newStock){
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // 1. Update Stock
            String updateSql = "UPDATE products SET quantity = ? WHERE id = ?";
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, newStock);
                psUpdate.setInt(2, prodId);
                psUpdate.executeUpdate();
            }

            // 2. Insert Order
            String insertSql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, 'Success')";
            try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                psInsert.setInt(1, prodId);
                psInsert.setInt(2, userId);
                psInsert.setInt(3, qty);
                psInsert.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public static boolean cancelOrder(int orderId, int prodId, int restoredStock)  {
        Connection conn = null;
        try{
            conn = getConnection();
            // 1. Disable auto-commit to start a transaction block
            conn.setAutoCommit(false);


            // 2. Restore the stock in the products table
            String updateStockSql = "UPDATE products SET quantity = ? WHERE id = ?";
            try (PreparedStatement psStock = conn.prepareStatement(updateStockSql)) {
                psStock.setInt(1, restoredStock);
                psStock.setInt(2, prodId);
                psStock.executeUpdate();
            }

            // 3. Mark the order as Cancelled
            String updateOrderSql = "UPDATE orders SET status = 'Cancelled' WHERE id = ?";
            try (PreparedStatement psOrder = conn.prepareStatement(updateOrderSql)) {
                psOrder.setInt(1, orderId);
                psOrder.executeUpdate();
            }

            // 4. Commit both changes as a single unit
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            System.err.println("[DB Error] cancelOrder failed: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }


    public static String getUserNameById(int userId){
        String sql = "SELECT username FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Direct retrieval from the ResultSet - no more JSON parsing!
                    return rs.getString("username");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB Error] getUserNameById failed: " + e.getMessage());
        }
        return null;
    }

    public static User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getInt("id"), rs.getString("username"),
                            rs.getString("email"), rs.getString("password"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }




    public static void initializeTables() throws SQLException {
        String userTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id SERIAL PRIMARY KEY, " +
                "username VARCHAR(255) NOT NULL, " +
                "email VARCHAR(255) NOT NULL, " +
                "password VARCHAR(255) NOT NULL);";

        String productTable = "CREATE TABLE IF NOT EXISTS products (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(255) NOT NULL, " +
                "description TEXT, " +
                "price DECIMAL(10,2) NOT NULL, " +
                "quantity INTEGER NOT NULL);";

        String orderTable = "CREATE TABLE IF NOT EXISTS orders (" +
                "id SERIAL PRIMARY KEY, " +
                "product_id INTEGER REFERENCES products(id), " +
                "user_id INTEGER REFERENCES users(id), " +
                "quantity INTEGER NOT NULL, " +
                "status VARCHAR(50) NOT NULL);";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(userTable);
            stmt.execute(productTable);
            stmt.execute(orderTable);

            // PostgreSQL handles INDEX IF NOT EXISTS similarly
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_product ON orders(product_id);");

            System.out.println("[DatabaseManager] PostgreSQL tables verified/initialized.");
        } catch (SQLException e) {
            System.err.println("[DB Error] Initialization failed: " + e.getMessage());
            throw e;
        }
    }


    public static int saveUserFull(int id, String username, String email, String password){
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            // If the ID already exists, PostgreSQL will throw a Unique Violation error.
            System.err.println("[DB Error] Failed to save user " + id + ": " + e.getMessage());
            return -1;
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

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                System.out.println("[DB Warning] Update user failed: User ID " + id + " not found.");
            }
        } catch (SQLException e) {
            System.err.println("[DB Error] Error updating user: " + e.getMessage());
            throw e;
        }
    }

    public static void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                System.out.println("[DB Warning] Delete user failed: User ID " + id + " not found.");
            } else {
                System.out.println("[DB] User " + id + " deleted successfully.");
            }
        } catch (SQLException e) {
            // If there are existing orders for this user, Postgres will throw a
            // Foreign Key Violation error here.
            System.err.println("[DB Error] Error deleting user: " + e.getMessage());
            throw e;
        }
    }

    static void main() {
        // 1. Check connectivity before starting the server
        if (!DatabaseManager.isDatabaseHealthy()) {
            System.err.println("Critical Error: Database Service is offline. Exiting...");
            System.exit(1);
        }
    }
}
