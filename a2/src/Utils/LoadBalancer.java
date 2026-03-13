package Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.javalin.Javalin;import org.eclipse.jetty.util.thread.QueuedThreadPool;import redis.clients.jedis.Jedis;import redis.clients.jedis.JedisPool;import redis.clients.jedis.JedisPoolConfig;

// To run this loadBalancer: java -jar target/A2_Project-1.0-SNAPSHOT-jar-with-dependencies.jar
public class LoadBalancer {
    private static List<String> orderServicePool;
    private static List<String> productServicePool;
    private static final AtomicInteger orderCounter = new AtomicInteger(0);
    private static List<String> userServicePool;
    private static final JedisPool jedisPool = new JedisPool(new JedisPoolConfig(), "142.1.114.76", 6379);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) {
        String configPath = (args.length > 0) ? args[0] : "config.json";

        try {
            userServicePool = ConfigReader.getServicePool(configPath, "UserService");
            productServicePool = ConfigReader.getServicePool(configPath, "ProductService");
            orderServicePool = ConfigReader.getServicePool(configPath, "OrderService");
            System.out.println("LB initialized: User(" + userServicePool.size() +
                    "), Product(" + productServicePool.size() +
                    "), Order(" + orderServicePool.size() + ")");
        } catch (Exception e) {
            System.err.println("Failed to load OrderService pool: " + e.getMessage());
            System.exit(1);
        }

        int lbPort = 18001;
        Javalin app = Javalin.create(config -> {
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setVirtualThreadsExecutor(Executors.newVirtualThreadPerTaskExecutor());
            threadPool.setMaxThreads(500);
            config.jetty.threadPool = threadPool;
        }).start("0.0.0.0", lbPort);

        app.before("/*", ctx -> { /* logging logic if needed */ });

        app.get("/*", ctx -> handleProxy(ctx));
        app.post("/*", ctx -> handleProxy(ctx));
        app.delete("/*", ctx -> handleProxy(ctx));
        app.put("/*", ctx -> handleProxy(ctx));

        System.out.println("Load Balancer active on " + lbPort);
    }

    private static void handleProxy(io.javalin.http.Context ctx) {
        String path = ctx.path();
        List<String> selectedPool;

        if (path.startsWith("/user")) {
            selectedPool = userServicePool;
        } else if (path.startsWith("/product")) {
            selectedPool = productServicePool;
        } else if (path.startsWith("/order")) {
            selectedPool = orderServicePool;
        } else {
            selectedPool = orderServicePool;
        }

        if (selectedPool == null || selectedPool.isEmpty()) {
            ctx.status(502).result("No backend targets available for: " + path);
            return;
        }

        // --- RE-ADDED REDIS LOOKUP ---
        if (ctx.method().name().equals("GET")) {
            try (Jedis jedis = jedisPool.getResource()) {
                String cachedResponse = jedis.get(path);
                if (cachedResponse != null) {
                    ctx.status(200).result(cachedResponse);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Redis Lookup Error: " + e.getMessage());
            }
        }

        int index = Math.abs(orderCounter.getAndIncrement() % selectedPool.size());
        String targetBaseUrl = selectedPool.get(index);
        String targetUrl = targetBaseUrl + path.replaceAll("^/+", "/");

        forwardRequest(ctx, targetUrl);
    }
    private static void forwardRequest(io.javalin.http.Context ctx, String targetUrl) {
        String body = ctx.body(); // Store once to be safe across threads
        String method = ctx.method().name();
        String path = ctx.path();

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(targetUrl));

        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else if (method.equals("DELETE")) {
            builder.DELETE();
        } else {
            builder.GET();
        }

        ctx.future(() ->
                httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                if (method.equals("GET") && res.statusCode() == 200) {
                                    jedis.setex(path, 30, res.body());
                                }
                                else if (!method.equals("GET") && res.statusCode() < 300) {
                                    jedis.del(path);
                                    // Invalidate specific item cache if we are at the base /user or /product path
                                    if (path.equals("/user") || path.equals("/product")) {
                                        String id = getJsonValue(body, "id");
                                        if (id != null) {
                                            jedis.del(path + "/" + id);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("Redis Operation Error: " + e.getMessage());
                            }

                            ctx.status(res.statusCode());
                            ctx.result(res.body());
                        })
                        .exceptionally(e -> {
                            ctx.status(502).result("Load Balancer Error: Cannot reach " + targetUrl);
                            return null;
                        })
        );
    }


    private static String getJsonValue(String json, String key) {
        if (json == null || json.isEmpty()) return null;

        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex + keyPattern.length());
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        int end;
        if (json.charAt(valueStart) == '\"') {
            valueStart++;
            end = json.indexOf("\"", valueStart);
            if (end == -1) return null;
            return json.substring(valueStart, end);
        } else {
            int nextComma = json.indexOf(",", valueStart);
            int nextBrace = json.indexOf("}", valueStart);

            if (nextComma != -1 && nextBrace != -1) end = Math.min(nextComma, nextBrace);
            else if (nextComma != -1) end = nextComma;
            else end = nextBrace;

            if (end == -1) return null;
            return json.substring(valueStart, end).trim();
        }
    }
}
