package br.com.alexandre.java_ai_lab.service.ingestao.classificacao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;

import java.util.List;

/** Classifica legislação por regras textuais puras, sem depender da geometria do documento. */
public class ClassificadorLegislacao {
    // A origem estruturada prevalece sobre rótulos presentes em tabelas ou equações.
    // Estruturas precedem dispositivos e alíneas; o cabeçalho opcional vem antes do texto residual.
    private final List<RegraClassificacao> regras = List.of(
            new RegraConteudoEstruturado(), new RegraEstruturaLegislacao(),
            new RegraDispositivoLegal(), new RegraAlinea());
    private final RegraClassificacao regraCabecalho = new RegraCabecalhoLegislacao();
    private static final ContextoClassificacao SEM_CONTEXTO = new ContextoClassificacao(-1, null);

    /**
     * Retorna a primeira correspondência, sem guardar estado entre blocos; o fallback é TEXTO.
     * As regras de conteúdo estruturado precedem estruturas, dispositivos e alíneas.
     *
     * @param bloco texto e metadados de origem a classificar
     * @return classificação vencedora ou {@code TEXTO} quando nenhuma regra corresponder
     */
    public Classificacao classificar(BlocoClassificacao bloco) {
        return classificar(bloco, false);
    }

    /**
     * Aplica as regras estruturais antes do cabeçalho opcional e do fallback TEXTO.
     * A permissão é local à chamada; o classificador não guarda o estado do documento.
     *
     * @param bloco texto e metadados de origem a classificar
     * @param cabecalhoPermitido true somente antes do corpo normativo e fora de citações
     * @return primeira classificação reconhecida, incluindo identificação/ementa quando permitido
     */
    public Classificacao classificar(BlocoClassificacao bloco, boolean cabecalhoPermitido) {
        for (RegraClassificacao regra : regras) {
            var classificacao = regra.classificar(bloco, SEM_CONTEXTO);
            if (classificacao.isPresent()) return classificacao.get();
        }
        if (cabecalhoPermitido) {
            return regraCabecalho.classificar(bloco, SEM_CONTEXTO)
                    .orElseGet(() -> new Classificacao(Tipo.TEXTO, null));
        }
        return new Classificacao(Tipo.TEXTO, null);
    }
}
