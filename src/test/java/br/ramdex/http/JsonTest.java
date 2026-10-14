package br.ramdex.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    record Exemplo(String nome, int valor) {
    }

    @Test
    void fazParseDeObjetoCompleto() {
        Object value = Json.parse("{\"title\":\"Olá\\nmundo\",\"n\":42,\"x\":1.5,\"ok\":true,\"nada\":null,\"lista\":[1,\"a\"]}");
        Map<?, ?> map = (Map<?, ?>) value;
        assertEquals("Olá\nmundo", map.get("title"));
        assertEquals(42L, map.get("n"));
        assertEquals(1.5, map.get("x"));
        assertEquals(true, map.get("ok"));
        assertEquals(null, map.get("nada"));
        assertEquals(List.of(1L, "a"), map.get("lista"));
    }

    @Test
    void decodificaEscapesUnicode() {
        assertEquals("ação", Json.parse("\"a\\u00e7\\u00e3o\""));
    }

    @Test
    void rejeitaJsonInvalido() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} lixo"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
    }

    @Test
    void serializaMapasListasERecords() {
        assertEquals("{\"a\":[1,\"x\\\"y\"],\"r\":{\"nome\":\"n\",\"valor\":3}}",
                Json.stringify(Response.fields("a", List.of(1, "x\"y"), "r", new Exemplo("n", 3))));
    }

    @Test
    void idaEVoltaPreservaConteudo() {
        String original = "Linha 1\n\tLinha 2 \"citação\" \\ fim";
        assertEquals(original, Json.parse(Json.stringify(original)));
    }
}
