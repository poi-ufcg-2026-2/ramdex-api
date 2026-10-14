package br.ramdex.http.handlers;

import br.ramdex.http.Request;
import br.ramdex.http.Response;
import br.ramdex.index.InvertedIndex;
import br.ramdex.metrics.RuntimeMetrics;

/** {@code GET /health}, {@code GET /stats} e {@code DELETE /index}. */
public final class StatsHandler {

    private final InvertedIndex index;
    private final int threads;
    private final int maxBodyMb;

    public StatsHandler(InvertedIndex index, int threads, int maxBodyMb) {
        this.index = index;
        this.threads = threads;
        this.maxBodyMb = maxBodyMb;
    }

    public Response health(Request req) {
        return Response.ok(Response.fields("status", "ok", "uptimeMs", RuntimeMetrics.uptimeMs()));
    }

    public Response stats(Request req) {
        return Response.ok(Response.fields(
                "index", index.stats(),
                "memory", Response.fields("heap", RuntimeMetrics.heap(), "nonHeap", RuntimeMetrics.nonHeap()),
                "gc", RuntimeMetrics.garbageCollectors(),
                "system", RuntimeMetrics.system(),
                "server", Response.fields("threads", threads, "maxBodyMb", maxBodyMb),
                "jvm", Response.fields(
                        "version", System.getProperty("java.version"),
                        "uptimeMs", RuntimeMetrics.uptimeMs())));
    }

    /** Esvazia o índice. Com {@code ?gc=true} pede um GC para que a queda do heap apareça na hora. */
    public Response clear(Request req) {
        boolean gc = Boolean.parseBoolean(req.query("gc", "false"));
        int removed = index.stats().documents();
        double before = RuntimeMetrics.usedHeapMb();
        index.clear();
        if (gc) {
            System.gc();
        }
        return Response.ok(Response.fields(
                "removedDocuments", removed,
                "gcRequested", gc,
                "heapBeforeMb", before,
                "heapAfterMb", RuntimeMetrics.usedHeapMb()));
    }
}
