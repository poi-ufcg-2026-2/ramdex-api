package br.ramdex.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest {

    @Test
    void normalizaCaixaAcentosEPontuacao() {
        assertEquals(List.of("acao", "rapida", "coracao"), Tokenizer.tokenize("Ação RÁPIDA, coração!"));
    }

    @Test
    void removeStopwordsETermosCurtos() {
        assertEquals(List.of("gato", "telhado"), Tokenizer.tokenize("O gato está no telhado e a"));
    }

    @Test
    void ignoraSintaxeMarkdown() {
        assertEquals(List.of("titulo", "texto", "negrito", "link"),
                Tokenizer.tokenize("# Título\n\nTexto **negrito** e [link](#)"));
    }

    @Test
    void mantemNumeros() {
        assertEquals(List.of("java", "21", "lts"), Tokenizer.tokenize("Java 21 LTS"));
    }

    @Test
    void termosUnicosPreservamOrdem() {
        assertEquals(List.of("busca", "memoria"), Tokenizer.uniqueTerms("busca memória busca"));
    }

    @Test
    void informaOffsetsNoTextoOriginal() {
        String text = "Olá, mundo";
        int[] offsets = new int[2];
        Tokenizer.forEachToken(text, (term, position, start, end) -> {
            if (term.equals("mundo")) {
                offsets[0] = start;
                offsets[1] = end;
            }
            return true;
        });
        assertEquals("mundo", text.substring(offsets[0], offsets[1]));
    }
}
