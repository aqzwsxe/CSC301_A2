package Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import io.javalin.Javalin;

// To run this loadBalancer: java -jar target/A2_Project-1.0-SNAPSHOT-jar-with-dependencies.jar
public class LoadBalancer {
    private static final List<String> ORDER_SERVICES = List.of(
            "http://142.1.46.9:8081");

    private static final AtomicInteger counter = new AtomicInteger(0);
    // Optimized: Uses Virtual Threads to handle massive concurrent requests
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) {
        // Standard initialization - stable across all Javalin 6 versions
        Javalin app = Javalin.create().start("0.0.0.0", 8080);

        app.post("/order", ctx -> {
            String target = ORDER_SERVICES.get(counter.getAndIncrement() % ORDER_SERVICES.size());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target + "/process"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(ctx.body()))
                    .build();

            // THIS IS THE KEY: ctx.future makes this non-blocking.
            // This is what allows you to handle thousands of requests.
            ctx.future(() -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> ctx.status(res.statusCode()).result(res.body()))
                    .exceptionally(e -> {
                        ctx.status(500).result("Order Service unreachable: " + e.getMessage());
                        return null;
                    })
            );
        });

        System.out.println("Load Balancer is running on port 8080...");
    }

}
