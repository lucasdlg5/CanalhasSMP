package com.canalhas.cummins;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Mutirão da Firma:
 * Quando dois ou mais funcionários trabalham juntos na mesma área (distância <= 28 blocos),
 * o espírito de equipe da firma ativa o bônus de Mutirão: Pressa I (Haste I) e Agilidade I (Speed I).
 * Gera partículas sutis de entusiasmo coletivo.
 */
public class CumminsMutiraoManager {
    private final CumminsMod mod;
    private int tickCounter = 0;
    private final Set<UUID> playersInMutirao = new HashSet<>();

    public CumminsMutiraoManager(CumminsMod mod) {
        this.mod = mod;
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 60 != 0) return; // Checa a cada 3 segundos (60 ticks)

        List<ServerPlayerEntity> playerList = server.getPlayerManager().getPlayerList();
        if (playerList.size() < 2) {
            playersInMutirao.clear();
            return;
        }

        Set<UUID> currentActive = new HashSet<>();

        // Agrupa por mundo
        Map<ServerWorld, List<ServerPlayerEntity>> byWorld = new HashMap<>();
        for (ServerPlayerEntity p : playerList) {
            if (p != null && !p.isSpectator() && p.isAlive()) {
                byWorld.computeIfAbsent((ServerWorld) p.getWorld(), k -> new ArrayList<>()).add(p);
            }
        }

        double radiusSq = 28.0 * 28.0;

        for (Map.Entry<ServerWorld, List<ServerPlayerEntity>> entry : byWorld.entrySet()) {
            ServerWorld world = entry.getKey();
            List<ServerPlayerEntity> players = entry.getValue();
            if (players.size() < 2) continue;

            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity p1 = players.get(i);
                for (int j = i + 1; j < players.size(); j++) {
                    ServerPlayerEntity p2 = players.get(j);
                    if (p1.squaredDistanceTo(p2) <= radiusSq) {
                        currentActive.add(p1.getUuid());
                        currentActive.add(p2.getUuid());
                    }
                }
            }

            for (ServerPlayerEntity p : players) {
                if (currentActive.contains(p.getUuid())) {
                    // Aplica Haste I (amplifier 0) e Speed I por 160 ticks (8s)
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 160, 0, true, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 0, true, false, true));

                    // Partículas de entusiasmo
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0.05);

                    if (!playersInMutirao.contains(p.getUuid())) {
                        p.sendMessage(Text.literal("⚡ §6[Mutirão da Firma] §eTrabalho em equipe ativado! §a(+Pressa I & +Agilidade I)"), true);
                    }
                }
            }
        }

        playersInMutirao.clear();
        playersInMutirao.addAll(currentActive);
    }
}
