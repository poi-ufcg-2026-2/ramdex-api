package br.ramdex.http.handlers;

import br.ramdex.generator.SyntheticGenerator;
import br.ramdex.http.HttpError;
import br.ramdex.http.Request;
import br.ramdex.http.Response;
import br.ramdex.index.InvertedIndex;
import br.ramdex.metrics.RuntimeMetrics;

import java.util.Map;

/**
 * {@code POST /generate}: gera e indexa documentos sintéticos.
 * Corpo (todos opcionais): {@code {"docs":10000,"wordsPerDoc":200,"vocabSize":50000,"seed":42}}.
 */
public final class GenerateHandler {

    private final InvertedIndex index;

    public GenerateHandler(InvertedIndex index) {
        this.index = index;
    }

    public Response generate(Request req) throws Exception {
        Map<String, Object> json = req.bodyAsJsonObject();
        int docs = Request.checkRange("docs", integer(json, "docs", 10_000), 1, 5_000_000);
        int wordsPerDoc = Request.checkRange("wordsPerDoc", integer(json, "wordsPerDoc", 200), 1, 10_000);
        int vocabSize = Request.checkRange("vocabSize", integer(json, "vocabSize", 50_000), 10, 1_000_000);
        long seed = integer(json, "seed", 42);

        SyntheticGenerator.Result result = SyntheticGenerator.generate(index, docs, wordsPerDoc, vocabSize, seed);
        double docsPerSecond = result.elapsedMs() == 0
                ? docs
                : Math.round(docs * 1000.0 / result.elapsedMs() * 10) / 10.0;

        return Response.ok(Response.fields(
                "generated", result.documents(),
                "words", result.words(),
                "elapsedMs", result.elapsedMs(),
                "docsPerSecond", docsPerSecond,
                "sampleTerms", result.sampleTerms(),
                "index", index.stats(),
                "heapUsedMb", RuntimeMetrics.usedHeapMb()));
    }

    private static long integer(Map<String, Object> json, String name, long defaultValue) {
        Object value = json.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Double d && d == Math.rint(d)) {
            return d.longValue();
        }
        throw HttpError.badRequest("campo '" + name + "' deve ser um número inteiro");
    }
}
