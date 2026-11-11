package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Compartilha o reconhecimento de artigos e incisos, distinguindo dois ou três componentes. */
public class RegraNumeracao implements RegraClassificacao {
    // Delimita o número para não reconhecer prefixos de numerações mais longas.
    // Aceita ponto final opcional e separadores como em "1.3.1 - Militares".
    private static final Pattern PADRAO = Pattern.compile(
            "^(\\d+\\.\\d+(?:\\.\\d+)?)(?:\\.)?(?=\\h|$|[-–—])");

    /** Exige o prefixo do capítulo ativo; fora de capítulo essa regra não se aplica. */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var numero = PADRAO.matcher(bloco.texto());
        if (!numero.find() || !numero.group(1).startsWith(contexto.capitulo() + ".")) return Optional.empty();
        String marcador = numero.group(1);
        Tipo tipo = marcador.chars().filter(c -> c == '.').count() == 1 ? Tipo.ARTIGO : Tipo.INCISO;
        return Optional.of(new Classificacao(tipo, marcador));
    }
}
