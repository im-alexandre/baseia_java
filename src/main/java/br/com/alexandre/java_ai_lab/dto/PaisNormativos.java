package br.com.alexandre.java_ai_lab.dto;

/**
 * Contexto hierárquico imutável de um elemento, sem incluir o próprio elemento.
 * Níveis ausentes permanecem null; uma alínea pode pertencer diretamente a um artigo.
 *
 * @param capitulo marcador e título do capítulo ativo
 * @param artigo marcador e título do artigo ativo
 * @param inciso referência ao inciso ativo
 * @param alinea referência à alínea ativa
 * @param subalinea referência à subalínea ativa
 * @param anexo referência ao anexo ativo, incluindo o título quando disponível
 */
public record PaisNormativos(String capitulo, String artigo, String inciso,
                             String alinea, String subalinea, String anexo) {
    /** Representa um elemento raiz ou uma leitura ainda sem escopos conhecidos. */
    public static PaisNormativos vazios() {
        return new PaisNormativos(null, null, null, null, null, null);
    }
}
