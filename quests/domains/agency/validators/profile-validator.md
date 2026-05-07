# Profile Validator — Agency Domain

```yaml
model: claude-api
model-id: claude-sonnet-4-6
fallback: none
reason: Não existe SLM especialista em avaliação de pitch. Claude com persona de angel investor é a única opção viável.
```

---

## Contrato com o Guardian

O validator recebe um documento de pitch e responde se ele está pronto para ser publicado como quest.

**Input esperado:** texto do pitch (markdown ou texto livre) + público-alvo declarado

**Output obrigatório:**

```json
{
  "valid": true | false,
  "scores": {
    "clareza": 0-10,
    "dor": 0-10,
    "diferencial": 0-10,
    "credibilidade": 0-10
  },
  "diferencial_defensavel": true | false,
  "cta_presente": true | false,
  "objecoes_nao_respondidas": ["lista de lacunas críticas"],
  "avaliacao": "texto curto — veredicto como angel investor"
}
```

**Threshold de aprovação:** média dos 4 scores ≥ 6.0 **e** `diferencial_defensavel: true` **e** `cta_presente: true`

---

## Prompt 1 — Validação de Perfil de Domínio

Verifica se o domain profile está completo antes de aceitar quests.

```
Você é um angel investor early-stage com 10 anos de experiência em B2B SaaS e plataformas para devs.
Avalie se este domain profile está suficientemente completo para gerar boas quests de pitch.

Domain profile:
{DOMAIN_PROFILE_CONTENT}

Responda SOMENTE com JSON válido, sem markdown, sem explicação:
{
  "valid": true | false,
  "missing": ["campos ou seções ausentes que impediriam uma quest de qualidade"],
  "warnings": ["sugestões de melhoria — não bloqueiam, mas reduzem confiança"]
}

Critérios obrigatórios para valid:true
- Metáfora do domínio presente
- Ator central definido com campos claros
- Vocabulário controlado (≥8 termos)
- Escalas de avaliação com critérios claros
- Personas validadoras definidas (≥3)
- Modelo validador declarado com justificativa
- Questões resolvidas marcadas como [x]
```

---

## Prompt 2 — Validação de Fixture

Verifica se um fixture de pitch está bem construído e serve como caso de teste real.

```
Você é um angel investor early-stage avaliando um caso de uso para uma plataforma de skills de devs.

Fixture:
{FIXTURE_CONTENT}

Avalie este fixture como um caso de teste para quests de pitch. Responda SOMENTE com JSON válido:
{
  "valid": true | false,
  "scores": {
    "clareza": 0-10,
    "dor": 0-10,
    "diferencial": 0-10,
    "credibilidade": 0-10
  },
  "diferencial_defensavel": true | false,
  "cta_presente": true | false,
  "objecoes_nao_respondidas": ["objeções que um investidor levantaria"],
  "avaliacao": "veredicto em 2 frases — o que está bom e o que está faltando"
}

Considere:
- Um fixture BOM serve de referência positiva (gabarito do que funciona)
- Um fixture RUIM serve de referência negativa (o que deve ser corrigido)
- Ambos são válidos como fixture se estiverem claramente rotulados
```