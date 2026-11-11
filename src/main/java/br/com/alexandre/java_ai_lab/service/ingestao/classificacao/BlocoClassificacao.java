package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import java.util.List;

/** Dados internos já extraídos: bbox validado pelo reader MinerU, ou vazio na leitura HTML sem geometria. */
public record BlocoClassificacao(String texto, String tipoOrigem, List<Double> bbox) {
    /** Evita que alterações na lista de origem modifiquem o bloco durante a classificação. */
    public BlocoClassificacao {
        bbox = List.copyOf(bbox);
    }

    /** Usa caixa de quatro coordenadas do MinerU; admite o centro a até 10% de distância do centro da página. */
    public boolean centralizado() {
        return Math.abs((bbox.get(0) + bbox.get(2)) / 2 - 0.5) <= 0.1;
    }
}
