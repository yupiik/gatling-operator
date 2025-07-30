package io.yupiik.gatling.controller.infra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.container.configuration.ConfigurationImpl;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public class Kubernetes implements AutoCloseable {
    private final HttpServer server;

    private final CrdHandler rootHandler;

    private HttpHandler handler;

    private final List<Request> captured = new CopyOnWriteArrayList<>();
    private final Semaphore capturedCount = new Semaphore(0);

    public Kubernetes() {
        this(new CrdHandler());
    }

    public Kubernetes(final CrdHandler handler) {
        this.rootHandler = handler;
        this.handler = handler;
        this.rootHandler.server = this;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 128);
            server.createContext("/").setHandler(exchange -> this.handler.handle(exchange));
            server.start();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Request> requests(final int awaitedCount) {
        if (awaitedCount > 0) {
            try {
                capturedCount.acquire(awaitedCount);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return captured;
    }

    public void handle(final HttpHandler handler, final Runnable task) {
        final var old = this.handler;
        this.handler = handler;

        Kubernetes oldServer = null;
        if (handler instanceof CrdHandler h) {
            oldServer = h.server;
            h.server = this;
        }
        try {
            task.run();
        } finally {
            this.handler = old;
            if (handler instanceof CrdHandler h) {
                h.server = oldServer;
            }
        }
    }

    @Override
    public void close() {
        if (handler instanceof CrdHandler h && h.watchExchange != null) {
            h.watchExchange.close();
        }
        server.stop(0);
    }

    public String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void sendEvent(final String event) {
        rootHandler.sendEvent(event);
    }

    public static class CrdHandler implements HttpHandler {
        private static final Logger LOGGER = Logger.getLogger(CrdHandler.class.getName());

        private final CountDownLatch watchReady = new CountDownLatch(1);
        private Kubernetes server;
        private volatile HttpExchange watchExchange;

        protected void capture(final HttpExchange exchange) throws IOException {
            try (final var in = exchange.getRequestBody()) {
                var json = new String(in.readAllBytes(), UTF_8);
                if (json.startsWith("{") && json.endsWith("}")) { // prettify
                    json = new SimplePrettyFormatter(new JsonMapperImpl(List.of(), new ConfigurationImpl(List.of())))
                            .apply(json);
                }
                server.captured.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(), json));
            }
            server.capturedCount.release();
        }

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            boolean skipClose = false;
            try {
                capture(exchange);

                skipClose = switch (exchange.getRequestMethod()) {
                    case "GET" -> doGet(exchange);
                    case "POST" -> doPost(exchange);
                    case "PATCH" -> doPatch(exchange);
                    case "DELETE" -> doDelete(exchange);
                    default -> false;
                };
                if (exchange.getResponseCode() > 0) {
                    return;
                }

                LOGGER.warning(
                        () -> "Unknown Request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
                exchange.sendResponseHeaders(404, 0);
                skipClose = false;
            } finally {
                if (!skipClose) {
                    exchange.close();
                }
            }
        }

        public void sendEvent(final String event) {
            try {
                watchReady.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            try {
                watchExchange.getResponseBody().write((event.replace('\n', ' ') + '\n').getBytes(UTF_8));
                watchExchange.getResponseBody().flush();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        protected boolean doGet(final HttpExchange exchange) throws IOException {
            switch (exchange.getRequestURI().getPath()) {
                case "/api/v1":
                    // just what we do use
                    send(
                            exchange,
                            200,
                            """
                            {
                              "kind": "APIResourceList",
                              "apiVersion": "v1",
                              "groupVersion": "v1",
                              "resources": [
                                {
                                  "name": "jobs",
                                  "singularName": "",
                                  "namespaced": true,
                                  "kind": "Pod",
                                  "verbs": [
                                    "create", "delete", "deletecollection", "get", "list", "patch", "update", "watch"
                                  ]
                                },
                                {
                                  "name": "services",
                                  "singularName": "",
                                  "namespaced": true,
                                  "kind": "Service",
                                  "verbs": [
                                    "create", "delete", "deletecollection", "get", "list", "patch", "update", "watch"
                                  ]
                                }
                              ]
                            }""");
                    return false;
                case "/apis/batch/v1":
                    send(
                            exchange,
                            200,
                            """
                            {
                              "kind": "APIResourceList",
                              "apiVersion": "v1",
                              "groupVersion": "batch/v1",
                              "resources": [
                                {
                                  "name": "jobs",
                                  "singularName": "",
                                  "namespaced": true,
                                  "kind": "Job",
                                  "verbs": [
                                    "create",
                                    "delete",
                                    "deletecollection",
                                    "get",
                                    "list",
                                    "patch",
                                    "update",
                                    "watch"
                                  ]
                                },
                                {
                                  "name": "jobs/status",
                                  "singularName": "",
                                  "namespaced": true,
                                  "kind": "Job",
                                  "verbs": [
                                    "get",
                                    "patch",
                                    "update"
                                  ]
                                }
                              ]
                            }""");
                    return false;
                case "/apis/batch/v1/namespaces/default/jobs/orchestrator":
                    send(exchange, 404, "{}");
                    return false;
                case "/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks":
                    final var query = exchange.getRequestURI().getQuery();
                    if (query == null || !query.contains("watch=true")) {
                        send(
                                exchange,
                                200,
                                """
                                        {
                                          "items": [],
                                          "metadata": {}
                                        }
                                        """);
                        return false;
                    }

                    exchange.sendResponseHeaders(200, 0);
                    watchExchange = exchange;
                    watchReady.countDown();
                    return true;
                default:
                    return false;
            }
        }

        protected boolean doPost(final HttpExchange exchange) throws IOException {
            switch (exchange.getRequestURI().getPath()) {
                case "/apis/batch/v1/namespaces/default/jobs":
                    send(exchange, 201, "{}");
                    return false;
                case "/api/v1/namespaces/default/services":
                    send(exchange, 201, "{}");
                    return false;
                default:
                    return false;
            }
        }

        protected boolean doPatch(final HttpExchange exchange) throws IOException {
            final var path = exchange.getRequestURI().getPath();
            if (path.startsWith("/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/")
                    && path.endsWith("/status")) {
                send(exchange, 200, "{}");
            }
            return false;
        }

        protected boolean doDelete(final HttpExchange exchange) throws IOException {
            send(exchange, 200, "{}");
            return false;
        }

        protected void send(final HttpExchange exchange, final int status, final String payload) throws IOException {
            final var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    public record Request(String method, URI uri, String payload) {
        public String asString() {
            return method + " " + uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "") + '\n' + payload;
        }

        public String requestLine() {
            return method + " " + uri.toASCIIString();
        }

        public void assertJsonPayloadEquals(final String raw) {
            try (final var mapper = new JsonMapperImpl(List.of(), k -> empty())) {
                if (!mapper.fromString(Object.class, raw).equals(mapper.fromString(Object.class, payload))) {
                    // better error message until we write one more accurate
                    assertEquals(raw, payload);
                }
            }
        }
    }
}
