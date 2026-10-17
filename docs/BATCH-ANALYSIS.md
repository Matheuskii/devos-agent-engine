# Análise por lotes e relatório com evidências

## Motivação

A amostra curta do primeiro incremento poderia terminar antes de alcançar código de negócio e testes. No backend-nhac, um SQL de dados gerados ocupa aproximadamente 4,6 MB. A execução original observou apenas 89 arquivos e não alcançou os testes, produzindo um relatório excessivamente compacto.

A nova versão separa três dimensões: coleta de arquivos elegíveis, conteúdo preparado para IA e conclusões sustentadas por referências verificadas. Ler um arquivo, enviá-lo integralmente ao modelo e verificar todas as suas propriedades são ações diferentes.

## Fronteiras arquiteturais

- EvidenceReference e AnalysisObservation pertencem ao domínio e não fazem I/O.
- BatchPlanner, EvidenceValidator, RepositoryCoverage e FullAnalysisEngine pertencem à aplicação.
- BatchInspectionPort, ObservationAuditPort, ReportSynthesisPort e ReportReviewPort são interfaces separadas, definidas pela necessidade do caso de uso.
- JsonBatchReasoner e OfflineBatchReasoner são Strategies dos mesmos contratos.
- DetailedReportRenderer formata dados já estruturados; ele não decide regras do domínio.
- FileRunRepository persiste históricos e checkpoints com escrita atômica por arquivo.

A inversão de dependência permanece: aplicação conhece contratos próprios e dados de domínio; JSON, HTTP, disco e renderização ficam nos adaptadores.

## Fluxo

1. Coletar todos os arquivos elegíveis dentro dos limites.
2. Preparar representações estruturais explicitamente identificadas para SQLs muito extensos.
3. Agrupar arquivos inteiros sempre que cada um cabe em um lote. Arquivos maiores que o lote são particionados sem perder caracteres ou coordenadas; fragmentos são identificados explicitamente. A divisão não parte pares substitutos Unicode. Riscos apoiados nas bordas de um fragmento parcial são descartados para evitar confundir cortes de contexto com assinaturas incompletas ou defeitos de sintaxe.
4. Conferir quantidade de lotes e orçamento mínimo antes da primeira chamada de IA.
5. Planejador define inventário e qualidade; ferramentas repetidas no plano não são reexecutadas sem necessidade.
6. Executor analisa cada lote e propõe observações com tipo, prioridade, explicação, impacto, recomendação e evidência.
7. A aplicação valida cada caminho, intervalo e citação literal contra o lote. Referências sem correspondência são descartadas e registradas.
8. Salvar checkpoint de progresso, cobertura e observações verificadas.
9. Auditar riscos e hipóteses em grupos de até três, com até 15 linhas adjacentes por lado de cada referência. Uma decisão é exigida para cada ID; UNSUPPORTED ou INCONCLUSIVE removem a observação. Em seguida, consolidar o relatório estruturado usando as observações aceitas.
10. Revisor avalia sustentação e clareza do relatório e a interpretação das observações citadas. Pode devolver IDs a descartar por contradição semântica, como tratar false como ativação de um estado. A aplicação valida os IDs, remove as observações rejeitadas e refaz a consolidação, sem repetir a inspeção dos lotes.
11. Persistir relatório final ou resultado parcial em caso de falha.

## Evidência não é prova de defeito

Uma citação textual válida apenas confirma que o trecho existe no arquivo e nas linhas informadas. Ela não confirma automaticamente a interpretação do modelo. Por isso, a revisão também pode descartar uma observação por contradição lógica, mesmo quando sua citação é válida. A presença de IDs rejeitados impede uma aprovação inconsistente e força nova consolidação. Cada observação distingue ponto forte, observação, risco e hipótese; riscos não recebem o rótulo de defeito confirmado por execução.

A auditoria por outro agente é probabilística. Na validação real, inclusive a auditoria focada aprovou uma interpretação equivocada de uma atribuição false. A conferência assistida removeu essa observação antes da entrega e preservou o registro da aprovação incorreta. O sistema automatiza verificações e rastreabilidade; não oferece garantia automática de correção semântica. Consulte [a validação por lotes](BATCH-VALIDATION.md).

Os overviews de lote são notas de navegação não verificadas. A consolidação e revisão são instruídas a usar as observações com referências aceitas para sustentar conclusões.

## Cobertura auditável

O JSON inclui os caminhos elegíveis coletados, caracteres originais, caracteres preparados, lotes planejados/concluídos, observações, descartes e avisos. O Markdown informa essas medidas antes das conclusões.

Coleta completa significa completa dentro dos tipos, exclusões e limites definidos pelo leitor; não inclui binários, dependências ou segredos omitidos. Processamento completo dos lotes não representa cobertura de testes, execução do código ou prova de ausência de defeitos.

SQLs acima de 250.000 caracteres são lidos, mas recebem contagem heurística de comandos e uma amostra inicial limitada como representação para IA. Seus dados individuais não são revisados semanticamente em sua totalidade. A representação não pode ser usada como evidência de um defeito em uma linha original.

## Falhas e checkpoints

Cada lote concluído atualiza os arquivos da execução e latest.md/latest.json. HTTP 429, deadline ou erro de formato encerra a execução como FAILED, preservando os lotes anteriores e informando quantos faltaram. Não há sucesso simulado nem repetição automática das chamadas. Os checkpoints ainda não são uma implementação de retomada após reinício.

As gravações são atômicas por arquivo, não uma transação entre JSON, Markdown e aliases. Em execuções simultâneas na mesma pasta, latest representa a atualização mais recente; o histórico por UUID continua sendo a referência de cada execução.

## Verificação

Testes cobrem reconstrução sem perda, linhas originais, fontes grandes sem redução, SQL explicitamente reduzido, Unicode, referências inventadas, evidências fora de linha, limites antes da API, persistência parcial, revisão sem reexecutar lotes, relatório e CLI além do antigo limite de 120 KB.

A avaliação acadêmica de qualidade semântica continua separada dos testes de software. Um relatório maior só é melhor quando suas explicações e prioridades ajudam o leitor e permanecem sustentadas.
