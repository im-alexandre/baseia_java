package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.No;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Preparacao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Garante árvore, proveniência, chunking e payload sem depender das extrações antigas. */
class PipelineCanonicoTest {
    private final ConstrutorArvoreNormativa construtor = new ConstrutorArvoreNormativa();
    private final PreparadorNormativo preparador = new PreparadorNormativo(construtor,
            new ValidadorArvoreNormativa(), new ChunkerContextual());

    /** O texto continuado em outra página pertence ao dispositivo mais específico. */
    @Test
    void vinculaContinuacaoDaSubalineaEExplicitaHierarquiaNoPayload() {
        String cap = "CAPÍTULO 1. PATRIMÔNIO";
        String art = "1.2. GESTÃO";
        String inciso = "1.2.1. Classificação dos bens:";
        String alinea = "a) Organização Militar Consumidora";
        String subalinea = "I) Organização Militar Consumidora Integrada";
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 1", cap, 14, 0, PaisNormativos.vazios()),
                norma(ARTIGO, "1.2", art, 14, 1, pais(cap, null, null, null, null)),
                norma(INCISO, "1.2.1", inciso, 14, 2, pais(cap, art, null, null, null)),
                norma(ALINEA, "a)", alinea, 14, 3, pais(cap, art, inciso, null, null)),
                norma(SUBALINEA, "I)", subalinea, 14, 4, pais(cap, art, inciso, alinea, null)),
                norma(TEXTO, null, "É a denominação para as OMC integradas.", 15, 0,
                        pais(cap, art, inciso, alinea, subalinea)));

        Preparacao preparado = preparador.norma("SGM-303", "7REV", elementos);
        Documento documento = preparado.documento();
        assertEquals(6, documento.nos().size());
        No descricao = documento.nos().getLast();
        assertEquals(documento.nos().get(4).id(), descricao.paiId());
        assertEquals(5, descricao.ancestraisIds().size());
        assertTrue(preparado.alertas().isEmpty());

        var chunk = preparado.chunks().stream().filter(c -> c.noId().equals(descricao.paiId()))
                .findFirst().orElseThrow();
        assertEquals(List.of(documento.nos().get(4).id(), descricao.id()), chunk.nosCobertos());
        assertTrue(chunk.texto().contains(subalinea));
        assertTrue(chunk.texto().contains("É a denominação"));
        assertTrue(chunk.textoEmbedding().contains("Hierarquia: CAPITULO"));
        assertEquals(14, chunk.paginaInicial());
        assertEquals(15, chunk.paginaFinal());

        Map<String, Object> payload = new QdrantPayloadBuilder().montar(documento, chunk);
        assertEquals(chunk.texto(), payload.get("text"));
        assertFalse(payload.containsKey("section_map"));
        assertEquals(descricao.paiId(), payload.get("node_id"));
        assertEquals(5, ((List<?>) payload.get("hierarchy")).size());
        assertEquals(documento.nos().get(3).id(), payload.get("parent_id"));
        assertEquals(14, payload.get("page_start"));
        assertEquals(15, payload.get("page_end"));
    }

    /** Uma leitura idêntica produz os mesmos IDs mesmo se as instâncias de serviço mudarem. */
    @Test
    void idsSaoDeterministicosNaMesmaEdicao() {
        List<ElementoNormativo> elementos = List.of(norma(CAPITULO, "CAPÍTULO 1", "CAPÍTULO 1. TESTE",
                1, 0, PaisNormativos.vazios()));
        assertEquals(construtor.norma("SGM-303", "7REV", elementos).nos().getFirst().id(),
                new ConstrutorArvoreNormativa().norma("SGM-303", "7REV", elementos).nos().getFirst().id());
        assertNotEquals(construtor.norma("SGM-303", "7REV", elementos).nos().getFirst().id(),
                construtor.norma("SGM-303", "8REV", elementos).nos().getFirst().id());
    }

    /** Requer inspeção do OCR quando I maiúsculo ocorre entre k e m. */
    @Test
    void alertaConfusaoEntreLetraLERomanoI() {
        String cap = "CAPÍTULO 1. TESTE";
        String art = "1.1. TESTE";
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 1", cap, 1, 0, PaisNormativos.vazios()),
                norma(ARTIGO, "1.1", art, 1, 1, pais(cap, null, null, null, null)),
                norma(ALINEA, "k)", "k) Texto K", 1, 2, pais(cap, art, null, null, null)),
                norma(SUBALINEA, "I)", "I) Texto suspeito", 1, 3, pais(cap, art, null, "k) Texto K", null)),
                norma(ALINEA, "m)", "m) Texto M", 1, 4, pais(cap, art, null, null, null)));

        Preparacao preparado = preparador.norma("SGM-303", "7REV", elementos);
        assertTrue(preparado.alertas().stream().anyMatch(a -> a.codigo().equals("OCR_I_OU_L")));
        assertEquals("I)", preparado.documento().nos().get(3).marcador());
    }

    /** Sinaliza a sequência impressa VIII → XIX → X, preservando os três marcadores. */
    @Test
    void alertaSaltosRomanosSemCorrigirDocumento() {
        String cap = "CAPÍTULO 2. GESTÃO";
        String art = "2.6. MOVIMENTAÇÃO";
        String inciso = "2.6.2. Procedimentos";
        String alinea = "a) Movimentações";
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 2", cap, 1, 0, PaisNormativos.vazios()),
                norma(ARTIGO, "2.6", art, 1, 1, pais(cap, null, null, null, null)),
                norma(INCISO, "2.6.2", inciso, 1, 2, pais(cap, art, null, null, null)),
                norma(ALINEA, "a)", alinea, 1, 3, pais(cap, art, inciso, null, null)),
                norma(SUBALINEA, "VIII)", "VIII) registro", 1, 4, pais(cap, art, inciso, alinea, null)),
                norma(SUBALINEA, "XIX)", "XIX) movimentação", 1, 5, pais(cap, art, inciso, alinea, null)),
                norma(SUBALINEA, "X)", "X) documento", 1, 6, pais(cap, art, inciso, alinea, null)));

        Preparacao resultado = preparador.norma("SGM-303", "7REV", elementos);
        assertEquals(List.of("VIII)", "XIX)", "X)"), resultado.documento().nos().subList(4, 7)
                .stream().map(No::marcador).toList());
        assertEquals(2, resultado.alertas().stream()
                .filter(a -> "SEQUENCIA_SUBALINEA".equals(a.codigo())).count());
        assertFalse(resultado.alertas().stream().anyMatch(a -> "ROMANO_INVALIDO".equals(a.codigo())));
    }

    /** Aponta dispositivos perdidos, hifenização e C0 sem tentar reescrever o original. */
    @Test
    void alertaConteudoSuspeitoPreservandoOTexto() {
        String cap = "CAPÍTULO 1. TESTE";
        String texto = "Continuação\nIII)o bem entregue aos pedi-\ndos da OM tem a\u0013vidades.";
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 1", cap, 1, 0, PaisNormativos.vazios()),
                norma(TEXTO, null, texto, 2, 1, pais(cap, null, null, null, null)));

        Preparacao resultado = preparador.norma("SGM-303", "7REV", elementos);
        assertEquals(texto, resultado.documento().nos().get(1).texto());
        assertTrue(resultado.alertas().stream().anyMatch(a -> "MARCADOR_NAO_CLASSIFICADO".equals(a.codigo())));
        assertTrue(resultado.alertas().stream().anyMatch(a -> "HIFENIZACAO_AMBIGUA".equals(a.codigo())));
        assertTrue(resultado.alertas().stream().anyMatch(a -> "GLIFO_NAO_MAPEADO".equals(a.codigo())));
    }

    /** Títulos numéricos da MB continuam na árvore, mas não viram pontos sem evidência. */
    @Test
    void omiteChunkDeTituloIsoladoSemOmitirDispositivoOuArtigoLegal() {
        String cap = "CAPÍTULO 1. TESTE";
        String secao = "1.2. PROPÓSITOS";
        String inciso = "1.2.1. Procedimentos para gestão:";
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 1", cap, 1, 0, PaisNormativos.vazios()),
                norma(ARTIGO, "1.2", secao, 1, 1, pais(cap, null, null, null, null)),
                norma(INCISO, "1.2.1", inciso, 1, 2, pais(cap, secao, null, null, null)),
                norma(ALINEA, "a)", "a) A primeira obrigação", 1, 3,
                        pais(cap, secao, inciso, null, null)),
                norma(ARTIGO, "1.3", "1.3. CONCEITOS", 2, 4, pais(cap, null, null, null, null)));
        Preparacao norma = preparador.norma("SGM-303", "7REV", elementos);

        assertEquals(5, norma.documento().nos().size());
        assertTrue(norma.chunks().stream().noneMatch(c -> c.noId().equals(norma.documento().nos().get(1).id())));
        assertTrue(norma.chunks().stream().noneMatch(c -> c.noId().equals(norma.documento().nos().get(4).id())));
        assertTrue(norma.chunks().stream().anyMatch(c -> c.noId().equals(norma.documento().nos().get(2).id())));

        Preparacao lei = preparador.lei("LEI-14129", "2021", List.of(
                lei(ARTIGO, "Art. 1º", "Art. 1º O acesso será garantido.", false,
                        PaisLegislacao.vazios(), 1)));
        assertEquals(1, lei.chunks().size());
    }

    /** A redação citada não substitui a cadeia hierárquica do artigo alterador. */
    @Test
    void separaArtigosPropriosDeDispositivosCitados() {
        String proprio = "Art. 51. Altera a lei anterior:";
        String citado = "Art. 12. Regra transcrita.";
        List<ElementoLegislacao> elementos = List.of(
                lei(ARTIGO, "Art. 51.", proprio, false, PaisLegislacao.vazios(), 0),
                lei(ARTIGO, "Art. 12.", citado, true, paisLei(proprio, null), 1),
                lei(PARAGRAFO, "§ 1º", "§ 1º Regra citada.", true, paisLei(proprio, citado), 2),
                lei(ARTIGO, "Art. 52.", "Art. 52. Regra própria.", false, PaisLegislacao.vazios(), 3));

        Documento documento = preparador.lei("LEI-14129", "2021", elementos).documento();
        assertEquals(documento.nos().get(0).id(), documento.nos().get(1).paiId());
        assertEquals(documento.nos().get(1).id(), documento.nos().get(2).paiId());
        assertNull(documento.nos().get(3).paiId());
        assertTrue(documento.nos().get(2).citacao());
        assertFalse(documento.nos().get(3).citacao());
    }

    /** Texto longo divide o ponto sem perder material nem gerar chunks vazios. */
    @Test
    void divideTextoLongoMantendoRastreioDoNo() {
        String cap = "CAPÍTULO 1. TESTE";
        String longo = "Conteúdo ".repeat(1100);
        List<ElementoNormativo> elementos = List.of(
                norma(CAPITULO, "CAPÍTULO 1", cap, 1, 0, PaisNormativos.vazios()),
                norma(TEXTO, null, longo, 1, 1, pais(cap, null, null, null, null)));

        var chunks = preparador.norma("SGM-303", "7REV", elementos).chunks();
        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(c -> c.texto().length() <= 4000 && !c.texto().isBlank()));
        assertEquals(1, chunks.stream().flatMap(c -> c.nosCobertos().stream()).distinct().count());
    }

    /** Monta uma origem MinerU sintética com cópia dos pais completos. */
    private ElementoNormativo norma(Tipo tipo, String marcador, String texto, int pagina, int indice,
                                    PaisNormativos pais) {
        return new ElementoNormativo(tipo, marcador, texto, pagina, "1-" + (pagina - 13),
                indice, List.of(0.1, 0.1, 0.8, 0.2), false, pais);
    }

    /** Referências completas da SGM sem atribuir níveis ausentes. */
    private PaisNormativos pais(String capitulo, String artigo, String inciso, String alinea, String subalinea) {
        return new PaisNormativos(capitulo, artigo, inciso, alinea, subalinea, null);
    }

    /** Cria um elemento legal HTML com origem DOM determinística. */
    private ElementoLegislacao lei(Tipo tipo, String marcador, String texto, boolean citacao,
                                  PaisLegislacao pais, int indice) {
        return new ElementoLegislacao(tipo, marcador, texto, List.of(indice), "art" + indice, pais, citacao);
    }

    /** Mantém a redação original do artigo introdutor e a referência ao citado. */
    private PaisLegislacao paisLei(String artigoProprio, String artigoCitado) {
        return new PaisLegislacao(null, null, null, artigoProprio, null, null, null, null, artigoCitado);
    }
}
