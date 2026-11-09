package br.com.alexandre.java_ai_lab.service;

import br.com.alexandre.java_ai_lab.tools.NormasTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private final NormasTools normasTools;

    public AgentService(
            ChatClient.Builder builder,
            NormasTools normasTools
    ) {
        this.chatClient = builder.build();
        this.normasTools = normasTools;
    }

    public String perguntar(String pergunta) {

        return chatClient.prompt()
                .system("""
                    Você é um assistente técnico especializado
                    em gestão patrimonial da Marinha do Brasil.

                    Para responder perguntas sobre normas,
                    procedimentos ou sistemas patrimoniais,
                    consulte a ferramenta consultar_normas.

                    Responda exclusivamente com base
                    nos documentos recuperados.

                    Regras:
                    - Não invente procedimentos.
                    - Não misture disposições de normas diferentes.
                    - Cite as fontes utilizando [FONTE n].
                    - Termine a resposta com a seção "Fontes consultadas".
                    - Nessa seção, liste uma vez cada fonte citada, mantendo
                      seu marcador [FONTE n] e identificando norma, página e
                      seção conforme os metadados dos documentos recuperados.
                    - Omita os metadados ausentes; não invente referências
                      nem inclua IDs internos ou scores de busca.
                    - Não escreva mais nada depois da lista de fontes.
                    - Não cite informações sem suporte documental.
                    - Se não encontrar informações suficientes,
                      informe claramente.
                    """)
                .user(pergunta)
                .tools(normasTools)
                .call()
                .content();
    }
}
