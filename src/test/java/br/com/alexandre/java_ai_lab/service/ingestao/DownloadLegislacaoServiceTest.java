package br.com.alexandre.java_ai_lab.service.ingestao;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica o tráfego HTTP real do Jsoup usando um proxy e uma origem locais. */
class DownloadLegislacaoServiceTest {
    /** O proxy deve receber tanto a página inicial quanto o link de texto atualizado. */
    @Test
    void baixaPaginaETextoAtualizadoPeloProxy() throws Exception {
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requisicoes = new ArrayList<>();
        proxy.createContext("/", exchange -> {
            String destino = exchange.getRequestURI().toString();
            requisicoes.add(destino);
            String html = destino.contains("-norma-")
                    ? "<a href='http://legislacao.invalid/atualizada.html'>Texto atualizado</a>"
                    : "<html><title>Lei de teste</title><body>Art. 1º Conteúdo.</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var saida = exchange.getResponseBody()) { saida.write(bytes); }
        });
        proxy.start();
        try {
            var download = new DownloadLegislacaoService(true, "127.0.0.1", proxy.getAddress().getPort());
            var resultado = download.parse("http://legislacao.invalid/lei-norma-1.html");
            assertThat(requisicoes).containsExactly("http://legislacao.invalid/lei-norma-1.html",
                    "http://legislacao.invalid/atualizada.html");
            assertThat(resultado.sourceUrl()).isEqualTo("http://legislacao.invalid/atualizada.html");
            assertThat(resultado.content()).contains("Art. 1º Conteúdo.");
        } finally {
            proxy.stop(0);
        }
    }

    /** Com o proxy desabilitado, o download usa conexão direta; o servidor local simula a fonte. */
    @Test
    void baixaDiretamenteQuandoProxyDesabilitado() throws Exception {
        HttpServer origem = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origem.createContext("/", exchange -> {
            byte[] bytes = "<html><body>Lei local</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var saida = exchange.getResponseBody()) { saida.write(bytes); }
        });
        origem.start();
        try {
            var download = new DownloadLegislacaoService(false, "127.0.0.1", 1);
            assertThat(download.parse("http://127.0.0.1:" + origem.getAddress().getPort()).content())
                    .contains("Lei local");
        } finally {
            origem.stop(0);
        }
    }
}
