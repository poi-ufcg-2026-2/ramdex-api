package br.ramdex.http;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resposta de um handler: status, corpo (serializado como JSON) e cabeçalhos extras. */
public record Response(int status, Object body, Map<String, String> headers) {

    public static Response ok(Object body) {
        return new Response(200, body, Map.of());
    }

    public static Response created(Object body, String location) {
        return new Response(201, body, Map.of("Location", location));
    }

    /** Atalho para montar corpos JSON com campos em ordem: {@code fields("a", 1, "b", 2)}. */
    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
