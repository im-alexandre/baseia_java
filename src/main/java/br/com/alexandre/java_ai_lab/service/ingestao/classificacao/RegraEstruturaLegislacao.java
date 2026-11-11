package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece capítulos, seções, subseções e o fecho datado de Brasília, sem inferir títulos. */
public class RegraEstruturaLegislacao implements RegraClassificacao {
    private static final String NUMERO = "(?:[IVXLCDM]+|\\d+)\\.?(?=\\s|$)";
    private static final Pattern CAPITULO = Pattern.compile("^((?iu:CAP[ÍI]TULO)\\h+" + NUMERO + ")");
    private static final Pattern SECAO = Pattern.compile("^((?iu:SE[ÇC][ÃA]O)\\h+" + NUMERO + ")");
    private static final Pattern SUBSECAO = Pattern.compile("^((?iu:SUBSE[ÇC][ÃA]O)\\h+" + NUMERO + ")");
    private static final Pattern FECHO = Pattern.compile(
            "^(?iu:Brasília,\\h+\\d{1,2}º?\\h+de\\h+"
                    + "(?:janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)"
                    + "\\h+de\\h+\\d{4})(?=\\s|[.;]|$)");

    /**
     * Extrai só o rótulo das estruturas, aceitando título inline; fechos não recebem marcador.
     * A precedência é capítulo, seção, subseção e, por fim, fecho datado de Brasília.
     *
     * @param bloco bloco textual sob análise
     * @param contexto contexto normativo atual; esta regra não o utiliza
     * @return classificação da primeira estrutura reconhecida ou vazio
     */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var capitulo = CAPITULO.matcher(bloco.texto());
        if (capitulo.find()) return Optional.of(new Classificacao(Tipo.CAPITULO, capitulo.group(1)));
        var secao = SECAO.matcher(bloco.texto());
        if (secao.find()) return Optional.of(new Classificacao(Tipo.SECAO, secao.group(1)));
        var subsecao = SUBSECAO.matcher(bloco.texto());
        if (subsecao.find()) return Optional.of(new Classificacao(Tipo.SUBSECAO, subsecao.group(1)));
        if (FECHO.matcher(bloco.texto()).find()) return Optional.of(new Classificacao(Tipo.FECHO, null));
        return Optional.empty();
    }
}
