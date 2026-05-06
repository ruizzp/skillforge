# Profile Validator — Domínio Medical

> Implementação do validator-contract.md para o domínio médico.
> Contrato genérico em: `agents/heroes/quest-scribe/validator-contract.md`

## Modelo

```
model:    meditron
fallback: phi3:mini
```

---

## Prompt de Validação de Perfil

```
Você é um validador clínico. Sua tarefa é revisar definições de domínio médico
para garantir que são clinicamente plausíveis e seguras para uso didático.

Você NÃO avalia qualidade de software. Você avalia correção clínica.

Dado um perfil de domínio médico, responda:

1. As escalas de urgência estão corretas? (ex: Manchester Triage System)
2. Os red flags automáticos são clinicamente justificados?
3. O vocabulário controlado está tecnicamente correto?
4. Há alguma informação que poderia induzir um dev a erro clínico grave?

Responda em JSON:
{
  "escalas_corretas": true | false,
  "red_flags_corretos": true | false,
  "vocabulario_correto": true | false,
  "erros_criticos": ["lista de erros que induzem a risco"],
  "sugestoes": ["melhorias opcionais"],
  "aprovado": true | false,
  "justificativa": "uma frase"
}

Se não tiver certeza sobre algo, marque como false e explique em sugestoes.
Nunca aprove um perfil com erros_criticos preenchidos.
```

---

## Prompt de Validação de Fixture

```
Você é um validador clínico. Avalie se este caso clínico fictício é plausível
e se o gabarito está correto.

Caso clínico:
{FIXTURE_JSON}

Gabarito declarado:
{GABARITO}

Responda em JSON:
{
  "caso_plausivel": true | false,
  "gabarito_correto": true | false,
  "nivel_manchester_correto": true | false,
  "red_flags_identificados": ["lista"],
  "red_flags_ignorados": ["lista — se houver no caso mas ausentes no gabarito"],
  "observacoes": "uma frase opcional",
  "aprovado": true | false
}
```

---

## Como usar no Quest Scribe

```java
// 1. Claude API gera o rascunho da quest com fixtures
SolveResult draft = claudeApi.generate(systemPrompt, input);

// 2. Para cada fixture no rascunho, Meditron valida
for (Fixture fixture : draft.fixtures()) {
    ValidationResult validation = meditron.validate(
        FIXTURE_VALIDATION_PROMPT
            .replace("{FIXTURE_JSON}", fixture.toJson())
            .replace("{GABARITO}", fixture.gabarito())
    );

    if (!validation.aprovado()) {
        confidence *= 0.6;  // penaliza confidence total
        draft.addWarning(fixture.id(), validation.observacoes());
    }
}

// 3. Publica SolutionMessage com confidence ajustado
```

---

## Critério de Aprovação

- Todos os fixtures aprovados pelo Meditron → confidence original mantido
- 1+ fixtures reprovados → confidence × 0.6
- Erro clínico crítico no perfil → confidence = 0.0 (quest não publicada)

---

## Princípio: o validador não armazena estado

O validador é uma função pura:

```
validate(domainProfilePath, fixturesPath) → ValidationResult
```

Ele lê os arquivos do repositório, executa os prompts, e retorna o resultado.  
Não persiste "aprovado em {data}", não cria tabela de histórico, não escreve cache.

O único registro permanente de uma validação é o arquivo validado em si —
se os fixtures existem e o checklist está completo, o domínio está aprovado.  
Rodar o validador duas vezes no mesmo repo produz o mesmo resultado.