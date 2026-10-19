package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Sistema de Contratos / Tarefas da Firma:
 * - Gera contratos rotativos diários para a manutenção da firma (Peugeot 206, caldeira, fábrica).
 * - Permite aos jogadores consultarem com /contrato e entregarem os recursos com /entregar.
 * - Recompensa os trabalhadores com bônus de produtividade, minérios e comida da diretoria.
 */
public class CumminsContractsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final CumminsMod mod;
    private final Path file;

    public static class ContractTemplate {
        public String id;
        public String title;
        public String description;
        public String requiredItemId;
        public int requiredAmount;
        public String rewardDescription;
        public List<String> rewardItemIds;
        public List<Integer> rewardAmounts;

        public ContractTemplate() {}

        public ContractTemplate(String id, String title, String description, String requiredItemId, int requiredAmount,
                                String rewardDescription, List<String> rewardItemIds, List<Integer> rewardAmounts) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.requiredItemId = requiredItemId;
            this.requiredAmount = requiredAmount;
            this.rewardDescription = rewardDescription;
            this.rewardItemIds = rewardItemIds;
            this.rewardAmounts = rewardAmounts;
        }
    }

    public static class ContractStore {
        public String currentDate = "";
        public int currentTemplateIndex = 0;
        public Map<String, String> completionsByPlayerDate = new HashMap<>(); // "player#date" -> contractId
    }

    private ContractStore store = new ContractStore();
    private final List<ContractTemplate> templates = new ArrayList<>();

    public CumminsContractsManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.file = configDir.resolve("contratos.json");
        initTemplates();
        load();
        checkDayRotation();
    }

    private void initTemplates() {
        templates.add(new ContractTemplate(
                "motor_peugeot",
                "Revisão do Motor do Peugeot 206",
                "A diretoria precisa de 32 Barras de Cobre para refazer o chicote elétrico do alternador.",
                "minecraft:copper_ingot", 32,
                "2x Diamantes + 8x Pães da Diretoria",
                List.of("minecraft:diamond", "minecraft:bread"),
                List.of(2, 8)
        ));

        templates.add(new ContractTemplate(
                "estofamento_bancos",
                "Estofamento da Van da Firma",
                "Precisamos de 16 Couros para forrar os bancos rasgados dos veículos da fábrica.",
                "minecraft:leather", 16,
                "1x Barra de Ouro + 4x Salmões Grelhados",
                List.of("minecraft:gold_ingot", "minecraft:cooked_salmon"),
                List.of(1, 4)
        ));

        templates.add(new ContractTemplate(
                "caldeira_vapor",
                "Combustível para a Caldeira do Create",
                "A caldeira principal da fábrica precisa de 32 Carvões para manter o vapor a todo vapor.",
                "minecraft:coal", 32,
                "3x Barras de Ferro + 1x Maçã Dourada",
                List.of("minecraft:iron_ingot", "minecraft:golden_apple"),
                List.of(3, 1)
        ));

        templates.add(new ContractTemplate(
                "almoco_diretoria",
                "Almoço Executivo da Firma",
                "O Senhor Cummins encomendou 12 Salmões Crus fresquinhos para o banquete dos diretores.",
                "minecraft:salmon", 12,
                "2x Esmeraldas + 1x Chapa de Ferro",
                List.of("minecraft:emerald", "minecraft:iron_ingot"),
                List.of(2, 1)
        ));

        templates.add(new ContractTemplate(
                "reforco_oficina",
                "Reforço Estrutural das Máquinas",
                "Precisamos de 16 Barras de Ferro para soldar as engrenagens mestras da linha de montagem.",
                "minecraft:iron_ingot", 16,
                "2x Diamantes + 16x Tochas da Firma",
                List.of("minecraft:diamond", "minecraft:torch"),
                List.of(2, 16)
        ));
    }

    public synchronized void load() {
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    ContractStore s = GSON.fromJson(r, ContractStore.class);
                    if (s != null) {
                        this.store = s;
                        if (this.store.completionsByPlayerDate == null) this.store.completionsByPlayerDate = new HashMap<>();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar contratos.json: {}", e.getMessage());
        }
        if (store == null) store = new ContractStore();
        if (store.completionsByPlayerDate == null) store.completionsByPlayerDate = new HashMap<>();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(store, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar contratos.json: {}", e.getMessage());
        }
    }

    private synchronized void checkDayRotation() {
        String today = LocalDate.now().toString();
        if (!today.equals(store.currentDate)) {
            store.currentDate = today;
            store.currentTemplateIndex = Math.abs(today.hashCode()) % templates.size();
            save();
        }
    }

    public synchronized ContractTemplate getCurrentContract() {
        checkDayRotation();
        int idx = store.currentTemplateIndex;
        if (idx < 0 || idx >= templates.size()) idx = 0;
        return templates.get(idx);
    }

    public synchronized boolean hasPlayerCompletedToday(String playerName) {
        checkDayRotation();
        String key = playerName.toLowerCase(Locale.ROOT) + "#" + store.currentDate;
        return store.completionsByPlayerDate.containsKey(key);
    }

    public synchronized boolean deliverContract(ServerPlayerEntity player) {
        if (player == null) return false;
        checkDayRotation();
        String playerName = player.getGameProfile().getName();

        if (hasPlayerCompletedToday(playerName)) {
            player.sendMessage(Text.literal("§6[Cummins]§r Você já entregou e concluiu o contrato da firma hoje, " + playerName + "! Volte amanhã para novos trabalhos."), false);
            return false;
        }

        ContractTemplate c = getCurrentContract();
        Item requiredItem = Registries.ITEM.get(new Identifier(c.requiredItemId));
        if (requiredItem == Items.AIR) return false;

        // Conta quantos itens o player tem no inventário
        int found = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack st = player.getInventory().getStack(i);
            if (!st.isEmpty() && st.isOf(requiredItem)) {
                found += st.getCount();
            }
        }

        if (found < c.requiredAmount) {
            player.sendMessage(Text.literal("§6[Cummins]§r Itens insuficientes! O contrato requer §e" + c.requiredAmount + "x "
                    + requiredItem.getName().getString() + "§r, mas você tem apenas §c" + found + "x§r."), false);
            return false;
        }

        // Remove os itens requeridos
        int remainingToRemove = c.requiredAmount;
        for (int i = 0; i < player.getInventory().size() && remainingToRemove > 0; i++) {
            ItemStack st = player.getInventory().getStack(i);
            if (!st.isEmpty() && st.isOf(requiredItem)) {
                int take = Math.min(remainingToRemove, st.getCount());
                st.decrement(take);
                remainingToRemove -= take;
            }
        }

        // Entrega as recompensas
        for (int i = 0; i < c.rewardItemIds.size(); i++) {
            Item rewItem = Registries.ITEM.get(new Identifier(c.rewardItemIds.get(i)));
            int count = (i < c.rewardAmounts.size()) ? c.rewardAmounts.get(i) : 1;
            if (rewItem != Items.AIR) {
                ItemStack rewardStack = new ItemStack(rewItem, count);
                if (!player.getInventory().insertStack(rewardStack)) {
                    player.dropItem(rewardStack, false);
                }
            }
        }

        // Registra conclusão
        String key = playerName.toLowerCase(Locale.ROOT) + "#" + store.currentDate;
        store.completionsByPlayerDate.put(key, c.id);
        save();

        // Efeitos de comemoração
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }

        String broadcast = "🎉 [Contrato Concluído!] O colaborador " + playerName + " entregou a encomenda da firma ('" + c.title + "')! Bônus de produtividade pago com sucesso! 🐾🏅";
        mod.brain().broadcast(broadcast, playerName);
        return true;
    }
}
