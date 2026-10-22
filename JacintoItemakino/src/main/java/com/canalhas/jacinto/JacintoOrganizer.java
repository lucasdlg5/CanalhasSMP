package com.canalhas.jacinto;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Motor de Organização Inteligente de Baús do Jacinto:
 * - Lê a placa ou examina o baú indicado pelo jogador
 * - Suporta identificação em Português, Inglês e por item exemplo dentro do baú (incluindo Baús Duplos)
 * - Identificação resiliente a pequenas variações (ex: Andesito -> Andesite) sem colisões com substrings curtas
 * - Varre os baús vizinhos em raio de 24 blocos
 * - PREVENÇÃO TOTAL DE LOOP: O baú de destino (e ambas as metades de baús duplos) é estritamente ignorado na busca
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

    // Dicionário ampliado Português -> Item
    private static final Map<String, Item> PT_DICT = new HashMap<>();

    static {
        // Pedras, Rochas e Construção
        PT_DICT.put("andesito", Items.ANDESITE);
        PT_DICT.put("andesito polido", Items.POLISHED_ANDESITE);
        PT_DICT.put("granito", Items.GRANITE);
        PT_DICT.put("granito polido", Items.POLISHED_GRANITE);
        PT_DICT.put("diorito", Items.DIORITE);
        PT_DICT.put("diorito polido", Items.POLISHED_DIORITE);
        PT_DICT.put("pedra", Items.STONE);
        PT_DICT.put("pedra lisa", Items.SMOOTH_STONE);
        PT_DICT.put("pedregulho", Items.COBBLESTONE);
        PT_DICT.put("pedregulho com musgo", Items.MOSSY_COBBLESTONE);
        PT_DICT.put("tijolos de pedra", Items.STONE_BRICKS);
        PT_DICT.put("tijolo", Items.BRICKS);
        PT_DICT.put("tijolos", Items.BRICKS);
        PT_DICT.put("terra", Items.DIRT);
        PT_DICT.put("terra arada", Items.FARMLAND);
        PT_DICT.put("grama", Items.GRASS_BLOCK);
        PT_DICT.put("areia", Items.SAND);
        PT_DICT.put("areia vermelha", Items.RED_SAND);
        PT_DICT.put("arenito", Items.SANDSTONE);
        PT_DICT.put("cascalho", Items.GRAVEL);
        PT_DICT.put("argila", Items.CLAY);
        PT_DICT.put("basalto", Items.BASALT);
        PT_DICT.put("basalto polido", Items.POLISHED_BASALT);
        PT_DICT.put("tufo", Items.TUFF);
        PT_DICT.put("ardosia", Items.DEEPSLATE);
        PT_DICT.put("ardosia polida", Items.POLISHED_DEEPSLATE);
        PT_DICT.put("calcario", Items.CALCITE);
        PT_DICT.put("pedra negra", Items.BLACKSTONE);
        PT_DICT.put("pedra negra polida", Items.POLISHED_BLACKSTONE);
        PT_DICT.put("obsidiana", Items.OBSIDIAN);
        PT_DICT.put("pedra luminosa", Items.GLOWSTONE);
        PT_DICT.put("vidro", Items.GLASS);
        PT_DICT.put("painel de vidro", Items.GLASS_PANE);
        PT_DICT.put("gelo", Items.ICE);
        PT_DICT.put("gelo compactado", Items.PACKED_ICE);
        PT_DICT.put("gelo azul", Items.BLUE_ICE);
        PT_DICT.put("neve", Items.SNOW_BLOCK);

        // Minérios e Metais
        PT_DICT.put("diamante", Items.DIAMOND);
        PT_DICT.put("bloco de diamante", Items.DIAMOND_BLOCK);
        PT_DICT.put("ferro", Items.IRON_INGOT);
        PT_DICT.put("barra de ferro", Items.IRON_INGOT);
        PT_DICT.put("ferro bruto", Items.RAW_IRON);
        PT_DICT.put("bloco de ferro", Items.IRON_BLOCK);
        PT_DICT.put("pepita de ferro", Items.IRON_NUGGET);
        PT_DICT.put("ouro", Items.GOLD_INGOT);
        PT_DICT.put("barra de ouro", Items.GOLD_INGOT);
        PT_DICT.put("ouro bruto", Items.RAW_GOLD);
        PT_DICT.put("bloco de ouro", Items.GOLD_BLOCK);
        PT_DICT.put("pepita de ouro", Items.GOLD_NUGGET);
        PT_DICT.put("cobre", Items.COPPER_INGOT);
        PT_DICT.put("barra de cobre", Items.COPPER_INGOT);
        PT_DICT.put("cobre bruto", Items.RAW_COPPER);
        PT_DICT.put("bloco de cobre", Items.COPPER_BLOCK);
        PT_DICT.put("carvao", Items.COAL);
        PT_DICT.put("bloco de carvao", Items.COAL_BLOCK);
        PT_DICT.put("carvao vegetal", Items.CHARCOAL);
        PT_DICT.put("redstone", Items.REDSTONE);
        PT_DICT.put("po de redstone", Items.REDSTONE);
        PT_DICT.put("bloco de redstone", Items.REDSTONE_BLOCK);
        PT_DICT.put("lapis", Items.LAPIS_LAZULI);
        PT_DICT.put("lapis lazuli", Items.LAPIS_LAZULI);
        PT_DICT.put("bloco de lapis", Items.LAPIS_BLOCK);
        PT_DICT.put("esmeralda", Items.EMERALD);
        PT_DICT.put("bloco de esmeralda", Items.EMERALD_BLOCK);
        PT_DICT.put("netherita", Items.NETHERITE_INGOT);
        PT_DICT.put("barra de netherita", Items.NETHERITE_INGOT);
        PT_DICT.put("detritos ancestrais", Items.ANCIENT_DEBRIS);
        PT_DICT.put("sucata de netherita", Items.NETHERITE_SCRAP);
        PT_DICT.put("quartzo", Items.QUARTZ);
        PT_DICT.put("bloco de quartzo", Items.QUARTZ_BLOCK);
        PT_DICT.put("ametista", Items.AMETHYST_SHARD);

        // Madeiras e Vegetação
        PT_DICT.put("madeira", Items.OAK_LOG);
        PT_DICT.put("tronco de carvalho", Items.OAK_LOG);
        PT_DICT.put("tabuas de carvalho", Items.OAK_PLANKS);
        PT_DICT.put("carvalho", Items.OAK_PLANKS);
        PT_DICT.put("pinheiro", Items.SPRUCE_PLANKS);
        PT_DICT.put("tronco de pinheiro", Items.SPRUCE_LOG);
        PT_DICT.put("eucalipto", Items.BIRCH_PLANKS);
        PT_DICT.put("tronco de eucalipto", Items.BIRCH_LOG);
        PT_DICT.put("selva", Items.JUNGLE_PLANKS);
        PT_DICT.put("tronco de selva", Items.JUNGLE_LOG);
        PT_DICT.put("acacia", Items.ACACIA_PLANKS);
        PT_DICT.put("tronco de acacia", Items.ACACIA_LOG);
        PT_DICT.put("cerejeira", Items.CHERRY_PLANKS);
        PT_DICT.put("tronco de cerejeira", Items.CHERRY_LOG);
        PT_DICT.put("mangue", Items.MANGROVE_PLANKS);
        PT_DICT.put("tronco de mangue", Items.MANGROVE_LOG);
        PT_DICT.put("bambu", Items.BAMBOO);
        PT_DICT.put("graveto", Items.STICK);
        PT_DICT.put("gravetos", Items.STICK);

        // Drops de Mobs e Fazenda
        PT_DICT.put("osso", Items.BONE);
        PT_DICT.put("ossos", Items.BONE);
        PT_DICT.put("farinha de osso", Items.BONE_MEAL);
        PT_DICT.put("linha", Items.STRING);
        PT_DICT.put("polvora", Items.GUNPOWDER);
        PT_DICT.put("pena", Items.FEATHER);
        PT_DICT.put("penas", Items.FEATHER);
        PT_DICT.put("couro", Items.LEATHER);
        PT_DICT.put("carne podre", Items.ROTTEN_FLESH);
        PT_DICT.put("teia", Items.COBWEB);
        PT_DICT.put("olho de aranha", Items.SPIDER_EYE);
        PT_DICT.put("perola do end", Items.ENDER_PEARL);
        PT_DICT.put("vara de blaze", Items.BLAZE_ROD);
        PT_DICT.put("po de blaze", Items.BLAZE_POWDER);
        PT_DICT.put("lagrima de ghast", Items.GHAST_TEAR);
        PT_DICT.put("slime", Items.SLIME_BALL);
        PT_DICT.put("bola de slime", Items.SLIME_BALL);
        PT_DICT.put("bloco de slime", Items.SLIME_BLOCK);
        PT_DICT.put("trigo", Items.WHEAT);
        PT_DICT.put("semente de trigo", Items.WHEAT_SEEDS);
        PT_DICT.put("sementes", Items.WHEAT_SEEDS);
        PT_DICT.put("cenoura", Items.CARROT);
        PT_DICT.put("batata", Items.POTATO);
        PT_DICT.put("beterraba", Items.BEETROOT);
        PT_DICT.put("cana de acucar", Items.SUGAR_CANE);
        PT_DICT.put("acucar", Items.SUGAR);
        PT_DICT.put("cacau", Items.COCOA_BEANS);
        PT_DICT.put("melancia", Items.MELON_SLICE);
        PT_DICT.put("abobora", Items.PUMPKIN);
        PT_DICT.put("maca", Items.APPLE);
        PT_DICT.put("pao", Items.BREAD);
        PT_DICT.put("carne", Items.BEEF);
        PT_DICT.put("bife", Items.COOKED_BEEF);
        PT_DICT.put("frango", Items.CHICKEN);
        PT_DICT.put("frango assado", Items.COOKED_CHICKEN);
        PT_DICT.put("porco", Items.PORKCHOP);
        PT_DICT.put("peixe", Items.COD);
        PT_DICT.put("salmao", Items.SALMON);
        PT_DICT.put("tocha", Items.TORCH);
        PT_DICT.put("flecha", Items.ARROW);
        PT_DICT.put("arco", Items.BOW);
        PT_DICT.put("livro", Items.BOOK);
        PT_DICT.put("papel", Items.PAPER);
        PT_DICT.put("balde", Items.BUCKET);
        PT_DICT.put("frasco", Items.GLASS_BOTTLE);
    }

    private BlockPos findChestFromItemFrame(ServerWorld world, ItemFrameEntity frame) {
        BlockPos decPos = frame.getDecorationBlockPos();
        if (decPos != null && getInventoryAtPos(world, decPos) != null) {
            return decPos;
        }

        Direction facing = frame.getHorizontalFacing();
        if (facing != null) {
            BlockPos behindPos = frame.getBlockPos().offset(facing.getOpposite());
            if (getInventoryAtPos(world, behindPos) != null) {
                return behindPos;
            }
        }

        for (Direction dir : Direction.values()) {
            BlockPos p = frame.getBlockPos().offset(dir);
            if (getInventoryAtPos(world, p) != null) {
                return p;
            }
        }
        return null;
    }

    private ItemFrameEntity findAttachedItemFrame(ServerWorld world, BlockPos chestPos) {
        ItemFrameEntity frame = scanItemFrames(world, chestPos);
        if (frame != null) return frame;

        BlockPos otherHalf = getDoubleChestOtherHalf(world, chestPos);
        if (otherHalf != null) {
            frame = scanItemFrames(world, otherHalf);
            if (frame != null) return frame;
        }
        return null;
    }

    private ItemFrameEntity scanItemFrames(ServerWorld world, BlockPos pos) {
        List<ItemFrameEntity> list = world.getEntitiesByClass(
                ItemFrameEntity.class,
                new Box(pos).expand(1.25),
                f -> !f.getHeldItemStack().isEmpty()
        );
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean canChestInsert(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return true;
        String dim = world.getRegistryKey().getValue().toString();
        if (!mod.getData().canInsertIntoChest(dim, pos)) return false;
        BlockPos other = getDoubleChestOtherHalf(world, pos);
        if (other != null && !mod.getData().canInsertIntoChest(dim, other)) return false;
        return true;
    }

    public boolean canChestExtract(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return true;
        String dim = world.getRegistryKey().getValue().toString();
        if (!mod.getData().canExtractFromChest(dim, pos)) return false;
        BlockPos other = getDoubleChestOtherHalf(world, pos);
        if (other != null && !mod.getData().canExtractFromChest(dim, other)) return false;
        return true;
    }

    public BlockPos findTargetChestFromPlayerLook(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) return null;
        ServerWorld serverWorld = player.getServerWorld();

        // 1. Raycast de Moldura
        Vec3d cameraPos = player.getCameraPosVec(1.0F);
        Vec3d rot = player.getRotationVec(1.0F);
        Vec3d reachVec = cameraPos.add(rot.multiply(6.0));
        Box searchBox = player.getBoundingBox().stretch(rot.multiply(6.0)).expand(1.5);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, cameraPos, reachVec, searchBox,
                e -> (e instanceof ItemFrameEntity), 36.0);

        if (entityHit != null && entityHit.getEntity() instanceof ItemFrameEntity frame) {
            return findChestFromItemFrame(serverWorld, frame);
        }

        // 2. Raycast de Bloco
        HitResult hit = player.raycast(6.0, 0, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos hitPos = blockHit.getBlockPos();
            BlockEntity hitBe = serverWorld.getBlockEntity(hitPos);

            if (hitBe instanceof SignBlockEntity) {
                return findAttachedChest(serverWorld, hitPos);
            } else if (getInventoryAtPos(serverWorld, hitPos) != null) {
                return hitPos;
            }
        }
        return null;
    }

    public boolean setChestPermission(ServerPlayerEntity player, boolean canInsert, boolean canExtract) {
        if (player == null || player.getWorld().isClient()) return false;
        ServerWorld serverWorld = player.getServerWorld();

        BlockPos chestPos = findTargetChestFromPlayerLook(player);
        if (chestPos == null) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, olhe para um baú, barril ou placa/moldura conectada a ele para configurar as permissões!"), false);
            return false;
        }

        String dim = serverWorld.getRegistryKey().getValue().toString();
        mod.getData().setChestRule(dim, chestPos, canInsert, canExtract);
        BlockPos otherHalf = getDoubleChestOtherHalf(serverWorld, chestPos);
        if (otherHalf != null) {
            mod.getData().setChestRule(dim, otherHalf, canInsert, canExtract);
        }

        serverWorld.playSound(null, chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                canInsert || canExtract ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_CHEST_LOCKED,
                SoundCategory.BLOCKS, 0.8f, 1.2f);
        serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                chestPos.getX() + 0.5, chestPos.getY() + 0.8, chestPos.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0.05);

        String isDouble = otherHalf != null ? " §7(Baú Duplo)" : "";
        player.sendMessage(Text.literal("§6=================================================="), false);
        player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aRegras de organização atualizadas!"), false);
        player.sendMessage(Text.literal("§fLocalização: §eX: " + chestPos.getX() + ", Y: " + chestPos.getY() + ", Z: " + chestPos.getZ() + isDouble), false);
        player.sendMessage(Text.literal("§f- Colocar itens (Destino): " + (canInsert ? "§a[SIM]" : "§c[NÃO]")), false);
        player.sendMessage(Text.literal("§f- Retirar itens (Origem):  " + (canExtract ? "§a[SIM]" : "§c[NÃO]")), false);

        if (!canInsert && !canExtract) {
            player.sendMessage(Text.literal("§7Status: §cIGNORADO TOTALMENTE §7(o Jacinto nunca colocará nem tirará itens deste baú)."), false);
        } else if (canInsert && !canExtract) {
            player.sendMessage(Text.literal("§7Status: §aAPENAS ENTRADA / DEPÓSITO §7(o Jacinto pode colocar itens aqui, mas NUNCA retirará nada dele)."), false);
        } else if (!canInsert && canExtract) {
            player.sendMessage(Text.literal("§7Status: §eBAÚ DE DESPEJO / APENAS SAÍDA §7(o Jacinto pode esvaziar itens daqui para outros baús, mas NUNCA colocará nada nele)."), false);
        } else {
            player.sendMessage(Text.literal("§7Status: §aFLUXO NORMAL §7(o Jacinto pode colocar e retirar itens deste baú normalmente)."), false);
        }
        player.sendMessage(Text.literal("§6=================================================="), false);
        return true;
    }

    public JacintoOrganizer(JacintoMod mod) {
        this.mod = mod;
    }

    public boolean isBusy() {
        return isBusy;
    }

    /**
     * Obtém o inventário completo do bloco (suporta Baús Duplos unificados de 54 slots, barris, shulkers, etc.)
     */
    public static Inventory getInventoryAtPos(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return null;
        Inventory inv = HopperBlockEntity.getInventoryAt(world, pos);
        if (inv != null) return inv;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Inventory i) return i;
        return null;
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

        ServerWorld serverWorld = player.getServerWorld();
        BlockPos foundChestPos = null;
        Item identifiedItem = null;
        String identificationSource = "";
        List<String> rawLines = new ArrayList<>();

        // 1. PRIMEIRO: Raycast de Entidade (detecta se o jogador está mirando diretamente em uma Moldura com item)
        Vec3d cameraPos = player.getCameraPosVec(1.0F);
        Vec3d rot = player.getRotationVec(1.0F);
        Vec3d reachVec = cameraPos.add(rot.multiply(6.0));
        Box searchBox = player.getBoundingBox().stretch(rot.multiply(6.0)).expand(1.5);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, cameraPos, reachVec, searchBox,
                e -> (e instanceof ItemFrameEntity), 36.0);

        if (entityHit != null && entityHit.getEntity() instanceof ItemFrameEntity frame) {
            ItemStack held = frame.getHeldItemStack();
            if (held.isEmpty()) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cEssa moldura está vazia! Coloque o item que deseja guardar dentro da moldura para eu usar como referência."), false);
                return false;
            }
            foundChestPos = findChestFromItemFrame(serverWorld, frame);
            if (foundChestPos == null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão encontrei nenhum baú ou barril conectado a esta moldura!"), false);
                return false;
            }
            identifiedItem = held.getItem();
            identificationSource = "Moldura";
        }

        // 2. SE NÃO MIROU EM MOLDURA: Raycast de Blocos (Placa ou Baú)
        if (identifiedItem == null) {
            HitResult hit = player.raycast(6.0, 0, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, olhe para a §6Moldura§c com o item, para uma §ePlaca§c ou diretamente para o §6Baú§c e tente novamente!"), false);
                return false;
            }

            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos hitPos = blockHit.getBlockPos();
            BlockEntity hitBe = serverWorld.getBlockEntity(hitPos);

            // 2.1 Caso tenha mirado diretamente na Placa
            if (hitBe instanceof SignBlockEntity signBe) {
                collectSignLines(signBe, rawLines);
                foundChestPos = findAttachedChest(serverWorld, hitPos);
            }
            // 2.2 Caso tenha mirado diretamente no Baú (ou barril)
            else if (getInventoryAtPos(serverWorld, hitPos) != null) {
                foundChestPos = hitPos;
                SignBlockEntity nearbySign = findNearbySign(serverWorld, hitPos);
                if (nearbySign != null) {
                    collectSignLines(nearbySign, rawLines);
                }
            }

            if (foundChestPos == null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão encontrei nenhum baú ou barril onde você está olhando!"), false);
                return false;
            }

            // Verifica se este baú possui uma Moldura com item afixada nele
            ItemFrameEntity attachedFrame = findAttachedItemFrame(serverWorld, foundChestPos);
            if (attachedFrame != null && !attachedFrame.getHeldItemStack().isEmpty()) {
                identifiedItem = attachedFrame.getHeldItemStack().getItem();
                identificationSource = "Moldura";
            } else {
                // Tenta descobrir o item através do texto da placa ou do conteúdo existente no baú
                identifiedItem = identifyItemFromSignOrChest(serverWorld, foundChestPos, rawLines);
                if (identifiedItem != null && !rawLines.isEmpty()) {
                    identificationSource = "Placa";
                } else if (identifiedItem != null) {
                    identificationSource = "Baú";
                }
            }
        }

        if (identifiedItem == null || identifiedItem == Items.AIR) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão consegui identificar qual item guardar! Coloque um item na §6Moldura§c, escreva o nome na §ePlaca§c ou coloque um exemplar dentro do §6Baú§c."), false);
            return false;
        }

        // Verifica se este baú tem permissão para receber itens (não colocar)
        if (!canChestInsert(serverWorld, foundChestPos)) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, este baú está configurado com [NÃO COLOCAR ITENS]!\n§7Use §e/organizar sim sim§7 ou §e/organizar sim nao§7 nele para permitir usá-lo como destino."), false);
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

        // ADICIONA O BAÚ ALVO À LISTA DE EXCLUSÃO (LOOP PREVENTION - AMBAS AS METADES)
        this.excludedChestPositions.add(foundChestPos);
        BlockPos otherHalf = getDoubleChestOtherHalf(serverWorld, foundChestPos);
        if (otherHalf != null) {
            this.excludedChestPositions.add(otherHalf);
        }

        // Varrer recipientes vizinhos em um raio de 24 blocos
        scanSourceChests(serverWorld, foundChestPos, 24);

        // Confirmação para o jogador
        String sourceLabel = switch (identificationSource) {
            case "Moldura" -> "na §6Moldura";
            case "Placa" -> "na §ePlaca";
            default -> "através do exemplo no §6Baú";
        };
        player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, patrão §b" + player.getGameProfile().getName() +
                "§f! Identifiquei o item " + sourceLabel + "§f: §b§l[" + targetItemDisplayName + "]§f.\n§7Revistando " + sourceChests.size() +
                " baús vizinhos para concentrar tudo aqui..."), false);

        if (sourceChests.isEmpty()) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §eNão encontrei nenhum outro baú num raio de 24 blocos que contenha §b[" + targetItemDisplayName + "]§e!"), false);
            this.isBusy = false;
            mod.getData().setIdleTask();
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
        Inventory targetInv = getInventoryAtPos(world, targetChestPos);
        Inventory sourceInv = getInventoryAtPos(world, sourcePos);

        if (targetInv == null) {
            finishOrganizing(true, "O baú de destino foi removido ou destruído!");
            return;
        }

        if (sourceInv == null) {
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

        // Se o baú de destino lotou completamente
        if (isInventoryFullOfItem(targetInv, targetItem)) {
            finishOrganizing(true, "O baú de destino lotou até a tampa!");
            return;
        }

        // Avança para o próximo baú da fila
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
        int moved = 0;
        for (int i = 0; i < source.size(); i++) {
            ItemStack srcStack = source.getStack(i);
            if (!srcStack.isEmpty() && srcStack.getItem() == matchItem) {
                int countBefore = srcStack.getCount();
                ItemStack remaining = insertItemIntoInventory(target, srcStack);
                int transferred = countBefore - remaining.getCount();
                moved += transferred;

                if (remaining.isEmpty()) {
                    source.setStack(i, ItemStack.EMPTY);
                } else {
                    source.setStack(i, remaining);
                }
                source.markDirty();

                if (isInventoryFullOfItem(target, matchItem)) {
                    break;
                }
            }
        }
        return moved;
    }

    /**
     * Insere item no inventário respeitando regras de empilhamento de slots existentes
     */
    private ItemStack insertItemIntoInventory(Inventory inv, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // Primeiro tenta mesclar em pilhas existentes do mesmo item
        for (int i = 0; i < inv.size(); i++) {
            ItemStack cur = inv.getStack(i);
            if (!cur.isEmpty() && ItemStack.canCombine(cur, stack)) {
                int max = Math.min(inv.getMaxCountPerStack(), cur.getMaxCount());
                int canAdd = max - cur.getCount();
                if (canAdd > 0) {
                    int toAdd = Math.min(canAdd, stack.getCount());
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
     * Varrer blocos de inventário no raio de ação, excluindo ambas as metades do baú de destino
     */
    private void scanSourceChests(ServerWorld world, BlockPos center, int radius) {
        sourceChests.clear();
        Set<BlockPos> visitedDoubleChests = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (excludedChestPositions.contains(p)) continue;
                    if (visitedDoubleChests.contains(p)) continue;
                    if (!canChestExtract(world, p)) continue;

                    Inventory inv = getInventoryAtPos(world, p);
                    if (inv != null) {
                        BlockPos other = getDoubleChestOtherHalf(world, p);
                        if (other != null) {
                            visitedDoubleChests.add(other);
                        }
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

        // 1. Se for placa de parede, o bloco atrás dela é o principal candidato
        if (signState.getBlock() instanceof WallSignBlock) {
            Direction backDir = signState.get(WallSignBlock.FACING).getOpposite();
            BlockPos backPos = signPos.offset(backDir);
            if (getInventoryAtPos(world, backPos) != null) {
                return backPos;
            }
        }

        // 2. Checa as 6 direções diretas (baixo, cima, lados)
        for (Direction dir : Direction.values()) {
            BlockPos p = signPos.offset(dir);
            if (getInventoryAtPos(world, p) != null) {
                return p;
            }
        }

        // 3. Checa raio de 2 blocos (ex: placa no chão em frente ao baú ou placa suspensa)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = signPos.add(dx, dy, dz);
                    if (getInventoryAtPos(world, p) != null) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private SignBlockEntity findNearbySign(ServerWorld world, BlockPos chestPos) {
        SignBlockEntity s = scanNearbySigns(world, chestPos);
        if (s != null) return s;

        // Se for baú duplo, checa também ao redor da outra metade!
        BlockPos otherHalf = getDoubleChestOtherHalf(world, chestPos);
        if (otherHalf != null) {
            s = scanNearbySigns(world, otherHalf);
            if (s != null) return s;
        }
        return null;
    }

    private SignBlockEntity scanNearbySigns(ServerWorld world, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = pos.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof SignBlockEntity signBe) {
                        return signBe;
                    }
                }
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
     * Identifica o item pretendido com máxima precisão:
     * 1. Se o baú já contém itens E a placa possui texto, compara as palavras da placa com os itens do baú
     * 2. Tenta casar o texto da placa com o dicionário em PT-BR e IDs oficiais do Minecraft (com tolerância fonética/Levenshtein)
     * 3. Se a placa for genérica, decorada ou em branco, usa o primeiro item do baú como exemplar
     */
    private Item identifyItemFromSignOrChest(ServerWorld world, BlockPos chestPos, List<String> rawLines) {
        Inventory chestInv = getInventoryAtPos(world, chestPos);
        List<ItemStack> itemsInChest = new ArrayList<>();
        if (chestInv != null) {
            for (int i = 0; i < chestInv.size(); i++) {
                ItemStack st = chestInv.getStack(i);
                if (!st.isEmpty()) {
                    itemsInChest.add(st);
                }
            }
        }

        // 1. Se a placa tem texto e o baú já tem itens, dá prioridade aos itens presentes no baú
        if (!rawLines.isEmpty() && !itemsInChest.isEmpty()) {
            for (ItemStack st : itemsInChest) {
                Item item = st.getItem();
                String itemPath = Registries.ITEM.getId(item).getPath().replace("_", " ");
                String itemName = normalize(st.getName().getString());
                for (String line : rawLines) {
                    String clean = normalize(line);
                    if (clean.equals(itemPath) || clean.equals(itemName)
                            || levenshtein(clean, itemPath) <= 2
                            || containsWholeWord(clean, itemPath)
                            || containsWholeWord(clean, itemName)) {
                        return item;
                    }
                }
            }
        }

        // 2. Tenta identificar diretamente a partir dos textos da placa via dicionário e registro
        for (String line : rawLines) {
            Item match = matchItemName(line);
            if (match != null && match != Items.AIR) {
                return match;
            }
        }

        // 3. Se a placa não casou ou estava decorativa/em branco, mas o baú já possui itens, usa o primeiro exemplar
        if (!itemsInChest.isEmpty()) {
            return itemsInChest.get(0).getItem();
        }

        return null;
    }

    private Item matchItemName(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = normalize(input);

        // 1. Dicionário PT-BR: primeiro correspondência exata
        for (Map.Entry<String, Item> entry : PT_DICT.entrySet()) {
            if (clean.equals(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. Registries.ITEM: correspondência exata por caminho de registro (ex: "andesite", "diamond")
        for (Item it : Registries.ITEM) {
            String path = Registries.ITEM.getId(it).getPath().replace("_", " ");
            if (clean.equals(path)) {
                return it;
            }
        }

        // 3. Similaridade fonética (Levenshtein <= 2) para variações mínimas (ex: andesito -> andesite, diorito -> diorite)
        Item bestFuzzy = null;
        int bestDist = Integer.MAX_VALUE;
        for (Item it : Registries.ITEM) {
            String path = Registries.ITEM.getId(it).getPath().replace("_", " ");
            if (Math.abs(path.length() - clean.length()) <= 2) {
                int dist = levenshtein(clean, path);
                if (dist <= 2 && dist < bestDist) {
                    bestDist = dist;
                    bestFuzzy = it;
                }
            }
        }
        if (bestFuzzy != null) {
            return bestFuzzy;
        }

        // 4. Correspondência de palavra inteira no dicionário PT (ex: "Bau de Andesito" -> andesito)
        for (Map.Entry<String, Item> entry : PT_DICT.entrySet()) {
            if (containsWholeWord(clean, entry.getKey())) {
                return entry.getValue();
            }
        }

        // 5. Correspondência de palavra inteira nos IDs do Minecraft (comprimento >= 4 para evitar "and", "in", "to")
        for (Item it : Registries.ITEM) {
            String path = Registries.ITEM.getId(it).getPath().replace("_", " ");
            if (path.length() >= 4 && containsWholeWord(clean, path)) {
                return it;
            }
        }

        return null;
    }

    private static boolean containsWholeWord(String text, String word) {
        if (text == null || word == null || word.isBlank()) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .trim()
                .replaceAll(" +", " ");
    }
}
