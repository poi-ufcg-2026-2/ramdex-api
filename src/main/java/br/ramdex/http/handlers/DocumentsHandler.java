package br.ramdex.http.handlers;

import br.ramdex.http.HttpError;
import br.ramdex.http.Multipart;
import br.ramdex.http.Request;
import br.ramdex.http.Response;
import br.ramdex.index.Document;
import br.ramdex.index.InvertedIndex;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** CRUD de documentos: {@code /documents} e {@code /documents/{id}}. */
public final class DocumentsHandler {

    private static final Set<String> TEXT_TYPES = Set.of(
            "", "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".txt", ".md", ".markdown");
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String DEFAULT_TITLE = "Sem título";

    private final InvertedIndex index;

    public DocumentsHandler(InvertedIndex index) {
        this.index = index;
    }

    /**
     * {@code POST /documents}. Aceita:
     * <ul>
     *   <li>{@code text/plain} ou {@code text/markdown}: o corpo é o conteúdo; título em {@code ?title=};</li>
     *   <li>{@code application/json}: {@code {"title": "...", "content": "..."}};</li>
     *   <li>{@code multipart/form-data}: campo {@code file} (.txt/.md) e, opcionalmente, {@code title}.</li>
     * </ul>
     */
    public Response create(Request req) throws Exception {
        String mediaType = req.mediaType();
        String title;
        String content;
        if (mediaType.equals("application/json")) {
            Map<String, Object> json = req.bodyAsJsonObject();
            title = stringField(json, "title", false);
            content = stringField(json, "content", true);
        } else if (mediaType.equals("multipart/form-data")) {
            List<Multipart.Part> parts = Multipart.parse(req.header("Content-Type"), req.body());
            Multipart.Part file = parts.stream()
                    .filter(p -> "file".equals(p.name()) || p.filename() != null)
                    .findFirst()
                    .orElseThrow(() -> HttpError.badRequest("envie o arquivo no campo 'file' do form-data"));
            checkExtension(file.filename());
            content = ensureText(file.data());
            title = parts.stream()
                    .filter(p -> "title".equals(p.name()) && p.filename() == null)
                    .map(Multipart.Part::text)
                    .findFirst()
                    .orElse(req.query("title") != null ? req.query("title") : file.filename());
        } else if (TEXT_TYPES.contains(mediaType)) {
            content = ensureText(req.body());
            title = req.query("title");
        } else {
            throw new HttpError(415, "Content-Type '" + mediaType
                    + "' não suportado; use text/plain, text/markdown, application/json ou multipart/form-data");
        }

        if (content == null || content.isBlank()) {
            throw HttpError.badRequest("o conteúdo do documento está vazio");
        }
        Document doc = index.add(normalizeTitle(title), content);
        return Response.created(summary(doc), "/documents/" + doc.id());
    }

    /** {@code GET /documents?offset=0&limit=20}: lista paginada (sem o conteúdo). */
    public Response list(Request req) {
        int offset = req.queryInt("offset", 0, 0, Integer.MAX_VALUE);
        int limit = req.queryInt("limit", 20, 1, 100);
        List<Map<String, Object>> page = new ArrayList<>();
        for (Document doc : index.list(offset, limit)) {
            page.add(summary(doc));
        }
        return Response.ok(Response.fields(
                "total", index.stats().documents(),
                "offset", offset,
                "limit", limit,
                "documents", page));
    }

    /** {@code GET /documents/{id}}: documento completo. */
    public Response get(Request req) {
        Document doc = index.get(id(req));
        if (doc == null) {
            throw HttpError.notFound("documento " + req.pathParam("id") + " não encontrado");
        }
        Map<String, Object> body = summary(doc);
        body.put("content", doc.content());
        return Response.ok(body);
    }

    /** {@code DELETE /documents/{id}}. */
    public Response delete(Request req) {
        long id = id(req);
        if (!index.remove(id)) {
            throw HttpError.notFound("documento " + id + " não encontrado");
        }
        return Response.ok(Response.fields("deleted", id));
    }

    private static Map<String, Object> summary(Document doc) {
        return Response.fields(
                "id", doc.id(),
                "title", doc.title(),
                "tokens", doc.tokenCount(),
                "uniqueTerms", doc.uniqueTerms(),
                "createdAt", Instant.ofEpochMilli(doc.createdAt()).toString());
    }

    private static long id(Request req) {
        return Request.parseLong("id", req.pathParam("id"));
    }

    private static String stringField(Map<String, Object> json, String name, boolean required) {
        Object value = json.get(name);
        if (value == null) {
            if (required) {
                throw HttpError.badRequest("campo '" + name + "' é obrigatório");
            }
            return null;
        }
        if (!(value instanceof String s)) {
            throw HttpError.badRequest("campo '" + name + "' deve ser uma string");
        }
        return s;
    }

    private static void checkExtension(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (TEXT_EXTENSIONS.stream().noneMatch(name::endsWith)) {
            throw new HttpError(415, "arquivo '" + filename + "' não suportado; envie .txt ou .md");
        }
    }

    /** Recusa conteúdo binário (ex.: PDF enviado como arquivo de texto). */
    private static String ensureText(byte[] data) {
        String prefix = new String(data, 0, Math.min(data.length, 5), StandardCharsets.ISO_8859_1);
        if (prefix.equals("%PDF-")) {
            throw new HttpError(415, "PDF não é suportado; converta para texto (ex.: pdftotext) e envie .txt");
        }
        for (byte b : data) {
            if (b == 0) {
                throw new HttpError(415, "o conteúdo não parece ser texto (contém bytes nulos)");
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.strip();
        return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
    }
}
