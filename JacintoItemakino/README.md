# 📦 Jacinto Itemakino Baú

> **Mod Oficial Server-Side para Fabric 1.20.1 — CanalhasSMP**  
> *"Jacinto organizando, minerando e a firma prosperando! Deixa que o Jacinto bota ordem na bagunça e traz os minérios pra base."*

---

## 📖 Visão Geral

O **Jacinto Itemakino Baú** é um NPC operário inteligente, carismático e multifuncional criado para a firma CanalhasSMP. Ele combina três grandes funções:

1. **📦 Organização Inteligente de Baús**: Lê a placa de um baú e varre recipientes vizinhos em 24 blocos, concentrando os itens com **proteção total contra loops infinitos**.
2. **⛏️ Mineração Passiva e Autônoma**: Você pede um item/minério para ele e ele ativa seu **scanner geológico de 32 blocos**, caminha pelo trajeto mais acessível e começa a escavar os veios de minério sem parar!
3. **⚔️ Proteção da Base e Persistência**: Pode ser equipado com quaisquer armas/armaduras de jogador, patrulha a base atacando monstros hostis e **nunca despawna** — graças a um sistema dinâmico de **3x3 Chunks Forçados**, continuando seu trabalho mesmo quando nenhum jogador está por perto! Se morrer, respawna em 5 segundos e retoma a tarefa imediatamente!

---

## 🔄 Fluxos de Funcionamento

### 1. Fluxo de Mineração Passiva Autônoma (`/minerar`)

```mermaid
flowchart TD
    A["⛏️ Jogador digita /minerar diamante (ou ferro, ouro, etc.)"] --> B["💬 Mensagem privada de confirmação ao jogador"]
    B --> C["🔒 Sistema ativa 3x3 Chunks Forçados e Anti-Despawn"]
    C --> D["📡 Scanner geológico varre raio de 32 blocos por veios"]
    D --> E{"Veio de minério encontrado?"}
    E -- "Sim" --> F["🚶 Caminha pelo trajeto aberto mais próximo ao veio"]
    F --> G["⛏️ Bate picareta e escava o minério com efeitos e drops"]
    G --> H["📈 Incrementa estatística e notifica marcos de extração"]
    H --> D
    E -- "Não" --> I["🧭 Prospeção / Strip-Mining em túnel 1x2 na camada ideal"]
    I --> D
    J["☠️ Jacinto morre em combate?"] --> K["⏳ Timer de 5s: Mantém tarefa e equipamentos"]
    K --> L["⚡ Respawn triunfal e volta direta para a mina!"]
    L --> D
    M["🛑 Jogador digita /minerar stop"] --> N["🔓 Libera chunks forçados e volta ao repouso na base"]
```

### 2. Fluxo de Organização de Baús (`/organizar`)

```mermaid
flowchart TD
    A["🪧 Jogador coloca Placa no Baú"] --> B["👀 Jogador olha p/ a Placa e digita /organizar"]
    B --> C["🔍 Jacinto identifica o Baú e o Item"]
    C --> D["💬 Mensagem Privada de Confirmação ao Jogador"]
    D --> E["🚫 Baú Alvo é adicionado à Blacklist (Anti-Loop)"]
    E --> F["📡 Scanner varre baús vizinhos em raio de 24 blocos"]
    F --> G["🚶 Jacinto caminha até cada baú e extrai o item"]
    G --> H["📦 Jacinto deposita tudo no Baú Designado"]
    H --> I{"Baú lotou ou acabaram os itens?"}
    I -- "Sim" --> J["✅ Mensagem de Conclusão com Total de Itens"]
    I -- "/organizar stop" --> K["🛑 Parada Imediata e Confirmação"]
```

---

## ⛏️ Como Usar a Mineração Passiva (Passo a Passo)

### Passo 1: Pedir o Item / Minério Desejado
Digite no chat do jogo o comando `/minerar` seguido do nome do minério (em português, inglês ou ID):
```minecraft
/minerar diamante
```
- **Outros exemplos populares:**
  - `/minerar ferro` (ou `iron`)
  - `/minerar ouro` (ou `gold`)
  - `/minerar carvao` (ou `coal`)
  - `/minerar cobre` (ou `copper`)
  - `/minerar redstone`
  - `/minerar lapis`
  - `/minerar esmeralda`
  - `/minerar ancient_debris` (ou `debris`, `netherite`, `detrito`)
  - `/minerar quartzo` (ou `quartz`)
  - Minérios de mods (ex: `/minerar tin_ore`, `/minerar lead`, etc.).

### Passo 2: Confirmação e Ativação do Scanner
O Jacinto envia uma confirmação privada para você:
> `[Jacinto Itemakino Baú] Opa, patrão Asminn! Iniciando mineração passiva de: [Diamante]! Ligando scanner geológico e fixando carregamento dos chunks da mina...`

### Passo 3: Como o Jacinto Trabalha na Mina
1. **Scanner Geológico:** Varre um cubo de até **32 blocos de raio** procurando por blocos do minério pedido.
2. **Navegação Inteligente:** Antes de quebrar qualquer bloco, ele procura o caminho aberto (chão sólido e ar) mais próximo do minério e caminha até lá.
3. **Escavação Cuidadosa:** Só quebra blocos quando necessário para abrir passagem até o minério, evitando quebrar lava ou cair em abismos.
4. **Prospeção por Túnel (Strip Mining):** Se não houver minérios visíveis no scanner imediato, ele desce com segurança até a altura ideal do minério (ex: Y = -58 para Diamante/Redstone, Y = 14 para Netherite, Y = 16 para Ferro) e escava um túnel 1x2 reto até o scanner apitar um novo veio!
5. **Ferramenta & Encantamentos:** Ele empunha uma picareta (se você der a ele uma picareta com **Fortuna III** ou **Eficiência**, ele usará com todos os bônus!).

### Passo 4: Interromper ou Trocar de Tarefa
- Para encerrar o expediente dele:
  ```minecraft
  /minerar stop
  ```
  Ele desativa os chunks forçados, para de cavar e avisa quantos minérios conseguiu extrair.
- Para trocar o que ele está minerando: basta digitar `/minerar ouro` (ele ajusta o alvo na hora).
- Para fazê-lo organizar baús: digite `/organizar` (ele cancela a mineração e vai para a organização).

---

## 🌐 Tecnologia Anti-Despawn e Chunks Forçados (Trabalho Contínuo a Distância)

No Minecraft vanilla, entidades normais entram em *despawn* ou congelam quando os jogadores se afastam mais de 32 ou 128 blocos, e os chunks são descarregados da memória.

Para resolver isso de forma 100% confiável no Jacinto:
1. **Persistência Total:** O Jacinto recebe flag de persistência permanente (`cannotDespawn`).
2. **Carregamento Dinâmico 3x3 (Forced Chunks):** Enquanto está minerando, o Jacinto mantém um grid de **9 chunks (3x3)** centralizados na posição dele forçados via API nativa de chunks (`setChunkForced`).
3. **Sem Vazamento de Memória:** Conforme o Jacinto avança caminhando pelo subterrâneo, os chunks antigos que ficaram para trás são liberados e os novos são ativados.
4. **Trabalho Sem Jogadores:** Ele continua minerando normalmente mesmo se todos os jogadores saírem do servidor ou estiverem em outra dimensão (Nether / The End)!

---

## 🔁 Persistência Ininterrupta Através de Mortes e Restarts

- **A Tarefa Nunca é Perdida:** A tarefa atual (`MINING`, `ORGANIZING` ou `IDLE`) e o alvo de mineração ficam salvos em `config/jacinto/jacinto_data.json`.
- **Morte em Combate:** Se monstros derrotarem o Jacinto na mina:
  1. Seus itens e picareta **não caem no chão** e ficam salvos em segurança.
  2. Um timer de 5 segundos é disparado.
  3. Ele renasce com 20 HP, equipa suas ferramentas e **volta imediatamente para a coordenada da mina para continuar escavando**!
  4. Manda frase cômica no chat:
     > `[Jacinto Itemakino Baú] Morri mas voltei com sede de minério! Retomando a picareta e a extração de [Diamante] imediatamente! ⛏️🔥`

---

## 📦 Como Usar a Organização de Baús (Passo a Passo)

O Jacinto oferece **3 formas intuitivas** para você definir qual item aquele baú deve receber:

### Opção A: 🖼️ Usando uma Moldura com o Item (Mais Fácil e Prático!)
1. Coloque uma **Moldura** (normal ou brilhante) diretamente na frente, em cima ou ao lado do seu baú.
2. Coloque o **item de referência** dentro da moldura (ex: Andesito, Diamante, Barra de Ferro, etc.).
3. Mire a mira do jogo diretamente na **Moldura** (ou no próprio baú com a moldura) e digite:
   ```minecraft
   /organizar
   ```
4. O Jacinto lê o item exato contido na moldura na hora e traz todos os itens correspondentes dos baús vizinhos!

### Opção B: 🪧 Usando uma Placa com o Nome do Item
1. Coloque uma placa grudada no baú ou no chão em frente a ele.
2. Escreva o nome do item (ex: `Andesito`, `Diamante`, `Ferro`, `Pedra`, etc.).
3. Mire na placa e digite `/organizar`.

### Opção C: 📦 Usando um Item Exemplo dentro do Baú
1. Se você já tiver colocado ao menos um exemplar do item dentro do baú (suporta **Baús Duplos grandes** em qualquer um dos 54 slots).
2. Mire no baú e digite `/organizar`. O Jacinto usará o item do baú como gabarito automático!

---

## 🔒 Permissões e Regras de Baús (Ignorar, Entrada e Saída)

Você pode personalizar individualmente como o Jacinto interage com cada baú da base! Basta olhar diretamente para o baú desejado (ou para a placa/moldura dele) e executar:

| Comando | Pode Colocar (Destino) | Pode Retirar (Origem) | Função Prática |
| :--- | :---: | :---: | :--- |
| `/organizar sim sim` | ✅ **SIM** | ✅ **SIM** | **Fluxo Normal (Padrão)**: Baú livre tanto para receber itens quanto para ter itens retirados. |
| `/organizar sim nao` | ✅ **SIM** | ❌ **NÃO** | **Baú de Depósito**: O Jacinto pode guardar itens aqui, mas **NUNCA** retira nada dele para outros baús! |
| `/organizar nao sim` | ❌ **NÃO** | ✅ **SIM** | **Baú de Despejo (Dump Chest)**: O Jacinto esvazia os itens daqui para a base, mas **NUNCA** guarda nada de volta nele! |
| `/organizar nao nao` | ❌ **NÃO** | ❌ **NÃO** | **Ignorado**: O Jacinto nunca mexe neste baú (mesmo efeito de `/organizar ignore`). |
| `/organizar ignore` | ❌ **NÃO** | ❌ **NÃO** | Atalho direto para fazer o Jacinto ignorar totalmente o baú. |

> [!TIP]
> Se o baú for um **Baú Duplo (Grande)**, a regra configurada se aplica automaticamente a ambas as metades simultaneamente!

---

## ⚔️ Equipamentos, Vida e Defesa da Base

| Característica | Detalhes |
| :--- | :--- |
| **Vida Máxima** | **20 HP** (10 corações normais de jogador). |
| **Aparência** | Humanóide operário (Husk) — não queima sob o sol e renderiza todas as armaduras. |
| **Comportamento Passivo** | Patrulha a base num raio de 16 blocos enquanto ocioso. |
| **Combate** | Ao avistar monstros hostis em até 14 blocos, parte para o ataque com sua arma equipada. |
| **Segurança Absoluta** | **Nunca ataca jogadores**, animais domesticados ou aldeões. |

### 🦺 Como Dar Equipamentos para o Jacinto:
- **Equipar:** Basta **clicar com o botão direito** no Jacinto segurando armadura (Capacete, Peitoral, Calça, Bota), Picareta, Espada ou Escudo.
- **Troca Justa:** Se ele já estiver usando uma peça naquele slot, veste a nova e **devolve a peça antiga para o seu inventário**.
- **Consulta de Status:** Use **Shift + Botão Direito com a mão vazia** para ver a vida dele, equipamentos, tarefa atual e recorde histórico de minérios e baús organizados!

---

## 📜 Lista Completa de Comandos

| Comando | Descrição | Permissão |
| :--- | :--- | :--- |
| `/organizar` | Inicia a organização de baús mirando no baú, placa ou moldura. | Todos |
| `/organizar ignore` | Faz o Jacinto ignorar totalmente o baú mirado (não coloca e não tira). | Todos |
| `/organizar sim sim` | Permite o Jacinto colocar e retirar itens do baú mirado (padrão). | Todos |
| `/organizar sim nao` | Permite o Jacinto colocar itens, mas **nunca retirar** (baú depósito). | Todos |
| `/organizar nao sim` | Permite o Jacinto retirar itens, mas **nunca colocar** (baú despejo/dump). | Todos |
| `/organizar nao nao` | Faz o Jacinto ignorar totalmente o baú mirado (mesmo que `ignore`). | Todos |
| `/organizar stop` | Interrompe imediatamente a organização de baús. | Todos |
| `/organizar status` / `/jacinto status` | Exibe a vida, coordenadas, tarefa e histórico de trabalho do Jacinto. | Todos |
| `/minerar <item>` | Inicia mineração passiva do minério desejado (ex: `/minerar diamante`). | Todos |
| `/minerar stop` / `/minerar parar` | Interrompe a mineração e libera os chunks forçados. | Todos |
| `/minerar status` | Exibe item em mineração, blocos minerados na sessão e chunks forçados. | Todos |
| `/jacinto minerar <item>` | Alias do comando de mineração passiva. | Todos |
| `/jacinto parar` / `/jacinto stop` | Para qualquer tarefa ativa (mineração ou organização). | Todos |
| `/jacinto chamar` | Teleporta o Jacinto imediatamente para a sua posição. | Todos |
| `/jacinto base` | Define a sua localização atual como base de repouso oficial do Jacinto. | Todos |
| `/jacinto respawn` | Força a reinvocação do Jacinto caso necessário. | OP (Nível 2) |

---

## ⚙️ Compilação e Instalação

Mod construído sobre o **Fabric Loom 1.7** para Minecraft **1.20.1** (Java 17):

### 1. Compilação do Código-Fonte:
```bash
# Na pasta JacintoItemakino/:
gradlew.bat build -x test
```
O arquivo JAR é gerado em:
`JacintoItemakino/build/libs/jacinto-itemakino-1.3.0.jar`

### 2. Instalação:
1. Copie o arquivo `jacinto-itemakino-1.3.0.jar` para a pasta `mods/` do servidor.
2. Inicie ou reinicie o servidor.
3. Não requer instalação no cliente dos jogadores (100% server-side).

---

## ☁️ Backup Automático do Servidor

O mod está integrado nativamente ao gerenciador de backup do ecossistema CanalhasSMP ([`ModSourceBackupManager`](../SenhorCummins/src/main/java/com/canalhas/cummins/ModSourceBackupManager.java)):
- A cada 15 minutos e ao encerrar o servidor, todo o código-fonte, scripts Gradle, JARs compilados e dados de persistência do Jacinto são sincronizados para `world/mods_desenvolvidos/` e enviados no `.zip` de backup do mundo.

---

*Desenvolvido com carinho para a firma CanalhasSMP.* 📦⛏️❤️
