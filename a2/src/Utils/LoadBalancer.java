package Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.javalin.Javalin;

// To run this loadBalancer: java -jar target/A2_Project-1.0-SNAPSHOT-jar-with-dependencies.jar
public class LoadBalancer {
    private static final String USER_SERVICE_IP = "142.1.46.9";    // pc06
    private static final String PRODUCT_SERVICE_IP = "142.1.46.10"; // pc07
    private static final String ORDER_SERVICE_IP = "142.1.46.12";   // pc09

    private static final List<String> USER_PORTS = IntStream.rangeClosed(14001, 14007).mapToObj(String::valueOf).toList();
    private static final List<String> PRODUCT_PORTS = IntStream.rangeClosed(15001, 15007).mapToObj(String::valueOf).toList();
    private static final List<String> ORDER_PORTS = IntStream.rangeClosed(16001, 16007).mapToObj(String::valueOf).toList();

    private static final AtomicInteger userCounter = new AtomicInteger(0);
    private static final AtomicInteger productCounter = new AtomicInteger(0);
    private static final AtomicInteger orderCounter = new AtomicInteger(0);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) {
        int lbPort = 18001;
        Javalin app = Javalin.create().start("0.0.0.0", lbPort);

        app.post("/user", ctx -> {
            String port = USER_PORTS.get(userCounter.getAndIncrement() % USER_PORTS.size());
            forwardRequest(ctx, "http://" + USER_SERVICE_IP + ":" + port + "/user");
        });
        app.get("/user/{id}", ctx -> {
            String port = USER_PORTS.get(userCounter.getAndIncrement() % USER_PORTS.size());
            forwardRequest(ctx, "http://" + USER_SERVICE_IP + ":" + port + "/user/" + ctx.pathParam("id"));
        });

        app.post("/product", ctx -> {
            String port = PRODUCT_PORTS.get(productCounter.getAndIncrement() % PRODUCT_PORTS.size());
            forwardRequest(ctx, "http://" + PRODUCT_SERVICE_IP + ":" + port + "/product");
        });
        app.get("/product/{id}", ctx -> {
            String port = PRODUCT_PORTS.get(productCounter.getAndIncrement() % PRODUCT_PORTS.size());
            forwardRequest(ctx, "http://" + PRODUCT_SERVICE_IP + ":" + port + "/product/" + ctx.pathParam("id"));
        });

        app.post("/order", ctx -> {
            String port = ORDER_PORTS.get(orderCounter.getAndIncrement() % ORDER_PORTS.size());
            forwardRequest(ctx, "http://" + ORDER_SERVICE_IP + ":" + port + "/order");
        });

        app.get("/user/purchased/{id}", ctx -> {
            String port = ORDER_PORTS.get(orderCounter.getAndIncrement() % ORDER_PORTS.size());
            forwardRequest(ctx, "http://" + ORDER_SERVICE_IP + ":" + port + "/user/purchased/" + ctx.pathParam("id"));
        });

        System.out.println("External Load Balancer active on " + lbPort + " routing to distributed lab machines.");
    }

    private static void forwardRequest(io.javalin.http.Context ctx, String targetUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(targetUrl));

        if (ctx.method().name().equals("POST")) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(ctx.body()));
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
                            ctx.status(502).result("Downstream service error: " + e.getMessage());
                            return null;
                        })
        );
    }

}
