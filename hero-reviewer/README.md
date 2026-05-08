# hero-reviewer

> Módulo Spring Boot do Hero Reviewer — avalia soluções de quests via Ollama e publica veredicto com feedback.

---

## Porta padrão

`8093`

---

## Responsabilidade

Consome `ReviewMessage` da fila `hero-reviewer.reviews` (routing: `review.#`), avalia a solução contra o DoD da quest usando um SLM local via Ollama, e publica `ReviewResultMessage` com routing key `review-result.quest`.

O guild-hub consome o resultado e decide: aprovar, solicitar revisão ou escalar para humano.

---

## Subindo localmente

```bash
# Pré-requisito: Ollama rodando com llama3.2
ollama pull llama3.2

# Subir o módulo
cd hero-reviewer
mvn spring-boot:run
```

Variáveis de ambiente necessárias:

```bash
export AMQP_URL=amqps://user:pass@broker.cloudamqp.com/vhost
export OLLAMA_URL=http://localhost:11434     # padrão, pode omitir
export OLLAMA_MODEL=llama3.2                # padrão, pode omitir
```

---

## AMQP

| Direção | Routing key | Mensagem |
|---|---|---|
| Consome | `review.#` | `ReviewMessage` |
| Publica | `review-result.quest` | `ReviewResultMessage` |
| Publica | `heartbeat.hero-reviewer` | `HeartbeatMessage` (a cada 60s) |

Exchange: `skillforge` (topic)  
Fila própria: `hero-reviewer.reviews`

---

## Configuração

`src/main/resources/application.yml`:

```yaml
server:
  port: 8093

ollama:
  base-url: ${OLLAMA_URL:http://localhost:11434}
  model:     ${OLLAMA_MODEL:llama3.2}

skillforge:
  reviewer:
    approval-threshold: ${APPROVAL_THRESHOLD:0.70}

guild:
  amqp:
    exchange: ${AMQP_EXCHANGE:skillforge}
  reviewer:
    max-revisions: ${MAX_REVISIONS:3}
```

| Variável | Padrão | Descrição |
|---|---|---|
| `OLLAMA_URL` | `http://localhost:11434` | URL base do Ollama |
| `OLLAMA_MODEL` | `llama3.2` | Modelo para avaliação |
| `APPROVAL_THRESHOLD` | `0.70` | Score mínimo para aprovação |
| `MAX_REVISIONS` | `3` | Máx. revisões antes de escalar (lido pelo guild-hub) |
| `AMQP_URL` | — | URL do broker RabbitMQ |
| `AMQP_EXCHANGE` | `skillforge` | Exchange AMQP |

---

## Fallbacks

| Situação | Comportamento |
|---|---|
| Quest sem DoD | Auto-aprovado, score 0.5, log `INFO` |
| Ollama indisponível | Auto-aprovado, score 0.5, log `WARN` |
| Resposta não parseável | Auto-aprovado, score 0.5, log `WARN` |
| Score < threshold (mesmo com `approved:true`) | Rejeitado, feedback gerado |

O fallback de auto-aprovação existe para não bloquear o fluxo quando o revisor não consegue avaliar. Quests com DoD bem definido e Ollama disponível recebem avaliação real.

---

## Estrutura do módulo

```
hero-reviewer/
├── pom.xml
└── src/main/
    ├── java/com/skillforge/reviewer/
    │   ├── HeroReviewerApplication.java
    │   ├── amqp/
    │   │   ├── AmqpConfig.java          — fila + binding + TypePrecedence.INFERRED
    │   │   ├── HeartbeatPublisher.java  — heartbeat a cada 60s
    │   │   ├── ReviewListener.java      — @RabbitListener
    │   │   ├── ReviewMessage.java       — record de entrada
    │   │   └── ReviewResultMessage.java — record de saída
    │   ├── client/
    │   │   └── OllamaClient.java        — Java 21 HttpClient, 30s connect / 120s read
    │   └── service/
    │       └── QuestReviewService.java  — DoD extraction + avaliação + threshold gate
    └── resources/
        ├── application.yml
        ├── hero-reviewer-manifest.json
        └── prompts/
            └── review-system-prompt.txt
```

Documentação completa do hero (skills, fluxo, calibração do prompt): `agents/heroes/hero-reviewer/`