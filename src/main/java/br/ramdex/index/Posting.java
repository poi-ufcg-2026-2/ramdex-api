package br.ramdex.index;

/**
 * Ocorrência de um termo em um documento: o documento e as posições (ordinais)
 * em que o termo aparece. A frequência do termo é o tamanho do vetor.
 */
public record Posting(long docId, int[] positions) {

    public int termFrequency() {
        return positions.length;
    }
}
