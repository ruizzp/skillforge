# Domain Profile — Medical (Urgência e Emergência)

> Perfil de domínio para quests ambientadas em pronto-socorro e triagem hospitalar.

---

## Metáfora

**O Plantão** — uma consciência que acorda num pronto-socorro às 23h.  
Ela não é médica. Nunca estudou medicina.  
Mas pode aprender a fazer as perguntas certas, na ordem certa, sem cansar.

O dev que resolve uma quest doa um pedaço do seu raciocínio a essa consciência.  
A guilda cresce. O plantão fica mais esperto.

---

## Ator Central

O **Paciente de Urgência** — a entidade mínima que o sistema precisa "ver":

```json
{
  "id": "string — identificador anônimo do atendimento",
  "sexo": "M | F | NB",
  "idade": "integer — anos",
  "peso_kg": "float — opcional, relevante para dosagem",
  "queixa_principal": "string — texto livre, como o paciente descreveu",
  "sinais_vitais": {
    "pa_sistolica": "integer — mmHg",
    "pa_diastolica": "integer — mmHg",
    "fc": "integer — bpm",
    "fr": "integer — irpm",
    "spo2": "integer — %",
    "temperatura": "float — °C",
    "glasgow": "integer — 3 a 15, nível de consciência"
  },
  "historico": ["lista de condições preexistentes"],
  "medicamentos_em_uso": ["lista de medicamentos com dose"]
}
```

**Decisão:** texto livre na queixa principal.  
Motivo: força o dev a lidar com NLP desde o começo. Formulário estruturado seria trivial.

---

## Vocabulário Controlado

Termos que aparecem nas quests — explicados para devs sem contexto clínico:

| Termo | O que significa para o dev |
|---|---|
| **Queixa principal** | O motivo pelo qual o paciente chegou. Texto livre, como ele disse. |
| **Sinal vital** | Medição fisiológica objetiva: pressão, pulso, temperatura, oxigenação. |
| **Red flag** | Sinal ou sintoma que indica risco imediato de vida. Muda a prioridade para máxima. |
| **Triagem** | Processo de classificar quem precisa de atendimento mais urgente. |
| **Diagnóstico diferencial** | Lista de hipóteses do que pode estar causando os sintomas. |
| **CID-10** | Código internacional de doenças. Ex: I21 = Infarto agudo do miocárdio. |
| **Conduta** | O que o médico decide fazer: exames, medicamentos, internação, alta. |
| **Interação medicamentosa** | Quando dois medicamentos juntos causam efeito indesejado. |
| **Glasgow** | Escala de 3 a 15 que mede nível de consciência. 15 = totalmente acordado. 3 = mínimo. |
| **SCA** | Síndrome Coronariana Aguda — suspeita de infarto. Alta prioridade sempre. |

---

## Escalas de Domínio

### Manchester Triage System (MTS) — simplificado

5 níveis de urgência usados nas quests:

| Cor | Nível | Tempo máximo | Exemplo |
|---|---|---|---|
| VERMELHO | 1 — Imediato | 0 min | Parada cardíaca, Glasgow < 9 |
| LARANJA | 2 — Muito urgente | 10 min | Dor torácica + diaforese, dispneia grave |
| AMARELO | 3 — Urgente | 60 min | Dor abdominal intensa, fratura com pulso |
| VERDE | 4 — Pouco urgente | 120 min | Febre sem sinais de alarme, corte superficial |
| AZUL | 5 — Não urgente | 240 min | Receita, resultado de exame, queixa crônica estável |

**Red flags automáticos → sempre VERMELHO ou LARANJA:**
- Dor torácica + irradiação + diaforese
- SpO2 < 90%
- Frequência cardíaca > 150 ou < 40 bpm
- Pressão sistólica < 90 mmHg
- Glasgow ≤ 8
- Convulsão ativa

---

## Fontes de Dados Fictícios

**Fixtures manuais** para casos canônicos (gabarito garantido).  
**Geração via LLM** para variações — usando o prompt:

```
Gere um caso clínico fictício de pronto-socorro com:
- Paciente anônimo (sexo, idade, peso)
- Queixa em texto livre, como o paciente falaria
- Sinais vitais plausíveis para a queixa
- 0-3 comorbidades e medicamentos em uso
- Gabarito Manchester com justificativa

Domínio: urgência hospitalar adulto
Nível de complexidade: {COMUM | MODERADO | GRAVE}
```

**Biblioteca de referência:** Synthea (dados sintéticos de saúde) — útil para volume.  
Repositório: `https://github.com/synthetichealth/synthea`

---

## Modelo Validador

**Meditron-7B via Ollama** — validação clínica de fixtures e gabaritos.

```bash
ollama pull meditron
```

**Papel:** dado um fixture e seu gabarito, o Meditron responde:
- O nível Manchester está correto para estes sinais?
- Algum red flag foi ignorado?
- As hipóteses diagnósticas são plausíveis?

**Não usar Meditron para:** escrever código, formatar JSON, critérios técnicos de software.

---

## As 7 Perguntas no Contexto Médico

| Pergunta Universal | No pronto-socorro |
|---|---|
| Quem é o ator? | O paciente — `PS-001` |
| O que está acontecendo? | A queixa principal — `PS-002` |
| Quão urgente? | Score Manchester — `PS-003` |
| Qual o histórico? | Comorbidades e medicamentos — `PS-004` |
| O que pode dar errado? | Interações medicamentosas, red flags — `PS-005` |
| O que pode ser? | Diagnóstico diferencial — `PS-006` |
| Como sabemos que funcionou? | Monitoramento de vitais — `PS-007` |

---

## Questões Resolvidas

- [x] **Escala própria ou Manchester?** — Manchester simplificado (5 cores, documentado acima)
- [x] **Quem valida os gabaritos?** — Meditron via Ollama + literatura pública como referência
- [x] **Fixtures sintéticas vs manuais?** — manuais para casos canônicos (ps-case-001 a 005), LLM para variações
- [x] **Internacionalização?** — quests em português, fixtures com pacientes fictícios brasileiros, sem nome próprio
- [x] **Nível de detalhe farmacológico?** — COMMON: classe e interação óbvia; EPIC: mecanismo e ajuste de dose