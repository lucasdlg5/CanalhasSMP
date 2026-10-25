package com.canalhas.jacinto;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
     * acompanhado de efeitos visuais/sonoros góticos e a lore do debuff.
     */
    private void applyVampireSunburn(ServerPlayerEntity player, ServerWorld world) {
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
        player.sendMessage(Text.literal("§4🦇 [Maldição do Sangue Duvidoso] §cO sol escalda seu sangue! §4§l-1 HP §7(Passe o mouse no chat para ler a Lore)"), true);

        // Mensagem no chat com Hover da Lore completa do Debuff
        MutableText chatAlert = Text.literal("§8[§4🩸 §c§lMaldição do Sangue Duvidoso§8] ")
                .append(Text.literal("§7O sol drenou §c1 de HP§7 da sua carcaça imortal! ")
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
        t.append(Text.literal("§7Efeito Passivo: §c-1 de HP a cada 1 minuto exposto ao Sol\n"));
        t.append(Text.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        t.append(Text.literal("§f§o\"Tu pediste a eternidade da noite, Jodevil,\n"));
        t.append(Text.literal("§f§omas ArzanLion leu o pergaminho de cabeça para baixo.\"\n\n"));
        t.append(Text.literal("§7Buscando a nobreza sombria dos antigos imortais,\n"));
        t.append(Text.literal("§7Jodevil submeteu-se aos feitiços proibidos de §eArzanLion§7.\n"));
        t.append(Text.literal("§7Porém, na falta de sangue ancestral dos Cárpatos,\n"));
        t.append(Text.literal("§7ArzanLion misturou vinho barato de taverna, alho vencido,\n"));
        t.append(Text.literal("§7suco de beterraba e espirrou no meio do cântico em latim.\n\n"));
        t.append(Text.literal("§7O resultado não foi a majestade de um Lorde Nosferatu,\n"));
        t.append(Text.literal("§7mas um §cVampirismo de Segunda Linha§7: você não vira\n"));
        t.append(Text.literal("§7morcego nem explode em chamas cinematográficas, mas o\n"));
        t.append(Text.literal("§7astro-rei cozinha lentamente sua dignidade, drenando\n"));
        t.append(Text.literal("§c1 ponto de sangue a cada minuto§7 sob o céu aberto.\n"));
        t.append(Text.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        t.append(Text.literal("§6📖 Relíquia: §eHá um Baú em seu leito de repouso (-678, 94, -271)\n"));
        t.append(Text.literal("§e   com o livro completo das Crônicas de sua transformação!"));
        return t;
    }

    public void sendDebuffWelcomeBanner(ServerPlayerEntity player) {
        MutableText msg = Text.literal("\n§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                .append(Text.literal("  §4🦇 §c§lMALDIÇÃO ATIVA: §4§lSANGUE DUVIDOSO DE ARZANLION §4🦇\n"))
                .append(Text.literal("  §7§o\"O sol é um tirano para aqueles cujo ritual deu errado...\"\n\n"))
                .append(Text.literal("  §fEfeito Permanente: §cAo sair ao sol, você perde §4§l1 HP§c a cada minuto.\n"))
                .append(Text.literal("  §fLore do Debuff: "))
                .append(Text.literal("§6§l[Passe o Mouse Aqui ou Clique para Ler]")
                        .setStyle(Style.EMPTY
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildDebuffLoreHoverText()))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vampiro"))))
                .append(Text.literal("\n  §7Um baú com as §eCrônicas do Sangue Duvidoso§7 aguarda em seu leito.\n"))
                .append(Text.literal("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"));
        player.sendMessage(msg, false);
    }

    public void sendFullDebuffLoreToPlayer(ServerPlayerEntity player) {
        player.sendMessage(buildDebuffLoreHoverText(), false);
    }

    /**
     * Cria o Baú no ponto de spawn salvo de Jodevil (-678, 94, -271) e deposita o Livro de Lore.
     */
    public BlockPos ensureLoreChestSpawned(MinecraftServer server, boolean forceRecreate) {
        JacintoData data = mod.getData();
        if (data.jodevilLoreChestCreated && !forceRecreate) {
            return new BlockPos(data.jodevilLoreChestX, data.jodevilLoreChestY, data.jodevilLoreChestZ);
        }

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return null;

        // Determina o ponto de spawn salvo de Jodevil (usa o player online se tiver cama ativa, senão o salvo no .dat)
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

        // Escolhe a posição ideal ao lado ou exatamente no ponto de spawn sem destruir camas ou baús existentes
        BlockPos chestPos = findSafeChestSpotNearSpawn(overworld, targetSpawn);

        BlockState existingState = overworld.getBlockState(chestPos);
        if (!(existingState.getBlock() instanceof ChestBlock)) {
            overworld.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 3);
        }

        BlockEntity be = overworld.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.literal("§4§l⚰️ Relíquias Noturnas de Conde Jodevil"));

            ItemStack loreBook = createVampireLoreBook();
            // Coloca o livro no centro do baú (slot 13) ou no primeiro slot livre caso o baú já tivesse itens
            if (chest.getStack(13).isEmpty() || chest.getStack(13).isOf(Items.WRITTEN_BOOK)) {
                chest.setStack(13, loreBook);
            } else {
                boolean placed = false;
                for (int i = 0; i < chest.size(); i++) {
                    if (chest.getStack(i).isEmpty()) {
                        chest.setStack(i, loreBook);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    chest.setStack(0, loreBook);
                }
            }
            chest.markDirty();
        }

        // Protege o baú para que o Jacinto NUNCA retire o livro durante o /organizar!
        data.setChestRule(overworld.getRegistryKey().getValue().toString(), chestPos, false, false);

        data.jodevilLoreChestCreated = true;
        data.jodevilLoreChestX = chestPos.getX();
        data.jodevilLoreChestY = chestPos.getY();
        data.jodevilLoreChestZ = chestPos.getZ();
        data.jodevilLoreChestDimension = overworld.getRegistryKey().getValue().toString();
        data.save();

        LOG.info("[JodevilVampire] Baú de Lore Vampírica criado com sucesso em ({}, {}, {})!",
                chestPos.getX(), chestPos.getY(), chestPos.getZ());

        if (onlineJodevil != null) {
            onlineJodevil.sendMessage(Text.literal("§4🦇 [Crônicas da Noite] §7Um baú antigo contendo o tomo §c\"Crônicas do Sangue Duvidoso\"§7 foi deixado junto ao seu leito em §e(" +
                    chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ() + ")§7."), false);
        }

        return chestPos;
    }

    private BlockPos findSafeChestSpotNearSpawn(ServerWorld world, BlockPos spawnPos) {
        // 1. Se já houver um baú da lore em um raio de 2 blocos, reutiliza
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = spawnPos.add(dx, dy, dz);
                    if (world.getBlockState(p).getBlock() instanceof ChestBlock) {
                        BlockEntity be = world.getBlockEntity(p);
                        if (be instanceof ChestBlockEntity chest && chest.hasCustomName()) {
                            String name = chest.getCustomName().getString();
                            if (name.contains("Jodevil") || name.contains("Relíquias")) {
                                return p;
                            }
                        }
                    }
                }
            }
        }

        // 2. Se o próprio ponto salvo for ar ou substituível (e não for cama), usa ele mesmo
        if (isValidNewChestSpot(world, spawnPos)) {
            return spawnPos;
        }

        // 3. Procura nos blocos cardeais e diagonais imediatos ao redor da cama/ponto de spawn
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        for (int[] off : offsets) {
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos candidate = spawnPos.add(off[0], dy, off[1]);
                if (isValidNewChestSpot(world, candidate)) {
                    return candidate;
                }
            }
        }

        // Fallback seguro: 1 bloco acima do ponto de spawn caso tudo ao redor esteja ocupado
        BlockPos above = spawnPos.up();
        if (world.getBlockState(above).isAir()) {
            return above;
        }
        return spawnPos;
    }

    private boolean isValidNewChestSpot(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) {
            return false;
        }
        BlockState below = world.getBlockState(pos.down());
        return below.isSolidBlock(world, pos.down()) || !below.isAir();
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

        // Lore no Tooltip do item
        NbtCompound display = nbt.getCompound("display");
        NbtList tooltipLore = new NbtList();
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7Manuscrito encadernado em couro carmesim").formatted(Formatting.GRAY))));
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

        String[] rawPages = new String[]{
                // Pág 1 - Capa
                "§4§lCRÔNICAS DO\nSANGUE DUVIDOSO§r\n" +
                "§8━━━━━━━━━━━━━━━§r\n\n" +
                "§0§oMemórias de um Imortal Mal-Acabado e o Desastroso Ritual de ArzanLion.§r\n\n" +
                "§8Compilado nas Criptas da Cidadela sob a luz de velas de sebo.",

                // Pág 2 - Prólogo (Estilo Entrevista com o Vampiro)
                "§4§lI. A Confissão à Vela§r\n\n" +
                "§0\"Vejo que preparaste a pena e o tinteiro, §9Asminn§0\", murmurou §4Jodevil§0, ajustando sua capa de veludo puído.\n\n" +
                "\"Queres saber como abandonei a mortalidade? Senta-te. A noite é longa, e minha tragédia... é patética.\"",

                // Pág 3 - A Vaidade de Jodevil
                "§4§lII. O Desejo Escarlate§r\n\n" +
                "§0Por anos caminhei entre os vivos desejando a majestade dos Antigos.\n\n" +
                "Eu não queria o suor da lavoura nem a velhice comum. Eu ansiava pela palidez de mármore, pelo charme hipnótico e pelo domínio da eterna meia-noite.",

                // Pág 4 - A Linhagem Imaginada (A Rainha dos Condenados)
                "§4§lIII. O Sonho Antigo§r\n\n" +
                "§0Sonhava com os tronos de alabastro descritos nos pergaminhos da Mãe de Todos os Imortais.\n\n" +
                "Imaginava-me planando sobre os telhados da província enquanto mortais sussurravam meu nome em reverência e pavor.",

                // Pág 5 - A Escolha do Alquimista
                "§4§lIV. O Erro Fatal§r\n\n" +
                "§0Como nenhuma criatura ancestral se dignou a morder-me o pescoço, recorri à única mente que prometia dobrar as leis da natureza:\n\n" +
                "O autoproclamado Grão-Mago e ocultista de taverna, §6ArzanLion§0.",

                // Pág 6 - Diário de Asminn (Estilo Drácula de Bram Stoker)
                "§4§lV. Diário de Asminn§r\n" +
                "§814 de Outubro, Meia-Noite§r\n\n" +
                "§0O vento uiva nas ameias. Vi §4Jodevil§0 descer à adega subterrânea de §6ArzanLion§0.\n\n" +
                "Dizia ele que hoje renasceria como Príncipe das Trevas. Temo, contudo, pela sanidade de ambos.",

                // Pág 7 - O Laboratório de ArzanLion
                "§4§lVI. O Círculo Torto§r\n\n" +
                "§0\"Entrei no covil de §6ArzanLion§0 esperando cálices de ouro\", confessou §4Jodevil§0 com um suspiro fúnebre.\n\n" +
                "\"Em vez disso, o círculo de giz parecia um ovo amassado, e o caldeirão cheirava suspeitamente a sopa de cebola.\"",

                // Pág 8 - Os Ingredientes Duvidosos
                "§4§lVII. A Receita Profana§r\n\n" +
                "§0O ritual exigia o Sangue dos Cárpatos e cinzas de três séculos.\n\n" +
                "Mas §6ArzanLion§0, sem moedas para o mercador, improvisou: usou vinho tinto azedo, caldo de beterraba, dois dentes de alho mofados e banha de porco.",

                // Pág 9 - O Cochilo de AsterZZZz
                "§4§lVIII. O Pergaminho§r\n\n" +
                "§0Para piorar, o acólito §5AsterZZZz§0 adormecera sobre o grimório sagrado horas antes.\n\n" +
                "A baba de §5AsterZZZz§0 borrou metade das runas abissais, e §6ArzanLion§0 simplesmente inventou as sílabas que faltavam no improviso.",

                // Pág 10 - O Espirro Arcano
                "§4§lIX. O Clímax Falho§r\n\n" +
                "§0Quando os trovões rugiram e bebi daquele cálice duvidoso, §6ArzanLion§0 ergueu o cajado e bradou:\n" +
                "§o\"Sanguis Noctis... ATCHIM!\"§r\n\n" +
                "Um espirro colossal. O livro caiu no braseiro e uma fumaça cor-de-rosa tomou o salão.",

                // Pág 11 - O Despertar
                "§4§lX. O Despertar§r\n\n" +
                "§0Desmaiei esperando acordar com presas afiadas e a força de cem homens.\n\n" +
                "Despertei três horas depois com uma enxaqueca terrível, gosto de vinagre na língua e §6ArzanLion§0 dizendo: §o\"Olha... tecnicamente funcionou!\"§r",

                // Pág 12 - Diário de Kikkawa e Luwuana
                "§4§lXI. Cartas da Corte§r\n" +
                "§8Relato de Kikkawa e Luwuana§r\n\n" +
                "§0\"Naquela madrugada, vimos §4Jodevil§0 subir ao parapeito do jardim. Gritou §o'Eu sou a tormenta!'§0 e saltou tentando transformar-se em morcego.\n\n" +
                "Não virou morcego. Caiu de cara nas roseiras.\"",

                // Pág 13 - O Diagnóstico
                "§4§lXII. A Meia-Maldição§r\n\n" +
                "§0O feitiço torto de §6ArzanLion§0 deixou-me no limbo entre os vivos e os mortos.\n\n" +
                "Tenho a sede noturna e a palidez cadavérica, mas não comando lobos, não atravesso paredes e ainda bato o dedinho nos móveis no escuro.",

                // Pág 14 - A Inquisição de Azarasquel e Reznozada
                "§4§lXIII. A Prova de Fogo§r\n\n" +
                "§0Desconfiados, §2Azarasquel§0 e §2Reznozada§0 tentaram exorcizar-me na praça com água benta e prata.\n\n" +
                "A prata não me queimou; apenas deixou-me com coceira no pescoço, enquanto §2Reznozada§0 ria até perder o fôlego.",

                // Pág 15 - A Apostas de Corvo
                "§4§lXIV. O Primeiro Sol§r\n\n" +
                "§0Ao alvorecer, §8Corvo§0 empoleirou-se no telhado da taverna recolhendo moedas de apostas.\n\n" +
                "Metade da província apostava que eu explodiria em cinzas ao primeiro raio de sol; a outra metade, que nada aconteceria.",

                // Pág 16 - O Sol Humilhante
                "§4§lXV. A Queimadura Lenta§r\n\n" +
                "§0Ambos erraram. Quando o sol tocou minha face, não houve explosão gloriosa.\n\n" +
                "Houve apenas um chiado lento, como toucinho na frigideira. A cada minuto sob o astro-rei, sinto uma gota de minha vida evaporar.",

                // Pág 17 - A Humilhação Solar
                "§4§lXVI. O Tributo do Sol§r\n\n" +
                "§0\"Compreendes minha dor, §9Asminn§0?\" — choramingou §4Jodevil§0.\n\n" +
                "\"Drácula temia a aurora porque ela o destruía em segundos! Eu temo o meio-dia porque ele me dá insolação crônica de um ponto por minuto!\"",

                // Pág 18 - O Encontro com Jacinto
                "§4§lXVII. O Servo Imortal§r\n\n" +
                "§0Em minha fúria noturna, tentei impor respeito sobre §6Jacinto§0, o incansável guardião ressecado dos cofres da cidadela.\n\n" +
                "Mas num descuido digno de minha sina, entreguei-lhe minha relíquia de guerra mais preciosa.",

                // Pág 19 - A Humilhação de Mjölnir
                "§4§lXVIII. O Martelo Perdido§r\n\n" +
                "§0Um Conde das Trevas implorando a um servo de armazém que lhe devolva o martelo!\n\n" +
                "Tentei conjurar chamas contra §6Jacinto§0, mas o maldito sequer chamuscou. Disse-me apenas para pedir com educação, enquanto toda a corte gargalhava.",

                // Pág 20 - A Defesa de ArzanLion
                "§4§lXIX. Nota de ArzanLion§r\n" +
                "§8Anexo deixado na porta§r\n\n" +
                "§0\"Caro §4Jodevil§0, não aceito devoluções. Tu pediste para sofrer sob a luz do sol como um verdadeiro vampiro, e agora sofres!\n\n" +
                "Recomendo um chapéu de palha. Com carinho, §6ArzanLion§0.\"",

                // Pág 21 - Epílogo
                "§4§lXX. O Fado Eterno§r\n\n" +
                "§0Assim vivo eu, §4Conde Jodevil§0, único vampiro de meia-tigela destas terras.\n\n" +
                "Enquanto o sol reinar no alto, escondo-me nas sombras; pois a cada minuto sob sua luz, pago com sangue o preço da alquimia barata.\n\n" +
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
