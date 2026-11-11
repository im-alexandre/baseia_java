package br.com.alexandre.java_ai_lab.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Value("${baseia.qdrant.collection}")
    private String collection;

    @Value("${baseia.qdrant.vector-name}")
    private String vectorName;

    public RetrievalService(
            EmbeddingModel embeddingModel,
            RestClient.Builder restClientBuilder,
            JsonMapper jsonMapper,
            @Value("${baseia.qdrant.url}") String qdrantUrl
    ) {
        this.embeddingModel = embeddingModel;
        this.restClient = restClientBuilder
                .baseUrl(qdrantUrl)
                .build();
        this.jsonMapper = jsonMapper;
    }

    public JsonNode buscar(String pergunta) {

        float[] embedding = embeddingModel.embed(pergunta);

        List<Float> vetor = new ArrayList<>(embedding.length);

        for (float valor : embedding) {
            vetor.add(valor);
        }

        Map<String, Object> body = Map.of(
                "query", vetor,
                "using", vectorName,
                "limit", 10,
                "with_payload", true
        );

        String resposta = restClient.post()
                .uri("/collections/{collection}/points/query", collection)
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            return jsonMapper.readTree(resposta);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erro ao interpretar resposta do Qdrant", e
            );
        }
    }
}