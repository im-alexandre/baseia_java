package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

/**
 * Decisão interna de reconhecimento; não é um contrato HTTP nem um elemento persistido.
 * @param tipo categoria reconhecida
 * @param marcador rótulo reconhecido; as regras MinerU omitem o ponto final opcional,
 *                 enquanto as regras de legislação conservam a pontuação; null quando ausente
 */
public record Classificacao(Tipo tipo, String marcador) {}
