# Validação executada

Data: 2026-10-16T22:03:14-03:00 (America/Sao_Paulo).

Build limpo com JDK 21.0.6 e Maven 3.9.9: **BUILD SUCCESS**. Comando lógico: `mvn -B -ntp clean verify`. O ambiente exigiu configuração local de proxy e certificado para baixar dependências; essa configuração não integra o projeto.

| Módulo | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| domain | 12 | 0 | 0 | 0 |
| application | 10 | 0 | 0 | 0 |
| infrastructure | 21 | 0 | 0 | 0 |
| cli | 5 | 0 | 0 | 0 |
| **Total** | **48** | **0** | **0** | **0** |

## Demonstrações reais

- JAR executável: `--help` funcionando.
- Análise offline de `examples/sample-repository`: COMPLETED, três chamadas lógicas de raciocínio, três achados esperados e registros dos três papéis. Relatório e JSON em `examples/output`. As chamadas lógicas offline não representam requisições de inferência.
- Autoanálise do código deste projeto: COMPLETED, observando os limites de amostragem.
- `jdeps -s domain/target/domain-0.1.0.jar`: somente `java.base` como dependência de produção.
- Contratos REST Gemini, Groq, embeddings e Qdrant testados por HTTP real contra servidores de teste locais controlados.
- Testes cobrem falha 429, resposta truncada, JSON inválido, plano cíclico, permissões, revisões limitadas, deadline e cancelamento de resposta HTTP excessiva.

## Limites da validação

Não foram fornecidas chaves de API. Não foram feitas inferências reais Gemini/Groq, nem executado um servidor Qdrant real. Os testes de protocolo não substituem um teste com o modelo escolhido e o banco em execução. A configuração Docker Compose, o Dev Container e o workflow GitHub foram preparados, mas não iniciados neste ambiente. A publicação no GitHub é uma etapa posterior a esta validação local.

Os resultados verificam este incremento do software. Não comprovam qualidade semântica, desempenho em produção ou superioridade acadêmica de uma arquitetura multiagente.
