package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ingestao.DownloadLegislacaoService;
import br.com.alexandre.java_ai_lab.service.ingestao.ParserLegislacaoService;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.HtmlLegislacaoReader;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParserLegislacaoControllerTest {
    private static final String ROTA = "/ingestao/legislacao/parse";
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final DownloadLegislacaoService download = mock(DownloadLegislacaoService.class);

    /** Confere a lei local completa, sua ordem documental e o contrato próprio da origem HTML. */
    @Test
    void getSemParametrosProcessaLeiRealDeClasspath() throws Exception {
        JsonNode elementos = mapper.readTree(mvc().perform(get(ROTA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        List<JsonNode> artigos = elementosDoTipo(elementos, "ARTIGO").stream()
                .filter(artigo -> !artigo.path("citacao").asBoolean()).toList();
        assertThat(artigos).hasSize(55);
        assertThat(artigos.stream().map(artigo -> artigo.path("ancora").asText()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 55).mapToObj(numero -> "art" + numero).toList());
        assertThat(artigos.getFirst().path("marcador").asText()).isEqualTo("Art. 1º");
        assertThat(artigos.getLast().path("marcador").asText()).isEqualTo("Art. 55.");
        for (String tipo : List.of("CAPITULO", "SECAO", "SUBSECAO", "FECHO", "CITACAO")) {
            assertThat(elementosDoTipo(elementos, tipo)).as("tipo omitido %s", tipo).isEmpty();
        }
        assertThat(artigos.getFirst().path("pais").path("capitulo").asText())
                .isEqualTo("CAPÍTULO I. DISPOSIÇÕES GERAIS");
        JsonNode paisArtigo18 = artigos.get(17).path("pais");
        assertThat(paisArtigo18.path("capitulo").asText()).isEqualTo("CAPÍTULO II. "
                + "DA DIGITALIZAÇÃO DA ADMINISTRAÇÃO PÚBLICA E DA PRESTAÇÃO DIGITAL DE SERVIÇOS PÚBLICOS - GOVERNO DIGITAL");
        assertThat(paisArtigo18.path("secao").asText()).isEqualTo("Seção IV. Dos Componentes do Governo Digital");
        assertThat(paisArtigo18.path("subsecao").asText()).isEqualTo("Subseção I. Da Definição");
        assertThat(elementos.size()).isEqualTo(275);
        JsonNode identificacao = elementos.get(0);
        assertThat(identificacao.path("tipo").asText()).isEqualTo("IDENTIFICACAO_LEI");
        assertThat(identificacao.path("texto").asText()).isEqualTo("LEI Nº 14.129, DE 29 DE MARÇO DE 2021");
        assertThat(identificacao.path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(8)));
        assertThat(identificacao.path("origens").size()).isEqualTo(1);
        assertThat(identificacao.path("origens").get(0).path("tipo").asText()).isEqualTo("IDENTIFICACAO_LEI");
        JsonNode ementa = elementos.get(1);
        assertThat(ementa.path("tipo").asText()).isEqualTo("EMENTA");
        assertThat(ementa.path("texto").asText()).isEqualTo(
                "Dispõe sobre princípios, regras e instrumentos para o Governo Digital e para o aumento da eficiência pública "
                + "e altera a Lei nº 7.116, de 29 de agosto de 1983, a Lei nº 12.527, de 18 de novembro de 2011 "
                + "(Lei de Acesso à Informação), a Lei nº 12.682, de 9 de julho de 2012, e a Lei nº 13.460, de 26 de junho de 2017.");
        assertThat(ementa.path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(11)));
        assertThat(ementa.path("origens").size()).isEqualTo(1);
        assertThat(ementa.path("origens").get(0).path("tipo").asText()).isEqualTo("EMENTA");
        for (JsonNode cabecalho : List.of(identificacao, ementa)) {
            assertThat(cabecalho.path("citacao").asBoolean()).isFalse();
            assertThat(cabecalho.path("marcador").isNull()).isTrue();
            cabecalho.path("pais").forEach(pai -> assertThat(pai.isNull()).isTrue());
        }
        assertThat(elementos.get(elementos.size() - 1).path("texto").asText())
                .isEqualTo("III - 180 (cento e oitenta) dias de sua publicação oficial, para os Municípios.");
        assertThat(elementosDoTipo(elementos, "PARAGRAFO").stream()
                .map(paragrafo -> paragrafo.path("marcador").asText()).toList())
                .contains("Parágrafo único.", "§ 1º", "§ 2º");
        assertaCitacoesDaLei(elementos, artigos);
        assertaContratoHtml(elementos);
        assertaAusenciaDeRuido(elementos);
        verifyNoInteractions(download);
    }

    /** O inciso diretamente subordinado ao artigo conserva o nível de parágrafo ausente no JSON. */
    @Test
    void postMantemParagrafoNuloParaIncisoDiretoDoArtigo() throws Exception {
        String html = """
                <p id="art1">Art. 1º São diretrizes desta Lei:</p>
                <p>I - simplificar o atendimento.</p>
                """;

        JsonNode elementos = mapper.readTree(mvc().perform(post(ROTA)
                        .contentType(HTML_UTF8).content(html.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(elementos.size()).isEqualTo(2);
        JsonNode inciso = elementos.get(1);
        assertThat(inciso.path("tipo").asText()).isEqualTo("INCISO");
        assertThat(inciso.path("marcador").asText()).isEqualTo("I -");
        assertThat(inciso.path("pais").path("artigo").asText())
                .isEqualTo("Art. 1º São diretrizes desta Lei:");
        assertThat(inciso.path("pais").has("paragrafo")).isTrue();
        assertThat(inciso.path("pais").path("paragrafo").isNull()).isTrue();
        assertThat(elementos.get(0).path("ancora").asText()).isEqualTo("art1");
        assertThat(elementos.get(0).path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(0)));
        assertThat(inciso.path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(1)));
        assertaContratoHtml(elementos);
        verifyNoInteractions(download);
    }

    /** Agrupa apenas prosa e itens irmãos, preservando dispositivos e todas as referências DOM. */
    @Test
    void postAgrupaTextoEListaSemAbsorverDispositivos() throws Exception {
        String html = """
                <p id="art1">Art. 1º Regra.</p>
                <p>Primeiro texto.</p><p>Segundo texto.</p>
                <p>§ 1º Parágrafo.</p><p>I - inciso:</p><p>a) alínea:</p>
                <p>Complemento inicial.</p><p>Complemento final.</p>
                <p>1. primeiro item;</p><p>2. segundo item.</p>
                <p>§ 2º Outro parágrafo.</p><p id="art2">Art. 2º Nova regra.</p>
                """;

        JsonNode elementos = mapper.readTree(mvc().perform(post(ROTA).contentType(HTML_UTF8)
                        .content(html.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        List<String> tipos = new ArrayList<>();
        elementos.forEach(elemento -> tipos.add(elemento.path("tipo").asText()));
        assertThat(tipos).containsExactly("ARTIGO", "TEXTO", "PARAGRAFO", "INCISO", "ALINEA", "TEXTO",
                "LISTA", "PARAGRAFO", "ARTIGO");
        assertThat(elementos.get(1).path("texto").asText()).isEqualTo("Primeiro texto.\nSegundo texto.");
        assertThat(elementos.get(1).path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(1, 2)));
        assertThat(elementos.get(1).path("origens").size()).isEqualTo(2);
        assertThat(elementos.get(5).path("texto").asText()).isEqualTo("Complemento inicial.\nComplemento final.");
        assertThat(elementos.get(5).path("pais").path("alinea").asText()).isEqualTo("a) alínea:");
        JsonNode lista = elementos.get(6);
        assertThat(lista.path("texto").asText()).isEqualTo("1. primeiro item;\n2. segundo item.");
        assertThat(lista.path("marcador").isNull()).isTrue();
        assertThat(lista.path("indicesOrigem")).isEqualTo(mapper.valueToTree(List.of(8, 9)));
        assertThat(lista.path("origens").size()).isEqualTo(2);
        assertThat(lista.path("origens").get(0).path("marcador").asText()).isEqualTo("1.");
        assertThat(lista.path("origens").get(1).path("marcador").asText()).isEqualTo("2.");
        assertThat(lista.path("pais")).isEqualTo(elementos.get(5).path("pais"));
        assertThat(elementos.get(7).path("pais").path("alinea").isNull()).isTrue();
        assertaContratoHtml(elementos);
        verifyNoInteractions(download);
    }

    /** Classifica §§/incisos citados, usa artigo omitido como pai e limpa o escopo ao voltar à lei. */
    @Test
    void postClassificaDispositivosCitadosSemSubstituirArtigoIntrodutor() throws Exception {
        String html = """
                <p id="art51">Art. 51. Altera outra lei:</p>
                <p>“Art. 12. …………</p><p>..................................</p>
                <p>§ 1º Regras citadas:</p><p>I - inciso citado:</p><p>a) alínea citada.” (NR)</p>
                <p id="art52">Art. 52. Outra alteração:</p>
                <blockquote><p>“Art. 10-A. Nova redação.</p><p>§ 2º Complemento citado.</p></blockquote>
                <p id="art53">Art. 53. Dispositivo próprio.</p><p>I - inciso próprio.</p>
                """;

        JsonNode elementos = mapper.readTree(mvc().perform(post(ROTA).contentType(HTML_UTF8)
                        .content(html.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        List<String> tipos = new ArrayList<>();
        elementos.forEach(elemento -> tipos.add(elemento.path("tipo").asText()));
        assertThat(tipos).containsExactly("ARTIGO", "PARAGRAFO", "INCISO", "ALINEA", "ARTIGO", "ARTIGO",
                "PARAGRAFO", "ARTIGO", "INCISO");
        for (int indice : List.of(1, 2, 3)) {
            JsonNode citado = elementos.get(indice);
            assertThat(citado.path("citacao").asBoolean()).isTrue();
            assertThat(citado.path("pais").path("artigo").asText()).isEqualTo("Art. 51. Altera outra lei:");
            assertThat(citado.path("pais").path("artigoCitado").asText()).isEqualTo("Art. 12.");
        }
        assertThat(elementos.get(1).path("marcador").asText()).isEqualTo("§ 1º");
        assertThat(elementos.get(1).path("pais").path("paragrafo").isNull()).isTrue();
        assertThat(elementos.get(2).path("pais").path("paragrafo").asText()).isEqualTo("§ 1º Regras citadas:");
        assertThat(elementos.get(3).path("pais").path("inciso").asText()).isEqualTo("I - inciso citado:");
        assertThat(elementos.get(5).path("citacao").asBoolean()).isTrue();
        assertThat(elementos.get(5).path("marcador").asText()).isEqualTo("Art. 10-A.");
        assertThat(elementos.get(5).path("pais").path("artigoCitado").isNull()).isTrue();
        assertThat(elementos.get(6).path("pais").path("artigo").asText()).isEqualTo("Art. 52. Outra alteração:");
        assertThat(elementos.get(6).path("pais").path("artigoCitado").asText()).isEqualTo("Art. 10-A. Nova redação.");
        JsonNode incisoProprio = elementos.get(8);
        assertThat(incisoProprio.path("citacao").asBoolean()).isFalse();
        assertThat(incisoProprio.path("pais").path("artigo").asText()).isEqualTo("Art. 53. Dispositivo próprio.");
        assertThat(incisoProprio.path("pais").path("artigoCitado").isNull()).isTrue();
        assertThat(incisoProprio.path("pais").path("paragrafo").isNull()).isTrue();
        assertThat(incisoProprio.path("pais").path("inciso").isNull()).isTrue();
        assertaContratoHtml(elementos);
        assertaAusenciaDeRuido(elementos);
        verifyNoInteractions(download);
    }

    /** A mesma fixture enviada por POST produz exatamente a resposta JSON do exemplo local GET. */
    @Test
    void postDaFixtureRealEquivaleAoGetLocal() throws Exception {
        MockMvc mvc = mvc();
        byte[] respostaGet = mvc.perform(get(ROTA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var entrada = getClass().getResourceAsStream("/ingestao/legislacao/lei_14129_2021.html")) {
            assertThat(entrada).isNotNull();
            byte[] respostaPost = mvc.perform(post(ROTA).contentType(HTML_UTF8)
                            .content(entrada.readAllBytes()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(mapper.readTree(respostaPost)).isEqualTo(mapper.readTree(respostaGet));
        }
        verifyNoInteractions(download);
    }

    /** A URL válida chega ao downloader, e somente seu HTML passa pelo reader e pelo parser reais. */
    @Test
    void getComUrlEncaminhaDownloadERetornaJson() throws Exception {
        String url = "https://legislacao.example/lei.html";
        when(download.parse(url)).thenReturn(new DownloadLegislacaoService.ParsedLegislation(
                url, "Lei de exemplo", "<p id=art7>Art. 7º Texto baixado.</p>"));

        JsonNode elementos = mapper.readTree(mvc().perform(get(ROTA).queryParam("url", url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(elementos.size()).isEqualTo(1);
        assertThat(elementos.get(0).path("tipo").asText()).isEqualTo("ARTIGO");
        assertThat(elementos.get(0).path("texto").asText()).isEqualTo("Art. 7º Texto baixado.");
        assertThat(elementos.get(0).path("ancora").asText()).isEqualTo("art7");
        verify(download).parse(url);
    }

    /** URLs vazias, relativas, malformadas ou fora de HTTP(S) são rejeitadas antes de qualquer rede. */
    @Test
    void getComUrlInvalidaRetorna400SemBaixar() throws Exception {
        MockMvc mvc = mvc();
        for (String url : List.of("", "/lei.html", "ftp://legislacao.example/lei.html", "https://", "https://[")) {
            mvc.perform(get(ROTA).queryParam("url", url)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(download);
    }

    /** A indisponibilidade de origem é exposta como 502, sem confundir falha de rede com HTML inválido. */
    @Test
    void getComFalhaDeDownloadRetorna502() throws Exception {
        String url = "https://legislacao.example/indisponivel.html";
        when(download.parse(url)).thenThrow(new IOException("Origem indisponível"));

        mvc().perform(get(ROTA).queryParam("url", url)).andExpect(status().isBadGateway());

        verify(download).parse(url);
    }

    /** HTML legível sem dispositivo ARTIGO não constitui uma lei reconhecível e resulta em 400. */
    @Test
    void postSemArtigosRetorna400() throws Exception {
        mvc().perform(post(ROTA).contentType(HTML_UTF8)
                        .content("<p>Publicação sem dispositivos legais.</p>"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(download);
    }

    /** O endpoint HTML rejeita outros tipos de mídia antes de executar o pipeline de leitura. */
    @Test
    void postComContentTypeIncorretoRetorna415() throws Exception {
        mvc().perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>Art. 1º Texto.</p>\"}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(download);
    }

    /** Monta apenas o adaptador HTTP em teste, com processamento real e download sem acesso externo. */
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ParserLegislacaoController(download,
                new HtmlLegislacaoReader(), new ParserLegislacaoService())).build();
    }

    /** Seleciona dispositivos de um tipo preservando a ordem documental da resposta HTTP. */
    private List<JsonNode> elementosDoTipo(JsonNode elementos, String tipo) {
        List<JsonNode> selecionados = new ArrayList<>();
        elementos.forEach(elemento -> {
            if (elemento.path("tipo").asText().equals(tipo)) selecionados.add(elemento);
        });
        return selecionados;
    }

    /** As redações citadas permanecem sob os artigos alteradores e não abrem novos dispositivos da lei. */
    private void assertaCitacoesDaLei(JsonNode elementos, List<JsonNode> artigos) {
        List<JsonNode> citacoes = new ArrayList<>();
        elementos.forEach(elemento -> {
            if (elemento.path("citacao").asBoolean()) citacoes.add(elemento);
        });
        // As linhas pontilhadas e os caputs sem prosa dos arts. 3º e 7º não são conteúdo.
        List<List<String>> tipos = List.of(
                List.of("ALINEA", "ALINEA", "PARAGRAFO", "PARAGRAFO", "PARAGRAFO"),
                List.of("ARTIGO", "PARAGRAFO", "PARAGRAFO"), List.of("ARTIGO"),
                List.of("PARAGRAFO", "ARTIGO", "PARAGRAFO", "PARAGRAFO", "PARAGRAFO"));
        List<List<String>> marcadores = List.of(List.of("g)", "h)", "§ 1º", "§ 2º", "§ 3º"),
                List.of("Art. 12.", "§ 1º", "§ 2º"), List.of("Art. 3º"),
                List.of("§ 6º", "Art. 10-A.", "§ 1º", "§ 2º", "§ 3º"));
        assertThat(citacoes).hasSize(tipos.stream().mapToInt(List::size).sum());
        for (int numero = 51; numero <= 54; numero++) {
            String artigoPai = artigos.get(numero - 1).path("texto").asText();
            List<JsonNode> doArtigo = citacoes.stream()
                    .filter(citacao -> citacao.path("pais").path("artigo").asText().equals(artigoPai)).toList();
            assertThat(doArtigo.stream().map(citacao -> citacao.path("tipo").asText()).toList())
                    .as("tipos citados pelo artigo %s", numero).containsExactlyElementsOf(tipos.get(numero - 51));
            assertThat(doArtigo.stream().map(citacao -> citacao.path("marcador").asText()).toList())
                    .as("marcadores citados pelo artigo %s", numero)
                    .containsExactlyElementsOf(marcadores.get(numero - 51));
        }
        List<JsonNode> alineasCitadas = citacoes.stream()
                .filter(citacao -> citacao.path("tipo").asText().equals("ALINEA")).toList();
        assertThat(alineasCitadas).allSatisfy(citacao ->
                assertThat(citacao.path("pais").path("artigoCitado").asText()).isEqualTo("Art. 3º"));
        JsonNode paragrafo6 = citacoes.stream()
                .filter(citacao -> citacao.path("marcador").asText().equals("§ 6º")).findFirst().orElseThrow();
        assertThat(paragrafo6.path("pais").path("artigoCitado").asText()).isEqualTo("Art. 7º");
        assertThat(elementosDoTipo(elementos, "ARTIGO").stream()
                .filter(artigo -> artigo.path("citacao").asBoolean()).toList()).allSatisfy(artigo -> {
            assertThat(artigo.path("texto").asText()).isNotEqualTo(artigo.path("marcador").asText());
            assertThat(artigo.path("pais").path("artigoCitado").isNull()).isTrue();
        });
    }

    /** Confere os oito campos HTML, os nove pais opcionais e a proveniência individual sem geometria. */
    private void assertaContratoHtml(JsonNode elementos) {
        for (JsonNode elemento : elementos) {
            assertThat(elemento.isObject()).isTrue();
            assertThat(elemento.size()).isEqualTo(8);
            for (String campo : List.of("tipo", "marcador", "texto", "indicesOrigem", "ancora", "pais", "citacao", "origens")) {
                assertThat(elemento.has(campo)).as("campo %s", campo).isTrue();
            }
            assertThat(elemento.path("citacao").isBoolean()).isTrue();
            assertThat(elemento.path("indicesOrigem").isArray()).isTrue();
            assertThat(elemento.path("indicesOrigem").size()).isPositive();
            assertThat(elemento.path("pais").isObject()).isTrue();
            assertThat(elemento.path("pais").size()).isEqualTo(9);
            for (String campo : List.of("capitulo", "secao", "subsecao", "artigo", "paragrafo", "inciso", "alinea", "item", "artigoCitado")) {
                assertThat(elemento.path("pais").has(campo)).as("pai %s", campo).isTrue();
            }
            assertThat(elemento.path("origens").isArray()).isTrue();
            assertThat(elemento.path("origens").size()).isPositive();
            List<Integer> indices = new ArrayList<>();
            for (JsonNode origem : elemento.path("origens")) {
                assertThat(origem.size()).isEqualTo(4);
                for (String campo : List.of("tipo", "marcador", "indicesOrigem", "ancora")) {
                    assertThat(origem.has(campo)).as("origem %s", campo).isTrue();
                }
                assertThat(origem.path("indicesOrigem").isArray()).isTrue();
                assertThat(origem.path("indicesOrigem").size()).isPositive();
                origem.path("indicesOrigem").forEach(indice -> indices.add(indice.asInt()));
            }
            assertThat(elemento.path("indicesOrigem")).isEqualTo(mapper.valueToTree(indices));
            assertThat(elemento.has("paginaFisica")).isFalse();
            assertThat(elemento.has("bbox")).isFalse();
        }
    }

    /** Inspeciona texto e cada pai para impedir resíduos institucionais, fecho, assinaturas e omissões. */
    private void assertaAusenciaDeRuido(JsonNode elementos) {
        for (JsonNode elemento : elementos) {
            List<String> textos = new ArrayList<>();
            textos.add(elemento.path("texto").asText());
            elemento.path("pais").forEach(pai -> {
                if (!pai.isNull()) textos.add(pai.asText());
            });
            assertThat(textos).allSatisfy(texto -> {
                assertThat(texto).isNotBlank().doesNotContain("CÂMARA DOS DEPUTADOS",
                        "Centro de Documentação e Informação", "O PRESIDENTE DA REPÚBLICA",
                        "Faço saber que o Congresso Nacional", "Brasília, 29 de março de 2021",
                        "JAIR MESSIAS BOLSONARO", "André Luiz de Almeida Mendonça", "Paulo Guedes",
                        "Marcos César Pontes", "Wagner de Campos Rosário", "Onyx Lorenzoni");
                assertThat(texto).doesNotContainPattern("(?:\\.\\h*){3,}|…")
                        .doesNotContainPattern("(?m)^\\h*$");
            });
        }
    }
}
