package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.BlocoMineru;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.ALINEA;
import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.TEXTO;
import static org.junit.jupiter.api.Assertions.*;

class TransicaoVolumeNormativoTest {
    private final ParserNormasService parser = new ParserNormasService();

    /** Retoma após uma divisória quando o marcador e o título do capítulo vêm no mesmo bloco. */
    @Test
    void reconheceAberturaDeCapituloComTituloNoMesmoBlocoAposDivisoria() {
        List<BlocoMineru> blocos = new ArrayList<>();
        adicionar(blocos, "text", 1, "PARTE", true, false);
        adicionar(blocos, "text", 1, "CAPÍTULO 8 - Apresentação", false, false);
        adicionar(blocos, "paragraph_title", 2,
                "CAPÍTULO 9 PRESTAÇÃO DE CONTAS DA EXECUÇÃO FINANCEIRA", true, false);
        adicionar(blocos, "paragraph_title", 2, "9.1. Responsabilidades", false, false);
        adicionar(blocos, "text", 2, "Primeiro texto do capítulo nove.", false, false);

        List<ElementoNormativo> elementos = parser.parse(blocos);

        assertEquals(1, elementos.size());
        ElementoNormativo texto = elementos.getFirst();
        assertEquals(TEXTO, texto.tipo());
        assertEquals("Primeiro texto do capítulo nove.", texto.texto());
        assertEquals("CAPÍTULO 9. PRESTAÇÃO DE CONTAS DA EXECUÇÃO FINANCEIRA", texto.pais().capitulo());
        assertEquals("9.1. Responsabilidades", texto.pais().artigo());
    }

    /** Ignora capa e divisória, limpa o contexto antigo e retoma somente no capítulo real seguinte. */
    @Test
    void ignoraFolhasPreliminaresSemPerderConteudoNemVazarPaisEntreVolumes() {
        List<BlocoMineru> blocos = new ArrayList<>();
        adicionar(blocos, "doc_title", 1, "CAPÍTULO 1", false, false);
        adicionar(blocos, "doc_title", 1, "GESTÃO PATRIMONIAL", false, false);
        adicionar(blocos, "paragraph_title", 1, "1.1. Organização", false, false);
        adicionar(blocos, "paragraph_title", 2, "a) Primeiro item", false, false);
        adicionar(blocos, "text", 2, "Detalhe do primeiro item", false, false);
        adicionar(blocos, "paragraph_title", 2, "b) Segundo item", false, false);
        adicionar(blocos, "text", 2, "Detalhe do segundo item", false, false);
        adicionar(blocos, "text", 2, "O VOLUME III também estabelece este procedimento.", false, false);

        adicionar(blocos, "text", 3, "MARINHA DO BRASIL", false, false);
        adicionar(blocos, "doc_title", 3, "INSTRUÇÕES GERAIS DE ADMINISTRAÇÃO", true, false);
        adicionar(blocos, "text", 3, "ESTADO-MAIOR DA ARMADA", false, false);
        adicionar(blocos, "text", 3, "VOLUME IX", false, false);
        adicionar(blocos, "text", 3, "3ª EDIÇÃO", false, false);
        adicionar(blocos, "text", 3, "2031", false, false);
        adicionar(blocos, "text", 3, "TIPO: MANUAL", false, false);
        adicionar(blocos, "text", 3, "FINALIDADE: DIDÁTICA", false, false);

        adicionar(blocos, "text", 4, "7ª PARTE", true, false);
        adicionar(blocos, "text", 4, "CAPÍTULO 11 - Descrição", false, false);
        adicionar(blocos, "text", 5, "INTENCIONALMENTE EM BRANCO", false, false);
        adicionar(blocos, "text", 6, "Texto preliminar que não pertence ao corpo normativo.", false, false);

        adicionar(blocos, "doc_title", 7, "CAPÍTULO 11", true, false);
        adicionar(blocos, "doc_title", 7, "DESCRIÇÃO", false, false);
        adicionar(blocos, "paragraph_title", 7, "11.1. Campo de aplicação", false, false);
        adicionar(blocos, "text", 7, "Conteúdo do capítulo 11.", false, false);

        List<ElementoNormativo> elementos = parser.parse(blocos);

        assertTrue(elementos.stream().noneMatch(e -> e.texto().contains("INSTRUÇÕES GERAIS DE ADMINISTRAÇÃO")
                || e.texto().contains("MARINHA DO BRASIL")
                || e.texto().contains("TIPO: MANUAL")
                || e.texto().contains("FINALIDADE: DIDÁTICA")
                || e.texto().contains("7ª PARTE")
                || e.texto().contains("CAPÍTULO 11 - Descrição")
                || e.texto().contains("Texto preliminar")));
        assertTrue(elementos.stream().flatMap(e -> e.origens().stream())
                .noneMatch(o -> o.paginaFisica() >= 3 && o.paginaFisica() <= 6),
                "nenhum bloco das folhas preliminares deve chegar à saída");

        ElementoNormativo itemB = elementos.stream()
                .filter(e -> e.origens().stream().anyMatch(o -> "b)".equals(o.marcador())))
                .findFirst().orElseThrow(() -> new AssertionError("a alínea b deve sobreviver à transição"));
        assertEquals(ALINEA, itemB.tipo());
        assertEquals("CAPÍTULO 1. GESTÃO PATRIMONIAL", itemB.pais().capitulo());
        assertEquals("1.1. Organização", itemB.pais().artigo());

        ElementoNormativo textoDoNovoCapitulo = elementos.stream()
                .filter(e -> e.tipo() == TEXTO && e.texto().contains("Conteúdo do capítulo 11"))
                .findFirst().orElseThrow();
        assertEquals("CAPÍTULO 11. DESCRIÇÃO", textoDoNovoCapitulo.pais().capitulo());
        assertEquals("11.1. Campo de aplicação", textoDoNovoCapitulo.pais().artigo());
        assertNotEquals("CAPÍTULO 1. GESTÃO PATRIMONIAL", textoDoNovoCapitulo.pais().capitulo());

        assertTrue(elementos.stream().anyMatch(e ->
                e.texto().contains("O VOLUME III também estabelece este procedimento.")),
                "menção a volume dentro do corpo não deve remover conteúdo");
        assertTrue(elementos.stream().noneMatch(e -> e.texto().contains("Texto preliminar")));
    }

    /** Cria bloco sintético com página física base 1 e geometria adequada ao tipo de cabeçalho. */
    private void adicionar(List<BlocoMineru> blocos, String origem, int pagina, String texto,
                           boolean centralizado, boolean continuaAnterior) {
        List<Double> caixa = centralizado
                ? List.of(0.45, 0.08, 0.60, 0.10)
                : List.of(0.14, 0.20, 0.80, 0.35);
        blocos.add(new BlocoMineru(origem, texto, pagina, null, blocos.size(), caixa, continuaAnterior));
    }
}
