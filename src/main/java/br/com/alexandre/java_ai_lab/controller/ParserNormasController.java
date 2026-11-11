package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.ParserNormasService;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;

/** Adaptador HTTP do parser local; recebe o JSON completo, sem upload multipart ou caminho de arquivo. */
@RestController
public class ParserNormasController {
    private final MineruReader mineruReader;
    private final ParserNormasService parserNormasService;

    /** Conecta a leitura do formato de entrada ao processamento normativo. */
    public ParserNormasController(MineruReader mineruReader, ParserNormasService parserNormasService) {
        this.mineruReader = mineruReader;
        this.parserNormasService = parserNormasService;
    }

    /**
     * Retorna conteúdos agrupados com pais e origens, omitindo registros de capítulo, artigo e inciso.
     * Não armazena nem indexa o resultado.
     * A query opcional anexosPermitidos=A,C seleciona letras de anexos já revisados.
     * Na ausência da query, todos os anexos ficam excluídos.
     */
    @PostMapping(value = "/ingestao/normas/parse", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ElementoNormativo> parse(@RequestBody JsonNode documento,
            @RequestParam(required = false) Set<String> anexosPermitidos) {
        try {
            return parserNormasService.parse(mineruReader.ler(documento),
                    anexosPermitidos == null ? Set.of() : anexosPermitidos);
        } catch (IllegalArgumentException e) {
            // Documento incompatível ou dados de layout inválidos são erros de entrada (400).
            // JSON malformado é rejeitado pelo próprio Spring antes de chegar a este método.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
