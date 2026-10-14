package br.ramdex.generator;

import br.ramdex.index.InvertedIndex;
import br.ramdex.index.Tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Gera documentos sintéticos para encher o índice de forma controlada.
 *
 * <p>O vocabulário é formado por pseudo-palavras (combinações de sílabas). A
 * palavra de posição {@code k} é sorteada com probabilidade proporcional a
 * {@code 1/k} (lei de Zipf), como em textos reais: poucas palavras muito
 * frequentes e muitas raras. Com a mesma seed, o conteúdo gerado é sempre o mesmo.
 */
public final class SyntheticGenerator {

    private static final String[] SYLLABLES = {
            "ba", "be", "bi", "bo", "bu", "ca", "ce", "ci", "co", "cu", "da", "de", "di", "do", "du",
            "fa", "fe", "fi", "fo", "fu", "ga", "ge", "gi", "go", "gu", "la", "le", "li", "lo", "lu",
            "ma", "me", "mi", "mo", "mu", "na", "ne", "ni", "no", "nu", "pa", "pe", "pi", "po", "pu",
            "ra", "re", "ri", "ro", "ru", "sa", "se", "si", "so", "su", "ta", "te", "ti", "to", "tu",
            "va", "ve", "vi", "vo", "vu", "za", "ze", "zi", "zo", "zu"};

    private static final int WORDS_PER_SENTENCE = 12;

    public record Result(int documents, long words, long elapsedMs, SampleTerms sampleTerms) {
    }

    /** Termos de exemplo para montar consultas: frequentes, de frequência média e raros. */
    public record SampleTerms(List<String> frequent, List<String> medium, List<String> rare) {
    }

    private SyntheticGenerator() {
    }

    /**
     * Palavra de índice {@code k} do vocabulário (0 = mais frequente). É
     * determinística, então pode ser recalculada para montar consultas.
     */
    public static String word(int k) {
        // k escrito em base N (N = número de sílabas), com pelo menos duas sílabas.
        StringBuilder sb = new StringBuilder();
        int value = k;
        do {
            sb.append(SYLLABLES[value % SYLLABLES.length]);
            value /= SYLLABLES.length;
        } while (value > 0);
        if (sb.length() < 4) {
            sb.append(SYLLABLES[0]);
        }
        // Algumas combinações viram stopwords ("para", "como") e seriam ignoradas pelo
        // tokenizer; o sufixo "x" as torna únicas, já que as demais terminam em vogal.
        if (Tokenizer.isStopword(sb.toString())) {
            sb.append('x');
        }
        return sb.toString();
    }

    public static Result generate(InvertedIndex index, int docs, int wordsPerDoc, int vocabSize, long seed) {
        long startedAt = System.nanoTime();
        String[] vocabulary = new String[vocabSize];
        for (int k = 0; k < vocabSize; k++) {
            vocabulary[k] = word(k);
        }
        double[] cumulative = zipfCumulative(vocabSize);
        Random random = new Random(seed);
        int baseNumber = index.stats().documents() + 1;

        StringBuilder content = new StringBuilder(wordsPerDoc * 8);
        long words = 0;
        for (int d = 0; d < docs; d++) {
            content.setLength(0);
            for (int w = 0; w < wordsPerDoc; w++) {
                String word = vocabulary[sample(cumulative, random)];
                if (w % WORDS_PER_SENTENCE == 0) {
                    if (w > 0) {
                        content.append(". ");
                    }
                    content.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
                } else {
                    content.append(' ').append(word);
                }
            }
            content.append('.');
            index.add("Documento sintético " + (baseNumber + d), content.toString());
            words += wordsPerDoc;
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new Result(docs, words, elapsedMs, sampleTerms(vocabSize));
    }

    private static double[] zipfCumulative(int size) {
        double[] cumulative = new double[size];
        double sum = 0;
        for (int k = 0; k < size; k++) {
            sum += 1.0 / (k + 1);
            cumulative[k] = sum;
        }
        for (int k = 0; k < size; k++) {
            cumulative[k] /= sum;
        }
        return cumulative;
    }

    private static int sample(double[] cumulative, Random random) {
        int i = Arrays.binarySearch(cumulative, random.nextDouble());
        return Math.min(i >= 0 ? i : -i - 1, cumulative.length - 1);
    }

    private static SampleTerms sampleTerms(int vocabSize) {
        return new SampleTerms(range(0, 5, vocabSize), range(vocabSize / 100, 5, vocabSize), range(vocabSize / 2, 5, vocabSize));
    }

    private static List<String> range(int from, int count, int vocabSize) {
        List<String> terms = new ArrayList<>();
        for (int k = from; k < Math.min(from + count, vocabSize); k++) {
            terms.add(word(k));
        }
        return terms;
    }
}
