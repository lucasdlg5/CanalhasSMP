package com.canalhas.cummins;

import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * Caçador de Pragas Noturno:
 * - Detecta Phantoms nos céus e Silverfish no chão ao redor do Senhor Cummins.
 * - Dá saltos com patadas sônicas, afugenta e rebate Phantoms que tentam fazer rasante.
 * - Dedetiza Silverfish instantaneamente ao avistá-los.
 * - Solta falas cômicas de vigia noturno no chat com cooldown respeitoso.
 */
public class CumminsNightHunter {
    private final CumminsMod mod;
    private final Random random = new Random();
    private long lastHissAt = 0;
    private long lastSpeechAt = 0;
    private static final long SPEECH_COOLDOWN_MS = 180_000L; // 3 minutos

    public CumminsNightHunter(CumminsMod mod) {
        this.mod = mod;
    }

    public void onTick(CatEntity cat) {
        if (cat == null || !cat.isAlive() || !(cat.getWorld() instanceof ServerWorld sw)) return;

        // Executa a cada 10 ticks (0.5s) para economizar CPU
        if (cat.age % 10 != 0) return;

        Box searchBox = cat.getBoundingBox().expand(26.0, 18.0, 26.0);

        // 1. Caça de Silverfish (Dedetização)
        List<SilverfishEntity> silverfish = sw.getEntitiesByClass(SilverfishEntity.class, searchBox, s -> s.isAlive());
        if (!silverfish.isEmpty()) {
            SilverfishEntity target = silverfish.get(0);
            Vec3d toTarget = target.getPos().subtract(cat.getPos());
            cat.getLookControl().lookAt(target, 60.0f, 60.0f);

            if (toTarget.lengthSquared() < 3.5 * 3.5) {
                target.damage(sw.getDamageSources().mobAttack(cat), 50.0f);
                sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.2, target.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.0f, 1.4f);

                long now = System.currentTimeMillis();
                if (now - lastSpeechAt > SPEECH_COOLDOWN_MS) {
                    lastSpeechAt = now;
                    mod.brain().broadcast("Peguei essa barata de pedra aqui! Dedetização Cummins em plena atividade! 🐾🪲", null);
                }
            } else if (cat.isOnGround()) {
                Vec3d jump = toTarget.normalize().multiply(0.45).add(0, 0.28, 0);
                cat.setVelocity(jump);
                cat.velocityDirty = true;
            }
            return;
        }

        // 2. Caça e Repulsão de Phantoms
        List<PhantomEntity> phantoms = sw.getEntitiesByClass(PhantomEntity.class, searchBox, p -> p.isAlive());
        if (!phantoms.isEmpty()) {
            PhantomEntity phantom = phantoms.get(0);
            cat.getLookControl().lookAt(phantom, 60.0f, 60.0f);

            double distSq = cat.squaredDistanceTo(phantom);
            // Se o phantom estiver fazendo rasante a menos de 20 blocos
            if (distSq < 20.0 * 20.0) {
                long now = System.currentTimeMillis();
                if (now - lastHissAt > 2000L) {
                    lastHissAt = now;

                    // Salto defensivo com unhas pro ar
                    if (cat.isOnGround()) {
                        cat.setVelocity(0, 0.42, 0);
                        cat.velocityDirty = true;
                    }

                    // Som territorial felino estridente
                    sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_HISS, SoundCategory.NEUTRAL, 1.3f, 0.85f);
                    sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.0f, 1.4f);

                    // Efeito de onda de choque sônica em direção ao Phantom
                    Vec3d dir = phantom.getPos().subtract(cat.getPos()).normalize();
                    for (double d = 1.0; d < 8.0; d += 1.2) {
                        Vec3d p = cat.getPos().add(dir.multiply(d));
                        sw.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y + 0.8, p.z, 1, 0, 0, 0, 0);
                    }
                    sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, cat.getX(), cat.getY() + 0.8, cat.getZ(), 4, 0.3, 0.3, 0.3, 0.05);

                    // Aplica dano e repelência violenta no Phantom para longe
                    phantom.damage(sw.getDamageSources().mobAttack(cat), 12.0f);
                    Vec3d push = dir.multiply(1.8).add(0, 0.6, 0);
                    phantom.setVelocity(push);
                    phantom.velocityDirty = true;

                    if (now - lastSpeechAt > SPEECH_COOLDOWN_MS) {
                        lastSpeechAt = now;
                        String[] quotes = new String[]{
                                "Sai pra lá bicho feio! Na minha firma ninguém perde o sono não!",
                                "Toma patada sônica no focinho pra aprender a respeitar o sono dos meus sócios!",
                                "Phantom folgado achou que ia emboscar a firma? Já mandei de volta pra estratosfera! 🐾✨"
                        };
                        mod.brain().broadcast(quotes[random.nextInt(quotes.length)], null);
                    }
                }
            }
        }
    }
}
