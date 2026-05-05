# SkillForge

> Transformando as habilidades de uma equipa de developers em uma guilda capaz de descobrir, desbloquear e resolver problemas de negócio em tempo real.

O **SkillForge** é uma iniciativa para estruturar capacidade técnica coletiva como um sistema vivo, observável e utilizável além da coordenação manual entre pessoas.

A proposta parte de um problema recorrente em equipas de software:  
a equipa até possui competências valiosas distribuídas entre diferentes developers, mas raramente existe uma forma clara, dinâmica e operacional de saber **o que o grupo consegue resolver em dado momento**, quais problemas estão bloqueados por falta de skill e que novas possibilidades surgem quando uma nova especialização entra no sistema.

---

## Problema

Em muitas equipas, capacidade técnica fica distribuída entre:

- o conhecimento individual de cada developer
- especializações implícitas que não estão formalizadas
- dependência de pessoas específicas para certos tipos de problema
- resolução sequencial de questões que poderiam ser tratadas em paralelo
- pouca visibilidade sobre a composição real de skills da equipa
- dificuldade em reutilizar expertise de forma operacional

Quando essa capacidade não é explicitada nem orquestrada, o time passa a depender excessivamente de coordenação manual.  
Com o tempo, isso gera:

- baixa visibilidade sobre o potencial coletivo da equipa
- onboarding mais lento
- gargalos em especialistas
- menor paralelismo na resolução de problemas
- dificuldade em identificar lacunas de skill
- dependência de APIs cloud para inferência e orquestração inteligente

Em outras palavras: a equipa possui capacidade, mas não consegue transformá-la facilmente em um sistema coordenado de execução.

---

## Proposta

O SkillForge propõe uma arquitetura em que cada developer possa expor a sua especialidade através de um **nó herói especializado**, enquanto um hub central mantém uma visão determinística das capacidades disponíveis na guilda.

Esse sistema permite:

- registar skills declaradas por cada developer
- manter um grafo de capacidades da equipa
- identificar quais quests podem ser resolvidas com as skills atuais
- desbloquear novos problemas quando uma nova habilidade entra no sistema
- distribuir problemas em paralelo pelos nós relevantes
- sintetizar contribuições numa solução final
- acompanhar progressão, XP e contribuição por herói

O objetivo não é apenas gamificar colaboração, mas transformar especialização técnica em infraestrutura operacional.

---

## Conceito central

### Guild Capability Platform

O **SkillForge** funciona como uma plataforma de capacidade coletiva orientada a skills.

Cada developer atua como um herói com autonomia local, capaz de:

- declarar as suas skills ao entrar na guilda
- expor um endpoint próprio para resolução de problemas
- contribuir com respostas especializadas
- participar de quests compatíveis com a sua especialidade
- evoluir em reputação, XP e impacto dentro do sistema

No centro dessa dinâmica, o **Guild Hub** atua como mestre da guilda, ajudando a:

- manter um catálogo vivo de capacidades
- recalcular quests desbloqueadas
- fazer roteamento determinístico sem depender de LLM
- executar fan-out paralelo para os heróis relevantes
- emitir notificações em tempo real quando novas possibilidades surgem
- coordenar síntese e persistência de resultados

---

## Como funciona

De forma conceitual, o fluxo é o seguinte:

1. um developer executa o seu nó herói localmente
2. o herói regista-se no Guild Hub com as suas skills e manifesto
3. o hub atualiza o Capability Graph da guilda
4. quests pendentes são reavaliadas com base nas capacidades atuais
5. quando um problema é submetido, o hub seleciona os heróis relevantes
6. a execução acontece em paralelo entre os nós especializados
7. um sintetizador combina as contribuições numa resposta final
8. eventos, XP, progresso e trilha de auditoria são persistidos

---

## O momento mágico

O momento central do SkillForge acontece quando uma nova especialização entra na guilda e altera imediatamente aquilo que a equipa consegue resolver.

Via SSE, o sistema pode notificar em tempo real algo como:

> “MLOracle entrou na guilda. Nova habilidade: ml-inference.  
> 3 quests desbloqueadas: Previsão de Churn [RARE +350 XP],  
> Análise de Sentimento [EPIC +700 XP],  
> Pipeline ML [LEGENDARY +1500 XP].”

Esse é o ponto em que capacidade deixa de ser apenas atributo humano e passa a ser comportamento emergente do sistema.

---

## O que o projeto busca resolver

O SkillForge busca reduzir problemas como:

- conhecimento técnico silado
- baixa reutilização de expertise entre developers
- desconhecimento sobre a capacidade total da equipa
- dificuldade em saber quais problemas estão desbloqueados
- execução manual e sequencial de problemas que poderiam ser tratados por especialistas em paralelo
- dependência estrutural de serviços cloud para compor inteligência operacional

---

## Arquitectura

```text
Frontend (SSE) → Guild Hub :8080 → CompletableFuture.allOf()
                                  → Herói :8081 (phi3:mini)
                                  → Herói :8082 (phi3:mini)
                                  → Herói :8083 (phi3:mini)
                                  → Sintetizador :8090 (qwen2.5:7b)
```

### Stack técnica

- **Java 21** — Records, Virtual Threads, HttpClient nativo
- **Spring Boot 3.3** — apenas `spring-boot-starter-web`
- **Ollama** — modelos locais por defeito
  - `phi3:mini` por nó herói
  - `qwen2.5:7b` no sintetizador
- **SQLite** — persistência de heróis, quests, XP e audit trail
- **Maven multi-módulo**
  - `core`
  - `guild-hub`
  - `synthesizer`
  - `hero-template`
  - `api`

---

## Princípios de design

- **Sem LLM no roteamento**  
  O Capability Graph é determinístico e implementado em Java puro.

- **Contratos imutáveis desde o início**  
  A comunicação principal é modelada com Java Records.

- **Resultado parcial continua a avançar**  
  Timeouts e respostas degradadas fazem parte do desenho do sistema.

- **Local-first por defeito**  
  Os modelos correm localmente via Ollama e os dados não precisam sair da máquina.

- **Cada developer é owner do seu herói**  
  Cada nó é independente, tem a sua própria porta e autonomia operacional.

- **Crescimento orgânico da guilda**  
  O sistema só resolve aquilo que as skills disponíveis permitem resolver.

---

## Contratos base

```java
record HeroRegistration(String heroId, String heroName, String heroClass,
    List<String> skills, String endpoint, String model, String specialty)

record AgentOutput(String result, double confidence, String reason, String agentId)

record QuestUnlockedEvent(String questId, String title, QuestRarity rarity,
    String unlockedBy, int xpReward)
```

---

## Mecânicas de jogo

### Quest Board

| Raridade   | Skills necessárias | XP reward | Estado         |
|------------|-------------------|-----------|----------------|
| COMMON     | 1–2               | 50–200    | Disponível     |
| RARE       | 3–4               | 200–500   | Desbloqueável  |
| EPIC       | 5–6               | 500–1000  | Requer equipa  |
| LEGENDARY  | 6+                | 1000+     | Toda a guilda  |

### Progressão do Herói

| Nível | Nome       | XP        | Privilégios                    |
|-------|------------|-----------|--------------------------------|
| 1     | Apprentice | 0         | Quests COMMON                  |
| 3     | Journeyman | 1 000     | Quests RARE                    |
| 5     | Expert     | 3 000     | Quests EPIC, voto em conflitos |
| 8     | Master     | 8 000     | Quests LEGENDARY, mentor       |
| 10    | Archmage   | 20 000    | Define skills, aprova quests   |

### Distribuição de XP

- skills `required` → XP base / nº de heróis × 1.2
- skills `optional` → XP base / nº de heróis × 0.6
- bónus de velocidade → top 25% mais rápidos recebe +10% XP
- sintetizador → 15% fixo por quest

---

## Roadmap inicial

### 1. Agente mínimo
- [ ] criar o nó herói mínimo com 4 ficheiros Java
- [ ] integrar Ollama localmente
- [ ] expor `/solve`, `/manifest` e `/health`

### 2. Guild Hub core
- [ ] implementar registo de heróis
- [ ] construir o Capability Graph
- [ ] fazer fan-out paralelo com `CompletableFuture`
- [ ] emitir notificações SSE para quests desbloqueadas

### 3. Gamificação completa
- [ ] implementar XP e leaderboard
- [ ] criar o Training Dojo
- [ ] expor Skill Gap Dashboard
- [ ] disponibilizar dashboard web em tempo real

### 4. Evolução para produto
- [ ] permitir submissão de quests via API
- [ ] suportar recompensa por quest completada
- [ ] evoluir para marketplace de capacidades
- [ ] expandir para múltiplos domínios de negócio

---

## Visão

Construir uma plataforma em que as habilidades de uma equipa não ficam apenas distribuídas entre pessoas, mas passam a compor um sistema vivo, coordenado e capaz de revelar, em tempo real, o potencial coletivo da guilda.

Em um cenário ideal:

- a equipa sabe exactamente quais capacidades possui
- novos heróis desbloqueiam novas possibilidades de execução
- problemas de negócio podem ser resolvidos de forma mais paralela
- especialização torna-se reutilizável como infraestrutura
- a colaboração passa a emergir também do sistema, não apenas da coordenação humana

---

## Estrutura documental

| Ficheiro               | Conteúdo                                     |
|------------------------|----------------------------------------------|
| `VISION.md`            | Visão do produto e conceito base             |
| `PROJECT_CONTEXT.md`   | Arquitectura, contratos e decisões técnicas  |
| `GETTING_STARTED.md`   | Código completo do agente mínimo             |
| `ISSUES.md`            | Backlog com milestones e issues              |
| `CLAUDE.md`            | Regras para o Claude Code                    |
| `CLAUDE_CODE_GUIDE.md` | Prompts de apoio à implementação             |

---

## Contribuição

Contribuições são bem-vindas, especialmente em temas como:

- modelação de capability graphs
- orquestração paralela entre agentes
- agentes locais com Ollama
- contratos imutáveis em Java
- gamificação aplicada à colaboração técnica
- visibilidade operacional de skills e lacunas de capacidade

Se a proposta fizer sentido para você, sinta-se à vontade para abrir uma issue ou iniciar uma discussão.

---

## Status

Projeto em construção.

A visão está estruturada e a implementação do proof of concept está a iniciar.
