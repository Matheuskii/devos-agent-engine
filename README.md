# DevOS Agent Engine

Engine multiagente em **Java 21** para analisar repositórios locais, com planejador, executor e revisor, Clean Architecture e adaptadores Gemini/Groq/Qdrant.

O modo padrão agora percorre todos os arquivos elegíveis por **lotes**, produz observações com evidências verificadas e consolida um relatório explicativo. O modo offline demonstra o fluxo sem inferência de IA.

## Começar

Requisitos: JDK 21+ e Maven 3.9+. A configuração de `.devcontainer` instala Java e Maven no Codespaces.

```bash
mvn -B -ntp verify
java -jar cli/target/cli-0.1.0.jar analyze examples/sample-repository --delay-ms 0
```

O primeiro build baixa dependências Maven. Depois, o exemplo offline não exige chave, internet para inferência ou Qdrant.

## Onde fica a análise?

**Abra `devos-output/latest.md` no VS Code e use Ctrl+Shift+V para visualizar.**

- `latest.md`: relatório mais recente ou checkpoint da execução em andamento.
- `latest.json`: registro detalhado, inventário de caminhos, lotes, evidências, cobertura e eventos.
- `<UUID>.md` e `<UUID>.json`: histórico por execução.

O estado EXECUTING ou FAILED não significa análise final aprovada; o arquivo informa quantos lotes terminaram. A persistência por lote preserva o trabalho já concluído se ocorrer falha posterior. Ainda não existe retomada automática a partir desses checkpoints.

Para escolher outra pasta:

```bash
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio \
  --output devos-output/meu-repositorio
```

## O que o relatório explica

1. Escopo, arquivos coletados, testes identificados e cobertura dos lotes.
2. Resumo executivo e diagnóstico sustentado por evidências.
3. Arquitetura: componentes, responsabilidades e relações observadas.
4. Testes e confiabilidade, sem confundir leitura estática com testes executados.
5. Pontos fortes.
6. Riscos e hipóteses priorizados.
7. Observações com explicação, impacto, próximo passo, arquivo, linha e trecho literal.
8. Plano de ação em ordem sugerida.
9. Limitações, dados reduzidos e avisos.
10. Parecer do revisor e rastreabilidade.

Cada referência de observação é comparada com o texto do lote e o intervalo de linhas. Referências inexistentes ou sem correspondência são descartadas e registradas. Isso verifica a evidência textual; não prova que toda interpretação do modelo é correta. Riscos e hipóteses não são apresentados como bugs confirmados por execução.

## Usar Gemini ou Groq

Escolha um modelo disponível na sua conta e compatível com JSON. Defina as variáveis no terminal:

```bash
export GEMINI_API_KEY="sua-chave"
export DEVOS_MODEL="modelo-disponivel-na-sua-conta"
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio --provider gemini
```

Para Groq, use `GROQ_API_KEY` e `--provider groq`. Modelos validados anteriormente: `gemini-3.5-flash-lite` e `openai/gpt-oss-20b`; disponibilidade e cotas podem mudar.

**A CLI Java não carrega `.env` automaticamente.** O arquivo `.env.example` documenta as configurações; exporte as variáveis no seu shell. No PowerShell: `$env:DEVOS_MODEL="modelo"`.

Ao escolher um provedor online, o conteúdo preparado dos arquivos elegíveis será enviado à API. O leitor omite arquivos de ambiente, credenciais e chaves privadas identificáveis e mascara padrões comuns de segredos. O mascaramento é heurístico. Use `.devosignore` para excluir conteúdo adicional antes da análise.

Não há troca automática para plano pago, retries ocultos ou fallback silencioso para respostas simuladas. Uma falha externa gera um registro FAILED com a cobertura já concluída.

## Leitura por lotes

O padrão `--scope full` lê todos os arquivos elegíveis dentro dos limites configurados: até 5.000 arquivos, 24 MB no total e 8 MB por arquivo. O conteúdo de código e testes é dividido em lotes de até 64.000 caracteres, preservando os números de linha e sem cortar silenciosamente os arquivos nos 120 KB da versão inicial.

Arquivos SQL acima de 250.000 caracteres recebem uma **representação estrutural**, com contagem heurística de comandos e amostra inicial. O arquivo é lido integralmente, mas seus dados individuais não recebem revisão semântica integral pela IA. Essa redução é identificada na cobertura e nas limitações, e não pode sustentar uma observação com linhas originais. Isso evita que milhões de caracteres de dados gerados consumam o contexto destinado ao código.

```bash
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio \
  --provider gemini --scope full \
  --batch-chars 64000 --max-batches 64 --max-calls 80 \
  --delay-ms 2000 --timeout-seconds 900 \
  --output devos-output/meu-repositorio
```

O número de lotes e o orçamento mínimo são conferidos antes de chamar a IA. Se excederem os limites, o programa informa o ajuste necessário. O intervalo entre lotes ajuda a controlar o ritmo, mas não garante conformidade com todas as cotas do provedor.

Para a amostragem curta original, use `--scope sample`: até 200 arquivos, 120 KB totais e 12 KB por arquivo, com orçamento padrão de sete chamadas e três minutos. O formato desse modo permanece compacto.

Mais detalhes: [contratos, lotes, evidências e cobertura](docs/BATCH-ANALYSIS.md).

## Memória vetorial opcional

Qdrant armazena sínteses aprovadas e recupera contexto do mesmo repositório. Embeddings são gerados pela API Gemini, inclusive quando o raciocínio usa Groq; portanto, essa configuração não é completamente offline.

```bash
docker compose up -d qdrant
export GEMINI_API_KEY="sua-chave"
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio --memory qdrant
```

A memória padrão é `none`. O Qdrant do Compose usa loopback, volume persistente e coleções separadas por modelo e dimensão. A implementação valida dimensões e filtra resultados pelo identificador do repositório. Falhas de memória geram aviso e preservam o relatório. O Qdrant não substitui o armazenamento operacional das execuções.

## Arquitetura

| Módulo | Responsabilidade | Dependência de produção |
|---|---|---|
| `domain` | Agent, Task, Plan, regras, observações e referências de evidência | Apenas JDK |
| `application` | Casos de uso, lotes, cobertura, orçamento e Ports | domain |
| `infrastructure` | Leitor, ferramentas, APIs, JSON, Markdown e Qdrant | application |
| `cli` | Configuração, composição, argumentos e progresso | infrastructure |

O caso de uso completo usa interfaces separadas para inspecionar um lote, consolidar o relatório e revisar seu conteúdo. O domínio não depende de Jackson, Spring, provedores de IA ou Qdrant. Spring Boot e LangChain4j podem ser introduzidos em adaptadores futuros, sem entrar nas entidades.

Padrões: Ports and Adapters, Strategy, Registry e Repository; máquina de estados para Task e composição explícita na CLI. A execução dos lotes é sequencial para limitar consumo e facilitar rastreabilidade. Três papéis não significam três processos distribuídos ou revisores estatisticamente independentes.

Veja [arquitetura](docs/ARCHITECTURE.md), [decisões](docs/DECISIONS.md) e [protocolo acadêmico](docs/TCC-EVALUATION.md).

## Limites reais

- Entrada por diretório local já clonado; sem clonagem GitHub automática.
- Análise estática: não compila, não executa testes e não altera o código analisado.
- Tipos elegíveis incluem Java, Kotlin, Python, JavaScript/TypeScript, Dart, C#, Go, Rust, documentos de texto e configurações selecionadas.
- Dependências, builds, `.git`, `.vscode`, `.idea`, `.devos` e `devos-output` são ignorados. Links simbólicos não são seguidos na coleta.
- `.devosignore` usa globs Java relativos à raiz; não implementa toda a sintaxe de `.gitignore` e não lê `.gitignore` automaticamente.
- A coleta mantém limites de profundidade e número de entradas; exclusões e truncamentos não equivalem a ausência de código.
- O prazo total é cooperativo entre operações; HTTP limita a resposta completa a até 40 segundos por requisição.
- O modo full prevê até 64 lotes, 80 chamadas, duas revisões adicionais e 15 minutos por padrão. O orçamento inclui planejamento, lotes, síntese e revisão; embeddings são chamadas adicionais não incluídas nessa métrica.
- A aprovação não prova ausência de defeitos nem substitui revisão humana ou uma ferramenta de análise estática especializada.

## Testes e CI

```bash
mvn -B -ntp clean verify
```

A suíte verifica invariantes, permissões, estados, ciclos, orçamento, falhas, leitura e mascaramento, particionamento sem perda, coordenadas de evidências, rejeição de referências inventadas, limites antes de chamadas externas, checkpoints parciais, revisão sem repetir os lotes, relatório explicativo, aliases latest e contratos HTTP.

O CI testa o projeto e executa a demonstração offline. JAR, XMLs de teste e demonstração são disponibilizados como artefatos do GitHub Actions.

Registros históricos: [primeira validação](docs/VALIDATION.md) e [primeira validação com APIs reais](docs/CODESPACE-VALIDATION.md).

## Referências técnicas

- [Gemini — geração](https://ai.google.dev/api/generate-content)
- [Gemini — embeddings](https://ai.google.dev/api/embeddings)
- [Groq — API](https://console.groq.com/docs/api-reference)
- [Qdrant — API](https://api.qdrant.tech/)
