# Guild Onboarding

> Você recebeu um convite. A guilda existe. O resto você vai descobrindo.

---

## O que você precisa saber agora

A **SkillForge Guild** é uma equipa de developers onde cada pessoa expõe as suas habilidades como um nó vivo dentro de um sistema distribuído.

Você não é apenas um contributor. Você é um **herói** com especialidade própria.

Quando você entra, o sistema recalcula o que a guilda consegue resolver.  
Quando você evolui, novas possibilidades se desbloqueiam para todos.

É isso. Siga o caminho abaixo.

---

## Primeira Quest: Forjar seu Herói

**Objetivo:** Registrar-se na guilda com um manifesto válido.  
**Recompensa:** 50 XP + status `ACTIVE` na guilda.  
**Desbloqueio:** Notificação automática para todos os membros da guilda.

### Passos

**1. Fork o repositório no GitHub**

Acesse `github.com/fidelisfelipe/skillforge` e clique em **Fork**.  
Você terá `github.com/SEU-USUARIO/skillforge`.

**2. Configure seu manifesto**

Edite `hero-template/src/main/resources/manifest.json`:

```json
{
  "heroId": "seu-hero-id",
  "heroName": "Seu Nome",
  "heroClass": "Backend",
  "skills": ["java", "spring-boot"],
  "endpoint": "http://localhost:8081",
  "model": "phi3:mini",
  "specialty": "Descreva em uma frase o que você resolve melhor.",
  "level": 1,
  "xp": 0
}
```

O `heroId` deve ser único: letras minúsculas, números e hífens (ex: `fidelisdev`).  
Consulte [`SKILL_MANIFEST_GUIDE.md`](SKILL_MANIFEST_GUIDE.md) para a lista completa de skills reconhecidas.

**3. Faça push do manifesto**

```bash
git add hero-template/src/main/resources/manifest.json
git commit -m "forge: configurar herói seu-hero-id"
git push
```

Pronto. O Guild Hub detecta o fork automaticamente e cuida do registro.

---

## O que acontece depois do push

```
ForkWatcher (hub)     → detecta seu fork, lê o manifest.json
RegistrationWatcher   → valida os campos, posta comentário na issue
                      → se válido:   label "registered" + boas-vindas
                      → se inválido: lista erros para você corrigir
HeroRegistryService   → atualiza leaderboard e mapa de skills
SSE                   → notifica todos os heróis ativos em tempo real
```

O hub verifica forks a cada 10 minutos e valida pendências a cada 2 minutos.  
**Você não precisa criar nenhuma issue manualmente.**

---

## (Opcional) Rodar seu nó herói localmente

Necessário apenas para participar de quests com endpoint ativo — quando o hub distribui problemas para os heróis resolverem em paralelo.

```bash
# Clone o seu fork
git clone https://github.com/SEU-USUARIO/skillforge.git
cd skillforge/hero-template

# Suba o nó
mvn spring-boot:run
```

Dashboard: `http://localhost:8081`  
Manifest ativo: `http://localhost:8081/api/manifest`  
Health check: `http://localhost:8081/api/health`

---

## O que você verá no início

| O que está visível agora | O que desbloqueia depois |
|--------------------------|--------------------------|
| Quests COMMON disponíveis | Quests RARE (Journeyman+) |
| Seu perfil de herói | Leaderboard da guilda |
| Skills declaradas | Skills validadas por peers |
| Quest Board básico | Skill Gap Dashboard |
| Sua posição: Apprentice | Trilha de progressão completa |

> Mais informação é liberada conforme você contribui, completa quests e sobe de nível.

---

## Notificações automáticas da guilda

Enquanto estiver conectado ao hub, você receberá via SSE:

```
# Novo herói entrou
⚡ DataWarden entrou. Skills: postgresql, data-modeling, jdbc
   3 quests desbloqueadas: Schema Migration [RARE +280 XP] ...

# Quest completada
✓ QUEST-007 completada por RxMage. Guild XP +400.

# Herói subiu de nível
▲ CodeBlade atingiu Expert. Votos em conflitos desbloqueados.
```

---

## Seu próximo passo

Quando o registro for confirmado, abra [`QUEST_BOARD.md`](QUEST_BOARD.md) e escolha sua primeira quest.

A qualidade do seu manifesto determina quais quests você pode participar.

---

*Mais detalhes técnicos sobre o hub em [`guild-hub/README.md`](guild-hub/README.md).*  
*Arquitetura completa em `PROJECT_CONTEXT.md` — desbloqueado no nível Journeyman.*