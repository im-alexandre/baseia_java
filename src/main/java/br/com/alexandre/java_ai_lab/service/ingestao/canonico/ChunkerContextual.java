package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Chunk;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Fonte;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Nivel;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.No;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/** Monta unidades semânticas por dispositivo, associando conteúdos diretos sem alterar seus nós. */
@Service
public class ChunkerContextual {
    private static final Set<Tipo> CONTEUDO = Set.of(TEXTO, LISTA, ITEM_LISTA, TABELA, EQUACAO, TITULO_ANEXO);
    private static final Set<Tipo> SEM_CAPUT = Set.of(CAPITULO, SECAO, SUBSECAO, ANEXO, TITULO_ANEXO);
    private static final int LIMITE_CARACTERES = 4000;

    /** Um dispositivo e seus filhos textuais imediatos formam uma unidade; o limite divide sem perder trechos. */
    public List<Chunk> montar(Documento documento) {
        Map<String, No> porId = new HashMap<>();
        Map<String, List<No>> filhos = new HashMap<>();
        for (No no : documento.nos()) {
            porId.put(no.id(), no);
            filhos.computeIfAbsent(no.paiId(), ignorado -> new ArrayList<>()).add(no);
        }
        List<Chunk> resultado = new ArrayList<>();
        for (No ancora : documento.nos()) {
            if (CONTEUDO.contains(ancora.tipo()) || ancora.tipo() == IDENTIFICACAO_LEI
                    || ancora.tipo() == EMENTA || ancora.tipo() == FECHO) continue;
            List<Parte> partes = new ArrayList<>();
            if (!SEM_CAPUT.contains(ancora.tipo()) && ancora.texto() != null
                    && !ancora.texto().isBlank() && !ancora.texto().equals(ancora.marcador())) {
                partes.add(new Parte(ancora.id(), ancora.texto()));
            }
            for (No filho : filhos.getOrDefault(ancora.id(), List.of())) {
                if (CONTEUDO.contains(filho.tipo()) && filho.texto() != null && !filho.texto().isBlank()) {
                    partes.add(new Parte(filho.id(), filho.texto()));
                }
            }
            // Seções da MB como "1.2. PROPÓSITOS" são contexto, não evidência isolada.
            // Um artigo legal "Art. 1º" e seções com prosa própria continuam indexáveis.
            if (tituloSecaoIsolado(ancora, partes)) continue;
            if (partes.isEmpty()) continue;
            List<Nivel> hierarquia = caminho(ancora, porId);
            List<Parte> fragmentos = dividir(partes);
            List<Parte> grupo = new ArrayList<>();
            int caracteres = 0;
            for (Parte fragmento : fragmentos) {
                if (!grupo.isEmpty() && caracteres + 1 + fragmento.texto().length() > LIMITE_CARACTERES) {
                    resultado.add(criarChunk(documento, ancora, hierarquia, grupo, resultado.size(), porId));
                    grupo.clear();
                    caracteres = 0;
                }
                grupo.add(fragmento);
                caracteres += fragmento.texto().length() + 1;
            }
            if (!grupo.isEmpty()) resultado.add(criarChunk(documento, ancora, hierarquia, grupo, resultado.size(), porId));
        }
        return List.copyOf(resultado);
    }

    /** Exclui somente o ponto composto pelo título da seção, conservando seu nó na árvore. */
    private boolean tituloSecaoIsolado(No ancora, List<Parte> partes) {
        return ancora.tipo() == ARTIGO && ancora.marcador() != null
                && ancora.marcador().matches("\\d+\\.\\d+")
                && partes.size() == 1 && partes.getFirst().noId().equals(ancora.id());
    }

    /** Divide textos grandes por espaço ou quebra, mantendo exatamente as sequências de caracteres. */
    private List<Parte> dividir(List<Parte> partes) {
        List<Parte> resultado = new ArrayList<>();
        for (Parte parte : partes) {
            String resto = parte.texto();
            while (resto.length() > LIMITE_CARACTERES) {
                int corte = resto.lastIndexOf('\n', LIMITE_CARACTERES);
                if (corte < LIMITE_CARACTERES / 2) corte = resto.lastIndexOf(' ', LIMITE_CARACTERES);
                if (corte < LIMITE_CARACTERES / 2) corte = LIMITE_CARACTERES;
                resultado.add(new Parte(parte.noId(), resto.substring(0, corte)));
                resto = resto.substring(corte);
            }
            if (!resto.isEmpty()) resultado.add(new Parte(parte.noId(), resto));
        }
        return resultado;
    }

    /** Gera texto de embedding com breadcrumbs explícitos, sem prefixá-los no campo texto do payload. */
    private Chunk criarChunk(Documento documento, No ancora, List<Nivel> hierarquia,
                             List<Parte> partes, int ordem, Map<String, No> porId) {
        String texto = partes.stream().map(Parte::texto).reduce((a, b) -> a + "\n" + b).orElse("");
        String contexto = hierarquia.stream()
                .map(nivel -> nivel.tipo().name() + ": " + resumir(nivel.titulo(), 240))
                .reduce((a, b) -> a + " > " + b).orElse("");
        String embedding = "Documento: " + documento.codigo() + "; versão: " + documento.versao()
                + "\nHierarquia: " + contexto + "\nConteúdo:\n" + texto;
        Set<String> ids = new LinkedHashSet<>();
        partes.forEach(parte -> ids.add(parte.noId()));
        List<Fonte> fontes = ids.stream().map(porId::get).flatMap(no -> no.fontes().stream())
                .filter(fonte -> fonte.paginaFisica() != null)
                .sorted(Comparator.comparing(Fonte::paginaFisica).thenComparing(Fonte::indice,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Fonte primeira = fontes.isEmpty() ? null : fontes.getFirst();
        Fonte ultima = fontes.isEmpty() ? null : fontes.getLast();
        String id = UUID.nameUUIDFromBytes((ancora.id() + "|" + ordem).getBytes(StandardCharsets.UTF_8)).toString();
        return new Chunk(id, ancora.id(), List.copyOf(ids), texto, embedding, hierarquia, ordem,
                ancora.citacao(), primeira == null ? null : primeira.paginaFisica(),
                ultima == null ? null : ultima.paginaFisica(),
                primeira == null ? null : primeira.paginaImpressa(),
                ultima == null ? null : ultima.paginaImpressa());
    }

    /** Reconstitui a cadeia tipada com os próprios nós e seus ancestrais. */
    private List<Nivel> caminho(No no, Map<String, No> porId) {
        List<Nivel> resultado = new ArrayList<>();
        for (String id : no.ancestraisIds()) {
            No ancestral = porId.get(id);
            if (ancestral != null) resultado.add(new Nivel(id, ancestral.tipo(), ancestral.marcador(), ancestral.texto()));
        }
        resultado.add(new Nivel(no.id(), no.tipo(), no.marcador(), no.texto()));
        return List.copyOf(resultado);
    }

    /** Limita somente o breadcrumb para evitar repetir caputs longos em cada embedding. */
    private String resumir(String texto, int limite) {
        if (texto == null) return "";
        return texto.length() <= limite ? texto : texto.substring(0, limite) + "…";
    }

    /** Fragmento cujo texto continua associado ao nó fonte original. */
    private record Parte(String noId, String texto) { }
}
