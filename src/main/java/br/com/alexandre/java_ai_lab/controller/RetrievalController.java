package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ContextAssemblerService;
import br.com.alexandre.java_ai_lab.service.RetrievalService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrievalController {

    private final RetrievalService retrievalService;
    private final ContextAssemblerService assembler;

    public RetrievalController(RetrievalService retrievalService, ContextAssemblerService assembler) {
        this.retrievalService = retrievalService;
        this.assembler = assembler;
    }

    @GetMapping(value = "/retrieval", produces = MediaType.TEXT_PLAIN_VALUE)
    public String buscar(@RequestParam String pergunta) {
        var resultados = retrievalService.buscar(pergunta);
        return assembler.montar(resultados);
    }
}
