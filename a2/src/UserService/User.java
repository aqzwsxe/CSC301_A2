package UserService;

/**
 * Represents a user with a unique id.
 *
 * <p><b>Invariants:</b>
 * <ul>
 *   <li>{@code id} always exists.</li>
 *   <li>{@code username} always exists and non-empty.</li>
 *   <li>{@code email} always exists and non-empty.</li>
 *   <li>{@code password} always exists and non-empty.</li>
 * </ul>
 */
public class User  {
    int id;
    String username;
    String email;
    String password;

    /**
     * Initializes a new User.
     *
     * @param id the user id
     * @param username the username; must be non-empty
     * @param email the user email; must be non-empty and exactly one @ present
     * @param password the user password
     */
    public User(int id, String username, String email, String password){
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    /**
     * Returns the user id
     *
     * @return the user id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the username
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the user email
     *
     * @return the user email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the user password
     *
     * @return the user password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Updates the user id
     *
     * @param id the user id
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Updates the username
     *
     * @param username the username; must be non-empty
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Updates the user email
     *
     * @param email the user email; must be non-empty and contain exactly one @
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Updates the user password
     *
     * @param password the user password; must be non-empty
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Convert the user information into json format.
     *
     * @return a JSON string containing the user's ID, username, and email
     */
    public String toJson(){
        return String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\"}",
                this.id, this.username,this.email);
    }
}
