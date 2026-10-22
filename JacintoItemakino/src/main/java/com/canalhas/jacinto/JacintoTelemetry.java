package com.canalhas.jacinto;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Exportador de Telemetria do Jacinto para o Dashboard C# e Canalhas Mobile App:
 * Salva periodicamente em config/jacinto/telemetry.json os dados em tempo real do operário.
 */
public final class JacintoTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger("JacintoTelemetry");
    private static final File CONFIG_DIR = new File("config/jacinto");
    private static final File TELEMETRY_FILE = new File(CONFIG_DIR, "telemetry.json");

    private JacintoTelemetry() {}

    public static void export(JacintoMod mod, MinecraftServer server) {
        if (mod == null || server == null) return;
        try {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }

            JacintoData data = mod.getData();
            HuskEntity entity = mod.getNpc().getEntity();

            String task = data.currentTask != null ? data.currentTask : "IDLE";
            boolean isMining = mod.getMiner().isMining();
            boolean isOrganizing = mod.getOrganizer().isBusy();

            ItemStack pick = data.getEffectivePickaxe();
            String pickName = !pick.isEmpty() ? pick.getName().getString() : "Mão Vazia";
            int pickDmg = pick.getDamage();
            int pickMaxDmg = pick.getMaxDamage();
            int pickTier = JacintoMiningCompatibility.getPickaxeMiningLevel(pick);
            String pickTierName = JacintoMiningCompatibility.getTierName(pickTier);

            double x = entity != null ? entity.getX() : data.miningX;
            double y = entity != null ? entity.getY() : data.miningY;
            double z = entity != null ? entity.getZ() : data.miningZ;
            String dim = entity != null ? entity.getWorld().getRegistryKey().getValue().toString() : (data.miningDimension != null ? data.miningDimension : "minecraft:overworld");

            int maxCap = JacintoMiner.getMaxCarriedCapacity(entity);

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"timestamp\": \"").append(Instant.now().toString()).append("\",\n");
            json.append("  \"status\": \"").append(isMining ? "MINING" : (isOrganizing ? "ORGANIZING" : "IDLE")).append("\",\n");
            json.append("  \"currentTask\": \"").append(task).append("\",\n");
            json.append("  \"miningTarget\": \"").append(data.miningTarget != null ? data.miningTarget : "").append("\",\n");
            json.append("  \"hasMiningChest\": ").append(data.hasMiningChest).append(",\n");
            json.append("  \"miningChest\": {\n");
            json.append("    \"x\": ").append(data.miningChestX).append(",\n");
            json.append("    \"y\": ").append(data.miningChestY).append(",\n");
            json.append("    \"z\": ").append(data.miningChestZ).append(",\n");
            json.append("    \"dimension\": \"").append(data.miningChestDimension != null ? data.miningChestDimension : "").append("\"\n");
            json.append("  },\n");
            json.append("  \"location\": {\n");
            json.append("    \"x\": ").append(String.format(java.util.Locale.US, "%.1f", x)).append(",\n");
            json.append("    \"y\": ").append(String.format(java.util.Locale.US, "%.1f", y)).append(",\n");
            json.append("    \"z\": ").append(String.format(java.util.Locale.US, "%.1f", z)).append(",\n");
            json.append("    \"dimension\": \"").append(dim).append("\"\n");
            json.append("  },\n");
            json.append("  \"heldTool\": {\n");
            json.append("    \"name\": \"").append(pickName.replace("\"", "\\\"")).append("\",\n");
            json.append("    \"tier\": \"").append(pickTierName).append("\",\n");
            json.append("    \"damage\": ").append(pickDmg).append(",\n");
            json.append("    \"maxDamage\": ").append(pickMaxDmg).append("\n");
            json.append("  },\n");
            json.append("  \"carriedMinedCount\": ").append(data.carriedMinedCount).append(",\n");
            json.append("  \"carriedCapacity\": ").append(maxCap).append(",\n");
            json.append("  \"totalBlocksMined\": ").append(data.totalBlocksMined).append(",\n");
            json.append("  \"totalItemsOrganized\": ").append(data.totalItemsOrganized).append(",\n");
            json.append("  \"glowing\": ").append(mod.getNpc().isGlowingSilhouette()).append(",\n");
            json.append("  \"isAlive\": ").append(entity != null && entity.isAlive()).append("\n");
            json.append("}");

            try (FileWriter fw = new FileWriter(TELEMETRY_FILE, StandardCharsets.UTF_8)) {
                fw.write(json.toString());
            }
        } catch (Exception e) {
            LOG.warn("Erro ao exportar telemetria do Jacinto: {}", e.getMessage());
        }
    }
}
