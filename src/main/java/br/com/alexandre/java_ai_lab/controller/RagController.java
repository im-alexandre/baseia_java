package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.RagService;
import com.openai.core.JsonObject;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping
    public String perguntar(@RequestParam String pergunta) {
        return ragService.perguntar(pergunta);
    }
}