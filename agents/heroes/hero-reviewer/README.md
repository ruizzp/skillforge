# Hero: Hero Reviewer

> O juiz automatizado da guilda. Avalia soluções contra o Definition of Done de cada quest e devolve veredicto com feedback acionável.

---

## Identidade

| Campo | Valor |
|---|---|
| `heroId` | `hero-reviewer` |
| `heroClass` | Reviewer |
| `port` | `8093` |
| `model` | `llama3.2` via Ollama (configurável) |

**Skills declaradas:**
- `quest-review` — avalia soluções contra DoD de quests
- `solution-evaluation` — atribui score de qualidade 0.0–1.0
- `dod-validation` — extrai e verifica critérios do Definition of Done
- `feedback-generation` — produz feedback específico e acionável para revisão

---

## O que este hero faz

Recebe uma `ReviewMessage` via AMQP e devolve uma `ReviewResultMessage` com veredicto:

```
Entrada (ReviewMessage):
{
  "questId":        "QUEST-42",
  "questTitle":     "Implementar triagem de Manchester",
  "questBody":      "...## Definition of Done\n- [ ] Implementar fluxo...",
  "solution":       "# Solução\n\nImplementei o fluxo de triagem...",
  "heroId":         "hero-marketing",
  "revisionCount":  0
}

Saída (ReviewResultMessage):
{
  "questId":      "QUEST-42",
  "heroId":       "hero-marketing",
  "approved":     false,
  "feedback":     "O critério 'testes unitários' não foi atendido. Adicione...",
  "reviewScore":  0.62,
  "revisionCount": 0,
  "reviewerModel": "llama3.2"
}
```

---

## Como o hero processa

```
1. Recebe ReviewMessage via AMQP (routing-key: "review.#")
        ↓
2. QuestReviewService.extractDoD()
   — Busca seção "## Definition of Done", "## DoD" ou "## Critérios de Aceite"
   — Se nenhum DoD encontrado → aprova automaticamente (score 0.5)
        ↓
3. buildUserPrompt() — monta prompt com título, DoD e solução
        ↓
4. OllamaClient.chat(systemPrompt, userPrompt)
   — Se Ollama indisponível → aprova automaticamente (sem bloquear fluxo)
        ↓
5. parseReviewResponse()
   — Extrai JSON (suporta markdown code fences)
   — Aplica threshold gate: approved=true mas score < 0.70 → rejeita
        ↓
6. Publica ReviewResultMessage
   routing-key: "review-result.quest"
```

---

## Fluxo de revisão iterativa

O `ReviewResultConsumer` no guild-hub orquestra o loop:

```
ReviewResultMessage recebida
        ↓
  approved = true?
  ├─ sim → label: pending-review + solved-by:{heroId}
  │         broadcast: SOLUTION_REVIEWED
  │         (aguarda aprovação humana no dashboard)
  │
  └─ não → revisionCount < maxRevisions?
       ├─ sim → posta feedback como comentário na quest
       │         re-despacha ProblemMessage com feedback no body
       │         broadcast: REVISION_REQUESTED
       │         (hero recebe o problema novamente com contexto adicional)
       │
       └─ não → escalado para humano
                 labels: pending-review + revision-limit
                 broadcast: SOLUTION_REVIEWED (revisionLimit: true)
```

**Limite padrão:** 3 revisões (`MAX_REVISIONS=3`).

---

## DoD — formatos suportados

O hero reconhece três aliases para o cabeçalho do Definition of Done:

```markdown
## Definition of Done
## DoD
## Critérios de Aceite
```

O conteúdo é tudo entre o cabeçalho encontrado e a próxima seção `##` (ou fim do documento).

**Quests sem DoD:** aprovadas automaticamente com score 0.5 e log de aviso. A aprovação automática existe para não bloquear o fluxo — quests bem escritas devem sempre ter DoD.

---

## Threshold de aprovação

Score mínimo: **0.70** (70%). Configurável via `APPROVAL_THRESHOLD`.

Mesmo que o LLM retorne `"approved": true`, se o score for abaixo do threshold o revisor rejeita e inclui feedback. Isso evita que o modelo seja excessivamente complacente.

```
score ≥ 0.70 AND approved = true  →  aprovado
score < 0.70                       →  rejeitado (mesmo com approved=true no JSON)
Ollama indisponível                →  auto-aprovado (fallback)
```

---

## Arquivos deste hero

| Arquivo | Responsabilidade |
|---|---|
| `manifest.json` | Identidade, skills, modelo, endpoint |
| `README.md` | Este arquivo — visão geral e fluxo |
| `system-prompt.md` | System prompt enviado ao LLM para cada revisão |

O módulo Spring Boot completo está em `hero-reviewer/` na raiz do repositório.

---

## Módulo Spring Boot

```
hero-reviewer/
├── pom.xml
└── src/main/
    ├── java/com/skillforge/reviewer/
    │   ├── HeroReviewerApplication.java
    │   ├── amqp/
    │   │   ├── AmqpConfig.java          — fila: hero-reviewer.reviews, binding review.#
    │   │   ├── HeartbeatPublisher.java  — publica heartbeat.hero-reviewer a cada 60s
    │   │   └── ReviewListener.java      — @RabbitListener → chama QuestReviewService
    │   ├── client/
    │   │   └── OllamaClient.java        — HTTP para /api/chat do Ollama, null se indisponível
    │   └── service/
    │       └── QuestReviewService.java  — extrai DoD, chama Ollama, aplica threshold
    └── resources/
        ├── application.yml
        ├── hero-reviewer-manifest.json
        └── prompts/
            └── review-system-prompt.txt
```

---

## Configuração

| Variável de ambiente | Padrão | Descrição |
|---|---|---|
| `OLLAMA_URL` | `http://localhost:11434` | URL base do Ollama |
| `OLLAMA_MODEL` | `llama3.2` | Modelo a usar para avaliação |
| `MAX_REVISIONS` | `3` | Máximo de revisões antes de escalar para humano |
| `APPROVAL_THRESHOLD` | `0.70` | Score mínimo para aprovação |
| `AMQP_URL` | — | URL do broker RabbitMQ |
| `AMQP_EXCHANGE` | `skillforge` | Exchange AMQP |

---

## Status

`[x]` Módulo Spring Boot implementado e funcional.

Pré-requisitos para rodar:
- `[x]` Ollama instalado localmente (`ollama pull llama3.2`)
- `[x]` RabbitMQ acessível (CloudAMQP ou local)
- `[ ]` Quests com `## Definition of Done` bem preenchido para revisão de qualidade real