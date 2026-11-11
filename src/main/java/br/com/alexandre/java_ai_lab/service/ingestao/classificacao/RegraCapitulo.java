package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece capítulo centralizado, com título em caixa alta opcional no mesmo bloco. */
public class RegraCapitulo implements RegraClassificacao {
    private static final Pattern PADRAO = Pattern.compile(
            "^(CAP[ÍI]TULO\\h+\\d+)(?:\\.?|\\s+([\\p{Lu}].*))$", Pattern.DOTALL);

    /**
     * Preserva a grafia do marcador sem ponto final. Título unido deve começar por letra
     * e estar em caixa alta, sem pontuação entre número e título. Entradas de índice como
     * CAPÍTULO N. TÍTULO ou CAPÍTULO N - Título não são aberturas.
     */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var marcador = PADRAO.matcher(bloco.texto());
        if (!bloco.centralizado() || !marcador.matches()) return Optional.empty();
        String titulo = marcador.group(2);
        if (titulo != null && !titulo.equals(titulo.toUpperCase(Locale.ROOT))) return Optional.empty();
        return Optional.of(new Classificacao(Tipo.CAPITULO, marcador.group(1)));
    }
}
