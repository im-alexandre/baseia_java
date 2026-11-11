package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reconhece artigos, parágrafos, incisos romanos e itens decimais no início do bloco. */
public class RegraDispositivoLegal implements RegraClassificacao {
    private static final String NUMERO = "\\d+[º°]?(?:-[A-Z])?\\.?";
    private static final Pattern ARTIGO = Pattern.compile("^((?iu:Art\\.)\\h*" + NUMERO + ")(?=\\s|$)");
    private static final Pattern PARAGRAFO = Pattern.compile(
            "^((?:§\\h*" + NUMERO + "|(?iu:Parágrafo\\h+único)\\.))(?=\\s|$)");
    private static final Pattern INCISO = Pattern.compile("^([IVXLCDM]+\\h*[-–—])(?=\\s|$)");
    private static final Pattern ITEM = Pattern.compile("^(\\d+\\.)(?=\\s|$)");

    /**
     * Preserva a pontuação do marcador e exige uma fronteira para não aceitar prefixos parciais.
     * A precedência interna é artigo, parágrafo, inciso romano e item decimal.
     *
     * @param bloco bloco textual sob análise
     * @param contexto contexto normativo atual; esta regra não o utiliza
     * @return classificação do primeiro marcador reconhecido ou vazio
     */
    @Override
    public Optional<Classificacao> classificar(BlocoClassificacao bloco, ContextoClassificacao contexto) {
        var artigo = ARTIGO.matcher(bloco.texto());
        if (artigo.find()) return Optional.of(new Classificacao(Tipo.ARTIGO, artigo.group(1)));
        var paragrafo = PARAGRAFO.matcher(bloco.texto());
        if (paragrafo.find()) return Optional.of(new Classificacao(Tipo.PARAGRAFO, paragrafo.group(1)));
        var inciso = INCISO.matcher(bloco.texto());
        if (inciso.find()) return Optional.of(new Classificacao(Tipo.INCISO, inciso.group(1)));
        var item = ITEM.matcher(bloco.texto());
        if (item.find()) return Optional.of(new Classificacao(Tipo.ITEM, item.group(1)));
        return Optional.empty();
    }
}
