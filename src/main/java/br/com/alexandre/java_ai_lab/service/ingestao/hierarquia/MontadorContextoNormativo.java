package br.com.alexandre.java_ai_lab.service.ingestao.hierarquia;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/**
 * Acrescenta os pais ativos aos elementos já classificados, sem alterar texto ou origem.
 * A leitura é sequencial, com escopos opcionais: uma alínea pode pertencer a um artigo
 * sem inciso intermediário. O estado é exclusivo de cada chamada e atravessa páginas.
 */
public class MontadorContextoNormativo {
    private static final List<Tipo> NIVEIS = List.of(
            CAPITULO, SECAO, SUBSECAO, ARTIGO, PARAGRAFO, INCISO, ALINEA, SUBALINEA, ITEM);

    /**
     * Preenche pais antes de abrir o escopo do próprio elemento, evitando autorreferências.
     * Referências reproduzem o texto estrutural disponível; esta etapa não separa título
     * de enunciado quando ambos estão no mesmo bloco e não reúne blocos de conteúdo.
     *
     * @param elementos blocos MinerU classificados, na ordem documental
     * @return mesmos elementos e origens, enriquecidos com snapshots imutáveis dos pais
     */
    public List<ElementoNormativo> montar(List<ElementoNormativo> elementos) {
        EnumMap<Tipo, Referencia> ativos = new EnumMap<>(Tipo.class);
        List<ElementoNormativo> resultado = new ArrayList<>();
        for (ElementoNormativo elemento : elementos) {
            prepararContexto(elemento.tipo(), ativos);
            validarPaisNumerados(elemento, ativos);
            resultado.add(elemento.comPais(pais(ativos)));
            registrarEscopo(elemento.tipo(), elemento.marcador(), elemento.texto(), ativos);
        }
        return List.copyOf(resultado);
    }

    /**
     * Reutiliza as transições e snapshots para leis, sem aplicar prefixos numéricos MinerU:
     * Art. 12 e inciso IV não codificam o capítulo ou o artigo pai no próprio marcador.
     * Um inciso herda parágrafo somente quando esse nível está ativo. Dispositivos citados
     * usam escopos locais: artigoCitado referencia a outra lei e artigo conserva o introdutor.
     *
     * @param elementos blocos da lei e redações citadas, classificados e em ordem documental
     * @return mesmos elementos e origens, com pais da lei e contexto citado separados
     */
    public List<ElementoLegislacao> montarLegislacao(List<ElementoLegislacao> elementos) {
        EnumMap<Tipo, Referencia> ativos = new EnumMap<>(Tipo.class);
        EnumMap<Tipo, Referencia> citados = new EnumMap<>(Tipo.class);
        List<ElementoLegislacao> resultado = new ArrayList<>();
        for (ElementoLegislacao elemento : elementos) {
            if (!elemento.citacao()) citados.clear();
            EnumMap<Tipo, Referencia> escopo = elemento.citacao() ? citados : ativos;
            prepararContexto(elemento.tipo(), escopo);
            resultado.add(elemento.comPais(new PaisLegislacao(referencia(ativos.get(CAPITULO)),
                    referencia(ativos.get(SECAO)), referencia(ativos.get(SUBSECAO)),
                    referencia(ativos.get(ARTIGO)), referencia(escopo.get(PARAGRAFO)),
                    referencia(escopo.get(INCISO)), referencia(escopo.get(ALINEA)), referencia(escopo.get(ITEM)),
                    elemento.citacao() ? referencia(citados.get(ARTIGO)) : null)));
            registrarEscopo(elemento.tipo(), elemento.marcador(), elemento.texto(), escopo);
        }
        return List.copyOf(resultado);
    }

    /**
     * Encerra o nível anterior e seus subordinados; capítulo/anexo reiniciam, e fecho encerra os pais.
     *
     * @param tipo nível ou conteúdo reconhecido no bloco atual
     * @param ativos referências locais à chamada que devem permanecer para os próximos blocos
     */
    private void prepararContexto(Tipo tipo, EnumMap<Tipo, Referencia> ativos) {
        if (tipo == CAPITULO || tipo == ANEXO || tipo == FECHO) {
            ativos.clear();
            return;
        }
        int nivel = NIVEIS.indexOf(tipo);
        if (nivel < 0) return;
        NIVEIS.subList(nivel, NIVEIS.size()).forEach(ativos::remove);
    }

    /**
     * Retira referências incompatíveis com a numeração, sem inventar pais ausentes.
     * Por exemplo, um inciso 1.2.1 não pode herdar o artigo 1.1 que permaneceu ativo.
     *
     * @param elemento artigo ou inciso numérico, conforme a classificação MinerU
     * @param ativos referências locais das quais os pais incompatíveis serão retirados
     */
    private void validarPaisNumerados(ElementoNormativo elemento, EnumMap<Tipo, Referencia> ativos) {
        if (elemento.tipo() != ARTIGO && elemento.tipo() != INCISO) return;
        String marcador = elemento.marcador();
        Referencia capitulo = ativos.get(CAPITULO);
        if (capitulo != null && !marcador.startsWith(capitulo.marcador().split("\\h+")[1] + ".")) {
            ativos.remove(CAPITULO);
        }
        Referencia artigo = ativos.get(ARTIGO);
        if (elemento.tipo() == INCISO && artigo != null
                && !marcador.substring(0, marcador.lastIndexOf('.')).equals(artigo.marcador())) {
            ativos.remove(ARTIGO);
        }
    }

    /**
     * Registra aberturas estruturais; texto, citações, tabelas e itens de lista comuns não abrem pais.
     *
     * @param tipo categoria reconhecida que pode abrir um nível
     * @param marcador rótulo original usado na confirmação de relações numéricas
     * @param texto conteúdo completo usado nas referências dos elementos descendentes
     * @param ativos referências locais a atualizar após produzir o snapshot do elemento atual
     */
    private void registrarEscopo(Tipo tipo, String marcador, String texto, EnumMap<Tipo, Referencia> ativos) {
        if (NIVEIS.contains(tipo) || tipo == ANEXO
                || (tipo == TITULO_ANEXO && ativos.containsKey(ANEXO))) {
            ativos.put(tipo, new Referencia(marcador, texto));
        }
    }

    /**
     * Cria uma cópia imutável dos pais MinerU conhecidos, com null para níveis ausentes.
     *
     * @param ativos referências anteriores ao registro do elemento atual
     * @return contexto de pais sem autorreferências
     */
    private PaisNormativos pais(EnumMap<Tipo, Referencia> ativos) {
        return new PaisNormativos(referencia(ativos.get(CAPITULO)), referencia(ativos.get(ARTIGO)),
                referencia(ativos.get(INCISO)), referencia(ativos.get(ALINEA)),
                referencia(ativos.get(SUBALINEA)), referenciaAnexo(ativos));
    }

    /**
     * Usa o texto completo já reconhecido, sem tentar separar títulos ou reconstruir marcadores.
     *
     * @param referencia referência ativa ou null quando o nível não foi reconhecido
     * @return texto do pai, ou null quando ausente
     */
    private String referencia(Referencia referencia) {
        return referencia == null ? null : referencia.texto();
    }

    /**
     * Incorpora o título reconhecido do anexo à referência herdada pelos próximos conteúdos.
     *
     * @param ativos referências do anexo e de seu título, quando disponíveis
     * @return marcador original ou marcador com título; null quando não há anexo ativo
     */
    private String referenciaAnexo(EnumMap<Tipo, Referencia> ativos) {
        Referencia anexo = ativos.get(ANEXO);
        if (anexo == null) return null;
        Referencia titulo = ativos.get(TITULO_ANEXO);
        if (titulo == null) return referencia(anexo);
        return (anexo.marcador() + ". " + titulo.texto()).replaceAll("\\s+", " ").strip();
    }

    /**
     * Guarda os dados usados pela hierarquia, compartilhados pelas duas entradas.
     *
     * @param marcador rótulo de origem que permite confirmar relações numéricas
     * @param texto texto integral do nível reconhecido
     */
    private record Referencia(String marcador, String texto) {}
}
