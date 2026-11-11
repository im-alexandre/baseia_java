package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Chunk;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Preparacao;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Escreve vetores densos nomeados usando a API REST Qdrant já disponível no Spring Boot. */
@Service
public class IndexadorQdrant {
    private final ObjectProvider<EmbeddingModel> modelos;
    private final QdrantPayloadBuilder payloads;
    private final RestClient cliente;
    private final String colecao;
    private final String vetorNome;

    /** Injeta opcionalmente EmbeddingModel; somente a rota de indexação depende dele. */
    public IndexadorQdrant(ObjectProvider<EmbeddingModel> modelos, QdrantPayloadBuilder payloads,
                           @Value("${baseia.qdrant.url:http://localhost:6333}") String url,
                           @Value("${baseia.qdrant.collection:normas_v1}") String colecao,
                           @Value("${baseia.qdrant.vector-name:dense}") String vetorNome) {
        this.modelos = modelos;
        this.payloads = payloads;
        this.cliente = RestClient.builder().baseUrl(url).build();
        this.colecao = colecao;
        this.vetorNome = vetorNome;
    }

    /** Indexa a edição somente após revisão dos alertas; reindexação não remove pontos obsoletos. */
    public int indexar(Preparacao preparacao, boolean confirmarAlertas) {
        if (preparacao.alertas().stream().anyMatch(alerta -> "GLIFO_NAO_MAPEADO".equals(alerta.codigo()))) {
            throw new IllegalArgumentException("Glifos não mapeados impedem a indexação; refaça a extração.");
        }
        if (!confirmarAlertas && !preparacao.alertas().isEmpty()) {
            throw new IllegalArgumentException("Há alertas para revisão antes da indexação.");
        }
        EmbeddingModel modelo = modelos.getIfAvailable();
        if (modelo == null) throw new IllegalStateException("Configure um EmbeddingModel para indexar no Qdrant.");
        if (preparacao.chunks().isEmpty()) return 0;

        boolean colecaoVerificada = false;
        int dimensoes = -1;
        List<Map<String, Object>> lote = new ArrayList<>();
        for (Chunk chunk : preparacao.chunks()) {
            float[] vetor = modelo.embed(chunk.textoEmbedding());
            if (vetor == null || vetor.length == 0) throw new IllegalStateException("Embedding vazio.");
            if (!colecaoVerificada) {
                dimensoes = vetor.length;
                garantirColecao(dimensoes);
                colecaoVerificada = true;
            } else if (vetor.length != dimensoes) {
                throw new IllegalStateException("O modelo retornou vetores com dimensões distintas.");
            }
            Map<String, Object> ponto = new LinkedHashMap<>();
            ponto.put("id", chunk.id());
            ponto.put("vector", Map.of(vetorNome, vetor));
            ponto.put("payload", payloads.montar(preparacao.documento(), chunk));
            lote.add(ponto);
            if (lote.size() == 32) {
                enviar(lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) enviar(lote);
        return preparacao.chunks().size();
    }

    /** Cria uma coleção com o nome do vetor caso ainda não exista, mantendo coleções existentes. */
    private void garantirColecao(int dimensoes) {
        try {
            cliente.get().uri("/collections/{colecao}", colecao).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound inexistente) {
            Map<String, Object> definicao = Map.of("vectors",
                    Map.of(vetorNome, Map.of("size", dimensoes, "distance", "Cosine")));
            cliente.put().uri("/collections/{colecao}", colecao)
                    .body(definicao).retrieve().toBodilessEntity();
        }
    }

    /** Envia um lote com espera pela confirmação do servidor Qdrant. */
    private void enviar(List<Map<String, Object>> lote) {
        cliente.put().uri("/collections/{colecao}/points?wait=true", colecao)
                .body(Map.of("points", List.copyOf(lote)))
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Qdrant rejeitou o upsert: HTTP " + res.getStatusCode());
                }).toBodilessEntity();
    }
}
