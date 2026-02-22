package Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DBConfig {
    public String url = "jdbc:postgresql://localhost:5432/postgres"; // Default
    public String user = "postgres";                                // Default
    public String pass = "mysecretpassword";


    public static DBConfig load1(){
        DBConfig config1 = new DBConfig();
        File file = new File("dbConfig.json");
        if(file.exists()){
            try {
                String content = new String(Files.readAllBytes(Paths.get("dbConfig.json")));
                String pasrseUrl = getDBJsonValue(content, "url");
                if(pasrseUrl != null) {
                    config1.url = pasrseUrl;
                    System.out.println("The url: "+ pasrseUrl);
                }

                String parsedUser = getDBJsonValue(content, "user");
                if(parsedUser != null) {
                    config1.user = parsedUser;
                    System.out.println("The user: "+ parsedUser);
                }

                String parsedPass = getDBJsonValue(content, "pass");
                if(parsedPass != null) {
                    config1.pass = parsedPass;
                    System.out.println("The password: "+ parsedPass);
                }

                System.out.println("[DBConfig] Loaded custom configuration from dbConfig.json");

            } catch (IOException e) {
                System.err.println("[DBConfig] Error reading file, using lab defaults.");

            }
        }else{
            System.out.println("[DBConfig] No dbConfig.json found. Using default lab credentials.");
        }
        return config1;
    }

    private static String getDBJsonValue(String json, String key){
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


//    static void main() {
//        load1();
//    }

}
