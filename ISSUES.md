# 📋 Backlog de Issues & Sugestões — CanalhasSMP

> Central oficial de acompanhamento de **problemas conhecidos**, **melhorias planejadas** e **sugestões de funcionalidades** para o ecossistema do servidor CanalhasSMP e o operário **Jacinto Itemakino Baú**.

---

## 🧭 Quadro Geral de Demandas

| ID | Título da Issue | Categoria | Prioridade | Status | Documento Detalhado |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **#1** | **Suporte a Encantamentos (Fortuna, Silk Touch, Eficiência)** | 🛠️ Mecânica / Bug | 🟡 Alta | 📋 Planejado | [.github/issues/ISSUE_01_ENCANTAMENTOS.md](file:///.github/issues/ISSUE_01_ENCANTAMENTOS.md) |
| **#2** | **Mineração Interdimensional (Nether & The End)** | 🚀 Feature | 🔴 Alta | 📋 Planejado | [.github/issues/ISSUE_02_DIMENSOES_NETHER_END.md](file:///.github/issues/ISSUE_02_DIMENSOES_NETHER_END.md) |
| **#3** | **Sistema Ativo de Contenção de Lava e Fluidos** | 🛡️ Sobrevivência | 🟡 Média | 💡 Sugestão | [.github/issues/ISSUE_03_CONTENCAO_LAVA.md](file:///.github/issues/ISSUE_03_CONTENCAO_LAVA.md) |
| **#4** | **Suporte a Mochilas de Operário (Traveler's Backpack)** | 🎒 QoL | 🟢 Média | 💡 Sugestão | [.github/issues/ISSUE_04_MOCHILAS_CAPACIDADE.md](file:///.github/issues/ISSUE_04_MOCHILAS_CAPACIDADE.md) |
| **#5** | **Telemetria do Jacinto no Dashboard C# & App Móvel** | 📊 Integração | 🟡 Média | 💡 Sugestão | [.github/issues/ISSUE_05_TELEMETRIA_DASHBOARD_APP.md](file:///.github/issues/ISSUE_05_TELEMETRIA_DASHBOARD_APP.md) |
| **#6** | **Holograma / Marcador de Progresso no Baú** | 🎨 UI / UX | 🟢 Baixa | 💡 Sugestão | [.github/issues/ISSUE_06_FEEDBACK_VISUAL_HOLOGRAMA.md](file:///.github/issues/ISSUE_06_FEEDBACK_VISUAL_HOLOGRAMA.md) |
| **#7** | **Suporte Avançado a Baús de Mods (Iron Chests & Gavetas)** | 🧩 Compatibilidade | 🟡 Média | 💡 Sugestão | [.github/issues/ISSUE_07_COMPATIBILIDADE_BAUS_MODS.md](file:///.github/issues/ISSUE_07_COMPATIBILIDADE_BAUS_MODS.md) |

---

## 📑 Resumo Didático de Cada Issue

### 1. ⛏️ [Issue #1: Suporte a Encantamentos na Picareta](file:///.github/issues/ISSUE_01_ENCANTAMENTOS.md)
* **O Problema:** Quando o jogador equipa o Jacinto com uma picareta encantada com *Fortuna III*, *Toque Suave* ou *Eficiência*, o método vanilla básico não aproveita todo o potencial do loot table.
* **A Solução:** Simular o minerador usando `Block.getDroppedStacks` com a ferramenta em mãos, multiplicando diamantes e carvões com Fortuna e acelerando a velocidade de quebra com Eficiência.

---

### 2. 🌌 [Issue #2: Mineração Interdimensional (Nether & The End)](file:///.github/issues/ISSUE_02_DIMENSOES_NETHER_END.md)
* **O Problema:** Pedir *Netherite (Ancient Debris)* ou *Quartzo* com um baú no Overworld faz o Jacinto escavar a camada Y=14 do mundo normal em vão, pois esses minérios só existem no Nether.
* **A Solução:** Fase 1: Jacinto avisa didaticamente que o minério exige baú e expedição no Nether. Fase 2: Atravessar portais de forma autônoma.

---

### 3. 🛡️ [Issue #3: Sistema Ativo de Contenção de Lava e Fluidos](file:///.github/issues/ISSUE_03_CONTENCAO_LAVA.md)
* **O Problema:** Ao minerar em camadas profundas de Deepslate (Y=-58), romper um bolsão lateral de lava pode queimar o Jacinto ou obstruir a rota de retorno.
* **A Solução:** Reação rápida de operário: ao detectar fluxo de lava fresca a 1 bloco de distância, o Jacinto recua e coloca um bloco de Cobblestone imediatamente para estancar a nascente.

---

### 4. 🎒 [Issue #4: Suporte a Mochilas de Operário e Expansão de Carga](file:///.github/issues/ISSUE_04_MOCHILAS_CAPACIDADE.md)
* **O Problema:** O lote de transporte fixo de 32 itens exige muitas viagens de ida e volta para minas distantes.
* **A Solução:** Permitir equipar o Jacinto com Mochilas (*Traveler's Backpack* ou *Bundles*), elevando a capacidade para 64, 128 ou 256 blocos por viagem, multiplicando a produtividade da firma em até 8x.

---

### 5. 📊 [Issue #5: Telemetria do Jacinto no Dashboard C# & App Móvel](file:///.github/issues/ISSUE_05_TELEMETRIA_DASHBOARD_APP.md)
* **O Problema:** Para saber como está o trabalho do Jacinto, os jogadores precisam estar dentro do servidor digitando `/minerar status`.
* **A Solução:** Exportar um arquivo `config/jacinto/telemetry.json` em tempo real para alimentar um card no **Dashboard C#** e telas no **CanalhasMobileApp**.

---

### 6. 🎨 [Issue #6: Holograma ou Marcador de Progresso no Baú](file:///.github/issues/ISSUE_06_FEEDBACK_VISUAL_HOLOGRAMA.md)
* **O Problema:** Os jogadores não conseguem ver de relance o quanto o baú já foi preenchido enquanto andam pela base.
* **A Solução:** Adicionar um texto flutuante limpo (`TextDisplayEntity` do 1.20) acima do baú de mineração com a porcentagem e contagem atual (`[Mineração: Andesito - 68% Cheio]`).

---

### 7. 🧩 [Issue #7: Suporte Avançado a Baús de Mods](file:///.github/issues/ISSUE_07_COMPATIBILIDADE_BAUS_MODS.md)
* **O Problema:** Baús de mods como *Iron Chests* (Ferro, Ouro, Diamante) ou *Storage Drawers* usam lógicas de inventário ampliadas ou APIs de transferência diferentes.
* **A Solução:** Adicionar compatibilidade direta com `ItemStorage.SIDED` (Fabric Transfer API) e calcular a capacidade real de até 108 slots dos baús avançados.

---

*Repositório mantido com carinho para a firma CanalhasSMP.* ⛏️📦❤️
