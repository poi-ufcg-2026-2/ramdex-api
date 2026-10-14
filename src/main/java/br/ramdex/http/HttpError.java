package br.ramdex.http;

/** Erro que vira uma resposta HTTP com o status indicado e {@code {"error": mensagem}}. */
public class HttpError extends RuntimeException {

    private final int status;

    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static HttpError badRequest(String message) {
        return new HttpError(400, message);
    }

    public static HttpError notFound(String message) {
        return new HttpError(404, message);
    }
}
