package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

/**
 * Visão imutável do estado necessário às regras, sem acesso às permissões ou à lista de saída.
 * @param capitulo capítulo ativo, ou -1 fora de capítulo
 * @param tituloEsperado tipo de título pendente, ou null quando não há título pendente
 */
public record ContextoClassificacao(int capitulo, Tipo tituloEsperado) {}
