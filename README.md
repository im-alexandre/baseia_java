# Java AI Lab

Aplicação Java 21 com Spring Boot e Spring AI. O repositório contém fluxos de chat/recuperação e um parser determinístico de publicações normativas MinerU. O parser recebe `docvortex.middle` 2.0 e devolve elementos planos em ordem documental, com snapshots opcionais dos pais normativos e conteúdo consecutivo agrupado; capítulos, artigos e incisos são usados para montar a hierarquia, funcionam como barreiras ao agrupamento e são omitidos da resposta. O parser não persiste nem indexa a resposta.

## Executar

Use a configuração de ambiente já adotada para esta aplicação e inicie pela raiz do repositório:

```powershell
.\mvnw.cmd spring-boot:run
```

Com a aplicação em execução, faça uma chamada GET sem parâmetros para processar a amostra SGM-303 empacotada na aplicação. Esta rota é provisória e devolve o resultado parseado em JSON em `target/SGM-303-Rev7-elementos.json`:

```powershell
curl.exe --fail-with-body "http://localhost:8080/ingestao/normas/parse" `
  --output "target/SGM-303-Rev7-elementos.json"
```

Para enviar um documento ao parser via POST, use JSON diretamente no corpo, sem envelope. Este exemplo usa o fixture versionado e grava a resposta em `target/parser-normas.json`:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/ingestao/normas/parse" `
  -H "Content-Type: application/json" `
  --data-binary "@src/test/resources/ingestao/mineru/SGM-303-Rev7.json" `
  --output "target/parser-normas.json"
```

Anexos são excluídos por padrão no POST. Para permitir anexos revisados pelo chamador, repita o parâmetro, por exemplo `?anexosPermitidos=A&anexosPermitidos=C`. Documento incompatível ou JSON inválido resulta em HTTP 400.

## Parsing de leis em HTML

O fluxo HTML usa `HtmlLegislacaoReader` e `ParserLegislacaoService`, reaproveitando os contratos das regras, a classificação de alíneas/tabelas e as transições de `MontadorContextoNormativo`. Artigos, parágrafos, incisos e alíneas permanecem classificados. Capítulos, seções e subseções aparecem em `pais` como contexto. Textos e itens consecutivos são agrupados com suas classificações de origem. O contrato `ElementoLegislacao` conserva âncoras e índices DOM, sem paginação ou coordenadas artificiais. Esta etapa não persiste nem indexa no RAG.

O novo `ParserLegislacaoController` serve a Lei 14.129/2021 empacotada no GET sem parâmetros:

```powershell
curl.exe --fail-with-body "http://localhost:8080/ingestao/legislacao/parse"
```

Para processar HTML local:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/ingestao/legislacao/parse" `
  -H "Content-Type: text/html; charset=UTF-8" `
  --data-binary "@src/test/resources/ingestao/legislacao/lei_14129_2021.html" `
  --output "target/lei_14129_2021-elementos.json"
```

O GET também aceita `?url=...` para baixar e processar uma lei. `GET /legislacao?url=...` continua devolvendo o HTML. As regras, a resposta e os limites da leitura estão na [documentação do parsing](docs/ingestao-normas.md#parsing-de-leis-em-html).

Na amostra da Câmara, a leitura exclui o cabeçalho institucional, a fórmula de sanção e o fecho com as assinaturas. A identificação da lei e sua ementa aparecem separadamente como `IDENTIFICACAO_LEI` e `EMENTA`, reconhecidas pelo conteúdo antes do corpo normativo, sem depender dos índices de origem. Remove pontilhados de omissão e mantém redações alteradas sob o artigo da lei que as introduz. Em citações, `citacao` identifica os dispositivos citados e `pais.artigoCitado` identifica o artigo da outra lei; `pais.artigo` continua apontando para o artigo da lei atual.

## Organização do código

As camadas existentes são mantidas, com subpacotes por funcionalidade e responsabilidade:

| Pacote | Responsabilidade |
| --- | --- |
| `controller` | Adaptar HTTP e conectar Reader e Parser. |
| `dto` | Representar contratos de transporte, como `ElementoNormativo`. |
| `service/ingestao` | Coordenar o parsing normativo, os filtros e as transições de estado. |
| `service/ingestao/leitura` | Interpretar o JSON MinerU, ordenar blocos e preservar texto e origem. |
| `service/ingestao/filtragem` | Identificar páginas preliminares a partir dos blocos tipados, sem alterar a leitura bruta. |
| `service/ingestao/classificacao` | Reconhecer elementos por regras independentes, sem alterar o estado de leitura. |
| `service/ingestao/hierarquia` | Acrescentar a cada elemento referências opcionais aos níveis normativos ativos, preservando a lista plana e sua ordem. |
| `service/ingestao/agrupamento` | Agrupar parágrafos `TEXTO` consecutivos de mesmos pais e `ITEM_LISTA` consecutivos com mesmos pais, preservando todas as origens. |

O fluxo é `JSON → MineruReader → IdentificadorPaginasPreliminares → ParserNormasService → ClassificadorNormativo → MontadorContextoNormativo → AgrupadorConteudoNormativo → filtro HTTP → ElementoNormativo`.
O classificador aceita a primeira regra aplicável de uma lista com precedência explícita.
Após página preliminar, a inclusão retoma por capítulo reconhecido por `RegraCapitulo` ou por anexo permitido. A regra aceita `CAPÍTULO N` isolado com ponto opcional e o heading centralizado `CAPÍTULO N TÍTULO` em um único bloco (inclusive `paragraph_title`), sem pontuação entre marcador e título, quando o título começa com maiúscula e está todo em caixa alta. Rejeita entradas de sumário como `CAPÍTULO N. TÍTULO` e `CAPÍTULO N - Título`, mesmo em caixa alta.
Registros internos, como `BlocoMineru` e `ContextoClassificacao`, ficam junto da implementação;
o uso de `record` não os transforma em DTOs da API. As convenções completas estão no [AGENTS.md](AGENTS.md).

O agrupador reúne parágrafos `TEXTO` consecutivos com os mesmos pais por uma quebra `\n` e apenas blocos `ITEM_LISTA` consecutivos de mesmos pais em um elemento `LISTA`. `ALINEA` e `SUBALINEA` permanecem como registros individuais; texto adjacente não é absorvido por uma lista. Outros tipos passam individualmente, e mudança de página sozinha não separa grupos. O elemento mantém os metadados legados do primeiro bloco e adiciona `origens`, com tipo, marcador e metadados originais de cada bloco. Veja [a documentação da ingestão](docs/ingestao-normas.md) para as regras e limites.

Cada elemento retornado inclui `pais` com `capitulo`, `artigo`, `inciso`, `alinea`, `subalinea` e `anexo`; valores sem relação reconhecida ficam `null`. `CAPITULO`, `ARTIGO` e `INCISO` continuam no fluxo interno até a montagem das referências e o agrupamento, sendo então omitidos da resposta. Conteúdo `TEXTO` diretamente sob capítulo, artigo ou inciso é preservado: sob capítulo, artigo/inciso/alínea/subalínea ficam `null`; sob artigo, inciso/alínea/subalínea ficam `null`; sob inciso, alínea/subalínea ficam `null`. `ALINEA` e `SUBALINEA` aparecem como registros individuais; `ITEM_LISTA` consecutivos com mesmos pais podem ser agrupados em `LISTA`. Anexos, títulos de anexo, tabelas e equações passam individualmente.

```json
{
  "tipo": "TEXTO",
  "marcador": null,
  "texto": "Conjunto de atividades, devidamente integradas, que tem por finalidade executar...",
  "pais": {
    "capitulo": "CAPÍTULO 1. ATIVIDADES DE ADMINISTRAÇÃO FINANCEIRA E CONTABILIDADE",
    "artigo": "1.2. CONTA DE GESTÃO",
    "inciso": "1.2.1. Execução Financeira",
    "alinea": null,
    "subalinea": null,
    "anexo": null
  }
}
```

Para itens agrupados, `tipo` é `LISTA` e `marcador` externo é `null`; cada marcador original permanece na linha correspondente e em `origens`. As alíneas e subalíneas são devolvidas individualmente com seus tipos próprios. O golden de regressão SGM-303 permanece intacto; as medições estão na [documentação de validação](docs/ingestao-normas.md).

## Testes do parser e dos controllers

Na raiz, execute:

```powershell
mvn -q '-Dtest=MineruReaderTest,IdentificadorPaginasPreliminaresTest,TransicaoVolumeNormativoTest,ParserNormasServiceTest,ClassificadorNormativoTest,MontadorContextoNormativoTest,AgrupadorConteudoNormativoTest,ParserNormasControllerTest,RetrievalControllerTest' test
```

## Documentação

- [Ingestão e classificação de normas](docs/ingestao-normas.md): contrato HTTP, formato da resposta, precedência das regras e limites atuais.
- [Instruções para agentes](AGENTS.md): convenções locais de implementação e organização.
