package com.canalhas.cummins;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutirão da Firma:
 * Quando dois ou mais funcionários trabalham juntos na mesma área (distância <= 28 blocos):
 * - O buff inicial dura 5 minutos (Pressa I e Agilidade I).
 * - Ao se renovar enquanto ainda estiverem próximos, o buff se aprimora para o próximo nível
 *   (Nível II, Nível III, Nível IV, até o Nível Máximo V) por mais 5 minutos consecutivamente.
 * - Sons dinâmicos de level-up e partículas celebram o ritmo de trabalho em equipe.
 */
public class CumminsMutiraoManager {
    public static final int BUFF_DURATION_TICKS = 5 * 60 * 20; // 5 minutos = 6000 ticks
    public static final double PROXIMITY_RADIUS_BLOCKS = 28.0;
    public static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS_BLOCKS * PROXIMITY_RADIUS_BLOCKS;
    public static final int MAX_LEVEL = 5;

    public static class MutiraoState {
        public int level = 1;
        public long levelStartedAtEpochMs = System.currentTimeMillis();
        public boolean hasActiveBuff = false;
        public final List<String> nearbyTeammates = new ArrayList<>();
    }

    private final CumminsMod mod;
    private int tickCounter = 0;
    private final Map<UUID, MutiraoState> states = new ConcurrentHashMap<>();

    public CumminsMutiraoManager(CumminsMod mod) {
        this.mod = mod;
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return; // Avalia a cada 1 segundo (20 ticks)

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        if (onlinePlayers.size() < 2) {
            // Menos de 2 jogadores online: limpa estados inativos
            for (ServerPlayerEntity p : onlinePlayers) {
                if (p != null && p.getStatusEffect(StatusEffects.HASTE) == null) {
                    states.remove(p.getUuid());
                }
            }
            return;
        }

        // Agrupa jogadores vivos e não-espectadores por dimensão
        Map<ServerWorld, List<ServerPlayerEntity>> byWorld = new HashMap<>();
        for (ServerPlayerEntity p : onlinePlayers) {
            if (p != null && !p.isSpectator() && p.isAlive()) {
                byWorld.computeIfAbsent((ServerWorld) p.getWorld(), k -> new ArrayList<>()).add(p);
            }
        }

        // Mapeia quem está próximo de quem nesta rodada
        Map<UUID, List<String>> currentProximity = new HashMap<>();

        for (Map.Entry<ServerWorld, List<ServerPlayerEntity>> entry : byWorld.entrySet()) {
            List<ServerPlayerEntity> players = entry.getValue();
            if (players.size() < 2) continue;

            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity p1 = players.get(i);
                for (int j = i + 1; j < players.size(); j++) {
                    ServerPlayerEntity p2 = players.get(j);
                    if (p1.squaredDistanceTo(p2) <= PROXIMITY_RADIUS_SQ) {
                        currentProximity.computeIfAbsent(p1.getUuid(), k -> new ArrayList<>()).add(p2.getGameProfile().getName());
                        currentProximity.computeIfAbsent(p2.getUuid(), k -> new ArrayList<>()).add(p1.getGameProfile().getName());
                    }
                }
            }
        }

        // Processa o ciclo de cada jogador
        for (ServerPlayerEntity p : onlinePlayers) {
            if (p == null || p.isSpectator() || !p.isAlive()) continue;

            UUID uuid = p.getUuid();
            boolean isNear = currentProximity.containsKey(uuid);
            List<String> nearbyNames = currentProximity.getOrDefault(uuid, Collections.emptyList());

            MutiraoState state = states.computeIfAbsent(uuid, k -> new MutiraoState());
            state.nearbyTeammates.clear();
            state.nearbyTeammates.addAll(nearbyNames);

            StatusEffectInstance haste = p.getStatusEffect(StatusEffects.HASTE);

            if (haste == null) {
                // Jogador está sem o efeito do buff
                if (isNear) {
                    // Início de um novo mutirão: Nível 1 por 5 minutos
                    state.level = 1;
                    state.levelStartedAtEpochMs = System.currentTimeMillis();
                    state.hasActiveBuff = true;

                    applyMutiraoEffects(p, 1, BUFF_DURATION_TICKS);

                    ServerWorld sw = (ServerWorld) p.getWorld();
                    sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.35, 0.45, 0.35, 0.05);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.0f);

                    p.sendMessage(Text.literal("⚡ §6[Mutirão da Firma] §eTrabalho em equipe ativado! §a(Nível I: Pressa I & Agilidade I por 5 min)"), true);
                } else {
                    // Sem buff e sem ninguém por perto: reinicia nível para quando reencontrar colegas
                    state.hasActiveBuff = false;
                    state.level = 1;
                }
            } else {
                // Jogador já possui o efeito ativo
                int remainingTicks = haste.getDuration();

                // Hora de renovar? (quando faltam 60 ticks ou menos para acabar os 5 minutos)
                if (remainingTicks <= 60) {
                    if (isNear) {
                        // RENOVAÇÃO COM APRIMORAMENTO DE NÍVEL!
                        int nextLevel = Math.min(state.level + 1, MAX_LEVEL);
                        state.level = nextLevel;
                        state.levelStartedAtEpochMs = System.currentTimeMillis();
                        state.hasActiveBuff = true;

                        applyMutiraoEffects(p, nextLevel, BUFF_DURATION_TICKS);

                        ServerWorld sw = (ServerWorld) p.getWorld();
                        sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 14, 0.4, 0.5, 0.4, 0.1);
                        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.35, 0.45, 0.35, 0.05);

                        float pitch = 0.9f + (nextLevel * 0.15f);
                        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, pitch);

                        String roman = toRoman(nextLevel);
                        if (nextLevel >= MAX_LEVEL) {
                            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.8f, 1.1f);
                            p.sendMessage(Text.literal("⚡ §6[Mutirão da Firma] §6§l★ NÍVEL MÁXIMO " + roman + "! ★ §eTrabalho incansável renovado por 5 min! (+Pressa " + roman + " & +Agilidade " + roman + ")"), true);
                        } else {
                            p.sendMessage(Text.literal("⚡ §6[Mutirão da Firma] §a§l▲ APRIMORADO PARA NÍVEL " + roman + "! §eTrabalho em equipe contínuo! (+Pressa " + roman + " & +Agilidade " + roman + " por 5 min)"), true);
                        }
                    } else {
                        // O buff está expirando e o jogador está longe de outros colegas: deixamos expirar
                        state.hasActiveBuff = false;
                    }
                } else {
                    // Buff em andamento (restam mais de 3s dos 5 minutos)
                    // Partículas de entusiasmo a cada 10 segundos quando estiverem próximos
                    if (isNear && tickCounter % 200 == 0) {
                        ServerWorld sw = (ServerWorld) p.getWorld();
                        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.1, p.getZ(), 3, 0.25, 0.35, 0.25, 0.04);
                    }
                }
            }
        }
    }

    private void applyMutiraoEffects(ServerPlayerEntity player, int level, int durationTicks) {
        int amplifier = Math.max(0, level - 1);
        // Pressa (Haste) e Agilidade (Speed) pelo tempo determinado com ícone visível na interface
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, durationTicks, amplifier, true, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, durationTicks, amplifier, true, false, true));
    }

    public MutiraoState getPlayerState(UUID playerUuid) {
        return states.get(playerUuid);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player != null) {
            states.remove(player.getUuid());
        }
    }

    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(number);
        };
    }
}
