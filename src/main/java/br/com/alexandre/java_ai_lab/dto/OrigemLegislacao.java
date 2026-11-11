package br.com.alexandre.java_ai_lab.dto;

import java.util.List;

/**
 * Classificação e referências DOM de cada bloco preservado em uma unidade de conteúdo HTML.
 *
 * @param tipo classificação normativa original do bloco
 * @param marcador marcador original reconhecido, se houver
 * @param indicesOrigem posições dos blocos DOM de origem, em base zero
 * @param ancora identificador id/name associado, se houver
 */
public record OrigemLegislacao(ElementoNormativo.Tipo tipo, String marcador,
                               List<Integer> indicesOrigem, String ancora) {
    /**
     * Conserva as posições de origem contra alterações posteriores pelo chamador.
     *
     * @param tipo classificação normativa original do bloco
     * @param marcador marcador original reconhecido, se houver
     * @param indicesOrigem posições dos blocos DOM de origem, em base zero
     * @param ancora identificador id/name associado, se houver
     * @throws NullPointerException se a lista de índices for nula
     */
    public OrigemLegislacao {
        indicesOrigem = List.copyOf(indicesOrigem);
    }
}
