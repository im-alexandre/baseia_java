package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Chunk;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Fonte;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Nivel;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.No;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Contrato enxuto do payload, sem section_map dentro de text e sem blobs de bbox/origens. */
@Service
public class QdrantPayloadBuilder {
    /** Constrói apenas metadados de recuperação e citação; a árvore completa fica no preparo. */
    public Map<String, Object> montar(Documento documento, Chunk chunk) {
        No ancora = documento.nos().stream().filter(no -> no.id().equals(chunk.noId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Nó âncora não encontrado."));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", 1);
        payload.put("text", chunk.texto());
        payload.put("document_id", documento.codigo());
        payload.put("document_version", documento.versao());
        payload.put("document_format", documento.formato());
        payload.put("node_id", ancora.id());
        payload.put("parent_id", ancora.paiId());
        payload.put("ancestor_ids", ancora.ancestraisIds());
        payload.put("node_type", ancora.tipo().name());
        payload.put("marker", ancora.marcador());
        payload.put("order", chunk.ordem());
        payload.put("covered_node_ids", chunk.nosCobertos());
        payload.put("cited_text", chunk.citacao());
        List<Map<String, Object>> hierarquia = chunk.hierarquia().stream().map(this::nivel).toList();
        payload.put("hierarchy", hierarquia);
        ancora.fontes().stream().map(Fonte::ancora).filter(Objects::nonNull).findFirst()
                .ifPresent(ancoraHtml -> payload.put("source_anchor", ancoraHtml));
        if (chunk.paginaInicial() != null) {
            payload.put("page_start", chunk.paginaInicial());
            payload.put("page_end", chunk.paginaFinal());
            payload.put("printed_page_start", chunk.paginaImpressaInicial());
            payload.put("printed_page_end", chunk.paginaImpressaFinal());
        }
        return payload;
    }

    /** Resume cada degrau com tipo, marcador e rótulo, mantendo a estrutura legível e filtrável. */
    private Map<String, Object> nivel(Nivel nivel) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", nivel.id());
        mapa.put("type", nivel.tipo().name());
        mapa.put("marker", nivel.marcador());
        String titulo = nivel.titulo();
        mapa.put("label", titulo == null || titulo.length() <= 200 ? titulo : titulo.substring(0, 200) + "…");
        return mapa;
    }
}
