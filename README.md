# BaseIA Java — ingestão normativa

Projeto Java 21 / Spring Boot 4.1.2 / Spring AI 2.0.1 para preparar normas da Marinha (MinerU) e legislação em HTML para indexação no Qdrant.

O antigo laboratório de chat, agentes, retrieval e interface foi removido. Os parsers existentes foram mantidos e a nova etapa constrói **nós com hierarquia explícita**, valida inconsistências, produz **chunks contextualizados** e um **payload enxuto**. O preparo não precisa de LLM, embeddings ou Qdrant disponíveis.

## Fluxo

1. `MineruReader` / `HtmlLegislacaoReader`: leitura, texto e metadados de origem.
2. `ParserNormasService` / `ParserLegislacaoService`: classificação e agrupamento determinístico. `parseCompleto()` preserva também capítulos/seções e dispositivos sem prosa para a árvore.
3. `ConstrutorArvoreNormativa`: IDs determinísticos, `paiId`, `ancestraisIds` e escopos separados para artigos citados em leis.
4. `ValidadorArvoreNormativa`: alertas de inconsistência e possível confusão `I)`/`l)` sem reparar OCR.
5. `ChunkerContextual`: reúne o dispositivo com conteúdo textual diretamente subordinado, inclusive continuidades de página; limite inicial de 4.000 caracteres por ponto.
6. `QdrantPayloadBuilder` / `IndexadorQdrant`: texto fiel e `hierarchy` separados; embeddings apenas no endpoint explícito de indexação.

## Rodar e preparar

```powershell
.\mvnw.cmd spring-boot:run
```

JSON MinerU `docvortex.middle` versão `2.0`, com `codigo` e `versao` informados pelo chamador:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/ingestao/normas/preparar?codigo=SGM-303&versao=7REV" `
  -H "Content-Type: application/json" `
  --data-binary "@C:\dados\SGM-303-extraida.json" `
  --output "preparacao-sgm303.json"
```

HTML de lei:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/ingestao/legislacao/preparar?codigo=LEI-14129&versao=2021" `
  -H "Content-Type: text/html; charset=utf-8" `
  --data-binary "@C:\dados\lei-14129.html" `
  --output "preparacao-lei.json"
```

O preparo retorna `documento.nos`, `chunks` (com `texto` e `textoEmbedding`) e `alertas`. O envio dos PDFs para o MinerU é feito fora deste projeto. **É necessário refazer as extrações antigas** antes de analisar corpus real; os JSONs anteriores foram removidos.

As rotas de parser plano continuam:

| Método e rota | Entrada | Saída |
| --- | --- | --- |
| `POST /ingestao/normas/parse` | JSON MinerU | Lista útil de `ElementoNormativo` |
| `POST /ingestao/legislacao/parse` | `text/html` | Lista útil de `ElementoLegislacao` |
| `GET /ingestao/legislacao/parse?url=...` | URL HTTP(S) | Lista útil de `ElementoLegislacao` |

Os GETs de exemplos sem entrada e o endpoint legado `/legislacao` não existem mais.

## Embeddings e Qdrant

Por padrão os modelos estão desativados. Para indexar usando um provedor OpenAI compatível, configure, por exemplo:

```powershell
$env:SPRING_AI_MODEL_EMBEDDING = 'openai'
$env:OPENAI_API_KEY = 'sua-chave'
$env:BASEIA_EMBEDDING_MODEL = 'text-embedding-3-small'
$env:BASEIA_QDRANT_URL = 'http://localhost:6333'
$env:BASEIA_QDRANT_COLLECTION = 'normas_v1'
$env:BASEIA_QDRANT_VECTOR_NAME = 'dense'
```

O modelo de embeddings também pode usar as propriedades do Spring AI para `base-url`, `api-key` e provedor. A coleção é criada com dimensão baseada no primeiro embedding, caso não exista. Uma coleção existente precisa possuir o vetor nomeado e as dimensões correspondentes.

Com a configuração pronta, troque `/preparar` por `/indexar` na mesma requisição. Se houver alertas, a indexação retorna **409**; depois de revisar os casos, `?confirmarAlertas=true` permite a operação. Não há exclusão automática de pontos antigos numa reindexação: planeje a limpeza da versão/coleção quando os algoritmos de chunking mudarem.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes de MinerU/normas são sintéticos e os de legislação preservam um HTML em `src/test/resources/ingestao/legislacao/`. Nenhuma extração JSON antiga faz parte do projeto. Consulte [detalhes da ingestão](docs/ingestao-normas.md) e [convenções](AGENTS.md).
