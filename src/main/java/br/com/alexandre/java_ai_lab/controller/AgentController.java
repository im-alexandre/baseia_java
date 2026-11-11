package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.AgentService;

import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public String perguntar(@RequestParam String pergunta) {
        return agentService.perguntar(pergunta);
    }
}