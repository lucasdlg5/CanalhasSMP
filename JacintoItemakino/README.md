# 📦 Jacinto Itemakino Baú

Mod independente server-side para Fabric 1.20.1 (CanalhasSMP).
Adiciona o NPC **Jacinto Itemakino Baú**, um operário fiel e carismático que organiza os baús da base lendo placas, defende a redondeza contra monstros e equipa armas e armaduras de jogadores.

---

## 🚀 Como Usar a Organização:
1. Coloque uma placa na frente, em cima ou grudada no baú onde você deseja concentrar os itens.
2. Escreva o nome do item na placa (ex: `Diamante`, `Ferro`, `Pedra`, `Carvão`, `raw_iron`, etc.).
   *(Se a placa estiver vazia ou com apelido, mas o baú já tiver itens dentro, o Jacinto identificará o item automaticamente pelo conteúdo!)*
3. Mire na placa e digite:
   ```
   /organizar
   ```
4. O Jacinto mandará uma mensagem privada confirmando o item identificado e varrerá todos os outros baús e barris em até **24 blocos**, levando tudo para o baú designado.
5. **Sem loops:** O baú de destino (e a outra metade se for baú duplo) é estritamente ignorado na busca de materiais!

---

## 🎮 Comandos Disponíveis:
- `/organizar` - Inicia a organização olhando para a placa ou baú.
- `/organizar stop` (ou `/organizar parar`) - Cancela a organização imediatamente no meio do caminho.
- `/organizar status` (ou `/jacinto status`) - Mostra a vida do Jacinto (20 HP), localização e total de itens guardados na carreira.
- `/organizar chamar` (ou `/jacinto chamar`) - Teleporta o Jacinto para perto do jogador.
- `/jacinto base` - Define a base oficial de respawn do Jacinto onde o jogador estiver.

---

## 🛡️ Equipamentos e Defesa:
- **Equipar Armaduras e Armas:** Clique com o botão direito no Jacinto segurando qualquer peça de armadura (Capacete, Peitoril, Calça, Bota), Espada, Machado ou Escudo. Ele equipará o item imediatamente e devolverá o anterior para você.
- **Proteção Total:** As armaduras e armas dadas ao Jacinto ficam salvas em memória e **nunca dropam nem são perdidas**.
- **Combate Passivo:** Quando o Jacinto está ocioso, patrulha a base e ataca monstros hostis próximos com o equipamento que você deu a ele. Ele **nunca** machuca jogadores, aldeões ou animais pacíficos.
- **Vida e Respawn:** Possui 20 HP (10 corações de player). Se for derrotado em combate, ele renasce 5 segundos depois na base com vida cheia e todas as suas armaduras restauradas, soltando uma frase bem humorada no chat global!
