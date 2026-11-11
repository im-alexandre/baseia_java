# Ingestão de publicações normativas da Marinha

O recorte é o das publicações normativas estruturadas segundo o EMA-411, com as seis normas SGM deste pacote como amostra. O objetivo é reconstruir a hierarquia e vincular cada trecho à seção e à página de origem. A legislação fica para outra etapa.

## Layout e hierarquia

O EMA-411 Rev. 7, Mod. 1 define a estrutura e o layout. Os itens 3.8.2 e 3.10 estão nas pp. impressas 3-6 a 3-11, correspondentes às páginas 28 a 33 do PDF. A alínea 3.10.1(a) remete à SGM-105 Rev. 6, item 1.4 e ao MaTDoc, item 1.2.2 para a tipografia.

| Elemento | Regra de layout |
| --- | --- |
| Página | A4 branco, preferencialmente em retrato. Margem esquerda de 3 cm e demais margens de 2 cm. Na impressão frente e verso, as margens laterais são espelhadas nas páginas pares. |
| Texto | Alinhamento justificado. Carlito ou Calibri, estilo normal, preto, 12 pt no texto geral, 11 pt nas citações e 10 pt nas notas de rodapé. |
| Capítulo | Começa em página ímpar. O marcador `CAPÍTULO 1` e o título ocupam linhas separadas, em maiúsculas, negrito e centralizados. |
| Artigo | `1.1.` à margem do corpo, seguido do título em maiúsculas e negrito. Tabulação de 1,50 cm na Tabela 1. |
| Inciso | `1.1.1.` à margem do corpo, com tabulação de 1,50 cm na Tabela 1. Pode ter título ou começar pelo texto, em fonte regular e com inicial maiúscula. |
| Alínea | `a)`, `b)` e assim por diante. Marcador a 1,50 cm e texto a 2 cm. Emprega inicial minúscula e fonte regular. |
| Subalínea | `I)`, `II)` e assim por diante. Marcador a 2 cm e texto a 3 cm. Pode haver parágrafos subordinados iniciados por hífen. |
| Cabeçalho e rodapé | Medida de 1,50 cm. No cabeçalho, a marcação e a sigla ficam à esquerda e à direita, respectivamente. No rodapé, a marcação fica à esquerda, a página ao centro e a edição ou revisão à direita. |
| Índice, páginas e anexos | O índice lista capítulos e artigos com suas páginas. Nas normas da amostra, a numeração preliminar usa romanos, o corpo usa capítulo e página (`1-1`) e os anexos usam letra e página (`A-1`). O marcador `ANEXO A` e seu título ficam centralizados em linhas separadas. |


## Extração e classificação determinística

1. Criar classes que representem os diferentes elementos, como corpo do Texto, título de capítulo, artigo, inciso, alínea e subalínea
2. Desconsiderar índices, sumários, capas, cabeçalhos, rodapés e outros elementos extra-textuais (com exceção dos ANEXOS, que podem ser mantidos em algumas situações)
3. ANEXOS que contém modelos, exemplos e formulários não devem ser ingeridos. Apenas anexos com informações úteis serão ingeridos, como tabelas de definição de contas, lista de tarefas ou definições, etc.
4. Utilizar regras **DETERMINÍSTICAS** baseadas nas regras de layout da tabela acima, considerando que a posição do texto no Json é em proporção da folha (A4 neste caso), o que influencia as regras de recuo e tabulação.
5. não utilizar as regras de espaçamento entre linhas.
6. A fonte do texto, o tamanho e o negrito são evidências que podem auxiliar na classificação, mas as regras explícitas, como numeração, posição e maiúsculo/capitalização são mais fortes.

### Amostras observadas

As verificações foram realizadas diretamente nos arquivos JSON produzidos pelo MinerU 4.2.0, utilizando páginas representativas de cada publicação. A análise confirmou a preservação dos principais marcadores normativos e identificou situações que deverão ser tratadas pelo parser estrutural em Java.

| Publicação e página | Caso verificado |
| --- | --- |
| [SGM-105, pp. 1-1 e 1-2](SGM-105-6REV.pdf#page=13) | A lista por hífen de `1.3.2.` foi preservada, mas dividida em dois blocos `list`, um em cada página. A continuidade deverá ser reconstruída pelo parser, considerando também a mudança de margem. |
| [SGM-201, p. 1-2](SGM-201-REV-8.pdf#page=16) | O item `1.3.1.` foi identificado como `paragraph_title`, enquanto suas alíneas `a)`, `b)` e `c)` foram classificadas como `text`, apresentando pequenas diferenças de recuo. A hierarquia deverá ser inferida pela combinação de marcadores, contexto e geometria. |
| [SGM-301, pp. 1-1 e 1-2](SGM-301-REV-10-ATU.pdf#page=22) | Os marcadores e textos examinados foram recuperados de forma legível pelo MinerU, inclusive o item `1.2.2.`, que continua na página seguinte. Entretanto, ainda não foi comprovada a eliminação integral dos problemas de codificação e espaçamento anteriormente identificados. Será necessária validação textual específica. |
| [SGM-303, pp. 1-1 e 1-2](SGM-303-Rev7.pdf#page=14) | Os marcadores `1.3.`, `1.3.1.`, `a)` e `I)` foram preservados. O texto do inciso `I)` continua na página seguinte, com alteração da margem esquerda. A reconstrução da hierarquia e a associação dos textos continuados serão responsabilidades do parser. |
| [SGM-701, p. 1-1](SGM-701-REV4.pdf#page=9) | A numeração `1.3.1 - Militares`, com hífen como separador, foi preservada e classificada como `paragraph_title`. As alíneas `a) Ativa` e `b) Veteranos` também foram identificadas como títulos, demonstrando a necessidade de reconhecer diferentes padrões de numeração e classificação. |
| [SGM-801, pp. 1-1 e 1-2](SGM-801-REV1.pdf#page=19) | As alíneas `a)` e `b)` do item `1.3.` aparecem na primeira página, enquanto `c)` e `d)` continuam na seguinte. O texto da alínea `b)` atravessa a quebra de página, acompanhada de mudança da margem esquerda. A continuidade deverá ser reconstruída independentemente das coordenadas absolutas. |

Os resultados indicam que o MinerU preserva, nos trechos examinados, os elementos necessários à reconstrução da estrutura normativa. Entretanto, a classificação dos blocos não é uniforme entre publicações, exigindo regras determinísticas que combinem marcadores textuais, contexto hierárquico, sequência de leitura e geometria relativa.

Esses seis casos serão utilizados como testes de regressão do parser Java. A validação integral das publicações e a identificação de eventuais perdas de conteúdo permanecem pendentes.
