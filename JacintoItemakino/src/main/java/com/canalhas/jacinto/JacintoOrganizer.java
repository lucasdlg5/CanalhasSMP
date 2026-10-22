package com.canalhas.jacinto;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.*;

/**
 * Motor de Organização Inteligente de Baús do Jacinto:
 * - Lê a placa indicada pelo jogador
 * - Identifica o baú de destino e o item a ser agrupado
 * - Varrer os baús vizinhos em raio de 24 blocos
 * - PREVENÇÃO TOTAL DE LOOP: O baú de destino (e sua metade dupla) é estritamente ignorado na busca
 * - Suporta parada instantânea via /organizar stop
 */
public class JacintoOrganizer {
    private static final Logger LOG = LoggerFactory.getLogger("JacintoOrganizer");
    private final JacintoMod mod;

    // Estado da organização atual
    private boolean isBusy = false;
    private ServerPlayerEntity requestingPlayer = null;
    private UUID requestingPlayerUuid = null;
    private ServerWorld world = null;
    private BlockPos targetChestPos = null;
    private final Set<BlockPos> excludedChestPositions = new HashSet<>();
    private Item targetItem = null;
    private String targetItemDisplayName = "";

    // Fila de baús de origem para visitar
    private final List<BlockPos> sourceChests = new ArrayList<>();
    private int currentChestIndex = 0;
    private int totalTransferred = 0;
    private int workCooldown = 0;

    public JacintoOrganizer(JacintoMod mod) {
        this.mod = mod;
    }

    public boolean isBusy() {
        return isBusy;
    }

    /**
     * Tenta iniciar o processo de organização a partir do que o jogador está olhando
     */
    public boolean tryStartOrganizing(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) return false;

        if (isBusy) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cCalma lá, patrão! Eu já estou no meio de um frete. Use §e/organizar stop§c se quiser que eu pare o que estou fazendo!"), false);
            return false;
        }

        // Se estava minerando, interrompe mineração para focar nos baús
        if (mod.getMiner().isMining()) {
            mod.getMiner().stopMining(player);
        }

        HitResult hit = player.raycast(6.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, olhe para uma placa na frente ou no baú que deseja organizar e tente novamente!"), false);
            return false;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos hitPos = blockHit.getBlockPos();
        ServerWorld serverWorld = player.getServerWorld();
        BlockEntity hitBe = serverWorld.getBlockEntity(hitPos);

        BlockPos foundChestPos = null;
        List<String> rawLines = new ArrayList<>();

        // 1. Caso tenha mirado diretamente na Placa
        if (hitBe instanceof SignBlockEntity signBe) {
            collectSignLines(signBe, rawLines);
            foundChestPos = findAttachedChest(serverWorld, hitPos);
        }
        // 2. Caso tenha mirado diretamente no Baú que contém a placa
        else if (hitBe instanceof Inventory) {
            foundChestPos = hitPos;
            SignBlockEntity nearbySign = findNearbySign(serverWorld, hitPos);
            if (nearbySign != null) {
                collectSignLines(nearbySign, rawLines);
            }
        }

        if (foundChestPos == null) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão encontrei nenhum baú ou barril conectado a esta placa!"), false);
            return false;
        }

        // Tenta descobrir o item alvo através do texto da placa ou do conteúdo existente no baú
        Item identifiedItem = identifyItemFromSignOrChest(serverWorld, foundChestPos, rawLines);
        if (identifiedItem == null || identifiedItem == Items.AIR) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão consegui identificar qual item guardar nesta placa! Escreva o nome do item na placa (ex: §eDiamante§c, §eFerro§c, §ePedra§c) ou coloque ao menos um exemplar dentro do baú."), false);
            return false;
        }

        // Garante que o Jacinto exista e esteja vivo no mundo
        HuskEntity jacinto = mod.getNpc().getEntity();
        if (jacinto == null) {
            jacinto = mod.getNpc().spawnOrRespawn(player.getServer(), false);
        }
        if (jacinto == null) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão consegui me materializar na firma! Tente novamente."), false);
            return false;
        }

        // Configura o alvo e as exclusões de loop
        this.isBusy = true;
        mod.getData().setOrganizingTask();
        this.requestingPlayer = player;
        this.requestingPlayerUuid = player.getUuid();
        this.world = serverWorld;
        this.targetChestPos = foundChestPos;
        this.targetItem = identifiedItem;
        this.targetItemDisplayName = new ItemStack(identifiedItem).getName().getString();
        this.totalTransferred = 0;
        this.currentChestIndex = 0;
        this.sourceChests.clear();
        this.excludedChestPositions.clear();

        // ADICIONA O BAÚ ALVO À LISTA DE EXCLUSÃO (LOOP PREVENTION)
        this.excludedChestPositions.add(foundChestPos);
        BlockPos otherHalf = getDoubleChestOtherHalf(serverWorld, foundChestPos);
        if (otherHalf != null) {
            this.excludedChestPositions.add(otherHalf);
        }

        // Varrer recipientes vizinhos em um raio de 24 blocos
        scanSourceChests(serverWorld, foundChestPos, 24);

        // Confirmação para o jogador
        player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, patrão §b" + player.getGameProfile().getName() +
                "§f! Identifiquei o item: §b§l[" + targetItemDisplayName + "]§f.\n§7Revistando " + sourceChests.size() +
                " baús vizinhos para concentrar tudo aqui..."), false);

        if (sourceChests.isEmpty()) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §eNão encontrei nenhum outro baú num raio de 24 blocos que contenha §b[" + targetItemDisplayName + "]§e!"), false);
            this.isBusy = false;
            return true;
        }

        // Move o Jacinto até o primeiro baú de origem
        BlockPos first = sourceChests.get(0);
        jacinto.getNavigation().startMovingTo(first.getX(), first.getY(), first.getZ(), 1.25);
        this.workCooldown = 10;
        return true;
    }

    /**
     * Loop de trabalho executado a cada tick pelo JacintoMod
     */
    public void onServerTick() {
        if (!isBusy || world == null || targetChestPos == null) return;

        if (--workCooldown > 0) return;

        HuskEntity jacinto = mod.getNpc().getEntity();
        if (jacinto == null || !jacinto.isAlive()) {
            // Se o Jacinto morreu enquanto trabalhava, aguarda o respawn
            return;
        }

        // Se checamos todos os baús da lista, finaliza com sucesso!
        if (currentChestIndex >= sourceChests.size()) {
            finishOrganizing(false, null);
            return;
        }

        BlockPos sourcePos = sourceChests.get(currentChestIndex);
        BlockEntity sourceBe = world.getBlockEntity(sourcePos);
        BlockEntity targetBe = world.getBlockEntity(targetChestPos);

        if (!(targetBe instanceof Inventory targetInv)) {
            finishOrganizing(true, "O baú de destino foi removido ou destruído!");
            return;
        }

        if (!(sourceBe instanceof Inventory sourceInv)) {
            // Baú de origem sumiu, pula para o próximo
            currentChestIndex++;
            workCooldown = 5;
            return;
        }

        // Caminha até o baú de origem se estiver longe
        double distSq = jacinto.squaredDistanceTo(Vec3d.ofCenter(sourcePos));
        if (distSq > 3.5 * 3.5) {
            jacinto.getNavigation().startMovingTo(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ(), 1.25);
            workCooldown = 15;
            return;
        }

        // Chegou ao baú de origem! Efeito sonoro e partículas
        world.playSound(null, sourcePos.getX(), sourcePos.getY(), sourcePos.getZ(),
                SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f);
        world.spawnParticles(ParticleTypes.CRIT, sourcePos.getX() + 0.5, sourcePos.getY() + 0.8, sourcePos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.05);

        // Extrai o item do baú de origem e deposita no baú de destino
        int movedInThisChest = transferMatchingItems(sourceInv, targetInv, targetItem);
        totalTransferred += movedInThisChest;

        // Efeito sonoro no destino
        if (movedInThisChest > 0) {
            world.playSound(null, targetChestPos.getX(), targetChestPos.getY(), targetChestPos.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.2f);
        }

        // Verifica se o baú alvo lotou
        if (isInventoryFullOfItem(targetInv, targetItem)) {
            finishOrganizing(true, "O baú de destino lotou até a boca! Não cabe mais nada.");
            return;
        }

        // Avança para o próximo baú de origem
        currentChestIndex++;
        if (currentChestIndex < sourceChests.size()) {
            BlockPos nextPos = sourceChests.get(currentChestIndex);
            jacinto.getNavigation().startMovingTo(nextPos.getX(), nextPos.getY(), nextPos.getZ(), 1.25);
            workCooldown = 15;
        } else {
            finishOrganizing(false, null);
        }
    }

    /**
     * Interrompe a organização imediatamente
     */
    public boolean stopOrganizing(ServerPlayerEntity player) {
        if (!isBusy) {
            if (player != null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §7Não há nenhuma organização em andamento no momento."), false);
            }
            return false;
        }

        this.isBusy = false;
        mod.getData().setIdleTask();
        HuskEntity jacinto = mod.getNpc().getEntity();
        if (jacinto != null) {
            jacinto.getNavigation().stop();
        }

        if (player != null) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cOpa, freio de mão puxado! Organização cancelada a pedido do patrão.\n§7(Foram guardados " + totalTransferred + " itens até agora)."), false);
        }
        return true;
    }

    private void finishOrganizing(boolean interrupted, String reason) {
        this.isBusy = false;
        mod.getData().setIdleTask();
        HuskEntity jacinto = mod.getNpc().getEntity();
        if (jacinto != null) {
            jacinto.getNavigation().stop();
        }

        // Atualiza estatísticas totais de carreira do Jacinto
        mod.getData().totalItemsOrganized += totalTransferred;
        mod.getData().save();

        ServerPlayerEntity p = requestingPlayer;
        if (p == null && requestingPlayerUuid != null && world != null) {
            p = world.getServer().getPlayerManager().getPlayer(requestingPlayerUuid);
        }

        if (p != null && p.isAlive()) {
            if (interrupted && reason != null) {
                p.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §eAviso: " + reason + " Foram organizados §b" + totalTransferred + " itens§e."), false);
            } else {
                p.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aTudo nos trinques, patrão! Organização concluída com sucesso.\n§fForam guardados §b§l" +
                        totalTransferred + "x [" + targetItemDisplayName + "]§a no baú designado!"), false);
            }
        }
    }

    /**
     * Transfere itens do tipo selecionado do inventário de origem para o de destino
     */
    private int transferMatchingItems(Inventory source, Inventory target, Item matchItem) {
        int movedCount = 0;
        for (int i = 0; i < source.size(); i++) {
            ItemStack stack = source.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == matchItem) {
                int countToMove = stack.getCount();
                ItemStack remaining = insertStack(target, stack.copy());
                int moved = countToMove - remaining.getCount();

                if (moved > 0) {
                    movedCount += moved;
                    if (remaining.isEmpty()) {
                        source.setStack(i, ItemStack.EMPTY);
                    } else {
                        source.setStack(i, remaining);
                    }
                    source.markDirty();
                    target.markDirty();
                }

                if (!remaining.isEmpty()) {
                    // O baú de destino lotou
                    break;
                }
            }
        }
        return movedCount;
    }

    private ItemStack insertStack(Inventory inv, ItemStack stack) {
        // Primeiro tenta empilhar em slots existentes com o mesmo item
        for (int i = 0; i < inv.size(); i++) {
            ItemStack cur = inv.getStack(i);
            if (!cur.isEmpty() && ItemStack.canCombine(cur, stack)) {
                int max = Math.min(inv.getMaxCountPerStack(), cur.getMaxCount());
                int space = max - cur.getCount();
                if (space > 0) {
                    int toAdd = Math.min(space, stack.getCount());
                    cur.increment(toAdd);
                    stack.decrement(toAdd);
                    inv.markDirty();
                    if (stack.isEmpty()) return ItemStack.EMPTY;
                }
            }
        }

        // Depois tenta colocar em slots vazios
        for (int i = 0; i < inv.size(); i++) {
            ItemStack cur = inv.getStack(i);
            if (cur.isEmpty()) {
                int max = Math.min(inv.getMaxCountPerStack(), stack.getMaxCount());
                if (stack.getCount() <= max) {
                    inv.setStack(i, stack.copy());
                    stack.setCount(0);
                    inv.markDirty();
                    return ItemStack.EMPTY;
                } else {
                    ItemStack split = stack.split(max);
                    inv.setStack(i, split);
                    inv.markDirty();
                }
            }
        }
        return stack;
    }

    private boolean isInventoryFullOfItem(Inventory inv, Item item) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack cur = inv.getStack(i);
            if (cur.isEmpty()) return false;
            if (cur.getItem() == item && cur.getCount() < Math.min(inv.getMaxCountPerStack(), cur.getMaxCount())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Varrer blocos de inventário no raio de ação, excluindo o baú de destino
     */
    private void scanSourceChests(ServerWorld world, BlockPos center, int radius) {
        sourceChests.clear();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (excludedChestPositions.contains(p)) continue;

                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof Inventory inv) {
                        // Verifica se este baú possui o item desejado
                        if (containsItem(inv, targetItem)) {
                            sourceChests.add(p);
                        }
                    }
                }
            }
        }
    }

    private boolean containsItem(Inventory inv, Item item) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    private BlockPos getDoubleChestOtherHalf(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction otherDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                return pos.offset(otherDir);
            }
        }
        return null;
    }

    private BlockPos findAttachedChest(ServerWorld world, BlockPos signPos) {
        BlockState signState = world.getBlockState(signPos);

        // Se for placa de parede, o bloco atrás dela é o principal candidato
        if (signState.getBlock() instanceof WallSignBlock) {
            Direction backDir = signState.get(WallSignBlock.FACING).getOpposite();
            BlockPos backPos = signPos.offset(backDir);
            if (world.getBlockEntity(backPos) instanceof Inventory) {
                return backPos;
            }
        }

        // Checa todas as 6 direções vizinhas (baixo, cima, lados)
        for (Direction dir : Direction.values()) {
            BlockPos p = signPos.offset(dir);
            if (world.getBlockEntity(p) instanceof Inventory) {
                return p;
            }
        }
        return null;
    }

    private SignBlockEntity findNearbySign(ServerWorld world, BlockPos chestPos) {
        for (Direction dir : Direction.values()) {
            BlockPos p = chestPos.offset(dir);
            if (world.getBlockEntity(p) instanceof SignBlockEntity signBe) {
                return signBe;
            }
        }
        return null;
    }

    private void collectSignLines(SignBlockEntity sign, List<String> out) {
        SignText front = sign.getFrontText();
        SignText back = sign.getBackText();
        for (int i = 0; i < 4; i++) {
            String f = front.getMessage(i, false).getString().trim();
            if (!f.isBlank()) out.add(f);
            String b = back.getMessage(i, false).getString().trim();
            if (!b.isBlank()) out.add(b);
        }
    }

    /**
     * Identifica o item pretendido a partir dos textos da placa ou do baú
     */
    private Item identifyItemFromSignOrChest(ServerWorld world, BlockPos chestPos, List<String> rawLines) {
        // 1. Tenta identificar diretamente a partir dos textos da placa
        for (String line : rawLines) {
            Item match = matchItemName(line);
            if (match != null && match != Items.AIR) {
                return match;
            }
        }

        // 2. Se a placa não casou diretamente, mas o baú já tem itens dentro,
        // verifica se algum item do baú bate com as palavras da placa
        BlockEntity be = world.getBlockEntity(chestPos);
        if (be instanceof Inventory inv) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty()) {
                    String itemName = s.getName().getString().toLowerCase(Locale.ROOT);
                    String itemPath = Registries.ITEM.getId(s.getItem()).getPath().toLowerCase(Locale.ROOT);
                    for (String line : rawLines) {
                        String cleanLine = normalize(line);
                        if (cleanLine.contains(normalize(itemName)) || normalize(itemName).contains(cleanLine)
                                || cleanLine.contains(itemPath) || itemPath.contains(cleanLine)) {
                            return s.getItem();
                        }
                    }
                }
            }

            // 3. Se a placa estava em branco ou com texto decorativo, mas o baú tem itens, pega o primeiro item do baú
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty()) {
                    return s.getItem();
                }
            }
        }

        return null;
    }

    private Item matchItemName(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = normalize(input);

        // Dicionário comum de minérios e itens em Português
        Map<String, Item> dict = new HashMap<>();
        dict.put("diamante", Items.DIAMOND);
        dict.put("bloco de diamante", Items.DIAMOND_BLOCK);
        dict.put("ferro", Items.IRON_INGOT);
        dict.put("barra de ferro", Items.IRON_INGOT);
        dict.put("ferro bruto", Items.RAW_IRON);
        dict.put("bloco de ferro", Items.IRON_BLOCK);
        dict.put("ouro", Items.GOLD_INGOT);
        dict.put("barra de ouro", Items.GOLD_INGOT);
        dict.put("ouro bruto", Items.RAW_GOLD);
        dict.put("cobre", Items.COPPER_INGOT);
        dict.put("barra de cobre", Items.COPPER_INGOT);
        dict.put("cobre bruto", Items.RAW_COPPER);
        dict.put("carvao", Items.COAL);
        dict.put("redstone", Items.REDSTONE);
        dict.put("lapis", Items.LAPIS_LAZULI);
        dict.put("lapis lazuli", Items.LAPIS_LAZULI);
        dict.put("esmeralda", Items.EMERALD);
        dict.put("netherita", Items.NETHERITE_INGOT);
        dict.put("barra de netherita", Items.NETHERITE_INGOT);
        dict.put("quartzo", Items.QUARTZ);
        dict.put("pedra", Items.STONE);
        dict.put("pedregulho", Items.COBBLESTONE);
        dict.put("terra", Items.DIRT);
        dict.put("areia", Items.SAND);
        dict.put("cascalho", Items.GRAVEL);
        dict.put("madeira", Items.OAK_LOG);
        dict.put("tronco de carvalho", Items.OAK_LOG);
        dict.put("tabuas de carvalho", Items.OAK_PLANKS);
        dict.put("vidro", Items.GLASS);
        dict.put("ossos", Items.BONE);
        dict.put("osso", Items.BONE);
        dict.put("linha", Items.STRING);
        dict.put("polvora", Items.GUNPOWDER);
        dict.put("trigo", Items.WHEAT);
        dict.put("cenoura", Items.CARROT);
        dict.put("batata", Items.POTATO);
        dict.put("peixe", Items.COD);
        dict.put("carne", Items.BEEF);

        for (Map.Entry<String, Item> entry : dict.entrySet()) {
            if (clean.equals(entry.getKey()) || clean.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Tenta achar diretamente pelo ID de registro do Minecraft
        for (Item it : Registries.ITEM) {
            String path = Registries.ITEM.getId(it).getPath().replace("_", " ");
            if (clean.equals(path) || clean.contains(path)) {
                return it;
            }
            String ptName = normalize(new ItemStack(it).getName().getString());
            if (clean.equals(ptName) || clean.contains(ptName)) {
                return it;
            }
        }
        return null;
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
    }
}
