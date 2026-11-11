package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.List;

/** Aplica regras sem estado em ordem explícita, aceitando a primeira correspondência. */
public class ClassificadorNormativo {
    // Aberturas substituem um título pendente. Títulos precedem qualquer regra do corpo.
    // Tabela/equação precedem marcadores textuais; TEXTO é somente o resultado residual.
    private final List<RegraClassificacao> regras = List.of(
            new RegraCapitulo(), new RegraAnexo(), new RegraTitulo(),
            new RegraConteudoEstruturado(), new RegraNumeracao(),
            new RegraAlinea(), new RegraSubalinea(), new RegraItemLista());

    /** Classifica o bloco sem atualizar o contexto; na ausência de correspondência retorna TEXTO. */
    public Classificacao classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        for (RegraClassificacao regra : regras) {
            var classificacao = regra.classificar(bloco, contexto);
            if (classificacao.isPresent()) return classificacao.get();
        }
        return new Classificacao(Tipo.TEXTO, null);
    }
}
