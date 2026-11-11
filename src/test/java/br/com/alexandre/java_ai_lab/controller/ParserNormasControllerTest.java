package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ingestao.ParserNormasService;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercita somente o contrato HTTP real, sem exemplos JSON versionados. */
class ParserNormasControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Encaminha anexos selecionados sem manter estado entre chamadas. */
    @Test
    void encaminhaSelecaoDeAnexos() throws Exception {
        MineruReader reader = mock(MineruReader.class);
        ParserNormasService parser = mock(ParserNormasService.class);
        when(reader.ler(org.mockito.ArgumentMatchers.any(JsonNode.class))).thenReturn(List.of());
        when(parser.parse(anyList(), eq(Set.of("A", "C")))).thenReturn(List.of());
        String documento = "{\"schema\":\"docvortex.middle\",\"schema_version\":\"2.0\",\"pages\":[]}";

        mvc(reader, parser).perform(post("/ingestao/normas/parse")
                        .queryParam("anexosPermitidos", "A", "C")
                        .contentType(MediaType.APPLICATION_JSON).content(documento))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(parser).parse(List.of(), Set.of("A", "C"));
    }

    /** Recebe um exemplo sintético e devolve o conteúdo reconhecido pelo parser. */
    @Test
    void processaPostSemFixture() throws Exception {
        String documento = """
                {"schema":"docvortex.middle","schema_version":"2.0","pages":[
                 {"page_idx":0,"blocks":[
                  {"type":"doc_title","index":0,"bbox":[0.45,0.08,0.60,0.10],"content":"CAPÍTULO 1"},
                  {"type":"doc_title","index":1,"bbox":[0.1,0.1,0.8,0.12],"content":"GESTÃO"},
                  {"type":"text","index":2,"bbox":[0.1,0.3,0.8,0.4],"content":"Conteúdo de teste."}
                ]}]}
                """;
        mvc(new MineruReader(), new ParserNormasService())
                .perform(post("/ingestao/normas/parse").contentType(MediaType.APPLICATION_JSON).content(documento))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Conteúdo de teste.")));
    }

    /** Rejeita JSON incompatível antes da classificação. */
    @Test
    void rejeitaDocumentoInvalido() throws Exception {
        mvc(new MineruReader(), new ParserNormasService())
                .perform(post("/ingestao/normas/parse").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** A antiga rota de demonstração não depende mais de JSON empacotado. */
    @Test
    void naoTemMaisGetDeExemplo() throws Exception {
        mvc(new MineruReader(), new ParserNormasService())
                .perform(get("/ingestao/normas/parse"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** Inicializa o adaptador HTTP com parsing real e sem contexto Spring completo. */
    private MockMvc mvc(MineruReader reader, ParserNormasService parser) {
        return MockMvcBuilders.standaloneSetup(new ParserNormasController(reader, parser)).build();
    }
}
