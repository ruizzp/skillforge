# Validator Contract — Quest Scribe

> Define o contrato que todo validador de domínio deve implementar.
> O Quest Scribe lê este contrato e executa — não sabe nada do domínio em si.

---

## Princípio

O Quest Scribe é domain-agnostic.  
Ele sabe **como** validar — cada domínio define **o quê** validar.

```
Quest Scribe                        Domínio
─────────────────────────────       ────────────────────────────────
lee  quests/domains/{d}/            fornece  validators/
executa o contrato                  implementa  profile-validator.md
                                                fixture-validator.md
```

---

## Localização dos Validadores

Cada domínio define seus validadores em:

```
quests/domains/{domain}/validators/
├── profile-validator.md    ← valida se o DOMAIN_PROFILE.md está correto
└── fixture-validator.md    ← valida se um fixture individual é plausível
```

O Quest Scribe descobre o validador lendo o repo — não há configuração central.

---

## Contrato: `profile-validator.md`

Todo `profile-validator.md` deve responder a estas duas perguntas como prompts executáveis:

### Prompt 1 — Validação do Perfil de Domínio

**Input:** conteúdo do `DOMAIN_PROFILE.md`  
**Output obrigatório em JSON:**

```json
{
  "escalas_corretas":      true,
  "vocabulario_correto":   true,
  "erros_criticos":        [],
  "sugestoes":             [],
  "aprovado":              true,
  "justificativa":         "string"
}
```

**Regra:** `aprovado: false` se `erros_criticos` não estiver vazio.

---

### Prompt 2 — Validação de Fixture Individual

**Input:** fixture JSON + gabarito declarado  
**Output obrigatório em JSON:**

```json
{
  "caso_plausivel":        true,
  "gabarito_correto":      true,
  "erros_criticos":        [],
  "observacoes":           "string",
  "aprovado":              true
}
```

---

## Contrato: qual modelo usar

O `profile-validator.md` deve declarar explicitamente:

```markdown
## Modelo
model: {nome-do-modelo-ollama ou "claude-api"}
fallback: {modelo alternativo se o principal não estiver disponível}
```

O Quest Scribe usa o modelo declarado — não decide por conta própria.

**Exemplos por domínio:**

| Domínio | Modelo validador | Razão |
|---|---|---|
| medical | `meditron` | Treinado em literatura clínica |
| financial | `phi3:mini` | Domínio genérico, regras no prompt |
| logistics | `phi3:mini` | Idem |
| legal | `claude-api` | Requer raciocínio sobre normas complexas |

---

## Como o Quest Scribe usa os validadores

```
1. Recebe problema: { domain: "medical", ... }
        ↓
2. Lê quests/domains/medical/validators/profile-validator.md
   Extrai: modelo, prompt de perfil, prompt de fixture
        ↓
3. Executa prompt de perfil → { aprovado: true/false }
   Se false → confidence = 0.0, retorna lista de erros
        ↓
4. Para cada fixture gerado, executa prompt de fixture
   Se algum reprovado → confidence × 0.6
        ↓
5. Publica SolutionMessage com confidence ajustado
```

---

## Adicionando um novo domínio

Checklist para o validador:

```
[ ] Criar quests/domains/{domain}/validators/profile-validator.md
[ ] Declarar modelo validador (e fallback)
[ ] Implementar Prompt 1 com output JSON no contrato acima
[ ] Implementar Prompt 2 com output JSON no contrato acima
[ ] Testar manualmente os dois prompts com um fixture real antes de usar
```

Sem `validators/profile-validator.md`, o Quest Scribe retorna `confidence: 0.0`
e bloqueia a geração de quests para o domínio.