# Parser de publicações normativas

## Escopo implementado

O fluxo transforma um documento JSON `docvortex.middle` versão `2.0` em uma lista plana de elementos normativos com posição, origem e referências opcionais de hierarquia. [`MineruReader`](../src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/leitura/MineruReader.java) valida e lê o formato de entrada, retornando `List<BlocoMineru>` sem filtrar os blocos brutos; [`IdentificadorPaginasPreliminares`](../src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/filtragem/IdentificadorPaginasPreliminares.java) é uma classe pura que identifica páginas de capa e divisão de parte a partir dessa lista tipada. [`ParserNormasService`](../src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/ParserNormasService.java) coordena filtros, seleção, estado e chamada ao classificador. O parser não manipula JSON nem depende de Jackson. O classificador e suas regras ficam em `service/ingestao/classificacao`; `MontadorContextoNormativo` anexa snapshots de pais sem mudar ordem, conteúdo ou origem. Depois da montagem, a orquestração do parser omite somente `CAPITULO`, `ARTIGO` e `INCISO` da lista HTTP; esses elementos continuam disponíveis internamente para compor `pais`. O DTO [`ElementoNormativo`](../src/main/java/br/com/alexandre/java_ai_lab/dto/ElementoNormativo.java) é servido pelo adaptador HTTP [`ParserNormasController`](../src/main/java/br/com/alexandre/java_ai_lab/controller/ParserNormasController.java); as rotas e os parâmetros GET/POST não mudam, e a resposta inclui o campo `pais` nos elementos restantes.

O recorte e as observações de layout estão em [`regras_observacoes.md`](../src/test/resources/ingestao/mineru/regras_observacoes.md). Esse material descreve seis publicações como amostra e validações pendentes; a fixture normativa usada nos testes é [`SGM-303-Rev7.json`](../src/test/resources/ingestao/mineru/SGM-303-Rev7.json), empacotada também em `src/main/resources/exemplos/` para a chamada GET provisória. Os testes de leitura, serviço, classificação e controller cobrem essa amostra; não comprovam a validação das seis publicações.

## HTTP

`GET /ingestao/normas/parse` sem parâmetros carrega a cópia de `SGM-303-Rev7.json` empacotada no classpath e retorna o resultado parseado como array JSON. Esta rota é provisória; não retorna o JSON original. Para executar a chamada e gravar o resultado em `target/SGM-303-Rev7-elementos.json`:

```powershell
curl.exe --fail-with-body "http://localhost:8080/ingestao/normas/parse" `
  --output "target/SGM-303-Rev7-elementos.json"
```

`POST /ingestao/normas/parse` recebe e devolve `application/json`. Envie o JSON do MinerU diretamente no corpo da requisição, sem encapsulá-lo em outro objeto. Documento inválido ou JSON malformado resulta em HTTP 400. Anexos não são incluídos por padrão; o exemplo permite explicitamente A e C:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/ingestao/normas/parse?anexosPermitidos=A&anexosPermitidos=C" `
  -H "Content-Type: application/json" `
  --data-binary "@src/test/resources/ingestao/mineru/SGM-303-Rev7.json" `
  --output "target/parser-normas.json"
```

`anexosPermitidos` é opcional e aceita letras de anexos como valores repetidos na query string. A comparação é case sensitive: `A` não equivale a `a`. Sem parâmetros, nenhum anexo é incluído. A permissão é uma decisão fornecida pelo chamador.

## Resposta

A resposta é um array JSON de objetos com os campos:

| Campo | Significado |
| --- | --- |
| `tipo` | Categoria do elemento, conforme o enum abaixo. |
| `marcador` | Marcador reconhecido (`CAPÍTULO 1`, numeração, alínea, subalínea ou hífen de lista), ou `null` quando não atribuído. |
| `texto` | Texto extraído; para capítulo com título reconhecido, reúne marcador e título em uma linha. Preserva HTML de tabela e LaTeX de equação. |
| `paginaFisica` | Posição física da página em base 1 (`page_idx + 1`). |
| `paginaImpressa` | Rótulo extraído do primeiro bloco `page_number` disponível naquela página; `null` se ausente. |
| `indiceOrigem` | `index` do bloco de origem, inclusive para conteúdo que estava dentro de um wrapper `list`. |
| `bbox` | Caixa delimitadora original como quatro coordenadas normalizadas entre 0 e 1. |
| `continuaAnterior` | Valor de `continues_prev` do bloco, ou `false` quando ausente. É o sinal de origem; não indica continuidade inferida pelo parser. |
| `pais` | Objeto com snapshots dos níveis normativos pais, sem incluir o próprio elemento; cada referência não reconhecida é `null`. |
| `origens` | Lista ordenada dos blocos MinerU que compõem a unidade; cada entrada conserva tipo e marcador originais, páginas, índice, `bbox` e sinal de continuação, sem duplicar o texto. |

`pais` contém `capitulo`, `artigo`, `inciso`, `alinea`, `subalinea` e `anexo`. Cada referência guarda o texto original completo do elemento estrutural ativo, inclusive em inciso, alínea e subalínea; o montador não tenta distinguir título de prosa normativa. Em resposta, os elementos `CAPITULO`, `ARTIGO` e `INCISO` são omitidos, mas seus textos estruturais continuam sendo usados como contexto. `ALINEA` e `SUBALINEA` permanecem como registros individuais com tipos próprios. Se o artigo ativo for `1.1`, um inciso `1.2.1` não herda esse artigo: o marcador numérico precisa confirmar o pai ativo, e uma relação não confirmada fica `null`.

```json
{
  "tipo": "ALINEA",
  "marcador": "a)",
  "texto": "a) exemplo de conteúdo",
  "paginaFisica": 14,
  "paginaImpressa": "1-1",
  "indiceOrigem": 13,
  "bbox": [0.1, 0.2, 0.9, 0.24],
  "continuaAnterior": false,
  "origens": [
    {
      "tipo": "ALINEA",
      "marcador": "a)",
      "paginaFisica": 14,
      "paginaImpressa": "1-1",
      "indiceOrigem": 13,
      "bbox": [0.1, 0.2, 0.9, 0.24],
      "continuaAnterior": false
    }
  ],
  "pais": {
    "capitulo": "CAPÍTULO 1. ESTRUTURA BÁSICA DA GESTÃO PATRIMONIAL",
    "artigo": "1.2. Regras gerais",
    "inciso": "1.2.1. Execução Financeira",
    "alinea": null,
    "subalinea": null,
    "anexo": null
  }
}
```

O objeto `pais` é parte da resposta, não uma árvore aninhada nem um endpoint de consulta. A lista permanece plana e em ordem documental; `CAPITULO`, `ARTIGO` e `INCISO` são filtrados depois da montagem da hierarquia e do agrupamento. Capítulo e anexo são raízes com pais vazios; artigo herda capítulo; inciso herda capítulo e artigo; alínea herda os níveis ativos até inciso quando a numeração permite, podendo pular inciso; subalínea também herda alínea. `TEXTO`, `TABELA`, `EQUACAO` e os itens antes de serem agrupados recebem os níveis ativos disponíveis. Assim, `TEXTO` diretamente sob capítulo permanece na resposta com `artigo`, `inciso`, `alinea` e `subalinea` nulos; diretamente sob artigo, permanece com `inciso`, `alinea` e `subalinea` nulos; diretamente sob inciso, permanece com `alinea` e `subalinea` nulos. Os níveis ancestrais reconhecidos são preenchidos normalmente. Um inciso `1.2.1` é contexto interno, não registro da resposta, e pode aparecer em `pais.inciso` de uma lista ou elemento posterior. Texto diretamente sob esse inciso herda o texto completo em `pais.inciso`. Uma referência não confirmada permanece `null`, sem reconstrução ou inferência de texto. Para diferenciar números repetidos, considere os pais junto ao marcador; por exemplo, o inciso `1.2.1` somente se associa ao artigo `1.2` quando essa relação está ativa e confirmada.

Ao reconhecer um nível, esse nível e todos os inferiores são limpos. Um capítulo ou anexo inicia uma nova raiz e limpa todos os níveis; paginação não reinicia contexto. O anexo mantém o marcador original até o título ficar disponível; depois a referência é `ANEXO A. TÍTULO`. O elemento `TITULO_ANEXO` permanece separado na saída.

Os valores de `tipo` que podem aparecer na resposta incluem `ALINEA`, `SUBALINEA`, `LISTA`, `TEXTO`, `ANEXO`, `TITULO_ANEXO`, `TABELA` e `EQUACAO`. `ITEM_LISTA` é reunido em `LISTA`, inclusive quando ocorre uma vez. `CAPITULO`, `ARTIGO` e `INCISO` participam da classificação e da montagem de contexto, mas são omitidos da lista HTTP. `TITULO_CAPITULO` continua definido no enum para classificar internamente o bloco de título e controlar o fluxo, mas não aparece como registro de saída.

## Agrupamento do conteúdo

Depois da classificação e da montagem de `pais`, `AgrupadorConteudoNormativo` recebe a lista completa em ordem documental, inclusive `CAPITULO`, `ARTIGO` e `INCISO`. Esses três tipos são barreiras durante o agrupamento e só são filtrados depois, para a resposta HTTP. O agrupador é uma classe pura em `service/ingestao/agrupamento`; não lê JSON nem depende do container Spring. Uma troca de página sozinha não termina um grupo.

O agrupador tem dois modos. `TEXTO` consecutivos com `pais` iguais são reunidos com uma única quebra `\n`. `ITEM_LISTA` consecutivos com `pais` iguais formam uma unidade `LISTA`, mesmo quando há apenas um item; o tipo externo é `LISTA`, o marcador externo é `null` e cada linha conserva o marcador original, sem indentação acrescentada. `ALINEA` e `SUBALINEA` são registros individuais preservados e nunca se convertem em `LISTA` nem absorvem texto adjacente. Um `TEXTO` próximo de um item permanece independente e encerra a sequência da lista. Pais diferentes ou qualquer tipo intermediário encerram o agrupamento; todos os demais tipos passam sem alteração.

Os campos de origem legados (`paginaFisica`, `paginaImpressa`, `indiceOrigem`, `bbox` e `continuaAnterior`) permanecem ancorados no primeiro bloco da unidade; a `bbox` não é ampliada para cobrir o grupo. `origens` mantém uma entrada `OrigemNormativa` por bloco original que compõe o elemento, inclusive alíneas e subalíneas preservadas individualmente, na ordem documental, com tipo e marcador originais, páginas, índice, `bbox` e sinal de continuação. O texto não é duplicado em cada entrada de origem: permanece uma vez no elemento. O construtor de `ElementoNormativo` com nove argumentos segue criando uma origem individual, e `comPais` conserva as origens já existentes.

O agrupamento do conteúdo e a junção do título de capítulo são dois processos distintos. O título reconhecido continua sendo unido ao registro estrutural do capítulo pelas regras atuais; não é separado de prosa por heurística. O agrupador não repara OCR nem recupera frases cujo início foi descartado como cabeçalho. Também não usa `continuaAnterior` para decidir que blocos pertencem juntos. `TABELA`, `EQUACAO`, anexos e títulos passam individualmente e sem alteração pelo agrupador. Para detalhes das responsabilidades, consulte [AGENTS.md](../AGENTS.md).

### Validação consolidada

Na medição final, a SGM-301 resultou em 1.243 unidades e 1.553 origens, com 605 registros `ALINEA` e 259 `SUBALINEA` preservados individualmente. A SGM-303 resultou em 1.280 unidades e 1.404 origens, mantendo intacto o golden de regressão. Os 56 testes selecionados passaram.

Na SGM-303, os oito títulos que sucedem marcadores de capítulo são reconhecidos internamente e incorporados ao contexto completo de cada capítulo. Esses registros estruturais não são exemplos de resposta porque `CAPITULO` é filtrado após a montagem da hierarquia. O texto unido usa o marcador e o título reconhecido, com espaços em branco consecutivos normalizados para um espaço. Página, índice, caixa e sinal de continuação vêm do bloco do marcador; os metadados do bloco de título não substituem os do marcador.

## Ordem, texto e classificação

`MineruReader.ler(JsonNode)` exige `schema: "docvortex.middle"`, `schema_version: "2.0"` e `pages` como array. Valida índices e `bbox`, ordena páginas por `page_idx` e blocos por `index`; aplica a mesma ordenação recursivamente a wrappers `list`, achatando-os sem emitir o contêiner ou duplicar blocos internos. Extrai o texto percorrendo strings, objetos com `content` e arrays, concatenando fragmentos sem inserir separadores. Espaços ausentes entre fragmentos não são corrigidos. O resultado `BlocoMineru` preserva origem, texto, página física/impressa, índice de origem, `bbox` e sinal de continuação; a leitura não filtra nem classifica blocos.

Antes da seleção normativa, `IdentificadorPaginasPreliminares.identificar(List<BlocoMineru>)`, uma classe pura em `service/ingestao/filtragem`, retorna `Set<Integer>` com as páginas preliminares identificadas. `MineruReader` continua preservando o JSON MinerU completo e não filtra ou classifica os blocos. O identificador reconhece capas com o padrão institucional `MARINHA DO BRASIL`, ao menos um heading centralizado distinto da instituição e dos metadados (obtido de `doc_title`/`paragraph_title` ou de texto em caixa alta centralizado) e pelo menos duas categorias editoriais distintas entre volume, revisão/edição, tipo (qualquer valor) e finalidade (qualquer valor). Blocos de origem `header`, `footer` e `page_number` não contribuem como evidência de capa. Órgão emissor, título, ano e código do documento são irrelevantes para essa combinação. O detector reutiliza `RegraCapitulo` para preservar uma abertura normativa reconhecida, mesmo que a página tenha metadados editoriais de capa. A generalização para heading `INSTRUÇÕES`, volume IX, 3ª edição e ano distinto é um cenário sintético automatizado para outra publicação da Marinha, da EMA; não é relato de conferência de publicação real. Não se afirma cobertura universal de PDFs; a palavra `VOLUME` em prosa, sozinha, não exclui uma página.

Uma folha de divisão de parte é identificada por `PARTE` isolada e centralizada, junto de entradas `CAPÍTULO N - ...` na mesma folha, que remetem aos capítulos seguintes. O ordinal pode faltar por OCR; `MineruReader` preserva a origem `equation_inline`, por isso o detector também reconhece o ordinal LaTeX `3^{a} PARTE`. A forma sem ordinal continua válida. `CAP N` isolado não confirma uma divisão. Ao identificar capa ou divisão, o parser suspende a inclusão e reinicia o contexto hierárquico; a leitura normativa retoma por uma abertura reconhecida por `RegraCapitulo` ou em anexo permitido, sem herdar uma alínea ou outro contexto da parte anterior. Na SGM-301, a conferência local identificou capas nas páginas físicas 3, 19, 91 e 180 e folhas de divisão nas páginas 20, 70, 92, 142, 166, 176, 181, 199 e 207. Esses números são evidência do corpus, não condição de reconhecimento.

O parser percorre os blocos tipados em sequência e, após uma página preliminar, retoma a inclusão ao encontrar uma abertura reconhecida por `RegraCapitulo` ou um anexo permitido. A regra aceita marcador isolado `CAPÍTULO N` com ponto opcional. Também aceita, em um único bloco centralizado (inclusive `paragraph_title`) e sem pontuação entre marcador e título, `CAPÍTULO N TÍTULO` quando o título começa com letra maiúscula e está todo em caixa alta. O parser registra esse caso como `CAPÍTULO N. TÍTULO` e não consome o bloco seguinte como título. Entradas de sumário `CAPÍTULO N. TÍTULO` e `CAPÍTULO N - Título` são rejeitadas, mesmo se o título estiver em caixa alta; preservar o golden foi o que revelou esse falso positivo. Assim, o formato unido cobre uma abertura real sem ampliar a captura de linhas de sumário. A classificação é isolada em regras puras e ordenadas, com primeira correspondência vencedora: `RegraCapitulo`, `RegraAnexo`, `RegraTitulo`, `RegraConteudoEstruturado` (origens `table` e `equation`), `RegraNumeracao` (artigo/inciso), `RegraAlinea`, `RegraSubalinea` e `RegraItemLista`; sem correspondência, o resultado é `TEXTO`. A classificação recebe texto, tipo de origem e `bbox`, derivados de cada `BlocoMineru`, além do contexto de capítulo e título esperado. As regras retornam tipo e marcador e não alteram estado. O parser permanece responsável por transições e filtros normativos; o reader mantém validação e extração do formato de origem.

Os padrões textuais incluem numeração de artigo/inciso iniciada pelo número do capítulo ativo, alíneas minúsculas (`a)`/`ab)`), subalíneas em algarismos romanos maiúsculos (`I)`) e listas iniciadas por hífen ou travessão. Os marcadores explícitos e a sequência prevalecem sobre rótulos de classificação do MinerU. A ordenação é explícita no classificador; as regras não são descobertas por reflection nem ordenadas pelo container Spring.

O reader valida que cada `index` e `page_idx` seja inteiro não negativo e que cada `bbox` tenha quatro coordenadas numéricas normalizadas entre 0 e 1, sem eixos invertidos. O parser também aplica a heurística de margem vertical A4: considera uma faixa de 2 cm em página de 29,7 cm (`2 / 29.7`). Exclui o bloco quando sua caixa está inteiramente na faixa superior (`y1 <= 2/29.7`) ou na inferior (`y0 >= 1 - 2/29.7`). Também filtra origens `header`, `footer` e `page_number`, texto vazio e `INTENCIONALMENTE EM BRANCO`. O reader conserva esses blocos de origem antes da seleção normativa.

### Anexos

Um marcador isolado `ANEXO A` ou `ANEXO` seguido de uma ou duas letras maiúsculas e aproximadamente centralizado suspende a inclusão do corpo até o bloco útil seguinte, tratado como `TITULO_ANEXO`. O anexo só é incluído quando sua letra consta em `anexosPermitidos` e o título não contém `MODELO`, `MODELOS`, `EXEMPLO`, `EXEMPLOS` ou `FORMULARIO(S)`, após remoção de acentos e conversão para maiúsculas. Quando aceito, o marcador e o título entram na saída e os blocos seguintes voltam a ser incluídos.

Essa regra combina permissão explícita e palavras vetadas no título. Não analisa automaticamente a utilidade do anexo; não há corpo de anexo na fixture normativa atual que demonstre esse fluxo.

## Limitações conhecidas

- A identificação de capa e folha de divisão usa os sinais de layout e metadados descritos acima; não identifica genericamente índice ou sumário. Material anterior ao primeiro capítulo reconhecido fica fora do resultado por causa do estado inicial de inclusão.
- Título de capítulo isolado no bloco seguinte continua sujeito ao fluxo existente; para abertura real em bloco único, `RegraCapitulo` aceita título em caixa alta sem pontuação após o marcador. As entradas de sumário `CAPÍTULO N. TÍTULO` e `CAPÍTULO N - Título` permanecem fora dessa regra, inclusive com título em caixa alta. Título de capítulo reconhecido é incorporado ao texto do registro `CAPITULO`; o título de anexo permanece como `TITULO_ANEXO`.
- Todo bloco inteiramente dentro das faixas superior e inferior de 2 cm é descartado por heurística, inclusive conteúdo legítimo que por acaso ocupe essas regiões.
- A geometria horizontal é usada apenas para decidir a centralização aproximada de capítulo/anexo (centro do `bbox` com tolerância de `±0.1`). Não usa recuos para classificar alíneas ou subalíneas; tampouco usa fonte, negrito ou espaçamento entre linhas.
- As referências são calculadas conforme os elementos reconhecidos pelo classificador atual; não garantem que todas as relações e títulos da norma estejam reconhecidos. Níveis ausentes ficam `null`; não repara OCR, não reconstrói estrutura perdida e não garante todas as anotações da SGM-301. `continuaAnterior` apenas preserva `continues_prev` informado pelo MinerU.
- A montagem da hierarquia não agrega blocos, não deduplica, não reprocessa JSON e não usa detector OCR ou semântico. Depois dessa etapa, o agrupador reúne somente os parágrafos e itens de lista descritos acima; não reclassifica conteúdo nem converte prosa estrutural em `TEXTO`. O filtro HTTP segue omitindo somente `CAPITULO`, `ARTIGO` e `INCISO` após a montagem e o agrupamento.
- O golden da SGM-303 permanece intacto; essa amostra não tem capas ou folhas de divisão internas. Uma chamada HTTP à SGM-301 confirmou também texto diretamente sob `1.2.1`, com `pais.inciso` contendo o título completo e `alinea`/`subalinea` nulos. A validação integral de todos os vínculos das publicações permanece pendente.
- A classificação depende de padrões textuais específicos: capítulos podem ser marcadores isolados centralizados ou headings unidos sem pontuação, com título em caixa alta; anexos precisam de marcador isolado e centralizado. Alíneas são letras minúsculas e subalíneas romanos maiúsculos. Outros formatos podem permanecer como `TEXTO`.
- O fluxo de anexos depende da lista de permissões e da negação por palavras no título; não existe análise automática de utilidade nem validação de corpo de anexo no fixture.
- O teste de regressão mantém intacto o resultado esperado original da SGM-303 e projeta fora do esperado apenas os tipos de contexto omitidos da resposta. Para comparar após o agrupamento, use `origens` para achatar a saída e verificar, por bloco, os metadados anteriores e o texto original na unidade, normalizando somente whitespace; não gere novo golden. Testes específicos cobrem hierarquia e agrupamento. Isso não comprova a correção de cada classificação nem a validação das seis normas mencionadas nas observações.

## Testes selecionados

[`MineruReaderTest`](../src/test/java/br/com/alexandre/java_ai_lab/service/ingestao/leitura/MineruReaderTest.java) cobre validação, ordenação, texto, flatten e preservação dos artefatos de página; `IdentificadorPaginasPreliminaresTest` cobre os padrões de capa/divisão, incluindo o cenário sintético automatizado da EMA; `TransicaoVolumeNormativoTest` cobre suspensão e retomada da inclusão e a limpeza do contexto hierárquico. [`ParserNormasServiceTest`](../src/test/java/br/com/alexandre/java_ai_lab/service/ingestao/ParserNormasServiceTest.java) cobre seleção normativa no fixture SGM-303, anexos e preservação de tabela/equação. [`ClassificadorNormativoTest`](../src/test/java/br/com/alexandre/java_ai_lab/service/ingestao/classificacao/ClassificadorNormativoTest.java) cobre as regras e sua precedência. `MontadorContextoNormativoTest` cobre snapshots, confirmação de relações numéricas e limpeza dos níveis inferiores; `AgrupadorConteudoNormativoTest` cobre agrupamento de texto/itens e preservação de origens. [`ParserNormasControllerTest`](../src/test/java/br/com/alexandre/java_ai_lab/controller/ParserNormasControllerTest.java) cobre query, resposta HTTP (incluindo `pais`) e erros 400. O controller usa `MockMvcBuilders.standaloneSetup`, sem iniciar o contexto Spring externo. Para executar esses testes e o teste de controller de Retrieval no PowerShell:

```powershell
mvn -q '-Dtest=MineruReaderTest,IdentificadorPaginasPreliminaresTest,TransicaoVolumeNormativoTest,ParserNormasServiceTest,ClassificadorNormativoTest,MontadorContextoNormativoTest,AgrupadorConteudoNormativoTest,ParserNormasControllerTest,RetrievalControllerTest' test
```

## Parsing de leis em HTML

`ParserLegislacaoController` segue o padrão Reader → Parser do controller de normas:

`HTML → HtmlLegislacaoReader → ParserLegislacaoService → ClassificadorLegislacao → MontadorContextoNormativo.montarLegislacao → AgrupadorConteudoLegislacao → filtro de contexto → List<ElementoLegislacao>`.

O reader usa o Jsoup já instalado, aceita HTML completo ou fragmento e extrai `<p>`, headings `<h1>` a `<h6>` e tabelas na ordem DOM. Quando existe `.WordSection1`, essa área delimita a leitura. Conteúdo de tabelas permanece HTML e seus parágrafos internos não são duplicados; scripts, estilos e navegação ficam fora da extração. Imagens não produzem texto. O reader continua preservando os blocos encontrados; cabeçalhos institucionais e a fórmula de sanção são filtrados pelo parser antes do corpo normativo.

O classificador reutiliza `RegraClassificacao`, `BlocoClassificacao`, `ContextoClassificacao` e `Classificacao`, com lista explícita: `RegraConteudoEstruturado`, `RegraEstruturaLegislacao`, `RegraDispositivoLegal` e `RegraAlinea`. Sem correspondência, aplica `RegraCabecalhoLegislacao` somente quando o parser permite cabeçalho; o fallback continua sendo `TEXTO`. As regras de tabela/equação e alínea são as mesmas do fluxo MinerU. Nenhuma regra usada pelo HTML exige `bbox`.

Antes do primeiro capítulo, seção, subseção ou artigo da própria lei, e fora de citações, a identificação (`LEI Nº ...` ou `LEI COMPLEMENTAR Nº ...`, com número e data opcional) recebe `IDENTIFICACAO_LEI`. A ementa recebe `EMENTA` quando começa com expressões como “Dispõe sobre”, “Institui”, “Altera” ou “Estabelece”. Ambos permanecem separados, com texto integral, marcador nulo, pais vazios e suas próprias origens. O reconhecimento usa conteúdo e estado documental, nunca número da lei, data ou índice fixo da amostra. `indicesOrigem` serve apenas para rastreabilidade. As mesmas expressões no corpo ou em citações continuam sujeitas à classificação normal, sem se tornarem cabeçalhos. Texto preliminar que não corresponde aos padrões permanece `TEXTO`; não se presume que todo bloco seguinte à identificação seja ementa.

Os marcadores reconhecidos incluem `Art. 1º`, `Art. 12.`, `Art. 10-A.`, `§ 1º`, `Parágrafo único.`, incisos romanos com hífen/meia-risca/travessão, alíneas e itens decimais como `1.`. Esses dispositivos são classificados também quando aparecem dentro de citações. Capítulos, seções e subseções fornecem contexto, mas não aparecem como elementos de conteúdo. Marcadores mantêm sua grafia e pontuação originais. Um rótulo estrutural isolado pode ser reunido ao bloco seguinte quando esse bloco é heading ou tem centralização explícita e foi classificado como texto; os índices de ambos são conservados.

As transições são compartilhadas pelo montador existente. Cada abertura limpa o mesmo nível e os inferiores, preservando os ancestrais. `pais` contém `capitulo`, `secao`, `subsecao`, `artigo`, `paragrafo`, `inciso`, `alinea`, `item` e `artigoCitado`; o elemento não referencia a si mesmo. Incisos diretamente sob artigo têm `paragrafo: null`. Um novo parágrafo limpa incisos, alíneas e itens anteriores; um novo artigo limpa os níveis inferiores. `artigoCitado` guarda o artigo da redação alterada, enquanto `artigo` permanece o dispositivo da lei que introduziu a citação. Referências reproduzem o texto original do elemento estrutural depois da remoção dos pontilhados de omissão.

Na resposta, artigos, parágrafos, incisos e alíneas mantêm seus tipos individuais. Os capítulos, seções e subseções passam pelo montador como barreiras e são removidos depois dele. O agrupador da legislação reúne somente `TEXTO` consecutivo com mesmos pais e valor de `citacao`, usando `\n`, e itens `ITEM` consecutivos em `LISTA` com marcador externo `null`. As origens em `origens` conservam o tipo, o marcador, o índice DOM e a âncora de cada bloco; texto ao lado de item de lista continua separado. Os contratos do parser MinerU permanecem inalterados.

O campo booleano `citacao` distingue dispositivos de outra lei, inclusive artigos, parágrafos, incisos, alíneas e itens; estes são classificados e hierarquizados em escopo próprio. Redações iniciadas com aspas e `Art.` mantêm essa indicação até as aspas finais, com `(NR)` opcional; blocos `<blockquote>` também são marcados. Artigos citados compostos somente pelo marcador e pontilhado servem de contexto para seus descendentes, mas não são emitidos como conteúdo. Pontilhados de omissão (`...`, sequências mais longas e `…`) são retirados do texto. Isso preserva as redações úteis dos arts. 51 a 54 sem reproduzir lacunas.

Antes do corpo, o parser remove somente os rótulos institucionais conhecidos da amostra e a fórmula do Presidente/Faço saber; a identificação da lei e a ementa permanecem com os tipos próprios descritos acima. Um fecho de Brasília com data encerra o conteúdo da lei mesmo quando uma citação textual anterior ficou com aspas incompletas; fecho, data e assinaturas posteriores não são retornados. Uma data dentro de `<blockquote>` permanece como texto citado. Em transcrição entre aspas, a data também é preservada quando o fechamento explícito da citação aparece antes de outra abertura ancorada ou outro fecho. Títulos institucionais encontrados depois do corpo permanecem como conteúdo, em vez de serem removidos por palavra isolada.

O estado das citações textuais considera a paridade das aspas ASCII, tipográficas e angulares: pares de aspas internas não encerram a citação externa. Somente delimitadores externos reconhecidos são retirados do texto. A fronteira de saída de `<blockquote>` e um novo artigo da própria lei confirmado por âncora encerram o estado textual mesmo com aspas incompletas. Marcadores sem prosa, inclusive itens decimais cujo conteúdo era só pontilhado, continuam como barreiras internas e são descartados na saída, sem virar listas vazias ou unir grupos separados por uma omissão. A limpeza preserva a pontuação dos marcadores, incluindo o ponto de `1.`.

### HTTP do HTML

| Chamada | Resultado |
| --- | --- |
| `GET /ingestao/legislacao/parse` | Processa a cópia local da Lei 14.129/2021, sem rede. |
| `GET /ingestao/legislacao/parse?url=...` | Usa `DownloadLegislacaoService` para baixar o HTML e processá-lo. |
| `POST /ingestao/legislacao/parse` com `Content-Type: text/html; charset=UTF-8` | Processa o HTML enviado diretamente no corpo. |

Todas retornam `application/json` com a mesma lista plana. HTML sem blocos suportados ou sem artigo reconhecível resulta em 400; URL não HTTP(S) absoluta resulta em 400; falha de download resulta em 502. O endpoint anterior `GET /legislacao?url=...` continua retornando HTML.

Cada elemento contém `tipo`, `marcador`, `texto`, `indicesOrigem`, `ancora`, `pais`, `citacao` e `origens`. `origens` guarda uma entrada por bloco agrupado, com o tipo e marcador classificados, seus índices em base zero e âncora. `indicesOrigem` reúne as posições desses blocos no elemento resultante. Índices são relativos à seleção DOM (incluem a posição dos blocos vazios), não linhas do arquivo. Não há página, caixa geométrica ou sinal de continuação no contrato HTML.

Exemplo do inciso diretamente sob artigo:

```json
{
  "tipo": "INCISO",
  "marcador": "I -",
  "texto": "I - simplificar o atendimento.",
  "indicesOrigem": [1],
  "ancora": null,
  "pais": {
    "capitulo": null,
    "secao": null,
    "subsecao": null,
    "artigo": "Art. 1º São diretrizes desta Lei:",
    "paragrafo": null,
    "inciso": null,
    "alinea": null,
    "item": null,
    "artigoCitado": null
  },
  "citacao": false,
  "origens": [
    {
      "tipo": "INCISO",
      "marcador": "I -",
      "indicesOrigem": [1],
      "ancora": null
    }
  ]
}
```

Para baixar e processar a mesma fonte usada no exemplo:

```powershell
curl.exe --fail-with-body --get "http://localhost:8080/ingestao/legislacao/parse" `
  --data-urlencode "url=https://www2.camara.leg.br/legin/fed/lei/2021/lei-14129-29-marco-2021-791203-normaatualizada-pl.html"
```

### Validação e limites do HTML

Os testes usam a amostra fornecida sem modificá-la: 55 artigos próprios com âncoras `art1` a `art55`; capítulos, seções e subseções ficam apenas em `pais`; e 14 dispositivos úteis de redações citadas permanecem classificados sob seus artigos introdutores. A saída remove as lacunas pontilhadas, a fórmula de sanção e o fecho. Testes verificam pais, blocos agrupados e origens, além da equivalência HTTP entre GET e POST. O download é simulado nos testes e isso não comprova a disponibilidade atual de sites externos. A cópia em `src/main/resources/exemplos/` serve o GET local; a original permanece em `src/test/resources/ingestao/legislacao/`.

```powershell
mvn -q '-Dtest=HtmlLegislacaoReaderTest,ClassificadorLegislacaoTest,ParserLegislacaoServiceTest,AgrupadorConteudoLegislacaoTest,ParserLegislacaoControllerTest,MineruReaderTest,IdentificadorPaginasPreliminaresTest,TransicaoVolumeNormativoTest,ClassificadorNormativoTest,ParserNormasServiceTest,MontadorContextoNormativoTest,AgrupadorConteudoNormativoTest,ParserNormasControllerTest' test
```

Esta etapa lê parágrafos, headings e tabelas. Texto diretamente em `div`/`span` fora desses blocos e dispositivos separados somente por `<br>` não são emitidos como blocos distintos. Livros, partes e títulos não abrem níveis próprios. Citações sem aspas no padrão descrito ou sem `<blockquote>` podem exigir ajustes. O fluxo não interpreta semanticamente citações, não consolida alterações, não determina vigência e não exclui texto tachado ou vetado por suposição. O resultado é JSON; não há armazenamento, embeddings ou indexação no RAG.

## Organização dos materiais

- Orquestração do parser: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/ParserNormasService.java`.
- Leitura de entrada e bloco interno: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/leitura/`.
- Identificação de páginas preliminares: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/filtragem/IdentificadorPaginasPreliminares.java`.
- Classificação e regras: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/classificacao/`.
- Montagem dos pais: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/hierarquia/MontadorContextoNormativo.java`.
- Agrupamento de conteúdo: `src/main/java/br/com/alexandre/java_ai_lab/service/ingestao/agrupamento/AgrupadorConteudoNormativo.java`.
- DTO e enum da resposta: `src/main/java/br/com/alexandre/java_ai_lab/dto/ElementoNormativo.java`.
- Origem por bloco: `src/main/java/br/com/alexandre/java_ai_lab/dto/OrigemNormativa.java`.
- DTO dos pais: `src/main/java/br/com/alexandre/java_ai_lab/dto/PaisNormativos.java`.
- Endpoint: `src/main/java/br/com/alexandre/java_ai_lab/controller/ParserNormasController.java`.
- Regras e observações de layout: `src/test/resources/ingestao/mineru/regras_observacoes.md`.
- Fixture JSON: `src/test/resources/ingestao/mineru/SGM-303-Rev7.json` (testes) e `src/main/resources/exemplos/SGM-303-Rev7.json` (amostra empacotada usada pelo GET provisório).
- Testes selecionados: `src/test/java/br/com/alexandre/java_ai_lab/service/ingestao/{leitura/MineruReaderTest.java,ParserNormasServiceTest.java,classificacao/ClassificadorNormativoTest.java}` e `src/test/java/br/com/alexandre/java_ai_lab/controller/{ParserNormasControllerTest.java,RetrievalControllerTest.java}`.
