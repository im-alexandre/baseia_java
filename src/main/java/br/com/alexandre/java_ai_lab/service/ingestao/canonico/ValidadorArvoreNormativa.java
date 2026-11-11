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

/** Diagnósticos de consistência que não alteram marcadores suspeitos nem reparentam elementos. */
@Service
public class ValidadorArvoreNormativa {
    /** Localiza referências inválidas e sequências compatíveis com confusão de OCR entre I e l. */
    public List<Alerta> validar(Documento documento) {
        List<Alerta> alertas = new ArrayList<>();
        Map<String, No> vistos = new HashMap<>();
        Map<String, String> marcadores = new HashMap<>();
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
            vistos.put(no.id(), no);
        }
        alertarConfusaoIl(documento.nos(), vistos, alertas);
        return List.copyOf(alertas);
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
