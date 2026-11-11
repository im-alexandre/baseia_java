# Ingestão normativa: contratos e limites

## Entradas

- **Normas MB:** o MinerU externo gera `docvortex.middle` 2.0. O `MineruReader` valida índices, `bbox`, páginas físicas/impressas, expansão de listas, `continues_prev` e glifos não mapeados (C0/`U+FFFD`). A classificação determina capítulos, seções numeradas (`ARTIGO` no parser legado), itens numerados (`INCISO` no parser legado), alíneas e subalíneas. Os marcadores `m)devolução` e `III)o bem` são reconhecidos sem exigir espaço após `)`. O parser filtra páginas preliminares e anexos sem permissão.
- **Leis:** o `HtmlLegislacaoReader` preserva referências DOM e âncoras, sem criar paginação artificial. `ParserLegislacaoService` trata identificação, ementa, capítulos, artigos, parágrafos, incisos, alíneas, artigos citados em alterações e fecho. O GET com `url` permite baixar o HTML da legislação.
- Ambos preservam `origens`; o parser legado `parse()` continua retornando a lista útil, enquanto `parseCompleto()` mantém também estruturas e marcadores vazios para construir a árvore.

## Contrato canônico

`ModeloCanonico.Documento` contém `codigo`, `versao`, `formato` e `nos`. Cada `No` contém:

| Campo | Uso |
| --- | --- |
| `id` | UUID determinístico por documento/edição, pai, tipo, marcador e ocorrência |
| `paiId` | Referência direta ao nó pai; `null` na raiz |
| `ancestraisIds` | Caminho da raiz ao pai imediato |
| `tipo`, `marcador`, `texto`, `ordem` | Classificação e texto fiel da extração |
| `citacao` | Se pertence a redação de outra lei transcrita |
| `fontes` | Páginas e caixas do MinerU **ou** índices/âncoras DOM, sem inventar coordenadas |

Os tipos `ARTIGO`/`INCISO` do MinerU designam seções e itens *numerados no padrão SGM*; não devem ser confundidos com os dispositivos jurídicos equivalentes de uma lei. A árvore respeita as referências de pai que o parser confirmou, mesmo se algum nível intermediário estiver ausente.

No HTML, artigos citados são filhos do dispositivo próprio que introduz a citação e abrem um escopo separado. Não se deve atribuir automaticamente vigência, revogação ou equivalência ao texto citado.

## Chunking

O `ChunkerContextual` cria unidade de recuperação centrada no dispositivo, incorporando seus filhos de `TEXTO`, `LISTA`, `ITEM_LISTA`, `TABELA` e `EQUACAO` diretamente subordinados. Capítulos/seções/títulos servem de contexto; uma seção numérica `ARTIGO` que contém apenas seu heading não gera ponto próprio. Texto direto desses níveis continua indexável, assim como os artigos de leis em HTML.

O campo `texto` conserva o conteúdo da unidade; `textoEmbedding` adiciona código da norma, edição e breadcrumbs tipados. Trechos maiores que 4.000 caracteres são divididos em fragmentos, rastreáveis por `nosCobertos`. Páginas inicial/final consideram as origens MinerU usadas pelo chunk, e ficam ausentes no HTML.

## Payload do Qdrant

O payload tem campos planos para filtros e citação, com `hierarchy` **separado** de `text`:

```json
{
  "schema_version": 1,
  "document_id": "SGM-303",
  "document_version": "7REV",
  "document_format": "MINERU",
  "text": "I) Organização Militar Consumidora Integrada\nÉ a denominação ...",
  "node_id": "uuid-do-no",
  "parent_id": "uuid-da-alinea",
  "ancestor_ids": ["uuid-capitulo", "uuid-secao", "uuid-item", "uuid-alinea"],
  "node_type": "SUBALINEA",
  "marker": "I)",
  "order": 42,
  "covered_node_ids": ["uuid-do-no", "uuid-do-texto-continuado"],
  "cited_text": false,
  "hierarchy": [
    {"id": "uuid-capitulo", "type": "CAPITULO", "marker": "CAPÍTULO 1", "label": "CAPÍTULO 1. ..."},
    {"id": "uuid-secao", "type": "ARTIGO", "marker": "1.3", "label": "1.3. ..."},
    {"id": "uuid-item", "type": "INCISO", "marker": "1.3.1", "label": "1.3.1. ..."},
    {"id": "uuid-alinea", "type": "ALINEA", "marker": "a)", "label": "a) ..."},
    {"id": "uuid-do-no", "type": "SUBALINEA", "marker": "I)", "label": "I) ..."}
  ],
  "page_start": 14,
  "page_end": 15,
  "printed_page_start": "1-1",
  "printed_page_end": "1-2"
}
```

Não há `section_map` embutido no texto, nem `bbox`, contadores temporários, estado de revisão ou `origens` completos em cada ponto. Para leis, o payload inclui também `source_anchor` quando disponível. A proveniência detalhada pertence ao documento canônico, que hoje é devolvido pelo endpoint de preparo e **ainda não possui persistência própria**.

O `IndexadorQdrant` usa os vetores nomeados configurados (`dense` por padrão), `PUT` em lotes de 32 pontos e IDs derivados dos chunks. A coleção é criada se não existir. Em coleção existente, formato/dimensões do vetor precisam ser compatíveis. **Reindexação não remove automaticamente pontos anteriores** cujos IDs deixaram de existir.

## Revisão e limitações

- `I)` é frequentemente um número romano legítimo. Uma sequência `k) / I) / m)` pode indicar troca entre `I` maiúsculo e `l` minúsculo; o validador emite alerta `OCR_I_OU_L`, sem corrigir ou reinterpretar a norma.
- Marcador repetido sob o mesmo pai emite alerta `MARCADOR_REPETIDO` para revisão, sem eliminar conteúdo.
- A ordem de irmãos `ARTIGO`, `INCISO`, `ALINEA` e `SUBALINEA` produz `SEQUENCIA_<TIPO>` quando salta ou retrocede; `VIII) → XIX) → X)` permanece exatamente como consta na fonte. Romanos de grafia não canônica produzem `ROMANO_INVALIDO`.
- Marcador de alínea/subalínea que sobrou em uma linha de `TEXTO` emite `MARCADOR_NAO_CLASSIFICADO`. O leitor recusa caracteres C0 indevidos ou `U+FFFD`; entradas canônicas externas com eles produzem `GLIFO_NAO_MAPEADO`.
- Hifens em quebras de linha, como `pedi-\ndos`, geram `HIFENIZACAO_AMBIGUA`. Não se remove automaticamente o hífen: pode pertencer a um termo composto. O texto fiel continua disponível para conferência.
- `IndexadorQdrant` exige confirmação explícita na presença de alertas. `GLIFO_NAO_MAPEADO` bloqueia a indexação mesmo com confirmação, pois não existe substituição automática confiável.
- `continuaAnterior` é mantido na proveniência; a associação semântica usa a hierarquia, não confia no sinal isolado do MinerU.
- Não há avaliação de vigência, consolidação de alterações legislativas, reranker, retrieval, agentes nem chat nesta fase.
- Os exemplos MinerU em `src/main/resources/exemplos/extracao_mineru_2` podem ser usados em auditoria manual; os testes unitários usam dados sintéticos. MAPCONT não possui uma abertura `CAPÍTULO` compatível com este parser SGM e requer política de classificação própria.

## Testes preservados

Leitura MinerU, páginas preliminares, classificação, transições de volume, montagem de contexto, agrupamento, parsing de HTML, citações legislativas e contratos HTTP. `PipelineCanonicoTest` verifica IDs, parentesco, continuação entre páginas, alertas, separação de citações, limite de chunks e formato do payload em casos sintéticos.
