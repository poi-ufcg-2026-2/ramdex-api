package br.ramdex;

import br.ramdex.http.Router;
import br.ramdex.http.handlers.DocumentsHandler;
import br.ramdex.http.handlers.GenerateHandler;
import br.ramdex.http.handlers.SearchHandler;
import br.ramdex.http.handlers.StatsHandler;
import br.ramdex.index.InvertedIndex;
import br.ramdex.metrics.RuntimeMetrics;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sobe a API. Configuração por variáveis de ambiente:
 * <ul>
 *   <li>{@code PORT} (padrão 8080)</li>
 *   <li>{@code THREADS}: threads que atendem requisições (padrão: número de núcleos)</li>
 *   <li>{@code MAX_BODY_MB}: tamanho máximo do corpo das requisições (padrão 10)</li>
 *   <li>{@code BACKLOG}: fila de conexões TCP pendentes (padrão 1024)</li>
 * </ul>
 * A memória disponível é definida na JVM: {@code java -Xmx1g -jar ramdex-api.jar}.
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        int port = env("PORT", 8080);
        int threads = env("THREADS", Math.max(2, Runtime.getRuntime().availableProcessors()));
        int maxBodyMb = env("MAX_BODY_MB", 10);
        int backlog = env("BACKLOG", 1024);

        InvertedIndex index = new InvertedIndex();
        DocumentsHandler documents = new DocumentsHandler(index);
        SearchHandler search = new SearchHandler(index);
        GenerateHandler generate = new GenerateHandler(index);
        StatsHandler stats = new StatsHandler(index, threads, maxBodyMb);

        Router router = new Router(maxBodyMb * 1024 * 1024)
                .get("/health", stats::health)
                .get("/stats", stats::stats)
                .delete("/index", stats::clear)
                .get("/documents", documents::list)
                .post("/documents", documents::create)
                .get("/documents/{id}", documents::get)
                .delete("/documents/{id}", documents::delete)
                .get("/search", search::search)
                .post("/generate", generate::generate)
                .get("/generate/terms", generate::terms);

        // Sem TCP_NODELAY, o algoritmo de Nagle somado ao ACK atrasado do TCP adiciona ~40 ms
        // a cada requisição numa conexão keep-alive, o que distorceria os testes de carga.
        System.setProperty("sun.net.httpserver.nodelay", "true");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), backlog);
        server.createContext("/", router);
        server.setExecutor(executor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));

        System.out.printf("[ramdex] ouvindo em http://0.0.0.0:%d (threads=%d, heap máx=%s MB, java %s)%n",
                port, threads, RuntimeMetrics.heap().get("maxMb"), System.getProperty("java.version"));
    }

    private static int env(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("variável " + name + " deve ser um número inteiro: " + value);
        }
    }
}
