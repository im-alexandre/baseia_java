package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.List;

/** Contratos internos do documento canônico, independentes de Qdrant, MinerU e HTML. */
public final class ModeloCanonico {
    private ModeloCanonico() { }

    /** Identifica uma edição normativa e conserva os nós em ordem de leitura. */
    public record Documento(String codigo, String versao, String formato, List<No> nos) {
        public Documento {
            if (codigo == null || codigo.isBlank() || versao == null || versao.isBlank()) {
                throw new IllegalArgumentException("Código e versão do documento são obrigatórios.");
            }
            nos = List.copyOf(nos);
        }
    }

    /** Nó com parentesco explícito; a lista de ancestrais vai da raiz ao pai imediato. */
    public record No(String id, String paiId, List<String> ancestraisIds, Tipo tipo,
                     String marcador, String texto, int ordem, boolean citacao, List<Fonte> fontes) {
        public No {
            ancestraisIds = List.copyOf(ancestraisIds);
            fontes = List.copyOf(fontes);
        }
    }

    /** Proveniência original sem inventar paginação para fontes HTML. */
    public record Fonte(Tipo tipo, String marcador, Integer paginaFisica, String paginaImpressa, Integer indice,
                        String ancora, List<Double> bbox, boolean continuaAnterior) {
        public Fonte {
            bbox = bbox == null ? List.of() : List.copyOf(bbox);
        }
    }

    /** Referência tipada que permanece separada do texto destinado ao embedding. */
    public record Nivel(String id, Tipo tipo, String marcador, String titulo) { }

    /** Chunk semântico, com texto fiel e texto de embedding contextualizado em campos distintos. */
    public record Chunk(String id, String noId, List<String> nosCobertos,
                        String texto, String textoEmbedding, List<Nivel> hierarquia, int ordem,
                        boolean citacao, Integer paginaInicial, Integer paginaFinal,
                        String paginaImpressaInicial, String paginaImpressaFinal) {
        public Chunk {
            nosCobertos = List.copyOf(nosCobertos);
            hierarquia = List.copyOf(hierarquia);
        }
    }

    /** Diagnóstico não destrutivo de ambiguidades ou inconsistências da árvore. */
    public record Alerta(String codigo, String noId, String mensagem) { }

    /** Resultado pronto para revisão; nenhum vetor é produzido durante o preparo. */
    public record Preparacao(Documento documento, List<Chunk> chunks, List<Alerta> alertas) {
        public Preparacao {
            chunks = List.copyOf(chunks);
            alertas = List.copyOf(alertas);
        }
    }
}
