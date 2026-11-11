package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece romanos maiúsculos seguidos de parêntese, mesmo sem espaço após o sinal. */
public class RegraSubalinea implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile("^([IVXLCDM]+\\))");

    /** Aceita "III)o bem"; sequência e forma canônica são verificadas depois, sem correção. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!marcador.find()) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.SUBALINEA, marcador.group(1)));
    }
}
