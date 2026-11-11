package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ContextAssemblerService;
import br.com.alexandre.java_ai_lab.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetrievalControllerTest {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new RetrievalController(retrievalService, new ContextAssemblerService())
    ).build();

    @Test
    void retornaContextoTextualMantendoOsItensDeCadaSecaoJuntos() throws Exception {
        var resultado = jsonMapper.readTree("""
                {"result":{"points":[
                  {"payload":{"text":"Trecho 23 b)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.23","b)"],"order":1}}}},
                  {"payload":{"text":"Trecho 18 a)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.18","a)"],"order":0}}}},
                  {"payload":{"text":"Trecho 15 b)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.15","b)"],"order":1}}}},
                  {"payload":{"text":"Trecho 23 a)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.23","a)"],"order":0}}}},
                  {"payload":{"text":"Trecho 15 a)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.15","a)"],"order":0}}}},
                  {"payload":{"text":"Trecho 18 b)","metadata":{"document_id":"mapcont","structure":{"path":["21.1.18","b)"],"order":1}}}}
                ]}}
                """);
        when(retrievalService.buscar("baixa de bens")).thenReturn(resultado);

        var resposta = mvc.perform(get("/retrieval")
                        .param("pergunta", "baixa de bens")
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        var trechos = Pattern.compile("Conteúdo:\\n([^\\n]+)")
                .matcher(resposta).results()
                .map(match -> match.group(1))
                .toList();
        assertThat(trechos).containsExactly(
                "Trecho 15 a)", "Trecho 15 b)",
                "Trecho 18 a)", "Trecho 18 b)",
                "Trecho 23 a)", "Trecho 23 b)"
        );
        verify(retrievalService).buscar("baixa de bens");
    }

    @Test
    void retornaTextoVazioQuandoNaoHaPontos() throws Exception {
        when(retrievalService.buscar("sem resultados"))
                .thenReturn(jsonMapper.readTree("""
                        {"result":{"points":[]}}
                        """));

        mvc.perform(get("/retrieval").param("pergunta", "sem resultados"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
