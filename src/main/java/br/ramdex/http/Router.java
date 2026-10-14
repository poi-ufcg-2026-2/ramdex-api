package br.ramdex.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roteador mínimo sobre o {@code HttpServer} do JDK. Rotas são registradas por
 * método e padrão de caminho, com parâmetros no formato {@code /documents/{id}}.
 * Também centraliza o tratamento de erros: tudo vira JSON {@code {"error": ...}}.
 */
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    private record Route(String method, String[] segments, Handler handler) {
    }

    private final List<Route> routes = new ArrayList<>();
    private final int maxBodyBytes;

    public Router(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public Router get(String path, Handler handler) {
        return add("GET", path, handler);
    }

    public Router post(String path, Handler handler) {
        return add("POST", path, handler);
    }

    public Router delete(String path, Handler handler) {
        return add("DELETE", path, handler);
    }

    private Router add(String method, String path, Handler handler) {
        routes.add(new Route(method, split(path), handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Response response;
        try {
            response = dispatch(exchange);
        } catch (HttpError e) {
            response = error(e.status(), e.getMessage());
        } catch (IllegalArgumentException e) {
            response = error(400, e.getMessage());
        } catch (OutOfMemoryError e) {
            // Sob pouca memória o índice pode esgotar o heap; respondemos 503 em vez de derrubar a conexão.
            System.err.println("[ramdex] OutOfMemoryError: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            response = error(503, "memória insuficiente (OutOfMemoryError)");
        } catch (Exception e) {
            System.err.println("[ramdex] erro em " + exchange.getRequestMethod() + " " + exchange.getRequestURI() + ": " + e);
            e.printStackTrace();
            response = error(500, "erro interno: " + e.getMessage());
        }
        send(exchange, response);
    }

    private Response dispatch(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String[] path = split(exchange.getRequestURI().getPath());
        Set<String> allowed = new LinkedHashSet<>();
        for (Route route : routes) {
            Map<String, String> params = match(route.segments(), path);
            if (params == null) {
                continue;
            }
            if (route.method().equals(method)) {
                return route.handler().handle(new Request(exchange, params, maxBodyBytes));
            }
            allowed.add(route.method());
        }
        if (allowed.isEmpty()) {
            throw HttpError.notFound("rota não encontrada: " + exchange.getRequestURI().getPath());
        }
        exchange.getResponseHeaders().set("Allow", String.join(", ", allowed));
        throw new HttpError(405, "método " + method + " não permitido; use " + String.join(", ", allowed));
    }

    private static Map<String, String> match(String[] pattern, String[] path) {
        if (pattern.length != path.length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                params.put(p.substring(1, p.length() - 1), path[i]);
            } else if (!p.equals(path[i])) {
                return null;
            }
        }
        return params;
    }

    private static String[] split(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/+");
    }

    private static Response error(int status, String message) {
        return new Response(status, Response.fields("error", message), Map.of());
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = Json.stringify(response.body()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        response.headers().forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
