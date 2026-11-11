package br.com.alexandre.java_ai_lab.service.ingestao.filtragem;

import br.com.alexandre.java_ai_lab.service.ingestao.leitura.BlocoMineru;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.BlocoClassificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.ContextoClassificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.RegraCapitulo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reconhece capas editoriais e folhas de divisão de partes por sinais conjuntos de uma página.
 * Não altera os blocos do Reader nem considera a simples menção a volume ou capítulo como capa.
 */
public class IdentificadorPaginasPreliminares {
    private final RegraCapitulo regraCapitulo = new RegraCapitulo();
    private static final Pattern VOLUME = Pattern.compile("VOLUME\\s+(?:[IVXLCDM]+|\\d+)");
    private static final Pattern TIPO_PUBLICACAO = Pattern.compile("TIPO\\s*:\\s*\\S.*");
    private static final Pattern FINALIDADE = Pattern.compile("FINALIDADE\\s*:\\s*\\S.*");
    private static final Pattern EDICAO = Pattern.compile("(?:\\d+\\s*[ªAºO]?\\s*)?(?:REVISAO|EDICAO)(?:\\s+.*)?");
    private static final List<Pattern> METADADOS = List.of(VOLUME, TIPO_PUBLICACAO, FINALIDADE, EDICAO);
    private static final Set<String> ARTEFATOS_PAGINA = Set.of("header", "footer", "page_number");
    // O MinerU pode manter o ordinal como LaTeX (3^{a}) ou perder o ordinal inteiro.
    private static final Pattern PARTE = Pattern.compile("(?:\\d+\\s*(?:[ªA]|\\^\\{A\\})?\\s*)?PARTE");
    private static final Pattern ENTRADA_CAPITULO = Pattern.compile("^CAPITULO\\s+\\d+\\s*[-–—]\\s+.+");

    /** Retorna páginas físicas em base 1; todos os dados permanecem disponíveis na entrada. */
    public Set<Integer> identificar(List<BlocoMineru> blocos) {
        return blocos.stream().collect(Collectors.groupingBy(BlocoMineru::paginaFisica))
                .entrySet().stream()
                .filter(pagina -> ehCapa(pagina.getValue()) || ehDivisoriaDeParte(pagina.getValue()))
                .map(pagina -> pagina.getKey())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Usa o padrão institucional e editorial, sem depender do título, órgão emissor, ano ou código da norma. */
    private boolean ehCapa(List<BlocoMineru> blocos) {
        if (temAberturaCapitulo(blocos)) return false;
        List<BlocoMineru> corpo = blocos.stream()
                .filter(b -> !ARTEFATOS_PAGINA.contains(b.tipoOrigem())).toList();
        Set<String> textos = corpo.stream().map(b -> normalizar(b.texto())).collect(Collectors.toSet());
        boolean instituicao = textos.contains("MARINHA DO BRASIL");
        boolean titulo = corpo.stream().anyMatch(this::ehTituloDeCapa);
        // Categorias distintas evitam tratar a repetição de uma mesma palavra como vários sinais.
        long metadados = METADADOS.stream()
                .filter(padrao -> textos.stream().anyMatch(padrao.asMatchPredicate())).count();
        return instituicao && titulo && metadados >= 2;
    }

    /** Usa título centralizado ou caixa alta como evidência, sem exigir um rótulo específico do MinerU. */
    private boolean ehTituloDeCapa(BlocoMineru bloco) {
        boolean titulo = Set.of("doc_title", "paragraph_title").contains(bloco.tipoOrigem())
                || bloco.texto().equals(bloco.texto().toUpperCase(Locale.ROOT));
        if (!titulo || !centralizado(bloco)) return false;
        String texto = normalizar(bloco.texto());
        return texto.matches(".*\\p{L}.*") && !texto.equals("MARINHA DO BRASIL")
                && !METADADOS.stream()
                        .anyMatch(padrao -> padrao.matcher(texto).matches());
    }

    /** Distingue a folha de parte com índice de capítulos de uma abertura real do corpo normativo. */
    private boolean ehDivisoriaDeParte(List<BlocoMineru> blocos) {
        boolean parte = blocos.stream().anyMatch(b -> centralizado(b)
                && PARTE.matcher(normalizar(b.texto())).matches());
        if (!parte) return false;
        boolean entradas = blocos.stream()
                .anyMatch(b -> ENTRADA_CAPITULO.matcher(normalizar(b.texto())).find());
        return entradas && !temAberturaCapitulo(blocos);
    }

    /** Preserva páginas com abertura normativa real, mesmo quando também trazem dados editoriais. */
    private boolean temAberturaCapitulo(List<BlocoMineru> blocos) {
        return blocos.stream().filter(b -> !ARTEFATOS_PAGINA.contains(b.tipoOrigem()))
                .anyMatch(b -> regraCapitulo.classificar(
                        new BlocoClassificacao(b.texto(), b.tipoOrigem(), b.bbox()),
                        new ContextoClassificacao(-1, null)).isPresent());
    }

    /** Usa somente a centralização horizontal normalizada; não exige fonte ou espaçamento entre linhas. */
    private boolean centralizado(BlocoMineru bloco) {
        return new BlocoClassificacao(bloco.texto(), bloco.tipoOrigem(), bloco.bbox()).centralizado();
    }

    /** Normaliza para reconhecimento, sem substituir a grafia original que será retornada. */
    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
