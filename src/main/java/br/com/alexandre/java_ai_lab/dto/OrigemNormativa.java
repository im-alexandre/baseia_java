package br.com.alexandre.java_ai_lab.dto;

import java.util.List;

/**
 * Referência a um bloco de origem, mesmo quando seu conteúdo participa de uma unidade agrupada.
 * Tipo e marcador são os reconhecidos antes do agrupamento; a caixa pertence somente a este bloco.
 */
public record OrigemNormativa(ElementoNormativo.Tipo tipo, String marcador,
                              int paginaFisica, String paginaImpressa, int indiceOrigem,
                              List<Double> bbox, boolean continuaAnterior) {
    /** Preserva a geometria contra alterações externas à origem. */
    public OrigemNormativa {
        bbox = List.copyOf(bbox);
    }
}
