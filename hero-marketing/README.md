# Hero Marketing

> Hero especializado em pitch e narrativa. Recebe uma quest via AMQP, gera dois documentos (Guild Pitch + Investor One-Pager) usando Claude API, valida com dois críticos distintos, posta o resultado como comentário na issue GitHub da quest e retorna a solução ao hub.

---

## Skills declaradas

| Skill | O que faz |
|---|---|
| `pitch-scribe` | Escreve Guild Pitch e Investor One-Pager a partir de um brief |
| `narrative-design` | Estrutura narrativa persuasiva de produto |
| `audience-segmentation` | Adapta tom e conteúdo para dev, CTO e investor |
| `structured-writing` | Formato, hierarquia e clareza de documentos técnicos |

---

## Fluxo completo

```
Hub  →  AMQP "problem.#"  →  PitchListener.onProblem(ProblemMessage)
           │
           ├─ buildBrief(msg)          — extrai ProjectBrief de questBody
           │
           ├─ PitchGeneratorService.generate(brief)
           │    └─ Claude API (temp 0.8, agency-system-prompt.txt)
           │         └─ retorna: "--- GUILD PITCH ---" + "--- INVESTOR ONE-PAGER ---"
           │
           ├─ PitchValidatorService.validate(guildPitch, investorOnePager)
           │    ├─ validador 1: guild-pitch-validator-prompt.txt  (dev cético, 8 anos de exp)
           │    ├─ validador 2: investor-validator-prompt.txt     (angel investor)
           │    └─ confidence = média ponderada dos scores (clareza, dor, diferencial, credibilidade)
           │
           ├─ GitHubCommentService.postArtifacts(questUrl, questId, draft)
           │    └─ posta comentário markdown formatado na issue GitHub da quest
           │
           └─ RabbitTemplate.convertAndSend(exchange, "solution.pitch-design", SolutionMessage)
                └─ Hub SolutionConsumer → SSE SOLUTION_RECEIVED → auto-validate skills
```

### Critério de aprovação

| Dimensão | Avaliador | Peso |
|---|---|---|
| `clareza` | dev cético + angel investor | média entre os dois |
| `dor` | dev cético + angel investor | média entre os dois |
| `diferencial` | dev cético + angel investor | média entre os dois |
| `credibilidade` | dev cético + angel investor | média entre os dois |

`valid = true` quando: média ≥ 6.0 **e** diferencial defensável em ambos os validadores **e** CTA presente em ambos.

`confidence` é degradada progressivamente: avg < 7.0 → ×0.80; diferencial não defensável → ×0.70; CTA ausente → ×0.80.

---

## Heartbeat

O hero envia heartbeat ao hub a cada 60s via `HeartbeatPublisher` (routing-key: `heartbeat`). Sem heartbeat o hero não aparece como candidato no painel de roteamento do hub — mesmo estando registrado como GitHub Issue.

```
Início → delay 5s → HeartbeatMessage(heroId, heroName, skills, timestamp)
           └─ repete a cada 60s enquanto o processo estiver vivo
```

---

## API REST local

Base: `http://localhost:8091`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/pitch` | Gera pitch a partir de um `ProjectBrief` JSON |
| `GET` | `/actuator/health` | Health check |

### Exemplo de chamada direta

```http
POST http://localhost:8091/api/pitch
Content-Type: application/json

{
  "id": "test-001",
  "projectName": "SkillForge",
  "problem": "Devs não têm forma rastreável de provar suas skills",
  "audiences": ["dev senior", "CTO", "angel investor"],
  "differentiator": "Skills validadas por desafios reais com IA, registradas no GitHub",
  "traction": "6 devs fundadores, meta de 50 beta em 90 dias",
  "revenueModel": "Freemium: quests públicas gratuitas + guild membership pago",
  "cta": "Entre na guild de fundadores",
  "context": ""
}
```

Retorna `200 OK` com `{valid, confidence, guildPitch, investorOnePager, scores}` ou `422 Unprocessable Entity` com os documentos gerados mas reprovados na validação.

---

## Rodando

### Pré-requisitos

- Java 21+
- Maven 3.9+
- RabbitMQ acessível
- `ANTHROPIC_API_KEY` — obrigatório (sem fallback local, LLM de marketing não existe em SLM 7B)
- `GITHUB_TOKEN` — opcional; sem ele os artefatos não são postados na issue

### Subindo localmente

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=ghp_...         # opcional
export AMQP_URL=amqp://localhost

cd hero-marketing
mvn spring-boot:run
```

O hero sobe em `http://localhost:8091` e começa a enviar heartbeats ao hub em 5 segundos.

---

## Configuração

`src/main/resources/application.yml`:

```yaml
server:
  port: 8091

spring:
  rabbitmq:
    addresses: ${AMQP_URL}

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}

guild:
  amqp:
    exchange: ${AMQP_EXCHANGE:skillforge}
    heartbeat-interval-ms: 60000
    heartbeat-initial-delay-ms: 5000

skillforge:
  github:
    token: ${GITHUB_TOKEN:}
  hero:
    id: hero-marketing
    name: Hero Marketing
    skills: pitch-scribe,narrative-design,audience-segmentation,structured-writing
  pitch:
    confidence-threshold: ${CONFIDENCE_THRESHOLD:0.75}
```

| Variável de ambiente | Padrão | Descrição |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | API key da Anthropic (obrigatória) |
| `AMQP_URL` | — | URL do broker RabbitMQ |
| `GITHUB_TOKEN` | vazio | Token para postar comentários nas issues |
| `AMQP_EXCHANGE` | `skillforge` | Exchange AMQP (deve coincidir com o hub) |
| `CONFIDENCE_THRESHOLD` | `0.75` | Confiança mínima para considerar solução válida |

---

## Estrutura do módulo

```
hero-marketing/
├── amqp/
│   ├── AmqpConfig.java          — filas, exchange, Jackson converter; binding: problem.#
│   ├── HeartbeatPublisher.java  — envia heartbeat a cada 60s
│   ├── PitchListener.java       — recebe ProblemMessage, orquestra o fluxo
│   ├── ProblemMessage.java      — mensagem de entrada (questId, problem, requiredSkills, questUrl, questBody)
│   └── SolutionMessage.java     — mensagem de saída (questId, heroId, confidence, model, solvedAt)
│
├── client/
│   └── AnthropicClient.java     — chamadas à Claude API (generate com system prompt + user prompt)
│
├── domain/
│   ├── PitchDraft.java          — resultado: guildPitch, investorOnePager, scores, valid, confidence
│   └── ProjectBrief.java        — input: projectName, problem, audiences, differentiator, traction, cta
│
├── service/
│   ├── GitHubCommentService.java   — posta artefatos como comentário na issue da quest
│   ├── PitchGeneratorService.java  — gera os dois documentos via Claude (temp 0.8)
│   └── PitchValidatorService.java  — valida com dois críticos distintos (temp 0.1)
│
└── web/
    └── PitchController.java    — POST /api/pitch para testes diretos
```

### Prompts

```
src/main/resources/prompts/
├── agency-system-prompt.txt          — persona: The Agency; gera os dois documentos
├── guild-pitch-validator-prompt.txt  — persona: dev cético com 8 anos de exp
└── investor-validator-prompt.txt     — persona: angel investor; exige MRR ou LOI para credibilidade ≥ 7
```

---

## Relação com o hub

O hero **não depende do hub** para funcionar. Ele:
1. Lê `ProblemMessage` da fila AMQP (`skillforge.hero-marketing.problems`)
2. Posta artefatos direto no GitHub
3. Publica `SolutionMessage` de volta na exchange

O hub consome `SolutionMessage` e decide se auto-valida as skills. O hero nunca chama o hub via HTTP.

### Binding AMQP

```
Exchange:     skillforge  (topic)
Queue:        skillforge.hero-marketing.problems
Binding key:  problem.#   (recebe qualquer quest despachada, independente da skill)
Reply key:    solution.pitch-design
```

> **Por que `problem.#` e não `problem.pitch-scribe`?**  
> O roteador do hub usa `problem.{normalizedSkill}` com a skill principal da quest. Como "pitch-scribe" pode não ser a skill indexada na issue, o binding wildcard garante que o hero receba qualquer quest despachada enquanto o sistema está em desenvolvimento. Em produção, restrinja para `problem.pitch-scribe` após confirmar a normalização.
