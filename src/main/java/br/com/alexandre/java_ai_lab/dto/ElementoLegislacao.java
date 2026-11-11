package br.com.alexandre.java_ai_lab.dto;

import java.util.List;

/**
 * Elemento da lei em ordem documental, com marcador original e pais opcionais.
 * indicesOrigem identifica posições, em base zero, dos blocos DOM selecionados pelo reader;
 * marcador e título reunidos conservam os dois índices. ancora é o id/name HTML, quando existe.
 * citacao distingue dispositivos de outra lei; origens conserva sua classificação após agrupar.
 * O HTML não fornece paginação ou geometria, portanto esses campos não integram este contrato.
 *
 * @param tipo classificação normativa do elemento
 * @param marcador marcador textual reconhecido, se houver
 * @param texto conteúdo completo do elemento
 * @param indicesOrigem posições dos blocos DOM de origem, em base zero
 * @param ancora identificador id/name associado, se houver
 * @param pais snapshot dos níveis normativos ativos
 * @param citacao indica se o elemento pertence a uma citação de outra lei
 * @param origens classificação e referências DOM dos blocos agrupados
 */
public record ElementoLegislacao(ElementoNormativo.Tipo tipo, String marcador, String texto,
                                 List<Integer> indicesOrigem, String ancora, PaisLegislacao pais,
                                 boolean citacao, List<OrigemLegislacao> origens) {
    /**
     * Impede que alterações externas modifiquem as referências de origem do elemento.
     *
     * @param tipo classificação normativa do elemento
     * @param marcador marcador textual reconhecido, se houver
     * @param texto conteúdo completo do elemento
     * @param indicesOrigem posições dos blocos DOM de origem, em base zero
     * @param ancora identificador id/name associado, se houver
     * @param pais snapshot dos níveis normativos ativos
     * @param citacao indica se o elemento pertence a uma citação de outra lei
     * @param origens classificação e referências DOM dos blocos agrupados
     * @throws NullPointerException se uma das listas de origem for nula
     */
    public ElementoLegislacao {
        indicesOrigem = List.copyOf(indicesOrigem);
        origens = List.copyOf(origens);
    }

    /**
     * Constrói um bloco individual da própria lei, mantendo o construtor usado pelo fluxo anterior.
     *
     * @param tipo classificação normativa do elemento
     * @param marcador marcador textual reconhecido, se houver
     * @param texto conteúdo completo do elemento
     * @param indicesOrigem posições dos blocos DOM de origem, em base zero
     * @param ancora identificador id/name associado, se houver
     * @param pais snapshot dos níveis normativos ativos
     */
    public ElementoLegislacao(ElementoNormativo.Tipo tipo, String marcador, String texto,
                               List<Integer> indicesOrigem, String ancora, PaisLegislacao pais) {
        this(tipo, marcador, texto, indicesOrigem, ancora, pais, false);
    }

    /**
     * Constrói uma origem individual, indicando se pertence a uma redação citada de outra lei.
     *
     * @param tipo classificação normativa do elemento
     * @param marcador marcador textual reconhecido, se houver
     * @param texto conteúdo completo do elemento
     * @param indicesOrigem posições dos blocos DOM de origem, em base zero
     * @param ancora identificador id/name associado, se houver
     * @param pais snapshot dos níveis normativos ativos
     * @param citacao indica se o elemento pertence a uma citação de outra lei
     */
    public ElementoLegislacao(ElementoNormativo.Tipo tipo, String marcador, String texto,
                               List<Integer> indicesOrigem, String ancora, PaisLegislacao pais, boolean citacao) {
        this(tipo, marcador, texto, indicesOrigem, ancora, pais, citacao,
                List.of(new OrigemLegislacao(tipo, marcador, indicesOrigem, ancora)));
    }

    /**
     * Anexa um snapshot de pais preservando classificação, texto e referências ao HTML.
     *
     * @param pais snapshot dos níveis normativos ativos
     * @return elemento com os novos pais e os demais dados preservados
     */
    public ElementoLegislacao comPais(PaisLegislacao pais) {
        return new ElementoLegislacao(tipo, marcador, texto, indicesOrigem, ancora, pais, citacao, origens);
    }
}
