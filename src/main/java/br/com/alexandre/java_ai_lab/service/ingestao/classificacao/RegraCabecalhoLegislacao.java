package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece a identificação e a síntese do objeto da lei, quando o parser permite cabeçalho. */
public class RegraCabecalhoLegislacao implements RegraClassificacao {
    private static final Pattern IDENTIFICACAO = Pattern.compile(
            "(?iu)^LEI(?:\\h+COMPLEMENTAR)?\\h+N[º°o]\\.?\\h*\\d+(?:\\.\\d{3})*"
                    + "(?:\\h*,\\h*DE\\h+[^.;]+)?\\.?$");
    private static final Pattern EMENTA = Pattern.compile(
            "(?iu)^(?:Disp[õo]e\\h+sobre\\b|(?:Altera|Institui|Estabelece|Regulamenta|Autoriza|"
                    + "Cria|Revoga|Define|Denomina|Aprova|Acrescenta)\\h+)");

    /**
     * Reconhece primeiro a linha de identificação e depois os verbos introdutórios da ementa.
     * Não cria marcador: o conteúdo integral de ambos deve permanecer na resposta.
     * A decisão de permitir esses tipos antes do corpo e fora de citações pertence ao parser.
     *
     * @param bloco conteúdo textual candidato a cabeçalho da lei
     * @param contexto contexto compartilhado de classificação; esta regra não o utiliza
     * @return identificação, ementa ou vazio quando nenhum padrão corresponde
     */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        if (IDENTIFICACAO.matcher(bloco.texto()).matches()) {
            return Optional.of(new Classificacao(Tipo.IDENTIFICACAO_LEI, null));
        }
        if (EMENTA.matcher(bloco.texto()).find()) return Optional.of(new Classificacao(Tipo.EMENTA, null));
        return Optional.empty();
    }
}
