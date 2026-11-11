package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece caracteres romanos maiúsculos seguidos de parêntese, sem corrigir OCR. */
public class RegraSubalinea implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile("^([IVXLCDM]+\\))(?=\\h|$)");

    /** Preserva o padrão existente; não valida a forma canônica ou a sequência dos algarismos romanos. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!marcador.find()) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.SUBALINEA, marcador.group(1)));
    }
}
