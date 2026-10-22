package com.canalhas.jacinto;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Gerenciador de Holograma Flutuante para o Baú Designado (Issue #6):
 * Exibe acima do baú o progresso da mineração passiva em tempo real com ArmorStands invisíveis (Marker).
 * 100% nativo do servidor Vanilla/Fabric, sem exigir mods no cliente.
 */
public class JacintoHologram {
    private ArmorStandEntity titleStand = null;
    private ArmorStandEntity progressStand = null;
    private BlockPos chestPos = null;

    public void update(ServerWorld world, BlockPos pos, String itemName, int currentCount, int totalCapacity) {
        if (world == null || pos == null) return;
        this.chestPos = pos;

        int pct = totalCapacity > 0 ? Math.min(100, (int) Math.round((currentCount * 100.0) / totalCapacity)) : 0;
        String line1 = "§6📦 [Mineração da Firma: §f" + itemName + "§6]";
        String line2 = "§eProgresso: §a" + currentCount + "§7/§e" + totalCapacity + " §7(§b" + pct + "%§7)";

        if (titleStand == null || titleStand.isRemoved()) {
            titleStand = EntityType.ARMOR_STAND.create(world);
            if (titleStand != null) {
                initStand(titleStand, pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5);
                world.spawnEntity(titleStand);
            }
        }

        if (progressStand == null || progressStand.isRemoved()) {
            progressStand = EntityType.ARMOR_STAND.create(world);
            if (progressStand != null) {
                initStand(progressStand, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
                world.spawnEntity(progressStand);
            }
        }

        if (titleStand != null) {
            titleStand.setCustomName(Text.literal(line1));
        }
        if (progressStand != null) {
            progressStand.setCustomName(Text.literal(line2));
        }
    }

    private void initStand(ArmorStandEntity stand, double x, double y, double z) {
        stand.refreshPositionAndAngles(x, y, z, 0, 0);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setSilent(true);
    }

    public void remove() {
        if (titleStand != null && !titleStand.isRemoved()) {
            titleStand.discard();
            titleStand = null;
        }
        if (progressStand != null && !progressStand.isRemoved()) {
            progressStand.discard();
            progressStand = null;
        }
    }
}
