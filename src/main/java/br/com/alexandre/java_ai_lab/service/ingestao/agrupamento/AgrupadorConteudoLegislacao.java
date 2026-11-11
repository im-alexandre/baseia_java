package br.com.alexandre.java_ai_lab.service.ingestao.agrupamento;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.OrigemLegislacao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/** Agrupa texto e itens HTML já classificados, mantendo dispositivos e barreiras estruturais. */
public class AgrupadorConteudoLegislacao {
    /**
     * Reúne somente TEXTO consecutivo ou ITEM consecutivo com pais e indicação de citação iguais.
     * Artigos, parágrafos, incisos e alíneas passam individualmente; os capítulos/seções ainda
     * presentes interrompem grupos, mesmo quando repetem o mesmo texto de contexto.
     * Rótulos sem conteúdo permanecem barreiras para a orquestração descartá-los depois.
     *
     * @param elementos lista completa, classificada e enriquecida com pais
     * @return unidades imutáveis, com todas as origens recebidas em ordem documental
     */
    public List<ElementoLegislacao> agrupar(List<ElementoLegislacao> elementos) {
        List<ElementoLegislacao> resultado = new ArrayList<>();
        for (int indice = 0; indice < elementos.size();) {
            ElementoLegislacao primeiro = elementos.get(indice);
            if (!agrupavel(primeiro)) {
                resultado.add(primeiro);
                indice++;
                continue;
            }
            int fim = indice + 1;
            while (fim < elementos.size() && mesmoGrupo(primeiro, elementos.get(fim))) fim++;
            List<ElementoLegislacao> grupo = elementos.subList(indice, fim);
            List<OrigemLegislacao> origens = grupo.stream().flatMap(e -> e.origens().stream()).toList();
            resultado.add(new ElementoLegislacao(primeiro.tipo() == ITEM ? LISTA : TEXTO,
                    primeiro.tipo() == ITEM ? null : primeiro.marcador(),
                    grupo.stream().map(ElementoLegislacao::texto).collect(Collectors.joining("\n")),
                    grupo.stream().flatMap(e -> e.indicesOrigem().stream()).toList(), primeiro.ancora(),
                    primeiro.pais(), primeiro.citacao(), origens));
            indice = fim;
        }
        return List.copyOf(resultado);
    }

    /**
     * Exige conteúdo além do marcador para não transformar um item omitido em uma lista vazia.
     *
     * @param elemento bloco classificado, que pode conter apenas um rótulo de omissão
     * @return true somente para texto ou item com conteúdo agrupável
     */
    private boolean agrupavel(ElementoLegislacao elemento) {
        return (elemento.tipo() == TEXTO || elemento.tipo() == ITEM)
                && !elemento.texto().equals(elemento.marcador());
    }

    /**
     * Impede agrupar tipos, pais ou escopos de citação diferentes, sem inferir continuidade.
     *
     * @param primeiro bloco que ancora a unidade atual
     * @param proximo bloco candidato a continuar essa unidade
     * @return true quando o candidato contém texto e pertence ao mesmo grupo
     */
    private boolean mesmoGrupo(ElementoLegislacao primeiro, ElementoLegislacao proximo) {
        return agrupavel(proximo) && primeiro.tipo() == proximo.tipo() && primeiro.citacao() == proximo.citacao()
                && Objects.equals(primeiro.pais(), proximo.pais());
    }
}
