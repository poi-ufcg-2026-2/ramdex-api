package br.ramdex.http.handlers;

import br.ramdex.http.HttpError;
import br.ramdex.http.Request;
import br.ramdex.http.Response;
import br.ramdex.index.InvertedIndex;
import br.ramdex.index.SearchResult;

import java.util.Locale;

/** {@code GET /search?q=...&mode=and|or&limit=10}. */
public final class SearchHandler {

    private final InvertedIndex index;

    public SearchHandler(InvertedIndex index) {
        this.index = index;
    }

    public Response search(Request req) {
        String q = req.query("q");
        if (q == null || q.isBlank()) {
            throw HttpError.badRequest("informe a consulta no parâmetro 'q'");
        }
        InvertedIndex.Mode mode;
        try {
            mode = InvertedIndex.Mode.valueOf(req.query("mode", "and").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw HttpError.badRequest("'mode' deve ser 'and' ou 'or'");
        }
        int limit = req.queryInt("limit", 10, 1, 100);

        long start = System.nanoTime();
        SearchResult result = index.search(q, mode, limit);
        double tookMs = Math.round((System.nanoTime() - start) / 1_000.0) / 1_000.0;

        return Response.ok(Response.fields(
                "query", q,
                "mode", mode.name().toLowerCase(Locale.ROOT),
                "terms", result.terms(),
                "total", result.total(),
                "tookMs", tookMs,
                "results", result.hits()));
    }
}
