package br.com.alexandre.java_ai_lab.dto;

public record AnalisePatrimonial(
        String assunto,
        String classificacao,
        boolean relevante,
        String justificativa
) {}