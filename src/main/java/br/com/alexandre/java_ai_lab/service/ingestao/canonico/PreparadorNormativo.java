package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoLegislacao;
import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Preparacao;
import org.springframework.stereotype.Service;

import java.util.List;

/** Fronteira determinística entre parsing/classificação e publicação em índice vetorial. */
@Service
public class PreparadorNormativo {
    private final ConstrutorArvoreNormativa construtor;
    private final ValidadorArvoreNormativa validador;
    private final ChunkerContextual chunker;

    /** Recebe colaboradores puros e independentes do provedor de embeddings. */
    public PreparadorNormativo(ConstrutorArvoreNormativa construtor,
                               ValidadorArvoreNormativa validador, ChunkerContextual chunker) {
        this.construtor = construtor;
        this.validador = validador;
        this.chunker = chunker;
    }

    /** Processa uma norma da MB sem alterar textos e marcadores classificados. */
    public Preparacao norma(String codigo, String versao, List<ElementoNormativo> elementos) {
        return preparar(construtor.norma(codigo, versao, elementos));
    }

    /** Processa a legislação em HTML e conserva separadamente os dispositivos citados. */
    public Preparacao lei(String codigo, String versao, List<ElementoLegislacao> elementos) {
        return preparar(construtor.lei(codigo, versao, elementos));
    }

    /** Valida relações antes de produzir as unidades candidatas à indexação. */
    private Preparacao preparar(Documento documento) {
        return new Preparacao(documento, chunker.montar(documento), validador.validar(documento));
    }
}
