package br.com.alexandre.java_ai_lab.service.ingestao;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo;
import br.com.alexandre.java_ai_lab.dto.PaisNormativos;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.BlocoClassificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.Classificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.ClassificadorNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.classificacao.ContextoClassificacao;
import br.com.alexandre.java_ai_lab.service.ingestao.leitura.BlocoMineru;
import br.com.alexandre.java_ai_lab.service.ingestao.hierarquia.MontadorContextoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.agrupamento.AgrupadorConteudoNormativo;
import br.com.alexandre.java_ai_lab.service.ingestao.filtragem.IdentificadorPaginasPreliminares;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo.*;

/**
 * Organiza blocos lidos pelo MineruReader em uma sequência plana de elementos normativos.
 * As regras de referência estão em src/test/resources/ingestao/mineru/regras_observacoes.md;
 * o fluxo implementado e suas limitações estão descritos em docs/ingestao-normas.md.
 *
 * <p>O Reader entrega blocos ordenados e com origem validada; o Parser abre o corpo em
 * um capítulo centralizado, com marcador isolado ou título em caixa alta unido ao número,
 * e classifica os blocos seguintes.
 * Marcadores explícitos prevalecem sobre os tipos atribuídos pelo MinerU, que pode
 * identificar tanto um inciso quanto uma alínea como paragraph_title.</p>
 *
 * <p>A geometria normalizada é usada para margens verticais e centralização.
 * Fonte, negrito, espaçamento entre linhas e recuos laterais não participam da
 * classificação atual. continuaAnterior reproduz somente o sinal original do MinerU;
 * o agrupamento posterior usa o tipo e os pais, sem inferir vínculos por esse sinal.</p>
 *
 * <p>Ao terminar a classificação e a seleção, enriquece a saída com os pais ativos.
 * Não corrige ambiguidades de OCR (por exemplo, l) versus I)).
 * Tabelas e equações conservam o HTML/LaTeX de origem. Todo estado de leitura é local
 * à chamada, permitindo compartilhar esta instância Spring entre requisições.</p>
 */
@Service
public class ParserNormasService {
    // O classificador é compartilhável: regras não mantêm nem alteram estado de leitura.
    private final ClassificadorNormativo classificador = new ClassificadorNormativo();
    private final MontadorContextoNormativo montadorContexto = new MontadorContextoNormativo();
    private final AgrupadorConteudoNormativo agrupadorConteudo = new AgrupadorConteudoNormativo();
    private final IdentificadorPaginasPreliminares identificadorPreliminares = new IdentificadorPaginasPreliminares();
    private static final Pattern ANEXO_EXCLUIDO = Pattern.compile("\\b(MODELOS?|EXEMPLOS?|FORMULARIOS?)\\b");
    private static final Set<String> EXTRA_TEXTUAIS = Set.of("header", "footer", "page_number");
    private static final Set<ElementoNormativo.Tipo> TIPOS_APENAS_CONTEXTO =
            Set.of(CAPITULO, ARTIGO, INCISO);
    // bbox usa frações da página, não centímetros: 2 cm / 29,7 cm de altura do A4.
    // Essa heurística não depende das margens laterais espelhadas; não detecta o papel real.
    private static final double MARGEM_VERTICAL = 2.0 / 29.7;

    /** Processa o corpo normativo e exclui todos os anexos por padrão. */
    public List<ElementoNormativo> parse(List<BlocoMineru> blocos) {
        return parse(blocos, Set.of());
    }

    /**
     * Processa o documento sem acessar arquivos, modelos de IA ou serviços externos.
     *
     * @param blocos blocos ordenados e validados pelo MineruReader, incluindo os extra-textuais
     * @param anexosPermitidos letras cuja utilidade foi revisada pelo chamador, como A e C;
     *                         conjunto não nulo, com comparação exata, sem normalizar caixa
     * @return elementos de conteúdo com pais, em ordem documental e lista imutável;
     *         capítulos, artigos e incisos são usados como contexto, mas omitidos da resposta
     */
    public List<ElementoNormativo> parse(List<BlocoMineru> blocos, Set<String> anexosPermitidos) {
        return parseCompleto(blocos, anexosPermitidos).stream()
                .filter(elemento -> !TIPOS_APENAS_CONTEXTO.contains(elemento.tipo()))
                .toList();
    }

    /** Preserva também os níveis estruturais necessários à árvore canônica e à indexação. */
    public List<ElementoNormativo> parseCompleto(List<BlocoMineru> blocos, Set<String> anexosPermitidos) {
        EstadoLeitura estado = new EstadoLeitura(anexosPermitidos);
        Set<Integer> preliminares = identificadorPreliminares.identificar(blocos);
        for (BlocoMineru bloco : blocos) {
            if (preliminares.contains(bloco.paginaFisica())) {
                reiniciarContexto(estado);
                continue;
            }
            processarBloco(bloco, estado);
        }
        // Não descarta aberturas: a representação canônica precisa de nós próprios para
        // capítulos, seções numeradas e itens, além de seus descendentes.
        return List.copyOf(agrupadorConteudo.agrupar(montadorContexto.montar(estado.resultado)));
    }

    /**
     * Uma capa ou divisão editorial encerra o contexto anterior sem apagar conteúdos já lidos.
     * A inclusão só reabre quando as regras reconhecem um novo capítulo ou anexo permitido.
     */
    private void reiniciarContexto(EstadoLeitura estado) {
        estado.capitulo = -1;
        estado.incluir = false;
        estado.tituloEsperado = null;
        estado.anexo = null;
        estado.marcadorAnexo = null;
    }

    /** Filtra um bloco já lido, classifica o conteúdo e registra o elemento com sua origem. */
    private void processarBloco(BlocoMineru bloco, EstadoLeitura estado) {
        if (extraTextual(bloco.tipoOrigem(), bloco.bbox())) return;
        String texto = bloco.texto();
        if (texto.isBlank() || "INTENCIONALMENTE EM BRANCO".equals(texto)) return;

        Classificacao classificacao = classificador.classificar(
                new BlocoClassificacao(texto, bloco.tipoOrigem(), bloco.bbox()),
                new ContextoClassificacao(estado.capitulo, estado.tituloEsperado));
        atualizarEstado(classificacao, texto, estado);
        ElementoNormativo.Tipo tipo = classificacao.tipo();
        if (tipo == TITULO_CAPITULO) {
            unirTituloCapitulo(texto, estado);
            return;
        }
        // O marcador é exposto separadamente, sem ser removido do texto.
        if (tipo == CAPITULO && estado.tituloEsperado == null) {
            texto = formatarCapitulo(classificacao.marcador(), tituloNoMesmoBloco(texto, classificacao.marcador()));
        }
        ElementoNormativo elemento = new ElementoNormativo(tipo, classificacao.marcador(), texto,
                bloco.paginaFisica(), bloco.paginaImpressa(), bloco.indiceOrigem(), bloco.bbox(),
                tipo != ANEXO && bloco.continuaAnterior(), PaisNormativos.vazios());
        if (tipo == ANEXO) {
            // Adia a inclusão até conhecer o título, mantendo a posição original do marcador.
            estado.marcadorAnexo = elemento;
            return;
        }
        if (estado.incluir) estado.resultado.add(elemento);
    }

    /**
     * Reúne o título ao último marcador de capítulo emitido, sem criar outro elemento.
     * O marcador reconhecido já exclui o ponto final opcional, evitando pontuação duplicada.
     * A origem permanece ancorada no marcador; os blocos originais continuam no Reader.
     */
    private void unirTituloCapitulo(String titulo, EstadoLeitura estado) {
        ElementoNormativo capitulo = estado.resultado.getLast();
        String texto = formatarCapitulo(capitulo.marcador(), titulo);
        estado.resultado.set(estado.resultado.size() - 1, new ElementoNormativo(
                CAPITULO, capitulo.marcador(), texto, capitulo.paginaFisica(),
                capitulo.paginaImpressa(), capitulo.indiceOrigem(), capitulo.bbox(),
                capitulo.continuaAnterior(), PaisNormativos.vazios()));
    }

    /** Remove o marcador e seu ponto opcional; retorna vazio quando o título está em outro bloco. */
    private String tituloNoMesmoBloco(String texto, String marcador) {
        return texto.substring(marcador.length()).replaceFirst("^\\.", "").strip();
    }

    /** Usa a mesma representação para capítulos extraídos de um ou de dois blocos MinerU. */
    private String formatarCapitulo(String marcador, String titulo) {
        return (marcador + ". " + titulo).replaceAll("\\s+", " ").strip();
    }

    /** Aplica o corte por tipo e por faixa vertical; não distingue notas de rodapé de outros textos. */
    private boolean extraTextual(String origem, List<Double> bbox) {
        return EXTRA_TEXTUAIS.contains(origem)
                || bbox.get(3) <= MARGEM_VERTICAL || bbox.get(1) >= 1 - MARGEM_VERTICAL;
    }

    /**
     * Aplica a decisão das regras ao estado exclusivo desta leitura.
     * Reconhecer um elemento não produz efeitos nas regras: somente o parser abre capítulos,
     * suspende anexos, consome títulos e decide o que entra na lista de saída.
     */
    private void atualizarEstado(Classificacao classificacao, String texto, EstadoLeitura estado) {
        switch (classificacao.tipo()) {
            case CAPITULO -> {
                estado.capitulo = Integer.parseInt(classificacao.marcador().split("\\h+")[1]);
                estado.incluir = true;
                estado.anexo = null;
                estado.tituloEsperado = tituloNoMesmoBloco(texto, classificacao.marcador()).isEmpty()
                        ? TITULO_CAPITULO : null;
            }
            case ANEXO -> {
                estado.anexo = classificacao.marcador().split("\\h+")[1];
                estado.incluir = false;
                estado.capitulo = -1;
                estado.tituloEsperado = TITULO_ANEXO;
            }
            case TITULO_CAPITULO -> estado.tituloEsperado = null;
            case TITULO_ANEXO -> {
                estado.tituloEsperado = null;
                estado.incluir = anexoPermitido(texto, estado);
                if (estado.incluir) estado.resultado.add(estado.marcadorAnexo);
            }
            default -> { /* Elementos do corpo não alteram a seção ativa nesta etapa. */ }
        }
    }

    /** Normaliza somente para a exclusão por palavras; a permissão exige letras com caixa exata. */
    private boolean anexoPermitido(String titulo, EstadoLeitura estado) {
        String normalizado = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return estado.anexosPermitidos.contains(estado.anexo)
                && !ANEXO_EXCLUIDO.matcher(normalizado).find();
    }

    /** Estado exclusivo de uma chamada: não é compartilhado entre requisições Spring. */
    private static final class EstadoLeitura {
        private final Set<String> anexosPermitidos;
        private final List<ElementoNormativo> resultado = new ArrayList<>();
        // -1 desativa artigos/incisos numerados fora de capítulo; o preâmbulo começa excluído.
        private int capitulo = -1;
        private boolean incluir;
        private ElementoNormativo.Tipo tituloEsperado;
        private String anexo;
        private ElementoNormativo marcadorAnexo;

        /** Copia as permissões para não depender de alterações posteriores no conjunto recebido. */
        private EstadoLeitura(Set<String> anexosPermitidos) {
            this.anexosPermitidos = Set.copyOf(anexosPermitidos);
        }
    }
}
