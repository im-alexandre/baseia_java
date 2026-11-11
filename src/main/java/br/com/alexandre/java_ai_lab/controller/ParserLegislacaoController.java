package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.service.ingestao.DownloadLegislacaoService;
import br.com.alexandre.java_ai_lab.service.ingestao.ParserLegislacaoService;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.HtmlLegislacaoReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/** Adaptador HTTP da leitura HTML, no mesmo padrão do parser de normas e sem armazenamento. */
@RestController
public class ParserLegislacaoController {
    private final DownloadLegislacaoService download;
    private final HtmlLegislacaoReader reader;
    private final ParserLegislacaoService parser;

    /**
     * Conecta o download opcional à leitura tipada e ao processamento determinístico.
     *
     * @param download serviço usado quando a requisição informa uma URL
     * @param reader extrator de blocos a partir do HTML
     * @param parser classificador e organizador dos elementos legais
     */
    public ParserLegislacaoController(DownloadLegislacaoService download, HtmlLegislacaoReader reader,
                                      ParserLegislacaoService parser) {
        this.download = download;
        this.reader = reader;
        this.parser = parser;
    }

    /**
     * Baixa e processa HTML de uma URL informada explicitamente.
     * URL inválida resulta em 400, falha de download em 502 e HTML incompatível em 400.
     *
     * @param url endereço HTTP(S) da legislação
     * @return elementos legais extraídos e hierarquizados em ordem documental
     */
    @GetMapping(value = "/ingestao/legislacao/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ElementoLegislacao> porUrl(@RequestParam String url) {
        validarUrl(url);
        try {
            return parse(download.parse(url).content());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível baixar a legislação.", e);
        }
    }

    /**
     * Recebe o HTML integral ou fragmento no corpo e retorna dispositivos e pais, sem indexar no RAG.
     *
     * @param html documento ou fragmento HTML enviado no corpo da requisição
     * @return elementos legais extraídos e hierarquizados em ordem documental
     * @throws ResponseStatusException com status 400 quando o HTML não pode ser lido
     */
    @PostMapping(value = "/ingestao/legislacao/parse", consumes = MediaType.TEXT_HTML_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ElementoLegislacao> parse(@RequestBody String html) {
        try {
            return parser.parse(reader.ler(html));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Aceita apenas URL HTTP(S) absoluta; o HTML enviado por POST não provoca requisições externas.
     *
     * @param url endereço informado pelo cliente
     * @throws ResponseStatusException com status 400 se a URL não for HTTP(S) absoluta
     */
    private void validarUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("Informe uma URL HTTP(S) absoluta.");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma URL HTTP(S) absoluta.", e);
        }
    }
}
