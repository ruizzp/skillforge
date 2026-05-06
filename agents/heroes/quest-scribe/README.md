# Hero: Quest Scribe

> O guardião das quests. Transforma contexto de domínio em quests bem estruturadas.

---

## Identidade

| Campo | Valor |
|---|---|
| `heroId` | `quest-scribe` |
| `heroClass` | Scribe |
| `endpoint` | `http://localhost:8090` |
| `model principal` | `claude-sonnet-4-6` (Claude API) |
| `model validador` | `meditron` (Ollama local) |

**Skills declaradas:**
- `quest-design` — estrutura quests segundo o QUEST_FRAMEWORK.md
- `domain-modeling` — define atores, vocabulário e regras de domínio
- `clinical-reasoning` — valida plausibilidade em domínios médicos
- `fixture-generation` — gera casos fictícios com gabarito

---

## O que este hero faz

Recebe um problema de design de quest via AMQP e devolve um rascunho completo:

```
Entrada:
{
  "domain":    "medical",
  "questType": "triagem",
  "level":     "RARE",
  "context":   "Paciente chega com queixa de dor torácica..."
}

Saída:
{
  "solution":    "### PS-003 — Motor de Triagem...\n...",
  "confidence":  0.87,
  "model":       "claude-sonnet-4-6 + meditron"
}
```

---

## Como o hero processa

```
1. Recebe ProblemMessage via AMQP
        ↓
2. QuestGuardianService valida se o domain profile está aprovado
   (lê o repo — não consulta banco)
        ↓
   Se bloqueantes → devolve confidence 0.0 + lista de pendências
        ↓
3. Claude API gera o rascunho com system-prompt.md como consciência
        ↓
4. Para cada fixture gerado, Meditron valida plausibilidade clínica
   (usa profile-validator.md)
        ↓
5. Confidence ajustado conforme resultado da validação
        ↓
6. Publica SolutionMessage
        ↓
7. Se confidence >= 0.75, grava rascunho em quests/domains/{domain}/drafts/
```

---

## Arquivos deste hero

| Arquivo | Responsabilidade |
|---|---|
| `manifest.json` | Identidade, skills, modelo, endpoint |
| `README.md` | Este arquivo — visão geral e status |
| `system-prompt.md` | Consciência do agente (Claude API) |
| `validator-contract.md` | Contrato que todo domínio deve implementar |

**O que NÃO está aqui:** validadores específicos de domínio.  
Cada domínio define o seu próprio em `quests/domains/{domain}/validators/`.  
O hero lê o contrato — o domínio implementa.

---

## Módulo Spring Boot (a implementar)

```
quest-scribe/
├── pom.xml
└── src/main/
    ├── java/com/skillforge/scribe/
    │   ├── QuestScribeApplication.java
    │   ├── amqp/
    │   │   └── QuestProblemConsumer.java
    │   ├── service/
    │   │   ├── QuestGuardianService.java   ← orquestra Claude + Meditron
    │   │   └── ProfileValidatorService.java ← lê repo, valida bloqueantes
    │   └── client/
    │       ├── AnthropicClient.java        ← Claude API
    │       └── MeditronClient.java         ← Ollama local
    └── resources/
        ├── manifest.json
        └── application.yml
```

Ver `CLAUDE.md` → seção "Decisões em Aberto" para contexto do design.  
Ver `agents/AGENT_GUIDE.md` para o checklist antes de subir.

---

## Status

`[ ]` Módulo Spring Boot não implementado — definição completa, código pendente.

Pré-requisitos para implementar:
- `[ ]` Domain profile médico aprovado (ver `quests/domains/medical/DOMAIN_PROFILE.md`)
- `[ ]` `ANTHROPIC_API_KEY` disponível no ambiente
- `[ ]` Meditron rodando localmente (`ollama pull meditron`)