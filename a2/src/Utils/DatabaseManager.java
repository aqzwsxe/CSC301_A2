package Utils;
import OrderService.Order;
import ProductService.Product;
import UserService.User;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {
    private static String dbUrl = "jdbc:sqlite:301A2.db?timeout=5000";
//    private static DBConfig config = DBConfig.load1();


    private static Connection getConnection() throws SQLException{
        Connection connection = DriverManager.getConnection(dbUrl);
        try(Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");        }
        return connection;
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


    public static void saveOrder(int prodId, int userId, int qty, String status) throws SQLException{
        String sql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
        try(Connection connection=getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
        ){
            preparedStatement.setInt(1, prodId);
            preparedStatement.setInt(2, userId);
            preparedStatement.setInt(3, qty);
            preparedStatement.setString(4, status);
            preparedStatement.executeUpdate();
        }
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



    public static int saveUser(String name, String email) throws SQLException{
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

    public static void saveProduct(int id, String name, String description, float price, int quantity){
        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
        try(Connection connection = getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ){
            preparedStatement.setInt(1,id);
            preparedStatement.setString(2,name);
            preparedStatement.setString(3,description);
            preparedStatement.setFloat(4,price);
            preparedStatement.setInt(5,quantity);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        try(Connection connection=getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setInt(1,newQuantity);
            preparedStatement.setInt(2,productId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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


    public static boolean placeOrder(int prodId, int userId, int qty, int newStock){
        String updateStockSql = "UPDATE products SET quantity = ? WHERE id = ?";
        String insertOrderSql = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, 'Success')";



        try(Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try(PreparedStatement updateStmt = conn.prepareStatement(updateStockSql);
            PreparedStatement insertStmt = conn.prepareStatement(insertOrderSql)
            ) {
                updateStmt.setInt(1, newStock);
                updateStmt.setInt(2, prodId);
                updateStmt.executeUpdate();

                insertStmt.setInt(1,prodId);
                insertStmt.setInt(2, userId);
                insertStmt.setInt(3, qty);
                insertStmt.executeUpdate();
                conn.commit();
                return  true;
            }
            catch (SQLException e){
                conn.rollback();
                return false;
            }

        } catch (SQLException e) {
            return false;
        }
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
                  conn.rollback();
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
        String sql = "SELECT * FROM users WHERE id = ?";
        try(Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(sql)

        ) {
            preparedStatement.setInt(1, id);
            try (ResultSet rs = preparedStatement.executeQuery()){
                if(rs.next()){
                    return new User(rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password")
                    );
                }
            }
        }
        return null;
    }


    public static void initializeTables() throws SQLException {
        String userTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY, " +
                "username TEXT NOT NULL, " +
                "email TEXT NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        String productTable = "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY, " +
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


    public static void saveUserFull(int id, String username, String email, String password){
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);
            pstmt.executeUpdate();
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
