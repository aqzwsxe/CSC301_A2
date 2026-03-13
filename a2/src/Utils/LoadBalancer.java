package Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.javalin.Javalin;import org.eclipse.jetty.util.thread.QueuedThreadPool;

// To run this loadBalancer: java -jar target/A2_Project-1.0-SNAPSHOT-jar-with-dependencies.jar
public class LoadBalancer {
    private static List<String> orderServicePool;
    private static final AtomicInteger orderCounter = new AtomicInteger(0);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) {
        String configPath = (args.length > 0) ? args[0] : "config.json";

        try {
            orderServicePool = ConfigReader.getServicePool(configPath, "OrderService");
            System.out.println("Load Balancer initialized with " + orderServicePool.size() + " targets.");
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

        app.get("/*", ctx -> handleProxy(ctx));
        app.post("/*", ctx -> handleProxy(ctx));
        app.delete("/*", ctx -> handleProxy(ctx));
        app.put("/*", ctx -> handleProxy(ctx));

        System.out.println("Load Balancer active on " + lbPort);
    }

    private static void handleProxy(io.javalin.http.Context ctx) {
        int index = Math.abs(orderCounter.getAndIncrement() % orderServicePool.size());
        String targetBaseUrl = orderServicePool.get(index);

        String targetUrl = targetBaseUrl + ctx.path().replaceAll("^/+", "/");
        System.out.println("Forwarding " + ctx.method() + " " + ctx.path() + " -> " + targetUrl);
        forwardRequest(ctx, targetUrl);
    }

    private static void forwardRequest(io.javalin.http.Context ctx, String targetUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(targetUrl));
        String method = ctx.method().name();
        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(ctx.body()));
        } else if (method.equals("DELETE")) {
            builder.DELETE();
        } else {
            builder.GET();
        }

        ctx.future(() ->
                httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> {
                            ctx.status(res.statusCode());
                            ctx.result(res.body());
                        })
                        .exceptionally(e -> {
                            ctx.status(502).result("Load Balancer Error: Cannot reach " + targetUrl);
                            return null;
                        })
        );
    }

}
