# Protocolo de avaliação proposto

## Pergunta de pesquisa

No escopo de análise estática de repositórios, separar planejamento, execução e revisão melhora a sustentação dos relatórios por evidências em comparação com uma única chamada ao mesmo modelo?

Esta é uma hipótese a testar. O MVP não comprova superioridade de sistemas multiagentes.

## Desenho experimental

1. Selecionar repositórios autorizados e fixar o commit de cada amostra.
2. Definir objetivos e critérios de aceitação antes das execuções.
3. Preparar uma referência humana revisada: documentação observável, arquivos de teste, marcadores e afirmações arquiteturais verificáveis.
4. Executar baseline com uma chamada de síntese e a mesma amostra de arquivos/evidências.
5. Executar o fluxo com três papéis, usando mesmo modelo, temperatura e condições de entrada.
6. Fazer pelo menos cinco repetições por combinação e registrar quotas, erros e exclusões. A quantidade final deve considerar tempo, tamanho da amostra e orientação acadêmica.
7. Avaliação humana preferencialmente sem saber qual configuração gerou o relatório; revisão por outro LLM é uma métrica auxiliar.

O baseline e o executor deste protocolo ainda precisam ser implementados. A suite automatizada atual valida software e contratos, não qualidade do modelo.

## Métricas

| Métrica | Como medir |
|---|---|
| Sustentação por evidências | Afirmações verificáveis sustentadas / afirmações verificáveis totais |
| Aceitação humana | Proporção de relatórios que atendem aos critérios definidos |
| Falsas alegações | Afirmações de testes executados, arquivos inexistentes ou resultados não observados |
| Custo operacional | Chamadas de raciocínio, embeddings, tokens quando disponíveis e duração |
| Revisões | Número de novas sínteses antes do estado final |
| Robustez | Comportamento sob erro 429, JSON inválido, plano cíclico e memória indisponível |

Os registros atuais incluem chamadas de raciocínio, revisões, duração e eventos. Tokens e chamadas de embedding não são contabilizados no JSON nesta versão e devem ser instrumentados antes da análise de custo por token.

## Ameaças à validade

- Mesmo modelo nos três papéis pode reproduzir os mesmos vieses.
- Amostragem pode omitir partes importantes dos projetos.
- Marcadores TODO e ausência de testes na amostra são indícios, não defeitos comprovados.
- Cotas e latência externa podem alterar duração e número de execuções válidas.
- Temperatura baixa não garante determinismo de APIs.
- Melhorias podem vir de mais chamadas ou maior orçamento, em vez da separação em agentes; incluir uma baseline com orçamento equivalente quando possível.

## Evidências para a banca

Arquitetura e ADRs, código dos invariantes, testes automatizados, registros JSON, relatórios comparáveis e análise honesta das limitações. Manter chaves e conteúdo privado fora dos anexos públicos.


## Avaliar também clareza e fidelidade de cobertura

Na versão por lotes, comparar explicações de arquitetura, evidências verificáveis, impacto e possibilidade de executar as recomendações. Registrar a proporção de observações descartadas por referência inválida e separar cobertura dos arquivos elegíveis de conteúdo integral enviado à IA. Dados reduzidos devem permanecer explícitos em toda comparação. A baseline deve usar entradas e orçamento comparáveis para não atribuir ao MAS uma melhoria causada apenas por maior quantidade de código lido.
