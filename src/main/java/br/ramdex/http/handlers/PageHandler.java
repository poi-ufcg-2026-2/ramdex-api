package br.ramdex.http.handlers;

import br.ramdex.http.Request;
import br.ramdex.http.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** {@code GET /}: página web simples para usar a API pelo navegador. */
public final class PageHandler {

    private final byte[] page;

    public PageHandler() {
        try (InputStream in = PageHandler.class.getResourceAsStream("/static/index.html")) {
            if (in == null) {
                throw new IllegalStateException("recurso /static/index.html não encontrado no jar");
            }
            page = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Response index(Request req) {
        return Response.html(page);
    }
}
