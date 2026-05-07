# `QUEST-014` — SkillForge: Apresentação Formal do Projeto `EPIC` `+800 XP`

**Domínio:** SkillForge (meta-quest — sobre o próprio projeto)
**Status:** `[ ]`
**Skills requeridas:** `technical-leadership`, `product-thinking`, `communication`
**Pergunta universal:** Como sabemos que funcionou? (a proposta foi compreendida e gerou interesse real)

---

## Contexto

O SkillForge existe como código e documentação — mas ainda não tem uma forma de se apresentar
para o mundo de forma que alguém de fora entenda o valor em menos de 5 minutos.

Sem isso, o projeto depende de explicações longas e contexto implícito.
Com isso, qualquer dev, empresa ou investidor entende a proposta e consegue responder:
*"Isso resolve um problema que eu tenho?"*

Esta quest cria a apresentação formal do projeto.

---

## O Problema que o SkillForge Resolve

Antes de criar a apresentação, você precisa entender o problema com clareza:

**Para devs:**
Skills declaradas não provam nada. Um currículo diz "Java Senior" —
mas não diz se essa pessoa já resolveu um problema real de concorrência sob pressão.
No SkillForge, skills são validadas por desafios reais, não por autodeclaração.

**Para times e empresas:**
Colaboração distribuída é caótica. Quem sabe o quê? Quem está disponível?
Que problema posso jogar para quem?
No SkillForge, o hub roteia problemas automaticamente para heroes com as skills certas.

**Para o ecossistema de IA:**
Agents de IA geralmente são testados em benchmarks artificiais.
No SkillForge, heroes de IA resolvem problemas reais ao lado de devs humanos —
com XP, confiança e histórico auditável via GitHub.

---

## O que fazer

Criar uma apresentação formal do SkillForge que sirva para **dois públicos distintos**
com materiais separados:

### Entregável 1 — Pitch para Devs e Guilds

**Formato:** documento Markdown em `docs/pitch/FOR_DEVS.md`
**Tamanho:** máx. 2 páginas (lido em 5 min)
**Objetivo:** dev lê e responde "quero criar minha guild" ou "quero entrar numa guild"

Deve cobrir:
- O que é o SkillForge em 3 frases
- O que muda para o dev que participa (skills validadas, XP visível, colaboração com IA)
- Como entrar em 3 passos
- O que está disponível hoje (plataforma funcional + domínio médico em construção)
- O que está chegando

### Entregável 2 — Pitch para Empresas e Investidores

**Formato:** documento Markdown em `docs/pitch/FOR_COMPANIES.md`  
**Tamanho:** máx. 3 páginas
**Objetivo:** tomador de decisão entende o valor de negócio e pede uma demo

Deve cobrir:
- O problema de mercado (tamanho, dor, por que agora)
- A solução e como funciona (sem detalhes técnicos desnecessários)
- O diferencial: skills validadas por IA + histórico público no GitHub
- O modelo de crescimento: guilds são autônomas, a plataforma escala sem custo linear
- Casos de uso concretos (ex: empresa contrata uma guild para projeto de 3 meses)
- Estado atual e próximos marcos

### Entregável 3 — README atualizado

O `README.md` atual é técnico. Adicionar uma seção `## Por que o SkillForge?` no topo —
antes de qualquer instrução técnica — que responda em 5 linhas o que o projeto é
e por que importa.

---

## Fixtures de Referência

O que uma boa apresentação faz em cada contexto:

```json
[
  {
    "id": "QUEST014-A",
    "publico": "dev senior procurando visibilidade",
    "pergunta_que_deve_responder": "Como o SkillForge prova minhas skills de forma que o mercado reconhece?",
    "resposta_esperada_no_pitch": "Skills são validadas por desafios reais via IA, registradas publicamente no GitHub como labels auditáveis"
  },
  {
    "id": "QUEST014-B",
    "publico": "CTO de startup de 30 pessoas",
    "pergunta_que_deve_responder": "Como o SkillForge me ajuda a encontrar o dev certo para um problema específico?",
    "resposta_esperada_no_pitch": "O hub roteia problemas para heroes com skills validadas — não declaradas. Você vê o histórico de XP e quests completadas antes de contratar."
  },
  {
    "id": "QUEST014-C",
    "publico": "investidor early-stage em infra de IA",
    "pergunta_que_deve_responder": "Qual é o diferencial defensável do SkillForge vs LinkedIn ou Upwork?",
    "resposta_esperada_no_pitch": "Skills são validadas por IA em contexto real, não por endorsements. O estado é público e auditável. E o sistema inclui agents de IA como membros da guild — não só humanos."
  }
]
```

---

## Oportunidades que a apresentação deve comunicar

Inclua ao menos 3 destas nos materiais, com evidência ou argumento:

| Oportunidade | Argumento |
|---|---|
| **Mercado de devs freelance** | 59M freelancers nos EUA (2023). Nenhuma plataforma valida skills tecnicamente. |
| **Trabalho distribuído pós-pandemia** | Times remotos precisam de visibilidade de capacidade — o hub resolve isso. |
| **IA como membro de time** | SkillForge é uma das poucas plataformas onde agents de IA e humanos colaboram com métricas comparáveis. |
| **GitHub como infraestrutura** | Zero custo de infraestrutura própria para estado — escala com o ecossistema do GitHub. |
| **Domínios especializados** | Guilds podem se especializar (médica, financeira, logística) — mercado vertical com alta barreira de entrada. |

---

## Critérios de Aceitação

**Técnico:**
- [ ] `docs/pitch/FOR_DEVS.md` criado e legível sem contexto prévio
- [ ] `docs/pitch/FOR_COMPANIES.md` criado com estrutura de problema → solução → diferencial → oportunidade
- [ ] `README.md` atualizado com seção `## Por que o SkillForge?` no topo
- [ ] Nenhum dos documentos exige leitura de outro documento para fazer sentido

**Produto:**
- [ ] Alguém sem contexto do projeto lê `FOR_DEVS.md` e consegue explicar o SkillForge com suas próprias palavras
- [ ] `FOR_COMPANIES.md` responde as 3 perguntas dos fixtures (QUEST014-A, B, C)
- [ ] O diferencial "skills validadas por IA, não autodeclaradas" aparece de forma clara em ambos os materiais
- [ ] Pelo menos 3 oportunidades da tabela estão comunicadas com argumento concreto

---

## Como pegar esta quest

1. Leia `README.md`, `GUILD_ONBOARDING.md` e `CLAUDE.md` para entender o projeto
2. Responda para si mesmo os 3 fixtures antes de escrever qualquer linha
3. Escreva `FOR_DEVS.md` primeiro — é o mais difícil (dev é cético, quer provas)
4. Derive `FOR_COMPANIES.md` a partir do mesmo núcleo de valor
5. Atualize o `README.md` por último

> Se você precisar de mais de 3 frases para explicar o que é o SkillForge,
> o pitch ainda não está pronto.