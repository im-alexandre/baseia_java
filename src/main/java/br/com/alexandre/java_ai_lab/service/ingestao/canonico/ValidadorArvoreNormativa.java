package br.com.alexandre.java_ai_lab.service.ingestao.canonico;

import br.com.alexandre.java_ai_lab.dto.ElementoNormativo.Tipo;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Alerta;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.Documento;
import br.com.alexandre.java_ai_lab.service.ingestao.canonico.ModeloCanonico.No;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Diagnósticos de consistência que não alteram marcadores suspeitos nem reparentam elementos. */
@Service
public class ValidadorArvoreNormativa {
    private static final Pattern MARCADOR_EM_TEXTO = Pattern.compile("(?m)^[ \\t]*([a-z]{1,2}\\)|[IVXLCDM]+\\))");
    private static final Pattern HIFEN_QUEBRADO = Pattern.compile("\\p{L}-\\h*\\R\\h*\\p{Ll}");
    private static final Pattern NUMERO_NORMATIVO = Pattern.compile("\\d+(?:\\.\\d+){1,2}");
    private static final Pattern ROMANO_CANONICO = Pattern.compile(
            "M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");

    /** Localiza pais inválidos, saltos numéricos, marcadores perdidos e suspeitas de extração. */
    public List<Alerta> validar(Documento documento) {
        List<Alerta> alertas = new ArrayList<>();
        Map<String, No> vistos = new HashMap<>();
        Map<String, String> marcadores = new HashMap<>();
        Map<String, No> ultimoIrmao = new HashMap<>();
        for (No no : documento.nos()) {
            if (no.paiId() != null && !vistos.containsKey(no.paiId())) {
                alertas.add(new Alerta("PAI_INVALIDO", no.id(), "Pai não encontrado antes do nó."));
            }
            if (no.paiId() != null && (no.ancestraisIds().isEmpty()
                    || !no.ancestraisIds().getLast().equals(no.paiId()))) {
                alertas.add(new Alerta("CAMINHO_INVALIDO", no.id(), "O último ancestral não corresponde ao pai."));
            }
            if (no.marcador() != null && !no.marcador().isBlank()) {
                String chave = no.paiId() + "|" + no.citacao() + "|" + no.tipo() + "|" + no.marcador();
                if (marcadores.putIfAbsent(chave, no.id()) != null) {
                    alertas.add(new Alerta("MARCADOR_REPETIDO", no.id(),
                            "Marcador repetido sob o mesmo pai; conferir a extração."));
                }
            }
            alertarMarcadorPerdido(no, alertas);
            alertarTextoSuspeito(no, alertas);
            alertarSequencia(no, ultimoIrmao, alertas);
            vistos.put(no.id(), no);
        }
        alertarConfusaoIl(documento.nos(), vistos, alertas);
        return List.copyOf(alertas);
    }

    /** Detecta dispositivos ainda classificados como prosa, inclusive em linhas internas de TEXTO. */
    private void alertarMarcadorPerdido(No no, List<Alerta> alertas) {
        if (no.tipo() != Tipo.TEXTO || no.texto() == null) return;
        Matcher marcador = MARCADOR_EM_TEXTO.matcher(no.texto());
        if (marcador.find()) {
            alertas.add(new Alerta("MARCADOR_NAO_CLASSIFICADO", no.id(),
                    "Marcador em TEXTO: " + marcador.group(1) + "; conferir classificação e agrupamento."));
        }
    }

    /** Registra glifos sem mapeamento e hifenização entre linhas, preservando a fonte literal. */
    private void alertarTextoSuspeito(No no, List<Alerta> alertas) {
        if (no.texto() == null) return;
        for (int i = 0; i < no.texto().length();) {
            int codigo = no.texto().codePointAt(i);
            if ((codigo < 32 && codigo != '\n' && codigo != '\r' && codigo != '\t')
                    || codigo == 127 || codigo == 0xFFFD) {
                alertas.add(new Alerta("GLIFO_NAO_MAPEADO", no.id(),
                        "Caractere U+%04X exige revisão da extração; texto original preservado.".formatted(codigo)));
                break;
            }
            i += Character.charCount(codigo);
        }
        if (HIFEN_QUEBRADO.matcher(no.texto()).find()) {
            alertas.add(new Alerta("HIFENIZACAO_AMBIGUA", no.id(),
                    "Palavra cortada entre linhas; conferir se o hífen pertence à grafia original."));
        }
    }

    /** Verifica a ordem apenas entre irmãos do mesmo nível, sem renumerar a fonte. */
    private void alertarSequencia(No no, Map<String, No> ultimoIrmao, List<Alerta> alertas) {
        if (no.paiId() == null || !sequenciavel(no.tipo()) || no.marcador() == null) return;
        String chave = no.paiId() + "|" + no.citacao() + "|" + no.tipo();
        No anterior = ultimoIrmao.put(chave, no);
        Integer atual = ordinal(no);
        if (no.tipo() == Tipo.SUBALINEA && atual == null) {
            alertas.add(new Alerta("ROMANO_INVALIDO", no.id(),
                    "Marcador romano não canônico: " + no.marcador() + "; manter texto e revisar a fonte."));
        }
        if (anterior == null || atual == null) return;
        Integer precedente = ordinal(anterior);
        if (precedente == null || atual.equals(precedente) || atual == precedente + 1) return;
        alertas.add(new Alerta("SEQUENCIA_" + no.tipo().name(), no.id(),
                "Sequência não consecutiva: " + anterior.marcador() + " → " + no.marcador()
                        + " (esperado ordinal " + (precedente + 1) + "). Marcador preservado."));
    }

    /** Limita a verificação aos níveis com ordinal confiável no próprio marcador. */
    private boolean sequenciavel(Tipo tipo) {
        return tipo == Tipo.ARTIGO || tipo == Tipo.INCISO || tipo == Tipo.ALINEA || tipo == Tipo.SUBALINEA;
    }

    /** Converte numerações decimais, alfabéticas e romanas sem modificar seus marcadores. */
    private Integer ordinal(No no) {
        String marcador = no.marcador();
        if (marcador == null) return null;
        if ((no.tipo() == Tipo.ARTIGO || no.tipo() == Tipo.INCISO)
                && NUMERO_NORMATIVO.matcher(marcador).matches()) {
            try {
                return Integer.parseInt(marcador.substring(marcador.lastIndexOf('.') + 1));
            } catch (NumberFormatException numeroAnomalo) {
                return null;
            }
        }
        if (no.tipo() == Tipo.ALINEA && marcador.matches("[a-z]{1,2}\\)")) {
            int numero = 0;
            for (int i = 0; i < marcador.length() - 1; i++) numero = numero * 26 + marcador.charAt(i) - 'a' + 1;
            return numero;
        }
        if (no.tipo() == Tipo.SUBALINEA && marcador.matches("[IVXLCDM]+\\)")) {
            String romano = marcador.substring(0, marcador.length() - 1);
            if (!ROMANO_CANONICO.matcher(romano).matches()) return null;
            int total = 0;
            int anterior = 0;
            for (int i = romano.length() - 1; i >= 0; i--) {
                int valor = valorRomano(romano.charAt(i));
                total += valor < anterior ? -valor : valor;
                anterior = valor;
            }
            return total;
        }
        return null;
    }

    /** Interpreta os sete símbolos romanos após a verificação de sua forma canônica. */
    private int valorRomano(char simbolo) {
        return switch (simbolo) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> throw new IllegalArgumentException("Símbolo romano inválido: " + simbolo);
        };
    }

    /** Detecta a sequência alínea k), subalínea I), alínea m) sem afirmar que I é l. */
    private void alertarConfusaoIl(List<No> nos, Map<String, No> porId, List<Alerta> alertas) {
        for (int i = 0; i < nos.size(); i++) {
            No suspeito = nos.get(i);
            if (suspeito.tipo() != Tipo.SUBALINEA || !"I)".equals(suspeito.marcador())) continue;
            No pai = porId.get(suspeito.paiId());
            if (pai == null || pai.tipo() != Tipo.ALINEA || !"k)".equals(pai.marcador())) continue;
            for (int j = i + 1; j < nos.size(); j++) {
                No seguinte = nos.get(j);
                if (seguinte.tipo() != Tipo.ALINEA) continue;
                if ("m)".equals(seguinte.marcador()) && java.util.Objects.equals(pai.paiId(), seguinte.paiId())) {
                    alertas.add(new Alerta("OCR_I_OU_L", suspeito.id(),
                            "Sequência k) / I) / m): verificar se o I maiúsculo deveria ser l minúsculo."));
                }
                break;
            }
        }
    }
}
