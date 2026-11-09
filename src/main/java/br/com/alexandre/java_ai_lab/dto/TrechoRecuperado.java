package br.com.alexandre.java_ai_lab.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

public record TrechoRecuperado(
        String id,
        String texto,
        Double score,
        Map<String, Object> metadata
) {
    public static TrechoRecuperado from(Document document) {
        return new TrechoRecuperado(
                document.getId(),
                document.getText(),
                document.getScore(),
                document.getMetadata()
        );
    }
}