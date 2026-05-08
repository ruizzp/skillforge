# Guild Hub

> Orquestrador central da guilda. Descobre heróis em forks, valida registros, expõe a API REST e transmite eventos em tempo real via SSE.

---

## O que faz

O Guild Hub é o único módulo que precisa rodar de forma contínua e acessível. Ele é o **banco de dados vivo** da guilda — tudo que acontece (herói registrado, quest criada, fork descoberto) fica persistido como issue no repositório GitHub, e o hub lê, agrega e expõe esses dados.

```
Repo GitHub (Issues)
  ├─ label: hero + registered  →  registro de heróis
  ├─ label: hero + invalid      →  registros rejeitados
  ├─ label: hero (sem mais)     →  pendentes de validação
  └─ label: quest               →  quest board

Guild Hub (este módulo)  :8080
  ├─ descobre forks automaticamente
  ├─ valida e registra novos heróis
  ├─ expõe API REST   → /api/*
  ├─ dashboard web    → /
  └─ stream SSE       → /events
```

---

## Fluxo completo: fork → herói registrado

```
1. Dev faz fork deste repo
2. Configura manifest.json com heroId, skills e endpoint
3. (sem mais nada — o hub faz o resto)

ForkWatcher         (a cada 10 min)
  ├─ lista todos os forks do repo principal
  ├─ para cada fork: tenta ler manifest.json em 3 caminhos possíveis
  ├─ ignora forks com heroId ainda no valor padrão ("your-hero-id")
  ├─ ignora forks cujo owner já tem issue hero no repo
  ├─ enriquece o manifest com githubLogin + avatarUrl do fork owner
  └─ cria issue com label "hero" no repo principal

RegistrationWatcher  (a cada 2 min)
  ├─ detecta issues com label "hero" sem "registered" nem "invalid"
  ├─ valida o manifest (heroId, heroName, skills, endpoint)
  ├─ se válido:   posta comentário de boas-vindas + label "registered"
  └─ se inválido: lista os erros + label "invalid"

HeroRegistryService  (a cada 5 min + após cada registro)
  └─ lê issues "hero + registered" → atualiza leaderboard e skill map
```

---

## Caminhos de manifest tentados

Ao escanear um fork, o hub tenta ler o manifest nessa ordem:

| # | Caminho |
|---|---|
| 1 | `hero-template/src/main/resources/manifest.json` |
| 2 | `src/main/resources/manifest.json` |
| 3 | `manifest.json` |

O primeiro caminho que responder com conteúdo válido é usado.

---

## Roteamento e despacho de quests

O hub elege qual herói resolve qual quest com base na interseção de skills e disponibilidade online.

```
GET /api/routing
  └─ QuestRoutingService.buildRoutingTable()
       ├─ para cada quest aberta: encontra heroes com skill intersection > 0
       ├─ ordena: online primeiro, depois por maior interseção
       └─ retorna: [ { questId, questTitle, routingKey, candidates[], elected } ]

POST /api/routing/{questId}/dispatch?submittedBy={login}
  └─ QuestRoutingService.dispatch(questId, submittedBy)
       ├─ valida: elected hero está online?
       ├─ constrói ProblemMessage com questUrl + questBody completo
       └─ publica no exchange "skillforge" com routingKey "problem.{skill}"
```

### Normalização de skills

Labels no GitHub usam espaço (`"narrative design"`); manifests usam hífen (`"narrative-design"`). O roteador normaliza ambos os lados antes de comparar:

```java
private static String normalizeSkill(String skill) {
    return skill.toLowerCase().replace(' ', '-').trim();
}
```

Heróis que não normalizarem as skills do manifest **não** aparecerão como candidatos.

---

## Ciclo completo: despacho → solução → revisão → aprovação humana

```
Dashboard  →  POST /api/routing/{questId}/dispatch
                └─ hub publica ProblemMessage(questId, problem, requiredSkills,
                     xpReward, submittedBy, questUrl, questBody)
                     routing-key: "problem.{normalizedSkill}"
                          └─ hero recebe via @RabbitListener
                               ├─ gera solução (LLM)
                               ├─ posta resultado como comentário na issue GitHub
                               └─ publica SolutionMessage(questId, heroId, confidence)
                                    routing-key: "solution.{skill}"
                                         └─ SolutionConsumer (hub)
                                              ├─ broadcast SSE SOLUTION_RECEIVED
                                              ├─ label: review-pending
                                              └─ publica ReviewMessage
                                                   routing-key: "review.{skill}"
                                                        └─ hero-reviewer recebe
                                                             ├─ extrai DoD do questBody
                                                             ├─ avalia solução vs DoD via Ollama
                                                             └─ publica ReviewResultMessage
                                                                  routing-key: "review-result.quest"
                                                                       └─ ReviewResultConsumer (hub)
                                                                            ├─ score ≥ threshold?
                                                                            │    ├─ sim → label pending-review
                                                                            │    │         label solved-by:{heroId}
                                                                            │    │         broadcast SOLUTION_REVIEWED
                                                                            │    └─ não (< maxRevisions)?
                                                                            │         ├─ sim → comenta feedback na quest
                                                                            │         │         re-publica ProblemMessage
                                                                            │         │         broadcast REVISION_REQUESTED
                                                                            │         └─ não → label pending-review + revision-limit
                                                                            │                   broadcast SOLUTION_REVIEWED
                                                                            │
                                                                            └─ (quando aprovação humana via dashboard)
                                                                                 POST /api/routing/{questId}/approve
                                                                                      └─ github.validateSkill()
                                                                                         github.addXp()
                                                                                         broadcast SKILL_AUTO_VALIDATED
                                                                                         broadcast QUEST_COMPLETED
```

**Revisões automáticas:** um herói pode ser chamado até `MAX_REVISIONS` vezes para refinar a solução antes de escalar para aprovação humana. O feedback de cada rodada é embutido no corpo da quest re-despachada.

**Persistência dos artefatos:** cada herói posta a solução como comentário na issue da quest. O hub não armazena o conteúdo — o GitHub Issue é o banco de dados.

### Labels de ciclo de vida da quest

| Label | Significado |
|---|---|
| `review-pending` | Solução recebida, aguardando revisão automatizada |
| `solved-by:{heroId}` | Herói que submeteu solução aprovada pelo revisor |
| `revision-count:{n}` | Número da tentativa atual (incrementado a cada revisão) |
| `revision-limit` | Limite de revisões atingido — escalado para revisão humana |
| `pending-review` | Pronto para aprovação humana (passou no revisor ou atingiu limite) |
| `assigned-to:{heroId}` | Herói para quem a quest foi despachada |

---

## Sincronização manual (refresh)

```
POST /api/refresh
  ├─ registry.refresh()    → re-lê issues hero+registered do GitHub
  └─ questBoard.refresh()  → re-lê issues quest do GitHub

Dashboard (botão ⟳)
  └─ chama POST /api/refresh
       └─ após resposta: recarrega /api/routing e atualiza painel

Auto-refresh configurável: manual / 30s / 1min / 5min  (padrão: 1min)
```

---

## API REST

Base: `http://localhost:8080/api`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/heroes` | Lista todos os heróis registrados |
| `GET` | `/heroes/{heroId}` | Dados de um herói específico |
| `GET` | `/leaderboard` | Ranking por XP com posição e nível |
| `GET` | `/quests` | Lista quests (filtros opcionais) |
| `POST` | `/quests` | Cria nova quest (requer GITHUB_TOKEN) |
| `GET` | `/stats` | Estatísticas completas da guilda |
| `POST` | `/heroes/{heroId}/skills/{skill}/validate` | Envia desafio AMQP ao herói; valida se resposta ≥ threshold |
| `GET` | `/presence` | Status online/offline dos heróis em tempo real |
| `GET` | `/routing` | Tabela de roteamento: quests × heroes × skills |
| `POST` | `/routing/{questId}/dispatch` | Despacha quest para o herói eleito |
| `POST` | `/routing/{questId}/approve` | Aprova solução de herói (valida skills + XP) — `?heroId=` opcional |
| `POST` | `/routing/{questId}/rereview` | Força nova revisão automatizada — body `{"heroId":"…","solution":"…"}` (solution = texto do comentário específico) |
| `GET` | `/quests?status=pending-review` | Lista quests aguardando aprovação humana |
| `GET` | `/quests/{questId}/comments` | Comentários da issue da quest (soluções submetidas) |
| `GET` | `/heroes/{heroId}/activity` | Histórico de quests concluídas pelo herói |
| `GET` | `/heroes/{heroId}/quests` | Quests ativas e em revisão do herói |
| `POST` | `/refresh` | Força re-sync imediato com GitHub (sem esperar ciclo de 5min) |
| `GET` | `/health` | Health check |
| `GET` | `/events` | SSE stream de eventos em tempo real |

### Filtros de quests

```
GET /api/quests?rarity=RARE&status=open
```

| Parâmetro | Valores | Padrão |
|---|---|---|
| `rarity` | `COMMON`, `RARE`, `EPIC`, `LEGENDARY` | todos |
| `status` | `open`, `completed`, `all` | `all` |

### Criar quest

```http
POST /api/quests
Content-Type: application/json

{
  "title": "Implementar cache distribuído",
  "body": "Descrição detalhada da quest...",
  "rarity": "RARE",
  "xpReward": 300,
  "requiredSkills": ["java", "redis"]
}
```

Cria um GitHub Issue com labels `quest`, `rare`, `xp:300`, `skill:java`, `skill:redis`.

---

## Validação de skills — fluxo completo

Clicar em "validar" no dashboard **não** aplica o label imediatamente. O hub envia um desafio real ao herói via AMQP e só valida quando ele responder com confiança suficiente.

```
Dashboard  →  POST /api/heroes/{heroId}/skills/java/validate
                └─ hub publica ProblemMessage questId="probe:java:{heroId}"
                     └─ hero recebe (se tiver "java" em skills)
                          └─ solve() → SolutionMessage(confidence=0.87)
                               └─ SolutionConsumer detecta questId.startsWith("probe:")
                                    └─ confidence ≥ 0.75?
                                         ├─ sim → github.validateSkill() → label skill-validated:java
                                         │         broadcast SKILL_AUTO_VALIDATED
                                         │         dashboard atualiza botão → "✓ validada"
                                         └─ não → sem ação (hero pode tentar de novo)
```

**Comportamento por estado:**

| Situação | Resposta do endpoint | Ação no dashboard |
|---|---|---|
| Skill não declarada no manifest | `400 Bad Request` | Botão "erro" em vermelho |
| Skill já validada | `200 OK` | Botão "✓ validada" imediatamente |
| Desafio enviado | `202 Accepted` | Botão "aguardando..." até SSE chegar |
| Herói offline / sem skill | Sem resposta AMQP | Botão fica "aguardando..." (timeout do broker) |

O threshold padrão é `0.75` (75% de confiança). Configurável via `SKILL_CONFIDENCE_THRESHOLD`.

---

## Presença dos heróis

> **Para quem desenvolve um hero:** heartbeat é obrigatório para aparecer no painel de roteamento. Sem heartbeat o hero existe no leaderboard mas nunca é eleito como candidato. Veja o padrão `HeartbeatPublisher` no `hero-template` ou no `hero-marketing`.

O hub rastreia quais heróis estão online em tempo real via heartbeats AMQP.

```
Hero (ao subir, após 5s)  →  HeartbeatMessage(heroId, heroName, skills, timestamp)
                                  └─ routing-key: "heartbeat"
                                       └─ HeartbeatConsumer → HeroPresenceService
                                            ├─ primeira vez: broadcast HERO_ONLINE
                                            └─ atualiza timestamp

HeroPresenceService (a cada 60s)
  └─ verifica heróis sem heartbeat há mais de HEARTBEAT_TIMEOUT_MS
       └─ remove do Set → broadcast HERO_OFFLINE
```

O dashboard mostra um **dot colorido** ao lado do XP de cada herói no leaderboard:
- **Verde** (com glow) — herói online agora
- **Cinza** — herói offline ou nunca conectado

O estado inicial do dot é renderizado pelo Thymeleaf no page load (heróis já online antes da abertura do browser aparecem corretamente). Atualizações em tempo real chegam via SSE.

---

## SSE — Eventos em tempo real

Conecte em `GET /events` para receber eventos conforme ocorrem.

| Evento | Quando | Payload |
|---|---|---|
| `GUILD_STATE` | Na conexão | `{heroes, openQuests, completedQuests, totalXp}` |
| `HERO_JOINED` | Herói validado e registrado | `{heroId, heroName, issueNumber}` |
| `FORK_DISCOVERED` | Fork com manifest configurado detectado | `{heroId, heroName, forkOwner, issueNumber}` |
| `HERO_ONLINE` | Hero envia primeiro heartbeat (ou volta online) | `{heroId, heroName}` |
| `HERO_OFFLINE` | Hero para de enviar heartbeat por mais de `HEARTBEAT_TIMEOUT_MS` | `{heroId, heroName}` |
| `QUEST_ROUTED` | Quest despachada para um herói | `{questId, heroId, heroName, routingKey}` |
| `SOLUTION_RECEIVED` | Hero submete solução via AMQP | `{questId, heroId, heroName, confidence}` |
| `SOLUTION_REVIEWED` | Revisor aprova solução (ou atinge limite) | `{questId, heroId, approved, score, revisionLimit?}` |
| `REVISION_REQUESTED` | Revisor rejeita e solicita revisão | `{questId, heroId, attempt, feedback}` |
| `SKILL_AUTO_VALIDATED` | Skill validada após aprovação humana | `{heroId, questId, skills[], xp, totalXp}` |
| `QUEST_COMPLETED` | Quest encerrada após aprovação humana | `{questId, heroId}` |
| `QUEST_UNLOCKED` | Quest criada (futuro) | `{rarity, title, xpReward}` |

Exemplo com `curl`:
```bash
curl -N http://localhost:8080/events
```

---

## Rodando

### Pré-requisitos

- Java 21+
- Maven 3.9+
- `GITHUB_TOKEN` com permissões `repo` (leitura + escrita de issues)

### Sem token (modo leitura)

O hub sobe mas o `ForkWatcher` e o `RegistrationWatcher` ficam desativados. A API REST e o dashboard funcionam normalmente para dados já existentes.

```bash
cd guild-hub
mvn spring-boot:run
```

### Com token (modo completo)

```bash
export GITHUB_TOKEN=ghp_seu_token_aqui
cd guild-hub
mvn spring-boot:run
```

Ou passando via propriedade:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--guild.github.token=ghp_seu_token"
```

O hub sobe em `http://localhost:8080`.

---

## Configuração

`src/main/resources/application.yml`:

```yaml
server:
  port: 8080

guild:
  github:
    owner: fidelisfelipe
    repo: skillforge
    token: ${GITHUB_TOKEN:}
  amqp:
    exchange: ${AMQP_EXCHANGE:skillforge}
    queue: ${AMQP_QUEUE:skillforge.problems}

skillforge:
  skill-validation:
    confidence-threshold: ${SKILL_CONFIDENCE_THRESHOLD:0.75}
  heartbeat:
    timeout-ms: ${HEARTBEAT_TIMEOUT_MS:180000}       # 3 min sem heartbeat = offline
    check-interval-ms: ${HEARTBEAT_CHECK_INTERVAL_MS:60000}  # verifica a cada 1 min
```

| Variável de ambiente | Padrão | Descrição |
|---|---|---|
| `GITHUB_TOKEN` | — | Token com permissão `repo` |
| `AMQP_URL` | — | URL do broker RabbitMQ |
| `AMQP_EXCHANGE` | `skillforge` | Exchange AMQP |
| `AMQP_QUEUE` | `skillforge.problems` | Fila de problemas |
| `SKILL_CONFIDENCE_THRESHOLD` | `0.75` | Confiança mínima para auto-validar skills |
| `HEARTBEAT_TIMEOUT_MS` | `180000` | Tempo sem heartbeat para marcar hero offline |
| `HEARTBEAT_CHECK_INTERVAL_MS` | `60000` | Frequência de verificação de heroes offline |
| `MAX_REVISIONS` | `3` | Máximo de revisões automáticas antes de escalar para humano |

---

## Estrutura do módulo

```
guild-hub/
├── domain/
│   ├── GuildMember.java       — herói registrado
│   ├── HeroLevel.java         — níveis: Apprentice → Archmage
│   ├── LeaderboardEntry.java  — entrada do ranking (rank + membro + nível)
│   ├── Quest.java             — quest do board
│   └── QuestRarity.java       — COMMON / RARE / EPIC / LEGENDARY
│
├── github/
│   └── GitHubClient.java      — toda comunicação com a API do GitHub
│
├── registration/
│   ├── ForkWatcher.java        — descobre forks com manifest configurado
│   ├── HeroValidator.java      — valida campos obrigatórios do manifest
│   └── RegistrationWatcher.java — processa issues pendentes de registro
│
├── amqp/
│   ├── AmqpConfig.java            — filas: problems, solutions, heartbeats, review-results
│   ├── HeartbeatConsumer.java     — consome heartbeats e atualiza presença
│   ├── HeartbeatMessage.java      — mensagem de heartbeat
│   ├── ProblemMessage.java        — mensagem de problema despachado ao herói
│   ├── ProblemPublisher.java      — publica problemas para os heróis
│   ├── ReviewMessage.java         — mensagem enviada ao hero-reviewer
│   ├── ReviewResultConsumer.java  — processa veredicto: aprova, solicita revisão ou escala
│   ├── ReviewResultMessage.java   — veredicto do hero-reviewer
│   ├── SolutionConsumer.java      — recebe soluções, encaminha ao hero-reviewer
│   └── SolutionMessage.java       — mensagem de solução
│
├── service/
│   ├── HeroPresenceService.java — rastreia online/offline via heartbeats, broadcast SSE
│   ├── HeroRegistryService.java — registry de heróis, leaderboard, skill map
│   └── QuestBoardService.java   — quest board, contadores, criação de quests
│
└── web/
    ├── HubApiController.java       — REST JSON /api/*
    └── HubDashboardController.java — dashboard Thymeleaf + SSE /events
```

---

## Relação com os outros módulos

| Módulo | Papel |
|---|---|
| `guild-hub` | Orquestrador central. Roda em produção. |
| `hero-template` | Referência / arquétipo. Devs fazem fork e customizam. Não roda no hub. |
| `hero-{nome}` | Repo separado de cada dev (fork do hero-template ou do skillforge completo). |

### Canais de comunicação

Os nós da guilda **não se comunicam via endpoint HTTP entre si**. Cada nó só conhece os outros através dos canais abaixo:

| Canal | Usado para |
|---|---|
| **GitHub Issues** | Registro de heróis, quests, validação de skills — estado persistido |
| **RabbitMQ (AMQP)** | Troca de problemas e soluções em tempo real entre hub e heróis |
| **SSE (`/events`)** | Dashboard recebe eventos do hub (somente leitura, hub → browser) |

O hub publica `ProblemMessage` na fila e qualquer herói online que tenha as skills necessárias responde com `SolutionMessage`. O `endpoint` no manifest serve apenas para identificação — **não é chamado diretamente pelo hub**.