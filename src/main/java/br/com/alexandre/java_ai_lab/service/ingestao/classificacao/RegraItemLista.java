package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece hífen, meia-risca ou travessão seguido de espaço horizontal no início do texto. */
public class RegraItemLista implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile("^([-–—])\\h+");

    /** Extrai somente o sinal da lista, mantendo o conteúdo completo no bloco original. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!marcador.find()) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.ITEM_LISTA, marcador.group(1)));
    }
}
