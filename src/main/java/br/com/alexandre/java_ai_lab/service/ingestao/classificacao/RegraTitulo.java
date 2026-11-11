package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import java.util.Optional;

/** Reconhece o próximo bloco útil como título quando o contexto informa um título pendente. */
public class RegraTitulo implements RegraClassificacao {
    /** Preserva a heurística atual: não exige maiúsculas, centralização ou tipo de origem específico. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        return Optional.ofNullable(contexto.tituloEsperado())
                .map(tipo -> new Classificacao(tipo, null));
    }
}
