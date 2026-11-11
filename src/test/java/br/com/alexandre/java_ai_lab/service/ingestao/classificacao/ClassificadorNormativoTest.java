package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassificadorNormativoTest {
    private final ClassificadorNormativo classificador = new ClassificadorNormativo();

    /** Confirma que aberturas reconhecidas substituem qualquer título pendente. */
    @Test
    void aberturaDeCapituloOuAnexoPrevaleceSobreTituloPendente() {
        assertClassificacao(CAPITULO, "CAPÍTULO 2", "text", "CAPÍTULO 2",
                new ContextoClassificacao(1, TITULO_ANEXO));
        assertClassificacao(ANEXO, "ANEXO B", "text", "ANEXO B.",
                new ContextoClassificacao(1, TITULO_CAPITULO));
    }

    /** Confirma que o título pendente prevalece sobre tabela e numeração textual. */
    @Test
    void tituloPendentePrevaleceSobreTabelaENumeracao() {
        var contexto = new ContextoClassificacao(2, TITULO_CAPITULO);
        assertClassificacao(TITULO_CAPITULO, null, "table", "2.3 Conteúdo", contexto);
        assertClassificacao(TITULO_CAPITULO, null, "text", "2.3 Conteúdo", contexto);
    }

    /** Preserva tabela e equação antes de marcadores encontrados no conteúdo. */
    @ParameterizedTest
    @CsvSource({"table,TABELA", "equation,EQUACAO"})
    void tipoEstruturadoPrevaleceSobrePrefixoTextual(String tipoOrigem, Tipo esperado) {
        assertClassificacao(esperado, null, tipoOrigem, "a) conteúdo estruturado",
                new ContextoClassificacao(1, null));
    }

    /** Distingue artigos e incisos, limitando a numeração ao capítulo ativo. */
    @Test
    void numeracaoExigeCapituloAtivoEIdentificaArtigoOuInciso() {
        var contexto = new ContextoClassificacao(2, null);
        assertClassificacao(ARTIGO, "2.4", "text", "2.4 Artigo", contexto);
        assertClassificacao(INCISO, "2.4.1", "text", "2.4.1 - Inciso", contexto);
        assertClassificacao(TEXTO, null, "text", "3.4 Artigo", contexto);
        assertClassificacao(TEXTO, null, "text", "2.4 Artigo", new ContextoClassificacao(-1, null));
        assertClassificacao(TEXTO, null, "text", "2.4.1.3 Subitem", contexto);
    }

    /** Reconhece alíneas, subalíneas e listas pelo texto, sem depender do tipo MinerU. */
    @ParameterizedTest
    @CsvSource({
            "a) item,ALINEA,a)",
            "ab) item,ALINEA,ab)",
            "IX) item,SUBALINEA,IX)",
            "- item,ITEM_LISTA,-",
            "– item,ITEM_LISTA,–",
            "— item,ITEM_LISTA,—"
    })
    void marcadoresTextuaisIndependemDoTipoDeOrigem(String texto, Tipo esperado, String marcador) {
        assertClassificacao(esperado, marcador, "doc_title", texto, new ContextoClassificacao(-1, null));
    }

    /** Aceita título unido em caixa alta, mas não referências ou entradas de índice com hífen. */
    @Test
    void reconheceCapituloComTituloUnidoPeloMineru() {
        var contexto = new ContextoClassificacao(-1, null);
        assertClassificacao(CAPITULO, "CAPÍTULO 4", "paragraph_title",
                "CAPÍTULO 4 ADMINISTRAÇÃO DE RECURSOS", contexto);
        assertClassificacao(CAPITULO, "CAPITULO 7", "doc_title",
                "CAPITULO 7 DISPOSIÇÕES GERAIS", contexto);
        assertClassificacao(TEXTO, null, "paragraph_title", "CAPÍTULO 4. ADMINISTRAÇÃO DE RECURSOS", contexto);
        assertClassificacao(TEXTO, null, "text", "CAPÍTULO 4 - ADMINISTRAÇÃO DE RECURSOS", contexto);
        assertClassificacao(TEXTO, null, "text", "CAPÍTULO 4 Administração de recursos", contexto);
        assertClassificacao(TEXTO, null, "paragraph_title", "CAPÍTULO 4 ADMINISTRAÇÃO DE RECURSOS",
                contexto, List.of(0.1, 0.1, 0.3, 0.2));
    }

    /** Requer centralização e preserva a distinção entre aberturas e referências no corpo. */
    @Test
    void aberturaExigeTextoIsoladoECentralizado() {
        var contexto = new ContextoClassificacao(-1, null);
        assertClassificacao(CAPITULO, "CAPÍTULO 1", "text", "CAPÍTULO 1", contexto);
        assertClassificacao(TEXTO, null, "text", "CAPÍTULO 1 — continuação", contexto);
        assertClassificacao(TEXTO, null, "text", "CAPÍTULO 1", contexto, List.of(0.1, 0.1, 0.3, 0.2));
        assertClassificacao(ANEXO, "ANEXO A", "text", "ANEXO A", contexto);
        assertClassificacao(TEXTO, null, "text", "ANEXO A conteúdo", contexto);
        assertClassificacao(TEXTO, null, "text", "ANEXO A", contexto, List.of(0.1, 0.1, 0.3, 0.2));
    }

    /** Garante que reutilizar o classificador não carrega contexto entre chamadas. */
    @Test
    void chamadasComContextosDiferentesNaoCompartilhamEstado() {
        var primeiro = new ContextoClassificacao(1, null);
        var segundo = new ContextoClassificacao(2, null);
        assertClassificacao(ARTIGO, "1.3", "text", "1.3 Artigo", primeiro);
        assertClassificacao(TEXTO, null, "text", "1.3 Artigo", segundo);
        assertClassificacao(TITULO_ANEXO, null, "text", "Qualquer título", new ContextoClassificacao(-1, TITULO_ANEXO));
        assertClassificacao(TEXTO, null, "text", "Qualquer título", new ContextoClassificacao(-1, null));
    }

    /** Compara tipo e marcador usando a caixa centralizada padrão dos blocos. */
    private void assertClassificacao(Tipo tipo, String marcador, String tipoOrigem, String texto,
                                     ContextoClassificacao contexto) {
        assertClassificacao(tipo, marcador, tipoOrigem, texto, contexto, List.of(0.4, 0.1, 0.6, 0.2));
    }

    /** Permite verificar explicitamente as regras geométricas de abertura. */
    private void assertClassificacao(Tipo tipo, String marcador, String tipoOrigem, String texto,
                                     ContextoClassificacao contexto, java.util.List<Double> bbox) {
        assertEquals(new Classificacao(tipo, marcador),
                classificador.classificar(new BlocoClassificacao(texto, tipoOrigem, bbox), contexto));
    }
}
