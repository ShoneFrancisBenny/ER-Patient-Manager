
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class ErApplication {
    static class Patient {
        String id;
        String name;
        int age;
        String condition;
        int severity;
        long arrival;

        Patient(String id, String name, int age, String condition,
                int severity, long arrival) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.condition = condition;
            this.severity = severity;
            this.arrival = arrival;
        }

        String json() {
            return "{\"id\":\"" + escape(id) + "\","
                + "\"name\":\"" + escape(name) + "\","
                + "\"age\":" + age + ","
                + "\"condition\":\"" + escape(condition) + "\","
                + "\"severity\":" + severity + "}";
        }
    }

    static final PriorityQueue<Patient> waiting = new PriorityQueue<>(
        Comparator.comparingInt((Patient p) -> p.severity).reversed()
            .thenComparingLong(p -> p.arrival)
    );
    static final Deque<Patient> history = new ArrayDeque<>();
    static Patient lastTreatment;
    static long sequence = 0;
    static final Object lock = new Object();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("localhost", 8080), 0);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().startsWith("/api/")) {
                try {
                    byte[] page = Files.readAllBytes(Path.of("index.html"));
                    exchange.getResponseHeaders().set(
                        "Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, page.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(page);
                    }
                } catch (IOException e) {
                    send(exchange, 500,
                        "Could not read index.html. Keep it beside ErApplication.java."
                            .getBytes(StandardCharsets.UTF_8));
                }
            } else {
                send(exchange, 404, "{\"error\":\"Not found\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }
        });

        server.createContext("/api/state", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                send(exchange, 405, "{\"error\":\"Use GET\"}"
                    .getBytes(StandardCharsets.UTF_8));
                return;
            }
            synchronized (lock) {
                StringBuilder w = new StringBuilder("[");
                List<Patient> sorted = new ArrayList<>(waiting);
                sorted.sort(waiting.comparator());
                for (Patient p : sorted) {
                    if (w.length() > 1) w.append(",");
                    w.append(p.json());
                }
                w.append("]");

                StringBuilder h = new StringBuilder("[");
                for (Patient p : history) {
                    if (h.length() > 1) h.append(",");
                    h.append(p.json());
                }
                h.append("]");

                String last = lastTreatment == null ? "null"
                    : lastTreatment.json();
                String json = "{\"waitingPatients\":" + w
                    + ",\"treatmentHistory\":" + h
                    + ",\"lastTreatment\":" + last + "}";
                send(exchange, 200, json.getBytes(StandardCharsets.UTF_8));
            }
        });

        server.createContext("/api/patient", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                send(exchange, 405, "{\"error\":\"Use POST\"}"
                    .getBytes(StandardCharsets.UTF_8));
                return;
            }
            try {
                Map<String, String> data = parseBody(exchange);
                String name = data.getOrDefault("name", "").trim();
                String condition = data.getOrDefault("condition", "").trim();
                int age = Integer.parseInt(data.getOrDefault("age", "0"));
                int severity = Integer.parseInt(
                    data.getOrDefault("severity", "0"));

                if (name.isEmpty() || condition.isEmpty()
                    || age < 0 || age > 130
                    || severity < 1 || severity > 5) {
                    send(exchange, 400,
                        "{\"error\":\"Enter valid patient details. Severity must be 1 to 5.\"}"
                            .getBytes(StandardCharsets.UTF_8));
                    return;
                }

                synchronized (lock) {
                    Patient p = new Patient(
                        UUID.randomUUID().toString(), name, age, condition,
                        severity, sequence++);
                    waiting.add(p);
                }
                send(exchange, 201, "{\"message\":\"Patient registered\"}"
                    .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                send(exchange, 400, "{\"error\":\"Invalid patient data\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }
        });

        server.createContext("/api/treat", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                send(exchange, 405, "{\"error\":\"Use POST\"}"
                    .getBytes(StandardCharsets.UTF_8));
                return;
            }
            synchronized (lock) {
                Patient p = waiting.poll();
                if (p == null) {
                    send(exchange, 400, "{\"error\":\"No patients waiting\"}"
                        .getBytes(StandardCharsets.UTF_8));
                    return;
                }
                history.push(p);
                lastTreatment = p;
                send(exchange, 200, "{\"message\":\"Treatment recorded\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }
        });

        server.createContext("/api/undo", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                send(exchange, 405, "{\"error\":\"Use POST\"}"
                    .getBytes(StandardCharsets.UTF_8));
                return;
            }
            synchronized (lock) {
                Patient p = history.pollFirst();
                if (p == null) {
                    send(exchange, 400, "{\"error\":\"No treatment to undo\"}"
                        .getBytes(StandardCharsets.UTF_8));
                    return;
                }
                waiting.add(p);
                lastTreatment = history.peekFirst();
                send(exchange, 200, "{\"message\":\"Last treatment undone\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("ER Patient Manager is running!");
        System.out.println("Open http://localhost:8080/");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    static Map<String, String> parseBody(HttpExchange exchange)
            throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|(-?\\d+))"
        ).matcher(body);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            if (m.group(2) != null) {
                value = value.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n");
            }
            result.put(m.group(1), value);
        }
        return result;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    static void send(HttpExchange exchange, int status, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
