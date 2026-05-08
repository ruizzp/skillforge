# Agent Guide — O Mínimo para um Hero Eficiente

> Referência para devs que querem construir heroes no SkillForge.
> Leia antes de criar qualquer novo módulo de agente.

---

## O que é um Hero no SkillForge

Um hero é um processo com três responsabilidades:

```
1. Declarar o que sabe     →  manifest.json
2. Escutar problemas       →  AMQP consumer
3. Devolver soluções       →  AMQP publisher + confidence score
```

Tudo o mais — qual modelo usa, qual domínio conhece, como processa — é detalhe de implementação. O hub não sabe e não precisa saber.

---

## Anatomia Mínima de um Hero

### 1. Identidade — `manifest.json`

```json
{
  "heroId":    "nome-unico-no-kebab-case",
  "heroName":  "Nome Legível",
  "heroClass": "categoria do hero (Solver | Validator | Scribe | Analyst)",
  "skills":    ["skill-1", "skill-2"],
  "specialty": "Uma frase: o que este hero faz de melhor",
  "endpoint":  "http://localhost:{porta}",
  "model":     "nome-do-modelo-ollama-ou-api"
}
```

**Regras:**
- `heroId` é imutável — mudar é criar um hero novo
- `skills` são o contrato público — o hub usa para rotear problemas
- `specialty` é documentação viva — escreva para humanos, não para o sistema

---

### 2. Consciência — System Prompt

O system prompt é o que separa um hero de um `curl` para o Ollama.

**Estrutura mínima:**

```
[IDENTIDADE]
Quem você é, o que você sabe, o que não é sua responsabilidade.

[DOMÍNIO]
O contexto do problema que você resolve. Vocabulário controlado.
Regras e restrições do domínio que nunca devem ser violadas.

[CONTRATO DE SAÍDA]
Formato exato da resposta esperada.
O que fazer quando não souber (nunca inventar — declarar incerteza).

[CALIBRAÇÃO DE CONFIANÇA]
Quando retornar confidence alto vs baixo.
Casos em que recusar é melhor que responder errado.
```

**Princípio:** o system prompt deve ser lido como um briefing de onboarding.
Se um humano lesse e não soubesse o que fazer, o prompt está incompleto.

---

### 3. Contrato de Entrada e Saída

Todo hero deve documentar explicitamente:

```java
// O que o hero recebe
record ProblemMessage(
    String questId,      // identificador do problema
    String heroId,       // quem enviou (para resposta direcionada)
    String problem,      // enunciado em texto livre ou JSON
    List<String> requiredSkills
)

// O que o hero devolve
record SolutionMessage(
    String questId,
    String heroId,
    String heroName,
    String solution,     // resposta do modelo
    double confidence,   // 0.0 a 1.0 — nunca omitir
    String model         // qual modelo produziu a resposta
)
```

**`confidence` nunca é opcional.** O hub usa para decidir se auto-valida.
Threshold padrão: `0.75`. Abaixo disso, a solução chega mas não valida skill.

---

### 4. Configuração AMQP — Padrão Obrigatório

Todo hero tem **exatamente** este `AmqpConfig`. Não invente variações.

```java
@Configuration
public class AmqpConfig {

    static final String QUEUE = "{hero-id}.problems";   // ex: hero-marketing.problems
    static final String KEY   = "problem.{skill}";      // ex: problem.#  ou  problem.java

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchangeName;

    @Bean TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean Queue problemQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean Binding problemBinding(Queue problemQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(problemQueue).to(skillforgeExchange).with(KEY);
    }

    // ⚠️ CRÍTICO: injete ObjectMapper + defina TypePrecedence.INFERRED
    @Bean Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }
}
```

**Por que cada detalhe importa:**

| Detalhe | Consequência se omitido |
|---|---|
| `TypePrecedence.INFERRED` | `ClassNotFoundException` em runtime — o converter tenta instanciar `com.skillforge.hub.amqp.ProblemMessage`, que não existe no classpath do hero |
| `ObjectMapper mapper` injetado | Usa um `ObjectMapper` sem os módulos do Spring Boot registrados (Java time, Kotlin, etc.) — falha ao deserializar campos de data |
| `RabbitTemplate` com converter configurado | Mensagens publicadas pelo hero saem como bytes serializados pelo Java, não como JSON — o hub não consegue deserializar |
| `@Value("${...exchange:skillforge}")` com default | Sem default, a app explode no startup se `AMQP_EXCHANGE` não estiver definida |

**Convenção de nomes:**

| Elemento | Padrão | Exemplo |
|---|---|---|
| Queue | `{hero-id}.problems` | `hero-marketing.problems` |
| Routing key (consumer) | `problem.{skill}` ou `problem.#` | `problem.pitch-design` |
| Routing key (publisher) | `solution.{skill}` | `solution.pitch-design` |
| Exchange bean | `skillforgeExchange` | igual em todos |

**`problem.#` vs `problem.{skill}`:**  
Use `problem.#` quando o hero aceita qualquer skill (raro — heroes são especialistas).  
Use `problem.{skill-exato}` para heroes com escopo restrito. Mais de uma skill → mais de um binding.

---

### 5. Seleção de Modelo

Siga a hierarquia do `CLAUDE.md`:

```
SLM local genérico      →  phi3:mini, llama3.2:3b
SLM especializado       →  meditron (médico), codellama (código)
Claude API              →  raciocínio estruturado, formato estrito
Claude API + SLM        →  estrutura (Claude) + validação de domínio (SLM)
```

**Como decidir:**

| O hero precisa de... | Usar |
|---|---|
| Resposta rápida, domínio simples | SLM local genérico |
| Conhecimento especializado de domínio | SLM especializado |
| JSON estrito, raciocínio em cadeia | Claude API |
| Gerar conteúdo + validar correção | Claude API + SLM |

---

### 5. Calibração de Confiança

Confidence não é "o quanto o modelo parece confiante". É uma estimativa calibrada de correção.

**Guia prático:**

| Situação | Confidence sugerida |
|---|---|
| Resposta direta do modelo sem ambiguidade | 0.85–0.95 |
| Resposta com fallback (Ollama indisponível) | 0.80 |
| Resposta com múltiplas hipóteses sem consenso | 0.60–0.75 |
| Domínio fora do escopo declarado | 0.30–0.50 |
| Falha de parsing ou resposta inválida | 0.0 |

**Regra:** se não tem como estimar, use `0.70`. Nunca use `1.0` — nenhum modelo é infalível.

---

### 6. Fallback Obrigatório

Todo hero deve funcionar mesmo quando o modelo primário está indisponível.

```java
try {
    result = callOllama(problem);          // tenta modelo primário
} catch (OllamaUnavailableException e) {
    result = fallbackResponse(problem);    // resposta degradada
    confidence = 0.80;                     // acima do threshold, abaixo do ideal
}
```

O fallback não precisa ser inteligente — precisa ser honesto e funcional.

---

### 7. O que um Hero NÃO deve fazer

- **Não acumular estado entre chamadas** — heroes são stateless por design
- **Não chamar outros heroes diretamente** — vai pelo hub via AMQP
- **Não logar dados do paciente/usuário** — logs são para debug, não para auditoria de dados
- **Não omitir confidence** — `0.0` é válido; ausência não é
- **Não responder fora do escopo declarado** — melhor `confidence: 0.3` do que inventar

---

## Checklist antes de subir um Hero

```
[ ] manifest.json com todos os campos obrigatórios
[ ] heroId único (verificar no hub antes de subir)
[ ] System prompt com identidade, domínio, contrato e calibração
[ ] Fallback implementado e testado com Ollama desligado
[ ] confidence sempre retornado (nunca null, nunca omitido)
[ ] Pelo menos 3 casos de teste documentados (happy, edge, falha)
[ ] skill declarada no manifesto existe no skill taxonomy da guilda

AMQP (use o padrão da seção 4 — não adapte livremente):
[ ] AmqpConfig com TypePrecedence.INFERRED no messageConverter
[ ] ObjectMapper injetado no messageConverter (não new Jackson2JsonMessageConverter())
[ ] RabbitTemplate configurado com o messageConverter
[ ] Queue nomeada {hero-id}.problems
[ ] Routing key específica — não usar problem.# sem justificativa
[ ] @Value("${guild.amqp.exchange:skillforge}") com default
[ ] HeartbeatPublisher presente — sem heartbeat o hero não aparece no roteamento
```

---

## Onde vivem as definições dos heroes

Cada hero tem sua pasta em `agents/heroes/{hero-id}/` com quatro arquivos:

```
agents/heroes/{hero-id}/
├── README.md            ← visão geral: o que faz, como processa, status
├── manifest.json        ← identidade, skills, modelo, endpoint
├── system-prompt.md     ← consciência do agente
└── {validator}.md       ← prompts de validação de domínio (se aplicável)
```

**Heroes definidos:**

| Hero | Skills | Modelo | Status |
|---|---|---|---|
| `quest-scribe` | `quest-design`, `clinical-reasoning` | Claude API + Meditron | Definido, não implementado |

---

## Referências

- `CLAUDE.md` — princípios e hierarquia de modelos do projeto
- `hero-template/` — módulo Spring Boot base para novos heroes
- `docs/learn-claude/` — guia de estudo progressivo para devs
- `agents/heroes/` — definições concretas de cada hero
- `agents/templates/` — templates reutilizáveis de system prompt