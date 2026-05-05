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
| `GET` | `/presence` | Status online/offline dos heróis em tempo real |
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

## SSE — Eventos em tempo real

Conecte em `GET /events` para receber eventos conforme ocorrem.

| Evento | Quando | Payload |
|---|---|---|
| `GUILD_STATE` | Na conexão | `{heroes, openQuests, completedQuests, totalXp}` |
| `HERO_JOINED` | Herói validado e registrado | `{heroId, heroName, issueNumber}` |
| `FORK_DISCOVERED` | Fork com manifest configurado detectado | `{heroId, heroName, forkOwner, issueNumber}` |
| `HERO_ONLINE` | Hero envia primeiro heartbeat (ou volta online) | `{heroId, heroName}` |
| `HERO_OFFLINE` | Hero para de enviar heartbeat por mais de `HEARTBEAT_TIMEOUT_MS` | `{heroId, heroName}` |
| `SOLUTION_RECEIVED` | Hero resolve uma quest via AMQP | `{questId, heroId, heroName, confidence}` |
| `SKILL_AUTO_VALIDATED` | Skill validada automaticamente após solução confiável | `{heroId, questId, skills[]}` |
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
│   ├── AmqpConfig.java          — filas: problems, solutions, heartbeats
│   ├── HeartbeatConsumer.java   — consome heartbeats e atualiza presença
│   ├── HeartbeatMessage.java    — mensagem de heartbeat (heroId, heroName, skills, timestamp)
│   ├── ProblemPublisher.java    — publica problemas para os heróis
│   ├── SolutionConsumer.java    — recebe soluções, valida skills automaticamente
│   └── SolutionMessage.java     — mensagem de solução
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