# 📱 CanalhasMobileApp — Aplicativo Oficial do Servidor

O **CanalhasMobileApp** é o aplicativo móvel oficial do servidor CanalhasSMP para smartphones Android. Desenvolvido em **Kotlin** com **Jetpack Compose** e interface moderna com tema escuro (*Catppuccin Mocha / Cyberpunk Dark*), ele permite que administradores e jogadores acompanhem a saúde do servidor, conversem no chat in-game, rastreiem coordenadas e executem comandos de qualquer lugar diretamente pelo celular.

---

## 🌟 Principais Recursos

* **⚡ Painel de Telemetria em Tempo Real:**
  * TPS (Ticks Per Second) com gráfico histórico animado dos últimos minutos.
  * MSPT médio e pico máximo (tempo de processamento de cada tick do Minecraft).
  * Consumo exato de memória RAM (MB e percentual).
  * Ping contínuo da internet do servidor (8.8.8.8) e taxa de perda de pacotes.
  * Contagem e lista de operários online na firma.

* **📜 Mural de Novidades & Ranking de Horas:**
  * Consulte as últimas atualizações, avisos da diretoria e novidades do servidor.
  * Ranking oficial de horas jogadas (*playtime*) de cada membro do servidor.

* **🧭 Radar de Jogadores & Pets Guardiões:**
  * Localização em tempo real de todos os jogadores (Coordenadas X, Y, Z, dimensão e barra de vida).
  * Localização exata dos pets guardiões (**🐶 Vitória**, **🦇 Juninho**, **🐟 Pipira** e **🦁 Javidizineo**).

* **💬 Chat in-Game Bidirecional:**
  * Leia todas as mensagens enviadas pelos jogadores no servidor, respostas do mascote Senhor Cummins, mortes e conquistas.
  * Envie mensagens como console (`/say`) ou converse com quem está jogando direto pelo teclado do celular.

* **💻 Terminal Interativo de Comandos:**
  * Envie qualquer comando de console com 1 toque (`/stop`, `/weather clear`, `/time set day`, `/cummins status`, `/spark gc`, etc.).
  * Histórico de comandos enviados para repetição rápida.

* **⚙️ Atalhos de Conexão Rápida:**
  * Botões de 1 toque para alternar entre **Wi-Fi de Casa** (`192.168.18.20`) e **Radmin VPN** (`26.x.x.x`).

---

## 📥 Como Instalar no Celular

### Opção 1: Instalando o APK Pré-compilado (Mais Fácil)
1. O arquivo instalador do aplicativo fica localizado na pasta do projeto:
   ```text
   CanalhasMobileApp/CanalhasSMP-Mobile.apk
   ```
2. Envie esse arquivo para o seu celular (via WhatsApp Web, Telegram, Google Drive, cabo USB ou download direto).
3. No celular, toque no arquivo `CanalhasSMP-Mobile.apk` para instalar.
4. Caso o Android solicite, autorize a opção **"Permitir desta fonte"** (Instalar apps desconhecidos).
5. Conclua a instalação e abra o aplicativo **CanalhasSMP**.

---

## 🌐 Como Conectar ao Servidor

O aplicativo se comunica diretamente via HTTP com o Bridge de Telemetria do mod Senhor Cummins na porta **`25588`**. **Não requer senha nem RCON!**

### 1. Se o celular estiver conectado no mesmo Wi-Fi do computador (Em Casa):
1. Abra o aplicativo e vá na aba **Conexão** (ícone `<-->` no canto inferior direito).
2. Toque no botão de atalho **"Wi-Fi Casa (Local)"** ou preencha:
   * **IP do Servidor:** `192.168.18.20` *(ou o IP local atual do seu computador na rede)*
   * **Porta:** `25588`
3. Toque no botão azul **"💾 Salvar e Conectar"**.
4. O indicador no topo mudará de `OFFLINE` para **`● ONLINE`** em verde!

### 2. Se você estiver na rua ou em outra rede de internet:
* Conecte seu celular à mesma rede virtual do servidor (ex: via aplicativo do Hamachi ou túnel VPN).
* Preencha o IP virtual do host (ex: `26.x.x.x`) com a porta `25588` e toque em **"Salvar e Conectar"**.

---

## 🛡️ Resolução de Problemas: Erro `failed to connect after 3000ms`

Se ao tentar conectar aparecer a mensagem:
> `failed to connect to /192.168.18.20 (port 25588) from /192.168.18.37 after 3000ms`

Isso significa que o **Firewall do Windows no computador está bloqueando conexões externas na porta 25588**.

### Como resolver em 5 segundos:
1. No computador onde o servidor roda, abra a pasta `CanalhasSMP`.
2. Clique com o botão direito no arquivo **`LIBERAR_FIREWALL_APP.bat`** e escolha **"Executar como administrador"** (ou clique no botão **`🛡️ Firewall`** no cabeçalho do Dashboard).
3. Uma tela rápida abrirá liberando as regras de entrada do Firewall.
4. No celular, toque novamente em **"Salvar e Conectar"**. A conexão será estabelecida imediatamente!

---

## 🧭 Visão Detalhada das Abas do App

| Ícone | Aba | Descrição das Funções |
| :---: | :--- | :--- |
| 📈 | **Painel** | Monitora TPS, MSPT, RAM, ping para o Google 8.8.8.8, status do servidor e botões rápidos de backup e save. |
| 📜 | **Mural** | Exibe as notícias oficiais do servidor, novidades da semana e o ranking de horas jogadas de cada player. |
| 🧭 | **Mapa/Radar** | Exibe a lista detalhada de operários e pets no mundo, suas coordenadas (X, Y, Z), dimensões e corações de vida. |
| 💬 | **Chat** | Sala de bate-papo in-game em tempo real. Permite enviar mensagens no chat público como `[Server]`. |
| 💻 | **Terminal** | Console do servidor para execução de comandos OP administrativos diretamente pelo celular. |
| ⚙️ | **Conexão** | Configuração de IP/Porta, diagnóstico do último erro e atalhos rápidos de rede. |

---

## 🛠️ Como Compilar o Código-Fonte do App

Caso você queira alterar a interface ou compilar uma nova versão do APK:

### Requisitos:
* **JDK 17** ou superior instalado.
* **Android Studio** (Koala / Ladybug ou mais recente) ou **Android SDK command-line tools**.

### Passos via Terminal:
1. Abra a pasta `CanalhasMobileApp`:
   ```bash
   cd CanalhasMobileApp
   ```
2. Para compilar o APK em modo Debug:
   ```bash
   ./gradlew assembleDebug
   ```
   *(No Windows: `gradlew.bat assembleDebug`)*
3. O APK gerado estará disponível em:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```
