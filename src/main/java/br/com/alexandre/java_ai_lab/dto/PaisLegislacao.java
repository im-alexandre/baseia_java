package br.com.alexandre.java_ai_lab.dto;

/**
 * Pais ativos na legislação HTML, sem autorreferência ou níveis intermediários inventados.
 * Incisos podem pertencer diretamente ao artigo; nesse caso, paragrafo permanece null.
 * Cada referência conserva o texto completo do elemento reconhecido, após limpar pontilhados.
 * artigoCitado identifica o artigo de outra lei sem substituir o artigo que introduz a citação.
 *
 * @param capitulo capítulo ativo, se reconhecido
 * @param secao seção ativa, se reconhecida
 * @param subsecao subseção ativa, se reconhecida
 * @param artigo artigo ativo da própria lei, se reconhecido
 * @param paragrafo parágrafo ativo, se reconhecido
 * @param inciso inciso ativo, se reconhecido
 * @param alinea alínea ativa, se reconhecida
 * @param item item ativo, se reconhecido
 * @param artigoCitado artigo de outra lei referido pelo conteúdo, se identificado
 */
public record PaisLegislacao(String capitulo, String secao, String subsecao, String artigo,
                             String paragrafo, String inciso, String alinea, String item, String artigoCitado) {
    /**
     * Constrói os pais da própria lei, sem referência a artigo de outra legislação.
     *
     * @param capitulo capítulo ativo, se reconhecido
     * @param secao seção ativa, se reconhecida
     * @param subsecao subseção ativa, se reconhecida
     * @param artigo artigo ativo da própria lei, se reconhecido
     * @param paragrafo parágrafo ativo, se reconhecido
     * @param inciso inciso ativo, se reconhecido
     * @param alinea alínea ativa, se reconhecida
     * @param item item ativo, se reconhecido
     */
    public PaisLegislacao(String capitulo, String secao, String subsecao, String artigo,
                           String paragrafo, String inciso, String alinea, String item) {
        this(capitulo, secao, subsecao, artigo, paragrafo, inciso, alinea, item, null);
    }

    /**
     * Representa conteúdo anterior ao corpo normativo, o fecho ou uma leitura sem pais conhecidos.
     *
     * @return referência com todos os níveis ausentes
     */
    public static PaisLegislacao vazios() {
        return new PaisLegislacao(null, null, null, null, null, null, null, null, null);
    }
}
