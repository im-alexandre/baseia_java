package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import java.util.Optional;

/** Reconhece um padrão normativo sem alterar o bloco ou o estado de leitura do documento. */
public interface RegraClassificacao {
    /** Retorna tipo e marcador quando aplicável; vazio permite tentar a próxima regra da lista. */
    Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto);
}
