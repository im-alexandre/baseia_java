package br.com.alexandre.java_ai_lab.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.*;

@Service
public class ContextAssemblerService {

    public String montar(JsonNode respostaQdrant) {

        JsonNode points = respostaQdrant
                .path("result")
                .path("points");

        if (!points.isArray() || points.isEmpty()) {
            return "";
        }

        StringBuilder contexto = new StringBuilder();
        Set<String> vistos = new HashSet<>();

        int referencia = 1;

        List<JsonNode> ordenados = new ArrayList<>();
        points.forEach(ordenados::add);

        Map<String, Integer> relevanciaDocumentos = new HashMap<>();

        for (int i = 0; i < ordenados.size(); i++) {
            String documentId = ordenados.get(i)
                    .path("payload")
                    .path("metadata")
                    .path("document_id")
                    .asText("");

            relevanciaDocumentos.putIfAbsent(documentId, i);
        }

        ordenados.sort(
                Comparator
                        .comparingInt((JsonNode p) -> {
                            String documentId = p.path("payload")
                                    .path("metadata")
                                    .path("document_id")
                                    .asText("");

                            return relevanciaDocumentos.get(documentId);
                        })
                        .thenComparing(p -> {
                            JsonNode path = p.path("payload")
                                    .path("metadata")
                                    .path("structure")
                                    .path("path");

                            return caminhoOrdenavel(path);
                        })
        );

        for (JsonNode point : ordenados) {
            JsonNode payload = point.path("payload");
            JsonNode metadata = payload.path("metadata");
            JsonNode structure = metadata.path("structure");

            String texto = payload.path("text").asText("");

            if (texto.isBlank()) {
                continue;
            }

            // Remove apenas duplicações exatas.
            if (!vistos.add(texto.strip())) {
                continue;
            }

            String norma = metadata.path("normative")
                    .path("code")
                    .asText("Documento não identificado");

            int pagina = metadata.path("page_start").asInt(-1);

            double score = point.path("score").asDouble(0);

            String id = point.path("id").asText();

            // Caminho estrutural, quando disponível.
            StringBuilder caminho = new StringBuilder();

            JsonNode path = structure.path("path");

            if (path.isArray()) {
                for (JsonNode item : path) {
                    if (!caminho.isEmpty()) {
                        caminho.append(" > ");
                    }
                    caminho.append(item.asText());
                }
            }

            // Remove somente o prefixo estrutural exato.
            String prefixo = caminho + ": ";

            if (!caminho.isEmpty() && texto.startsWith(prefixo)) {
                texto = texto.substring(prefixo.length()).strip();
            }

            contexto.append("\n[FONTE ")
                    .append(referencia++)
                    .append("]\n");

            contexto.append("Norma: ").append(norma).append("\n");

            if (pagina >= 0) {
                contexto.append("Página: ").append(pagina).append("\n");
            }

            if (!caminho.isEmpty()) {
                contexto.append("Seção: ").append(caminho).append("\n");
            }

            contexto.append("ID: ").append(id).append("\n");
            contexto.append("Score: ").append(score).append("\n");
            contexto.append("Conteúdo:\n").append(texto).append("\n");
        }

        return contexto.toString();
    }

    private String caminhoOrdenavel(JsonNode path) {

        if (!path.isArray()) {
            return "";
        }

        StringBuilder resultado = new StringBuilder();

        for (JsonNode item : path) {
            resultado.append(item.asText())
                    .append("\u0000");
        }

        return resultado.toString();
    }
}