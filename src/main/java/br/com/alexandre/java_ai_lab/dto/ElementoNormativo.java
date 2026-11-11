package br.com.alexandre.java_ai_lab.dto;

import java.util.List;

/**
 * DTO de saída do parser, compartilhado pelo serviço e pela resposta HTTP.
 * Cada registro representa um conteúdo extraído ou agrupado, não um nó de árvore nem uma entidade persistida.
 * Capítulos reúnem marcador e título; os dados de origem permanecem os do bloco marcador.
 *
 * @param tipo classificação atribuída pelas regras determinísticas
 * @param marcador rótulo reconhecido, como CAPÍTULO 1, 1.3.1 ou a); null em texto sem marcador ou LISTA;
 *                 números não incluem o ponto final opcional do rótulo
 * @param texto conteúdo do bloco, mantendo o marcador e removendo apenas espaços nas bordas;
 *              capítulos usam "CAPÍTULO N. TÍTULO", normalizando espaços e quebras de linha;
 *              parágrafos agrupados usam uma quebra entre blocos e listas preservam marcadores/recuos;
 *              tabelas/equações podem conter HTML/LaTeX
 * @param paginaFisica posição no documento em base 1; no agrupado, âncora do primeiro bloco
 * @param paginaImpressa primeiro rótulo page_number da página, como 1-1 ou A-1; null se ausente;
 *                       molduras de hífens são removidas quando o padrão é reconhecido
 * @param indiceOrigem campo index do bloco no JSON; não é um identificador global do documento
 * @param bbox caixa [x0, y0, x1, y1], normalizada; no agrupado, caixa somente do primeiro bloco
 * @param continuaAnterior cópia do campo continues_prev do MinerU, false quando ausente;
 *                         não indica que o parser reconstruiu uma associação entre blocos
 * @param pais cópia imutável do contexto hierárquico, sem incluir o próprio elemento
 * @param origens referências dos blocos reunidos, preservadas na ordem documental
 */
public record ElementoNormativo(
        Tipo tipo,
        String marcador,
        String texto,
        int paginaFisica,
        String paginaImpressa,
        int indiceOrigem,
        List<Double> bbox,
        boolean continuaAnterior,
        PaisNormativos pais,
        List<OrigemNormativa> origens
) {
    public ElementoNormativo {
        // O record protege também a lista interna contra alterações posteriores pelo chamador.
        bbox = List.copyOf(bbox);
        origens = List.copyOf(origens);
    }

    /** Constrói um elemento individual com uma única referência de origem. */
    public ElementoNormativo(Tipo tipo, String marcador, String texto, int paginaFisica,
                             String paginaImpressa, int indiceOrigem, List<Double> bbox,
                             boolean continuaAnterior, PaisNormativos pais) {
        this(tipo, marcador, texto, paginaFisica, paginaImpressa, indiceOrigem, bbox,
                continuaAnterior, pais, List.of(new OrigemNormativa(tipo, marcador,
                        paginaFisica, paginaImpressa, indiceOrigem, bbox, continuaAnterior)));
    }

    /** Acrescenta o contexto preservando integralmente o conteúdo e a origem deste elemento. */
    public ElementoNormativo comPais(PaisNormativos pais) {
        return new ElementoNormativo(tipo, marcador, texto, paginaFisica, paginaImpressa,
                indiceOrigem, bbox, continuaAnterior, pais, origens);
    }

    /** Vocabulário semântico da saída; os nomes são serializados diretamente na API JSON. */
    public enum Tipo {
        // TITULO_CAPITULO é um sinal interno da classificação, reunido ao CAPITULO na saída.
        // Anexos e seus títulos continuam sendo emitidos separadamente.
        CAPITULO, TITULO_CAPITULO, ARTIGO, INCISO, ALINEA, SUBALINEA,
        ITEM_LISTA, LISTA, TEXTO, ANEXO, TITULO_ANEXO, TABELA, EQUACAO,
        // A leitura HTML compartilha o vocabulário sem mudar os tipos emitidos pelo MinerU.
        SECAO, SUBSECAO, PARAGRAFO, ITEM, CITACAO, FECHO, IDENTIFICACAO_LEI, EMENTA
    }
}
