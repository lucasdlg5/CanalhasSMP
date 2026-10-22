# Issue #5: Integração de Telemetria do Jacinto no Dashboard C# e Canalhas Mobile App

- **Tipo:** 📊 Integração & Monitoramento (Cross-Platform)
- **Prioridade:** 🟡 Média
- **Módulo:** `JacintoItemakino`, `CanalhasServerDashboard`, `CanalhasMobileApp`
- **Status:** 📋 Aberto / Sugestão

---

## 🎯 Objetivo & Visão Geral

O ecossistema CanalhasSMP possui duas ferramentas de monitoramento ativas:
1. **CanalhasServerDashboard** (Aplicativo desktop Windows em C# Windows Forms com métricas de TPS, RAM, players e contratos do Senhor Cummins).
2. **CanalhasMobileApp** (Aplicativo móvel Android em Kotlin com telemetria via WebSocket/HTTP).

Atualmente, o Jacinto opera exclusivamente via chat in-game e comandos. Ter um **Card do Jacinto Operário** no Dashboard e no App móvel permitiria aos administradores e membros da firma acompanhar o expediente do operário em tempo real sem precisar abrir o Minecraft!

---

## 🔍 Visualização Conceitual (Mockup)

```
+-------------------------------------------------------+
| 👷 JACINTO ITEMAKINO BAÚ (OPERÁRIO OFICIAL DA FIRMA)   |
+-------------------------------------------------------+
| Status: MINERANDO PASSIVAMENTE (3x3 Chunks Ativos)    |
| Alvo: Andesito 🪨 | Camada: Y=15                      |
| Destino: Baú em [X: 120, Y: 64, Z: -350]              |
| Progresso do Baú: [██████████░░░░░░░░░░] 52% (900/1728)|
| Ferramenta em Mãos: Picareta de Ferro (Durab: 180/250)|
| Total na Carreira: 3.420 blocos minerados             |
| [ 🛑 Pausar Expediente ]   [ 📍 Ver no Mapa ]         |
+-------------------------------------------------------+
```

---

## 💡 Proposta de Implementação

1. **Geração de JSON Periódico no Mod (`jacinto_telemetry.json`):**
   - A cada 5 segundos (ou a cada depósito em baú), o mod exporta um arquivo em `config/jacinto/telemetry.json`:
   ```json
   {
     "status": "MINING",
     "targetItem": "Andesito",
     "chestProgress": {
       "current": 900,
       "total": 1728,
       "percentage": 52.0
     },
     "heldPickaxe": {
       "name": "Picareta de Ferro",
       "tier": "Ferro",
       "damage": 70,
       "maxDamage": 250
     },
     "location": {
       "x": 124.5,
       "y": 15.0,
       "z": -348.2,
       "dimension": "minecraft:overworld"
     },
     "careerMined": 3420
   }
   ```
2. **Consumo no Dashboard C# (`DashboardForm.cs`):**
   - Um timer lê esse JSON e atualiza uma `GroupBox` estilizada no painel do servidor.
3. **Consumo no App Móvel (`TelemetryModels.kt`):**
   - Expor via endpoint HTTP `/api/jacinto` do servidor web local.

---

## ✅ Critérios de Aceite

- [ ] Arquivo `config/jacinto/telemetry.json` é atualizado em tempo real com as métricas do operário.
- [ ] O Dashboard C# exibe o card do Jacinto com status, alvo e durabilidade da ferramenta.
- [ ] Administradores podem visualizar o progresso do baú sem entrar no jogo.
