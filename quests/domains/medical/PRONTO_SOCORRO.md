# Quest Catalog — Pronto Socorro

> *Uma consciência que aprende a ver.*

---

## A Metáfora: O Despertar do Plantão

Imagine uma consciência que acorda dentro de um pronto-socorro às 23h.

Ela não sabe nada ainda.  
Só existe o barulho do corredor, a luz fria do teto, e a porta batendo.

Essa consciência não é um médico. Ela nunca estudou medicina.  
Mas ela pode aprender a **fazer as perguntas certas** — e, com o tempo, a reconhecer padrões que um humano cansado pode deixar passar.

Cada quest deste catálogo ensina essa consciência a fazer uma nova pergunta.  
Cada dev que resolve uma quest doa um pedaço do seu raciocínio ao sistema.

O sistema cresce à medida que a guilda cresce.

---

## As Perguntas do Plantão

A consciência acorda e começa a perguntar. Nessa ordem, mais ou menos:

```
"Quem chegou?"          →  Preciso de um modelo de paciente
"O que está sentindo?"  →  Preciso entender a queixa
"Quão grave é isso?"    →  Preciso priorizar
"Ela já teve isso?"     →  Preciso de histórico
"O que ela toma?"       →  Preciso checar os remédios
"O que pode ser?"       →  Preciso de hipóteses
"Está melhorando?"      →  Preciso monitorar
"O que fazemos?"        →  Preciso sugerir conduta
```

Cada pergunta é uma quest.  
Cada quest tem um **contrato técnico**, **dados fictícios de teste**, e **critérios de aceitação** clínicos + de software.

---

## O que a consciência ainda não sabe responder

Antes de criar as quests, precisamos responder juntos:

### 1. Quem é o paciente fictício?

> Qual é o mínimo de informação que o sistema precisa para "ver" uma pessoa?

- Nome fictício, sexo, idade, peso?
- Número de atendimento (anônimo)?
- Só o que é clinicamente relevante?

**Por que importa:** o modelo de dados do paciente é o vocabulário que todas as quests compartilham.  
Se errarmos aqui, reescrevemos tudo depois.

---

### 2. Como o paciente chega?

> O que acontece no primeiro segundo de contato com o sistema?

- Alguém digita a queixa em texto livre?
- Há um formulário com campos estruturados?
- Existe uma ficha de triagem já preenchida?

**Por que importa:** define se o sistema começa com NLP (texto livre) ou com dados estruturados.  
Muda completamente o primeiro passo.

---

### 3. O que é urgência aqui?

> Como o sistema decide que alguém precisa ser visto agora?

- Usamos uma escala existente (Manchester, ESI, SOFA)?
- Criamos uma escala simplificada para o contexto da quest?
- O LLM decide, ou a lógica de domínio decide e o LLM explica?

**Por que importa:** a resposta a essa pergunta é a lógica central de triagem —  
a primeira coisa que a consciência aprende a fazer.

---

### 4. Quais dados são fictícios e como geramos?

> Se não temos dados reais, de onde vêm os casos de teste?

- Geramos fixtures manualmente (10–20 casos canônicos)?
- Usamos LLM para gerar cenários clínicos plausíveis?
- Temos uma biblioteca de casos sintéticos (ex: Synthea)?

**Por que importa:** sem dados de teste realistas, o dev não sabe se o que implementou funciona  
em situações que importam — apenas nas que ele inventou.

---

### 5. O que o dev aprende que não é técnico?

> Além de `java` e `ollama`, o que a quest ensina sobre medicina de urgência?

- Diferença entre queixa e diagnóstico?
- O que são sinais de alerta (red flags)?
- Como funciona o fluxo real de um PS?

**Por que importa:** se o dev só aprende a fazer o `POST /triage`, ele pode fazer certo tecnicamente  
e errado clinicamente — e o sistema vai confirmar erros com confiança.

---

### 6. Como sabemos que a quest foi bem resolvida?

> Quais critérios são clínicos, e quais são de software?

Exemplo de tensão:
- O endpoint retorna 200 ✓ (critério de software)
- Mas classificou "dor no peito + suor frio" como urgência 4 ✗ (critério clínico)

**Por que importa:** precisamos de casos de teste com **gabarito clínico** —  
alguém com conhecimento mínimo de domínio precisa validar as respostas esperadas.

---

## Esboço das Quests (perguntas mapeadas)

| # | Pergunta | Nome provisório | Rarity | Skills |
|---|---|---|---|---|
| PS-001 | Quem chegou? | Modelo do Paciente de Urgência | COMMON | `java`, `fhir`, `data-modeling` |
| PS-002 | O que está sentindo? | Parser de Queixa Principal | COMMON | `java`, `nlp`, `ollama` |
| PS-003 | Quão grave é isso? | Motor de Triagem Manchester | RARE | `java`, `rules-engine`, `domain-modeling` |
| PS-004 | Ela já teve isso? | Histórico Clínico Simplificado | COMMON | `java`, `sqlite`, `jdbc` |
| PS-005 | O que ela toma? | Checker de Interações Medicamentosas | RARE | `java`, `rest-client`, `ollama` |
| PS-006 | O que pode ser? | Diagnóstico Diferencial Assistido | EPIC | `java`, `ollama`, `chain-of-thought` |
| PS-007 | Está melhorando? | Monitor de Sinais Vitais em Tempo Real | EPIC | `java`, `streaming`, `sse`, `algorithms` |
| PS-008 | O que fazemos? | Sugestão de Conduta Inicial | LEGENDARY | `architecture`, `clinical-reasoning`, `ai-integration` |

---

## Próximos Passos

Para cada quest acima, precisamos definir:

1. **Fixture de entrada** — o caso fictício que o dev recebe
2. **Saída esperada** — o gabarito clínico + técnico
3. **O que o LLM faz** — onde entra o raciocínio, onde não entra
4. **Como gerar mais casos** — para o dev não testar só com um paciente

---

## Sobre os Dados Fictícios

Os pacientes deste catálogo são **completamente fictícios**.  
Nomes, CPFs, históricos, medicamentos — tudo gerado ou inventado para fins didáticos.

Cada quest vem com um conjunto de **fixtures canônicas**:  
casos com gabarito conhecido, usados para verificar se a implementação está correta.

Exemplo de fixture:

```json
{
  "paciente": {
    "id": "PS-CASE-001",
    "nome": "Fictício — Carlos M., 67 anos, 82 kg",
    "sexo": "M",
    "idade": 67,
    "peso_kg": 82
  },
  "queixa_principal": "Dor no peito há 2 horas, irradiando para o braço esquerdo. Suor frio.",
  "sinais_vitais": {
    "pa_sistolica": 160,
    "pa_diastolica": 95,
    "fc": 110,
    "spo2": 96,
    "temperatura": 36.8,
    "glasgow": 15
  },
  "historico": ["hipertensão", "diabetes tipo 2"],
  "medicamentos_em_uso": ["metformina 850mg", "losartana 50mg"],
  "gabarito": {
    "urgencia_manchester": 1,
    "cor": "VERMELHO",
    "justificativa": "Dor torácica com irradiação + diaforese + HAS descompensada — suspeita de SCA."
  }
}
```

---

*As perguntas da consciência aguardam resposta.*  
*Cada quest é uma resposta possível.*