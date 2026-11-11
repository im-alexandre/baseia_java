package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Lê documentos ou fragmentos HTML com Jsoup, sem classificar dispositivos ou modificar a fonte. */
@Component
public class HtmlLegislacaoReader {
    private static final Pattern CENTRALIZADO = Pattern.compile("(?i)(?:^|;)\\s*text-align\\s*:\\s*center\\s*(?:;|$)");

    /**
     * Extrai parágrafos, headings e tabelas na ordem DOM. A área WordSection1 da Câmara,
     * quando presente, delimita o texto; a classe MsoHeader não significa cabeçalho descartável.
     * Tabelas permanecem HTML e seus parágrafos não são emitidos novamente.
     *
     * @param html documento HTML ou fragmento a ler
     * @return blocos extraídos na ordem do DOM
     * @throws IllegalArgumentException se o HTML estiver vazio ou não contiver elementos reconhecidos
     */
    public List<BlocoLegislacao> ler(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Informe o HTML da legislação.");
        }
        var documento = Jsoup.parse(html);
        documento.select("script, style, nav").remove();
        Element corpo = documento.selectFirst(".WordSection1");
        if (corpo == null) corpo = documento.body();
        var elementos = corpo.select("p, h1, h2, h3, h4, h5, h6, table");
        List<BlocoLegislacao> blocos = new ArrayList<>();
        for (int indice = 0; indice < elementos.size(); indice++) {
            Element elemento = elementos.get(indice);
            // A tabela externa já inclui integralmente as tabelas e os parágrafos internos.
            if (elemento.parents().stream().anyMatch(pai -> pai.tagName().equals("table"))) continue;
            boolean tabela = elemento.tagName().equals("table");
            String texto = tabela ? elemento.outerHtml() : elemento.text().strip();
            if (texto.isBlank()) continue;
            String tipoOrigem = tabela ? "table"
                    : elemento.closest("blockquote") != null ? "citacao" : elemento.tagName();
            boolean titulo = elemento.tagName().matches("h[1-6]")
                    || "center".equalsIgnoreCase(elemento.attr("align"))
                    || CENTRALIZADO.matcher(elemento.attr("style")).find();
            blocos.add(new BlocoLegislacao(texto, tipoOrigem, indice, ancora(elemento), titulo));
        }
        if (blocos.isEmpty()) {
            throw new IllegalArgumentException("O HTML deve conter parágrafos, títulos ou tabelas.");
        }
        return List.copyOf(blocos);
    }

    /**
     * Prioriza o id do bloco; depois, a primeira âncora id/name presente no seu conteúdo.
     *
     * @param elemento elemento DOM cuja referência será obtida
     * @return valor do atributo id/name ou {@code null} quando não houver referência
     */
    private String ancora(Element elemento) {
        if (!elemento.id().isBlank()) return elemento.id();
        Element referencia = elemento.selectFirst("a[name], [id]");
        if (referencia == null) return null;
        String valor = referencia.hasAttr("name") ? referencia.attr("name") : referencia.id();
        return valor.isBlank() ? null : valor;
    }
}
