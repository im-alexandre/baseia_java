package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece ANEXO seguido de uma ou duas letras maiúsculas; a seleção fica com o parser. */
public class RegraAnexo implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile("^(ANEXO\\h+[A-Z]{1,2})\\.?$");

    /** Exige marcador isolado e centralizado; não decide se o conteúdo do anexo será incluído. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!bloco.centralizado() || !marcador.matches()) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.ANEXO, marcador.group(1)));
    }
}
