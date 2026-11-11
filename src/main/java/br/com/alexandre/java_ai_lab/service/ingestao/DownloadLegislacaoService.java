package br.com.alexandre.java_ai_lab.service.ingestao;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DownloadLegislacaoService {

    /**
     * Resultado do download, com a URL efetiva e o HTML do corpo do documento.
     *
     * @param sourceUrl URL do documento efetivamente baixado
     * @param title título HTML do documento
     * @param content conteúdo HTML do corpo do documento
     */
    public record ParsedLegislation(
            String sourceUrl,
            String title,
            String content
    ) {}

    /**
     * Baixa a legislação e, para páginas de norma da Câmara, seleciona o link de texto atualizado quando disponível.
     *
     * @param url endereço inicial do documento
     * @return URL efetiva, título e HTML do corpo do documento
     * @throws IOException quando a conexão ou leitura do documento falha
     */
    public ParsedLegislation parse(String url) throws IOException {
        Document document = baixarDocumento(url);

        if (url.contains("-norma-")) {
            for (Element link : document.select("a[href]")) {
                if (link.text().toLowerCase().contains("texto atualizado")
                        && link.absUrl("href").endsWith(".html")) {
                    url = link.absUrl("href");
                    document = baixarDocumento(url);
                    break;
                }
            }
        }

        return new ParsedLegislation(
                url,
                document.title(),
                document.body().html()
        );
    }

    /**
     * Busca uma página HTML com o user agent e o timeout usados pelo serviço.
     *
     * @param url endereço da página a baixar
     * @return documento HTML carregado pelo Jsoup
     * @throws IOException quando a conexão ou leitura do documento falha
     */
    private Document baixarDocumento(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(30000)
                .get();
    }

    /**
     * Executa uma demonstração local do download e imprime título e conteúdo obtidos.
     *
     * @param args argumentos da linha de comando, não utilizados
     * @throws IOException quando a demonstração não consegue baixar o documento
     */
    public static void main(String[] args) throws IOException {
        String url = "https://www2.camara.leg.br/legin/fed/lei/2021/lei-14129-29-marco-2021-791203-normaatualizada-pl.html";
        var parser = new DownloadLegislacaoService();
        ParsedLegislation result = parser.parse(url);
        System.out.println(result.title());
        System.out.println(result.content());
    }
}
