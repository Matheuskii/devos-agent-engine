# DevOS Agent Engine

Engine de agentes para analisar repositórios locais com **Java 21**, arquitetura em camadas e três papéis: planejador, executor e revisor.

**Versão 0.1.0 — primeiro incremento funcional do TCC.** A autonomia é delimitada: o planejador escolhe e ordena ferramentas de leitura previamente autorizadas; o executor coleta evidências e sintetiza; o revisor pode aprovar ou solicitar uma nova síntese. A engine controla dependências, permissões, limites e estados. Nenhum comando do repositório é executado.

## Executar em cinco minutos

Requisitos: **JDK 21+ e Maven 3.9+**. No Codespaces, abra este projeto usando a configuração de `.devcontainer` e aguarde a construção do contêiner. O ambiente instala Java e Maven. Em outra máquina, instale-os e confira `java -version` e `mvn -version`.

Se você baixou o pacote desta versão, ele também inclui um JAR pronto em `dist/devos-agent-engine-0.1.0.jar`. Com Java 21, execute sem Maven: `java -jar dist/devos-agent-engine-0.1.0.jar analyze examples/sample-repository`. Para desenvolver e recompilar, siga abaixo.

Na raiz do projeto:

```bash
mvn -B -ntp verify
java -jar cli/target/cli-0.1.0.jar analyze examples/sample-repository
```

O segundo comando funciona **sem internet, chave ou Qdrant** depois do build. É um modo determinístico para demonstrar o fluxo; ele não realiza inferência de IA.

Analisar um repositório já clonado:

```bash
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio \
  --objective "Analisar estrutura, documentação e sinais de qualidade" \
  --output .devos/runs
```

No Windows, use o caminho local entre aspas e execute o comando em uma linha. Ajuda: `java -jar cli/target/cli-0.1.0.jar --help`.

Saída: um relatório Markdown e um registro JSON por execução, identificados por UUID. O JSON registra estado, achados, papéis, revisões, tempo e chamadas de raciocínio. Código de saída: `0` aprovado; `1` análise rejeitada ou falhou; `2` erro de configuração/CLI ou persistência.

## Usar IA real

Escolha **um modelo atualmente disponível na sua conta e compatível com JSON**. O nome do modelo é obrigatório para evitar depender de modelos aposentados. As cotas gratuitas e os modelos variam por provedor; a engine não habilita cobrança nem troca automaticamente para planos pagos.

Exemplo Bash com Groq:

```bash
export GROQ_API_KEY="sua-chave"
export DEVOS_MODEL="identificador-do-modelo-disponivel-na-sua-conta"
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio --provider groq
```

Exemplo Bash com Gemini:

```bash
export GEMINI_API_KEY="sua-chave"
export DEVOS_MODEL="identificador-do-modelo-disponivel-na-sua-conta"
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio --provider gemini
```

PowerShell: `$env:GROQ_API_KEY="sua-chave"` e `$env:DEVOS_MODEL="modelo"`.

`.env.example` documenta as variáveis, mas arquivos `.env` **não são carregados automaticamente**. Nunca comite chaves. Os adaptadores REST usam Java HttpClient e Jackson; Spring Boot e LangChain4j não são necessários para este incremento.

Ao escolher um provedor online, a amostra de código elegível será enviada à API para síntese. O leitor omite arquivos de ambiente, arquivos com nomes de credenciais/segredos e chaves privadas; mascara padrões comuns de tokens e atribuições de credenciais. Isso é uma proteção heurística, sem garantia de encontrar todo segredo. Defina exclusões adicionais em `.devosignore` antes da análise de conteúdo privado.

## Memória vetorial local com Qdrant

Opcional. Exige Docker/Compose **na sua máquina** e uma chave Gemini para gerar embeddings, inclusive quando o raciocínio usar Groq. O Qdrant armazena e consulta vetores; a geração semântica dos embeddings é feita pela API Gemini. Portanto, esta configuração não é totalmente offline.

```bash
docker compose up -d qdrant
export GEMINI_API_KEY="sua-chave"
java -jar cli/target/cli-0.1.0.jar analyze /caminho/do/repositorio --memory qdrant
```

O exemplo usa o raciocínio offline com memória online. Para ativar ambos, acrescente `--provider gemini` ou `--provider groq` e configure `DEVOS_MODEL`.

- Servidor vinculado a `127.0.0.1:6333`, com volume persistente.
- Embeddings normalizados com 768 dimensões; modelo configurável em `DEVOS_EMBEDDING_MODEL`.
- Coleção separada por modelo e dimensão; incompatibilidade não mistura vetores.
- Recupera até três sínteses aprovadas, filtradas pelo identificador do repositório.
- Identificador do repositório: SHA-256 do caminho real local; mover o diretório cria outro contexto de memória.
- Falhas na memória geram um aviso e preservam a análise; resultados rejeitados não entram na memória.
- O contexto recuperado é histórico não confiável; não substitui evidências atuais.

## Arquitetura

| Módulo | Responsabilidade | Dependência de produção |
|---|---|---|
| `domain` | Agent, Task, Plan, PlanStep, regras e objetos de valor | Apenas JDK |
| `application` | Caso de uso, orçamento de execução e Ports | domain |
| `infrastructure` | Arquivos, ferramentas, APIs, JSON e Qdrant | application e dependências transitivas |
| `cli` | Argumentos, configuração e composição | infrastructure e dependências transitivas |

A direção de dependências é fiscalizada pelo build Maven: o domínio não possui dependências de produção externas nem acesso aos módulos externos. Os testes usam JUnit. Interfaces pequenas separam planejamento, síntese, revisão, ferramentas, repositório e memória.

Padrões utilizados: **Ports and Adapters**, **Strategy** para implementação de raciocínio e memória, **Registry** para ferramentas e **Repository** para registros. A composição explícita fica na CLI. A máquina de estados de Task valida transições sem depender de frameworks. Não há contêiner de injeção obrigatório.

Detalhes e justificativas: [arquitetura](docs/ARCHITECTURE.md), [decisões](docs/DECISIONS.md) e [avaliação do TCC](docs/TCC-EVALUATION.md).

## Escopo e limites reais

- Entrada local: clone o repositório por conta própria e informe o caminho. Não há clonagem ou autenticação GitHub automática.
- Ferramentas deste incremento: inventário de arquivos e verificações heurísticas de README, testes observados e marcadores TODO/FIXME.
- A engine não compila nem executa os testes do repositório analisado, não altera arquivos e não comprova vulnerabilidades.
- Amostragem: até 200 arquivos, 120 KB no total, 12 KB por arquivo, profundidade 20 e até 10 mil entradas visitadas. Truncamentos e falhas de leitura são informados.
- Diretórios de dependências, builds, `.git`, `.tools`, `.devos` e `devos-output` são ignorados. Links simbólicos não são seguidos durante a coleta.
- `.devosignore`: um glob Java por linha, relativo à raiz; linhas iniciadas por `#` são comentários. Exemplo: `private` para omitir o diretório, `**/*.sql` para omitir caminhos correspondentes. Não implementa a sintaxe completa de `.gitignore` e não lê `.gitignore` automaticamente.
- Padrões: no máximo 6 etapas, 7 chamadas de raciocínio e 2 revisões adicionais. Modelo: temperatura 0,1 e até 2500 tokens de saída. HTTP: timeout de até 40 segundos por requisição. Sem retries ocultos nem fallback silencioso para respostas simuladas.
- O prazo total de três minutos é cooperativo entre operações; não é um mecanismo de interrupção forçada de leitura em disco. A memória opcional pode fazer duas chamadas adicionais de embedding; elas não entram na métrica de chamadas de raciocínio.
- Estados finais são persistidos; retomada após queda no meio da execução e coordenação distribuída ficam para módulos posteriores.
- Aprovação do relatório não significa ausência de defeitos. No modo online, a revisão entre agentes é probabilística; os três papéis podem usar o mesmo modelo e compartilhar vieses.

## Testes e CI

```bash
mvn -B -ntp verify
```

A suíte testa invariantes, ciclos, transições, orçamento, revisão, falhas, leitura limitada, exclusões, mascaramento, CLI e contratos HTTP Gemini/Groq/Qdrant com servidores locais controlados. Não exige chaves, internet para inferência nem um Qdrant real. O primeiro build precisa baixar as dependências Maven.

O workflow em `.github/workflows/ci.yml` repete o build, executa a demonstração e publica JAR, testes e relatório de exemplo como artefatos nos pushes e pull requests do GitHub.

Veja [VALIDATION.md](docs/VALIDATION.md) para o resultado obtido neste ambiente e os testes de serviços reais que ainda precisam ser realizados.

## Referências técnicas

- [Gemini — geração de conteúdo](https://ai.google.dev/api/generate-content)
- [Gemini — embeddings](https://ai.google.dev/api/embeddings)
- [Groq — API](https://console.groq.com/docs/api-reference)
- [Groq — limites](https://console.groq.com/docs/rate-limits)
- [Gemini — preços e plano gratuito](https://ai.google.dev/gemini-api/docs/pricing)
- [Qdrant — API REST](https://api.qdrant.tech/)
