package br.ramdex.index;

import java.util.List;

/** Resultado de uma consulta: termos usados, total de documentos encontrados e os mais relevantes. */
public record SearchResult(List<String> terms, int total, List<Hit> hits) {

    public record Hit(long id, String title, double score, String snippet) {
    }
}
