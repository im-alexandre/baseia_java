package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.MineruReader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Testes sintéticos do parser, independentes das extrações MinerU que serão refeitas. */
class ParserNormasServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MineruReader reader = new MineruReader();
    private final ParserNormasService parser = new ParserNormasService();

    /** Une marcador e título do capítulo sem descartar o escopo na leitura completa. */
    @Test
    void preservaEstruturaCompletaEContratoDeParseAnterior() {
        JsonNode entrada = documento(bloco("doc_title", 0, "CAPÍTULO 2.", true),
                bloco("doc_title", 1, " GESTÃO\n PATRIMONIAL ", false),
                bloco("paragraph_title", 2, "2.1. OBJETIVO", false),
                bloco("paragraph_title", 3, "2.1.1. Regra de teste", false),
                bloco("text", 4, "Descrição da regra.", false));

        List<ElementoNormativo> completo = parser.parseCompleto(reader.ler(entrada), Set.of());
        assertEquals(List.of(CAPITULO, ARTIGO, INCISO, TEXTO),
                completo.stream().map(ElementoNormativo::tipo).toList());
        assertEquals("CAPÍTULO 2. GESTÃO PATRIMONIAL", completo.getFirst().texto());
        assertEquals("2.1.1. Regra de teste", completo.getLast().pais().inciso());
        assertEquals(List.of(TEXTO), parser.parse(reader.ler(entrada)).stream()
                .map(ElementoNormativo::tipo).toList());
    }

    /** Mantém texto sob o escopo mais específico sem absorver uma alínea ou subalínea. */
    @Test
    void mantemDispositivosETextoDeContinuacaoSeparados() {
        JsonNode entrada = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("paragraph_title", 2, "1.2.1. Bens:", false),
                bloco("text", 3, "a) Alínea", false),
                bloco("text", 4, "I) Subalínea", false),
                bloco("text", 5, "Continuação da subalínea.", false));
        List<ElementoNormativo> elementos = parser.parse(reader.ler(entrada));

        assertEquals(List.of(ALINEA, SUBALINEA, TEXTO),
                elementos.stream().map(ElementoNormativo::tipo).toList());
        assertEquals("a) Alínea", elementos.get(1).pais().alinea());
        assertEquals("I) Subalínea", elementos.get(2).pais().subalinea());
    }

    /** A ausência de permissão a anexos não depende de leituras anteriores. */
    @Test
    void respeitaAllowlistDeAnexos() {
        JsonNode entrada = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false),
                bloco("doc_title", 2, "ANEXO A", true),
                bloco("text", 3, "TABELA DE DEFINIÇÕES", false),
                bloco("doc_title", 4, "ANEXO B", true),
                bloco("text", 5, "MODELO DE FORMULÁRIO", false));

        assertTrue(parser.parse(reader.ler(entrada)).stream().noneMatch(e -> e.tipo() == ANEXO));
        List<ElementoNormativo> permitidos = parser.parse(reader.ler(entrada), Set.of("A", "B"));
        assertTrue(permitidos.stream().anyMatch(e -> e.tipo() == ANEXO && "ANEXO A".equals(e.marcador())));
        assertFalse(permitidos.stream().anyMatch(e -> e.tipo() == ANEXO && "ANEXO B".equals(e.marcador())));
    }

    /** Mantém tabelas e equações com seu conteúdo original. */
    @Test
    void preservaTabelaEEquacao() {
        JsonNode entrada = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "TÍTULO", false),
                bloco("paragraph_title", 2, "1.3.1 - Militares", false),
                bloco("table", 3, "<table><tr><td>Conta</td></tr></table>", false),
                bloco("equation", 4, "x = y", false));
        List<ElementoNormativo> elementos = parser.parse(reader.ler(entrada));

        assertTrue(elementos.stream().anyMatch(e -> e.tipo() == TABELA && e.texto().contains("<table>")));
        assertTrue(elementos.stream().anyMatch(e -> e.tipo() == EQUACAO && e.texto().equals("x = y")));
    }

    /** Agrupa somente texto irmão, preservando suas fontes. */
    @Test
    void agrupaParagrafosDeMesmoPai() {
        JsonNode entrada = documento(bloco("doc_title", 0, "CAPÍTULO 1", true),
                bloco("doc_title", 1, "GESTÃO", false), bloco("text", 2, "Primeiro.", false),
                bloco("text", 3, "Segundo.", false));
        List<ElementoNormativo> elementos = parser.parse(reader.ler(entrada));

        assertEquals(1, elementos.size());
        assertEquals("Primeiro.\nSegundo.", elementos.getFirst().texto());
        assertEquals(2, elementos.getFirst().origens().size());
    }

    /** Cria um JSON MinerU mínimo com bloco e geometria válidos. */
    private JsonNode documento(String... blocos) {
        return mapper.readTree("{\"schema\":\"docvortex.middle\",\"schema_version\":\"2.0\",\"pages\":[{\"page_idx\":0,\"blocks\":["
                + String.join(",", blocos) + "]}]}");
    }

    /** Mantém o texto bruto e configura somente a posição do bloco de teste. */
    private String bloco(String tipo, int indice, String texto, boolean marcadorCapitulo) {
        String bbox = marcadorCapitulo ? "[0.45,0.08,0.60,0.10]" : "[0.14,0.1,0.8,0.12]";
        String conteudo = tipo.equals("table") || tipo.equals("equation")
                ? mapper.writeValueAsString(texto)
                : "[{\"type\":\"text\",\"content\":" + mapper.writeValueAsString(texto) + "}]";
        return "{\"type\":\"" + tipo + "\",\"index\":" + indice + ",\"bbox\":" + bbox + ",\"content\":" + conteudo + "}";
    }
}
