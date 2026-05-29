# Contribuindo com o SkillForge

Obrigado por querer melhorar a guilda. Este guia cobre o fluxo para contribuições de código ao repositório principal.

---

## Antes de começar

- Leia o [`CLAUDE.md`](CLAUDE.md) — ele define as decisões técnicas inegociáveis do projeto.
- Verifique se já existe uma [issue](https://github.com/fidelisfelipe/skillforge/issues) relacionada à sua contribuição. Se não existir, abra uma antes do PR.

---

## Fluxo de contribuição

### 1. Fork e clone

```bash
# Fork via GitHub, depois:
git clone https://github.com/SEU-USUARIO/skillforge.git
cd skillforge
git remote add upstream https://github.com/fidelisfelipe/skillforge.git
```

### 2. Sincronize com o upstream antes de começar

```bash
git fetch upstream
git checkout main
git merge upstream/main
```

### 3. Crie uma branch

Use o prefixo adequado ao tipo de mudança:

| Prefixo | Quando usar |
|---|---|
| `feature/` | Nova funcionalidade ou hero |
| `fix/` | Correção de bug |
| `docs/` | Apenas documentação |
| `chore/` | Configs, dependências, build |

```bash
git checkout -b fix/nome-descritivo
```

> **Atenção:** branches com prefixo `local/` nunca devem ser enviadas ao remote — um hook `pre-push` bloqueia automaticamente. Use `local/` apenas para WIP local.

### 4. Faça as alterações

- Siga a stack canônica descrita no `CLAUDE.md`
- Rode o build antes de commitar:

```bash
mvn verify
```

### 5. Commit

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
fix: corrigir configuração rabbitmq no hero-template
feat: adicionar hero de análise de logs
docs: atualizar guia de onboarding
chore: atualizar versão do spring-boot
```

### 6. Push e Pull Request

```bash
git push origin fix/nome-descritivo
```

Abra o PR em `github.com/fidelisfelipe/skillforge` apontando para a branch `main`.

**No PR, descreva:**
- O que foi alterado e por quê
- Como testar (endpoint, comando, arquivo `.http`)
- Issue relacionada (ex: `Closes #42`)

---

## Padrões obrigatórios

### Novo hero

Todo novo hero deve incluir:

- `application.yml` com `spring.rabbitmq.addresses` (não `host`/`port`)
- `manifest.json` preenchido
- Arquivo `src/test/resources/{hero-id}.http` com health check e POST principal

### AMQP

```yaml
spring:
  rabbitmq:
    addresses: ${AMQP_URL}
    ssl:
      enabled: ${AMQP_SSL_ENABLED:false}
```

### LLM

Avalie modelo local via Ollama antes de usar API paga. Consulte a hierarquia em `CLAUDE.md`.

---

## Dúvidas

Abra uma [issue](https://github.com/fidelisfelipe/skillforge/issues) com a label `question`.
