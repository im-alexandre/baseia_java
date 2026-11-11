package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

import java.util.List;

/**
 * Bloco folha lido do MinerU, com texto e origem preservados antes de filtros normativos.
 * A página física é base 1; a página impressa pode ser nula quando não há page_number.
 */
public record BlocoMineru(String tipoOrigem, String texto, int paginaFisica,
                          String paginaImpressa, int indiceOrigem, List<Double> bbox,
                          boolean continuaAnterior) {
    /** Copia a geometria para que o bloco não dependa de uma lista mutável do chamador. */
    public BlocoMineru {
        bbox = List.copyOf(bbox);
    }
}
