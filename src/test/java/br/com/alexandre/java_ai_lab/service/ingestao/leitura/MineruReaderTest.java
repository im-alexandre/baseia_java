package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica a leitura do formato MinerU independentemente dos filtros e regras normativas. */
class MineruReaderTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MineruReader reader = new MineruReader();

    /** Confere a expansão da amostra real e a preservação dos artefatos anteriores ao corpo. */
    @Test
    void leTodasAsFolhasDaFixtureRealComOrigemPreservada() throws Exception {
        JsonNode documento;
        try (var entrada = getClass().getResourceAsStream("/ingestao/mineru/SGM-303-Rev7.json")) {
            assertNotNull(entrada);
            documento = mapper.readTree(entrada);
        }
        int blocosOriginais = 0;
        for (JsonNode pagina : documento.path("pages")) blocosOriginais += pagina.path("blocks").size();
        assertEquals(1952, blocosOriginais);

        List<BlocoMineru> blocos = reader.ler(documento);
        assertEquals(2576, blocos.size());
        BlocoMineru capa = blocos.getFirst();
        assertEquals("table", capa.tipoOrigem());
        assertEquals(1, capa.paginaFisica());
        assertNull(capa.paginaImpressa());
        assertEquals(0, capa.indiceOrigem());
        assertEquals(List.of(0.139, 0.098, 0.919, 0.913), capa.bbox());
        assertTrue(capa.texto().startsWith("<table>"));
        assertTrue(capa.texto().contains("MARINHA DO BRASIL"));
        assertTrue(blocos.stream().anyMatch(b -> b.tipoOrigem().equals("header")));
        assertTrue(blocos.stream().anyMatch(b -> b.tipoOrigem().equals("footer")));
        assertTrue(blocos.stream().anyMatch(b -> b.tipoOrigem().equals("page_number")));
        assertTrue(blocos.stream().anyMatch(b -> b.texto().equals("INTRODUÇÃO")));

        BlocoMineru capitulo = blocos.stream()
                .filter(b -> b.paginaFisica() == 14 && b.indiceOrigem() == 2).findFirst().orElseThrow();
        assertEquals("doc_title", capitulo.tipoOrigem());
        assertEquals("CAPÍTULO 1", capitulo.texto());
        assertEquals("1-1", capitulo.paginaImpressa());
        assertEquals(List.of(0.471, 0.068, 0.576, 0.084), capitulo.bbox());
    }

    /** Exercita páginas e listas fora de ordem sem emitir novamente o conteúdo dos contêineres. */
    @Test
    void ordenaPaginasEBlocosEAchataListasAninhadasSemDuplicar() {
        String listaInterna = lista(4, bloco("text", 6, "sexto"), bloco("text", 5, "quinto"));
        String lista = lista(2, listaInterna, bloco("text", 3, "terceiro"));
        JsonNode documento = documento(
                pagina(2, bloco("text", 0, "outra página")),
                pagina(0, bloco("text", 7, "sétimo"), lista, bloco("header", 0, "cabeçalho")));

        List<BlocoMineru> blocos = reader.ler(documento);
        assertEquals(List.of("cabeçalho", "terceiro", "quinto", "sexto", "sétimo", "outra página"),
                blocos.stream().map(BlocoMineru::texto).toList());
        assertEquals(List.of(0, 3, 5, 6, 7, 0), blocos.stream().map(BlocoMineru::indiceOrigem).toList());
        assertEquals(List.of(1, 1, 1, 1, 1, 3), blocos.stream().map(BlocoMineru::paginaFisica).toList());
        assertTrue(blocos.stream().noneMatch(b -> b.tipoOrigem().equals("list")));
    }

    /** Preserva concatenação inline, HTML, LaTeX e a flag original, inclusive em marcadores. */
    @Test
    void preservaTextoCompostoEContinuacaoSemAplicarFiltros() {
        String composto = """
                {"type":"text","index":0,"bbox":[0,0,1,1],"continues_prev":true,
                 "content":[{"content":"  antes "},{"content":[{"content":"depois"}]}," !  "]}
                """;
        String anexo = """
                {"type":"doc_title","index":1,"bbox":[0,0,1,1],
                 "content":"ANEXO A","continues_prev":true}
                """;
        List<BlocoMineru> blocos = reader.ler(documento(pagina(0, composto, anexo,
                bloco("table", 2, " <table><tr><td>A</td></tr></table> "),
                bloco("equation", 3, " \\frac{x}{y} "),
                bloco("text", 4, "INTENCIONALMENTE EM BRANCO"), bloco("text", 5, "  "))));

        assertEquals(List.of("antes depois !", "ANEXO A", "<table><tr><td>A</td></tr></table>",
                "\\frac{x}{y}", "INTENCIONALMENTE EM BRANCO", ""),
                blocos.stream().map(BlocoMineru::texto).toList());
        assertTrue(blocos.get(0).continuaAnterior());
        assertTrue(blocos.get(1).continuaAnterior());
        assertFalse(blocos.get(2).continuaAnterior());
    }

    /** Usa o primeiro rótulo na ordem de leitura e conserva rótulos fora do padrão conhecido. */
    @Test
    void identificaPaginaImpressaAntesDeQualquerFiltro() {
        List<BlocoMineru> blocos = reader.ler(documento(
                pagina(0, bloco("page_number", 3, "- 9-9 -"), bloco("text", 1, "corpo"),
                        bloco("page_number", 2, " - IV - ")),
                pagina(1, bloco("page_number", 0, " - AB-12 - ")),
                pagina(2, bloco("page_number", 0, "  sem numeração  ")),
                pagina(3, bloco("text", 0, "sem rótulo"))));

        assertEquals(List.of("IV", "IV", "IV", "AB-12", "sem numeração"),
                blocos.subList(0, 5).stream().map(BlocoMineru::paginaImpressa).toList());
        assertEquals("- IV -", blocos.get(1).texto());
        assertNull(blocos.getLast().paginaImpressa());
    }

    /** Protege tanto a lista retornada quanto a geometria contra alterações externas. */
    @Test
    void retornaListaEGeometriaImutaveis() {
        List<BlocoMineru> blocos = reader.ler(documento(pagina(0, bloco("text", 0, "texto"))));
        assertThrows(UnsupportedOperationException.class, () -> blocos.add(blocos.getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> blocos.getFirst().bbox().set(0, 0.5));
        List<Double> caixa = new ArrayList<>(List.of(0.0, 0.1, 0.9, 1.0));
        BlocoMineru bloco = new BlocoMineru("text", "texto", 1, null, 0, caixa, false);
        caixa.set(0, 0.5);
        assertEquals(0.0, bloco.bbox().getFirst());
    }

    /** Rejeita schema e coleções inválidos com as mensagens já usadas pelo parser. */
    @Test
    void rejeitaContratoOuArraysInvalidos() {
        assertErro("Esperado JSON docvortex.middle 2.0 com pages.", null);
        assertErro("Esperado JSON docvortex.middle 2.0 com pages.", mapper.readTree("{}"));
        assertErro("Esperado JSON docvortex.middle 2.0 com pages.", mapper.readTree("""
                {"schema":"docvortex.middle","schema_version":"1.0","pages":[]}
                """));
        assertErro("Esperado JSON docvortex.middle 2.0 com pages.", mapper.readTree("""
                {"schema":"docvortex.middle","schema_version":"2.0","pages":{}}
                """));
        assertErro("Esperada lista de blocos.", documento("{\"page_idx\":0,\"blocks\":{}}"));
        assertErro("Esperada lista de blocos.", documento(pagina(0,
                "{\"type\":\"list\",\"index\":0,\"content\":\"inválido\"}")));
    }

    /** Valida índices mesmo em páginas e listas unitárias, incluindo contêineres list. */
    @Test
    void rejeitaIndicesAusentesNegativosFracionariosOuForaDeInt() {
        for (String indice : List.of("null", "-1", "1.5", "\"0\"", "4294967296")) {
            assertErro("Índice inválido: page_idx", documento("{\"page_idx\":" + indice + ",\"blocks\":[]}"));
            assertErro("Índice inválido: index", documento(pagina(0,
                    "{\"type\":\"list\",\"index\":" + indice + ",\"content\":[]}")));
        }
        assertErro("Índice inválido: page_idx", documento("{\"blocks\":[]}"));
        assertErro("Índice inválido: index", documento(pagina(0,
                "{\"type\":\"text\",\"bbox\":[0,0,1,1],\"content\":\"texto\"}")));
    }

    /** Exige geometria normalizada e ordenada para qualquer folha, inclusive cabeçalhos. */
    @Test
    void rejeitaCaixasInvalidasEmTodasAsFolhas() {
        for (String caixa : List.of("null", "[]", "[0,0,1]")) {
            assertErroCaixa("bbox inválido.", caixa);
        }
        for (String caixa : List.of("[-0.1,0,1,1]", "[0,0,1.1,1]", "[0,\"0\",1,1]")) {
            assertErroCaixa("bbox deve usar coordenadas normalizadas entre 0 e 1.", caixa);
        }
        assertErroCaixa("bbox invertido.", "[0.8,0,0.2,1]");
        assertErroCaixa("bbox invertido.", "[0,0.8,1,0.2]");
    }

    /** Monta somente o envelope suportado; os testes controlam os fragmentos das páginas. */
    private JsonNode documento(String... paginas) {
        return mapper.readTree("{\"schema\":\"docvortex.middle\",\"schema_version\":\"2.0\",\"pages\":["
                + String.join(",", paginas) + "]}");
    }

    /** Preserva a ordem recebida para que a ordenação fique a cargo do Reader. */
    private String pagina(int indice, String... blocos) {
        return "{\"page_idx\":" + indice + ",\"blocks\":[" + String.join(",", blocos) + "]}";
    }

    /** Cria uma folha válida e usa o mapper para escapar seu texto sem alterá-lo. */
    private String bloco(String tipo, int indice, String texto) {
        return "{\"type\":\"" + tipo + "\",\"index\":" + indice + ",\"bbox\":[0,0,1,1],\"content\":"
                + mapper.writeValueAsString(texto) + "}";
    }

    /** Representa listas de blocos sem exigir bbox de um contêiner que não será emitido. */
    private String lista(int indice, String... blocos) {
        return "{\"type\":\"list\",\"index\":" + indice + ",\"content\":[" + String.join(",", blocos) + "]}";
    }

    /** Compara a mensagem para impedir mudanças acidentais no contrato de validação. */
    private void assertErro(String mensagem, JsonNode documento) {
        assertEquals(mensagem, assertThrows(IllegalArgumentException.class, () -> reader.ler(documento)).getMessage());
    }

    /** Injeta uma caixa inválida em um cabeçalho, que o Reader deve validar sem filtrar. */
    private void assertErroCaixa(String mensagem, String caixa) {
        assertErro(mensagem, documento(pagina(0, "{\"type\":\"header\",\"index\":0,\"bbox\":" + caixa
                + ",\"content\":\"cabeçalho\"}")));
    }
}
