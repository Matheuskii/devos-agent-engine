# Registro de decisões arquiteturais

## ADR-001 — Java 21 e Maven multimódulo

**Status:** adotada para o MVP, conforme escolha do autor.

**Contexto:** demonstrar domínio puro, contratos explícitos e regras testáveis no TCC.

**Decisão:** quatro módulos Maven. JDK como única dependência de produção do domínio; JUnit apenas em testes. Framework web adiado até existir um caso de uso estável.

**Consequência:** mais arquivos de configuração do que um módulo único, com fronteiras verificáveis pela compilação. TypeScript permanece uma alternativa tecnicamente válida, sem necessidade de misturar runtimes agora.

## ADR-002 — Três papéis e execução sequencial

**Status:** adotada.

**Decisão:** planejador, executor e revisor separados logicamente, com instâncias e instruções próprias. Mesmo provedor/modelo pode atender aos três. Plano é um DAG, executado sequencialmente em ordem topológica.

**Consequência:** rastreabilidade e menor complexidade operacional; não demonstra agentes distribuídos, paralelos ou revisores estatisticamente independentes.

## ADR-003 — REST direto atrás de adaptadores

**Status:** adotada para o primeiro incremento.

**Decisão:** HttpClient do JDK e Jackson para Gemini/Groq/Qdrant; não usar LangChain4j ou Spring na camada central.

**Consequência:** protocolo explícito e superfície de dependências menor, com manutenção dos contratos de cada provedor sob nossa responsabilidade. Migração futura pode reutilizar os mesmos Ports.

## ADR-004 — Ferramentas delimitadas e evidências heurísticas

**Status:** adotada.

**Decisão:** entrada por diretório local; apenas inventário e verificações estáticas simples. Toda observação de código tem trecho e linha quando aplicável; observações da amostra usam linha 0 e caminho raiz.

**Consequência:** MVP demonstrável sem executar código desconhecido. Não é equivalente a SonarQube, compilação, testes ou auditoria de segurança. IA sintetiza observações e pode levantar hipóteses; hipóteses não se tornam achados estruturados automaticamente.

## ADR-005 — Memória vetorial auxiliar e registros em arquivo

**Status:** adotada.

**Decisão:** JSON/Markdown para resultados finais; Qdrant opcional para sínteses aprovadas, com embeddings Gemini. Escopo de memória por caminho real do repositório, modelo e dimensão.

**Consequência:** memória indisponível não bloqueia o resultado; mover o clone muda seu contexto. Arquivos não oferecem retomada transacional nem busca operacional eficiente em grande escala. Qdrant não é usado como banco de estados de tarefas.

## ADR-006 — Falhas explícitas e custo limitado

**Status:** adotada.

**Decisão:** validar respostas JSON, rejeitar ferramenta desconhecida e planos inválidos, limitar chamadas e revisões, definir timeout e não simular sucesso quando a API falha.

**Consequência:** maior previsibilidade, podendo encerrar cedo sob cotas ou respostas inválidas. Retry/backoff e fallback entre provedores serão decisões posteriores, com métricas próprias.


## ADR-007 — Coleta ampliada, lotes e evidências verificadas

**Status:** adotada no incremento de análise detalhada.

**Contexto:** a amostra de 120 KB podia ser consumida por dados gerados e deixar classes e testes de fora. A síntese em um único campo produziu relatórios pouco úteis.

**Decisão:** modo full por padrão, contratos separados para inspeção/consolidação/revisão e relatório estruturado. Código e testes são particionados sem perda. SQLs extensos têm representação estrutural com redução declarada. Caminhos, linhas e trechos das observações são verificados antes de usá-las na consolidação.

**Consequência:** mais chamadas e maior latência, com orçamento e ritmo configuráveis. Checkpoints preservam resultados parciais; uma referência textual correta não prova a interpretação do modelo. O modo sample mantém a leitura curta para comparação e demonstrações.
