package br.ramdex.index;

/** Documento indexado. O conteúdo completo fica em memória para exibir trechos e o documento original. */
public record Document(long id, String title, String content, long createdAt, int tokenCount, int uniqueTerms) {
}
