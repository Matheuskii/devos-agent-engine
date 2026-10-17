# Validação no Codespace por Desktop Commander

Data: 16/10/2026, 22:33:04 (America/Sao_Paulo).

## Ambiente e testes

Java 21, Maven 3.10.0, Node.js 24.22.0 e npm 11.19.1 disponíveis. Build limpo com Maven concluído: 49 testes aprovados, sem falhas, erros ou testes ignorados. Distribuição: domínio 12, aplicação 10, infraestrutura 22 e CLI 5.

Análise offline do exemplo e autoanálise do próprio projeto: COMPLETED, com relatórios Markdown e JSON.

## Validação com serviços reais

| Configuração | Resultado | Chamadas de raciocínio | Revisões adicionais |
|---|---|---:|---:|
| Gemini / gemini-3.5-flash-lite | COMPLETED | 3 | 0 |
| Groq / openai/gpt-oss-20b | COMPLETED | 3 | 0 |
| Gemini + Qdrant + embeddings reais | COMPLETED, sem avisos de memória | 3 | 0 |

As análises online usaram o repositório de exemplo incluído no projeto. As chaves foram carregadas do arquivo local de configuração somente nos processos de teste e não foram exibidas, alteradas ou versionadas. O modelo foi definido apenas para os processos de validação.

O Codespace não tinha Docker. Um Qdrant 1.13.6 temporário foi iniciado pela distribuição nativa estática, vinculado ao loopback na porta 16333. A engine gravou uma síntese aprovada em uma coleção de 768 dimensões com embeddings Gemini gemini-embedding-001. Uma sonda Java usando os adaptadores reais confirmou a recuperação da síntese e a ausência de resultados quando o identificador do repositório era diferente.

## Problema encontrado e correção

O revisor recebia os achados das ferramentas, mas não a amostra de código usada pelo executor. Além disso, uma resposta do modelo reprovou a qualidade do repositório de exemplo em vez de avaliar a precisão do relatório, provocando revisões desnecessárias.

O contrato ReviewPort passou a receber RepositorySnapshot. O adaptador envia arquivos, avisos de amostragem e um alvo explícito de avaliação da precisão do relatório. As instruções diferenciam relatório correto de código sem problemas e orientam o planejador a evitar repetir as mesmas ferramentas. Foi incluído um teste de regressão para a presença das evidências de código na revisão; o teste da aplicação também verifica que a mesma amostra chega ao revisor.

## Falhas externas observadas

O modelo gemini-2.5-flash-lite retornou HTTP 404 informando indisponibilidade para novas contas e recomendando gemini-3.5-flash-lite. O Groq retornou HTTP 429 durante a tentativa anterior à correção. Essas tentativas produziram registros FAILED, sem sucesso simulado. Após corrigir a revisão e usar um modelo Gemini disponível, os fluxos reais foram aprovados. Disponibilidade e cotas podem mudar.

## Evidências locais

Os relatórios, registros JSON, listas de modelos e a sonda de memória ficaram em .devos/desktop-validation no Codespace. Esse diretório é ignorado pelo Git e não contém cópia das chaves. Os logs do Maven ficaram em /tmp/devos-codespace-tests.log e /tmp/devos-codespace-fix-tests.log.

A aprovação demonstra funcionamento dos fluxos verificados. Não garante qualidade de toda análise futura nem substitui a avaliação acadêmica proposta em TCC-EVALUATION.md.
