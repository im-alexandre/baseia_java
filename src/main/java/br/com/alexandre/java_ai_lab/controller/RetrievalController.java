package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.RetrievalService;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
public class RetrievalController {

    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/retrieval")
    public JsonNode buscar(@RequestParam String pergunta) {
        return retrievalService.buscar(pergunta);
    }
}