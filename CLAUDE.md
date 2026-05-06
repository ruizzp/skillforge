# SkillForge — Guia de Decisões Técnicas

> Este arquivo orienta decisões de arquitetura para IAs e colaboradores.
> Leia antes de propor qualquer mudança estrutural.

---

## Princípios Inegociáveis

### 1. SLM local antes de API paga

Qualquer funcionalidade que envolva LLM deve primeiro avaliar se um modelo local via Ollama resolve com qualidade suficiente.

**Hierarquia de decisão:**
```
1. SLM local via Ollama          (custo zero, privacidade total)
2. SLM especializado via Ollama  (ex: meditron para domínio médico)
3. API paga como fallback        (Claude, OpenAI — apenas quando SLM não atinge qualidade mínima)
4. API paga + SLM em conjunto    (casos onde estrutura + validação de domínio são necessários)
```

**Quando API paga é justificável:**
- Raciocínio estruturado complexo que SLMs 7B não entregam de forma consistente
- Geração de conteúdo com formato estrito (JSON, código, documentação técnica)
- Validação cruzada entre modelos (SLM valida domínio, API estrutura o output)

**Exemplo aprovado:** `quest-scribe` usa Claude API para estrutura da quest + Meditron (Ollama) para validação clínica. Cada modelo faz o que faz melhor.

---

### 2. GitHub como banco de dados intencional

Heroes, quests, XP e validações de skills vivem em GitHub Issues + Labels.

**Não adicionar** banco relacional externo sem discussão explícita.  
**Motivo:** a transparência do estado da guilda via GitHub é uma feature, não uma limitação.

Labels usadas como ledger:
- `xp:{total}` — XP acumulado do herói
- `skill-validated:{skill}` — skill confirmada por desafio real
- `level:{n}` — nível atual

---

### 3. Zero frameworks de frontend

Dashboards usam HTML + CSS + SSE nativo.  
**Sem React, Vue, HTMX ou similares** sem justificativa de produto clara.  
Thymeleaf renderiza server-side; EventSource API cuida do tempo real.

---

### 4. Java 21 + Virtual Threads — sem platform threads

Toda concorrência usa `Executors.newVirtualThreadPerTaskExecutor()` ou `@Async` com executor configurado para virtual threads.  
`CompletableFuture` com pool de platform threads é considerado regressão.

---

### 5. Módulos são heroes — heroes são módulos

Cada capacidade nova do sistema deve ser avaliada como um hero node antes de virar código acoplado ao hub.  
O hub orquestra; os heroes especializam.

---

### 6. Guardrails são funções puras — o repositório é o único estado

Guardrails validam — não armazenam.

Todo mecanismo de validação (domain profile, quest schema, agent checklist) deve:
- **Ler** o estado atual do repositório (arquivos, fixtures, manifests)
- **Retornar** aprovado/reprovado + razões
- **Nunca persistir** resultado de validação em banco, cache ou memória própria

```
guardrail(repo_state) → { valid: bool, missing: [], warnings: [] }
```

O "estado aprovado" de um domínio ou quest é expresso pelo que **existe no repo**:
- `fixtures/ps-cases.json` existe → fixtures criadas
- Checklist em `DOMAIN_PROFILE.md` sem `[ ]` bloqueantes → domínio aprovado
- Quest em `catalog/` → quest publicada

**Consequência:** rodar o guardrail duas vezes no mesmo repo produz o mesmo resultado.  
**Proibido:** tabela de "domínios aprovados", cache de validação, flag em banco.

---

## Stack Canônica

| Camada | Tecnologia | Notas |
|---|---|---|
| Runtime | Java 21 | Records, virtual threads, sealed classes |
| Framework | Spring Boot 3.3.x | Sem Jakarta EE externo |
| Mensageria | RabbitMQ (AMQP) | Topic exchange `skillforge` |
| LLM local | Ollama | Modelos configuráveis por hero |
| LLM remoto | Claude API | Fallback ou casos justificados |
| Persistência de estado | GitHub Issues + Labels | Ledger público e auditável |
| Persistência local | SQLite via JDBC | Apenas para cache/estado efêmero |
| Frontend | Thymeleaf + SSE | Sem SPA |
| Build | Maven multi-módulo | Parent `pom.xml` na raiz |

---

## Decisões em Aberto (Brainstorm)

> Ideias aprovadas para exploração futura — não implementar sem discussão.

### Quest Framework e Domínios
Ver `quests/QUEST_FRAMEWORK.md` — define o schema invariante de toda quest e como criar um perfil de domínio novo.  
Cada domínio de negócio vive em `quests/domains/{domain}/DOMAIN_PROFILE.md` antes de qualquer quest ser criada.  
Domínio médico (pronto-socorro) está em `quests/domains/medical/` — perfil completo com Manchester, vocabulário e modelo validador (Meditron).

### Quest Scribe Hero
Hero especializado em design de quests usando modelo dual:
- **Claude API** — escreve estrutura, formato, critérios técnicos, fixtures JSON
- **Meditron (Ollama)** — valida plausibilidade clínica dos casos fictícios e gabaritos
- Aceita problemas via AMQP: `{domain, questType, heroLevel, context}`
- Grava rascunho em `quests/{domain}/{QUEST-ID}.md`
- Skills declaradas: `quest-design`, `clinical-reasoning`, `domain-modeling`
- Definição completa em `agents/heroes/quest-scribe/`

### Catálogo de Quests Médicas (Pronto Socorro)
Ver `quests/PRONTO_SOCORRO.md`.  
Perguntas estruturantes pendentes de decisão antes de criar as quests individuais.

---

## O que não fazer

- Não mockar banco em testes de integração (aprendi com incidente anterior)
- Não usar `@Transactional` sem entender o escopo — SQLite tem limitações de concorrência
- Não adicionar dependências sem checar se o Java 21 stdlib já resolve
- Não fazer push para main sem build local passando (`mvn verify`)