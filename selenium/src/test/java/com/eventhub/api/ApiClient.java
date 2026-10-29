package com.eventhub.api;

import com.eventhub.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Direct calls to the EventHub API for test setup, verification and cleanup. */
public class ApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String CUSTOMER_NAME  = "Selenium QA";
    public static final String CUSTOMER_EMAIL = "selenium.qa@example.com";
    public static final String CUSTOMER_PHONE = "9876543210";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record Event(long id, String title, String category, String venue, String city, double price, int availableSeats) {}
    public record Booking(long id, String bookingRef, int quantity, double totalPrice) {}

    /** Registers the account unless it can already log in (a duplicate register is a 400, not a 409). */
    public void ensureAccount(String email, String password) {
        Map<String, String> credentials = Map.of("email", email, "password", password);
        if (send("POST", "/auth/login", null, credentials, -1).status() == 200) return;
        send("POST", "/auth/register", null, credentials, 201);
    }

    public String login(String email, String password) {
        JsonNode body = send("POST", "/auth/login", null, Map.of("email", email, "password", password), 200).body();
        return body.get("token").asText();
    }

    /** A static (seeded, shared) event with enough personal seats left for this user. */
    public Event findStaticEventWithSeats(String token, int seats) {
        JsonNode events = send("GET", "/events?limit=50", token, null, 200).body().get("data");
        for (JsonNode e : events) {
            if (e.get("isStatic").asBoolean() && e.get("availableSeats").asInt() >= seats) {
                return new Event(e.get("id").asLong(), e.get("title").asText(), e.get("category").asText(),
                        e.get("venue").asText(), e.get("city").asText(), e.get("price").asDouble(),
                        e.get("availableSeats").asInt());
            }
        }
        throw new IllegalStateException("No static event with " + seats + " free seats");
    }

    public Booking createBooking(String token, long eventId, int quantity) {
        Map<String, Object> payload = Map.of(
                "eventId", eventId, "quantity", quantity,
                "customerName", CUSTOMER_NAME, "customerEmail", CUSTOMER_EMAIL, "customerPhone", CUSTOMER_PHONE);
        JsonNode b = send("POST", "/bookings", token, payload, 201).body().get("data");
        return new Booking(b.get("id").asLong(), b.get("bookingRef").asText(),
                b.get("quantity").asInt(), b.get("totalPrice").asDouble());
    }

    /** Looks up a booking by its ref, e.g. one created through the UI, so the test can record its id. */
    public Booking findBookingByRef(String token, String bookingRef) {
        JsonNode b = send("GET", "/bookings/ref/" + bookingRef, token, null, 200).body().get("data");
        return new Booking(b.get("id").asLong(), b.get("bookingRef").asText(),
                b.get("quantity").asInt(), b.get("totalPrice").asDouble());
    }

    /** Total number of this user's bookings (pagination.total of GET /bookings). */
    public int totalBookings(String token) {
        return send("GET", "/bookings?limit=1", token, null, 200).body().get("pagination").get("total").asInt();
    }

    /** This user's available seats for an event (seats are computed per user). */
    public int availableSeats(String token, long eventId) {
        return send("GET", "/events/" + eventId, token, null, 200).body().get("data").get("availableSeats").asInt();
    }

    /** HTTP status of GET /bookings/:id (200 = exists, 404 = gone, 403 = someone else's). */
    public int bookingStatus(String token, long bookingId) {
        return send("GET", "/bookings/" + bookingId, token, null, -1).status();
    }

    /** Best-effort cleanup: a 404 just means the test already removed it. Other failures are logged. */
    public void deleteBookings(String token, List<Long> ids) {
        for (Long id : ids) {
            try {
                int status = send("DELETE", "/bookings/" + id, token, null, -1).status();
                if (status != 200 && status != 404) {
                    System.err.println("Cleanup of booking #" + id + " failed: HTTP " + status);
                }
            } catch (RuntimeException e) {
                System.err.println("Cleanup of booking #" + id + " failed: " + e.getMessage());
            }
        }
    }

    private record Result(int status, JsonNode body) {}

    private Result send(String method, String path, String token, Object body, int expectedStatus) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(Config.API_URL + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json");
            if (token != null) req.header("Authorization", "Bearer " + token);
            req.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));

            HttpResponse<String> res;
            try {
                res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                // A pooled keep-alive connection the server already closed answers with zero bytes.
                // The request never reached the server, so resending it once is safe (even a POST).
                if (!isStaleConnection(e)) throw e;
                res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            }
            if (expectedStatus > 0 && res.statusCode() != expectedStatus) {
                throw new AssertionError(method + " " + path + " -> " + res.statusCode() + ": " + res.body());
            }
            return new Result(res.statusCode(), JSON.readTree(res.body()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean isStaleConnection(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains("received no bytes");
    }
}
