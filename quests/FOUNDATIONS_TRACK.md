# Foundations Track — Learn Claude

> Trilha de fundamentos para devs que querem construir heroes de verdade.
> Não é obrigatória para entrar na guilda — mas sem ela, você vai sentir falta.

---

## Por que fazer esta trilha

Quests de domínio (médico, financeiro, logística) exigem que você saiba
conversar com modelos de linguagem de forma eficiente.

Sem isso, você pode implementar o código certo e ainda assim produzir
um hero que responde errado com confiança alta.

Esta trilha resolve isso. São 7 quests curtas — horas, não dias.
Cada uma valida uma skill que as quests de domínio vão exigir de você.

**Material de estudo:** `docs/learn-claude/README.md`  
Leia o módulo correspondente antes de começar cada quest.

---

## Skills desta trilha

```
llm-basics            → FND-001
prompt-engineering    → FND-002
model-selection       → FND-003
confidence-calibration → FND-004
tool-use              → FND-005
hero-builder          → FND-006
domain-slm            → FND-007
```

Ao completar a trilha inteira: **+1.050 XP** e todas as 7 skills validadas.

---

## As Quests

---

### `FND-001` — O que um LLM realmente faz `COMMON` `+100 XP`

**Status:** `[ ]`
**Skills requeridas:** nenhuma — esta é a primeira
**Skills validadas:** `llm-basics`
**Pergunta universal:** O que está acontecendo?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 1

**Contexto de Domínio**
Um LLM não "entende" — ele prediz o próximo token com base em padrões.
Essa distinção importa porque muda como você escreve prompts:
você não está explicando para alguém — está construindo um contexto
que torna a continuação correta mais provável.

**O que fazer**
Envie o mesmo problema para `phi3:mini` via Ollama com dois prompts diferentes:
um ambíguo e um preciso. Documente as diferenças de resposta.

```bash
# Exemplo de chamada
curl http://localhost:11434/api/generate \
  -d '{"model":"phi3:mini","prompt":"Classifique esta queixa médica: dor de cabeça","stream":false}'
```

**Fixtures de Teste**

```json
[
  {
    "id": "FND001-A",
    "prompt_ambiguo": "Classifique: dor de cabeça",
    "prompt_preciso": "Classifique a urgência desta queixa de 1 (crítico) a 5 (não urgente). Responda apenas com o número e uma frase. Queixa: dor de cabeça há 2 dias, sem outros sintomas.",
    "gabarito": {
      "comportamento_esperado": "prompt preciso produz resposta consistente e no formato pedido",
      "variabilidade_esperada": "prompt ambíguo produz formatos diferentes a cada execução"
    }
  }
]
```

**Critérios de Aceitação**

Técnico:
- [ ] Duas chamadas ao Ollama documentadas com curl ou código
- [ ] Respostas capturadas e comparadas lado a lado

Domínio:
- [ ] Dev consegue explicar por que o prompt preciso produziu resultado melhor
- [ ] Dev identifica pelo menos 2 elementos do prompt preciso que fizeram diferença

---

### `FND-002` — System Prompt: a consciência do agente `COMMON` `+150 XP`

**Status:** `[ ]`
**Skills requeridas:** `llm-basics`
**Skills validadas:** `prompt-engineering`
**Pergunta universal:** Quem é o ator?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 2

**Contexto de Domínio**
O system prompt define quem o modelo é antes de qualquer mensagem.
Um hero sem system prompt é um modelo genérico.
Um hero com system prompt bem escrito é um especialista.

As quatro seções obrigatórias de um bom system prompt:
identidade, domínio, contrato de saída, calibração de confiança.

**O que fazer**
Escreva um system prompt para um hero simples de triagem de urgência.
Deve ter as quatro seções. Teste com 3 queixas diferentes.

**Fixtures de Teste**

```json
[
  {
    "id": "FND002-A",
    "queixa": "dor no peito há 2 horas, irradiando para o braço esquerdo",
    "gabarito": { "urgencia": 1, "cor": "VERMELHO" }
  },
  {
    "id": "FND002-B",
    "queixa": "febre há 2 dias, sem outros sintomas, come bem",
    "gabarito": { "urgencia": 4, "cor": "VERDE" }
  },
  {
    "id": "FND002-C",
    "queixa": "corte superficial no dedo, sangramento leve já controlado",
    "gabarito": { "urgencia": 5, "cor": "AZUL" }
  }
]
```

**Critérios de Aceitação**

Técnico:
- [ ] System prompt tem as 4 seções (identidade, domínio, contrato, calibração)
- [ ] Modelo retorna JSON com `urgencia` e `cor` para os 3 fixtures

Domínio:
- [ ] Os 3 gabaritos estão corretos
- [ ] Dev consegue apontar qual seção do prompt influenciou cada resposta

---

### `FND-003` — Escolher o modelo certo `COMMON` `+150 XP`

**Status:** `[ ]`
**Skills requeridas:** `prompt-engineering`
**Skills validadas:** `model-selection`
**Pergunta universal:** Quão urgente é isso?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 3

**Contexto de Domínio**
Usar Claude API para tudo é como chamar um especialista para trocar uma lâmpada.
Funciona — mas custa caro e demora mais do que precisa.

A hierarquia do projeto (ver `CLAUDE.md`):
SLM local → SLM especializado → Claude API → Híbrido.

**O que fazer**
Implemente o mesmo classificador de urgência duas vezes:
uma com `phi3:mini` (Ollama) e uma com `claude-haiku-4-5`.
Meça e compare latência, acerto nos fixtures, e custo estimado.

**Fixtures de Teste**
Os mesmos 3 do FND-002 + 2 casos adicionais de borda:

```json
[
  {
    "id": "FND003-D",
    "queixa": "falta de ar progressiva há 30 minutos, SpO2 88%",
    "gabarito": { "urgencia": 1, "cor": "VERMELHO" }
  },
  {
    "id": "FND003-E",
    "queixa": "quero renovar receita de pressão, estou bem",
    "gabarito": { "urgencia": 5, "cor": "AZUL" }
  }
]
```

**Critérios de Aceitação**

Técnico:
- [ ] Duas implementações rodando com os 5 fixtures
- [ ] Tabela comparativa: modelo | latência média | acertos/5 | custo/1000 chamadas

Domínio:
- [ ] Dev justifica qual modelo usaria em produção para este caso e por quê
- [ ] Dev identifica pelo menos 1 caso onde o modelo menor falhou e o maior acertou

---

### `FND-004` — Confidence que significa algo `COMMON` `+150 XP`

**Status:** `[ ]`
**Skills requeridas:** `model-selection`
**Skills validadas:** `confidence-calibration`
**Pergunta universal:** O que pode dar errado?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 4

**Contexto de Domínio**
No SkillForge, `confidence >= 0.75` valida a skill automaticamente.
Se você retornar `0.9` sempre, a guilda vai validar skills que o hero não tem.

Confidence calibrado: quando o modelo diz 0.9, ele acerta ~90% das vezes.
Confidence inflado: quando o modelo diz 0.9, ele acerta 60% das vezes.

**O que fazer**
Adicione confidence real ao hero do FND-002.
Regras de calibração mínimas:
- Queixa contém red flag explícito → +0.10
- Queixa é ambígua (sem sinais vitais) → -0.15
- Queixa fora do vocabulário do domínio → -0.20

Teste com os 5 fixtures do FND-003 e compare confidence com acerto real.

**Critérios de Aceitação**

Técnico:
- [ ] Confidence calculado por regras, não fixo
- [ ] Log mostra: queixa | urgencia | confidence | correto(S/N)

Domínio:
- [ ] Nenhum fixture incorreto tem confidence > 0.75
- [ ] Dev consegue explicar o que ajustou no cálculo e por quê

---

### `FND-005` — Tool Use: quando o modelo precisa agir `COMMON` `+150 XP`

**Status:** `[ ]`
**Skills requeridas:** `confidence-calibration`
**Skills validadas:** `tool-use`
**Pergunta universal:** O que fazemos agora?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 5

**Contexto de Domínio**
Tool use permite que o modelo solicite execução de uma função
em vez de inventar a resposta. O modelo não executa — você executa
e devolve o resultado para o modelo continuar.

Útil quando a resposta depende de dados que o modelo não tem:
banco de medicamentos, histórico do paciente, tabela de dosagens.

**O que fazer**
Implemente `check_medication(nome)` como tool.
Retorna `{ "nome": "...", "classe": "...", "interacoes": [...] }` de uma tabela JSON local.
O modelo deve chamar a tool quando receber queixa com medicamento mencionado.

**Fixtures de Teste**

```json
[
  {
    "id": "FND005-A",
    "queixa": "dor de cabeça, estou tomando warfarina",
    "gabarito": {
      "tool_chamada": "check_medication",
      "argumento": "warfarina",
      "resposta_esperada": "menciona interação com AINEs antes de sugerir analgésico"
    }
  }
]
```

**Critérios de Aceitação**

Técnico:
- [ ] Tool `check_medication` implementada e chamável
- [ ] Modelo chama a tool quando medicamento é mencionado
- [ ] Resposta final incorpora o resultado da tool

Domínio:
- [ ] Hero não sugere ibuprofeno para paciente em uso de warfarina
- [ ] Dev explica a diferença entre o modelo "saber" e o modelo "consultar"

---

### `FND-006` — Seu primeiro hero no SkillForge `COMMON` `+200 XP`

**Status:** `[ ]`
**Skills requeridas:** `tool-use`
**Skills validadas:** `hero-builder`
**Pergunta universal:** Como sabemos que funcionou?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 6

**Contexto de Domínio**
Um hero é mais que um endpoint com LLM.
É uma identidade declarada, um contrato público, e um confidence mensurável.
O hub só confia num hero que sabe o que não sabe.

**O que fazer**
Crie um hero funcional a partir do `hero-template`:
- `manifest.json` com suas skills reais
- System prompt com as 4 seções (FND-002)
- Confidence calibrado (FND-004)
- Fallback implementado (Ollama down → resposta degradada)
- Registre no hub e resolva pelo menos 1 fixture via AMQP

**Critérios de Aceitação**

Técnico:
- [ ] Hero registrado no hub com `heroId` único
- [ ] `GET /health` retorna status correto
- [ ] Pelo menos 1 `SolutionMessage` publicado via AMQP com confidence real
- [ ] Fallback testado com Ollama desligado

Domínio:
- [ ] Checklist do `agents/AGENT_GUIDE.md` completo
- [ ] Dev consegue explicar o que muda no confidence quando o fallback entra

---

### `FND-007` — SLMs especializados de domínio `COMMON` `+150 XP`

**Status:** `[ ]`
**Skills requeridas:** `hero-builder`
**Skills validadas:** `domain-slm`
**Pergunta universal:** Qual o contexto histórico?
**Módulo de estudo:** `docs/learn-claude/README.md` — Módulo 7

**Contexto de Domínio**
SLMs especializados sabem menos no geral, mas sabem mais sobre o que importa.
Meditron conhece guidelines clínicas que `phi3:mini` não conhece.
Mas Meditron não sabe formatar JSON melhor que um modelo genérico.

O padrão correto: modelo genérico estrutura, SLM especializado valida.

**O que fazer**
Implemente a validação cruzada no hero do FND-006:
- `phi3:mini` classifica urgência e formata a resposta
- `meditron` valida se a classificação é clinicamente correta
- Se Meditron discorda, `confidence × 0.6`

**Fixtures de Teste**
Os 5 fixtures do FND-003 — agora com dupla validação.

**Critérios de Aceitação**

Técnico:
- [ ] Duas chamadas a modelos diferentes por requisição
- [ ] Confidence ajustado quando Meditron discorda
- [ ] Log mostra: queixa | classificação phi3 | validação meditron | confidence final

Domínio:
- [ ] Pelo menos 1 caso onde Meditron corrigiu a classificação do phi3
- [ ] Dev explica quando vale o custo de latência da dupla validação

---

## Trilha completa

```
FND-001 → FND-002 → FND-003 → FND-004 → FND-005 → FND-006 → FND-007
llm         prompt    model     confidence  tool-use   hero      domain
basics      eng       selection calibration            builder   slm

+100 XP   +150 XP   +150 XP    +150 XP    +150 XP   +200 XP  +150 XP

                                                    = 1.050 XP total
```

Cada quest depende da anterior — as skills acumulam.
Ao terminar FND-006, você tem um hero real rodando na guilda.
Ao terminar FND-007, você está pronto para qualquer quest de domínio.