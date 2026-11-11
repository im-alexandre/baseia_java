package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

/**
 * Entrada interna extraída do DOM, ainda sem classificação normativa ou pais.
 * tituloEstrutural indica heading HTML ou alinhamento central explícito, sem inferir CSS externo.
 *
 * @param texto conteúdo textual ou HTML da tabela
 * @param tipoOrigem tag HTML ou categoria de origem do bloco
 * @param indiceOrigem posição do elemento selecionado no DOM, em base zero
 * @param ancora identificador id/name associado ao bloco, se presente
 * @param tituloEstrutural indica heading ou alinhamento central explícito
 */
public record BlocoLegislacao(String texto, String tipoOrigem, int indiceOrigem,
                              String ancora, boolean tituloEstrutural) {
}
