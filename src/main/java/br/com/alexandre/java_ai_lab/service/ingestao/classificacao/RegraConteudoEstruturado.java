package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Map;
import java.util.Optional;

/** Preserva a identificação de tabelas e equações fornecida pelo produtor do JSON. */
public class RegraConteudoEstruturado implements RegraClassificacao {
    private static final Map<String, Tipo> TIPOS = Map.of("table", Tipo.TABELA, "equation", Tipo.EQUACAO);

    /** Usa a chave explícita de origem, independentemente de marcadores dentro do HTML/LaTeX. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        return Optional.ofNullable(TIPOS.get(bloco.tipoOrigem()))
                .map(tipo -> new Classificacao(tipo, null));
    }
}
