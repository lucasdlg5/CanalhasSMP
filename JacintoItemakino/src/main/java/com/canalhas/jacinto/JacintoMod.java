package com.canalhas.jacinto;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class JacintoMod implements ModInitializer {
    public static final String MOD_ID = "jacintoitemakino";
    public static final Logger LOG = LoggerFactory.getLogger("JacintoItemakino");

    private JacintoData data;
    private JacintoNpc npc;
    private JacintoOrganizer organizer;
    private JacintoMiner miner;

    @Override
    public void onInitialize() {
        LOG.info("[Jacinto Itemakino] Inicializando mod organizador de baús e mineração autônoma...");

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("jacinto");
        this.data = JacintoData.load(configDir);
        this.npc = new JacintoNpc(this);
        this.organizer = new JacintoOrganizer(this);
        this.miner = new JacintoMiner(this);

        // Registro de Comandos (/organizar, /minerar e /jacinto)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                JacintoCommands.register(dispatcher, this));

        // Tick loop do servidor para movimentação, organização, mineração e respawn
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            npc.onServerTick(server);
            organizer.onServerTick();
            miner.onServerTick();
        });

        // Interação com o Jacinto (equipar armaduras/armas e conversar)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && entity instanceof HuskEntity husk && husk.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                npc.handleInteract(player, husk, hand);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Jogadores NÃO PODEM bater no Jacinto (cancela o ataque na origem e impede farm de XP)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity != null && entity.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                if (!world.isClient()) {
                    player.sendMessage(Text.literal("§6[Jacinto Itemakino Baú] §eEi patrão §b" + player.getName().getString() + "§e, sem fogo amigo! Sou da firma, não me bata! 📦👷"), true);
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Controle de Dano e Proteção
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity attacker = source.getAttacker();

            // 1. O Jacinto NUNCA causa dano a jogadores, aldeões ou animais pacíficos!
            if (attacker != null && attacker.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                if (entity instanceof PlayerEntity || entity instanceof PassiveEntity) {
                    if (attacker instanceof HuskEntity husk) {
                        husk.setTarget(null);
                        husk.setAttacking(false);
                    }
                    return false;
                }
            }

            // 2. Proteção do Jacinto contra ataques de Players, Aliados (Vitoria/Companions) e Sufocamento:
            if (entity != null && entity.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                // Jogadores e projéteis de jogadores NUNCA dão dano no Jacinto
                if (attacker instanceof PlayerEntity || source.getSource() instanceof PlayerEntity) {
                    return false;
                }

                // Pets e animais domesticados NUNCA atacam Jacinto
                if (attacker instanceof TameableEntity) {
                    return false;
                }

                // Companheiros/NPCs amigos (ex: Vitoria, Guard Villagers, Golens) NUNCA atacam Jacinto
                if (attacker != null) {
                    String name = attacker.getName().getString().toLowerCase();
                    if (name.contains("vitoria") || name.contains("vitória") || name.contains("guard") || name.contains("companion")) {
                        if (attacker instanceof MobEntity mob) {
                            mob.setTarget(null);
                        }
                        return false;
                    }
                    // Apenas Monstros Hostis reais (zumbi, esqueleto, creeper, etc.) podem danificar Jacinto
                    if (!(attacker instanceof Monster) || attacker.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                        if (attacker instanceof MobEntity mob) {
                            mob.setTarget(null);
                        }
                        return false;
                    }
                } else {
                    // Previne morte acidental por sufocamento em baús/blocos ou arbustos
                    if (source.isOf(DamageTypes.IN_WALL) ||
                        source.isOf(DamageTypes.CACTUS) ||
                        source.isOf(DamageTypes.SWEET_BERRY_BUSH) ||
                        source.isOf(DamageTypes.DROWN)) {
                        return false;
                    }
                }
            }

            return true;
        });

        // Evento de morte do Jacinto (ativa cronômetro de 5s e salva itens)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof HuskEntity husk && husk.getCommandTags().contains(JacintoNpc.JACINTO_TAG)) {
                npc.onDeath(husk);
            }
        });

        LOG.info("[Jacinto Itemakino] Mod inicializado com sucesso! Pronto para organizar a firma.");
    }

    public JacintoData getData() {
        return data;
    }

    public JacintoNpc getNpc() {
        return npc;
    }

    public JacintoOrganizer getOrganizer() {
        return organizer;
    }

    public JacintoMiner getMiner() {
        return miner;
    }
}
