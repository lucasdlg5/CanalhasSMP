# 🖥️ CanalhasServerDashboard — Painel de Controle Desktop

O **CanalhasServerDashboard** é a aplicação desktop oficial desenvolvida em **C# e .NET 10 (Windows Forms)** para monitoramento, administração e controle em tempo real do servidor CanalhasSMP. 

Ele combina visualização de métricas de hardware e telemetria do jogo (estilo Spark Profiler), chat in-game bidirecional, terminal de comandos com histórico, renderizador de mapa do mundo baseado nos arquivos Anvil (`.mca`), e atalhos de gerenciamento rápido em uma interface moderna com tema escuro.

---

## 🌟 Principais Recursos

### 1. Gestão Inteligente do Servidor Minecraft
* **Controle Total de Ciclo de Vida:**
  * **▶ Iniciar Servidor:** Inicia o processo Java com os parâmetros otimizados e alocação de memória configurada.
  * **🛑 Parar Servidor:** Desligamento seguro enviando o comando `stop`, aguardando a finalização dos workers de IO, profilers e gravação de chunks antes de encerrar o processo.
  * **🔄 Reiniciar:** Encerra o servidor com segurança e o reinicia automaticamente em seguida.
* **Auto-Reconexão a Processos Existentes (*AttachToPid*):**
  * Se o Dashboard for fechado ou reiniciado com o servidor já rodando em segundo plano, ele se reconecta automaticamente ao processo Java em execução sem duplicar instâncias e sem interromper os jogadores.
* **Limpeza de Instâncias Órfãs:**
  * Detecta e encerra instâncias zumbis ou travadas de execuções anteriores para liberar portas (`25565` e `25588`) e memória RAM.

---

### 2. Cards de Telemetria e Monitoramento de Saúde
Localizados no topo do painel com atualização constante a cada 1 segundo:
* **TPS (Ticks Per Second):** Indicador visual de desempenho do jogo (20.0 cravado = excelente; amarelo = oscilação leve; vermelho = queda de ticks).
* **MSPT (Milliseconds Per Tick):** Exibe a média, pico máximo e percentil P95 do tempo que a CPU gasta por tick (< 30ms é excelente, > 50ms indica gargalo).
* **Consumo de Memória RAM:** Mostra o uso real da JVM em Megabytes e porcentagem sobre o total alocado.
* **Saúde da Internet do Host:** Ping médio para o Google DNS (`8.8.8.8`) e taxa de perda de pacotes medida pelo mod Senhor Cummins.
* **Consumo de Tokens da IA:** Acompanhamento do gasto de tokens da API do Gemini e tamanho de contexto retido na memória.
* **Detalhamento:** Clicar duas vezes em qualquer card abre uma janela com gráficos e histórico analítico detalhado.

---

### 3. Abas de Controle e Ferramentas

| Aba | Descrição |
| :--- | :--- |
| **📋 Logs do Sistema (Completo)** | Console de saída de texto em tempo real com todas as mensagens brutas do servidor. Inclui barra de pesquisa com filtro instantâneo, rolagem automática e filtro rápido para visualizar apenas erros (`[ERROR]` e exceções). |
| **💬 Chat in-Game** | Aba exclusiva para acompanhar conversas dos jogadores, avisos do Senhor Cummins, mortes e conquistas em formato limpo. Possui campo inferior para digitar e enviar mensagens como console (`[Server]`) para os jogadores. |
| **🗺️ Mapa Regional (Anvil Map)** | Renderizador visual que lê diretamente os arquivos de região (`.mca`) das dimensões (Overworld, Nether e End). Exibe o relevo do mundo, cabeças dos jogadores com coordenadas exatas, raio de geração do mod Chunky, e controles de Zoom (+ / -) e Pan (arrastar com o mouse). |
| **📜 Mural da Firma** | Exibição visual do Mural de Novidades oficial com avisos aos membros e tabela com o ranking de horas jogadas (*Playtime*). |

---

### 4. Atalhos de Comando no Cabeçalho
No canto superior direito da janela, botões rápidos executam tarefas operacionais com 1 clique:
* **⚡ Spark:** Inicia ou para a gravação de relatórios de perfil de desempenho do mod Spark.
* **💾 Backup:** Executa imediatamente um backup completo do mundo via Textile Backup.
* **🗺️ Chunky:** Consulta o progresso da pré-geração de chunks no mapa.
* **🐱 Gato TP:** Teleporta o corpo físico do mascote Cummins para perto do operador.
* **🐶 Vitória / 🧠 Vitória AI:** Gerencia as ações e a inteligência artificial do pet guardião Vitória.
* **📜 Mural:** Força a transmissão do mural de novidades atualizado para todos os players online.
* **🎲 Pérola:** Pede para o Cummins puxar assunto ou soltar uma frase descontraída no chat.
* **🧹 GC:** Força a coleta de lixo imediata da memória Java (`spark gc`).
* **🛡️ Firewall:** Abre a confirmação de Administrador do Windows e libera as portas `25588` (App Celular) e `25565` (Minecraft) no Firewall com 1 clique.

---

## 🚀 Como Iniciar o Dashboard

### Opção 1: Pelo Inicializador Rápido na Raiz (Recomendado)
Basta dar dois cliques no arquivo:
```text
INICIAR_PAINEL_CANALHAS.bat
```
Este script copia automaticamente as últimas atualizações compiladas e abre o executável do painel.

### Opção 2: Pelo Executável Direto
Execute o arquivo:
```text
CanalhasServerDashboard/publish/CanalhasServerDashboard.exe
```

---

## 🛠️ Como Compilar e Atualizar o Código-Fonte

O projeto utiliza **.NET 10 SDK** (C# Windows Forms).

### Compilar via Linha de Comando:
1. Abra a pasta `CanalhasServerDashboard`:
   ```bash
   cd CanalhasServerDashboard
   ```
2. Compile em modo Release:
   ```bash
   dotnet build CanalhasServerDashboard.csproj -c Release
   ```
3. Para publicar os binários para distribuição:
   ```bash
   dotnet publish CanalhasServerDashboard.csproj -c Release -o publish_staged
   ```
4. Para aplicar a atualização sem travar o painel que estiver rodando:
   ```bash
   ApplyDashboardUpdate.bat
   ```
