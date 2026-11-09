package br.com.alexandre.java_ai_lab.service;
import br.com.alexandre.java_ai_lab.dto.AnalisePatrimonial;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String responder(String pergunta){
        return chatClient.prompt(
                """
                        Você é um assistente especializado em gestão patrimonial da Marinha do Brasil.
                        
                        Responda em português, utilizando linguagem formal, técnica e objetiva.
                        
                        Se não souber a resposta, informe claramente.
                        """
        ).user(pergunta).call().content();
    }


    public AnalisePatrimonial analisar(String pergunta){
        return chatClient.prompt(
                """
                        Você é um assistente especializado em gestão patrimonial da Marinha do Brasil.
                        
                        Responda em português, utilizando linguagem formal, técnica e objetiva.
                        
                        Se não souber a resposta, informe claramente.
                        """
        ).user(pergunta).call().entity(AnalisePatrimonial.class);
    }
}
