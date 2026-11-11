package br.com.alexandre.java_ai_lab.service.ingestao.agrupamento;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.OrigemLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgrupadorConteudoLegislacaoTest {
    private final AgrupadorConteudoLegislacao agrupador = new AgrupadorConteudoLegislacao();

    /** Junta textos por igualdade dos pais, preservando whitespace, índices e todas as âncoras. */
    @Test
    void agrupaTextosSemAlterarConteudoEConservaOrigensEmOrdem() {
        PaisLegislacao contexto = pais("Art. 1º", null);
        ElementoLegislacao primeiro = new ElementoLegislacao(TEXTO, null, "  Primeiro.  ",
                List.of(2, 3), "inicio", contexto);
        ElementoLegislacao segundo = new ElementoLegislacao(TEXTO, null, "\nSegundo.\n",
                List.of(8), null, pais("Art. 1º", null));
        ElementoLegislacao terceiro = new ElementoLegislacao(TEXTO, null, "Terceiro.",
                List.of(10, 11), "fim", contexto);
        List<ElementoLegislacao> entrada = List.of(primeiro, segundo, terceiro);

        List<ElementoLegislacao> resultado = agrupador.agrupar(entrada);

        assertEquals(1, resultado.size());
        ElementoLegislacao texto = resultado.getFirst();
        assertEquals(TEXTO, texto.tipo());
        assertNull(texto.marcador());
        assertEquals("  Primeiro.  \n\nSegundo.\n\nTerceiro.", texto.texto());
        assertEquals(List.of(2, 3, 8, 10, 11), texto.indicesOrigem());
        assertEquals("inicio", texto.ancora());
        assertEquals(contexto, texto.pais());
        assertFalse(texto.citacao());
        assertEquals(List.of(
                new OrigemLegislacao(TEXTO, null, List.of(2, 3), "inicio"),
                new OrigemLegislacao(TEXTO, null, List.of(8), null),
                new OrigemLegislacao(TEXTO, null, List.of(10, 11), "fim")), texto.origens());
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Converte itens consecutivos em LISTA sem absorver prosa e conserva os marcadores nas linhas. */
    @Test
    void agrupaItensInclusiveUnicoSemAbsorverTextoAdjacente() {
        PaisLegislacao contexto = pais("Art. 2º", null);
        ElementoLegislacao antes = elemento(TEXTO, null, "Introdução", 1, contexto, false);
        ElementoLegislacao primeiro = elemento(ITEM, "1.", "1. Primeiro", 2, contexto, false);
        ElementoLegislacao segundo = elemento(ITEM, "2.", "2. Segundo", 3, contexto, false);
        ElementoLegislacao entre = elemento(TEXTO, null, "Prosa independente", 4, contexto, false);
        ElementoLegislacao isolado = elemento(ITEM, "3.", "3. Isolado", 5, contexto, false);
        ElementoLegislacao depois = elemento(TEXTO, null, "Conclusão", 6, contexto, false);
        List<ElementoLegislacao> entrada = List.of(antes, primeiro, segundo, entre, isolado, depois);

        List<ElementoLegislacao> resultado = agrupador.agrupar(entrada);

        assertEquals(List.of(TEXTO, LISTA, TEXTO, LISTA, TEXTO),
                resultado.stream().map(ElementoLegislacao::tipo).toList());
        assertEquals(List.of("Introdução", "1. Primeiro\n2. Segundo", "Prosa independente",
                "3. Isolado", "Conclusão"), resultado.stream().map(ElementoLegislacao::texto).toList());
        ElementoLegislacao lista = resultado.get(1);
        assertNull(lista.marcador());
        assertEquals(List.of(2, 3), lista.indicesOrigem());
        assertEquals(primeiro.ancora(), lista.ancora());
        assertEquals(contexto, lista.pais());
        assertFalse(lista.citacao());
        assertEquals(List.of(primeiro.origens().getFirst(), segundo.origens().getFirst()), lista.origens());
        assertNull(resultado.get(3).marcador());
        assertEquals(isolado.origens(), resultado.get(3).origens());
        assertEquals(antes, resultado.getFirst());
        assertEquals(entre, resultado.get(2));
        assertEquals(depois, resultado.get(4));
        assertOrigensPreservadas(entrada, resultado);
    }

    /** Pais diferentes, inclusive artigo citado, e a fronteira corpo/citação encerram ambos os grupos. */
    @Test
    void separaTextosEItensPorContextoECitacaoSemReunirTrechosNaoConsecutivos() {
        for (Tipo tipo : List.of(TEXTO, ITEM)) {
            PaisLegislacao contexto = pais("Art. 3º", null);
            PaisLegislacao outroArtigo = pais("Art. 4º", null);
            PaisLegislacao artigoCitado = pais("Art. 3º", "Art. 9º da outra lei");
            List<ElementoLegislacao> entrada = List.of(
                    elemento(tipo, "1.", "Corpo antes", 10, contexto, false),
                    elemento(tipo, "2.", "Citação um", 11, contexto, true),
                    elemento(tipo, "3.", "Citação dois", 12, contexto, true),
                    elemento(tipo, "4.", "Corpo depois", 13, contexto, false),
                    elemento(tipo, "5.", "Outro pai", 14, outroArtigo, false),
                    elemento(tipo, "6.", "Artigo citado", 15, artigoCitado, true),
                    elemento(tipo, "7.", "Outro artigo citado", 16,
                            pais("Art. 3º", "Art. 10 da outra lei"), true));

            List<ElementoLegislacao> resultado = agrupador.agrupar(entrada);

            assertEquals(6, resultado.size());
            assertEquals(List.of("Corpo antes", "Citação um\nCitação dois", "Corpo depois",
                    "Outro pai", "Artigo citado", "Outro artigo citado"),
                    resultado.stream().map(ElementoLegislacao::texto).toList());
            assertEquals(List.of(false, true, false, false, true, true),
                    resultado.stream().map(ElementoLegislacao::citacao).toList());
            assertEquals(List.of(contexto, contexto, contexto, outroArtigo, artigoCitado,
                            pais("Art. 3º", "Art. 10 da outra lei")),
                    resultado.stream().map(ElementoLegislacao::pais).toList());
            assertEquals(tipo == ITEM ? LISTA : TEXTO, resultado.get(1).tipo());
            assertTrue(resultado.get(1).citacao());
            assertOrigensPreservadas(entrada, resultado);
        }
    }

    /** Dispositivos e estruturas repetidos permanecem distintos e interrompem texto e lista. */
    @Test
    void preservaDispositivosEEstruturasComoBarreirasMesmoComRotulosIguais() {
        PaisLegislacao contexto = pais("Art. 5º", null);
        for (Tipo barreira : List.of(ARTIGO, PARAGRAFO, INCISO, ALINEA, CAPITULO, SECAO,
                SUBSECAO, TABELA, EQUACAO, ANEXO, TITULO_ANEXO, CITACAO, FECHO)) {
            for (Tipo tipo : List.of(TEXTO, ITEM)) {
                ElementoLegislacao antes = elemento(tipo, "1.", "Antes", 20, contexto, false);
                ElementoLegislacao primeiraBarreira = elemento(barreira, "mesmo rótulo",
                        "Mesmo texto estrutural", 21, contexto, false);
                ElementoLegislacao meio = elemento(tipo, "2.", "Entre barreiras", 22, contexto, false);
                ElementoLegislacao segundaBarreira = elemento(barreira, "mesmo rótulo",
                        "Mesmo texto estrutural", 23, contexto, false);
                ElementoLegislacao depois = elemento(tipo, "3.", "Depois", 24, contexto, false);
                List<ElementoLegislacao> entrada = List.of(antes, primeiraBarreira, meio,
                        segundaBarreira, depois);

                List<ElementoLegislacao> resultado = agrupador.agrupar(entrada);

                assertEquals(5, resultado.size(), barreira + " entre " + tipo);
                assertSame(primeiraBarreira, resultado.get(1));
                assertSame(segundaBarreira, resultado.get(3));
                assertEquals(List.of("Antes", "Mesmo texto estrutural", "Entre barreiras",
                        "Mesmo texto estrutural", "Depois"),
                        resultado.stream().map(ElementoLegislacao::texto).toList());
                assertOrigensPreservadas(entrada, resultado);
            }
        }
    }

    /** A origem de uma unidade já composta é achatada integralmente, sem perder âncoras internas. */
    @Test
    void preservaTodasAsOrigensDeUnidadesJaCompostas() {
        PaisLegislacao contexto = pais("Art. 6º", null);
        OrigemLegislacao origemUm = new OrigemLegislacao(TEXTO, "original", List.of(30), "um");
        OrigemLegislacao origemDois = new OrigemLegislacao(TEXTO, null, List.of(31, 32), "dois");
        ElementoLegislacao composto = new ElementoLegislacao(TEXTO, "original", "Um\nDois",
                List.of(30, 31, 32), "um", contexto, true, List.of(origemUm, origemDois));
        ElementoLegislacao terceiro = elemento(TEXTO, null, "Três", 33, contexto, true);

        List<ElementoLegislacao> resultado = agrupador.agrupar(List.of(composto, terceiro));

        assertEquals(1, resultado.size());
        assertEquals("Um\nDois\nTrês", resultado.getFirst().texto());
        assertEquals("original", resultado.getFirst().marcador());
        assertEquals("um", resultado.getFirst().ancora());
        assertEquals(List.of(30, 31, 32, 33), resultado.getFirst().indicesOrigem());
        assertEquals(List.of(origemUm, origemDois, terceiro.origens().getFirst()),
                resultado.getFirst().origens());
        assertOrigensPreservadas(List.of(composto, terceiro), resultado);
    }

    /** Agrupar não altera a entrada nem compartilha listas mutáveis ou estado entre chamadas. */
    @Test
    void isolaEntradaResultadoEChamadasEProtegeListasInternas() {
        List<Integer> indices = new ArrayList<>(List.of(40, 41));
        OrigemLegislacao origem = new OrigemLegislacao(TEXTO, null, indices, "ancora");
        List<OrigemLegislacao> origens = new ArrayList<>(List.of(origem));
        ElementoLegislacao primeiro = new ElementoLegislacao(TEXTO, null, "Um", indices,
                "ancora", PaisLegislacao.vazios(), false, origens);
        ElementoLegislacao segundo = elemento(TEXTO, null, "Dois", 42, PaisLegislacao.vazios(), false);
        List<ElementoLegislacao> entrada = new ArrayList<>(List.of(primeiro, segundo));
        List<ElementoLegislacao> copiaEntrada = List.copyOf(entrada);

        List<ElementoLegislacao> resultado = agrupador.agrupar(entrada);

        assertEquals(copiaEntrada, entrada);
        assertEquals("Um", primeiro.texto());
        assertEquals(List.of(origem), primeiro.origens());
        indices.clear();
        origens.clear();
        entrada.clear();
        assertEquals(List.of(40, 41), primeiro.indicesOrigem());
        assertEquals(List.of(40, 41), origem.indicesOrigem());
        assertEquals("Um\nDois", resultado.getFirst().texto());
        assertEquals(List.of(40, 41, 42), resultado.getFirst().indicesOrigem());
        assertEquals(List.of(origem, segundo.origens().getFirst()), resultado.getFirst().origens());
        assertEquals(List.of(), agrupador.agrupar(List.of()));
        assertEquals(List.of(segundo), agrupador.agrupar(List.of(segundo)));
        assertEquals("Um\nDois", resultado.getFirst().texto());
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(segundo));
        assertThrows(UnsupportedOperationException.class, () -> resultado.getFirst().indicesOrigem().add(99));
        assertThrows(UnsupportedOperationException.class, () -> resultado.getFirst().origens().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.getFirst().origens().getFirst().indicesOrigem().clear());
    }

    /** Cria um bloco individual com âncora e índice identificáveis para rastrear a ordem original. */
    private ElementoLegislacao elemento(Tipo tipo, String marcador, String texto, int indice,
                                       PaisLegislacao pais, boolean citacao) {
        return new ElementoLegislacao(tipo, marcador, texto, List.of(indice), "bloco-" + indice,
                pais, citacao);
    }

    /** Mantém contexto completo estável e permite variar somente o artigo próprio ou citado. */
    private PaisLegislacao pais(String artigo, String artigoCitado) {
        return new PaisLegislacao("CAPÍTULO I", "Seção I", "Subseção I", artigo,
                "§ 1º", "I", "a)", "1.", artigoCitado);
    }

    /** Compara as origens achatadas para detectar perda, duplicação, reordenação ou alteração de âncoras. */
    private void assertOrigensPreservadas(List<ElementoLegislacao> entrada,
                                          List<ElementoLegislacao> resultado) {
        assertEquals(entrada.stream().flatMap(elemento -> elemento.origens().stream()).toList(),
                resultado.stream().flatMap(elemento -> elemento.origens().stream()).toList());
    }
}
