# Validação da análise por lotes

Em 17/10/2026, a engine analisou estaticamente o repositório público NhacDelivery/backend-nhac, branch main, snapshot da380cc74085e17e032d5cb640df8275df665aa5. O código do alvo não foi compilado nem executado.

## Escopo observado

| Medida | Resultado |
|---|---:|
| Arquivos elegíveis coletados | 510 |
| Java em src/main | 301 |
| Java em src/test | 105 |
| Arquivos na árvore de testes, incluindo apoio | 106 |
| Caracteres originais | 6.279.890 |
| Caracteres preparados | 1.713.173 |
| Lotes processados | 30 / 30 |
| Observações aceitas após conferência assistida | 96 |
| Chamadas de raciocínio registradas na linhagem final | 38 |

A coleta anterior por amostra alcançava 89 arquivos. O SQL V998, com 4.570.014 caracteres, foi representado estruturalmente por 3.297 caracteres, com aviso explícito. Seus dados individuais não receberam revisão semântica integral. Coleta completa significa completa nos tipos, exclusões e limites configurados.

## Verificação da engine

`mvn -B -ntp clean verify` passou no Codespace; a suíte de 83 testes também passou no ambiente local. São testes da engine, não do backend analisado. Os casos incluem descarte de referências inexistentes, revisão com IDs rejeitados, nova consolidação sem repetir lotes, rejeição de IDs desconhecidos, contexto adjacente e decisões de auditoria ausentes ou inconclusivas.

O fluxo real com Gemini processou 30 lotes e concluiu uma primeira consolidação/revisão. Os resultados dos lotes foram reutilizados na validação dos novos contratos de auditoria, com três chamadas focadas, nova síntese e nova revisão. Isso totaliza 38 chamadas na linhagem final; tentativas exploratórias descartadas não integram essa métrica. O relatório final foi persistido pelo FileRunRepository em Markdown/JSON e aliases latest, preservando os históricos por UUID.

## Resultado semântico e limitação observada

A conferência assistida encontrou dois problemas de interpretação:

- B12-O4 descrevia ativação de um estado incerto de pagamento, embora a evidência atribuísse false. O modelo aprovou a observação tanto na revisão geral quanto na auditoria focada. A observação foi removida na conferência assistida.
- B21-O4 inferia esperas fixas e instabilidade de testes a partir de Future.get com timeout. Esse trecho não comprova Thread.sleep ou instabilidade; a observação também foi removida.

As seções explicativas receberam edição assistida para distinguir organização por negócio de Clean Architecture, cenários declarados de resultados executados e hipóteses de gargalos medidos. As remoções e a edição estão registradas em notices/events. A revisão entre agentes não demonstrou correção semântica automática; testes de software dos contratos não equivalem a avaliação de qualidade do modelo.

## Saída no Codespace

Pasta: `/workspaces/devos-agent-engine/devos-output/backend-nhac-completo/`.

Relatório atual: `latest.md`. Registro completo: `latest.json`. Execução final: `1e7f6a52-9900-4dff-b9bb-63f4bdc73848`. Os arquivos do alvo e resultados de análise não são versionados no repositório da engine.
