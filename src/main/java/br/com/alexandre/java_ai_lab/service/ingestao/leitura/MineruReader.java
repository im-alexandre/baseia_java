package br.com.alexandre.java_ai_lab.service.ingestao.leitura;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lê o formato docvortex.middle 2.0 sem classificar ou filtrar conteúdo normativo.
 * Ordena páginas e blocos pelos índices do produtor e substitui apenas contêineres list
 * por suas folhas, mantendo tabelas, equações e todos os artefatos de página.
 */
@Component
public class MineruReader {
    // Remove a moldura do rótulo impresso, como "- 1-1 -"; não confundir com page_idx.
    private static final Pattern PAGINA = Pattern.compile("^-?\\h*([IVXLCDM]+|(?:\\d+|[A-Z]{1,2})-\\d+)\\h*-?$");

    /**
     * Extrai todas as folhas em ordem documental, sem associar blocos de continuação.
     *
     * @param documento raiz JSON com schema docvortex.middle, schema_version 2.0 e pages
     * @return lista imutável de blocos, inclusive cabeçalhos, rodapés e preâmbulo
     * @throws IllegalArgumentException se o contrato, os índices ou as caixas forem inválidos
     */
    public List<BlocoMineru> ler(JsonNode documento) {
        validarDocumento(documento);
        List<BlocoMineru> resultado = new ArrayList<>();
        for (JsonNode pagina : ordenados(documento.path("pages"), "page_idx")) {
            lerPagina(pagina, resultado);
        }
        return List.copyOf(resultado);
    }

    /** Prepara as folhas e o rótulo impresso da página antes de extrair seus blocos. */
    private void lerPagina(JsonNode pagina, List<BlocoMineru> resultado) {
        // page_idx é base 0; a página física inclui a capa e é exposta em base 1.
        int paginaFisica = indice(pagina, "page_idx") + 1;
        List<JsonNode> blocos = new ArrayList<>();
        achatar(pagina.path("blocks"), blocos);
        String paginaImpressa = paginaImpressa(blocos);
        for (JsonNode bloco : blocos) {
            resultado.add(extrairBloco(bloco, paginaFisica, paginaImpressa));
        }
    }

    /** Copia conteúdo e metadados originais de uma folha sem interpretar sua função normativa. */
    private BlocoMineru extrairBloco(JsonNode bloco, int paginaFisica, String paginaImpressa) {
        String conteudo = texto(bloco.path("content"));
        int indice = indice(bloco, "index");
        validarGlifos(conteudo, paginaFisica, indice);
        return new BlocoMineru(bloco.path("type").asText(), conteudo.strip(),
                paginaFisica, paginaImpressa, indice, bbox(bloco),
                bloco.path("continues_prev").asBoolean(false));
    }

    /** Impede que glifos não mapeados da camada PDF cheguem silenciosamente ao RAG. */
    private void validarGlifos(String conteudo, int paginaFisica, int indice) {
        for (int posicao = 0; posicao < conteudo.length();) {
            int codigo = conteudo.codePointAt(posicao);
            if ((codigo < 32 && codigo != '\n' && codigo != '\r' && codigo != '\t')
                    || codigo == 127 || codigo == 0xFFFD) {
                throw new IllegalArgumentException("Glifo não mapeado U+%04X na página física %d, bloco %d."
                        .formatted(codigo, paginaFisica, indice));
            }
            posicao += Character.charCount(codigo);
        }
    }

    /** Rejeita contratos diferentes do JSON de origem suportado antes de iniciar a leitura. */
    private void validarDocumento(JsonNode documento) {
        if (documento == null || !"docvortex.middle".equals(documento.path("schema").asText())
                || !"2.0".equals(documento.path("schema_version").asText())
                || !documento.path("pages").isArray()) {
            throw new IllegalArgumentException("Esperado JSON docvortex.middle 2.0 com pages.");
        }
    }

    /** Lê o primeiro page_number em ordem de leitura e conserva rótulos não reconhecidos. */
    private String paginaImpressa(List<JsonNode> blocos) {
        String rotulo = blocos.stream()
                .filter(b -> "page_number".equals(b.path("type").asText()))
                .map(b -> texto(b.path("content")).strip()).findFirst().orElse(null);
        if (rotulo == null) return null;
        var numero = PAGINA.matcher(rotulo);
        return numero.matches() ? numero.group(1) : rotulo;
    }

    /** Substitui contêineres list por seus filhos sem emitir o texto do contêiner em duplicidade. */
    private void achatar(JsonNode nos, List<JsonNode> destino) {
        if (!nos.isArray()) throw new IllegalArgumentException("Esperada lista de blocos.");
        for (JsonNode no : ordenados(nos, "index")) {
            if ("list".equals(no.path("type").asText())) achatar(no.path("content"), destino);
            else destino.add(no);
        }
    }

    /** Usa os índices do produtor, validando inclusive listas com um único elemento. */
    private List<JsonNode> ordenados(JsonNode nos, String campo) {
        List<JsonNode> lista = new ArrayList<>();
        for (JsonNode no : nos) {
            indice(no, campo);
            lista.add(no);
        }
        lista.sort(Comparator.comparingInt(n -> indice(n, campo)));
        return lista;
    }

    /** Lê índices inteiros não negativos: page_idx para páginas e index para blocos. */
    private int indice(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (!valor.isIntegralNumber() || !valor.canConvertToInt() || valor.asInt(-1) < 0)
            throw new IllegalArgumentException("Índice inválido: " + campo);
        return valor.asInt();
    }

    /** Valida e copia [x0, y0, x1, y1] normalizado, com origem no canto superior esquerdo. */
    private List<Double> bbox(JsonNode bloco) {
        JsonNode caixa = bloco.path("bbox");
        if (!caixa.isArray() || caixa.size() != 4) throw new IllegalArgumentException("bbox inválido.");
        List<Double> valores = new ArrayList<>();
        for (JsonNode valor : caixa) {
            double coordenada = valor.asDouble();
            if (!valor.isNumber() || !Double.isFinite(coordenada) || coordenada < 0 || coordenada > 1)
                throw new IllegalArgumentException("bbox deve usar coordenadas normalizadas entre 0 e 1.");
            valores.add(coordenada);
        }
        if (valores.get(0) > valores.get(2) || valores.get(1) > valores.get(3))
            throw new IllegalArgumentException("bbox invertido.");
        return valores;
    }

    /**
     * Reúne strings de content direto, objeto ou array inline sem introduzir separadores.
     * Espaços, HTML e LaTeX permanecem como extraídos; somente a borda da folha recebe strip.
     */
    private String texto(JsonNode conteudo) {
        if (conteudo.isString()) return conteudo.asText();
        if (conteudo.isObject()) return texto(conteudo.path("content"));
        StringBuilder texto = new StringBuilder();
        if (conteudo.isArray()) for (JsonNode parte : conteudo) texto.append(texto(parte));
        return texto.toString();
    }
}
