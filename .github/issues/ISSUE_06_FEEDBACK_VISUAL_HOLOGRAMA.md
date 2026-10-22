# Issue #6: Holograma ou Marcador Visual de Progresso acima do Baú Designado

- **Tipo:** 🎨 Interface e Experiência do Usuário (UI/UX)
- **Prioridade:** 🟢 Baixa
- **Módulo:** `JacintoItemakino` (`JacintoMiner.java`)
- **Status:** ✅ Concluído / Implementado na v2.0.0

---

## 🎯 Objetivo & Visão Geral

Quando um jogador configura um baú com moldura e digita `/minerar`, o Jacinto parte para a mina e pode passar horas escavando no subsolo.
Ao passar pela sala de baús da base, seria incrível e muito prático se o jogador pudesse olhar para o baú e ver instantaneamente uma indicação visual suave do progresso (ex: partículas ou um texto flutuante temporário tipo *ArmorStand invisível* ou *TextDisplay* do 1.20).

---

## 🔍 Ilustração do Recurso

```
                 [ 📦 Mineração: Andesito ]
                 [ ⛏️ Jacinto Ativo | 68% ]
                   +------------------+
                   |     MOLDURA      |
                   |    [Andesito]    |
                   |   +----------+   |
                   |   |   BAÚ    |   |
                   +---+----------+---+
```

---

## 💡 Proposta de Implementação

1. **Uso de `DisplayEntity.TextDisplayEntity` (Nativo do Minecraft 1.20.1):**
   - No Minecraft 1.20+, a Mojang introduziu entidades nativas de display de texto (`TextDisplayEntity`), que não causam lag, não têm colisão e renderizam texto flutuante limpo.
   - Ao iniciar a mineração com baú, spawnar um `TextDisplay` 0.6 blocos acima do baú com o texto:
     - Linha 1: `§e[Mineração: Andesito]`
     - Linha 2: `§a68% cheio (1.175/1.728)`
2. **Atualização Periódica:**
   - A cada descarga do Jacinto no baú, o texto atualiza a porcentagem.
3. **Remoção Limpa:**
   - Ao concluir a tarefa ou digitar `/minerar stop`, a entidade de display é descartada com efeito de partículas comemorativas.

---

## ✅ Critérios de Aceite

- [ ] Baú designado para mineração exibe texto flutuante legível acima dele.
- [ ] O texto atualiza os números sempre que o Jacinto deposita uma remessa.
- [ ] O texto desaparece automaticamente quando o baú enche ou o jogador cancela a tarefa.
