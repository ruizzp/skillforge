# Quest Framework

> O que é invariante em toda quest, independente do domínio de negócio.

---

## A Ideia Central

Uma quest ensina um dev a resolver um problema real dentro de um domínio específico.

Mas a **estrutura** de como uma quest é construída não muda.  
O que muda é o **contexto** — o vocabulário, os dados, as regras de validação.

```
Quest = Estrutura Invariante + Perfil de Domínio
```

---

## As 7 Perguntas Universais

Toda quest, em qualquer domínio, nasce de uma consciência que faz as mesmas perguntas.  
O domínio preenche as respostas de forma diferente — as perguntas nunca mudam.

| # | Pergunta | O que define |
|---|---|---|
| 1 | **Quem é o ator?** | Modelo de dados central do domínio |
| 2 | **O que está acontecendo?** | O evento ou problema que dispara a ação |
| 3 | **Quão urgente é isso?** | Critério de priorização |
| 4 | **Qual é o contexto histórico?** | Estado anterior relevante |
| 5 | **O que pode dar errado?** | Riscos, edge cases, validações |
| 6 | **O que fazemos agora?** | A ação ou decisão esperada |
| 7 | **Como sabemos que funcionou?** | Critérios de aceitação mensuráveis |

Cada pergunta mapeia para uma quest ou para uma seção de uma quest maior.

---

## Anatomia de uma Quest (Schema Invariante)

Todo arquivo de quest deve ter estes campos — sem exceção:

```markdown
### `{DOMAIN}-{NNN}` — {Título} `{RARITY}` `+{XP} XP`

**Domínio:** {nome do domínio}
**Status:** [ ] / [~] / [x] / [!]
**Skills requeridas:** lista de skills técnicas
**Pergunta universal:** qual das 7 perguntas esta quest responde

**Contexto de Domínio** (máx. 1 página)
  O que o dev precisa saber sobre o negócio para resolver esta quest.
  Sem jargão sem explicação. Sem pressupor conhecimento prévio.

**O que fazer**
  Descrição técnica da implementação esperada.

**Fixtures de Teste**
  Casos fictícios com gabarito de domínio validado.
  Mínimo 3 casos: happy path, edge case, caso de falha.

**Critérios de Aceitação**
  Separados em duas camadas:
  - [ ] Técnico — o que o software deve fazer
  - [ ] Domínio — o que o resultado deve significar no contexto de negócio
```

**Por que as duas camadas de critério são obrigatórias:**  
Um endpoint pode retornar 200 e ainda assim estar errado clinicamente, financeiramente ou logisticamente.  
A separação força o dev a pensar nos dois planos.

---

## Perfil de Domínio

Cada domínio de negócio precisa de um perfil antes que qualquer quest possa ser criada.  
O perfil responde: *o que é específico deste domínio que toda quest vai precisar?*

### Estrutura de um Perfil (`DOMAIN_PROFILE.md`)

```markdown
# Domain Profile — {Nome do Domínio}

## Metáfora
A consciência que guia este domínio. Quem ela é, onde acorda, o que não sabe ainda.

## Ator Central
O modelo de dados mínimo. Quem é a "pessoa" ou "entidade" deste domínio.

## Vocabulário Controlado
Termos que o dev vai encontrar nas quests. Explicados sem pressupor contexto.

## Escalas e Regras de Domínio
As regras que existem no mundo real e que as quests devem respeitar.
(ex: Manchester Triage, SOFA score, regras de compliance financeiro)

## Fontes de Dados Fictícios
Como gerar fixtures realistas para este domínio.
Ferramentas, geradores, conjuntos de dados sintéticos recomendados.

## Modelo Validador
Qual SLM (ou estratégia) valida a correção de domínio dos gabaritos.
(ex: meditron para médico, phi3:mini para domínios genéricos)

## Questões Abertas do Domínio
O que ainda não foi decidido e precisa de resposta antes de criar novas quests.
```

---

## Hierarquia de Arquivos

```
quests/
├── QUEST_FRAMEWORK.md          ← este arquivo (invariante)
│
├── domains/
│   ├── medical/
│   │   ├── DOMAIN_PROFILE.md   ← perfil do domínio médico
│   │   ├── PRONTO_SOCORRO.md   ← metáfora + perguntas + esboço de quests
│   │   └── fixtures/           ← casos fictícios com gabarito
│   │       └── ps-cases.json
│   │
│   ├── financial/              ← a preencher quando o domínio for iniciado
│   └── logistics/
│
└── catalog/                    ← quests aprovadas, prontas para o board
    ├── PS-001-modelo-paciente.md
    └── PS-002-parser-queixa.md
```

**Regra:** nenhuma quest vai para `catalog/` sem um `DOMAIN_PROFILE.md` aprovado para o domínio.

---

## Processo de Criação de uma Quest Nova

```
1. Domínio existe?
   └── Não → criar DOMAIN_PROFILE.md primeiro
   └── Sim → continuar

2. Qual das 7 perguntas esta quest responde?
   └── Mapear explicitamente no campo "Pergunta universal"

3. Gerar fixtures com gabarito de domínio
   └── Usar o modelo validador definido no perfil
   └── Mínimo 3 casos (happy, edge, falha)

4. Escrever critérios em duas camadas
   └── Técnico: o software funciona?
   └── Domínio: o resultado faz sentido no negócio?

5. Revisão por hero com skill de domínio ou pelo Quest Scribe
   └── Quest Scribe valida estrutura (Claude API)
   └── Modelo de domínio valida gabarito clínico/financeiro/etc.

6. Quest aprovada → mover para catalog/
```

---

## Adicionando um Novo Domínio

Checklist mínimo antes de criar a primeira quest.

**O guardrail lê estes sinais do repositório — não há estado externo:**

| Sinal no repo | O que verifica |
|---|---|
| `domains/{domain}/DOMAIN_PROFILE.md` existe | Perfil criado |
| `domains/{domain}/DOMAIN_PROFILE.md` sem `[ ]` bloqueantes | Perfil aprovado |
| `domains/{domain}/fixtures/` com ≥ 5 arquivos `.json` | Fixtures criadas |
| `domains/{domain}/DOMAIN_PROFILE.md` tem seção `## Modelo Validador` preenchida | Validador definido |
| Qualquer `manifest.json` de hero ativo declara skill do domínio | Hero disponível |

Um domínio está **pronto** quando todos esses sinais existem no repo.  
Não há flag, banco ou cache — o guardrail relê os arquivos a cada execução.

---

## O que não varia entre domínios

- O schema de uma quest (campos obrigatórios)
- As 7 perguntas como ponto de partida
- A separação técnico vs domínio nos critérios
- A obrigatoriedade de fixtures com gabarito
- O processo de aprovação

## O que varia por domínio

- A metáfora e o vocabulário
- O modelo validador (SLM especializado vs genérico)
- As escalas e regras de negócio
- A fonte de dados fictícios
- O nível de contexto clínico/técnico necessário