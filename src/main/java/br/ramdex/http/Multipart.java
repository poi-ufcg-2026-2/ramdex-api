package br.ramdex.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser simples de {@code multipart/form-data}, suficiente para o upload de
 * arquivos de texto feito pelo Postman (aba Body → form-data).
 */
public final class Multipart {

    public record Part(String name, String filename, String contentType, byte[] data) {

        public String text() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static final Pattern BOUNDARY = Pattern.compile("boundary=(\"?)([^\";]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("[; ]name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private Multipart() {
    }

    public static List<Part> parse(String contentTypeHeader, byte[] body) {
        Matcher m = BOUNDARY.matcher(contentTypeHeader == null ? "" : contentTypeHeader);
        if (!m.find()) {
            throw HttpError.badRequest("multipart/form-data sem boundary");
        }
        byte[] delimiter = ("--" + m.group(2)).getBytes(StandardCharsets.ISO_8859_1);
        List<Part> parts = new ArrayList<>();

        int pos = indexOf(body, delimiter, 0);
        if (pos < 0) {
            throw HttpError.badRequest("multipart/form-data malformado");
        }
        while (true) {
            pos += delimiter.length;
            if (pos + 1 < body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }
            pos = skipLineBreak(body, pos);
            int headersEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), pos);
            if (headersEnd < 0) {
                throw HttpError.badRequest("multipart/form-data malformado");
            }
            String headers = new String(body, pos, headersEnd - pos, StandardCharsets.UTF_8);
            int dataStart = headersEnd + 4;
            int next = indexOf(body, delimiter, dataStart);
            if (next < 0) {
                throw HttpError.badRequest("multipart/form-data malformado");
            }
            int dataEnd = next;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }
            byte[] data = new byte[Math.max(0, dataEnd - dataStart)];
            System.arraycopy(body, dataStart, data, 0, data.length);
            parts.add(new Part(find(NAME, headers), find(FILENAME, headers), headerValue(headers, "content-type"), data));
            pos = next;
        }
        return parts;
    }

    private static String find(Pattern pattern, String headers) {
        Matcher m = pattern.matcher(headers);
        return m.find() ? m.group(1) : null;
    }

    private static String headerValue(String headers, String name) {
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static int skipLineBreak(byte[] body, int pos) {
        if (pos < body.length && body[pos] == '\r') {
            pos++;
        }
        if (pos < body.length && body[pos] == '\n') {
            pos++;
        }
        return pos;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
