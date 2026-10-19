package com.canalhas.cummins;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Café da Madrugada do Tio Cummins:
 * Distribui o lendário café passado na hora da firma e pão quentinho.
 * O Cafézin Preto concede Visão Noturna (5 min), Agilidade I (3 min), Pressa I (3 min) e Nutrição instantânea!
 */
public class CumminsCafeManager {
    private final CumminsMod mod;
    // Cooldown de 15 minutos reais entre cafés por jogador (ou 1 por noite)
    private final Map<UUID, Long> lastCafeTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 15 * 60 * 1000L;

    public CumminsCafeManager(CumminsMod mod) {
        this.mod = mod;
    }

    public static ItemStack createCafezin() {
        ItemStack potion = new ItemStack(Items.POTION);
        NbtCompound tag = potion.getOrCreateNbt();

        // Cor de café expresso escuro (#3E2723)
        tag.putInt("CustomPotionColor", 0x3E2723);

        // Display e Lore
        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serializer.toJson(Text.literal("☕ Cafézin Preto da Firma").formatted(Formatting.GOLD, Formatting.BOLD)));
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Passado fresquinho no coador de pano pelo Cummins.").formatted(Formatting.GRAY))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eAquece a alma e sustenta o turno da madrugada!").formatted(Formatting.YELLOW))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§b• Visão Noturna (5:00)").formatted(Formatting.AQUA))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§b• Agilidade I (3:00)").formatted(Formatting.AQUA))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§b• Pressa no Trabalho I (3:00)").formatted(Formatting.AQUA))));
        display.put("Lore", lore);
        tag.put("display", display);

        // Efeitos NBT nativos do Minecraft (compatível com clientes vanilla e modded)
        NbtList effects = new NbtList();

        // 1. Visão Noturna (5 min = 6000 ticks)
        NbtCompound effNightVision = new NbtCompound();
        effNightVision.putByte("Id", (byte) Registries.STATUS_EFFECT.getRawId(StatusEffects.NIGHT_VISION));
        effNightVision.putInt("Duration", 6000);
        effNightVision.putByte("Amplifier", (byte) 0);
        effects.add(effNightVision);

        // 2. Agilidade / Speed (3 min = 3600 ticks)
        NbtCompound effSpeed = new NbtCompound();
        effSpeed.putByte("Id", (byte) Registries.STATUS_EFFECT.getRawId(StatusEffects.SPEED));
        effSpeed.putInt("Duration", 3600);
        effSpeed.putByte("Amplifier", (byte) 0);
        effects.add(effSpeed);

        // 3. Pressa / Haste (3 min = 3600 ticks)
        NbtCompound effHaste = new NbtCompound();
        effHaste.putByte("Id", (byte) Registries.STATUS_EFFECT.getRawId(StatusEffects.HASTE));
        effHaste.putInt("Duration", 3600);
        effHaste.putByte("Amplifier", (byte) 0);
        effects.add(effHaste);

        // 4. Saturação instantânea
        NbtCompound effSat = new NbtCompound();
        effSat.putByte("Id", (byte) Registries.STATUS_EFFECT.getRawId(StatusEffects.SATURATION));
        effSat.putInt("Duration", 20);
        effSat.putByte("Amplifier", (byte) 1);
        effects.add(effSat);

        tag.put("CustomPotionEffects", effects);
        return potion;
    }

    public static ItemStack createPaoNaChapa() {
        ItemStack bread = new ItemStack(Items.BREAD, 2);
        NbtCompound tag = bread.getOrCreateNbt();

        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serializer.toJson(Text.literal("🥖 Pão na Chapa do Posto").formatted(Formatting.GOLD, Formatting.BOLD)));
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Pão francês tostadinho com manteiga de primeira.").formatted(Formatting.GRAY))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eAcompanhamento oficial da pausa de descanso.").formatted(Formatting.YELLOW))));
        display.put("Lore", lore);
        tag.put("display", display);

        return bread;
    }

    public int serveCafe(ServerPlayerEntity player) {
        if (player == null) return 0;
        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();
        Long lastTime = lastCafeTime.get(uuid);

        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            long remainingSec = (COOLDOWN_MS - (now - lastTime)) / 1000L;
            long min = remainingSec / 60;
            long sec = remainingSec % 60;
            player.sendMessage(Text.literal(String.format("⏳ §cO bule do Cummins ainda está fervendo! Aguarde §e%02d:%02d §cpara a próxima rodada de café.", min, sec)), false);
            return 0;
        }

        lastCafeTime.put(uuid, now);

        ItemStack cafe = createCafezin();
        ItemStack pao = createPaoNaChapa();

        if (!player.getInventory().insertStack(cafe)) {
            player.dropItem(cafe, false);
        }
        if (!player.getInventory().insertStack(pao)) {
            player.dropItem(pao, false);
        }

        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BOTTLE_FILL,
                SoundCategory.PLAYERS,
                1.0f, 1.0f
        );

        player.sendMessage(Text.literal("☕ §6[Tio Cummins] §aAqui está, chefia! Um §eCafézin Preto da Firma §ae dois §ePães na Chapa §apara aguentar o serviço!"), false);
        return 1;
    }
}
