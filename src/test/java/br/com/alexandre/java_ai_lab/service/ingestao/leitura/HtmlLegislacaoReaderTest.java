package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlLegislacaoReaderTest {
    private final HtmlLegislacaoReader reader = new HtmlLegislacaoReader();

    /** Lê fragmento Word sem descartar MsoHeader, decodifica entidades e preserva índices DOM. */
    @Test
    void extraiTextoAncorasEFormatacaoSemClassificar() {
        var blocos = reader.ler("""
                <p>Fora da área da lei</p>
                <div class="WordSection1">
                  <p>&nbsp;</p>
                  <p class="MsoHeader" align="center">CAPÍTULO I</p>
                  <p style="text-align: center">DISPOSIÇÕES GERAIS</p>
                  <p id="dispositivo"><a name="art1">Art. 1º</a>&nbsp;Texto <i>normativo</i> &amp; completo.</p>
                  <p><a name="art2">Art. 2º</a> Outra regra.</p>
                </div>
                """);

        assertThat(blocos).hasSize(4);
        assertThat(blocos.getFirst()).isEqualTo(new BlocoLegislacao("CAPÍTULO I", "p", 1, null, true));
        assertThat(blocos.get(1).tituloEstrutural()).isTrue();
        assertThat(blocos.get(2).texto()).isEqualTo("Art. 1º Texto normativo & completo.");
        assertThat(blocos.get(2).ancora()).isEqualTo("dispositivo");
        assertThat(blocos.get(3).ancora()).isEqualTo("art2");
    }

    /** A tabela é uma única origem; o parágrafo interno não é emitido pela segunda vez. */
    @Test
    void preservaTabelaSemDuplicarParagrafosEIdentificaBlockquote() {
        var blocos = reader.ler("""
                <h2>CAPÍTULO I</h2>
                <table><tr><td><p>Art. 99. Texto tabular.</p></td></tr></table>
                <blockquote><p>Art. 2º Texto citado.</p></blockquote>
                <p>Art. 1º Regra própria.</p>
                """);

        assertThat(blocos).hasSize(4);
        assertThat(blocos.getFirst().tituloEstrutural()).isTrue();
        assertThat(blocos.get(1).tipoOrigem()).isEqualTo("table");
        assertThat(blocos.get(1).texto()).contains("<table>", "Art. 99.");
        assertThat(blocos.get(2).tipoOrigem()).isEqualTo("citacao");
    }

    /** Exige blocos HTML suportados em vez de aceitar silenciosamente uma resposta vazia. */
    @Test
    void rejeitaEntradaVaziaOuSemBlocos() {
        assertThatThrownBy(() -> reader.ler(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.ler(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.ler("<div>Sem parágrafos</div>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
