package br.com.alexandre.java_ai_lab.service.ingestao.agrupamento;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.OrigemNormativa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/** Agrupa parágrafos e itens de lista comuns, sem reunir alíneas ou subalíneas. */
public class AgrupadorConteudoNormativo {
    /**
     * Reúne somente sequências de TEXTO ou ITEM_LISTA. Qualquer outro tipo é preservado
     * individualmente e encerra a sequência, inclusive os níveis estruturais internos.
     */
    public List<ElementoNormativo> agrupar(List<ElementoNormativo> elementos) {
        List<ElementoNormativo> resultado = new ArrayList<>();
        int indice = 0;
        while (indice < elementos.size()) {
            ElementoNormativo elemento = elementos.get(indice);
            if (elemento.tipo() != TEXTO && elemento.tipo() != ITEM_LISTA) {
                resultado.add(elemento);
                indice++;
                continue;
            }
            ResultadoGrupo grupo = agruparConsecutivos(elementos, indice);
            resultado.add(grupo.elemento());
            indice = grupo.proximoIndice();
        }
        return List.copyOf(resultado);
    }

    /**
     * Agrupa apenas blocos consecutivos do mesmo tipo e com pais idênticos.
     * TEXTO mantém uma quebra entre parágrafos; ITEM_LISTA mantém cada item em sua linha.
     * Não incorpora textos de continuação nem cria recuos ou novos vínculos hierárquicos.
     */
    private ResultadoGrupo agruparConsecutivos(List<ElementoNormativo> elementos, int inicio) {
        ElementoNormativo primeiro = elementos.get(inicio);
        StringBuilder texto = new StringBuilder();
        List<OrigemNormativa> origens = new ArrayList<>();
        int indice = inicio;
        while (indice < elementos.size()) {
            ElementoNormativo elemento = elementos.get(indice);
            if (elemento.tipo() != primeiro.tipo() || !Objects.equals(elemento.pais(), primeiro.pais())) break;
            if (indice > inicio) texto.append('\n');
            texto.append(elemento.texto());
            origens.addAll(elemento.origens());
            indice++;
        }
        boolean lista = primeiro.tipo() == ITEM_LISTA;
        ElementoNormativo grupo = new ElementoNormativo(lista ? LISTA : TEXTO,
                lista ? null : primeiro.marcador(), texto.toString(), primeiro.paginaFisica(),
                primeiro.paginaImpressa(), primeiro.indiceOrigem(), primeiro.bbox(),
                primeiro.continuaAnterior(), primeiro.pais(), origens);
        return new ResultadoGrupo(grupo, indice);
    }

    /** Registra o elemento pronto e o primeiro índice ainda não consumido pela sequência. */
    private record ResultadoGrupo(ElementoNormativo elemento, int proximoIndice) {}
}
