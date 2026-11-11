package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;

class ParserNormasServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MineruReader reader = new MineruReader();
    private final ParserNormasService parser = new ParserNormasService();

    /** Confere que o agrupamento conserva, em ordem, cada origem do golden imutável. */
    @Test
    void preservaResultadoCompletoDaAmostraValidada() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/ingestao/mineru/SGM-303-Rev7.json");
             var esperado = getClass().getResourceAsStream(
                     "/ingestao/mineru/SGM-303-Rev7-elementos-esperados.json")) {
            assertNotNull(entrada);
            assertNotNull(esperado);
            var elementos = parser.parse(reader.ler(mapper.readTree(entrada)));
            JsonNode golden = mapper.readTree(esperado);
            assertaOrigensContraGolden(elementos, golden);
            assertTrue(elementos.stream().noneMatch(e -> Set.of(CAPITULO, ARTIGO, INCISO).contains(e.tipo())));
        }
    }

    /** Confere a leitura dos metadados da fixture e os filtros do corpo normativo. */
    @Test
    void interpretaFixtureRealSemPreambuloEArtefatosDePagina() throws Exception {
        JsonNode json;
        try (InputStream in = getClass().getResourceAsStream("/ingestao/mineru/SGM-303-Rev7.json")) {
            assertNotNull(in);
            json = mapper.readTree(in);
        }
        List<ElementoNormativo> elementos = parser.parse(reader.ler(json));

        assertTrue(elementos.stream().noneMatch(e -> Set.of(CAPITULO, ARTIGO, INCISO).contains(e.tipo())));
        assertEquals(8, elementos.stream().map(e -> e.pais().capitulo()).filter(java.util.Objects::nonNull).distinct().count());
        assertTrue(elementos.stream().noneMatch(e -> e.texto().contains("ÍNDICE")
                || e.texto().equals("INTRODUÇÃO") || e.texto().contains("OSTENSIVO")
                || e.texto().contains("INTENCIONALMENTE EM BRANCO")));
        assertTrue(elementos.stream().noneMatch(e -> e.tipo() == ANEXO || e.tipo() == TITULO_ANEXO));

        String capitulo1 = "CAPÍTULO 1. ESTRUTURA BÁSICA DA GESTÃO PATRIMONIAL";
        ElementoNormativo primeiraAlinea = elementos.stream().filter(e -> e.origens().stream()
                        .anyMatch(o -> o.paginaFisica() == 14 && o.indiceOrigem() == 8))
                .findFirst().orElseThrow();
        assertEquals(ALINEA, primeiraAlinea.tipo());
        assertEquals(1, primeiraAlinea.origens().size());
        assertTrue(primeiraAlinea.texto().contains("a)"));
        assertEquals(6, elementos.stream().filter(e -> e.tipo() == ALINEA && e.origens().stream()
                .anyMatch(o -> o.paginaFisica() == 14 && o.indiceOrigem() >= 8
                        && o.indiceOrigem() <= 13)).count());

        ElementoNormativo textoContinuidade = elementos.stream().filter(e -> e.origens().stream()
                .anyMatch(o -> o.paginaFisica() == 15 && o.indiceOrigem() == 2)).findFirst().orElseThrow();
        assertEquals(TEXTO, textoContinuidade.tipo());
        assertEquals(capitulo1, textoContinuidade.pais().capitulo());
        assertEquals("1.3. ESTRUTURA ORGANIZACIONAL DA GESTÃO PATRIMONIAL", textoContinuidade.pais().artigo());
        assertEquals("1.3.1. Para efeito de recebimento, distribuição, consumo e respectivos controles contábeis, fiscais e de tomada de contas dos bens patrimoniais, as OM da MB são classificadas segundo a sua natureza patrimonial, como:",
                textoContinuidade.pais().inciso());
        assertEquals("a) Organização Militar Consumidora (OMC)", textoContinuidade.pais().alinea());
        assertEquals("I) Organização Militar Consumidora Integrada (OMCI)",
                textoContinuidade.pais().subalinea());
        assertTrue(textoContinuidade.texto().contains("É a denominação utilizada para as OMC"));
        var origemContinuidade = textoContinuidade.origens().stream().filter(o -> o.paginaFisica() == 15
                && o.indiceOrigem() == 2).findFirst().orElseThrow();
        assertEquals("TEXTO", origemContinuidade.tipo().name());
        assertNull(origemContinuidade.marcador());
        assertEquals(15, origemContinuidade.paginaFisica());
        assertEquals("1-2", origemContinuidade.paginaImpressa());
        assertEquals(List.of(0.09, 0.068, 0.862, 0.165), origemContinuidade.bbox());
        assertFalse(origemContinuidade.continuaAnterior());
    }

    /** Une o título ao marcador e conserva no texto seguinte os metadados de sua própria origem. */
    @Test
    void uneTituloAoCapituloComEspacosNormalizadosEMetadadosDoMarcador() {
        JsonNode json = documento(
                bloco("page_number", 0, "- IV -", false),
                bloco("doc_title", 1, "CAPÍTULO 2.", true, true),
                bloco("doc_title", 2, " GESTÃO\n PATRIMONIAL ", false),
                bloco("text", 3, "Conteúdo do capítulo", false));

        ElementoNormativo conteudo = parser.parse(reader.ler(json)).getFirst();

        assertEquals(TEXTO, conteudo.tipo());
        assertEquals("Conteúdo do capítulo", conteudo.texto());
        assertEquals("CAPÍTULO 2. GESTÃO PATRIMONIAL", conteudo.pais().capitulo());
        assertEquals(3, conteudo.indiceOrigem());
        assertEquals(List.of(0.14, 0.1, 0.8, 0.12), conteudo.bbox());
        assertEquals(1, parser.parse(reader.ler(json)).size());
    }

    /** Omite capítulo sem conteúdo retornável, conforme o filtro explícito da resposta. */
    @Test
    void omiteCapituloSemTituloDaResposta() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 3", true));

        List<ElementoNormativo> elementos = parser.parse(reader.ler(json));

        assertTrue(elementos.isEmpty());
    }

    /** Filtra anexos sem permissão e anexos cujo conteúdo indica modelo ou formulário. */
    @Test
    void filtraAnexoPorAllowListEConteudo() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("doc_title", 2, "ANEXO A", true), bloco("text", 3, "TABELA DE DEFINIÇÕES", false),
                bloco("doc_title", 4, "ANEXO B", true), bloco("text", 5, "MODELO DE FORMULÁRIO", false));
        assertTrue(parser.parse(reader.ler(json)).stream().noneMatch(e -> e.tipo() == ANEXO));
        List<ElementoNormativo> permitidos = parser.parse(reader.ler(json), Set.of("A", "B"));
        assertTrue(permitidos.stream().anyMatch(e -> e.tipo() == ANEXO && e.marcador().equals("ANEXO A")));
        assertTrue(permitidos.stream().anyMatch(e -> e.tipo() == TITULO_ANEXO && e.texto().contains("DEFINIÇÕES")));
        assertFalse(permitidos.stream().anyMatch(e -> e.tipo() == ANEXO && e.marcador().equals("ANEXO B")));
        assertTrue(parser.parse(reader.ler(json)).stream().noneMatch(e -> e.tipo() == ANEXO),
                "permissões de uma leitura não podem vazar para a próxima chamada");
    }

    /** Preserva tabela e equação sem devolver o inciso que as antecede. */
    @Test
    void preservaTabelaEquacaoESeparadorHifenEmNumero() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "TÍTULO", false), bloco("paragraph_title", 2, "1.3.1 - Militares", false),
                bloco("table", 3, "<table><tr><td>Conta</td></tr></table>", false),
                bloco("equation", 4, "x = y", false));
        List<ElementoNormativo> elementos = parser.parse(reader.ler(json));
        assertTrue(elementos.stream().noneMatch(e -> e.tipo() == INCISO));
        assertTrue(elementos.stream().anyMatch(e -> e.tipo() == TABELA && e.texto().equals("<table><tr><td>Conta</td></tr></table>")));
        assertTrue(elementos.stream().anyMatch(e -> e.tipo() == EQUACAO && e.texto().equals("x = y")));
    }

    /** Preserva o contexto completo do inciso nos pais do texto sem devolver níveis estruturais. */
    @Test
    void preservaPaisDoTextoMesmoComArtigoEIncisoOmitidos() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("paragraph_title", 2, "1.2. Artigo de teste", false),
                bloco("paragraph_title", 3, "1.2.1. Inciso completo", false),
                bloco("text", 4, "Conteúdo", false));

        List<ElementoNormativo> elementos = parser.parse(reader.ler(json));
        ElementoNormativo texto = elementos.stream().filter(e -> e.tipo() == TEXTO).findFirst().orElseThrow();

        assertEquals(1, elementos.size());
        assertEquals("1.2. Artigo de teste", texto.pais().artigo());
        assertEquals("1.2.1. Inciso completo", texto.pais().inciso());
        assertNull(texto.pais().alinea());
        assertNull(texto.pais().subalinea());
    }

    /** Mantém texto direto em cada nível e associa somente os pais ativos até aquele ponto. */
    @Test
    void preservaTextoDiretoDeCapituloArtigoEInciso() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("text", 2, "Texto direto do capítulo", false),
                bloco("paragraph_title", 3, "1.2. Artigo de teste", false),
                bloco("text", 4, "Texto direto do artigo", false),
                bloco("paragraph_title", 5, "1.2.1. Inciso completo", false),
                bloco("text", 6, "Texto direto do inciso", false));

        List<ElementoNormativo> textos = parser.parse(reader.ler(json));

        assertEquals(3, textos.size());
        assertEquals(List.of("Texto direto do capítulo", "Texto direto do artigo", "Texto direto do inciso"),
                textos.stream().map(ElementoNormativo::texto).toList());
        assertEquals("CAPÍTULO 1. GESTÃO", textos.get(0).pais().capitulo());
        assertNull(textos.get(0).pais().artigo());
        assertNull(textos.get(0).pais().inciso());
        assertEquals("CAPÍTULO 1. GESTÃO", textos.get(1).pais().capitulo());
        assertEquals("1.2. Artigo de teste", textos.get(1).pais().artigo());
        assertNull(textos.get(1).pais().inciso());
        assertEquals("CAPÍTULO 1. GESTÃO", textos.get(2).pais().capitulo());
        assertEquals("1.2. Artigo de teste", textos.get(2).pais().artigo());
        assertEquals("1.2.1. Inciso completo", textos.get(2).pais().inciso());
    }

    /** Reúne textos consecutivos sob o mesmo contexto com uma quebra entre os parágrafos. */
    @Test
    void agrupaParagrafosConsecutivosComMesmoPais() {
        JsonNode json = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("text", 2, "Primeiro.", false),
                bloco("text", 3, "Segundo.", false));

        List<ElementoNormativo> elementos = parser.parse(reader.ler(json));

        assertEquals(1, elementos.size());
        assertEquals(TEXTO, elementos.getFirst().tipo());
        assertEquals("Primeiro.\nSegundo.", elementos.getFirst().texto());
        assertEquals(2, elementos.getFirst().origens().size());
        assertEquals("CAPÍTULO 1. GESTÃO", elementos.getFirst().pais().capitulo());
    }

    /** Monta um documento MinerU mínimo a partir de blocos já serializados. */
    private JsonNode documento(String... blocos) {
        return mapper.readTree("{\"schema\":\"docvortex.middle\",\"schema_version\":\"2.0\",\"pages\":[{\"page_idx\":0,\"blocks\":["
                + String.join(",", blocos) + "]}]}");
    }

    /** Confere origem, metadados e texto de cada entrada antiga sem calcular grupos esperados. */
    private void assertaOrigensContraGolden(List<ElementoNormativo> elementos, JsonNode golden) throws Exception {
        JsonNode grupos = mapper.readTree(mapper.writeValueAsString(elementos));
        JsonNode esperados = somenteOrigensRetornaveis(golden);
        var origensObtidas = mapper.createArrayNode();
        grupos.forEach(grupo -> grupo.path("origens").forEach(origem -> origensObtidas.add(projecaoOrigem(origem))));
        var origensEsperadas = mapper.createArrayNode();
        esperados.forEach(elemento -> origensEsperadas.add(projecaoOrigem(elemento)));
        assertEquals(1404, origensObtidas.size(), "quantidade de origens preservadas");
        assertEquals(origensEsperadas, origensObtidas, "origens e metadados na ordem documental");
        esperados.forEach(esperado -> {
            JsonNode origemEsperada = projecaoOrigem(esperado);
            String textoEsperado = normalizaWhitespace(esperado.path("texto").asText());
            assertTrue(elementos.stream().anyMatch(grupo -> grupo.origens().stream()
                    .anyMatch(origem -> projecaoOrigem(mapper.valueToTree(origem)).equals(origemEsperada))
                    && normalizaWhitespace(grupo.texto()).contains(textoEsperado)),
                    "texto associado à origem " + esperado.path("paginaFisica").asInt() + "/" + esperado.path("indiceOrigem").asInt());
        });
    }

    /** Mantém somente os blocos-fonte que aparecem na resposta, usando o golden fixo como referência. */
    private JsonNode somenteOrigensRetornaveis(JsonNode elementos) {
        var resultado = mapper.createArrayNode();
        elementos.forEach(elemento -> {
            if (!Set.of("CAPITULO", "ARTIGO", "INCISO").contains(elemento.path("tipo").asText())) {
                resultado.add(elemento);
            }
        });
        return resultado;
    }

    /** Projeta exclusivamente os sete campos persistidos em cada origem normativa. */
    private JsonNode projecaoOrigem(JsonNode origem) {
        var projetada = mapper.createObjectNode();
        for (String campo : List.of("tipo", "marcador", "paginaFisica", "paginaImpressa", "indiceOrigem", "bbox", "continuaAnterior")) {
            if (origem.has(campo)) projetada.set(campo, origem.get(campo).deepCopy());
        }
        return projetada;
    }

    /** Normaliza somente sequências de whitespace para comparar textos dentro de linhas indentadas. */
    private String normalizaWhitespace(String texto) {
        return texto.replaceAll("\\s+", " ").trim();
    }

    /** Monta um bloco MinerU e serializa seu conteúdo de texto com escape JSON. */
    private String bloco(String type, int index, String text, boolean chapterMarker) {
        return bloco(type, index, text, chapterMarker, false);
    }

    /** Monta um bloco MinerU com opção de sinalizar continuação do bloco anterior. */
    private String bloco(String type, int index, String text, boolean chapterMarker, boolean continuesPrev) {
        String bbox = chapterMarker ? "[0.45,0.08,0.60,0.10]" : "[0.14,0.1,0.8,0.12]";
        String content = type.equals("table") || type.equals("equation")
                ? "\"content\":" + mapper.writeValueAsString(text)
                : "\"content\":[{\"type\":\"text\",\"content\":" + mapper.writeValueAsString(text) + "}]";
        return "{\"type\":\"" + type + "\",\"index\":" + index + ",\"bbox\":" + bbox + ","
                + (continuesPrev ? "\"continues_prev\":true," : "") + content + "}";
    }
}
