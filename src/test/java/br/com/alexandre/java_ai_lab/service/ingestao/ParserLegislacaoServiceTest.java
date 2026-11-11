package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.HtmlLegislacaoReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserLegislacaoServiceTest {
    private final HtmlLegislacaoReader reader = new HtmlLegislacaoReader();
    private final ParserLegislacaoService parser = new ParserLegislacaoService();

    /** Verifica toda a cadeia, irmãos e limpeza de contexto, inclusive inciso direto sob artigo. */
    @Test
    void montaPaisOpcionaisSemHerdarIrmaosOuDescendentesAnteriores() {
        var elementos = parse("""
                <p>Art. 1º A regra é:</p>
                <p>I - inciso direto;</p>
                <p>§ 1º Regras do parágrafo:</p>
                <p>I – inciso do parágrafo:</p>
                <p>a) alínea:</p>
                <p>1. primeiro item;</p>
                <p>Texto complementar.</p>
                <p>2. segundo item;</p>
                <p>b) outra alínea.</p>
                <p>§ 2º Outro parágrafo.</p>
                <p>Art. 2º Nova regra.</p>
                <p>II — outro inciso direto.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo).containsExactly(
                ARTIGO, INCISO, PARAGRAFO, INCISO, ALINEA, LISTA, TEXTO, LISTA, ALINEA, PARAGRAFO, ARTIGO, INCISO);
        assertThat(elementos.get(1).pais().artigo()).isEqualTo("Art. 1º A regra é:");
        assertThat(elementos.get(1).pais().paragrafo()).isNull();
        assertThat(elementos.get(2).pais().inciso()).isNull();
        assertThat(elementos.get(3).pais().paragrafo()).isEqualTo("§ 1º Regras do parágrafo:");
        assertThat(elementos.get(5).pais().alinea()).isEqualTo("a) alínea:");
        assertThat(elementos.get(5).pais().item()).isNull();
        assertThat(elementos.get(6).pais().item()).isEqualTo("1. primeiro item;");
        assertThat(elementos.get(7).pais().item()).isNull();
        assertThat(elementos.get(8).pais().alinea()).isNull();
        assertThat(elementos.get(8).pais().item()).isNull();
        assertThat(elementos.get(9).pais().inciso()).isNull();
        assertThat(elementos.get(10).pais()).isEqualTo(PaisLegislacao.vazios());
        assertThat(elementos.get(11).pais().artigo()).isEqualTo("Art. 2º Nova regra.");
        assertThat(elementos.get(11).pais().paragrafo()).isNull();
        assertThat(elementos.get(11).pais().alinea()).isNull();
    }

    /** Mantém estruturas só nos pais; uma seção/capítulo novo limpa os contextos inferiores. */
    @Test
    void reuneTitulosComOrigemEReiniciaSecoesSubsecoesECapitulos() {
        var elementos = parse("""
                <p align="center">CAPÍTULO I</p><p align="center">GERAL</p>
                <h2>Seção I</h2><h3>PRIMEIRA</h3>
                <h3>Subseção I</h3><h4>DETALHES</h4>
                <p>Art. 1º Primeiro.</p><p>Parágrafo único. Complemento.</p>
                <h2>Seção II</h2><p>Art. 2º Segundo.</p>
                <h2>CAPÍTULO II</h2><p>Art. 3º Terceiro.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(ARTIGO, PARAGRAFO, ARTIGO, ARTIGO);
        assertThat(elementos.getFirst().indicesOrigem()).containsExactly(6);
        assertThat(elementos.getFirst().pais().capitulo()).isEqualTo("CAPÍTULO I. GERAL");
        assertThat(elementos.getFirst().pais().secao()).isEqualTo("Seção I. PRIMEIRA");
        assertThat(elementos.getFirst().pais().subsecao()).isEqualTo("Subseção I. DETALHES");
        assertThat(elementos.get(2).pais().secao()).isEqualTo("Seção II");
        assertThat(elementos.get(2).pais().subsecao()).isNull();
        assertThat(elementos.get(2).pais().paragrafo()).isNull();
        assertThat(elementos.get(3).pais().capitulo()).isEqualTo("CAPÍTULO II");
        assertThat(elementos.get(3).pais().secao()).isNull();
    }

    /** Classifica a hierarquia citada sem substituir o artigo introdutor da própria lei. */
    @Test
    void preservaCitacoesEntreAspasEBlockquoteSemContaminarDispositivosSeguintes() {
        var elementos = parse("""
                <p>Art. 51. Altera outra lei:</p>
                <p>“Art. 12. Nova redação:</p><p>§ 1º Texto citado.</p><p>a) final.” (NR)</p>
                <p>Art. 52. Outra alteração:</p>
                <blockquote><p>“Art. 10-A. Redação sem aspas finais no blockquote.</p><p>§ 2º Complemento.</p></blockquote>
                <p>Art. 53. Dispositivo próprio.</p>
                <p>I - inciso próprio.</p>
                """);

        assertThat(elementos).filteredOn(e -> e.tipo() == ARTIGO && !e.citacao())
                .extracting(ElementoLegislacao::marcador).containsExactly("Art. 51.", "Art. 52.", "Art. 53.");
        assertThat(elementos.subList(1, 4)).extracting(ElementoLegislacao::tipo)
                .containsExactly(ARTIGO, PARAGRAFO, ALINEA);
        assertThat(elementos.subList(1, 4)).allSatisfy(e -> {
            assertThat(e.citacao()).isTrue();
            assertThat(e.pais().artigo()).isEqualTo("Art. 51. Altera outra lei:");
        });
        assertThat(elementos.get(1).pais().artigoCitado()).isNull();
        assertThat(elementos.get(2).pais().artigoCitado()).isEqualTo("Art. 12. Nova redação:");
        assertThat(elementos.get(3).pais().paragrafo()).isEqualTo("§ 1º Texto citado.");
        assertThat(elementos.getLast().pais().artigo()).isEqualTo("Art. 53. Dispositivo próprio.");
        assertThat(elementos.getLast().pais().paragrafo()).isNull();
        assertThat(elementos.getLast().citacao()).isFalse();
        assertThat(elementos.getLast().pais().artigoCitado()).isNull();
    }

    /** Confere a fixture original, conteúdo útil, contextos e exclusões sem regenerar o arquivo. */
    @Test
    void leLei14129Com55ArtigosECitacoesSobSeusArtigosDeAlteracao() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/ingestao/legislacao/lei_14129_2021.html")) {
            assertThat(entrada).isNotNull();
            var blocos = reader.ler(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            var elementos = parser.parse(blocos);
            var artigos = elementos.stream().filter(e -> e.tipo() == ARTIGO && !e.citacao()).toList();
            assertThat(artigos).hasSize(55);
            for (int indice = 0; indice < artigos.size(); indice++) {
                assertThat(artigos.get(indice).ancora()).isEqualTo("art" + (indice + 1));
            }
            assertThat(elementos).extracting(ElementoLegislacao::tipo)
                    .doesNotContain(CAPITULO, SECAO, SUBSECAO, FECHO, CITACAO);
            assertThat(artigos.stream().map(e -> e.pais().capitulo()).distinct()).hasSize(8);
            assertThat(artigos).extracting(e -> e.pais().subsecao()).contains("Subseção I. Da Definição");
            assertThat(elementos).hasSize(275);
            assertThat(elementos.get(0).tipo()).isEqualTo(IDENTIFICACAO_LEI);
            assertThat(elementos.get(0).texto()).isEqualTo("LEI Nº 14.129, DE 29 DE MARÇO DE 2021");
            assertThat(elementos.get(1).tipo()).isEqualTo(EMENTA);
            assertThat(elementos.get(1).texto()).startsWith("Dispõe sobre princípios");
            assertThat(elementos).filteredOn(ElementoLegislacao::citacao).hasSize(14).allSatisfy(e -> {
                assertThat(e.pais().artigo()).matches("Art\\. 5[1-4]\\. .+");
                assertThat(e.texto()).doesNotContain("...", "…");
            });
            assertThat(elementos).allSatisfy(e -> {
                assertThat(e.texto()).doesNotContain("CÂMARA DOS DEPUTADOS", "Centro de Documentação", "Faço saber",
                        "O PRESIDENTE DA REPÚBLICA", "Brasília, 29", "JAIR MESSIAS BOLSONARO", "...", "…");
                assertThat(e.indicesOrigem()).containsExactlyElementsOf(
                        e.origens().stream().flatMap(o -> o.indicesOrigem().stream()).toList());
            });
            assertThat(elementos.getLast().texto()).startsWith("III - 180");
        }
    }

    /** Não compartilha estado entre chamadas e rejeita texto sem artigo reconhecido. */
    @Test
    void isolaChamadasERejeitaDocumentoSemArtigos() {
        parse("<p>CAPÍTULO I</p><p>Art. 1º Regra.</p><p>§ 1º Parágrafo.</p>");
        assertThat(parse("<p>Art. 2º Outra lei.</p>").getFirst().pais()).isEqualTo(PaisLegislacao.vazios());
        assertThatThrownBy(() -> parse("<p>Documento sem artigos.</p>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Omissões podem abrir um artigo citado, mas não geram conteúdo nem contaminam a próxima citação. */
    @Test
    void removePontilhadosEPreservaMarcadoresApenasComoContextoDasCitacoes() {
        var elementos = parse("""
                <p>Art. 51. Altera a lei:</p>
                <p>"Art. 3º .....................................</p><p>..................................</p>
                <p>g) conteúdo útil;</p><p>§ 1º primeiro parágrafo." (NR)</p>
                <p>"Art. 7º ………</p><p>§ 6º outra redação." (NR)</p>
                <p>Art. 52. Regra seguinte.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(ARTIGO, ALINEA, PARAGRAFO, PARAGRAFO, ARTIGO);
        assertThat(elementos.get(1).pais().artigoCitado()).isEqualTo("Art. 3º");
        assertThat(elementos.get(2).pais().artigoCitado()).isEqualTo("Art. 3º");
        assertThat(elementos.get(3).pais().artigoCitado()).isEqualTo("Art. 7º");
        assertThat(elementos.get(3).pais().alinea()).isNull();
        assertThat(elementos.getLast().pais().artigoCitado()).isNull();
        assertThat(elementos).allSatisfy(e -> assertThat(e.texto()).doesNotContain("...", "…"));
    }

    /** Os filtros atuam no preâmbulo e no fecho; termos institucionais no corpo continuam úteis. */
    @Test
    void removeCabecalhoFormulaFechoEAssinaturasPreservandoIdentificacaoEEmenta() {
        var elementos = parse("""
                <p>CÂMARA DOS DEPUTADOS</p><p>Centro de Documentação e Informação</p>
                <p>LEI Nº 1, DE 2026</p><p>Dispõe sobre o atendimento.</p>
                <p>O PRESIDENTE DA REPÚBLICA</p>
                <p>Faço saber que o Congresso Nacional decreta e eu sanciono a seguinte Lei:</p>
                <p>Art. 1º A Câmara dos Deputados deverá atuar.</p>
                <p>O PRESIDENTE DA REPÚBLICA</p>
                <p>Brasília, 29 de março de 2021; 200º da Independência.</p>
                <p>JAIR MESSIAS BOLSONARO</p><p>Nome do Ministro</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::texto).containsExactly(
                "LEI Nº 1, DE 2026", "Dispõe sobre o atendimento.",
                "Art. 1º A Câmara dos Deputados deverá atuar.", "O PRESIDENTE DA REPÚBLICA");
        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(IDENTIFICACAO_LEI, EMENTA, ARTIGO, TEXTO);
    }

    /**
     * Cabeçalhos de outras leis independem da posição DOM; preserva âncoras e origens individuais.
     *
     * @param blocosAnteriores quantidade de blocos vazios que desloca a posição de origem
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 3, 17})
    void classificaIdentificacaoEEmentaSemDependerDosIndices(int blocosAnteriores) {
        var elementos = parse("<p></p>".repeat(blocosAnteriores) + """
                <h1 id="lei">LEI COMPLEMENTAR Nº 250, DE 10 DE JANEIRO DE 2026</h1>
                <p id="ementa">Institui o programa de atendimento.</p>
                <p>O PRESIDENTE DA REPÚBLICA</p>
                <p>Art. 1º Regra.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(IDENTIFICACAO_LEI, EMENTA, ARTIGO);
        assertThat(elementos.get(0).texto()).isEqualTo("LEI COMPLEMENTAR Nº 250, DE 10 DE JANEIRO DE 2026");
        assertThat(elementos.get(1).texto()).isEqualTo("Institui o programa de atendimento.");
        for (int indice = 0; indice < 2; indice++) {
            var elemento = elementos.get(indice);
            assertThat(elemento.indicesOrigem()).containsExactly(blocosAnteriores + indice);
            assertThat(elemento.pais()).isEqualTo(PaisLegislacao.vazios());
            assertThat(elemento.citacao()).isFalse();
            assertThat(elemento.marcador()).isNull();
            assertThat(elemento.origens()).hasSize(1);
            assertThat(elemento.origens().getFirst().tipo()).isEqualTo(elemento.tipo());
            assertThat(elemento.origens().getFirst().indicesOrigem()).isEqualTo(elemento.indicesOrigem());
            assertThat(elemento.origens().getFirst().ancora()).isEqualTo(elemento.ancora());
        }
        assertThat(elementos.get(0).ancora()).isEqualTo("lei");
        assertThat(elementos.get(1).ancora()).isEqualTo("ementa");
    }

    /** Expressões de cabeçalho no corpo e em citações continuam sendo prosa dos pais ativos. */
    @Test
    void naoClassificaReferenciasNoCorpoOuCitacoesComoCabecalho() {
        var elementos = parse("""
                <p>LEI Nº 1, DE 2026</p><p>Altera as regras de atendimento.</p>
                <blockquote><p>LEI Nº 2, DE 2025</p><p>Dispõe sobre outra regra.</p></blockquote>
                <p>CAPÍTULO I</p><p>Altera as condições anteriores.</p>
                <p>Art. 1º Referências:</p>
                <p>LEI Nº 3, DE 2024</p><p>Institui outra regra.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(IDENTIFICACAO_LEI, EMENTA, TEXTO, TEXTO, ARTIGO, TEXTO);
        assertThat(elementos.get(2).citacao()).isTrue();
        assertThat(elementos.get(2).texto()).isEqualTo("LEI Nº 2, DE 2025\nDispõe sobre outra regra.");
        assertThat(elementos.get(3).pais().capitulo()).isEqualTo("CAPÍTULO I");
        assertThat(elementos.getLast().texto()).isEqualTo("LEI Nº 3, DE 2024\nInstitui outra regra.");
        assertThat(elementos.getLast().pais().artigo()).isEqualTo("Art. 1º Referências:");
    }

    /**
     * Aspas internas balanceadas no fim de um parágrafo não encerram a redação citada.
     *
     * @param abertura delimitador externo e interno usado no exemplo, ASCII ou tipográfico
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"", "“"})
    void preservaAspasInternasEContinuaHierarquiaDaCitacao(String abertura) {
        String fechamento = abertura.equals("“") ? "”" : "\"";
        var elementos = parse("""
                <p>Art. 51. Altera a lei:</p>
                <p>%sArt. 12. Redação:</p>
                <p>§ 1º termo %sinterno%s</p>
                <p>Continuação do parágrafo.</p>
                <p>§ 2º Final da redação.%s (NR)</p>
                <p>Art. 52. Próxima regra.</p>
                """.formatted(abertura, abertura, fechamento, fechamento));

        assertThat(elementos).extracting(ElementoLegislacao::tipo)
                .containsExactly(ARTIGO, ARTIGO, PARAGRAFO, TEXTO, PARAGRAFO, ARTIGO);
        assertThat(elementos.get(2).texto()).isEqualTo("§ 1º termo " + abertura + "interno" + fechamento);
        assertThat(elementos.get(3).citacao()).isTrue();
        assertThat(elementos.get(3).pais().artigo()).isEqualTo("Art. 51. Altera a lei:");
        assertThat(elementos.get(3).pais().artigoCitado()).isEqualTo("Art. 12. Redação:");
        assertThat(elementos.get(3).pais().paragrafo()).isEqualTo(elementos.get(2).texto());
        assertThat(elementos.get(4).texto()).isEqualTo("§ 2º Final da redação.");
        assertThat(elementos.getLast().citacao()).isFalse();
        assertThat(elementos.getLast().pais().artigoCitado()).isNull();
    }

    /** O fecho documental encerra a leitura mesmo se uma redação anterior perdeu suas aspas finais. */
    @Test
    void excluiFechoEAssinaturasMesmoComCitacaoTextualIncompleta() {
        var elementos = parse("""
                <p>Art. 51. Altera a lei:</p>
                <p>“Art. 12. Redação sem fechamento.</p>
                <p>Brasília, 29 de março de 2021; 200º da Independência.</p>
                <p>JAIR MESSIAS BOLSONARO</p><p>Nome do Ministro</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::texto).containsExactly(
                "Art. 51. Altera a lei:", "Art. 12. Redação sem fechamento.");
        assertThat(elementos.getLast().citacao()).isTrue();
    }

    /** Uma data dentro do contêiner explícito de citação não é o fecho da lei que a introduz. */
    @Test
    void preservaDataCitadaEmBlockquoteEEncerraSomenteNoFechoDaPropriaLei() {
        var elementos = parse("""
                <p>Art. 51. Reproduz a lei:</p>
                <blockquote><p>“Art. 12. Regra citada:</p>
                <p>Brasília, 1º de janeiro de 2020.</p></blockquote>
                <p>Art. 52. Regra da própria lei.</p>
                <p>Brasília, 29 de março de 2021.</p><p>Assinatura</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo).containsExactly(ARTIGO, ARTIGO, TEXTO, ARTIGO);
        assertThat(elementos.get(2).texto()).isEqualTo("Brasília, 1º de janeiro de 2020.");
        assertThat(elementos.get(2).citacao()).isTrue();
        assertThat(elementos.get(2).pais().artigoCitado()).isEqualTo("Art. 12. Regra citada:");
        assertThat(elementos.getLast().citacao()).isFalse();
    }

    /** Uma data na transcrição entre aspas permanece citada quando o fechamento é explícito adiante. */
    @Test
    void preservaDataEmCitacaoTextualComFechamentoAntesDoProximoArtigo() {
        var elementos = parse("""
                <p id="art51">Art. 51. Transcreve a lei:</p>
                <p>“Art. 12. Texto da outra lei.</p>
                <p>Brasília, 1º de janeiro de 2020.</p>
                <p>Fim da transcrição.” (NR)</p>
                <p id="art52">Art. 52. Regra própria.</p>
                <p>Brasília, 29 de março de 2021.</p><p>Assinatura</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo).containsExactly(ARTIGO, ARTIGO, TEXTO, ARTIGO);
        assertThat(elementos.get(2).texto()).isEqualTo("Brasília, 1º de janeiro de 2020.\nFim da transcrição.");
        assertThat(elementos.get(2).citacao()).isTrue();
        assertThat(elementos.get(2).pais().artigoCitado()).isEqualTo("Art. 12. Texto da outra lei.");
        assertThat(elementos.getLast().marcador()).isEqualTo("Art. 52.");
        assertThat(elementos.getLast().citacao()).isFalse();
    }

    /** Uma âncora de artigo real evita propagar aspas incompletas para o restante da própria lei. */
    @Test
    void artigoAncoradoEncerraCitacaoComAspasInternasIncompletas() {
        var elementos = parse("""
                <p id="art51">Art. 51. Altera a lei:</p>
                <p>“Art. 12. Texto com “aspa interna incompleta.”</p>
                <p id="art52">Art. 52. Regra própria.</p>
                <p>I - inciso da própria lei.</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::citacao).containsExactly(false, true, false, false);
        assertThat(elementos.getLast().pais().artigo()).isEqualTo("Art. 52. Regra própria.");
        assertThat(elementos.getLast().pais().artigoCitado()).isNull();
    }

    /** Itens omitidos são barreiras internas e não reaparecem como listas de marcadores vazios. */
    @Test
    void removeItensApenasPontilhadosSemUnirOsGruposQueElesSeparam() {
        var elementos = parse("""
                <p>Art. 1º Os requisitos são:</p><p>a) documentos:</p>
                <p>1. primeiro documento;</p><p>2. . . . . .</p><p>3. ………</p>
                <p>4. quarto documento.</p><p>5. ............</p>
                """);

        assertThat(elementos).extracting(ElementoLegislacao::tipo).containsExactly(ARTIGO, ALINEA, LISTA, LISTA);
        assertThat(elementos).extracting(ElementoLegislacao::texto).containsExactly(
                "Art. 1º Os requisitos são:", "a) documentos:", "1. primeiro documento;", "4. quarto documento.");
        assertThat(elementos.get(2).indicesOrigem()).containsExactly(2);
        assertThat(elementos.get(3).indicesOrigem()).containsExactly(5);
        assertThat(elementos.get(3).origens().getFirst().marcador()).isEqualTo("4.");
    }

    /**
     * Exercita o pipeline puro a partir de um fragmento HTML, sem download ou contexto Spring.
     *
     * @param html fragmento de legislação usado pelo cenário do teste
     * @return conteúdo selecionado, classificado e enriquecido com pais
     */
    private List<ElementoLegislacao> parse(String html) {
        return parser.parse(reader.ler(html));
    }
}
