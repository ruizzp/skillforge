# Hero Registration Playbook

Guia de referência para forjar seu herói e registrá-lo na guilda.

> **TL;DR:** Fork → edite `manifest.json` → push. O hub registra automaticamente.

---

## Fluxo automático (padrão)

O Guild Hub escaneia todos os forks do repo a cada 10 minutos.  
Quando encontra um fork com `manifest.json` configurado (heroId diferente do padrão), cria a issue de registro e processa a validação sem nenhuma ação manual sua.

```
Seu fork com manifest.json
         ↓
   ForkWatcher (hub)
   └─ lê manifest.json do fork
   └─ valida heroId, heroName, skills, endpoint
   └─ cria issue "hero" no repo principal  ←── banco de dados da guilda
         ↓
   RegistrationWatcher (hub)
   └─ se válido:   comenta boas-vindas + label "registered"
   └─ se inválido: lista erros + label "invalid"
```

---

## Passo a passo

### 1. Fork

Acesse `github.com/fidelisfelipe/skillforge` → **Fork**.

### 2. Edite o manifest

Arquivo: `hero-template/src/main/resources/manifest.json`

| Campo | Obrigatório | Descrição |
|---|---|---|
| `heroId` | ✅ | Identificador único. Letras minúsculas, números e hífens. Ex: `fidelisdev` |
| `heroName` | ✅ | Seu nome ou apelido na guilda |
| `heroClass` | — | `Backend`, `Frontend`, `Fullstack`, `DevOps`, `Data`, `Mobile`, etc. |
| `skills` | ✅ | Lista de skills. Pelo menos uma. Veja `SKILL_MANIFEST_GUIDE.md` |
| `endpoint` | ✅ | URL do seu nó quando estiver rodando. Ex: `http://localhost:8081` |
| `model` | — | Modelo Ollama local. Padrão: `phi3:mini` |
| `specialty` | — | Uma frase do que você resolve melhor |
| `level` | — | Começa em `1`. Atualizado pelo hub conforme você completa quests |
| `xp` | — | Começa em `0` |

Exemplo mínimo:

```json
{
  "heroId": "fidelisdev",
  "heroName": "Fidelis",
  "heroClass": "Backend",
  "skills": ["java", "spring-boot", "distributed-systems"],
  "endpoint": "http://localhost:8081",
  "model": "phi3:mini",
  "specialty": "Arquitetura de plataformas de capacidade coletiva.",
  "level": 1,
  "xp": 0
}
```

### 3. Push

```bash
git add hero-template/src/main/resources/manifest.json
git commit -m "forge: configurar herói fidelisdev"
git push
```

O hub detecta em até 10 minutos e você receberá um comentário na issue criada automaticamente.

---

## Registro manual (alternativo)

Se preferir não esperar o ForkWatcher, você pode criar a issue diretamente:

1. Abra uma issue em `github.com/fidelisfelipe/skillforge/issues/new`
2. Adicione o label `hero`
3. Cole o JSON do manifest como corpo da issue
4. O `RegistrationWatcher` processa em até 2 minutos

---

## Corrigindo um registro inválido

Se o hub postar um comentário com erros:

1. Corrija o `manifest.json` no seu fork
2. Faça push
3. O ForkWatcher tentará novamente no próximo ciclo (10 min)

Ou edite diretamente o corpo da issue que foi criada — o `RegistrationWatcher` reprocessa automaticamente.

---

## Rodando o nó localmente

Opcional para registro, obrigatório para participar de quests com endpoint ativo.

```bash
cd hero-template
mvn spring-boot:run
```

| Endpoint | O que retorna |
|---|---|
| `http://localhost:8081` | Dashboard do herói |
| `http://localhost:8081/api/health` | Status do nó |
| `http://localhost:8081/api/manifest` | Seu manifest ativo |
| `http://localhost:8081/api/solve` | Recebe problemas do hub (implemente com Ollama) |

---

## Problemas comuns

**Fork detectado mas heroId ainda é `your-hero-id`**  
O ForkWatcher ignora forks com o heroId padrão. Edite e faça push.

**Issue criada mas sem label `registered` após 5 minutos**  
O hub pode estar sem `GITHUB_TOKEN`. Verifique se o hub está rodando em modo completo.

**Quero aparecer no leaderboard mas não quero rodar o nó agora**  
Só o push do manifest já é suficiente para aparecer no registro e no leaderboard.

---

## Após o registro

Abra [`QUEST_BOARD.md`](QUEST_BOARD.md) e escolha sua primeira quest.