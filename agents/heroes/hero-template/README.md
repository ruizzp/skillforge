# Hero Template — Guia de Criação

Este diretório contém os arquivos de definição do hero base.
O código-fonte do módulo Spring Boot está em `hero-template/` na raiz do repositório.

---

## Criando um novo hero

### 1. Copie o módulo

```bash
cp -r hero-template meu-hero
```

Renomeie os pacotes de `com.skillforge.hero` para `com.skillforge.{seu-hero-id}` e atualize o `pom.xml`.

### 2. Configure a identidade

Edite `src/main/resources/application.yml`:

```yaml
server:
  port: 808X  # escolha uma porta livre

skillforge:
  hero:
    id: meu-hero-id          # kebab-case, único na guilda
    name: Nome do Hero
    class: Backend           # Backend | Frontend | Data | Reviewer | etc.
    skills: skill-1,skill-2  # CSV — devem bater com as routing keys que você consome
    specialty: O que este hero faz em uma frase
    endpoint: http://localhost:808X

ollama:
  model: ${OLLAMA_MODEL:phi3:mini}
```

### 3. Escreva o system prompt

Crie `src/main/resources/prompts/system-prompt.txt` baseando-se no template em `system-prompt.md` deste diretório.

Defina:
- **Papel** — quem é o hero dentro da guilda
- **Formato de saída** — JSON com os campos que o hub espera
- **Regras** — o que fazer e o que não fazer
- **Campo `confidence`** — entre `0.0` e `1.0`, seguindo a tabela de calibração

### 4. Implemente `SolveService`

`SolveService.solve(ProblemMessage)` é o único método que você precisa implementar. O template já inclui:
- Recebimento de problema via AMQP (`ProblemConsumer`)
- Heartbeat periódico (`HeartbeatPublisher`)
- Auto-registro no hub ao subir (`SelfRegistrationPublisher`)
- Dashboard HTTP local

### 5. Atualize os manifests

**`src/main/resources/manifest.json`** — identidade do hero (usada no auto-registro):
```json
{
  "heroId": "meu-hero-id",
  "heroName": "Nome do Hero",
  "heroClass": "Backend",
  "skills": ["skill-1", "skill-2"],
  "endpoint": "http://localhost:808X",
  "model": "phi3:mini",
  "specialty": "O que este hero faz"
}
```

**`agents/heroes/{hero-id}/manifest.json`** — definição canônica para o repositório (copie e edite o `manifest.json` deste diretório).

---

## Convenções obrigatórias

| Item | Padrão |
|---|---|
| Porta | Única, diferente de 8080 (hub) e outras já em uso |
| AMQP routing key consumida | `problem.{skill}` |
| AMQP routing key publicada | `solution.{skill}` |
| `TypePrecedence` no `AmqpConfig` | `INFERRED` — obrigatório |
| Concorrência | Virtual threads — sem platform thread pool |
| LLM | Ollama local primeiro; API paga só se justificado |

Veja `agents/AGENT_GUIDE.md` para o padrão completo de `AmqpConfig`.

---

## Estrutura esperada após criação

```
meu-hero/
├── src/main/java/com/skillforge/{hero-id}/
│   ├── amqp/
│   │   ├── AmqpConfig.java            ← copiar do template, não modificar estrutura
│   │   ├── ProblemConsumer.java
│   │   └── SelfRegistrationPublisher.java
│   └── service/
│       └── SolveService.java          ← sua lógica aqui
└── src/main/resources/
    ├── application.yml
    ├── manifest.json
    └── prompts/
        └── system-prompt.txt          ← seu prompt aqui

agents/heroes/{hero-id}/
├── manifest.json
├── system-prompt.md                   ← documentação do prompt
└── README.md
```