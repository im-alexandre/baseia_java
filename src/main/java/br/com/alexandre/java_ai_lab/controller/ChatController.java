package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.dto.AnalisePatrimonial;
import br.com.alexandre.java_ai_lab.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService){
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String chat (@RequestParam String pergunta) {

        return chatService.responder(pergunta);
    }

    @PostMapping("/analisar")
    public AnalisePatrimonial analsar(@RequestBody String texto) {
        return chatService.analisar(texto);
    }
}
