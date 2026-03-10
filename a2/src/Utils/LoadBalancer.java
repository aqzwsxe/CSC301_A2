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
    private static final String ORDER_SERVICE_IP = "142.1.46.12";   // pc09

    private static final List<String> ORDER_PORTS = IntStream.rangeClosed(16001, 16007).mapToObj(String::valueOf).toList();


    private static final AtomicInteger orderCounter = new AtomicInteger(0);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) {
        int lbPort = 18001;
        Javalin app = Javalin.create().start("0.0.0.0", lbPort);

        // Using "/*" ensures /user/1001 is caught correctly
        app.get("/*", ctx -> handleProxy(ctx));
        app.post("/*", ctx -> handleProxy(ctx));
        app.delete("/*", ctx -> handleProxy(ctx));
        app.put("/*", ctx -> handleProxy(ctx));

        System.out.println("Load Balancer active on " + lbPort);
    }

    private static void handleProxy(io.javalin.http.Context ctx) {
        String port = ORDER_PORTS.get(orderCounter.getAndIncrement() % ORDER_PORTS.size());
        String targetUrl = "http://" + ORDER_SERVICE_IP + ":" + port + ctx.path();

        System.out.println("Forwarding " + ctx.method() + " " + ctx.path() + " -> Port " + port);
        forwardRequest(ctx, targetUrl);
    }

    private static void forwardRequest(io.javalin.http.Context ctx, String targetUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(targetUrl));

        // Map the method and body correctly
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
                            ctx.status(502).result("Load Balancer Error: Cannot reach Order Service at " + targetUrl);
                            return null;
                        })
        );
    }

}
