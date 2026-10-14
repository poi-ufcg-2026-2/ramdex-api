package br.ramdex.index;

import br.ramdex.generator.SyntheticGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertedIndexTest {

    private static List<Long> ids(SearchResult result) {
        return result.hits().stream().map(SearchResult.Hit::id).toList();
    }

    @Test
    void indexaEBuscaComModosAndEOr() {
        InvertedIndex index = new InvertedIndex();
        Document a = index.add("A", "O índice invertido fica em memória");
        Document b = index.add("B", "Memória RAM e disco");
        index.add("C", "Nada relacionado aqui");

        assertEquals(List.of(a.id()), ids(index.search("indice memoria", InvertedIndex.Mode.AND, 10)));
        assertEquals(Set.of(a.id(), b.id()),
                new HashSet<>(ids(index.search("indice memoria", InvertedIndex.Mode.OR, 10))));
        assertEquals(0, index.search("inexistente", InvertedIndex.Mode.OR, 10).total());
    }

    @Test
    void rankeiaPorRelevancia() {
        InvertedIndex index = new InvertedIndex();
        index.add("pouco", "java aparece uma vez entre muitas outras palavras diferentes");
        Document muito = index.add("muito", "java java java");
        index.add("outro", "python");

        assertEquals(muito.id(), index.search("java", InvertedIndex.Mode.OR, 10).hits().get(0).id());
    }

    @Test
    void respeitaLimiteMasInformaTotal() {
        InvertedIndex index = new InvertedIndex();
        for (int i = 0; i < 30; i++) {
            index.add("doc " + i, "termo comum " + i);
        }
        SearchResult result = index.search("comum", InvertedIndex.Mode.AND, 5);
        assertEquals(30, result.total());
        assertEquals(5, result.hits().size());
    }

    @Test
    void removeDocumentoDoIndice() {
        InvertedIndex index = new InvertedIndex();
        Document doc = index.add("x", "palavra unica");
        index.add("y", "palavra repetida");

        assertTrue(index.remove(doc.id()));
        assertFalse(index.remove(doc.id()));
        assertNull(index.get(doc.id()));
        assertEquals(0, index.search("unica", InvertedIndex.Mode.OR, 10).total());
        assertEquals(1, index.search("palavra", InvertedIndex.Mode.OR, 10).total());

        InvertedIndex.Stats stats = index.stats();
        assertEquals(1, stats.documents());
        assertEquals(2, stats.terms());
        assertEquals(2, stats.postings());
    }

    @Test
    void trechoContemOTermoBuscado() {
        String content = "Começo do texto. ".repeat(20) + "Aqui está o Índice procurado. " + "Fim. ".repeat(20);
        String snippet = InvertedIndex.snippet(content, List.of("indice"));
        assertTrue(snippet.contains("Índice"), snippet);
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet);
    }

    @Test
    void geradorEhReprodutivelEProduzTermosBuscaveis() {
        InvertedIndex a = new InvertedIndex();
        InvertedIndex b = new InvertedIndex();
        SyntheticGenerator.Result result = SyntheticGenerator.generate(a, 200, 50, 1_000, 7);
        SyntheticGenerator.generate(b, 200, 50, 1_000, 7);

        assertEquals(a.get(1).content(), b.get(1).content());
        assertEquals(200, a.stats().documents());
        assertEquals(200L * 50, a.stats().tokens());
        String frequent = result.sampleTerms().frequent().get(0);
        assertTrue(a.search(frequent, InvertedIndex.Mode.OR, 10).total() > 100);
    }

    @Test
    void palavrasDoVocabularioSaoUnicasENaoSaoStopwords() {
        Set<String> words = new HashSet<>();
        for (int k = 0; k < 20_000; k++) {
            String word = SyntheticGenerator.word(k);
            assertFalse(Tokenizer.isStopword(word), word);
            assertTrue(words.add(word), "repetida: " + word);
        }
    }
}
