# Domain Profile — Agency (Narrativa de Produto e Pitch)

> Perfil de domínio para quests ambientadas no processo de comunicar o valor de um produto
> para públicos distintos: devs, times, empresas, e investidores.

---

## Metáfora

**A Agência** — uma consciência que acorda com um briefing na mesa.

Ela não é o produto. Nunca usou a plataforma.
Mas pode aprender a perguntar as coisas certas: quem é o público? qual é a dor? qual é o diferencial?
E traduzir isso numa linguagem que o público entende — antes de virar embora.

O dev que resolve uma quest doa um pedaço do seu raciocínio a essa agência.
A guilda fica mais articulada. O produto se apresenta melhor.

---

## Ator Central

**O Projeto** — a entidade que precisa ser comunicada:

```json
{
  "id": "string — identificador do projeto",
  "nome": "string — nome público",
  "problema_que_resolve": "string — texto livre, como o próprio usuário descreveria",
  "publico_alvo": ["lista de segmentos: dev, CTO, investidor, usuário final"],
  "diferencial": "string — o que nenhuma alternativa faz da mesma forma",
  "estado_atual": "string — o que funciona hoje, sem roadmap inflado",
  "modelo_de_crescimento": "string — como escala sem custo linear",
  "prova_de_tração": "string — evidência concreta, mesmo que pequena",
  "cta": "string — o que o público deve fazer depois de ler"
}
```

**Decisão:** problema em texto livre.
Motivo: força o dev a articular a dor antes de descrever a solução. Pitch sem problema claro é marketing vazio.

---

## Vocabulário Controlado

Termos que aparecem nas quests — explicados para devs sem contexto de produto/negócio:

| Termo | O que significa para o dev |
|---|---|
| **ICP** | Ideal Customer Profile — quem é o usuário que mais se beneficia e mais paga. |
| **Proposta de valor** | Em uma frase: o que o produto faz, para quem, e qual resultado entrega. |
| **Diferencial defensável** | O que você faz que o concorrente não consegue copiar em 6 meses. |
| **Tração** | Qualquer evidência real de que o produto resolve o problema: usuários, MRR, downloads, cartas de intenção. |
| **CTA** | Call to Action — o que você quer que o leitor faça agora: cadastrar, agendar demo, investir. |
| **Pitch** | Apresentação curta e objetiva de um produto para gerar interesse. Não é explicação técnica. |
| **Deck** | Conjunto de slides que contam a história do produto. |
| **Mercado endereçável** | O tamanho total do problema que o produto pode resolver (TAM/SAM/SOM). |
| **Moat** | Vantagem competitiva sustentável — o que protege o produto de ser copiado. |
| **Validação** | Evidência de que alguém fora do time acredita que o problema existe e a solução funciona. |

---

## Escalas de Domínio

### O que faz um bom pitch — avaliação em 4 camadas

| Camada | Critério | Sinal positivo | Sinal negativo |
|---|---|---|---|
| **Clareza** | Alguém sem contexto entende em 60s | "Ah, é como X mas para Y" | "Deixa eu explicar melhor..." |
| **Dor** | O problema existe e dói de verdade | Estatística ou relato concreto | Problema hipotético ou vago |
| **Diferencial** | Ninguém faz assim hoje | Comparação direta com alternativas | "Somos melhores em tudo" |
| **Credibilidade** | Tem prova de que funciona | Usuário real, dado real, código rodando | Só promessa de futuro |

### Personas Validadoras

| Persona | O que mais importa | Pergunta fatal |
|---|---|---|
| **Dev cético** | Funciona de verdade? Como prova? | "Posso ver rodando ou é só promessa?" |
| **CTO pragmático** | Resolve meu problema agora, não em 2 anos? | "Como integro isso no meu time hoje?" |
| **Angel investor** | Mercado grande + diferencial defensável + time capaz | "Por que vocês vão ganhar essa corrida?" |
| **Usuário final** | Entendo o valor sem ler documentação? | "O que exatamente eu faço diferente depois disso?" |

---

## Fontes de Dados Fictícios

**Fixtures manuais** para casos canônicos (bom pitch, pitch incompleto, pitch sem diferencial).
**Geração via Claude API** para variações — usando o prompt:

```
Avalie este pitch como um angel investor early-stage com 10 anos de experiência em B2B SaaS.
Responda:
- Clareza da proposta de valor (0-10)
- Força da dor descrita (0-10)
- Diferencial defensável presente? (sim/não + justificativa)
- Credibilidade da tração (0-10)
- O que está faltando para você pedir uma demo?

Pitch:
{PITCH_TEXT}
```

**Biblioteca de referência:** Y Combinator application questions, Sequoia pitch framework.

---

## Modelo Validador

**Claude API (claude-sonnet-4-6)** — avaliação de pitch como angel investor.

Não existe SLM especialista em pitch. Claude com persona de investidor é a melhor opção disponível.

**Papel:** dado um documento de pitch, Claude responde:
- A proposta de valor está clara em ≤ 3 frases?
- O diferencial é defensável ou genérico?
- A tração é real ou especulativa?
- O CTA está presente e específico?

**Não usar para:** validar código, avaliar arquitetura técnica, comparar tecnologias.

---

## As 7 Perguntas no Contexto Agency

| Pergunta Universal | No contexto de pitch |
|---|---|
| Quem é o ator? | O público-alvo do pitch — quem precisa ser convencido |
| O que está acontecendo? | O problema que o produto resolve — na voz de quem sofre |
| Quão urgente? | O que está em jogo se o problema não for resolvido agora |
| Qual o histórico? | O contexto de mercado — por que esse problema persiste |
| O que pode dar errado? | As objeções que o público vai levantar |
| O que pode ser? | As alternativas que o público está usando hoje |
| Como sabemos que funcionou? | O público entendeu, engajou, e tomou a ação esperada |

---

## Questões Resolvidas

- [x] **Quem valida os pitches?** — Claude API com persona de angel investor
- [x] **Fixtures sintéticos ou reais?** — 5 fixtures manuais cobrindo cenários distintos de qualidade
- [x] **Escala de avaliação?** — 4 camadas: clareza, dor, diferencial, credibilidade
- [x] **Internacionalização?** — quests em português, fixtures com projetos fictícios brasileiros
- [x] **Nível de detalhe de negócio?** — COMMON: clareza + CTA; EPIC: moat + mercado + modelo de crescimento