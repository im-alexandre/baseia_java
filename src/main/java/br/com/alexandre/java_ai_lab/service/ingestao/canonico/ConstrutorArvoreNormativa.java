package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.dto.OrigemLegislacao;
import br.com.alexandre.java_ai_lab.dto.OrigemNormativa;
import br.com.alexandre.java_ai_lab.dto.PaisLegislacao;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Fonte;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.No;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/** Cria uma árvore por referências a IDs, sem reescrever nem normalizar o texto extraído. */
@Service
public class ConstrutorArvoreNormativa {
    private static final List<Tipo> NIVEIS_NORMA = List.of(ANEXO, CAPITULO, ARTIGO, INCISO, ALINEA, SUBALINEA);
    private static final List<Tipo> NIVEIS_LEI = List.of(CAPITULO, SECAO, SUBSECAO, ARTIGO,
            PARAGRAFO, INCISO, ALINEA, ITEM);

    /** Transforma toda a sequência classificada do MinerU, inclusive aberturas estruturais. */
    public Documento norma(String codigo, String versao, List<ElementoNormativo> elementos) {
        Estado estado = new Estado(codigo, versao, "MINERU", NIVEIS_NORMA);
        for (ElementoNormativo elemento : elementos) {
            List<Fonte> fontes = elemento.origens().stream().map(this::fonteMineru).toList();
            estado.inserir(elemento.tipo(), elemento.marcador(), elemento.texto(), false, fontes,
                    referenciasNorma(elemento.pais()));
        }
        return estado.documento();
    }

    /** Mantém a redação citada sob o artigo introdutor, mas em um escopo próprio. */
    public Documento lei(String codigo, String versao, List<ElementoLegislacao> elementos) {
        Estado estado = new Estado(codigo, versao, "HTML", NIVEIS_LEI);
        for (ElementoLegislacao elemento : elementos) {
            List<Fonte> fontes = elemento.origens().stream().flatMap(origem -> fonteHtml(origem).stream()).toList();
            estado.inserir(elemento.tipo(), elemento.marcador(), elemento.texto(), elemento.citacao(), fontes,
                    referenciasLei(elemento.pais(), elemento.citacao()));
        }
        return estado.documento();
    }

    /** Preserva página impressa, página física, bbox e index de cada origem MinerU. */
    private Fonte fonteMineru(OrigemNormativa origem) {
        return new Fonte(origem.tipo(), origem.marcador(), origem.paginaFisica(),
                origem.paginaImpressa(), origem.indiceOrigem(), null, origem.bbox(), origem.continuaAnterior());
    }

    /** Mantém índices DOM em uma referência por bloco, sem fabricar coordenadas HTML. */
    private List<Fonte> fonteHtml(OrigemLegislacao origem) {
        return origem.indicesOrigem().stream()
                .map(indice -> new Fonte(origem.tipo(), origem.marcador(), null, null, indice,
                        origem.ancora(), List.of(), false))
                .toList();
    }

    /** Transfere os pais confirmados pelo parser MinerU, inclusive ausências intencionais. */
    private Map<Tipo, String> referenciasNorma(PaisNormativos pais) {
        Map<Tipo, String> referencias = new EnumMap<>(Tipo.class);
        if (pais.capitulo() != null) referencias.put(CAPITULO, pais.capitulo());
        if (pais.artigo() != null) referencias.put(ARTIGO, pais.artigo());
        if (pais.inciso() != null) referencias.put(INCISO, pais.inciso());
        if (pais.alinea() != null) referencias.put(ALINEA, pais.alinea());
        if (pais.subalinea() != null) referencias.put(SUBALINEA, pais.subalinea());
        if (pais.anexo() != null) referencias.put(ANEXO, pais.anexo());
        return referencias;
    }

    /** Conserva o artigo introdutor em ramo próprio ao navegar redações transcritas. */
    private Map<Tipo, String> referenciasLei(PaisLegislacao pais, boolean citacao) {
        Map<Tipo, String> referencias = new EnumMap<>(Tipo.class);
        if (pais.capitulo() != null) referencias.put(CAPITULO, pais.capitulo());
        if (pais.secao() != null) referencias.put(SECAO, pais.secao());
        if (pais.subsecao() != null) referencias.put(SUBSECAO, pais.subsecao());
        String artigo = citacao ? pais.artigoCitado() : pais.artigo();
        if (artigo != null) referencias.put(ARTIGO, artigo);
        if (pais.paragrafo() != null) referencias.put(PARAGRAFO, pais.paragrafo());
        if (pais.inciso() != null) referencias.put(INCISO, pais.inciso());
        if (pais.alinea() != null) referencias.put(ALINEA, pais.alinea());
        if (pais.item() != null) referencias.put(ITEM, pais.item());
        return referencias;
    }

    /** Estado local à construção, com escopos distintos para texto próprio e citado. */
    private static final class Estado {
        private final String codigo;
        private final String versao;
        private final String formato;
        private final List<Tipo> niveis;
        private final List<No> nos = new ArrayList<>();
        private final Map<String, Integer> ocorrencias = new HashMap<>();
        private final EnumMap<Tipo, No> proprios = new EnumMap<>(Tipo.class);
        private final EnumMap<Tipo, No> citados = new EnumMap<>(Tipo.class);

        /** Isola o estado de cada documento. */
        private Estado(String codigo, String versao, String formato, List<Tipo> niveis) {
            this.codigo = codigo;
            this.versao = versao;
            this.formato = formato;
            this.niveis = niveis;
        }

        /** Insere o nó com pai, ancestrais e ID determinístico estável na mesma edição. */
        private void inserir(Tipo tipo, String marcador, String texto, boolean citacao,
                             List<Fonte> fontes, Map<Tipo, String> referencias) {
            if (!citacao) citados.clear();
            EnumMap<Tipo, No> ativos = citacao ? citados : proprios;
            int nivel = niveis.indexOf(tipo);
            No pai = nivel >= 0 ? anterior(ativos, nivel, referencias) : ultimo(ativos, referencias);
            if (citacao && pai == null) pai = proprios.get(ARTIGO);
            if (tipo == ANEXO) {
                proprios.clear();
                pai = null;
            } else if (tipo == CAPITULO && !citacao) {
                proprios.clear();
                pai = null;
            }
            String paiId = pai == null ? null : pai.id();
            String chave = paiId + "|" + citacao + "|" + tipo + "|" + marcador;
            int ocorrencia = ocorrencias.merge(chave, 1, Integer::sum);
            String identificacao = codigo + "|" + versao + "|" + chave + "|" + ocorrencia;
            String id = UUID.nameUUIDFromBytes(identificacao.getBytes(StandardCharsets.UTF_8)).toString();
            List<String> ancestrais = new ArrayList<>();
            if (pai != null) {
                ancestrais.addAll(pai.ancestraisIds());
                ancestrais.add(pai.id());
            }
            No no = new No(id, paiId, ancestrais, tipo, marcador, texto, nos.size(), citacao, fontes);
            nos.add(no);
            if (nivel >= 0) {
                niveis.subList(nivel, niveis.size()).forEach(ativos::remove);
                ativos.put(tipo, no);
            }
        }

        /** Seleciona o pai mais próximo com nível estritamente superior na estrutura. */
        private No anterior(EnumMap<Tipo, No> ativos, int nivel, Map<Tipo, String> referencias) {
            for (int pos = nivel - 1; pos >= 0; pos--) {
                No pai = ativos.get(niveis.get(pos));
                if (corresponde(pai, referencias)) return pai;
            }
            return null;
        }

        /** Seleciona o último escopo estrutural ativo para texto e tabelas não numerados. */
        private No ultimo(EnumMap<Tipo, No> ativos, Map<Tipo, String> referencias) {
            for (int pos = niveis.size() - 1; pos >= 0; pos--) {
                No pai = ativos.get(niveis.get(pos));
                if (corresponde(pai, referencias)) return pai;
            }
            return null;
        }

        /** Aceita o título posterior do anexo sem trocar sua referência estrutural original. */
        private boolean corresponde(No pai, Map<Tipo, String> referencias) {
            if (pai == null) return false;
            String esperado = referencias.get(pai.tipo());
            return pai.texto().equals(esperado) || pai.tipo() == ANEXO && esperado != null
                    && esperado.startsWith(pai.texto() + ". ");
        }

        /** Conclui a árvore imutável do documento. */
        private Documento documento() {
            return new Documento(codigo, versao, formato, nos);
        }
    }
}
