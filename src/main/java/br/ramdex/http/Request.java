package br.ramdex.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Request {

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private final int maxBodyBytes;

    Request(HttpExchange exchange, Map<String, String> pathParams, int maxBodyBytes) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
        this.maxBodyBytes = maxBodyBytes;
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public String query(String name) {
        return query.get(name);
    }

    public String query(String name, String defaultValue) {
        return query.getOrDefault(name, defaultValue);
    }

    public int queryInt(String name, int defaultValue, int min, int max) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return checkRange(name, parseLong(name, value), min, max);
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String mediaType() {
        String contentType = header("Content-Type");
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    public byte[] body() throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(maxBodyBytes + 1);
            if (bytes.length > maxBodyBytes) {
                throw new HttpError(413, "corpo da requisição maior que " + (maxBodyBytes / (1024 * 1024)) + " MB");
            }
            return bytes;
        }
    }

    public String bodyAsString() throws IOException {
        return new String(body(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> bodyAsJsonObject() throws IOException {
        String text = bodyAsString();
        if (text.isBlank()) {
            return new HashMap<>();
        }
        Object value;
        try {
            value = Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw HttpError.badRequest(e.getMessage());
        }
        if (!(value instanceof Map)) {
            throw HttpError.badRequest("o corpo deve ser um objeto JSON");
        }
        return (Map<String, Object>) value;
    }

    public static int checkRange(String name, long value, int min, int max) {
        if (value < min || value > max) {
            throw HttpError.badRequest("'" + name + "' deve estar entre " + min + " e " + max);
        }
        return (int) value;
    }

    public static long parseLong(String name, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw HttpError.badRequest("'" + name + "' deve ser um número inteiro");
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }
}
