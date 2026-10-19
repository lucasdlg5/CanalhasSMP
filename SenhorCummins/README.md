# Senhor Cummins da Silva

NPC anfitrião **server-side** para o CanalhasSMP (Fabric 1.20.1). Fala no chat como se fosse um jogador, usando uma LLM (qualquer API compatível com OpenAI). Zoa quem morre, puxa assunto quando o chat fica parado, lembra das piadas internas e pode ler o Discord pra usar como contexto.

Os jogadores **não precisam instalar nada**.

## Instalação
1. Copie `build/libs/senhor-cummins-1.0.0.jar` para `CanalhasSMP/mods/`.
2. Inicie o servidor uma vez — ele cria `config/senhor-cummins/config.json`.
3. Coloque sua chave em `api.apiKey` e rode `cummins reload` no console.

## Provedores de LLM (campo `api`)
| Provedor | `baseUrl` | `model` (exemplo) | `extraBody` |
|---|---|---|---|
| Google Gemini (tem plano grátis) | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-flash` | `{"reasoning_effort":"none"}` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | `{}` |
| Groq (tem plano grátis) | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` | `{}` |
| OpenRouter | `https://openrouter.ai/api/v1` | qualquer um do site | `{}` |
| Ollama (local, sem chave) | `http://localhost:11434/v1` | `llama3.1` | `{}` |

## Quando ele fala (`chances`, de 0.0 a 1.0)
- `mention`: alguém escreve o nome dele (`triggerWords`).
- `conversation`: a pessoa com quem ele falou nos últimos `conversationWindowSeconds` responde.
- `solo` / `group`: mensagem qualquer com 1 jogador / 2+ jogadores online.
- `death`, `join`, `advancement`: mortes, entradas e conquistas.
- `idleSolo` / `idleGroup`: puxar assunto depois de `idleMinutesSolo` / `idleMinutesGroup` sem chat.

## Memória
A cada `memoryUpdateEveryEvents` mensagens, ele resume o chat em anotações (piadas internas, apelidos, quem morreu pra quê...) salvas em `config/senhor-cummins/memoria.json`. Elas entram em todos os prompts.

## Discord (opcional)
1. https://discord.com/developers/applications → New Application → **Bot** → *Reset Token* (copie o token).
2. Na mesma página, ative **Message Content Intent**.
3. OAuth2 → URL Generator → escopo `bot`, permissões *View Channels* + *Read Message History* (+ *Send Messages* se for usar o relay). Abra a URL e adicione o bot ao seu servidor.
4. No Discord: Configurações → Avançado → **Modo Desenvolvedor**. Botão direito no canal → *Copiar ID*.
5. No config: `discord.enabled: true`, `botToken`, `channelIds: ["123..."]`. Opcional: `relayChannelId` para ele postar no Discord o que fala no jogo.
6. `cummins reload`.

## Comandos (OP nível 2 ou console, sem a `/` no console)
- `/cummins status`: estado, modelo, memória, Discord, último erro.
- `/cummins reload`: recarrega o config.
- `/cummins toggle`: liga/desliga (mudo).
- `/cummins falar [assunto]`: força ele a puxar assunto.
- `/cummins memoria`: lista as anotações. `/cummins lembrar`: força a atualização da memória. `/cummins esquecer`: apaga tudo.

## Compilar
```
gradlew build
```
(JDK 17.) O jar sai em `build/libs/`.
