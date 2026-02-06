package UserService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Handles the given user request and generate an appropriate response.
 */
public class UserHandler implements HttpHandler {

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
        System.out.println("[User] method: " + method);
        System.out.println("[User] path: " + path);
        try {
            if(method.equals("GET")){
                System.out.println("Try to call handle get");
                handleGet(exchange,path);
            } else if (method.equals("POST")) {
                handlePost(exchange);
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    /**
     * Hash the input string using SHA256
     *
     * @param password1 the hashing string
     * @return the hashed string
     */
    private String hash_helper(String password1) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA256");
        byte[] hashBytes = md.digest(password1.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for(byte b: hashBytes){
            String hex = Integer.toHexString(0xff & b);
            if(hex.length()==1){
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * Check if an email is valid, an valid email must have exactly one @
     *
     * @param str1 the email address
     * @return a Boolean of whether the email address contain exactly one @
     */
    private boolean checkEmail(String str1){
        int counter = 0 ;
        char target = '@';
        for(int i = 0; i < str1.length(); i++){
            char temp = str1.charAt(i);
            if (temp==target){
                counter++;
            }
        }
        return counter==1;
    }

    /**
     * Handles a GET request to fetch a user by ID from the request path.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: user exists; response body is {@code user.toJson()}</li>
     *   <li>{@code 400}: malformed path or non-integer id; response body is {@code {}}</li>
     *   <li>{@code 404}: no user with the given id; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param path the request URI path; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handleGet(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if(parts.length < 3){
            // SendResponse
            sendResponse(exchange, 400, "{}");
            return;
        }
        try {
            int id = Integer.parseInt(parts[2]);
            User user = UserService.userDatabase.get(id);
            // 404 or 400
            if(user==null){
                sendResponse(exchange, 404, "{}");
                return;
            }
            String hashed_password = hash_helper(user.getPassword());
            String res1 = String.format("{\n" +
                    "        \"id\": %d,\n" +
                    "        \"username\": \"%s\",\n" +
                    "        \"email\": \"%s\",\n" +
                    "        \"password\": \"%s\"\n" +
                    "    }", user.getId(), user.getUsername(), user.getEmail(), hashed_password);

            if(user!=null){
                sendResponse(exchange, 200, res1);
                return;
            }
            else{
                sendResponse(exchange,404, "{}");
                return;
            }
        }catch (NumberFormatException e){
            sendResponse(exchange, 400, "{}");
            return;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }


    }
    // bridge the gap between a raw HTTP request and the User data
    // handle both Get requests and the Post requests


    /**
     * Handles a POST request to and redirects to handler based on the command.
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @throws IOException if an I/O error occurs while sending the response
     */
    private void handlePost(HttpExchange exchange) throws IOException, NoSuchAlgorithmException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
//        System.out.println("inside the handlePost: "+body);

        String command = getJsonValue(body, "command");
        String idStr = getJsonValue(body, "id");
        // this part handles create and delete and update
        if(command==null || idStr == null || idStr.equals("invalid-info")){
            sendResponse(exchange, 400, "{}");
            return;
        }
        int id = Integer.parseInt(idStr);

        // command: represent the value associated with the command key inside the JSON payload
        // that the client sends to your server
        switch (command){
            case "create":
                handleCreate(exchange,id,body);
                break;
            case "update":
                handleUpdate(exchange,id,body);
                break;
            case "delete":
                handleDelete(exchange, id, body);
                break;
            default:
                sendResponse(exchange, 400, "{}");
                return;

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
        // Add new line character, so the terminal prompt will start a new line
//        System.out.println("The user send the request back to ISCS");
        String response1 = response + "\n";
        byte[] bytes = response1.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try(OutputStream os = exchange.getResponseBody()){
            os.write(bytes);
        }
    }

    /**
     * Handles a create command. Create a new user in database if all fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: user creation success; response body is {@code user.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code {}}</li>
     *   <li>{@code 409}: duplicate user id; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the user ID of the user to be created/updated
     * @param body a JSON string containing the user id, username, email, password
     * @throws IOException if an I/O error occurs while sending the response
     */
    public  void  handleCreate(HttpExchange exchange, int id, String body) throws IOException, NoSuchAlgorithmException {
        System.out.println("Start the handle create method");
        if(UserService.userDatabase.containsKey(id)){
            System.out.println("User already exist");
            sendResponse(exchange,409,"{}");
            return;
        }

        String username = getJsonValue(body, "username");
        if(username.isEmpty()){
            sendResponse(exchange,400,"{}");
            return;
        }
        String email = getJsonValue(body, "email");
        String password = getJsonValue(body, "password");
        if(username==null || username.isEmpty()||
                email==null || email.isEmpty()||
                password==null||password.isEmpty()){
            System.out.println("sth is null");
            sendResponse(exchange,400, "{}");
            return;
        }
        if (!checkEmail(email)){
            System.out.println("The email is invalid");
            sendResponse(exchange,400, "{}");
            return;
        }



        User newUser = new User(id, username, email, password);
        UserService.userDatabase.put(id,newUser);
        String hashed_password = hash_helper(password);
        String res1 = String.format("{\n" +
                "        \"id\": %d,\n" +
                "        \"username\": \"%s\",\n" +
                "        \"email\": \"%s\",\n" +
                "        \"password\": \"%s\"\n" +
                "    }", id, username, email, hashed_password);
        System.out.println("successfully create the user");
        sendResponse(exchange, 200, res1);
        return;


    }

    /**
     * Handles a update command. Update an existing user in database if updating fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product update success; response body is {@code product.toJson()}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code {}}</li>
     *   <li>{@code 404}: product id does not exist; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the product id of the product attempt to create
     * @param body a JSON string containing the user id, username, email, password
     * @throws IOException if an I/O error occurs while sending the response
     */
    public  void handleUpdate(HttpExchange exchange, int id, String body) throws IOException, NoSuchAlgorithmException {
        User user = UserService.userDatabase.get(id);
        if(user==null){
            sendResponse(exchange, 404, "{}");
            return;
        }

        String newUsername = getJsonValue(body, "username");
        String newEmail = getJsonValue(body, "email");
        String newPassword = getJsonValue(body, "password");
        // 1: if the email has an invalid type (the email does not have exact one @)
        // 2: newEmail if empty
        if (newEmail!=null && (newEmail.isEmpty() || !checkEmail(newEmail))) {
            sendResponse(exchange, 400, "{}");
            return;
        }

        if(newPassword != null && newPassword.isEmpty()){
            sendResponse(exchange, 400, "{}");
            return;
        }


        // According to the instruction: only update the info that are exist
        if(newUsername!=null){
            user.setUsername(newUsername);
        }
        if(newEmail != null){
            user.setEmail(newEmail);
        }
        if(newPassword != null){
            user.setPassword(newPassword);
        }
        String hashed_password = hash_helper(user.getPassword());
        String res1 = String.format("{\n" +
                "        \"id\": %d,\n" +
                "        \"username\": \"%s\",\n" +
                "        \"email\": \"%s\",\n" +
                "        \"password\": \"%s\"\n" +
                "    }", id, user.getUsername(), user.getEmail(), hashed_password);
        sendResponse(exchange, 200, res1);
        return;
    }

    /**
     * Handles a delete command. Delete an existing user in database if all fields are valid.
     *
     * <p><b>Responses:</b>
     * <ul>
     *   <li>{@code 200}: product delete success; response body is {@code {}}</li>
     *   <li>{@code 400}: missing fields or invalid field type; response body is {@code {}}</li>
     *   <li>{@code 404}: product id does not exist; response body is {@code {}}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange used to read and write the response; must be non-null
     * @param id the product id of the product attempt to create
     * @param body a JSON string containing the user id, username, email, password
     * @throws IOException if an I/O error occurs while sending the response
     */
    public void handleDelete(HttpExchange exchange, int id, String body) throws IOException, NoSuchAlgorithmException {
        User user = UserService.userDatabase.get(id);
        if(user==null){
            sendResponse(exchange,404, "{}");
            return;
        }


        String reqUser = getJsonValue(body,"username");
        String reqEmail = getJsonValue(body, "email");
        String reqPassword = getJsonValue(body, "password");

        if(reqUser == null || reqEmail == null || reqPassword == null || reqUser.equals("invalid-info") || reqEmail.equals("invalid-info") || reqPassword.equals("invalid-info")){
            sendResponse(exchange, 400, "{}");
            return;
        }
        String hashedStored = hash_helper(user.getPassword());
        String hashedIncoming = hash_helper(reqPassword);

        boolean match = user.getUsername().equals(reqUser) &&
                user.getEmail().equals(reqEmail) &&
                hashedStored.equals(hashedIncoming);

        if(match){
            UserService.userDatabase.remove(id);
            sendResponse(exchange, 200, "{}");
            return;
        } else{
            sendResponse(exchange, 404, "{}");
            return;
        }

    }


}
