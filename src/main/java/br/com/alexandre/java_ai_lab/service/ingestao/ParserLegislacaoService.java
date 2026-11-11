package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import br.com.alexandre.java_ai_lab.service.ingestao.agrupamento.AgrupadorConteudoLegislacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.BlocoClassificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.Classificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.ClassificadorLegislacao;
import br.com.alexandre.java_ai_lab.service.ingestao.hierarquia.MontadorContextoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.BlocoLegislacao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/** Orquestra a classificação dos blocos HTML e a montagem de pais, sem rede, DOM ou persistência. */
@Service
public class ParserLegislacaoService {
    private static final Set<Tipo> ESTRUTURA = Set.of(CAPITULO, SECAO, SUBSECAO);
    private static final Pattern ABRE_CITACAO = Pattern.compile("^[\"“«]\\h*Art\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern FECHA_CITACAO = Pattern.compile("[\"”»]\\h*(?:\\(NR\\))?\\.?$");
    private static final Pattern ASPAS = Pattern.compile("[\"“”«»]");
    private static final Pattern PONTILHADO = Pattern.compile("(?:\\.\\h*){3,}|…+");
    private static final Pattern INSTITUCIONAL = Pattern.compile(
            "(?iu)^(?:CÂMARA DOS DEPUTADOS|Centro de Documentação e Informação|"
                    + "O PRESIDENTE DA REPÚBLICA(?:\\h+Faço saber que o Congresso Nacional decreta e eu sanciono a seguinte Lei:?)?|"
                    + "Faço saber que o Congresso Nacional decreta e eu sanciono a seguinte Lei:?)$");
    private final ClassificadorLegislacao classificador = new ClassificadorLegislacao();
    private final MontadorContextoNormativo montador = new MontadorContextoNormativo();
    private final AgrupadorConteudoLegislacao agrupador = new AgrupadorConteudoLegislacao();

    /**
     * Seleciona conteúdo útil, classifica também os dispositivos citados, monta pais e agrupa.
     * Identificação e ementa são reconhecidas somente antes do corpo e fora de citações;
     * seus tipos próprios impedem que o agrupador as reúna como texto residual.
     * Cabeçalho institucional e fórmula de sanção só são excluídos antes do corpo. O fecho
     * encerra a leitura da própria lei; pontilhados não são conteúdo. Marcadores sem prosa
     * permanecem internamente para abrir contexto e impedir uniões entre dispositivos distintos.
     * Capítulos/seções/subseções só saem da lista depois de montar pais e encerrar os grupos.
     * O fecho documental prevalece sobre aspas incompletas; uma transcrição explicitamente
     * fechada antes da próxima abertura da própria lei conserva sua data como texto citado.
     *
     * @param blocos blocos HTML ordenados pelo reader, com texto e referências de origem
     * @return unidades úteis com classificação, origens e snapshots de pais, em ordem documental
     * @throws IllegalArgumentException quando nenhum artigo da própria lei é reconhecido
     */
    public List<ElementoLegislacao> parse(List<BlocoLegislacao> blocos) {
        return parseCompleto(blocos).stream()
                .filter(elemento -> !ESTRUTURA.contains(elemento.tipo()) && temConteudo(elemento))
                .toList();
    }

    /** Mantém os títulos de capítulo/seção/subseção e marcadores vazios para a árvore normativa. */
    public List<ElementoLegislacao> parseCompleto(List<BlocoLegislacao> blocos) {
        List<ElementoLegislacao> elementos = new ArrayList<>();
        boolean aspasAbertas = false;
        boolean blockquoteAnterior = false;
        boolean corpoIniciado = false;
        for (int indice = 0; indice < blocos.size(); indice++) {
            BlocoLegislacao bloco = blocos.get(indice);
            boolean blockquote = bloco.tipoOrigem().equals("citacao");
            Classificacao classificacao = classificar(bloco.texto(), bloco.tipoOrigem());
            // A fronteira HTML encerra o blockquote mesmo se suas aspas estiverem incompletas.
            if ((blockquoteAnterior && !blockquote) || artigoAncorado(bloco, classificacao)) aspasAbertas = false;
            if (!blockquote && classificacao.tipo() == FECHO
                    && (!aspasAbertas || !citacaoFechaAdiante(blocos, indice))) break;
            boolean abertura = ABRE_CITACAO.matcher(bloco.texto()).find();
            boolean citacao = blockquote || aspasAbertas || abertura;
            boolean fechamento = false;
            if (aspasAbertas || abertura) {
                if (quantidadeImparDeAspas(bloco.texto())) aspasAbertas = !aspasAbertas;
                fechamento = !aspasAbertas;
            }
            blockquoteAnterior = blockquote;
            String texto = limparTexto(bloco, abertura, fechamento);
            if (texto.isBlank()) continue;
            classificacao = classificador.classificar(
                    new BlocoClassificacao(texto, bloco.tipoOrigem(), List.of()), !corpoIniciado && !citacao);
            if (citacao && classificacao.tipo() == FECHO) classificacao = new Classificacao(TEXTO, null);
            if (!corpoIniciado && !citacao && INSTITUCIONAL.matcher(texto).matches()) continue;
            if (!citacao && (ESTRUTURA.contains(classificacao.tipo()) || classificacao.tipo() == ARTIGO)) {
                corpoIniciado = true;
            }
            List<Integer> origens = List.of(bloco.indiceOrigem());
            if (!citacao && ESTRUTURA.contains(classificacao.tipo()) && texto.equals(classificacao.marcador())
                    && indice + 1 < blocos.size() && tituloSeparado(blocos.get(indice + 1))) {
                BlocoLegislacao titulo = blocos.get(++indice);
                texto += ". " + limparTexto(titulo, false, false);
                origens = List.of(bloco.indiceOrigem(), titulo.indiceOrigem());
            }
            elementos.add(new ElementoLegislacao(classificacao.tipo(), classificacao.marcador(),
                    texto, origens, bloco.ancora(), PaisLegislacao.vazios(), citacao));
        }
        if (elementos.stream().noneMatch(elemento -> elemento.tipo() == ARTIGO && !elemento.citacao())) {
            throw new IllegalArgumentException("O HTML não contém artigos reconhecíveis da legislação.");
        }
        return List.copyOf(agrupador.agrupar(montador.montarLegislacao(elementos)));
    }

    /**
     * Usa o contrato de classificação existente, sem atribuir coordenadas artificiais ao HTML.
     *
     * @param texto texto já extraído ou limpo para reconhecimento do marcador
     * @param tipoOrigem tipo de bloco informado pelo reader
     * @return primeira classificação reconhecida, ou TEXTO quando nenhuma regra se aplica
     */
    private Classificacao classificar(String texto, String tipoOrigem) {
        return classificador.classificar(new BlocoClassificacao(texto, tipoOrigem, List.of()));
    }

    /**
     * Remove pontilhados e somente delimitadores confirmados da citação, preservando aspas internas.
     *
     * @param bloco conteúdo extraído, cuja representação HTML tabular permanece intacta
     * @param abertura indica aspas iniciais que introduzem um artigo citado
     * @param fechamento indica que as aspas do bloco encerram a citação externa
     * @return texto limpo ou HTML original de tabela
     */
    private String limparTexto(BlocoLegislacao bloco, boolean abertura, boolean fechamento) {
        if (bloco.tipoOrigem().equals("table")) return bloco.texto();
        String texto = bloco.texto();
        if (abertura) texto = texto.substring(1).stripLeading();
        if (fechamento) texto = FECHA_CITACAO.matcher(texto).replaceFirst("");
        String marcador = classificar(texto, bloco.tipoOrigem()).marcador();
        int inicioConteudo = marcador == null ? 0 : marcador.length();
        // O ponto de "1." é parte do marcador, mesmo quando seguido de lacunas pontilhadas.
        String conteudo = PONTILHADO.matcher(texto.substring(inicioConteudo)).replaceAll(" ");
        return (texto.substring(0, inicioConteudo) + conteudo).replaceAll("\\h+", " ").strip();
    }

    /**
     * Confirma uma abertura da própria lei pelo artigo sem aspas e pela âncora HTML de origem.
     *
     * @param bloco bloco original, antes de retirar delimitadores ou pontilhados
     * @param classificacao reconhecimento do texto original desse bloco
     * @return true quando a âncora confirma um artigo externo às redações citadas
     */
    private boolean artigoAncorado(BlocoLegislacao bloco, Classificacao classificacao) {
        return bloco.ancora() != null && !bloco.tipoOrigem().equals("citacao") && classificacao.tipo() == ARTIGO;
    }

    /**
     * Preserva uma data transcrita somente quando há fechamento explícito da citação adiante.
     * Uma nova abertura ancorada ou outro fecho interrompe a busca, sem reconstruir aspas ausentes.
     *
     * @param blocos sequência original de blocos HTML
     * @param inicio posição da data encontrada dentro de uma citação textual aberta
     * @return true quando as aspas externas fecham antes da próxima fronteira documental
     */
    private boolean citacaoFechaAdiante(List<BlocoLegislacao> blocos, int inicio) {
        boolean aberta = true;
        for (int indice = inicio; indice < blocos.size(); indice++) {
            BlocoLegislacao bloco = blocos.get(indice);
            Classificacao classificacao = classificar(bloco.texto(), bloco.tipoOrigem());
            if (indice > inicio && (artigoAncorado(bloco, classificacao)
                    || (!bloco.tipoOrigem().equals("citacao") && classificacao.tipo() == FECHO))) return false;
            if (quantidadeImparDeAspas(bloco.texto())) aberta = !aberta;
            if (!aberta && FECHA_CITACAO.matcher(bloco.texto()).find()) return true;
        }
        return false;
    }

    /**
     * Conta delimitadores para que pares internos de aspas não fechem a citação externa.
     *
     * @param texto bloco original, incluindo delimitadores externos e aspas internas balanceadas
     * @return true quando o bloco alterna o estado aberto/fechado da citação textual
     */
    private boolean quantidadeImparDeAspas(String texto) {
        return ASPAS.matcher(texto).results().count() % 2 != 0;
    }

    /**
     * Mantém rótulos sem conteúdo apenas como contexto interno, sem expô-los como unidade útil.
     *
     * @param elemento unidade classificada ou agrupada, com pontilhados já removidos
     * @return true quando existe conteúdo além de um marcador isolado
     */
    private boolean temConteudo(ElementoLegislacao elemento) {
        return !elemento.texto().isBlank() && !elemento.texto().equals(elemento.marcador());
    }

    /**
     * Reúne apenas título explicitamente centralizado/heading e sem marcador de outro dispositivo.
     *
     * @param bloco próximo bloco candidato a título da estrutura recém-aberta
     * @return true quando o bloco pode completar o capítulo, a seção ou a subseção
     */
    private boolean tituloSeparado(BlocoLegislacao bloco) {
        return bloco.tituloEstrutural() && !bloco.tipoOrigem().equals("citacao")
                && !ABRE_CITACAO.matcher(bloco.texto()).find()
                && !limparTexto(bloco, false, false).isBlank()
                && classificar(bloco.texto(), bloco.tipoOrigem()).tipo() == TEXTO;
    }
}
