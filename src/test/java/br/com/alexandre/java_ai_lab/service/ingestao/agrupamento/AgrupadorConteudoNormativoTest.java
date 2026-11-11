package br.com.alexandre.java_ai_lab.service.ingestao.agrupamento;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.OrigemNormativa;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgrupadorConteudoNormativoTest {
    private final AgrupadorConteudoNormativo agrupador = new AgrupadorConteudoNormativo();

    /** Junta somente textos consecutivos com todo o contexto igual, mantendo bytes textuais e origens. */
    @Test
    void agrupaTextosComPaisIguaisAtravessandoPaginasSemAlterarConteudo() {
        PaisNormativos paisA = pais("Capítulo", "Artigo", "Inciso", "Alínea", null, null);
        PaisNormativos paisB = pais("Capítulo", "Artigo", "Inciso", "Alínea", "Subalínea", null);
        ElementoNormativo primeiro = elemento(TEXTO, null, "  Parágrafo 1.  ", 2, paisA, 11);
        ElementoNormativo segundo = elemento(TEXTO, null, "\nParágrafo 2.\n", 8, paisA, 12);
        ElementoNormativo terceiro = elemento(TEXTO, null, "Outro contexto", 9, paisB, 13);
        ElementoNormativo quarto = elemento(TEXTO, null, "Continuação", 9, paisB, 14);
        List<ElementoNormativo> entrada = List.of(primeiro, segundo, terceiro, quarto);

        List<ElementoNormativo> resultado = agrupador.agrupar(entrada);

        assertEquals(2, resultado.size());
        assertEquals("  Parágrafo 1.  \n\nParágrafo 2.\n", resultado.get(0).texto());
        assertEquals(paisA, resultado.get(0).pais());
        assertEquals(primeiro.paginaFisica(), resultado.get(0).paginaFisica());
        assertEquals(primeiro.paginaImpressa(), resultado.get(0).paginaImpressa());
        assertEquals(primeiro.indiceOrigem(), resultado.get(0).indiceOrigem());
        assertEquals(primeiro.bbox(), resultado.get(0).bbox());
        assertEquals(primeiro.continuaAnterior(), resultado.get(0).continuaAnterior());
        assertEquals(List.of("Outro contexto\nContinuação"), resultado.stream().skip(1)
                .map(ElementoNormativo::texto).toList());
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Trata tabelas, equações e anexos como barreiras, preservando cada elemento original. */
    @Test
    void preservaConteudoEstruturadoEAnexosEUsaTiposComoBarreira() {
        PaisNormativos contexto = pais("Capítulo", null, null, null, null, null);
        List<ElementoNormativo> entrada = List.of(
                elemento(TEXTO, null, "antes", 1, contexto, 1),
                elemento(TABELA, null, "<table>dados</table>", 1, contexto, 2),
                elemento(TEXTO, null, "depois da tabela", 1, contexto, 3),
                elemento(EQUACAO, null, "\\(x = y\\)", 2, contexto, 4),
                elemento(ANEXO, "ANEXO A", "ANEXO A", 3, PaisNormativos.vazios(), 5),
                elemento(TITULO_ANEXO, null, "Título do anexo", 3,
                        pais(null, null, null, null, null, "ANEXO A"), 6)
        );

        List<ElementoNormativo> resultado = agrupador.agrupar(entrada);

        assertEquals(entrada, resultado);
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Mantém alíneas, subalíneas e seus textos em unidades separadas com pais próprios. */
    @Test
    void preservaAlineasSubalineasETextosComPaisOriginais() {
        PaisNormativos base = pais("Capítulo", "Artigo", "Inciso", null, null, null);
        String alineaA = "a) Grupo";
        PaisNormativos contextoA = pais("Capítulo", "Artigo", "Inciso", alineaA, null, null);
        String subalineaI = "I) Primeiro";
        PaisNormativos contextoI = pais("Capítulo", "Artigo", "Inciso", alineaA,
                subalineaI, null);
        List<ElementoNormativo> entrada = List.of(
                elemento(ALINEA, "a)", alineaA, 1, base, 20),
                elemento(TEXTO, null, "Introdução", 1, contextoA, 21),
                elemento(SUBALINEA, "I)", subalineaI, 2, contextoA, 22),
                elemento(TEXTO, null, "Detalhe", 2, contextoI, 23)
        );

        List<ElementoNormativo> resultado = agrupador.agrupar(entrada);

        assertEquals(entrada, resultado);
        assertEquals(List.of(ALINEA, TEXTO, SUBALINEA, TEXTO),
                resultado.stream().map(ElementoNormativo::tipo).toList());
        assertEquals(List.of(base, contextoA, contextoA, contextoI),
                resultado.stream().map(ElementoNormativo::pais).toList());
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Agrupa somente itens consecutivos de mesmo contexto, inclusive entre páginas. */
    @Test
    void agrupaItensConsecutivosComMesmoPaisSemRecuoEEncerraAntesDeTexto() {
        PaisNormativos contexto = pais("Capítulo", "Artigo", "Inciso", "a) Grupo", null, null);
        ElementoNormativo primeiro = elemento(ITEM_LISTA, "-1", "-1 Primeiro", 4, contexto, 30);
        ElementoNormativo segundo = elemento(ITEM_LISTA, "-2", "-2 Segundo", 5, contexto, 31);
        ElementoNormativo terceiro = elemento(ITEM_LISTA, "-3", "-3 Terceiro", 5, contexto, 32);
        ElementoNormativo textoDepois = elemento(TEXTO, null, "Continuação independente", 5,
                contexto, 33);
        List<ElementoNormativo> entrada = List.of(
                primeiro, segundo, terceiro, textoDepois
        );

        List<ElementoNormativo> resultado = agrupador.agrupar(entrada);

        assertEquals(2, resultado.size());
        ElementoNormativo lista = resultado.getFirst();
        assertEquals(LISTA, lista.tipo());
        assertEquals(null, lista.marcador());
        assertEquals("-1 Primeiro\n-2 Segundo\n-3 Terceiro", lista.texto());
        assertEquals(contexto, lista.pais());
        assertRootMetadata(primeiro, lista);
        assertEquals(textoDepois, resultado.get(1));
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Mantém itens isolados quando muda o pai ou quando um tipo intermediário cria barreira. */
    @Test
    void separaItensPorMudancaDePaisEBarreiras() {
        PaisNormativos escopo = pais("Capítulo repetido", "Artigo repetido", null, null, null, null);
        PaisNormativos outroEscopo = pais("Capítulo repetido", "Outro artigo", null, null, null, null);
        ElementoNormativo primeiroItem = elemento(ITEM_LISTA, "-1", "-1 Primeiro", 1, escopo, 40);
        ElementoNormativo itemOutroPai = elemento(ITEM_LISTA, "-2", "-2 Outro pai", 1,
                outroEscopo, 41);
        ElementoNormativo artigo = elemento(ARTIGO, "1.2", "Art. 1.2. Barreira", 2,
                pais("Capítulo repetido", null, null, null, null, null), 42);
        ElementoNormativo itemDepoisBarreira = elemento(ITEM_LISTA, "-3", "-3 Depois", 3,
                escopo, 43);
        ElementoNormativo textoForaDeLista = elemento(TEXTO, null, "Texto fora", 4,
                escopo, 44);
        List<ElementoNormativo> entrada = List.of(primeiroItem, itemOutroPai, artigo,
                itemDepoisBarreira, textoForaDeLista);

        List<ElementoNormativo> resultado = agrupador.agrupar(entrada);

        assertEquals(List.of(LISTA, LISTA, ARTIGO, LISTA, TEXTO),
                resultado.stream().map(ElementoNormativo::tipo).toList());
        assertEquals(List.of("-1 Primeiro", "-2 Outro pai", "Art. 1.2. Barreira", "-3 Depois", "Texto fora"),
                resultado.stream().map(ElementoNormativo::texto).toList());
        assertEquals(List.of(escopo, outroEscopo), resultado.subList(0, 2).stream()
                .map(ElementoNormativo::pais).toList());
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Confirma que o resultado e sua lista de origens não podem ser alterados pelo chamador. */
    @Test
    void devolveListaImutavel() {
        List<ElementoNormativo> resultado = agrupador.agrupar(List.of(
                elemento(TEXTO, null, "um", 1, PaisNormativos.vazios(), 50)));

        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(elemento(TEXTO, null, "dois", 1, PaisNormativos.vazios(), 51)));
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.getFirst().origens().add(origem(TEXTO, null, 2, "2", 52)));
    }

    /** Constrói um elemento com metadados reconhecíveis para rastrear a origem individual. */
    private ElementoNormativo elemento(Tipo tipo, String marcador, String texto, int pagina,
                                      PaisNormativos pais, int indice) {
        return new ElementoNormativo(tipo, marcador, texto, pagina, "página-" + pagina,
                indice, List.of(0.1, 0.2, 0.3, 0.4), indice % 2 == 0, pais);
    }

    /** Constrói o contexto hierárquico completo usado para decidir agrupamentos. */
    private PaisNormativos pais(String capitulo, String artigo, String inciso, String alinea,
                                String subalinea, String anexo) {
        return new PaisNormativos(capitulo, artigo, inciso, alinea, subalinea, anexo);
    }

    /** Compara todas as origens achatadas, incluindo marcadores e tipos originais dos candidatos. */
    private void assertOrigensPreservadas(List<ElementoNormativo> entrada,
                                          List<ElementoNormativo> resultado) {
        List<OrigemNormativa> origensEntrada = entrada.stream()
                .flatMap(elemento -> elemento.origens().stream()).toList();
        List<OrigemNormativa> origensResultado = resultado.stream()
                .flatMap(elemento -> elemento.origens().stream()).toList();
        assertEquals(origensEntrada.size(), origensResultado.size());
        assertEquals(origensEntrada, origensResultado);
    }

    /** Verifica que o item-raiz agregado conserva todos os metadados do primeiro elemento. */
    private void assertRootMetadata(ElementoNormativo raiz, ElementoNormativo agregado) {
        assertEquals(raiz.paginaFisica(), agregado.paginaFisica());
        assertEquals(raiz.paginaImpressa(), agregado.paginaImpressa());
        assertEquals(raiz.indiceOrigem(), agregado.indiceOrigem());
        assertEquals(raiz.bbox(), agregado.bbox());
        assertEquals(raiz.continuaAnterior(), agregado.continuaAnterior());
    }

    /** Constrói a origem esperada com o tipo e marcador originais, antes da conversão em LISTA. */
    private OrigemNormativa origem(Tipo tipo, String marcador, int paginaFisica,
                                  String paginaImpressa, int indiceOrigem) {
        return new OrigemNormativa(tipo, marcador, paginaFisica, paginaImpressa,
                indiceOrigem, List.of(0.1, 0.2, 0.3, 0.4), indiceOrigem % 2 == 0);
    }
}
