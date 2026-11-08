package org.supermas.observation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.supermas.blackboard.persistence.codec.JsonCodec;

/** Loopback-only metadata server for a trusted developer, independent of the command runtime. */
public final class ObservationServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers;
    private final ObservationSource source;
    private final byte[] authorization;
    private final boolean diagnostics;

    public ObservationServer(ObservationSource source, String token, int port, boolean diagnostics) throws IOException {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("Use a token of at least 32 characters");
        this.source = source;
        this.diagnostics = diagnostics;
        authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Cache-Control", "no-store, max-age=0");
            if (!MessageDigest.isEqual(authorization, exchange.getRequestHeaders().getFirst("Authorization") == null
                    ? new byte[0] : exchange.getRequestHeaders().getFirst("Authorization").getBytes(StandardCharsets.UTF_8))) {
                send(exchange, 401, Map.of("error", "UNAUTHORIZED")); return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, Map.of("error", "READ_ONLY")); return;
            }
            Map<String, String> query = query(exchange.getRequestURI());
            switch (exchange.getRequestURI().getPath()) {
                case "/boards" -> {
                    if (!query.isEmpty()) throw new IllegalArgumentException();
                    send(exchange, 200, Map.of("boards", source.boards().stream().map(key -> Map.of("key", key)).toList()));
                }
                case "/view" -> {
                    if (!Set.of("board", "agent", "from", "to").containsAll(query.keySet())) throw new IllegalArgumentException();
                    String board = query.getOrDefault("board", "");
                    if (!board.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
                    String agent = query.getOrDefault("agent", "*");
                    if (agent.length() > 200) throw new IllegalArgumentException();
                    Instant from = Instant.ofEpochMilli(Long.parseLong(query.getOrDefault("from", "0")));
                    Instant to = Instant.ofEpochMilli(Long.parseLong(query.getOrDefault("to", "253402300799000")));
                    if (to.isBefore(from)) throw new IllegalArgumentException();
                    send(exchange, 200, new ObservationView(source.capture(board), diagnostics).render(agent, from, to));
                }
                default -> send(exchange, 404, Map.of("error", "NOT_FOUND"));
            }
        } catch (NoSuchElementException e) { send(exchange, 404, Map.of("error", "BOARD_UNAVAILABLE"));
        } catch (IllegalArgumentException e) { send(exchange, 400, Map.of("error", "INVALID_QUERY"));
        } catch (RuntimeException e) {
            // Never return SQL, local paths, payloads or exception text to a dashboard or log.
            send(exchange, 503, Map.of("error", "OBSERVATION_UNAVAILABLE", "fresh", false));
        } finally { exchange.close(); }
    }

    private static Map<String, String> query(URI uri) {
        String raw = uri.getRawQuery();
        if (raw == null) return Map.of();
        if (raw.length() > 2048) throw new IllegalArgumentException();
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException();
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (result.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException();
        }
        return result;
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JsonCodec.write(body).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2 * 1024 * 1024) {
            status = 503;
            bytes = "{\"error\":\"RESPONSE_LIMIT_EXCEEDED\",\"fresh\":false}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ObservationServer <existing-database>");
        String token = System.getenv("SUPERMAS_OBSERVATION_TOKEN");
        String boards = Objects.requireNonNull(System.getenv("SUPERMAS_OBSERVATION_BOARDS"), "Set authorized board keys");
        ObservationSource source = new SqliteObservationSource(Path.of(args[0]), Set.of(boards.split(",")), Clock.systemUTC());
        boolean diagnostics = "true".equals(System.getenv("SUPERMAS_OBSERVATION_DIAGNOSTICS"));
        ObservationServer server = new ObservationServer(source, token, 8091, diagnostics);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Read-only observation listening on 127.0.0.1:8091");
        new CountDownLatch(1).await();
    }
}
