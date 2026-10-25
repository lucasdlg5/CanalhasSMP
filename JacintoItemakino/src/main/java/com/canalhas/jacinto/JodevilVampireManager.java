package com.canalhas.jacinto;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sistema de Lore e Maldição Vampírica Exclusiva do jogador Jodevil:
 * - Debuff Permanente ("Maldição do Sangue Duvidoso — Vampirismo de ArzanLion") com lore interativa.
 * - Ao sair ao sol, Jodevil recebe 1 de dano de HP a cada 1 minuto (1200 ticks) de exposição solar.
 * - Gera no ponto de spawn salvo de Jodevil (-678, 94, -271) um Baú contendo o Livro de Lore completo,
 *   escrito no estilo de "Entrevista com o Vampiro", "A Rainha dos Condenados" e "Drácula" de Bram Stoker,
 *   sem qualquer referência a Minecraft.
 */
public class JodevilVampireManager {
    private static final Logger LOG = LoggerFactory.getLogger("JodevilVampire");

    public static final String JODEVIL_NAME = "jodevil";
    public static final UUID JODEVIL_ONLINE_UUID = UUID.fromString("97911e84-98a5-3be0-a88e-45cd038e6f82");
    public static final UUID JODEVIL_OFFLINE_UUID = UUID.fromString("c2c46a05-309e-4665-b8b2-b5a242bda400");

    // Ponto de spawn salvo nos dados do jogador Jodevil (extraído do playerdata)
    public static final BlockPos DEFAULT_JODEVIL_SPAWN = new BlockPos(-678, 94, -271);
    // Posição ao lado da cama/caixão no piso de pedra (afastado da cama, em Y=94)
    public static final BlockPos DEFAULT_JODEVIL_CHEST_POS = new BlockPos(-676, 94, -273);

    // 1 minuto = 60 segundos = 1200 ticks
    private static final int SUN_DAMAGE_INTERVAL_TICKS = 1200;

    private final JacintoMod mod;
    private final Map<UUID, Integer> sunExposureTicks = new HashMap<>();
    private final Set<UUID> welcomedThisSession = new HashSet<>();
    private boolean chestVerificationAttemptedThisBoot = false;

    public JodevilVampireManager(JacintoMod mod) {
        this.mod = mod;
    }

    public static boolean isJodevil(ServerPlayerEntity player) {
        if (player == null) return false;
        String name = player.getGameProfile().getName();
        if (name != null && name.equalsIgnoreCase(JODEVIL_NAME)) {
            return true;
        }
        UUID id = player.getUuid();
        return JODEVIL_ONLINE_UUID.equals(id) || JODEVIL_OFFLINE_UUID.equals(id);
    }

    public void onServerTick(MinecraftServer server) {
        // 1. Garante que o Baú com o Livro de Lore no ponto de spawn salvo de Jodevil exista
        if (!chestVerificationAttemptedThisBoot && server.getTicks() > 60) {
            chestVerificationAttemptedThisBoot = true;
            ensureLoreChestSpawned(server, false);
        }

        // 2. Processa o Debuff Permanente e exposição solar do Jodevil
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isJodevil(player)) continue;

            // Se o baú ainda não foi marcado como criado e Jodevil conectou, verifica o spawn dele
            if (!mod.getData().jodevilLoreChestCreated) {
                ensureLoreChestSpawned(server, false);
            }

            // Envia o card da Maldição Permanente ao entrar no servidor
            if (!welcomedThisSession.contains(player.getUuid()) && player.age > 40) {
                welcomedThisSession.add(player.getUuid());
                sendDebuffWelcomeBanner(player);
            }

            // Mantém o ícone permanente da maldição ativo na HUD/Inventário (sem partículas intrusivas)
            maintainPermanentCurseEffect(player);

            // Processa queimação solar (1 HP por minuto ao sol)
            tickSunlightExposure(player);
        }
    }

    /**
     * Mantém um efeito permanente visível no inventário/HUD de Jodevil para representar o Debuff
     * sem exigir que os jogadores instalem novos mods no cliente.
     */
    private void maintainPermanentCurseEffect(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.UNLUCK);
        if (current == null || current.getDuration() < 400) {
            // Duração muito longa (ambient = true, showParticles = false, showIcon = true)
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.UNLUCK,
                    72000, // 1 hora renovada continuamente (permanente)
                    0,
                    true,  // ambient (borda sutil de maldição/aura)
                    false, // sem partículas poluindo a visão
                    true   // ícone sempre visível na tela e inventário
            ));
        }
    }

    /**
     * Verifica se Jodevil está exposto diretamente à luz do sol e aplica 1 de dano de HP a cada 60 segundos.
     */
    private void tickSunlightExposure(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
            sunExposureTicks.put(player.getUuid(), 0);
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos eyePos = BlockPos.ofFloored(player.getX(), player.getEyeY(), player.getZ());

        boolean inOverworld = world.getRegistryKey() == World.OVERWORLD;
        boolean isSunnyDay = world.isDay() && !world.isRaining() && !world.isThundering();
        boolean skyVisible = world.isSkyVisible(eyePos);
        boolean notInWater = !player.isSubmergedInWater();

        if (inOverworld && isSunnyDay && skyVisible && notInWater) {
            int ticks = sunExposureTicks.getOrDefault(player.getUuid(), 0) + 1;
            sunExposureTicks.put(player.getUuid(), ticks);

            // Avisos sutis na ActionBar ao entrar no sol ou na metade do minuto
            if (ticks == 20) {
                player.sendMessage(Text.literal("§4🩸 [Sangue Duvidoso] §cA luz do sol queima lentamente sua pele amaldiçoada por ArzanLion... §7(Abrigue-se na sombra!)"), true);
            } else if (ticks == 600) {
                player.sendMessage(Text.literal("§4🩸 [Sangue Duvidoso] §eSuas veias fervem sob o astro-rei... §7(30s para sofrer queimadura vampírica)"), true);
                world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.4, 0.25, 0.01);
            }

            // A cada 1 minuto (1200 ticks) ao sol: aplica exatamente 1.0 de dano de HP
            if (ticks >= SUN_DAMAGE_INTERVAL_TICKS) {
                sunExposureTicks.put(player.getUuid(), 0);
                applyVampireSunburn(player, world);
            }
        } else {
            // Resfria gradualmente quando volta para a sombra ou à noite
            int current = sunExposureTicks.getOrDefault(player.getUuid(), 0);
            if (current > 0) {
                sunExposureTicks.put(player.getUuid(), Math.max(0, current - 2));
            }
        }
    }

    /**
     * Aplica exatamente 1 de dano de HP (1.0f = meio coração) ignorando redução de armadura,
     * com 1% de chance de Combustão Solar Instantânea (morte imediata ao sol!),
     * acompanhado de efeitos visuais/sonoros góticos e a lore do debuff.
     */
    private void applyVampireSunburn(ServerPlayerEntity player, ServerWorld world) {
        // 1% de chance de morrer instantaneamente ao tomar o dano solar da maldição!
        boolean instantSolarDeath = world.getRandom().nextInt(100) == 0;

        if (instantSolarDeath) {
            // Partículas cinematográficas de combustão vampírica
            world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 45, 0.4, 0.7, 0.4, 0.08);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 35, 0.4, 0.7, 0.4, 0.08);
            world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.04);
            world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.1);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.7f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_BAT_DEATH, SoundCategory.PLAYERS, 1.2f, 0.6f);

            // Anúncio global com Hover da Lore
            MutableText globalMsg = Text.literal("§8[§4🩸 §c§lCOMBUSTÃO VAMPÍRICA CRÍTICA (1%)§8] ")
                    .append(Text.literal("§eO feitiço instável de §6ArzanLion§e entrou em colapso! §4Conde Jodevil§e virou cinzas instantaneamente sob o sol! 🦇☀️💀")
                            .setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildDebuffLoreHoverText()))));

            if (world.getServer() != null) {
                world.getServer().getPlayerManager().broadcast(globalMsg, false);
            } else {
                player.sendMessage(globalMsg, false);
            }

            // Morte instantânea ao sol
            player.setOnFireFor(5);
            player.setHealth(0.001f);
            player.damage(world.getDamageSources().onFire(), 10000.0f);
            if (player.isAlive()) {
                player.kill();
            }
            return;
        }

        float currentHealth = player.getHealth();
        if (currentHealth > 1.0f) {
            player.setHealth(currentHealth - 1.0f);
            // Aciona animação/som de dano sem subtrair vida adicional
            player.damage(world.getDamageSources().generic(), 0.0001f);
        } else {
            // Se estiver com apenas 1 de HP e insistir em ficar no sol, sucumbe às cinzas
            player.damage(world.getDamageSources().onFire(), 10.0f);
        }

        // Partículas de cinzas, fumaça e chama espectral
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.2, player.getZ(), 25, 0.4, 0.6, 0.4, 0.05);

        // Som de chiado de queimadura vampírica
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 0.75f);

        // ActionBar imediata
        player.sendMessage(Text.literal("§4🦇 [Maldição do Sangue Duvidoso] §cO sol escalda seu sangue! §4§l-1 HP §7(1% de chance de morte instantânea!)"), true);

        // Mensagem no chat com Hover da Lore completa do Debuff
        MutableText chatAlert = Text.literal("§8[§4🩸 §c§lMaldição do Sangue Duvidoso§8] ")
                .append(Text.literal("§7O sol drenou §c1 de HP§7 da sua carcaça imortal §8(1% de risco de virar cinzas na hora!) ")
                        .setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildDebuffLoreHoverText()))))
                .append(Text.literal("§8[§6§nLer Lore do Debuff§8]")
                        .setStyle(Style.EMPTY
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildDebuffLoreHoverText()))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vampiro"))));

        player.sendMessage(chatAlert, false);
    }

    /**
     * Constrói o card de Lore Escrita do Debuff Permanente de Jodevil.
     */
    public static Text buildDebuffLoreHoverText() {
        MutableText t = Text.empty();
        t.append(Text.literal("§4§l🦇 MALDIÇÃO DO SANGUE DUVIDOSO §8(Debuff Permanente)\n"));
        t.append(Text.literal("§7Portador Exclusivo: §cConde Jodevil\n"));
        t.append(Text.literal("§7Autor do Desastre: §eGrão-Alquimista ArzanLion\n"));
        t.append(Text.literal("§7Efeito Passivo: §c-1 de HP a cada 1 min ao Sol §4(1% de Morte Instantânea!)\n"));
        t.append(Text.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        t.append(Text.literal("§f§o\"Tu pediste a eternidade da noite, Jodevil,\n"));
        t.append(Text.literal("§f§omas ArzanLion leu o pergaminho de cabeça para baixo.\"\n\n"));
        t.append(Text.literal("§7Buscando a nobreza sombria dos antigos imortais,\n"));
        t.append(Text.literal("§7Jodevil submeteu-se aos feitiços proibidos de §eArzanLion§7.\n"));
        t.append(Text.literal("§7Porém, na falta de sangue ancestral dos Cárpatos,\n"));
        t.append(Text.literal("§7ArzanLion misturou vinho barato de taverna, alho vencido,\n"));
        t.append(Text.literal("§7suco de beterraba e espirrou no meio do cântico em latim.\n\n"));
        t.append(Text.literal("§7O resultado não foi a majestade de um Lorde Nosferatu,\n"));
        t.append(Text.literal("§7mas um §cVampirismo de Segunda Linha§7: o astro-rei cozinha\n"));
        t.append(Text.literal("§7lentamente sua dignidade, drenando §c1 ponto de HP por minuto§7,\n"));
        t.append(Text.literal("§7com §4§l1% de chance§7 de o feitiço entrar em curto-circuito\n"));
        t.append(Text.literal("§7e §4reduzi-lo a cinzas instantaneamente§7 ao tomar o dano!\n"));
        t.append(Text.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        t.append(Text.literal("§6📖 Relíquia: §eHá um Baú ao lado de seu leito (-676, 94, -273)\n"));
        t.append(Text.literal("§e   com o livro completo das Crônicas de sua transformação!"));
        return t;
    }

    public void sendDebuffWelcomeBanner(ServerPlayerEntity player) {
        MutableText msg = Text.literal("\n§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                .append(Text.literal("  §4🦇 §c§lMALDIÇÃO ATIVA: §4§lSANGUE DUVIDOSO DE ARZANLION §4🦇\n"))
                .append(Text.literal("  §7§o\"O sol é um tirano para aqueles cujo ritual deu errado...\"\n\n"))
                .append(Text.literal("  §fEfeito Permanente: §cAo sair ao sol, você perde §4§l1 HP§c a cada minuto\n"))
                .append(Text.literal("  §c                   (com §4§l1% de chance§c de virar cinzas na hora!).\n"))
                .append(Text.literal("  §fLore do Debuff: "))
                .append(Text.literal("§6§l[Passe o Mouse Aqui ou Clique para Ler]")
                        .setStyle(Style.EMPTY
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildDebuffLoreHoverText()))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vampiro"))))
                .append(Text.literal("\n  §7Um baú com as §eCrônicas do Sangue Duvidoso§7 aguarda ao lado do seu leito.\n"))
                .append(Text.literal("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        player.sendMessage(msg, false);
    }

    public void sendFullDebuffLoreToPlayer(ServerPlayerEntity player) {
        player.sendMessage(buildDebuffLoreHoverText(), false);
    }

    /**
     * Cria o Baú Duplo alguns blocos ao lado da cama de Jodevil (-676, 94, -273 e -675, 94, -273)
     * e deposita os 54 Livros (27 do Arco 1 + 27 do Arco 2: O Ciclo do Uroboro).
     */
    public BlockPos ensureLoreChestSpawned(MinecraftServer server, boolean forceRecreate) {
        JacintoData data = mod.getData();
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return null;

        // Se estava salvo na posição antiga em cima da cama (Y=95), força relocação para o piso (Y=94)
        boolean wasBuggedAboveBed = (data.jodevilLoreChestY == 95 && data.jodevilLoreChestZ == -271);

        // Verifica se o Baú Duplo atual já possui os 54 capítulos completos da revisão atual ("Inquisidor Valerius")
        if (data.jodevilLoreChestCreated && !forceRecreate && !wasBuggedAboveBed) {
            BlockPos savedPos = new BlockPos(data.jodevilLoreChestX, data.jodevilLoreChestY, data.jodevilLoreChestZ);
            overworld.getChunk(savedPos.getX() >> 4, savedPos.getZ() >> 4);
            BlockState savedState = overworld.getBlockState(savedPos);
            if (savedState.getBlock() instanceof ChestBlock chestBlock) {
                Inventory doubleInv = ChestBlock.getInventory(chestBlock, savedState, overworld, savedPos, true);
                if (doubleInv != null && doubleInv.size() >= 54) {
                    int bookCount = 0;
                    boolean hasUpdatedArc2Author = false;
                    for (int i = 0; i < doubleInv.size(); i++) {
                        ItemStack st = doubleInv.getStack(i);
                        if (st.isOf(Items.WRITTEN_BOOK)) {
                            bookCount++;
                            if (i == 27 && st.hasNbt() && "Inquisidor Valerius".equals(st.getNbt().getString("author"))) {
                                hasUpdatedArc2Author = true;
                            }
                        }
                    }
                    if (bookCount >= 54 && hasUpdatedArc2Author) {
                        spawnBedroomNarrativeProps(overworld, savedPos);
                        return savedPos;
                    }
                }
            }
        }

        // Determina o ponto de spawn salvo de Jodevil
        BlockPos targetSpawn = DEFAULT_JODEVIL_SPAWN;
        ServerPlayerEntity onlineJodevil = null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (isJodevil(p)) {
                onlineJodevil = p;
                if (p.getSpawnPointPosition() != null && p.getSpawnPointDimension() == World.OVERWORLD) {
                    targetSpawn = p.getSpawnPointPosition();
                }
                break;
            }
        }

        // Garante que o chunk esteja carregado
        overworld.getChunk(targetSpawn.getX() >> 4, targetSpawn.getZ() >> 4);

        // Remove qualquer baú antigo que tenha ficado em cima da cama (-677, 95, -271 ou -678, 95, -271)
        for (BlockPos aboveBed : new BlockPos[]{new BlockPos(-677, 95, -271), new BlockPos(-678, 95, -271)}) {
            if (overworld.getBlockState(aboveBed).getBlock() instanceof ChestBlock) {
                BlockEntity oldBe = overworld.getBlockEntity(aboveBed);
                if (oldBe instanceof ChestBlockEntity oldChest) {
                    oldChest.clear();
                }
                overworld.setBlockState(aboveBed, Blocks.AIR.getDefaultState(), 3);
                data.setChestRule(overworld.getRegistryKey().getValue().toString(), aboveBed, true, true);
            }
        }

        // Escolhe a posição ideal a alguns blocos ao lado da cama no piso firme (nunca em cima da cama)
        BlockPos chestPos = findSafeChestSpotNearSpawn(overworld, targetSpawn);
        BlockPos secondChestPos = chestPos.east();

        // Posiciona as duas metades do Baú Duplo olhando para o Norte (LEFT em chestPos, RIGHT em chestPos.east())
        BlockState leftChestState = Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
        BlockState rightChestState = Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.RIGHT);

        overworld.setBlockState(chestPos, leftChestState, 3);
        overworld.setBlockState(secondChestPos, rightChestState, 3);

        Text customTitle = Text.literal("§4§l⚰️ Relíquias Noturnas de Conde Jodevil (54 Tomos)");
        if (overworld.getBlockEntity(chestPos) instanceof ChestBlockEntity leftBe) {
            leftBe.setCustomName(customTitle);
        }
        if (overworld.getBlockEntity(secondChestPos) instanceof ChestBlockEntity rightBe) {
            rightBe.setCustomName(customTitle);
        }

        java.util.List<ItemStack> all54Books = new java.util.ArrayList<>(54);
        all54Books.addAll(JodevilLoreLibrary.createAll27ChapterBooks());
        all54Books.addAll(JodevilFutureLoreLibrary.createAllArc2ChapterBooks());

        Inventory doubleInv = ChestBlock.getInventory((ChestBlock) Blocks.CHEST, overworld.getBlockState(chestPos), overworld, chestPos, true);
        if (doubleInv != null && doubleInv.size() >= 54) {
            for (int i = 0; i < Math.min(doubleInv.size(), all54Books.size()); i++) {
                doubleInv.setStack(i, all54Books.get(i));
            }
            doubleInv.markDirty();
        } else {
            // Fallback individual caso o inventário duplo não seja resolvido imediatamente
            if (overworld.getBlockEntity(secondChestPos) instanceof ChestBlockEntity firstHalf) {
                for (int i = 0; i < 27 && i < all54Books.size(); i++) {
                    firstHalf.setStack(i, all54Books.get(i));
                }
                firstHalf.markDirty();
            }
            if (overworld.getBlockEntity(chestPos) instanceof ChestBlockEntity secondHalf) {
                for (int i = 0; i < 27 && (i + 27) < all54Books.size(); i++) {
                    secondHalf.setStack(i, all54Books.get(i + 27));
                }
                secondHalf.markDirty();
            }
        }

        // Adiciona os 3 elementos físicos da narrativa no quarto (apenas em blocos de AR livres):
        // 1) Estandarte Preto na parede (Espelho Velado)
        // 2) Vaso com Arbusto Seco perto da janela (Esconderijo do Livro 21)
        // 3) Suporte de Poções com o Cálice Cor-de-Rosa na cabeceira
        spawnBedroomNarrativeProps(overworld, chestPos);

        // Protege AMBAS as metades do Baú Duplo para que o Jacinto NUNCA retire os livros durante o /organizar!
        String dimKey = overworld.getRegistryKey().getValue().toString();
        data.setChestRule(dimKey, chestPos, false, false);
        data.setChestRule(dimKey, secondChestPos, false, false);

        data.jodevilLoreChestCreated = true;
        data.jodevilLoreChestX = chestPos.getX();
        data.jodevilLoreChestY = chestPos.getY();
        data.jodevilLoreChestZ = chestPos.getZ();
        data.jodevilLoreChestDimension = dimKey;
        data.save();

        LOG.info("[JodevilVampire] Baú Duplo de Lore Vampírica (54 Volumes) e decoração narrativa posicionados ao lado da cama em ({}, {}, {}) e ({}, {}, {})!",
                chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                secondChestPos.getX(), secondChestPos.getY(), secondChestPos.getZ());

        if (onlineJodevil != null) {
            onlineJodevil.sendMessage(Text.literal("§4🦇 [Crônicas da Noite] §7Um Baú Duplo contendo a coleção épica de §c54 Volumes (Crônicas do Sangue Duvidoso & Autos da Expurgação Noturna)§7 repousa ao lado do seu leito em §e(" +
                    chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ() + ")§7."), false);
        }

        return chestPos;
    }

    /**
     * Posiciona de forma segura (apenas em espaços de AR livres, sem substituir cama ou baús)
     * os 3 elementos físicos do Arco 2 no quarto de Jodevil:
     * 1. Estandarte Preto na parede ("O Espelho Velado")
     * 2. Vaso com Arbusto Seco/Samambaia ("Esconderijo Tático do Cap. XXI")
     * 3. Suporte de Poções com 3 Cálices Cor-de-Rosa ("Dose da Meia-Noite do 27º Dia")
     */
    private void spawnBedroomNarrativeProps(ServerWorld world, BlockPos chestPos) {
        try {
            // 1. Vaso com Arbusto Seco (a 1 bloco a oeste do Baú Duplo: -677, 94, -273, se estiver livre)
            BlockPos potPos = chestPos.west();
            if (world.getBlockState(potPos).isAir() && world.getBlockState(potPos.down()).isSolidBlock(world, potPos.down())) {
                world.setBlockState(potPos, Blocks.POTTED_DEAD_BUSH.getDefaultState(), 3);
            }

            // 2. Suporte de Poções com o Cálice Cor-de-Rosa (a leste do Baú Duplo: -674, 94, -273, se estiver livre)
            BlockPos brewPos = chestPos.east(2);
            if ((world.getBlockState(brewPos).isAir() || world.getBlockState(brewPos).isOf(Blocks.BREWING_STAND))
                    && world.getBlockState(brewPos.down()).isSolidBlock(world, brewPos.down())) {
                world.setBlockState(brewPos, Blocks.BREWING_STAND.getDefaultState(), 3);
                if (world.getBlockEntity(brewPos) instanceof BrewingStandBlockEntity brewBe) {
                    brewBe.setCustomName(Text.literal("§d§l⚗️ Cabeceira — Elixir do 27º Dia"));
                    ItemStack pinkChalice = createPinkElixirBottle();
                    for (int i = 0; i < 3; i++) {
                        brewBe.setStack(i, pinkChalice.copy());
                    }
                    brewBe.markDirty();
                }
            }

            // 3. Estandarte Preto na Parede (O Espelho Velado — acima do vaso ou na parede sul em Y=95)
            BlockPos bannerPos = chestPos.up();
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                BlockPos wallCheck = bannerPos.offset(dir.getOpposite());
                if (world.getBlockState(bannerPos).isAir() && world.getBlockState(wallCheck).isSolidBlock(world, wallCheck)) {
                    world.setBlockState(bannerPos, Blocks.BLACK_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, dir), 3);
                    if (world.getBlockEntity(bannerPos) instanceof BannerBlockEntity bannerBe) {
                        bannerBe.readFrom(new ItemStack(Items.BLACK_BANNER), net.minecraft.util.DyeColor.BLACK);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            LOG.warn("[JodevilVampire] Aviso ao posicionar elementos narrativos no quarto: {}", e.getMessage());
        }
    }

    public static ItemStack createPinkElixirBottle() {
        ItemStack potion = new ItemStack(Items.POTION);
        NbtCompound nbt = potion.getOrCreateNbt();
        nbt.putString("Potion", "minecraft:mundane");
        nbt.putInt("CustomPotionColor", 16738740); // Cor-de-rosa intenso (beterraba & papoula)

        NbtCompound display = nbt.getCompound("display");
        display.putString("Name", Text.Serializer.toJson(
                Text.literal("§d§l🍷 Cálice Cor-de-Rosa (Dose do 27º Dia)")));
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7\"Receita de ArzanLion e AsterZZZz contra a Dor da Eternidade.\""))));
        lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8Cheira fortemente a vinho tinto de terceira, beterraba e alho."))));
        display.put("Lore", lore);
        nbt.put("display", display);
        return potion;
    }

    private BlockPos findSafeChestSpotNearSpawn(ServerWorld world, BlockPos spawnPos) {
        // 1. Se o ponto de spawn for próximo da cama padrão (-678, 94, -271), prioriza (-675, 94, -273) (3 blocos ao lado no piso)
        if (spawnPos.isWithinDistance(DEFAULT_JODEVIL_SPAWN, 6.0)) {
            if (isValidNewChestSpot(world, DEFAULT_JODEVIL_CHEST_POS) ||
                world.getBlockState(DEFAULT_JODEVIL_CHEST_POS).getBlock() instanceof ChestBlock) {
                return DEFAULT_JODEVIL_CHEST_POS;
            }
        }

        // 2. Procura no MESMO nível do piso (dy = 0) a 2-4 blocos de distância da cama para nunca ficar colado ou em cima dela
        int[][] sideOffsets = {
                {2, -2}, {3, -2}, {0, -3}, {1, -3}, {-1, -3},
                {3, -1}, {3, 0}, {2, -3}, {-2, -2}, {-2, -3},
                {4, -2}, {4, -1}
        };
        for (int[] off : sideOffsets) {
            BlockPos candidate = spawnPos.add(off[0], 0, off[1]);
            if (isValidNewChestSpot(world, candidate) || world.getBlockState(candidate).getBlock() instanceof ChestBlock) {
                return candidate;
            }
        }

        return DEFAULT_JODEVIL_CHEST_POS;
    }

    private boolean isValidNewChestSpot(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        // NUNCA colocar em cima de camas, baús ou blocos com BlockEntity
        if (below.getBlock() instanceof net.minecraft.block.BedBlock || world.getBlockEntity(belowPos) != null) {
            return false;
        }
        // Garante distância mínima horizontal da cama para não atrapalhar o jogador ao dormir/acordar
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockState(pos.add(dx, 0, dz)).getBlock() instanceof net.minecraft.block.BedBlock) {
                    return false;
                }
            }
        }
        return below.isFullCube(world, belowPos) || below.isSolidBlock(world, belowPos);
    }

    /**
     * Cria o Livro Escrito ("Crônicas do Sangue Duvidoso") com narrativa inspirada em:
     * - "Entrevista com o Vampiro" (Anne Rice)
     * - "A Rainha dos Condenados" (Anne Rice)
     * - "Drácula" (Bram Stoker)
     * Sem qualquer referência a Minecraft, tratando a terra dos Canalhas como um universo gótico real
     * e incluindo os personagens que viveram neste reino com tons cômicos e Jodevil como protagonista.
     */
    public static ItemStack createVampireLoreBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();

        nbt.putString("title", "Crônicas do Sangue Duvidoso");
        nbt.putString("author", "Conde Jodevil & Asminn");
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);

        // Aparência visual do item "Tome of Vampiric Knowledge" (death_knights:blood_spell_book) via Transmog
        NbtCompound transmogTag = new NbtCompound();
        transmogTag.putString("id", "death_knights:blood_spell_book");
        transmogTag.putByte("Count", (byte) 1);
        nbt.put("transmog:transmogItem", transmogTag);

        // Lore no Tooltip do item
        NbtCompound display = nbt.getCompound("display");
        display.putString("Name", Text.Serializer.toJson(
                Text.literal("§4§lTome of Vampiric Knowledge — Crônicas de Jodevil")));
        NbtList tooltipLore = new NbtList();
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7Grimório vampírico encadernado em couro carmesim").formatted(Formatting.GRAY))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7e manchado de vinho barato e alho.").formatted(Formatting.GRAY))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(""))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§4🩸 Relato oficial da Maldição de Jodevil").formatted(Formatting.DARK_RED))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8\"E o feitiço de ArzanLion jamais foi desfeito...\"").formatted(Formatting.DARK_GRAY))));
        display.put("Lore", tooltipLore);
        nbt.put("display", display);

        // Todas as páginas possuem no máximo 11 a 12 linhas (de 14 possíveis) e <= 20 caracteres por linha
        // para que NENHUMA palavra seja cortada no rodapé da página do Minecraft!
        String[] rawPages = new String[]{
                // Pág 1 - Capa (12 linhas)
                "§4§lCRÔNICAS DO\n" +
                "SANGUE DUVIDOSO§r\n" +
                "§8━━━━━━━━━━━━━━§r\n\n" +
                "§0§oMemórias de um\n" +
                "Imortal Mal-Acabado\n" +
                "e o Desastroso\n" +
                "Ritual de ArzanLion.§r\n\n" +
                "§8Escrito à luz de\n" +
                "velas por Jodevil\n" +
                "& Asminn.",

                // Pág 2 - Cap. I (12 linhas)
                "§4§lI. A Confissão§r\n\n" +
                "§0\"Vejo que trouxeste\n" +
                "a pena e o tinteiro,\n" +
                "§9Asminn§0\", murmurou\n" +
                "§4Jodevil§0, ajustando\n" +
                "sua capa de veludo.\n\n" +
                "\"Queres saber como\n" +
                "perdi a mortalidade?\n" +
                "Senta-te. A noite é\n" +
                "longa e patética.\"",

                // Pág 3 - Cap. II Parte 1 (11 linhas)
                "§4§lII. O Desejo§r\n\n" +
                "§0Por anos caminhei\n" +
                "entre os vivos\n" +
                "desejando a nobreza\n" +
                "dos Antigos.\n\n" +
                "Eu não queria o suor\n" +
                "da lavoura nem a\n" +
                "velhice comum dos\n" +
                "mortais.",

                // Pág 4 - Cap. II Parte 2 (10 linhas)
                "§0Eu ansiava pela\n" +
                "palidez de mármore,\n" +
                "pelo olhar hipnótico\n" +
                "e pelo domínio da\n" +
                "eterna meia-noite.\n\n" +
                "Imaginava-me sobre\n" +
                "os telhados enquanto\n" +
                "sussurravam meu nome\n" +
                "em pavor e respeito.",

                // Pág 5 - Cap. III (11 linhas)
                "§4§lIII. O Alquimista§r\n\n" +
                "§0Como nenhum lorde\n" +
                "noturno dignou-se a\n" +
                "morder-me o pescoço,\n" +
                "busquei outro meio.\n\n" +
                "Recorri ao único que\n" +
                "prometia dobrar a\n" +
                "natureza: o ocultista\n" +
                "de taverna §6ArzanLion§0.",

                // Pág 6 - Cap. IV (12 linhas)
                "§4§lIV. Nota de Asminn§r\n" +
                "§814 de Outubro§r\n\n" +
                "§0O vento uiva nas\n" +
                "ameias. Vi §4Jodevil§0\n" +
                "descer à adega de\n" +
                "§6ArzanLion§0.\n\n" +
                "Jura que renascerá\n" +
                "como Príncipe das\n" +
                "Trevas. Temo pela\n" +
                "sanidade de ambos.",

                // Pág 7 - Cap. V Parte 1 (11 linhas)
                "§4§lV. O Covil Torto§r\n\n" +
                "§0\"Entrei na adega de\n" +
                "§6ArzanLion§0 esperando\n" +
                "cálices de ouro\",\n" +
                "suspirou §4Jodevil§0.\n\n" +
                "\"Mas o círculo de giz\n" +
                "parecia um ovo torto\n" +
                "e o caldeirão cheirava\n" +
                "a sopa de cebola.\"",

                // Pág 8 - Cap. V Parte 2 (10 linhas)
                "§0O ritual exigia o\n" +
                "Sangue dos Cárpatos\n" +
                "e cinzas de três\n" +
                "séculos.\n\n" +
                "Sem moedas, §6ArzanLion§0\n" +
                "improvisou: usou\n" +
                "vinho barato, caldo\n" +
                "de beterraba, alho\n" +
                "mofado e banha.",

                // Pág 9 - Cap. VI Parte 1 (11 linhas)
                "§4§lVI. O Grimório§r\n\n" +
                "§0Para piorar, o\n" +
                "acólito §5AsterZZZz§0\n" +
                "adormecera sobre o\n" +
                "pergaminho sagrado.\n\n" +
                "Sua baba borrou\n" +
                "metade das runas, e\n" +
                "§6ArzanLion§0 inventou as\n" +
                "sílabas que faltavam!",

                // Pág 10 - Cap. VI Parte 2 (11 linhas)
                "§0Quando bebi daquele\n" +
                "cálice duvidoso,\n" +
                "§6ArzanLion§0 ergueu o\n" +
                "cajado e bradou:\n\n" +
                "§o\"Sanguis Noctis...\n" +
                "ATCHIM!\"§r\n\n" +
                "Um espirro colossal!\n" +
                "O livro caiu no fogo\n" +
                "sob fumaça rosa.",

                // Pág 11 - Cap. VII (11 linhas)
                "§4§lVII. O Despertar§r\n\n" +
                "§0Desmaiei esperando\n" +
                "acordar com presas e\n" +
                "a força de cem homens.\n\n" +
                "Acordei com enxaqueca,\n" +
                "gosto de vinagre na\n" +
                "boca e §6ArzanLion§0\n" +
                "dizendo: §o\"Olha...\n" +
                "quase funcionou!\"§r",

                // Pág 12 - Cap. VIII (12 linhas)
                "§4§lVIII. As Roseiras§r\n" +
                "§8Por Kikkawa e Luwuana§r\n\n" +
                "§0\"Vimos §4Jodevil§0 subir\n" +
                "ao parapeito à noite.\n" +
                "Gritou §o'Eu sou a\n" +
                "tormenta!'§0 e saltou\n" +
                "para virar morcego.\n\n" +
                "Não virou morcego.\n" +
                "Caiu de cara nas\n" +
                "roseiras do jardim.\"",

                // Pág 13 - Cap. IX (12 linhas)
                "§4§lIX. Meia-Maldição§r\n\n" +
                "§0O feitiço torto de\n" +
                "§6ArzanLion§0 deixou-me\n" +
                "no limbo entre os\n" +
                "vivos e os mortos.\n\n" +
                "Tenho a sede e a\n" +
                "palidez, mas não\n" +
                "comando lobos e ainda\n" +
                "bato o dedinho nos\n" +
                "móveis no escuro.",

                // Pág 14 - Cap. X (12 linhas)
                "§4§lX. A Inquisição§r\n\n" +
                "§0Desconfiados,\n" +
                "§2Azarasquel§0 e §2Reznozada§0\n" +
                "testaram água benta e\n" +
                "prata contra mim.\n\n" +
                "A prata não me feriu;\n" +
                "só deu coceira no\n" +
                "pescoço, enquanto\n" +
                "§2Reznozada§0 ria até\n" +
                "perder o fôlego.",

                // Pág 15 - Cap. XI Parte 1 (11 linhas)
                "§4§lXI. O Sol Tirano§r\n\n" +
                "§0Ao alvorecer, §8Corvo§0\n" +
                "subiu ao telhado da\n" +
                "taverna recolhendo\n" +
                "apostas da corte.\n\n" +
                "Metade apostava que\n" +
                "eu explodiria ao sol;\n" +
                "a outra metade, que\n" +
                "nada aconteceria.",

                // Pág 16 - Cap. XI Parte 2 (12 linhas)
                "§0Houve apenas um\n" +
                "chiado lento, como\n" +
                "toucinho na chapa.\n\n" +
                "A cada minuto ao sol,\n" +
                "minha vida evapora...\n" +
                "com uma chance em cem\n" +
                "de o feitiço falhar e\n" +
                "reduzir-me a cinzas\n" +
                "na mesma hora!",

                // Pág 17 - Cap. XII (12 linhas)
                "§4§lXII. A Insolação§r\n\n" +
                "§0\"Entendes minha dor,\n" +
                "§9Asminn§0?\" — lamentou\n" +
                "§4Jodevil§0.\n\n" +
                "\"Drácula temia o sol\n" +
                "porque o destruía em\n" +
                "segundos! Eu temo o\n" +
                "meio-dia porque me dá\n" +
                "insolação de um ponto\n" +
                "por minuto!\"",

                // Pág 18 - Cap. XIII Parte 1 (11 linhas)
                "§4§lXIII. O Martelo§r\n\n" +
                "§0Em minha fúria,\n" +
                "tentei impor respeito\n" +
                "sobre §6Jacinto§0, o\n" +
                "servo imortal que\n" +
                "guarda os cofres.\n\n" +
                "Porém, num descuido,\n" +
                "entreguei-lhe minha\n" +
                "arma mais sagrada!",

                // Pág 19 - Cap. XIII Parte 2 (10 linhas)
                "§0Um Conde das Trevas\n" +
                "implorando ao servo\n" +
                "que devolva o martelo!\n\n" +
                "Tentei lançar chamas\n" +
                "contra §6Jacinto§0, mas\n" +
                "ele sequer chamuscou.\n" +
                "Mandou-me pedir com\n" +
                "carinho enquanto toda\n" +
                "a corte gargalhava.",

                // Pág 20 - Cap. XIV (11 linhas)
                "§4§lXIV. Sem Reembolso§r\n" +
                "§8Bilhete de ArzanLion§r\n\n" +
                "§0\"Caro §4Jodevil§0, não\n" +
                "aceito devoluções.\n" +
                "Pediste para sofrer\n" +
                "sob o sol como um\n" +
                "vampiro, e sofres!\n\n" +
                "Sugiro um chapéu de\n" +
                "palha. — §6ArzanLion§0.\"",

                // Pág 21 - Epílogo (12 linhas)
                "§4§lXV. O Fado Eterno§r\n\n" +
                "§0Assim vivo eu, §4Conde\n" +
                "Jodevil§0, vampiro de\n" +
                "meia-tigela do reino.\n\n" +
                "Enquanto o sol brilha,\n" +
                "busco as sombras; pois\n" +
                "a cada minuto na luz,\n" +
                "pago o preço da\n" +
                "alquimia barata.\n" +
                "§4§o— Fim das Crônicas —"
        };

        NbtList pagesList = new NbtList();
        for (String pageContent : rawPages) {
            String jsonPage = Text.Serializer.toJson(Text.literal(pageContent));
            pagesList.add(NbtString.of(jsonPage));
        }
        nbt.put("pages", pagesList);

        return book;
    }
}
