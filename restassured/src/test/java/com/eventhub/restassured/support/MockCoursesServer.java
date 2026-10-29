package com.eventhub.restassured.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Local stand-in for the courses API: serves whatever body a test sets on {@code path}, as 200 application/json.
 * Binds a free port on 127.0.0.1, so runs never collide and nothing leaves the machine.
 */
public final class MockCoursesServer implements AutoCloseable {
    private final HttpServer server;
    private volatile String body;

    public MockCoursesServer(String path, String initialBody) throws IOException {
        this.body = initialBody;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, this::respond);
        server.start();
    }

    private void respond(HttpExchange exchange) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** The body every following GET /courses returns. */
    public void setBody(String body) {
        this.body = body;
    }

    public String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
