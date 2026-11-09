package br.com.alexandre.java_ai_lab.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final RetrievalService retrievalService;
    private final ContextAssemblerService assembler;
    private final ChatClient chatClient;

    public RagService(
            RetrievalService retrievalService,
            ContextAssemblerService assembler,
            ChatClient.Builder builder
    ) {
        this.retrievalService = retrievalService;
        this.assembler = assembler;
        this.chatClient = builder.build();
    }

    public String perguntar(String pergunta) {

        var resultados = retrievalService.buscar(pergunta);

        String contexto = assembler.montar(resultados);

        if (contexto.isBlank()) {
            return "Nenhum documento relevante foi recuperado.";
        }

        return chatClient.prompt()
                .system("""
    Você é um assistente técnico especializado em
    gestão patrimonial da Marinha do Brasil.

    Responda exclusivamente com base no contexto recuperado.

    REGRAS:
    1. Utilize apenas as informações fornecidas.
    2. Não invente procedimentos ou referências.
    3. Considere a hierarquia e a identificação das seções.
    4. Não misture informações de procedimentos distintos.
    5. Se houver etapas explicitamente identificadas,
       respeite sua sequência.
    6. Cite as fontes utilizando [FONTE n].
    7. Não cite fontes que não sustentem a afirmação.
    8. Se as informações forem insuficientes,
       informe claramente.
    9. Não acrescente conclusões sem suporte documental.
    10. Termine a resposta com a seção "Fontes consultadas".
        Liste uma vez cada fonte citada, mantendo seu marcador [FONTE n]
        e identificando norma, página e seção conforme os metadados
        do contexto recuperado. Omita os metadados ausentes;
        não invente referências nem inclua IDs internos ou scores de busca.
        Não escreva mais nada depois da lista de fontes.
    """)
                .user(u -> u.text("""
                    PERGUNTA:
                    {pergunta}

                    CONTEXTO:
                    {contexto}
                    """)
                        .param("pergunta", pergunta)
                        .param("contexto", contexto))
                .call()
                .content();

    }
}
