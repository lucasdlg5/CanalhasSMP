# 🏰 CanalhasSMP — Ecossistema Completo

Bem-vindo ao repositório oficial do ecossistema **CanalhasSMP**! Este projeto reúne o servidor do modpack, o mod exclusivo de inteligência artificial e utilitários (**Senhor Cummins**), o painel de gerenciamento desktop (**CanalhasServerDashboard**) e o aplicativo móvel oficial (**CanalhasMobileApp**).

---

## 📌 Visão Geral das Versões

| Componente | Ambiente / Plataforma | Versão Suportada | Notas |
| :--- | :--- | :--- | :--- |
| **Servidor Minecraft** | Fabric Server Dedicated | **Minecraft 1.20.1** | Fabric Loader `0.16.14` (Java 17/21) |
| **Cliente dos Jogadores** | Prominence II / Fabric Client | **Minecraft 1.20.1** | Compatível com todos os mods do pack |
| **Mod Senhor Cummins** | Fabric Mod (Server-side) | **1.0.0** (Java 17) | Requer chave Gemini API (opcional) |
| **Dashboard de Controle** | Windows Desktop (.NET 10) | **Windows Forms / C#** | Monitoramento de TPS, RAM, Mapa e Chat |
| **Aplicativo Mobile** | Android (Kotlin / Compose) | **Android 8.0+ (API 26+)** | Conexão local Wi-Fi ou Radmin VPN |

---

## 🧩 1. Mod Exclusivo: Senhor Cummins (`/SenhorCummins`)

O **Senhor Cummins** é um mod desenvolvido sob medida para o servidor CanalhasSMP. Ele combina **Inteligência Artificial conversacional via LLM (Google Gemini)**, companheiros/pets vivos no mundo, sistemas cooperativos de economia, teletransporte místico e monitoramento avançado de rede em tempo real.

### 🌟 Principais Mecânicas

* **🐱 Senhor Cummins (O Mascote Felino):**
  * Companheiro felino inteligente que caminha pela base, reage a carinho (botão direito) e à dança do shift.
  * Responde a dúvidas sobre receitas e guias de mods quando perguntado no chat (*"Como faço..."*, *"O que é..."*).
  * Possui 1% de chance a cada carinho de recarregar as energias místicas e resetar o cooldown de teleporte do jogador.
  * **Pochete de Emergência:** Shift + Botão Direito no Cummins abre um inventário portátil de 9 slots.

* **🐾 Sistema de Pets Guardiões Personalizados:**
  * **🐶 Vitória (Fox Paulistinha Tricolor do ArzanLion):** Protege contra monstros com ataques rápidos, late ao farejar perigo nas costas e nunca ataca jogadores ou aliados.
  * **🦇 Juninho (Morcego Guardião do Jodevil):** Voo baixo pelas laterais, sonar sônico que revela monstros na escuridão (*Glowing*) e presenteia com o item místico **Bat Leite** (concede todos os buffs positivos por 15s com zero debuffs).
  * **🐟 Pipira (Magikarp Voador Místico do Kikkawa):** Flutua pelos flancos sem obstruir a visão, com trilha de bolhas e física suave.
  * **🦁 Javidizineo (Leãozinho Real de Azarasquel):** Caminhada mansa, mimos reais ao ser alimentado e proteção feroz com mini-rugidos.
  * **Pausa Unificada de Carinho:** Ao acariciar qualquer pet, ele congela e aproveita o chamego por 12 segundos.

* **📦 Correio da Firma — O Malote do Peugeot 206 (`/malote` e `/enviar`):**
  * Envie itens instantaneamente para qualquer amigo pelo mapa. Se o colega estiver offline ou sem espaço, o item fica guardado com segurança em disco para resgate posterior.

* **🏛️ Almoxarifado Cooperativo (`/almoxarifado` e `/doar`):**
  * Baú duplo compartilhado de 54 slots para estocar recursos comuns de obras e mineração da firma.

* **⚡ Bônus de Mutirão:**
  * Trabalhar junto a colegas em raio de até 28 blocos concede automaticamente Pressa I (*Haste*) e Agilidade I (*Speed*).

* **☕ Pausa para o Café (`/cafe` ou `/cummins cafe`):**
  * Concede o tradicional *Cafézin Preto da Firma* (Visão Noturna, Agilidade, Pressa e saciedade) com 2x *Pão na Chapa do Posto*.

* **📶 Monitor Contínuo de Internet & Ping 8.8.8.8 (`/conexao` ou `/ping`):**
  * O mod realiza pings periódicos para `8.8.8.8` em segundo plano. Ao perguntar no chat (*"como tá a internet do servidor"*, *"o arzan tomou timeout"*), o Cummins informa o ping médio exato em milissegundos e atesta se a oscilação foi do host ou da rota do jogador.

* **🌀 Teleporte Místico Bidirecional (`/cumminstp` ou via Chat):**
  * Peça para ir até um amigo ou para trazê-lo até você com linguagem natural (ex: *"cummins me teleporte pro arzan"*, *"cummins puxa o kikkawa pra cá"*).

* **📜 Mural de Novidades & Ranking de Horas:**
  * Tela visual de boas-vindas com as últimas novidades e ranking oficial de operários mais dedicados (*Playtime*).

---

### 💻 Comandos Disponíveis do Senhor Cummins

#### Comandos para Todos os Jogadores
| Comando | Descrição |
| :--- | :--- |
| `/malote <jogador>` | Envia o item segurado na mão para outro jogador |
| `/enviar <jogador>` | Sinônimo amigável do `/malote` |
| `/malote receber` | Resgata itens que estavam guardados na sua caixa postal |
| `/almoxarifado` | Abre o baú duplo compartilhado da firma (54 slots) |
| `/doar` | Deposita o item da mão diretamente no Almoxarifado |
| `/cafe` | Recebe o Cafézin Preto da Firma e Pão na Chapa noturno |
| `/conexao` ou `/ping` | Exibe o status da internet do host e o ping médio para o 8.8.8.8 |
| `/cumminstp <jogador>` | Solicita teleporte místico até o amigo (cooldown de 1 hora) |
| `/cummins cooldown` | Consulta o tempo restante do seu teleporte místico |
| `/pochete` | Abre o compartimento portátil de emergência do gato |
| `/horas` ou `/ranking` | Exibe o ranking de horas jogadas no servidor |
| `/contrato` | Consulta a tarefa diária da diretoria da firma |
| `/entregar` | Entrega os itens requeridos no contrato e recebe recompensas |
| `/reclamar <alvo> <motivo>` | Abre um chamado formal de reclamação no RH da firma |
| `/levar <ponto>` | Pede para o Cummins te guiar andando até um ponto turístico |
| `/pontos` | Lista os pontos turísticos cadastrados no servidor |

#### Comandos Administrativos (Nível OP / Permissão 2)
| Comando | Descrição |
| :--- | :--- |
| `/cummins status` | Relatório completo de API, modelo, contexto, memória e TPS |
| `/cummins reload` | Recarrega as configurações e sincroniza pastas de backup |
| `/cummins backupmods` | Força a sincronização imediata dos mods em `world/mods_desenvolvidos` |
| `/cummins toggle` | Liga ou desliga as respostas de IA do Cummins no chat |
| `/cummins falar [assunto]` | Força o Cummins a puxar assunto sobre um tema |
| `/cummins dizer <mensagem>` | Faz o Cummins falar uma frase exata no chat |
| `/cummins resetcooldown <jogador>` | Zera o cooldown de teleporte do jogador especificado |
| `/cummins lagreports` | Exibe os últimos relatos de lentidão registrados com coordenadas |
| `/cummins mural [todos\|resetar]` | Abre o Mural de Novidades ou reseta o visto diário |
| `/cummins rh` | Consulta os últimos chamados abertos no livro do RH |
| `/cummins memoria` | Lista as anotações memorizadas pela IA |
| `/cummins esquecer` | Limpa o histórico de memória e contexto da IA |
| `/cummins gato summon` | Invoca o corpo felino do Cummins próximo ao administrador |

---

## 🖥️ 2. CanalhasServerDashboard (`/CanalhasServerDashboard`)

Aplicativo nativo Windows (.NET 10 / Windows Forms) desenvolvido para monitorar e administrar o servidor sem depender de consoles pretos de terminal.

### Recursos:
* **Controle Total:** Iniciar, Parar de forma segura (`stop`), Reiniciar e Forçar Encerramento.
* **Aba de Chat In-Game (Limpo):** Exibe o chat dos jogadores, mensagens do Cummins, conquistas, entradas/saídas e comandos `[CONSOLE COMANDO] > say` em tempo real com cores dedicadas.
* **Aba de Logs do Sistema (Completo):** Terminal com busca/filtro em tempo real, destaque para erros e avisos.
* **Gráficos em Tempo Real:** Curvas de TPS (20.0), MSPT e Consumo de Memória RAM.
* **Mapa do Mundo Integrado:** Visualização aérea dos blocos minerados e construções exploradas.
* **Telemetria de Tokens LLM:** Painel visual com consumo total de tokens, custo estimado e média por requisição.
* **Monitor Host de Ping:** Gráfico contínuo da estabilidade de conexão para o Google DNS (`8.8.8.8`).

---

## 📱 3. CanalhasMobileApp (`/CanalhasMobileApp`)

Aplicativo Android moderno construído em **Kotlin com Jetpack Compose** e arquitetura reativa Material 3.

### Recursos:
* **Conexão Rápida:** Conexão com o servidor via IP Local (Wi-Fi) ou Radmin VPN (`26.x.x.x`).
* **Chat Remoto:** Leia o chat dos jogadores e converse em tempo real enviando mensagens como console.
* **Aba Terminal:** Visualize os últimos logs do console do servidor e envie comandos administrativos direto do smartphone.
* **Mural da Comunidade:** Acesse o Mural de Novidades oficial da firma onde você estiver.
* **Cards de Saúde:** Visualize TPS atual, MSPT, RAM utilizada e ping de conexão do host na palma da mão.

---

## 📁 Estrutura de Diretórios

```
CanalhasSMP/
├── SenhorCummins/              # Código-fonte Java do mod Fabric 1.20.1
│   ├── src/main/java/          # Lógica da IA, pets, teleporte, correio e rede
│   └── build.gradle            # Script de compilação Fabric Loom
├── CanalhasServerDashboard/    # Código-fonte C# do Dashboard Desktop (.NET 10)
│   ├── DashboardForm.cs        # Interface gráfica e abas de monitoramento
│   └── ServerProcessManager.cs # Gerenciador do processo do servidor e bridges
├── CanalhasMobileApp/          # Código-fonte Android em Kotlin (Jetpack Compose)
│   └── app/src/main/java/      # Telas de Chat, Console, Métricas e Mural
├── CanalhasSMP/                # Servidor Minecraft pronto para execução
│   ├── mods/                   # Jar do modpack e senhor-cummins-1.0.0.jar
│   ├── config/                 # Configurações de mods e parâmetros da IA
│   └── server.properties       # Propriedades de rede e mundo
├── INICIAR_PAINEL_CANALHAS.bat # Inicializador de um clique do Dashboard
├── .gitignore                  # Regras de exclusão para mundos e arquivos de build
└── README.md                   # Esta documentação
```

---

## 🚀 Como Compilar os Projetos

### Mod Senhor Cummins
```bash
cd SenhorCummins
./gradlew build -x test
# O arquivo gerado estará em build/libs/senhor-cummins-1.0.0.jar
```

### Dashboard Desktop (.NET)
```bash
cd CanalhasServerDashboard
dotnet build -c Release
dotnet publish -c Release -o publish
# Executável gerado em publish/CanalhasServerDashboard.exe
```

### Aplicativo Mobile Android
```bash
cd CanalhasMobileApp
./gradlew assembleDebug
# O APK gerado estará em app/build/outputs/apk/debug/app-debug.apk
```

---
*Desenvolvido com carinho e dedicação para a firma do **CanalhasSMP**!* 🐾✨
