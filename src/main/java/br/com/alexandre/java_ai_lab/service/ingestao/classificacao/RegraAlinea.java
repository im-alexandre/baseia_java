package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece uma ou duas letras minúsculas seguidas de parêntese no início do texto. */
public class RegraAlinea implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile("^([a-z]{1,2}\\))(?=\\h|$)");

    /** Identifica o marcador textual sem exigir o tipo atribuído pelo MinerU ou recuo lateral. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!marcador.find()) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.ALINEA, marcador.group(1)));
    }
}
