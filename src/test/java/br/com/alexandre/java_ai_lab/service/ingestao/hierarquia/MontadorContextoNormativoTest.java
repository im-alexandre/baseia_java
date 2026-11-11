package br.com.alexandre.java_ai_lab.service.ingestao.hierarquia;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MontadorContextoNormativoTest {
    private final MontadorContextoNormativo montador = new MontadorContextoNormativo();

    /** Atribui os pais ativos aos descendentes sem incluir o próprio marcador na sua hierarquia. */
    @Test
    void associaCapituloArtigoIncisoETextoInlineSemVincularNumeracaoIncompativel() {
        ElementoNormativo capitulo = elemento(CAPITULO, "CAPÍTULO 1", "CAPÍTULO 1. DISPOSIÇÕES", 1);
        ElementoNormativo artigo = elemento(ARTIGO, "1.1", "Art. 1.1. Regra", 1);
        ElementoNormativo inciso = elemento(INCISO, "1.1.1", "1.1.1. Aplicar a regra", 1);
        ElementoNormativo textoInline = elemento(TEXTO, null, "1.1.2. Texto reconhecido como conteúdo", 1);
        ElementoNormativo artigoComInciso = elemento(ARTIGO, "1.2", "Art. 1.2. Segunda regra", 1);
        ElementoNormativo incisoComTexto = elemento(INCISO, "1.2.1", "1.2.1. Execução Financeira", 1);
        ElementoNormativo textoDiretoDoInciso = elemento(TEXTO, null, "Complemento do inciso", 2);
        ElementoNormativo artigoIncompativel = elemento(ARTIGO, "2.1", "Art. 2.1. Outra regra", 1);
        ElementoNormativo incisoIncompativel = elemento(INCISO, "2.2.1", "2.2.1. Outro inciso", 1);
        ElementoNormativo capituloIncompativel = elemento(CAPITULO, "CAPÍTULO 3", "CAPÍTULO 3. FINAL", 2);
        ElementoNormativo artigoSemCapituloCorrespondente = elemento(ARTIGO, "2.1", "Art. 2.1. Final", 2);

        List<ElementoNormativo> resultado = montador.montar(List.of(capitulo, artigo, inciso, textoInline,
                artigoComInciso, incisoComTexto, textoDiretoDoInciso, artigoIncompativel,
                incisoIncompativel, capituloIncompativel, artigoSemCapituloCorrespondente));

        assertEquals(List.of(
                pais(null, null, null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", null, null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Aplicar a regra", null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", null, null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.2. Segunda regra", null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.2. Segunda regra", "1.2.1. Execução Financeira", null, null, null),
                pais(null, null, null, null, null, null),
                pais(null, null, null, null, null, null),
                pais(null, null, null, null, null, null),
                pais(null, null, null, null, null, null)
        ), paises(resultado));
        assertEquals("1.1.2. Texto reconhecido como conteúdo", resultado.get(3).texto());
        assertEquals("Complemento do inciso", resultado.get(6).texto());
    }

    /** Limpa descendentes anteriores ao trocar de alínea e zera os níveis inferiores do novo ramo. */
    @Test
    void redefineAlineaSubalineaEIrmaSemHerdarDescendentes() {
        List<ElementoNormativo> resultado = montador.montar(List.of(
                elemento(CAPITULO, "CAPÍTULO 1", "CAPÍTULO 1. DISPOSIÇÕES", 1),
                elemento(ARTIGO, "1.1", "Art. 1.1. Regra", 1),
                elemento(INCISO, "1.1.1", "1.1.1. Inciso", 1),
                elemento(ALINEA, "a)", "a) Primeira alínea", 1),
                elemento(SUBALINEA, "I)", "I) Subalínea", 1),
                elemento(ALINEA, "b)", "b) Alínea irmã", 2),
                elemento(TEXTO, null, "Continuação da alínea irmã", 2),
                elemento(SUBALINEA, "II)", "II) Nova subalínea", 2)
        ));

        assertEquals(List.of(
                pais(null, null, null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", null, null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", null, null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Inciso", null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Inciso", "a) Primeira alínea", null, null),
                // A alínea b) não é filha da alínea a); somente os elementos seguintes herdam b).
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Inciso", null, null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Inciso", "b) Alínea irmã", null, null),
                pais("CAPÍTULO 1. DISPOSIÇÕES", "Art. 1.1. Regra", "1.1.1. Inciso", "b) Alínea irmã", null, null)
        ), paises(resultado));
    }

    /** Mantém os níveis atuais através de páginas e em conteúdo de tabela, equação e lista. */
    @Test
    void conteudosEQuebrasDePaginaHerdamHierarquiaAtiva() {
        List<ElementoNormativo> resultado = montador.montar(List.of(
                elemento(CAPITULO, "CAPÍTULO 4", "CAPÍTULO 4. CONTAS", 1),
                elemento(ARTIGO, "4.1", "Art. 4.1. Descrição", 1),
                elemento(INCISO, "4.1.1", "4.1.1. Inciso", 1),
                elemento(ALINEA, "a)", "a) Critério", 1),
                elemento(TABELA, null, "<table/>", 2),
                elemento(EQUACAO, null, "x = y", 2),
                elemento(ITEM_LISTA, "1.", "1. Item", 3),
                elemento(TEXTO, null, "Texto posterior", 3)
        ));
        PaisNormativos esperado = pais("CAPÍTULO 4. CONTAS", "Art. 4.1. Descrição",
                "4.1.1. Inciso", "a) Critério", null, null);

        // Há quatro conteúdos depois da alínea; cada um herda o mesmo contexto completo.
        assertEquals(List.of(PaisNormativos.vazios(),
                pais("CAPÍTULO 4. CONTAS", null, null, null, null, null),
                pais("CAPÍTULO 4. CONTAS", "Art. 4.1. Descrição", null, null, null, null),
                pais("CAPÍTULO 4. CONTAS", "Art. 4.1. Descrição", "4.1.1. Inciso", null, null, null),
                pais("CAPÍTULO 4. CONTAS", "Art. 4.1. Descrição", "4.1.1. Inciso", "a) Critério", null, null),
                esperado, esperado, esperado), paises(resultado));
    }

    /** Permite alínea diretamente sob artigo e não inventa um inciso como seu pai. */
    @Test
    void associaAlineaDiretamenteAoArtigoSemCriarIncisoParaTextoSeguinte() {
        List<ElementoNormativo> resultado = montador.montar(List.of(
                elemento(CAPITULO, "CAPÍTULO 5", "CAPÍTULO 5. DISPOSIÇÕES", 1),
                elemento(ARTIGO, "5.1", "Art. 5.1. Procedimento", 1),
                elemento(ALINEA, "a)", "a) Condição", 1),
                elemento(TEXTO, null, "Detalhe da condição", 2)
        ));

        // A hierarquia usa só os níveis efetivamente vistos: o inciso fica ausente nos dois elementos.
        assertEquals(List.of(
                PaisNormativos.vazios(),
                pais("CAPÍTULO 5. DISPOSIÇÕES", null, null, null, null, null),
                pais("CAPÍTULO 5. DISPOSIÇÕES", "Art. 5.1. Procedimento", null, null, null, null),
                pais("CAPÍTULO 5. DISPOSIÇÕES", "Art. 5.1. Procedimento", null, "a) Condição", null, null)
        ), paises(resultado));
    }

    /** Usa o marcador no item ANEXO e o rótulo completo depois do título, até uma nova raiz. */
    @Test
    void associaAnexoAoTituloEConteudoEReiniciaNaProximaRaiz() {
        List<ElementoNormativo> resultado = montador.montar(List.of(
                elemento(CAPITULO, "CAPÍTULO 2", "CAPÍTULO 2. CONTAS", 1),
                elemento(ARTIGO, "2.1", "Art. 2.1. Regra", 1),
                elemento(INCISO, "2.1.1", "2.1.1. Inciso", 1),
                elemento(ANEXO, "ANEXO A", "ANEXO A", 4),
                elemento(TITULO_ANEXO, null, "TABELA DE CONTAS", 4),
                elemento(TABELA, null, "<table/>", 5),
                elemento(ANEXO, "ANEXO B", "ANEXO B", 6),
                elemento(TEXTO, null, "Conteúdo do anexo B", 6),
                elemento(CAPITULO, "CAPÍTULO 3", "CAPÍTULO 3. FINAL", 7),
                elemento(TEXTO, null, "Conteúdo do capítulo", 7)
        ));

        assertEquals(List.of(
                pais(null, null, null, null, null, null),
                pais("CAPÍTULO 2. CONTAS", null, null, null, null, null),
                pais("CAPÍTULO 2. CONTAS", "Art. 2.1. Regra", null, null, null, null),
                pais(null, null, null, null, null, null),
                pais(null, null, null, null, null, "ANEXO A"),
                pais(null, null, null, null, null, "ANEXO A. TABELA DE CONTAS"),
                pais(null, null, null, null, null, null),
                pais(null, null, null, null, null, "ANEXO B"),
                pais(null, null, null, null, null, null),
                pais("CAPÍTULO 3. FINAL", null, null, null, null, null)
        ), paises(resultado));
    }

    /** Retorna elementos na ordem original, preserva seus campos e não carrega estado entre chamadas. */
    @Test
    void preservaElementosCamposOrdemEntradaEIsolamentoEntreChamadas() {
        ElementoNormativo capitulo = elemento(CAPITULO, "CAPÍTULO 1", "CAPÍTULO 1. TÍTULO", 2);
        ElementoNormativo artigo = new ElementoNormativo(ARTIGO, "1.1", "Art. 1.1. Regra", 3,
                "2-1", 17, List.of(0.1, 0.2, 0.3, 0.4), true, PaisNormativos.vazios());
        List<ElementoNormativo> entrada = List.of(capitulo, artigo);

        List<ElementoNormativo> primeira = montador.montar(entrada);
        List<ElementoNormativo> segunda = montador.montar(List.of(elemento(TEXTO, null, "Isolado", 1)));

        assertEquals(List.of(capitulo, artigo), primeira.stream().map(e -> semPais(e)).toList());
        assertNotSame(entrada.get(0), primeira.get(0));
        assertEquals(List.of(PaisNormativos.vazios()), paises(segunda));
    }

    /** Constrói um elemento sintético, variando tipo, conteúdo e página para os cenários do teste. */
    private ElementoNormativo elemento(Tipo tipo, String marcador, String texto, int pagina) {
        return new ElementoNormativo(tipo, marcador, texto, pagina, "rotulo-" + pagina,
                pagina * 10, List.of(0.1, 0.2, 0.3, 0.4), false, PaisNormativos.vazios());
    }

    /** Constrói o valor de pais esperado nos asserts de hierarquia. */
    private PaisNormativos pais(String capitulo, String artigo, String inciso, String alinea,
                                String subalinea, String anexo) {
        return new PaisNormativos(capitulo, artigo, inciso, alinea, subalinea, anexo);
    }

    /** Extrai os pais na ordem recebida, para comparar somente a relação hierárquica. */
    private List<PaisNormativos> paises(List<ElementoNormativo> elementos) {
        return elementos.stream().map(ElementoNormativo::pais).toList();
    }

    /** Remove a hierarquia de um elemento, permitindo comparar os campos originais do DTO. */
    private ElementoNormativo semPais(ElementoNormativo elemento) {
        return elemento.comPais(PaisNormativos.vazios());
    }
}
