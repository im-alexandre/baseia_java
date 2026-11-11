package br.com.alexandre.java_ai_lab.service.ingestao.filtragem;

import br.com.alexandre.java_ai_lab.service.ingestao.leitura.BlocoMineru;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentificadorPaginasPreliminaresTest {
    private final IdentificadorPaginasPreliminares identificador = new IdentificadorPaginasPreliminares();

    /** Reconhece capa de outra publicação naval por sinais institucionais e editoriais independentes. */
    @Test
    void identificaCapaDeOutraPublicacaoComCategoriasEditoriaisIndependentes() {
        List<BlocoMineru> blocos = new ArrayList<>();
        adicionar(blocos, 19, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 19, "INSTRUÇÕES GERAIS DE ADMINISTRAÇÃO", "doc_title", true);
        adicionar(blocos, 19, "ESTADO-MAIOR DA ARMADA", "text", false);
        adicionar(blocos, 19, "VOLUME IX", "text", false);
        adicionar(blocos, 19, "3ª EDIÇÃO", "text", false);
        adicionar(blocos, 19, "2031", "text", false);
        adicionar(blocos, 19, "TIPO: MANUAL", "text", false);
        adicionar(blocos, 19, "FINALIDADE: DIDÁTICA", "text", false);

        // Outra publicação pode trazer o título em caixa alta como texto comum do MinerU.
        adicionar(blocos, 20, "MARINHA DO BRASIL", false);
        adicionar(blocos, 20, "REGULAMENTO GERAL DE ADMINISTRAÇÃO NAVAL", true);
        adicionar(blocos, 20, "ESTADO-MAIOR DA ARMADA", false);
        adicionar(blocos, 20, "VOLUME IX", false);
        adicionar(blocos, 20, "3ª EDIÇÃO", false);
        adicionar(blocos, 20, "TIPO: MANUAL", false);
        adicionar(blocos, 20, "FINALIDADE: DIDÁTICA", false);

        assertEquals(Set.of(19, 20), identificador.identificar(blocos));
    }

    /** Reconhece divisórias com ordinal textual/LaTeX ou ausente, junto às entradas de capítulo. */
    @Test
    void identificaDivisoriasMesmoComOrdinalPerdidoPorOcr() {
        List<BlocoMineru> blocos = new ArrayList<>();
        adicionar(blocos, 20, "7ª PARTE", true);
        adicionar(blocos, 20, "CAPÍTULO 11 - Descrição", false);
        adicionar(blocos, 70, "PARTE", true);
        adicionar(blocos, 70, "CAPITULO 12 - Disposições gerais", false);
        adicionar(blocos, 92, "3^{a} PARTE", true);
        adicionar(blocos, 92, "CAPÍTULO 4 - Administração", false);

        assertEquals(Set.of(20, 70, 92), identificador.identificar(blocos));
    }

    /** Preserva abertura real unida a seu título, mas reconhece a folha de parte com preview. */
    @Test
    void distingueAberturaRealComMetadadosDaDivisoriaComPreview() {
        List<BlocoMineru> blocos = new ArrayList<>();
        adicionar(blocos, 80, "3ª PARTE", "text", true);
        adicionar(blocos, 80, "CAPÍTULO 12 - Descrição", "text", false);

        adicionar(blocos, 81, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 81, "CAPÍTULO 9 PRESTAÇÃO DE CONTAS DA EXECUÇÃO FINANCEIRA",
                "paragraph_title", true);
        adicionar(blocos, 81, "VOLUME IX", "text", false);
        adicionar(blocos, 81, "3ª EDIÇÃO", "text", false);
        adicionar(blocos, 81, "TIPO: MANUAL", "text", false);
        adicionar(blocos, 81, "FINALIDADE: NORMATIVA", "text", false);

        assertEquals(Set.of(80), identificador.identificar(blocos));
    }

    /** Não confunde menções no corpo, metadados únicos/repetidos ou capítulos reais com capas e divisórias. */
    @Test
    void exigeDuasCategoriasEditoriaisIndependentesNaMesmaPagina() {
        List<BlocoMineru> blocos = new ArrayList<>();
        // Volume no corpo e instituição, sem título editorial centralizado.
        adicionar(blocos, 30, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 30, "O VOLUME III apresenta este procedimento.", "text", false);

        // Título, instituição, ano, código e repetições de TIPO ainda formam só uma categoria.
        adicionar(blocos, 31, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 31, "INSTRUÇÕES ADMINISTRATIVAS", "doc_title", true);
        adicionar(blocos, 31, "ESTADO-MAIOR DA ARMADA", "text", false);
        adicionar(blocos, 31, "2031", "text", false);
        adicionar(blocos, 31, "SGM-301", "text", false);
        adicionar(blocos, 31, "TIPO: NORMA", "text", false);
        adicionar(blocos, 31, "TIPO: MANUAL", "text", false);

        // Repetir cada sinal da mesma categoria não o converte em duas categorias.
        adicionar(blocos, 32, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 32, "PUBLICAÇÃO DE SERVIÇO", "paragraph_title", true);
        adicionar(blocos, 32, "VOLUME I", "text", false);
        adicionar(blocos, 32, "VOLUME II", "text", false);

        adicionar(blocos, 33, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 33, "PUBLICAÇÃO DE SERVIÇO", "doc_title", true);
        adicionar(blocos, 33, "3ª EDIÇÃO", "text", false);
        adicionar(blocos, 33, "4ª EDIÇÃO", "text", false);

        adicionar(blocos, 34, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 34, "PUBLICAÇÃO DE SERVIÇO", "doc_title", true);
        adicionar(blocos, 34, "FINALIDADE: INFORMATIVA", "text", false);
        adicionar(blocos, 34, "FINALIDADE: DIDÁTICA", "text", false);

        adicionar(blocos, 35, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 35, "PUBLICAÇÃO DE SERVIÇO", "doc_title", true);
        adicionar(blocos, 35, "TIPO: MANUAL", "text", false);
        adicionar(blocos, 35, "TIPO: NORMA", "text", false);

        // Um capítulo isolado e real impede o corte da página que também contém cabeçalho de parte.
        adicionar(blocos, 36, "PARTE", "text", true);
        adicionar(blocos, 36, "CAPÍTULO 11", "doc_title", true);
        adicionar(blocos, 36, "CAPÍTULO 11 - Descrição", "text", false);

        // A abertura real do capítulo prevalece mesmo quando a página reúne dados editoriais.
        adicionar(blocos, 37, "MARINHA DO BRASIL", "text", false);
        adicionar(blocos, 37, "CAPÍTULO 2", "doc_title", true);
        adicionar(blocos, 37, "GESTÃO ADMINISTRATIVA", "paragraph_title", true);
        adicionar(blocos, 37, "VOLUME IV", "text", false);
        adicionar(blocos, 37, "2ª EDIÇÃO", "text", false);

        // Cabeçalhos e rodapés repetidos no corpo não contam como metadados editoriais de capa.
        adicionar(blocos, 38, "MARINHA DO BRASIL", "header", false);
        adicionar(blocos, 38, "VOLUME IV", "header", false);
        adicionar(blocos, 38, "2ª EDIÇÃO", "footer", false);
        adicionar(blocos, 38, "GESTÃO ADMINISTRATIVA", "paragraph_title", true);

        assertEquals(Set.of(), identificador.identificar(blocos));
    }

    /** Cria bloco com tipo MinerU explícito e centralização horizontal configurável. */
    private void adicionar(List<BlocoMineru> blocos, int pagina, String texto, boolean centralizado) {
        adicionar(blocos, pagina, texto, "text", centralizado);
    }

    /** Cria bloco tipado para permitir testar o requisito de heading centralizado. */
    private void adicionar(List<BlocoMineru> blocos, int pagina, String texto,
                           String tipoOrigem, boolean centralizado) {
        List<Double> caixa = centralizado
                ? List.of(0.43, 0.40, 0.57, 0.60)
                : List.of(0.12, 0.30, 0.38, 0.50);
        blocos.add(new BlocoMineru(tipoOrigem, texto, pagina, null, blocos.size(), caixa, false));
    }
}
