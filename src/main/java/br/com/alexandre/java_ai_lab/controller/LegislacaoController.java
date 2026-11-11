package br.com.alexandre.java_ai_lab.controller;

import br.com.alexandre.java_ai_lab.service.ingestao.DownloadLegislacaoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Expõe o HTML baixado da legislação por meio do endpoint legado. */
@RestController
public class LegislacaoController {
    private final DownloadLegislacaoService downloadLegislacaoService;

    /**
     * Cria o controller com o serviço responsável por obter o documento.
     *
     * @param downloadLegislacaoService serviço de download e extração do HTML
     */
    public LegislacaoController(DownloadLegislacaoService downloadLegislacaoService) {
        this.downloadLegislacaoService = downloadLegislacaoService;
    }

    /**
     * Retorna o conteúdo HTML associado à URL informada.
     *
     * @param url endereço da legislação a baixar
     * @return HTML do corpo do documento
     * @throws RuntimeException quando ocorre falha ao baixar o documento
     */
    @GetMapping(value = "/legislacao", produces = MediaType.TEXT_HTML_VALUE)
    public String legislacao(@RequestParam String url) {
        try {
            return downloadLegislacaoService.parse(url).content();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
