# Issue #4: Suporte a Mochilas de Operário e Expansão do Lote de Transporte

- **Tipo:** 🎒 Melhoria de Qualidade de Vida (QoL & Performance)
- **Prioridade:** 🟢 Média-Baixa
- **Módulo:** `JacintoItemakino` (`JacintoNpc.java`, `JacintoMiner.java`)
- **Status:** 📋 Aberto / Sugestão

---

## 🎯 Objetivo & Visão Geral

Atualmente, o Jacinto transporta um lote fixo de **32 itens** no seu compartimento interno virtual antes de interromper a escavação e fazer o percurso de caminhada de volta ao baú de destino.
Quando a jazida de minério fica a 100 ou 150 blocos de distância da base, o tempo gasto caminhando de ida e volta torna-se muito maior do que o tempo efetivo de escavação.

Como o modpack Prominence II possui mods de mochila como **Traveler's Backpack** e mochilas de couro/ferro, poder equipar o Jacinto com uma mochila ou saco de transporte (bundle) aumentaria drasticamente sua produtividade.

---

## 🔍 Tabela de Capacidade por Equipamento

| Equipamento Equipado no Peito/Costas | Capacidade do Lote | Viagens para Encher Baú Simples (1.728 itens) | Eficiência |
| :--- | :---: | :---: | :---: |
| **Padrão (Sem Mochila / Bolso da Firma)** | 32 itens | 54 viagens | 100% (Base) |
| **Trouxa / Bundle** | 64 itens (1 pack) | 27 viagens | 200% (+100%) |
| **Mochila de Couro (Leather Backpack)** | 128 itens (2 packs) | 14 viagens | 400% (+300%) |
| **Mochila de Ferro ou Superior** | 256 itens (4 packs) | 7 viagens | 800% (+700%) |

---

## 💡 Proposta de Implementação

1. **Reconhecimento da Mochila:**
   - Ao clicar com o botão direito no Jacinto segurando um item da categoria `BackpackItem` ou `BundleItem`:
     - O Jacinto equipa o item no slot `CHEST` (peitoral/costas) ou salva na persistência `customBackpackSnbt`.
2. **Cálculo Dinâmico do Limite de Carga:**
   - No `JacintoMiner.java`, substituir o valor fixo `32` por uma função `getMaxCarriedCapacity(entity)`:
   ```java
   int maxCapacity = 32;
   if (hasBackpack(entity)) {
       maxCapacity = 128; // Ou proporcional ao tier da mochila
   }
   if (hasMiningChest && carriedItemCount >= maxCapacity) {
       isReturningToChest = true;
   }
   ```

---

## ✅ Critérios de Aceite

- [ ] Jogador pode entregar uma mochila ao Jacinto com o botão direito.
- [ ] Jacinto notifica no chat que sua capacidade de carga foi aumentada.
- [ ] Jacinto realiza menos viagens de retorno quando equipado com mochila, aumentando a velocidade de entrega no baú.
