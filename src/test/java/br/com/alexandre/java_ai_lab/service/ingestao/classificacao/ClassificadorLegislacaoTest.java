package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassificadorLegislacaoTest {
    private final ClassificadorLegislacao classificador = new ClassificadorLegislacao();

    /** Reconhece dispositivos e conserva a grafia e a pontuação dos seus marcadores originais. */
    @ParameterizedTest
    @CsvSource({
            "Art. 1º Disposição,ARTIGO,Art. 1º",
            "Art. 12. Disposição,ARTIGO,Art. 12.",
            "Art. 10-A. Disposição,ARTIGO,Art. 10-A.",
            "art. 1º-A. Disposição,ARTIGO,art. 1º-A.",
            "§ 1º Disposição,PARAGRAFO,§ 1º",
            "§ 10. Disposição,PARAGRAFO,§ 10.",
            "§ 1º-A Disposição,PARAGRAFO,§ 1º-A",
            "Parágrafo único. Disposição,PARAGRAFO,Parágrafo único.",
            "I - Disposição,INCISO,I -",
            "IV – Disposição,INCISO,IV –",
            "XXVI — Disposição,INCISO,XXVI —",
            "1. Disposição,ITEM,1.",
            "a) Disposição,ALINEA,a)"
    })
    void reconheceDispositivosSemExigirBbox(String texto, Tipo tipo, String marcador) {
        assertClassificacao(tipo, marcador, "text", texto);
    }

    /** Aceita marcadores isolados e quebras de linha como limite antes da prosa do dispositivo. */
    @Test
    void reconheceMarcadoresIsoladosOuAntesDeQuebraDeLinha() {
        assertClassificacao(ARTIGO, "Art. 1º", "text", "Art. 1º");
        assertClassificacao(PARAGRAFO, "§ 10.", "text", "§ 10.");
        assertClassificacao(INCISO, "IV –", "text", "IV –");
        assertClassificacao(ITEM, "1.", "text", "1.");
        assertClassificacao(ARTIGO, "Art. 12.", "text", "Art. 12.\nDisposição");
    }

    /** Títulos inline permanecem no texto de origem, fora do marcador da estrutura. */
    @ParameterizedTest
    @CsvSource({
            "CAPÍTULO I Disposições gerais,CAPITULO,CAPÍTULO I",
            "CAPITULO 12. Disposições gerais,CAPITULO,CAPITULO 12.",
            "Seção IV Dos direitos,SECAO,Seção IV",
            "SECAO 2 Dos direitos,SECAO,SECAO 2",
            "Subseção XXVI Das obrigações,SUBSECAO,Subseção XXVI",
            "SUBSECAO 1. Das obrigações,SUBSECAO,SUBSECAO 1."
    })
    void reconheceEstruturasComTituloInline(String texto, Tipo tipo, String marcador) {
        assertClassificacao(tipo, marcador, "doc_title", texto);
    }

    /** A origem explícita de tabela/equação prevalece sobre qualquer prefixo textual reconhecível. */
    @Test
    void conteudoEstruturadoPrevaleceSobreEstruturasEDispositivos() {
        assertClassificacao(TABELA, null, "table", "CAPÍTULO I Disposições gerais");
        assertClassificacao(EQUACAO, null, "equation", "Art. 1º Disposição");
        assertClassificacao(TABELA, null, "table", "a) Disposição");
        assertClassificacao(EQUACAO, null, "equation", "Brasília, 1º de janeiro de 2026.");
    }

    /** Restringe o fecho ao padrão de local e data, inclusive quando seguem dados da promulgação. */
    @Test
    void reconheceFechoExplicitoDeBrasilia() {
        assertClassificacao(FECHO, null, "text", "Brasília, 1º de janeiro de 2026.");
        assertClassificacao(FECHO, null, "text", "Brasília, 11 de novembro de 2026; 205º da Independência.");
    }

    /** Prefixos incompletos, citações e estruturas fora do escopo continuam sendo texto. */
    @ParameterizedTest
    @ValueSource(strings = {
            "Texto livre", "Art. 12.1 Disposição", "Art. 1ºabc Disposição", "Art. 10-A.B Disposição",
            "§ 1º-Aabc Disposição", "Parágrafo único.Disposição", "IV –Disposição", "iv - Disposição",
            "1.2.3 Disposição", "1.Disposição", "CAPÍTULO IVa Disposições", "Seção I.2 Disposições",
            "\"Art. 1º Disposição", "“§ 1º Disposição", "'I - Disposição", "LIVRO I", "PARTE I", "TÍTULO I",
            "Rio de Janeiro, 11 de novembro de 2026.", "Brasília, sede do Governo Federal.",
            "Brasília, 11 de novembro de 20260.", "Brasília, 11 de qualquer de 2026."
    })
    void preservaTextoQuandoNaoHaMarcadorCompleto(String texto) {
        assertClassificacao(TEXTO, null, "text", texto);
    }

    /** A classificação de um bloco não cria título pendente nem depende do bloco anterior. */
    @Test
    void chamadasNaoCompartilhamEstado() {
        assertClassificacao(CAPITULO, "CAPÍTULO I", "text", "CAPÍTULO I");
        assertClassificacao(TEXTO, null, "text", "Disposições gerais");
        assertClassificacao(INCISO, "I -", "text", "I - Disposição");
        assertClassificacao(ARTIGO, "Art. 1º", "text", "Art. 1º Disposição");
    }

    /**
     * Reconhece variantes de número, caixa e objeto sem exigir a lei da fixture ou uma posição.
     *
     * @param texto identificação ou ementa candidata
     * @param tipo classificação semântica esperada no cabeçalho
     */
    @ParameterizedTest
    @CsvSource(value = {
            "LEI Nº 14.129, DE 29 DE MARÇO DE 2021|IDENTIFICACAO_LEI",
            "Lei Complementar nº 95, de 26 de fevereiro de 1998.|IDENTIFICACAO_LEI",
            "LEI N° 250|IDENTIFICACAO_LEI",
            "Lei no. 123, de 2026|IDENTIFICACAO_LEI",
            "Dispõe sobre serviços públicos.|EMENTA",
            "Institui o programa de atendimento.|EMENTA",
            "Altera a Lei nº 1, de 2020.|EMENTA",
            "ESTABELECE DIRETRIZES PARA O SERVIÇO.|EMENTA"
    }, delimiter = '|')
    void reconheceCabecalhoSomenteQuandoPermitido(String texto, Tipo tipo) {
        var bloco = new BlocoClassificacao(texto, "p", List.of());
        assertEquals(new Classificacao(tipo, null), classificador.classificar(bloco, true));
        assertEquals(new Classificacao(TEXTO, null), classificador.classificar(bloco));
    }

    /** Origem estruturada e dispositivos prevalecem; texto introdutório desconhecido não é ementa. */
    @Test
    void cabecalhoNaoSubstituiOutrasClassificacoesNemQualquerTextoPreliminar() {
        assertEquals(new Classificacao(TABELA, null), classificador.classificar(
                new BlocoClassificacao("Dispõe sobre regras.", "table", List.of()), true));
        assertEquals(new Classificacao(ARTIGO, "Art. 1º"), classificador.classificar(
                new BlocoClassificacao("Art. 1º Institui o programa.", "p", List.of()), true));
        for (String texto : List.of("Vigência", "Mensagem de veto", "Instituição responsável", "Lei sem número")) {
            assertEquals(new Classificacao(TEXTO, null), classificador.classificar(
                    new BlocoClassificacao(texto, "p", List.of()), true));
        }
    }

    /** Confere tipo e marcador usando bbox ausente para todas as regras de legislação. */
    private void assertClassificacao(Tipo tipo, String marcador, String tipoOrigem, String texto) {
        assertEquals(new Classificacao(tipo, marcador),
                classificador.classificar(new BlocoClassificacao(texto, tipoOrigem, List.of())));
    }
}
