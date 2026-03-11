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
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        props.setProperty("password", "password123");
        // Do NOT store this in a static variable
        return DriverManager.getConnection(dbUrl, props);
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


//    public static void saveOrder(int prodId, int userId, int qty, String status) throws SQLException{
//        String sql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//            pstmt.setInt(1, prodId);
//            pstmt.setInt(2, userId);
//            pstmt.setInt(3, qty);
//            pstmt.setString(4, status);
//            pstmt.executeUpdate();
//        }
//    }


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





//    public static  int  saveUser(String name, String email) throws SQLException{
//        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, 'default')";
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//            pstmt.setString(1, name);
//            pstmt.setString(2, email);
//
//            // executeUpdate returns the number of rows affected
//            int affectedRows = pstmt.executeUpdate();
//            return (affectedRows > 0) ? 1 : -1;
//        } catch (SQLException e) {
//            System.err.println("[DB Error] saveUser failed: " + e.getMessage());
//            return -1;
//        }
//    }

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

    public static void deleteProduct(int id, String name, float price, int quantity) {
        // Only filter by ID for the actual execution
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                System.out.println("[DB Warning] deleteProduct: No row found with ID " + id);
            }
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


    public static Map<Integer, Integer> getUserPurchases(int userId) throws SQLException {
        Map<Integer, Integer> purchases = new HashMap<>();
        String sql = "SELECT product_id, quantity FROM user_purchases WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    purchases.put(rs.getInt("product_id"), rs.getInt("quantity"));
                }
            }
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

            String purchaseSql = "INSERT INTO user_purchases (user_id, product_id, quantity) " +
                    "VALUES (?, ?, ?) " +
                    "ON CONFLICT (user_id, product_id) " +
                    "DO UPDATE SET quantity = user_purchases.quantity + EXCLUDED.quantity";
            try (PreparedStatement psPur = conn.prepareStatement(purchaseSql)) {
                psPur.setInt(1, userId);
                psPur.setInt(2, prodId);
                psPur.setInt(3, qty);
                psPur.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public static boolean cancelOrder(int orderId, int prodId, int restoredStock)  {
        Connection conn = null;
        try{
            conn = getConnection();
            conn.setAutoCommit(false);

            // 1. Fetch the order details first to know the quantity and user
            int qty = 0;
            int userId = 0;
            String findOrder = "SELECT user_id, quantity FROM orders WHERE id = ?";
            try (PreparedStatement psFind = conn.prepareStatement(findOrder)) {
                psFind.setInt(1, orderId);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                        qty = rs.getInt("quantity");
                    } else {
                        return false; // Order not found
                    }
                }
            }

            // 2. Restore the stock
            String updateStockSql = "UPDATE products SET quantity = ? WHERE id = ?";
            try (PreparedStatement psStock = conn.prepareStatement(updateStockSql)) {
                psStock.setInt(1, restoredStock);
                psStock.setInt(2, prodId);
                psStock.executeUpdate();
            }

            // 3. Update the purchase history (Subtract the quantity)
            String subPurchaseSql = "UPDATE user_purchases SET quantity = quantity - ? " +
                    "WHERE user_id = ? AND product_id = ?";
            try (PreparedStatement psSub = conn.prepareStatement(subPurchaseSql)) {
                psSub.setInt(1, qty);
                psSub.setInt(2, userId);
                psSub.setInt(3, prodId);
                psSub.executeUpdate();
            }

            // 4. Mark the order as Cancelled
            String updateOrderSql = "UPDATE orders SET status = 'Cancelled' WHERE id = ?";
            try (PreparedStatement psOrder = conn.prepareStatement(updateOrderSql)) {
                psOrder.setInt(1, orderId);
                psOrder.executeUpdate();
            }

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
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
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
                "id INTEGER PRIMARY KEY, " +
                "username VARCHAR(255) NOT NULL, " +
                "email VARCHAR(255) NOT NULL, " +
                "password VARCHAR(255) NOT NULL);";

        String productTable = "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY, " +
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

        String user_purchases = "CREATE TABLE IF NOT EXISTS user_purchases (\n" +
                "    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,\n" +
                "    product_id INTEGER REFERENCES products(id) ON DELETE CASCADE,\n" +
                "    quantity INTEGER,\n" +
                "    PRIMARY KEY (user_id, product_id)\n" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(userTable);
            stmt.execute(productTable);
            stmt.execute(orderTable);
            stmt.execute(user_purchases);

            // PostgreSQL handles INDEX IF NOT EXISTS similarly
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_product ON orders(product_id);");

            System.out.println("[DatabaseManager] PostgreSQL tables verified/initialized.");
        } catch (SQLException e) {
            System.err.println("[DB Error] Initialization failed: " + e.getMessage());
            throw e;
        }
    }


    public static int saveUserFull(int id, String username, String email, String password) {
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);

            pstmt.executeUpdate();
            return 200; // Success: Created

        } catch (SQLException e) {
            // "23505" is the standard Postgres code for Unique Violation (Duplicate ID)
            if ("23505".equals(e.getSQLState())) {
                return 409; // Conflict
            }
            System.err.println("[DB Error] Failed to save user " + id + ": " + e.getMessage());
            return 400; // General Bad Request
        }
    }

    public static int deleteUserSecure(int id, String username, String email, String password) {
        // Add username to the WHERE clause to be fully compliant
        String sql = "DELETE FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                // If the user ID exists but the delete failed, it means username/email/password was wrong
                return (getUserById(id) == null) ? 404 : 401;
            }
            return 200;
        } catch (SQLException e) {
            System.err.println("[DB Error] Delete failed: " + e.getMessage());
            return 500;
        }
    }

    public static int updateUser(int id, String username, String email, String password) {
        String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.setInt(4, id);

            int affectedRows = pstmt.executeUpdate();

            // If 0 rows were updated, the ID doesn't exist in the database
            if (affectedRows == 0) {
                System.out.println("[DB Warning] Update failed: User ID " + id + " not found.");
                return 404;
            }

            return 200; // Success
        } catch (SQLException e) {
            System.err.println("[DB Error] Error updating user: " + e.getMessage());
            return 400; // Bad Request (e.g., data type mismatch)
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

    public static void recordPurchase(int userId, int productId, int quantity) throws SQLException {
        String sql = "INSERT INTO user_purchases (user_id, product_id, quantity) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id, product_id) " +
                "DO UPDATE SET quantity = user_purchases.quantity + EXCLUDED.quantity";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, productId);
            pstmt.setInt(3, quantity);
            pstmt.executeUpdate();
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
