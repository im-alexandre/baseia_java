package br.com.alexandre.java_ai_lab.tools;

import br.com.alexandre.java_ai_lab.service.RetrievalService;
import br.com.alexandre.java_ai_lab.service.ContextAssemblerService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class NormasTools {

    private final RetrievalService retrievalService;
    private final ContextAssemblerService assembler;

    public NormasTools(
            RetrievalService retrievalService,
            ContextAssemblerService assembler
    ) {
        this.retrievalService = retrievalService;
        this.assembler = assembler;
    }

    @Tool(
            name = "consultar_normas",
            description = """
            Pesquisa documentos e normas de gestão patrimonial
            da Marinha do Brasil.

            Utilize para responder dúvidas sobre procedimentos,
            sistemas patrimoniais e registros contábeis,
            
           """
    )
    public String consultarNormas(
            @ToolParam(description = "Pergunta ou assunto a pesquisar")
            String consulta
    ) {
        System.out.println("TOOL CALL: consultar_normas -> " + consulta);

        var resultados = retrievalService.buscar(consulta);

        return assembler.montar(resultados);
    }
}