# Learn Claude — Guia de Estudo para Devs do SkillForge

> Como construir agents e heroes que funcionam de verdade.
> Estudo progressivo — cada módulo tem teoria + exercício prático no SkillForge.

---

## Para quem é este guia

Devs que querem construir heroes no SkillForge e entender o que estão fazendo —
não só copiar código que funciona por acidente.

Você não precisa de experiência com IA. Precisa saber Java e querer entender.

---

## Trilha de Estudo

```
Módulo 1 — O que é um LLM (e o que não é)
Módulo 2 — System Prompts: a consciência do agente
Módulo 3 — Seleção de modelo: SLM vs API, quando usar cada um
Módulo 4 — Confidence e calibração: como o hero sabe que está certo
Módulo 5 — Tool Use: quando o modelo precisa agir, não só falar
Módulo 6 — Heroes no SkillForge: da teoria ao AMQP
Módulo 7 — Domínio especializado: SLMs médicos, financeiros e outros
```

Cada módulo tem:
- **Conceito** — o que é e por que importa (5 min de leitura)
- **Armadilha comum** — o erro que todo mundo comete na primeira vez
- **Exercício** — uma quest do SkillForge que pratica exatamente isso

---

## Módulo 1 — O que é um LLM (e o que não é)

### Conceito

Um LLM é um modelo que, dado um texto de entrada, prediz qual texto tem maior probabilidade de vir a seguir.

É só isso.

Não há "compreensão". Não há "raciocínio" no sentido humano. Há padrões aprendidos de bilhões de textos que, quando bem conduzidos, produzem resultados que *parecem* raciocínio.

O que importa para você como dev:

```
Entrada (prompt)  →  [LLM]  →  Saída (completion)
```

Sua responsabilidade é construir o prompt. A qualidade da saída depende quase inteiramente de como você estrutura a entrada.

### Armadilha comum

> "O modelo vai entender o que eu quis dizer."

Não vai. O modelo completa padrões. Se o seu prompt é ambíguo, a saída é imprevisível — mas consistentemente imprevisível da mesma forma.

**Exemplo:**

```
Prompt ruim:   "Classifique esta queixa médica."
Prompt bom:    "Classifique a urgência desta queixa médica usando os níveis 1 (crítico)
                a 5 (não urgente). Responda apenas com o número e uma frase de justificativa."
```

### Exercício

Quest `PS-002` — Parser de Queixa Principal.
Escreva dois prompts para o mesmo caso clínico e compare os resultados.
Documente o que mudou e por quê.

---

## Módulo 2 — System Prompts: a consciência do agente

### Conceito

O system prompt é o texto que aparece antes de qualquer mensagem do usuário.
É onde você define *quem* o modelo é nesta conversa.

```
[system]   Você é um triagista de pronto-socorro...
[user]     Paciente chega com dor no peito há 2h.
[model]    → responde como triagista, não como assistente genérico
```

Um bom system prompt tem quatro partes:

| Parte | O que define |
|---|---|
| Identidade | Quem o modelo é e o que sabe |
| Domínio | As regras e restrições do contexto |
| Contrato de saída | Formato exato da resposta esperada |
| Calibração | Quando ter certeza, quando expressar dúvida |

### Armadilha comum

> System prompt longo é system prompt bom.

Falso. Prompts longos e vagos produzem resultados vagos.
Um prompt de 10 linhas focado supera um de 100 linhas difuso.

Teste: se você removesse metade do prompt, o comportamento mudaria?
Se não mudaria, a metade removida era ruído.

### Exercício

Abra `hero-template/src/main/resources/` e leia o system prompt do hero base.
Reescreva-o aplicando as quatro partes. Compare as respostas para o mesmo problema.

---

## Módulo 3 — Seleção de Modelo

### Conceito

Não existe modelo certo para tudo. Existe modelo certo para cada tarefa.

```
Custo × Qualidade × Latência × Privacidade
```

Esses quatro fatores determinam a escolha. No SkillForge, a hierarquia está em `CLAUDE.md`:

```
1. SLM local (Ollama)          — custo zero, privacidade total, qualidade variável
2. SLM especializado (Ollama)  — meditron, codellama, biom istral
3. API paga (Claude)           — raciocínio estruturado, formato estrito
4. Híbrido                     — Claude escreve, SLM valida domínio
```

### Quando SLM local basta

- Classificação simples (urgente / não urgente)
- Extração de campos de texto estruturado
- Geração de variações de texto

### Quando Claude API é necessário

- JSON com schema estrito que o modelo precisa respeitar
- Raciocínio em múltiplos passos (chain-of-thought)
- Geração de documentação técnica com consistência

### Armadilha comum

> "Vou usar Claude para tudo — é mais simples."

É mais caro, mais lento, e cria dependência de API externa.
Um SLM de 7B rodando local resolve 80% dos casos. Use a API para os 20% restantes.

### Exercício

Implemente o mesmo classifier de urgência duas vezes:
uma com `phi3:mini` (Ollama) e uma com `claude-haiku-4-5`.
Meça latência, qualidade nos fixtures canônicos, e custo estimado por 1000 chamadas.

---

## Módulo 4 — Confidence e Calibração

### Conceito

Confidence não é "o modelo parece confiante". É sua estimativa de que a resposta está correta.

Um modelo mal calibrado tem confidence alto em respostas erradas.
Um modelo bem calibrado diz "não sei" quando não sabe.

No SkillForge, confidence determina se uma skill é auto-validada:

```
confidence >= 0.75  →  skill validada automaticamente
confidence < 0.75   →  solução registrada, validação manual
confidence = 0.0    →  falha — não registra nada
```

### Como calibrar na prática

1. Rode o modelo em 20 fixtures com gabarito conhecido
2. Meça em quantas ele acertou quando disse confidence ≥ 0.75
3. Se acertou < 70% das vezes, o modelo está superestimando — ajuste para baixo
4. Se acertou > 95% das vezes, pode subir um pouco o threshold

### Armadilha comum

> Retornar sempre `confidence: 0.9` para passar no threshold.

Isso destrói o valor do sistema. A guilda vai validar skills que o hero não tem.
E quando o sistema falhar em produção, ninguém vai entender por quê.

### Exercício

Implemente `SolveService` com confidence real:
baixe quando a queixa contém termos fora do vocabulário do domínio,
suba quando os sinais vitais são textbook para o diagnóstico sugerido.

---

## Módulo 5 — Tool Use

### Conceito

Tool use (ou function calling) permite que o modelo *solicite* a execução de uma função
em vez de apenas gerar texto.

```
[user]     Qual a interação entre metformina e contraste iodado?
[model]    → tool_call: check_interaction("metformina", "contraste iodado")
[tool]     → { "severity": "HIGH", "description": "..." }
[model]    → responde com base no resultado real
```

O modelo não executa a função — ele diz que quer executá-la. Você executa e devolve o resultado.

### Quando usar

- Quando a resposta depende de dados externos (banco, API, arquivo)
- Quando o modelo precisa fazer cálculos precisos
- Quando precisa tomar ações (gravar arquivo, enviar mensagem)

### Quando não usar

- Quando o modelo pode responder corretamente com o que já sabe
- Quando o fluxo de tool + resposta é mais lento que uma resposta direta
- Quando a ferramenta seria chamada em 99% dos casos (nesse caso, coloque no system prompt)

### Exercício

No Quest Scribe, implemente `write_quest_draft` como tool.
O modelo chama a tool quando tem conteúdo suficiente para gravar.
Se não tiver, continua fazendo perguntas.

---

## Módulo 6 — Heroes no SkillForge

### Conceito

Leia `agents/AGENT_GUIDE.md` completo antes deste módulo.

Um hero é um processo que:
1. Escuta via AMQP (tópico `skillforge`, routing key `problem`)
2. Processa com um modelo (Ollama ou API)
3. Publica a solução de volta (routing key `solution`)

O hub não sabe como o hero processa — só vê o `SolutionMessage` com `confidence`.

### Fluxo completo

```
Hub publica ProblemMessage
        ↓
Hero consome (ProblemConsumer)
        ↓
Hero chama SolveService
        ↓
SolveService monta prompt + chama modelo
        ↓
SolveService retorna SolveResult(solution, confidence, model)
        ↓
Hero publica SolutionMessage
        ↓
Hub consome, valida confidence, atualiza XP
```

### Exercício

Crie um hero novo a partir do `hero-template`.
Mude o `manifest.json`, escreva um novo system prompt,
e resolva 3 fixtures do domínio médico.
Verifique no hub que as skills foram validadas.

---

## Módulo 7 — Domínio Especializado

### Conceito

SLMs especializados são modelos fine-tuned em corpus de um domínio específico.
Eles sabem menos no geral, mas sabem mais sobre o que importa para você.

| Modelo | Domínio | Via Ollama |
|---|---|---|
| `meditron` | Medicina clínica (EPFL + OMS) | `ollama pull meditron` |
| `codellama` | Código e programação | `ollama pull codellama` |
| `biom istral` | Biomédico (PubMed) | HuggingFace GGUF |
| `phi3:mini` | Genérico pequeno e rápido | `ollama pull phi3:mini` |

### Padrão de validação cruzada

```java
String structure = claudeApi.generate(structurePrompt, input);   // formato correto
boolean valid    = meditron.validate(validationPrompt, structure); // conteúdo correto

if (!valid) {
    confidence *= 0.6;  // penaliza confidence se domínio não validou
}
```

### Exercício

Implemente a validação cruzada no Quest Scribe:
Claude gera o rascunho da quest, Meditron valida se os fixtures clínicos fazem sentido.
Se Meditron retornar "implausível", confidence cai para 0.5.

---

## Próximos Passos

Após completar os 7 módulos:

1. Crie um hero original com domínio próprio
2. Escreva o `DOMAIN_PROFILE.md` do seu domínio
3. Submeta 3 quests para o catalog
4. Revise um hero de outro dev usando o `AGENT_GUIDE.md` como critério

> A melhor forma de aprender a construir agents é construir um que resolva um problema que você conhece melhor que o modelo.