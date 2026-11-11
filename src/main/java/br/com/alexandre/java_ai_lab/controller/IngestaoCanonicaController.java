package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ingestao.ParserLegislacaoService;
import br.com.alexandre.java_ai_lab.service.ingestao.ParserNormasService;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.IndexadorQdrant;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Preparacao;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.PreparadorNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.HtmlLegislacaoReader;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/** Entrada para a nova fase: preparo determinístico e indexação acionada explicitamente. */
@RestController
public class IngestaoCanonicaController {
    private final MineruReader mineru;
    private final ParserNormasService normas;
    private final HtmlLegislacaoReader html;
    private final ParserLegislacaoService legislacao;
    private final PreparadorNormativo preparador;
    private final IndexadorQdrant indexador;

    /** Conecta parsers existentes à representação canônica e ao indexador opcional. */
    public IngestaoCanonicaController(MineruReader mineru, ParserNormasService normas,
                                      HtmlLegislacaoReader html, ParserLegislacaoService legislacao,
                                      PreparadorNormativo preparador, IndexadorQdrant indexador) {
        this.mineru = mineru;
        this.normas = normas;
        this.html = html;
        this.legislacao = legislacao;
        this.preparador = preparador;
        this.indexador = indexador;
    }

    /** Recebe o documento MinerU integral e devolve nós, chunks e alertas sem indexar. */
    @PostMapping(value = "/ingestao/normas/preparar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Preparacao prepararNorma(@RequestBody JsonNode documento,
                                   @RequestParam String codigo, @RequestParam String versao,
                                   @RequestParam(required = false) Set<String> anexosPermitidos) {
        try {
            return preparador.norma(codigo, versao, normas.parseCompleto(mineru.ler(documento),
                    anexosPermitidos == null ? Set.of() : anexosPermitidos));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** Recebe HTML e devolve a árvore com dispositivos da própria lei e redações citadas. */
    @PostMapping(value = "/ingestao/legislacao/preparar", consumes = MediaType.TEXT_HTML_VALUE)
    public Preparacao prepararLei(@RequestBody String documento,
                                 @RequestParam String codigo, @RequestParam String versao) {
        try {
            return preparador.lei(codigo, versao, legislacao.parseCompleto(html.ler(documento)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** Faz embeddings e upsert somente quando o cliente escolhe a indexação. */
    @PostMapping(value = "/ingestao/normas/indexar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Integer> indexarNorma(@RequestBody JsonNode documento,
            @RequestParam String codigo, @RequestParam String versao,
            @RequestParam(defaultValue = "false") boolean confirmarAlertas,
            @RequestParam(required = false) Set<String> anexosPermitidos) {
        return indexar(prepararNorma(documento, codigo, versao, anexosPermitidos), confirmarAlertas);
    }

    /** Permite indexar explicitamente a versão HTML após consultar a preparação. */
    @PostMapping(value = "/ingestao/legislacao/indexar", consumes = MediaType.TEXT_HTML_VALUE)
    public Map<String, Integer> indexarLei(@RequestBody String documento,
            @RequestParam String codigo, @RequestParam String versao,
            @RequestParam(defaultValue = "false") boolean confirmarAlertas) {
        return indexar(prepararLei(documento, codigo, versao), confirmarAlertas);
    }

    /** Traduz validação em 409 e ausência de modelo/índice em 503 sem ocultar erros de entrada. */
    private Map<String, Integer> indexar(Preparacao preparacao, boolean confirmarAlertas) {
        try {
            return Map.of("pontos", indexador.indexar(preparacao, confirmarAlertas),
                    "alertas", preparacao.alertas().size());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
    }
}
