package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ingestao.ParserNormasService;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParserNormasControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Confere no POST todas as origens do golden e os pais do conteúdo agrupado. */
    @Test
    void recebeJsonRealERetornaGoldenDoParser() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/ingestao/mineru/SGM-303-Rev7.json");
             var esperado = getClass().getResourceAsStream(
                     "/ingestao/mineru/SGM-303-Rev7-elementos-esperados.json")) {
            assertThat(entrada).isNotNull();
            assertThat(esperado).isNotNull();
            byte[] json = entrada.readAllBytes();
            byte[] resposta = mvc(new MineruReader(), new ParserNormasService())
                    .perform(post("/ingestao/normas/parse")
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsByteArray();

            JsonNode elementos = mapper.readTree(resposta);
            JsonNode golden = mapper.readTree(esperado);
            assertaOrigensContraGolden(elementos, golden);
            assertaTiposEQuantidade(elementos);
            assertaPaisNormativos(elementos);
        }
    }

    /** Confirma pipeline do GET, cobertura das origens do golden e parentesco na fixture real. */
    @Test
    void getProcessaFixtureDeClasspathContraGolden() throws Exception {
        try (InputStream esperado = getClass().getResourceAsStream(
                "/ingestao/mineru/SGM-303-Rev7-elementos-esperados.json")) {
            assertThat(esperado).isNotNull();
            byte[] resposta = mvc(new MineruReader(), new ParserNormasService())
                    .perform(get("/ingestao/normas/parse"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsByteArray();

            JsonNode elementosJson = mapper.readTree(resposta);
            JsonNode golden = mapper.readTree(esperado);
            assertaOrigensContraGolden(elementosJson, golden);
            assertaTiposEQuantidade(elementosJson);
            assertaPaisNormativos(elementosJson);
        }
    }

    /** Encaminha os blocos lidos e a seleção de anexos ao parser. */
    @Test
    void encaminhaBlocosEAnexosPermitidosDaQueryString() throws Exception {
        var reader = mock(MineruReader.class);
        var parser = mock(ParserNormasService.class);
        when(reader.ler(org.mockito.ArgumentMatchers.any(JsonNode.class))).thenReturn(List.of());
        when(parser.parse(anyList(), eq(Set.of("A", "C")))).thenReturn(List.of());
        String documento = "{\"schema\":\"docvortex.middle\",\"schema_version\":\"2.0\",\"pages\":[]}";

        mvc(reader, parser).perform(post("/ingestao/normas/parse")
                        .queryParam("anexosPermitidos", "A", "C")
                        .contentType(MediaType.APPLICATION_JSON).content(documento))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(reader).ler(mapper.readTree(documento));
        verify(parser).parse(List.of(), Set.of("A", "C"));
    }

    /** Converte documento incompatível rejeitado pelo Reader em resposta 400. */
    @Test
    void retorna400ParaDocumentoInvalido() throws Exception {
        mvc(new MineruReader(), new ParserNormasService()).perform(post("/ingestao/normas/parse")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** Mantém a resposta 400 do Spring para corpo que não contém JSON válido. */
    @Test
    void retorna400ParaJsonMalformado() throws Exception {
        mvc(new MineruReader(), new ParserNormasService()).perform(post("/ingestao/normas/parse")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    /** Monta o controller com os três colaboradores usados pela nova pipeline. */
    private MockMvc mvc(MineruReader reader, ParserNormasService parser) {
        return MockMvcBuilders.standaloneSetup(new ParserNormasController(reader, parser, mapper)).build();
    }

    /** Verifica os pais originais da alínea e subalínea no texto de continuação via HTTP. */
    private void assertaPaisNormativos(JsonNode resposta) {
        List<JsonNode> elementos = new ArrayList<>();
        resposta.forEach(elementos::add);
        JsonNode texto = elementos.stream().filter(e -> e.path("tipo").asText().equals("TEXTO")
                        && encontrarOrigem(e, 15, 2) != null)
                .findFirst().orElseThrow();
        assertThat(texto.path("texto").asText()).contains("É a denominação utilizada para as OMC");
        assertThat(texto.path("pais").path("capitulo").asText())
                .isEqualTo("CAPÍTULO 1. ESTRUTURA BÁSICA DA GESTÃO PATRIMONIAL");
        assertThat(texto.path("pais").path("artigo").asText())
                .isEqualTo("1.3. ESTRUTURA ORGANIZACIONAL DA GESTÃO PATRIMONIAL");
        assertThat(texto.path("pais").path("inciso").asText())
                .isEqualTo("1.3.1. Para efeito de recebimento, distribuição, consumo e respectivos controles contábeis, fiscais e de tomada de contas dos bens patrimoniais, as OM da MB são classificadas segundo a sua natureza patrimonial, como:");
        assertThat(texto.path("pais").path("alinea").asText())
                .isEqualTo("a) Organização Militar Consumidora (OMC)");
        assertThat(texto.path("pais").path("subalinea").asText())
                .isEqualTo("I) Organização Militar Consumidora Integrada (OMCI)");
        JsonNode origem = encontrarOrigem(texto, 15, 2);
        assertThat(origem).isNotNull();
        assertThat(origem.path("tipo").asText()).isEqualTo("TEXTO");
        assertThat(origem.path("marcador").isNull()).isTrue();
        assertThat(origem.path("paginaFisica").asInt()).isEqualTo(15);
        assertThat(origem.path("indiceOrigem").asInt()).isEqualTo(2);
        assertThat(origem.path("paginaImpressa").asText()).isEqualTo("1-2");
        assertThat(origem.path("bbox")).isEqualTo(mapper.valueToTree(List.of(0.09, 0.068, 0.862, 0.165)));
        assertThat(origem.path("continuaAnterior").asBoolean()).isFalse();
    }

    /** Assegura que os três tipos estruturais foram omitidos e que as origens totalizam 1404. */
    private void assertaTiposEQuantidade(JsonNode resposta) {
        List<String> tipos = new ArrayList<>();
        int[] quantidadeOrigens = {0};
        resposta.forEach(elemento -> {
            tipos.add(elemento.path("tipo").asText());
            elemento.path("origens").forEach(origem -> quantidadeOrigens[0]++);
        });
        assertThat(tipos).doesNotContain("CAPITULO", "ARTIGO", "INCISO");
        assertThat(quantidadeOrigens[0]).isEqualTo(1404);
    }

    /** Confere origens e textos-fonte contra o golden sem criar grupos esperados. */
    private void assertaOrigensContraGolden(JsonNode resposta, JsonNode golden) {
        JsonNode esperados = somenteOrigensRetornaveis(golden);
        var origensObtidas = mapper.createArrayNode();
        resposta.forEach(grupo -> grupo.path("origens").forEach(origem -> origensObtidas.add(projecaoOrigem(origem))));
        var origensEsperadas = mapper.createArrayNode();
        esperados.forEach(elemento -> origensEsperadas.add(projecaoOrigem(elemento)));
        assertThat(origensObtidas.size()).isEqualTo(1404);
        assertThat(origensObtidas).isEqualTo(origensEsperadas);
        esperados.forEach(esperado -> {
            JsonNode origemEsperada = projecaoOrigem(esperado);
            String textoEsperado = normalizaWhitespace(esperado.path("texto").asText());
            assertThat(elementosComOrigem(resposta, origemEsperada).stream()
                    .anyMatch(grupo -> normalizaWhitespace(grupo.path("texto").asText()).contains(textoEsperado)))
                    .as("texto associado à origem %s/%s", esperado.path("paginaFisica").asInt(),
                            esperado.path("indiceOrigem").asInt()).isTrue();
        });
    }

    /** Mantém como fontes somente entradas retornáveis do golden original. */
    private JsonNode somenteOrigensRetornaveis(JsonNode elementos) {
        var filtrados = mapper.createArrayNode();
        elementos.forEach(elemento -> {
            if (!List.of("CAPITULO", "ARTIGO", "INCISO").contains(elemento.path("tipo").asText())) {
                filtrados.add(elemento);
            }
        });
        return filtrados;
    }

    /** Projeta os sete campos que identificam a origem e seus metadados de leitura. */
    private JsonNode projecaoOrigem(JsonNode origem) {
        var projetada = mapper.createObjectNode();
        for (String campo : List.of("tipo", "marcador", "paginaFisica", "paginaImpressa", "indiceOrigem", "bbox", "continuaAnterior")) {
            if (origem.has(campo)) projetada.set(campo, origem.get(campo).deepCopy());
        }
        return projetada;
    }

    /** Busca o grupo que preservou uma origem específica. */
    private List<JsonNode> elementosComOrigem(JsonNode resposta, JsonNode origemEsperada) {
        List<JsonNode> grupos = new ArrayList<>();
        resposta.forEach(grupo -> {
            boolean contem = false;
            for (JsonNode origem : grupo.path("origens")) {
                if (projecaoOrigem(origem).equals(origemEsperada)) contem = true;
            }
            if (contem) grupos.add(grupo);
        });
        return grupos;
    }

    /** Localiza a origem por página física e índice MinerU. */
    private JsonNode encontrarOrigem(JsonNode grupo, int pagina, int indice) {
        for (JsonNode origem : grupo.path("origens")) {
            if (origem.path("paginaFisica").asInt() == pagina && origem.path("indiceOrigem").asInt() == indice) {
                return origem;
            }
        }
        return null;
    }

    /** Normaliza somente whitespace para comparar uma origem com o texto indentado do grupo. */
    private String normalizaWhitespace(String texto) {
        return texto.replaceAll("\\s+", " ").trim();
    }
}

