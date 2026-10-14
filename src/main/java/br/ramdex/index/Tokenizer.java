package br.ramdex.index;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quebra um texto em termos normalizados: minúsculas, sem acentos e sem
 * pontuação. Qualquer caractere que não seja letra ou dígito é separador, o
 * que também descarta a sintaxe de Markdown (#, *, _, [], etc.). Stopwords
 * comuns em português e inglês e termos de um único caractere são ignorados.
 */
public final class Tokenizer {

    /** Recebe cada termo encontrado. Retornar {@code false} interrompe a varredura. */
    @FunctionalInterface
    public interface TokenConsumer {
        boolean accept(String term, int position, int start, int end);
    }

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERM_LENGTH = 64;

    // Set.copyOf aceita repetições (ex.: "as" existe nas duas línguas).
    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            // português
            "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas", "um", "uma", "uns", "umas",
            "por", "para", "pra", "com", "sem", "que", "se", "ao", "aos", "as", "os", "ou", "mas",
            "como", "mais", "foi", "ser", "esta", "este", "isso", "isto", "sua", "seu", "suas", "seus",
            "ja", "nao", "sim", "ate", "pelo", "pela", "pelos", "pelas", "entre", "quando", "muito",
            "tambem", "so", "sao", "ha", "me", "te", "lhe", "ele", "ela", "eles", "elas", "nem",
            // inglês
            "the", "of", "and", "to", "in", "is", "it", "that", "for", "on", "with", "as", "at", "by",
            "an", "be", "this", "are", "or", "from", "was", "were", "not", "but", "have", "has", "had"));

    private static final char[] FOLD = new char[0x180];

    static {
        for (char c = 0; c < FOLD.length; c++) {
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            FOLD[c] = Character.toLowerCase(decomposed.charAt(0));
        }
    }

    private Tokenizer() {
    }

    public static boolean isStopword(String term) {
        return STOPWORDS.contains(term);
    }

    private static char fold(char c) {
        return c < FOLD.length ? FOLD[c] : Character.toLowerCase(c);
    }

    /** Percorre o texto entregando cada termo válido, sua posição (ordinal) e seus offsets no texto original. */
    public static void forEachToken(String text, TokenConsumer consumer) {
        StringBuilder current = new StringBuilder(16);
        int position = 0;
        int start = -1;
        int length = text.length();
        for (int i = 0; i <= length; i++) {
            char c = i < length ? text.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = i;
                }
                if (current.length() < MAX_TERM_LENGTH) {
                    current.append(fold(c));
                }
            } else if (start >= 0) {
                if (current.length() >= MIN_TERM_LENGTH) {
                    String term = current.toString();
                    if (!STOPWORDS.contains(term)) {
                        if (!consumer.accept(term, position, start, i)) {
                            return;
                        }
                        position++;
                    }
                }
                current.setLength(0);
                start = -1;
            }
        }
    }

    public static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        forEachToken(text, (term, position, start, end) -> terms.add(term));
        return terms;
    }

    public static List<String> uniqueTerms(String text) {
        return new ArrayList<>(new LinkedHashSet<>(tokenize(text)));
    }
}
