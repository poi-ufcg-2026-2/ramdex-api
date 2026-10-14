package br.ramdex.index;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Índice invertido mantido inteiramente em memória.
 *
 * <p>Para cada termo guarda a lista de {@link Posting}s (documento + posições),
 * além do conteúdo completo de cada documento. Isso torna o consumo de RAM
 * proporcional ao volume indexado, que é o que caracteriza a aplicação como
 * memory-bound. Buscas usam o lock de leitura (podem rodar em paralelo);
 * inserções e remoções usam o lock de escrita.
 */
public final class InvertedIndex {

    public enum Mode { AND, OR }

    private static final int SNIPPET_CONTEXT = 60;

    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final Map<Long, Document> documents = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private long nextId = 1;
    private long postingCount;
    private long tokenCount;

    /** Indexa um documento e devolve o registro criado. */
    public Document add(String title, String content) {
        String text = Normalizer.normalize(content, Normalizer.Form.NFC);
        Map<String, IntList> occurrences = new LinkedHashMap<>();
        int[] tokens = {0};
        Tokenizer.forEachToken(text, (term, position, start, end) -> {
            occurrences.computeIfAbsent(term, t -> new IntList()).add(position);
            tokens[0]++;
            return true;
        });

        lock.writeLock().lock();
        try {
            long id = nextId++;
            Document doc = new Document(id, title, text, System.currentTimeMillis(), tokens[0], occurrences.size());
            documents.put(id, doc);
            for (Map.Entry<String, IntList> e : occurrences.entrySet()) {
                postings.computeIfAbsent(e.getKey(), t -> new ArrayList<>()).add(new Posting(id, e.getValue().toArray()));
            }
            postingCount += occurrences.size();
            tokenCount += tokens[0];
            return doc;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Document get(long id) {
        lock.readLock().lock();
        try {
            return documents.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Document> list(int offset, int limit) {
        lock.readLock().lock();
        try {
            List<Document> page = new ArrayList<>(limit);
            Iterator<Document> it = documents.values().iterator();
            for (int skipped = 0; skipped < offset && it.hasNext(); skipped++) {
                it.next();
            }
            while (page.size() < limit && it.hasNext()) {
                page.add(it.next());
            }
            return page;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean remove(long id) {
        lock.writeLock().lock();
        try {
            Document doc = documents.remove(id);
            if (doc == null) {
                return false;
            }
            for (String term : Tokenizer.uniqueTerms(doc.content())) {
                List<Posting> list = postings.get(term);
                if (list == null) {
                    continue;
                }
                list.removeIf(p -> p.docId() == id);
                postingCount--;
                if (list.isEmpty()) {
                    postings.remove(term);
                }
            }
            tokenCount -= doc.tokenCount();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            postings.clear();
            documents.clear();
            nextId = 1;
            postingCount = 0;
            tokenCount = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Busca por relevância (TF-IDF com tf logarítmico):
     * {@code score(d) = Σ (1 + ln tf(t,d)) · ln(1 + N / df(t))}.
     * No modo AND o documento precisa conter todos os termos; no modo OR, ao menos um.
     */
    public SearchResult search(String query, Mode mode, int limit) {
        List<String> terms = Tokenizer.uniqueTerms(query);
        lock.readLock().lock();
        try {
            if (terms.isEmpty() || documents.isEmpty()) {
                return new SearchResult(terms, 0, List.of());
            }
            int n = documents.size();
            Map<Long, double[]> scores = mode == Mode.AND ? scoreAll(terms, n) : scoreAny(terms, n);
            List<SearchResult.Hit> hits = new ArrayList<>();
            for (Map.Entry<Long, double[]> e : topK(scores, limit)) {
                Document doc = documents.get(e.getKey());
                double score = Math.round(e.getValue()[0] * 10_000) / 10_000.0;
                hits.add(new SearchResult.Hit(doc.id(), doc.title(), score, snippet(doc.content(), terms)));
            }
            return new SearchResult(terms, scores.size(), hits);
        } finally {
            lock.readLock().unlock();
        }
    }

    private static double weight(Posting p, int n, int df) {
        return (1 + Math.log(p.termFrequency())) * Math.log(1 + (double) n / df);
    }

    private Map<Long, double[]> scoreAny(List<String> terms, int n) {
        Map<Long, double[]> scores = new HashMap<>();
        for (String term : terms) {
            List<Posting> list = postings.get(term);
            if (list == null) {
                continue;
            }
            for (Posting p : list) {
                scores.computeIfAbsent(p.docId(), id -> new double[1])[0] += weight(p, n, list.size());
            }
        }
        return scores;
    }

    private Map<Long, double[]> scoreAll(List<String> terms, int n) {
        List<List<Posting>> lists = new ArrayList<>();
        for (String term : terms) {
            List<Posting> list = postings.get(term);
            if (list == null) {
                return Map.of();
            }
            lists.add(list);
        }
        // Começa pela lista mais curta para o conjunto de candidatos ser o menor possível.
        lists.sort(Comparator.comparingInt(List::size));
        Map<Long, double[]> scores = new HashMap<>();
        for (Posting p : lists.get(0)) {
            scores.put(p.docId(), new double[] {weight(p, n, lists.get(0).size())});
        }
        for (List<Posting> list : lists.subList(1, lists.size())) {
            Map<Long, double[]> next = new HashMap<>();
            for (Posting p : list) {
                double[] score = scores.get(p.docId());
                if (score != null) {
                    score[0] += weight(p, n, list.size());
                    next.put(p.docId(), score);
                }
            }
            scores = next;
            if (scores.isEmpty()) {
                break;
            }
        }
        return scores;
    }

    /** Os {@code k} documentos de maior score, do maior para o menor (empate: menor id primeiro). */
    private static List<Map.Entry<Long, double[]>> topK(Map<Long, double[]> scores, int k) {
        Comparator<Map.Entry<Long, double[]>> best = Comparator
                .comparingDouble((Map.Entry<Long, double[]> e) -> e.getValue()[0])
                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder());
        PriorityQueue<Map.Entry<Long, double[]>> heap = new PriorityQueue<>(k + 1, best);
        for (Map.Entry<Long, double[]> e : scores.entrySet()) {
            heap.offer(e);
            if (heap.size() > k) {
                heap.poll();
            }
        }
        List<Map.Entry<Long, double[]>> result = new ArrayList<>(heap);
        result.sort(best.reversed());
        return result;
    }

    /** Trecho do documento ao redor da primeira ocorrência de um dos termos da consulta. */
    static String snippet(String content, List<String> terms) {
        int[] match = {-1, -1};
        Tokenizer.forEachToken(content, (term, position, start, end) -> {
            if (terms.contains(term)) {
                match[0] = start;
                match[1] = end;
                return false;
            }
            return true;
        });
        int from = 0;
        int to = Math.min(content.length(), 2 * SNIPPET_CONTEXT);
        if (match[0] >= 0) {
            from = Math.max(0, match[0] - SNIPPET_CONTEXT);
            to = Math.min(content.length(), match[1] + SNIPPET_CONTEXT);
        }
        // Evita cortar palavras no meio.
        while (from > 0 && Character.isLetterOrDigit(content.charAt(from - 1))) {
            from--;
        }
        while (to < content.length() && Character.isLetterOrDigit(content.charAt(to))) {
            to++;
        }
        String text = content.substring(from, to).replaceAll("\\s+", " ").trim();
        return (from > 0 ? "…" : "") + text + (to < content.length() ? "…" : "");
    }

    public Stats stats() {
        lock.readLock().lock();
        try {
            return new Stats(documents.size(), postings.size(), postingCount, tokenCount);
        } finally {
            lock.readLock().unlock();
        }
    }

    public record Stats(int documents, int terms, long postings, long tokens) {
    }

    /** Vetor de int crescente, evita boxing ao coletar posições. */
    private static final class IntList {
        private int[] values = new int[2];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
