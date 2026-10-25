package com.canalhas.jacinto;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Entidade e lógica de presença do Jacinto Itemakino Baú:
 * - Corpo: HuskEntity (não queima ao sol, equipa qualquer armadura e arma de player)
 * - Vida: 20 HP (10 corações, igual player normal)
 * - Nome: Jacinto Itemakino Baú
 * - Combate: Ataca monstros hostis ao redor enquanto em idle
 * - Morte: Não perde equipamentos e renasce em 5 segundos mandando mensagem engraçada
 */
public class JacintoNpc {
    public static final String JACINTO_TAG = "jacinto_itemakino_npc";
    public static final String NPC_NAME = "§e§lJacinto Itemakino Baú";
    public static final String ENEMY_TEAM_NAME = "jacinto_enemy_white";
    public static final double MAX_BASE_PATROL_RADIUS = 20.0;
    public static final double MAX_BASE_PATROL_RADIUS_SQ = MAX_BASE_PATROL_RADIUS * MAX_BASE_PATROL_RADIUS;
    private static final Logger LOG = LoggerFactory.getLogger("JacintoNpc");

    private final JacintoMod mod;
    private final Random random = new Random();

    private HuskEntity jacintoEntity = null;
    private int respawnTicksRemaining = 0;
    private int idleCombatCooldown = 0;
    private int idleWanderCooldown = 0;

    // Silhueta Luminosa (Glowing Outline) ativa durante tarefas delegadas
    private boolean glowingSilhouette = false;
    private UUID taskDelegatorUuid = null;

    // Alvo de combate ativo, silhueta branca e controle de obstáculos/alcance
    private LivingEntity currentCombatTarget = null;
    private int combatStuckTicks = 0;
    private Vec3d lastCombatPos = null;
    private final Map<UUID, Long> unreachableEnemies = new HashMap<>();
    private final Map<UUID, Long> chatCooldowns = new HashMap<>();

    private static final String[] RESPAWN_PHRASES = {
            "§6[Jacinto Itemakino Baú] §eRapaz, fui de base rapidão... tropecei na quina do baú, mas o Jacinto já tá vivinho da silva de novo! 📦💀",
            "§6[Jacinto Itemakino Baú] §eBateram no Jacinto, mas esqueceram que vaso ruim não quebra! Já tô de pé pro trabalho!",
            "§6[Jacinto Itemakino Baú] §eMorri mas passo bem! 5 segundinhos de CLT remunerado e já voltei pra labuta da firma!",
            "§6[Jacinto Itemakino Baú] §eQuase que o Jacinto fica guardado no baú pra sempre, mas o plano de saúde da firma cobriu o respawn!",
            "§6[Jacinto Itemakino Baú] §eDerrubaram o peão, mas o Jacinto levanta mais rápido que entrega expressa! Quem é o próximo monstro?"
    };

    public JacintoNpc(JacintoMod mod) {
        this.mod = mod;
    }

    public HuskEntity getEntity() {
        if (jacintoEntity != null && (jacintoEntity.isRemoved() || !jacintoEntity.isAlive())) {
            jacintoEntity = null;
        }
        return jacintoEntity;
    }

    public boolean isAlive() {
        return getEntity() != null;
    }

    public void onServerTick(MinecraftServer server) {
        if (server == null) return;

        // Gerenciamento de Respawn (5 segundos = 100 ticks)
        if (respawnTicksRemaining > 0) {
            respawnTicksRemaining--;
            if (respawnTicksRemaining == 0) {
                spawnOrRespawn(server, true);
            }
            return;
        }

        // A cada 40 ticks (2 segundos), executa varredura para eliminar clones/órfãos
        if (server.getTicks() % 40 == 0) {
            cleanupDuplicateJacintos(server);
        }

        HuskEntity entity = getEntity();
        if (entity == null) {
            // Tenta encontrar um Jacinto já existente nos mundos antes de criar outro
            cleanupDuplicateJacintos(server);
            entity = getEntity();
        }

        if (entity == null) {
            // Se o Jacinto ainda não está no mundo e tem jogadores online, tenta invocá-lo na base
            if (server.getTicks() % 100 == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
                spawnOrRespawn(server, false);
            }
            return;
        }

        // Extingue fogo imediatamente (imunidade a fogo/lava para impedir que tentem queimá-lo)
        if (entity.isOnFire()) {
            entity.extinguish();
        }

        // Garante que Jacinto NUNCA tenha jogadores ou entidades pacíficas como alvo e mantenha braços relaxados
        if (entity.getTarget() instanceof PlayerEntity || entity.getTarget() instanceof PassiveEntity) {
            clearCombatTarget(entity);
        }
        if (entity.getTarget() == null && entity.isAttacking()) {
            entity.setAttacking(false);
        }

        // Sincroniza a silhueta luminosa (Glowing): Ativa enquanto executa tarefa delegada, desliga quando ocioso
        boolean shouldGlow = glowingSilhouette || mod.getOrganizer().isBusy() || mod.getMiner().isMining();
        if (entity.isGlowing() != shouldGlow) {
            entity.setGlowing(shouldGlow);
        }

        // Se uma tarefa iniciou, garante que combate ativo e silhueta do monstro sejam limpos imediatamente
        if (mod.getOrganizer().isBusy() || mod.getMiner().isMining()) {
            if (currentCombatTarget != null || entity.getTarget() != null) {
                clearCombatTarget(entity);
            }
        } else {
            // Comportamento quando está Ocioso (Idle): Defesa inteligente da base e patrulha
            tickIdleCombat(entity);
            tickIdleWander(entity);
        }
    }

    /**
     * Varre todos os mundos do servidor e elimina cópias órfãs ou duplicadas do Jacinto.
     * Mantém apenas 1 Jacinto ativo e reconecta a referência se necessário.
     */
    public int cleanupDuplicateJacintos(MinecraftServer server) {
        if (server == null) return 0;
        int purged = 0;
        JacintoData data = mod.getData();

        HuskEntity primary = (jacintoEntity != null && !jacintoEntity.isRemoved() && jacintoEntity.isAlive()) ? jacintoEntity : null;
        UUID savedUuid = null;
        if (data.jacintoUuid != null && !data.jacintoUuid.isEmpty()) {
            try {
                savedUuid = UUID.fromString(data.jacintoUuid);
            } catch (Exception ignored) {}
        }

        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof HuskEntity husk) {
                    boolean isJacintoTag = husk.getCommandTags().contains(JACINTO_TAG);
                    boolean isJacintoName = husk.getCustomName() != null && husk.getCustomName().getString().contains("Jacinto");
                    if (isJacintoTag || isJacintoName) {
                        if (primary == null && (savedUuid == null || husk.getUuid().equals(savedUuid)) && husk.isAlive()) {
                            // Reconecta o Jacinto principal
                            primary = husk;
                            this.jacintoEntity = husk;
                            data.jacintoUuid = husk.getUuidAsString();
                            configureJacintoAI(husk);
                        } else if (husk == primary) {
                            // É a única entidade principal válida
                            continue;
                        } else {
                            // É uma duplicata/clone do Jacinto! Descarta imediatamente
                            husk.discard();
                            purged++;
                        }
                    }
                }
            }
        }

        if (primary != null && this.jacintoEntity == null) {
            this.jacintoEntity = primary;
        }

        return purged;
    }

    /**
     * Configura a IA e propriedades do Jacinto para agir como trabalhador amigável:
     * - Não ataca jogadores nem aldeões (arms down)
     * - Defende a base atacando monstros hostis
     * - Sem colisão com players (não bloqueia portas nem empurra ninguém)
     * - Não dropa XP ao morrer
     * - Usa uniforme de operário se não houver armadura
     */
    public void configureJacintoAI(HuskEntity husk) {
        if (husk == null) return;

        husk.addCommandTag(JACINTO_TAG);
        husk.setCustomName(Text.literal(NPC_NAME));
        husk.setCustomNameVisible(true);
        husk.setPersistent();
        husk.setSilent(true);
        husk.setBaby(false);
        husk.setCanPickUpLoot(false);
        husk.disableExperienceDropping();

        var maxHpAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(20.0);
        if (husk.getHealth() > 20.0f) husk.setHealth(20.0f);

        var speedAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.33); // Velocidade aprimorada para passos ágeis e decididos

        var attackAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(4.0);

        // Limpa TODOS os alvos e objetivos de zumbi vanilla (removendo ActiveTargetGoal<PlayerEntity>)
        husk.targetSelector.clear(goal -> true);
        husk.goalSelector.clear(goal -> true);

        // Registra IA amigável (sem LookAtPlayer nem Wander distrativos enquanto trabalha)
        husk.goalSelector.add(0, new SwimGoal(husk));
        husk.goalSelector.add(2, new MeleeAttackGoal(husk, 1.25, false));
        husk.goalSelector.add(7, new LookAroundGoal(husk));

        // NOTA: Não adicionamos ActiveTargetGoal aqui! O foco de combate é gerenciado
        // 100% pelo tickIdleCombat() com verificação estrita de alcançabilidade de rota (findPathTo)
        // e silhueta branca. Isso impede que o Jacinto trave em paredes mirando monstros inacessíveis.
        husk.targetSelector.clear(goal -> true);

        husk.setAttacking(false);

        // Adiciona ao scoreboard com colisão desligada e cor da equipe
        applyScoreboardTeam(husk);

        // Uniforme de trabalho
        ensureWorkerAttire(husk);

        // Silhueta luminosa caso esteja em tarefa ativa
        boolean shouldGlow = glowingSilhouette || mod.getOrganizer().isBusy() || mod.getMiner().isMining();
        husk.setGlowing(shouldGlow);
    }

    public void ensureWorkerAttire(HuskEntity husk) {
        if (husk == null) return;
        if (husk.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            husk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            husk.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            husk.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            husk.setEquipmentDropChance(EquipmentSlot.CHEST, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
            husk.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            husk.setEquipmentDropChance(EquipmentSlot.LEGS, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
            husk.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            husk.setEquipmentDropChance(EquipmentSlot.FEET, 0.0f);
        }
        if (husk.getMainHandStack().isEmpty()) {
            ItemStack mainTool = mod.getMiner().isMining() ? mod.getData().getEffectivePickaxe() : mod.getData().getEffectiveSword();
            husk.equipStack(EquipmentSlot.MAINHAND, mainTool.copy());
            husk.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    public void applyScoreboardTeam(HuskEntity husk) {
        if (husk == null) return;
        MinecraftServer server = husk.getServer();
        if (server == null) return;
        try {
            ServerScoreboard scoreboard = server.getScoreboard();
            Team team = scoreboard.getTeam("jacinto_team");
            if (team == null) {
                team = scoreboard.addTeam("jacinto_team");
                team.setDisplayName(Text.literal("Equipe Jacinto"));
                team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
                team.setFriendlyFireAllowed(false);
                team.setColor(Formatting.GOLD);
            } else {
                team.setColor(Formatting.GOLD);
            }
            scoreboard.addPlayerToTeam(husk.getEntityName(), team);
        } catch (Exception e) {
            LOG.warn("Falha ao registrar equipe scoreboard para Jacinto: " + e.getMessage());
        }
    }

    /**
     * Ativa ou desativa a silhueta luminosa (Glowing Outline) do Jacinto.
     * Fica visível através de paredes para que o jogador acompanhe o trajeto até o baú ou mina.
     */
    public void setGlowingSilhouette(boolean active, ServerPlayerEntity player) {
        this.glowingSilhouette = active;
        this.taskDelegatorUuid = (active && player != null) ? player.getUuid() : null;
        HuskEntity entity = getEntity();
        if (entity != null && entity.isAlive()) {
            entity.setGlowing(active);
            if (active && entity.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.7f, 1.6f);
                sw.spawnParticles(ParticleTypes.GLOW, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                        15, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    public boolean isGlowingSilhouette() {
        return glowingSilhouette;
    }

    /**
     * Aplica silhueta branca no monstro alvo para indicar claramente aos jogadores
     * qual criatura Jacinto está enfrentando ou tentando alcançar.
     */
    private void applyEnemyWhiteHighlight(MinecraftServer server, LivingEntity target) {
        if (server == null || target == null) return;
        try {
            ServerScoreboard scoreboard = server.getScoreboard();
            Team team = scoreboard.getTeam(ENEMY_TEAM_NAME);
            if (team == null) {
                team = scoreboard.addTeam(ENEMY_TEAM_NAME);
                team.setDisplayName(Text.literal("Alvo do Jacinto"));
                team.setColor(Formatting.WHITE);
            } else {
                team.setColor(Formatting.WHITE);
            }
            scoreboard.addPlayerToTeam(target.getEntityName(), team);
            target.setGlowing(true);
        } catch (Exception e) {
            LOG.warn("Falha ao aplicar silhueta branca no inimigo: " + e.getMessage());
        }
    }

    /**
     * Remove a silhueta branca do monstro quando for derrotado, desengajado ou ignorado.
     */
    private void clearEnemyHighlight(MinecraftServer server, LivingEntity target) {
        if (target == null) return;
        try {
            target.setGlowing(false);
            if (server != null) {
                ServerScoreboard scoreboard = server.getScoreboard();
                Team team = scoreboard.getTeam(ENEMY_TEAM_NAME);
                if (team != null) {
                    scoreboard.removePlayerFromTeam(target.getEntityName(), team);
                }
                scoreboard.clearPlayerTeam(target.getEntityName());
            }
        } catch (Exception e) {
            LOG.warn("Falha ao remover silhueta branca do inimigo: " + e.getMessage());
        }
    }

    private void setCombatTarget(HuskEntity entity, LivingEntity target, Path path) {
        if (currentCombatTarget != null && currentCombatTarget != target) {
            clearEnemyHighlight(entity.getServer(), currentCombatTarget);
        }
        this.currentCombatTarget = target;
        this.combatStuckTicks = 0;
        this.lastCombatPos = entity.getPos();

        entity.setTarget(target);
        entity.setAttacking(true);

        applyEnemyWhiteHighlight(entity.getServer(), target);

        if (path != null) {
            entity.getNavigation().startMovingAlong(path, 1.25);
        } else {
            entity.getNavigation().startMovingTo(target, 1.25);
        }
    }

    public void clearCombatTarget(HuskEntity entity) {
        if (entity != null) {
            entity.setTarget(null);
            entity.setAttacking(false);
            entity.getNavigation().stop();
        }
        if (currentCombatTarget != null) {
            MinecraftServer server = entity != null ? entity.getServer() : currentCombatTarget.getServer();
            clearEnemyHighlight(server, currentCombatTarget);
            currentCombatTarget = null;
        }
        combatStuckTicks = 0;
        lastCombatPos = null;
    }

    /**
     * Move Jacinto para outro setor na patrulha da base quando um monstro é inalcançável
     * ou para revezar a vigilância da área
     */
    private void patrolToNewPoint(HuskEntity entity, Vec3d basePos) {
        if (entity == null || basePos == null) return;
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = 4.0 + random.nextDouble() * 10.0; // 4 a 14 blocos da base
        double targetX = basePos.x + Math.cos(angle) * dist;
        double targetZ = basePos.z + Math.sin(angle) * dist;

        entity.getNavigation().stop();
        entity.getNavigation().startMovingTo(targetX, basePos.y, targetZ, 0.95);
    }

    /**
     * Patrulha e ataca monstros hostis ao redor protegendo a base:
     * - Destaca o alvo atual com silhueta branca (glowing outline branco) visível através de paredes.
     * - Checa conectividade de rota (findPathTo / reachesTarget).
     * - Inimigos em cavernas subterrâneas ou atrás de paredes intransponíveis são ignorados.
     * - Preso/Loop em parede por mais de 2.0s -> abandona alvo, coloca na blacklist e patrulha outro canto.
     * - Restrito ao raio de proteção da base (não sai para longe).
     */
    private void tickIdleCombat(HuskEntity entity) {
        if (--idleCombatCooldown > 0) return;
        idleCombatCooldown = 10; // Checa a cada 0.5s

        ServerWorld world = (ServerWorld) entity.getWorld();
        JacintoData data = mod.getData();

        // 1. Limpa blacklist expirada
        long currentTime = world.getTime();
        unreachableEnemies.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // 2. Garante âncora de base se ainda não definida
        if (!data.baseSet && entity.isOnGround()) {
            data.setBase(entity.getPos(), world.getRegistryKey().getValue().toString());
        }

        Vec3d basePos = data.baseSet ? new Vec3d(data.baseX, data.baseY, data.baseZ) : entity.getPos();
        double distToBaseSq = entity.squaredDistanceTo(basePos);

        // 3. Tether de Base: Se Jacinto se afastou além do raio máximo (20 blocos), retorna imediatamente
        if (distToBaseSq > MAX_BASE_PATROL_RADIUS_SQ) {
            if (currentCombatTarget != null || entity.getTarget() != null) {
                clearCombatTarget(entity);
            }
            entity.getNavigation().startMovingTo(basePos.x, basePos.y, basePos.z, 1.15);
            return;
        }

        // 4. Verificação do alvo atual se já estiver em combate
        if (currentCombatTarget != null) {
            // Se morreu ou foi removido
            if (!currentCombatTarget.isAlive() || currentCombatTarget.isRemoved()) {
                clearCombatTarget(entity);
                // Pequena celebração ao abater inimigo
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.2, entity.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            } else {
                // Alvo saiu para muito longe da base?
                if (currentCombatTarget.squaredDistanceTo(basePos) > (MAX_BASE_PATROL_RADIUS + 6.0) * (MAX_BASE_PATROL_RADIUS + 6.0)) {
                    clearCombatTarget(entity);
                    patrolToNewPoint(entity, basePos);
                    return;
                }

                // Detector de loop/travamento contra paredes ou obstáculos
                combatStuckTicks += 10;
                double distToTargetSq = entity.squaredDistanceTo(currentCombatTarget);

                // Se já faz mais de 40 ticks (2 segundos) tentando alcançar e está fora do alcance melee
                if (combatStuckTicks >= 40 && distToTargetSq > 9.0) {
                    boolean barelyMoved = lastCombatPos != null && entity.squaredDistanceTo(lastCombatPos) < 0.36;
                    Path testPath = entity.getNavigation().findPathTo(currentCombatTarget, 1);
                    boolean cannotReach = testPath == null || !testPath.reachesTarget();

                    if (barelyMoved || cannotReach) {
                        LOG.info("[Jacinto Combat] Inimigo {} inacessível (atrás de parede ou caverna). Ignorando e patrulhando outro setor.", currentCombatTarget.getName().getString());
                        unreachableEnemies.put(currentCombatTarget.getUuid(), currentTime + 300L); // 15s de blacklist
                        clearCombatTarget(entity);
                        patrolToNewPoint(entity, basePos);
                        return;
                    }
                }

                lastCombatPos = entity.getPos();

                // Mantém navegação ativa se estiver parado longe do alvo
                if (entity.getNavigation().isIdle() && distToTargetSq > 4.0) {
                    entity.getNavigation().startMovingTo(currentCombatTarget, 1.25);
                }
                return;
            }
        }

        // 5. Se foi atacado recentemente por um inimigo próximo, prioriza revidar caso haja rota
        if (entity.getAttacker() instanceof HostileEntity attacker && attacker.isAlive() && !attacker.isRemoved()
                && !unreachableEnemies.containsKey(attacker.getUuid())
                && attacker.squaredDistanceTo(basePos) <= (MAX_BASE_PATROL_RADIUS + 4.0) * (MAX_BASE_PATROL_RADIUS + 4.0)) {
            Path path = entity.getNavigation().findPathTo(attacker, 1);
            if (path != null && path.reachesTarget()) {
                setCombatTarget(entity, attacker, path);
                return;
            } else {
                unreachableEnemies.put(attacker.getUuid(), currentTime + 300L);
            }
        }

        // 6. Busca novos inimigos nas redondezas da base (raio de 14 blocos)
        Box searchBox = entity.getBoundingBox().expand(14.0, 5.0, 14.0);
        List<HostileEntity> monsters = world.getEntitiesByClass(HostileEntity.class, searchBox, m ->
                m.isAlive() &&
                !m.isRemoved() &&
                !m.getCommandTags().contains(JACINTO_TAG) &&
                !unreachableEnemies.containsKey(m.getUuid()) &&
                m.squaredDistanceTo(basePos) <= (MAX_BASE_PATROL_RADIUS + 4.0) * (MAX_BASE_PATROL_RADIUS + 4.0)
        );

        if (monsters.isEmpty()) {
            return;
        }

        // Ordena monstros por proximidade de Jacinto
        monsters.sort((a, b) -> Double.compare(entity.squaredDistanceTo(a), entity.squaredDistanceTo(b)));

        for (HostileEntity m : monsters) {
            // Checa rota aberta e acessível antes de engajar
            Path path = entity.getNavigation().findPathTo(m, 1);
            if (path != null && path.reachesTarget()) {
                setCombatTarget(entity, m, path);
                return;
            } else {
                // Inimigo preso atrás de parede ou caverna subterrânea
                unreachableEnemies.put(m.getUuid(), currentTime + 300L);
            }
        }

        // Se há monstros na área mas NENHUM tem rota transponível:
        // Move para outro canto da patrulha para proteger o restante da base sem ficar parado na parede
        patrolToNewPoint(entity, basePos);
    }

    /**
     * Passeia suavemente pela base quando não há perigo por perto, mantendo guarda próxima à base
     */
    private void tickIdleWander(HuskEntity entity) {
        if (--idleWanderCooldown > 0) return;
        idleWanderCooldown = 80 + random.nextInt(80); // A cada 4-8s

        if (entity.getTarget() != null || currentCombatTarget != null) return;

        JacintoData data = mod.getData();
        Vec3d basePos = data.baseSet ? new Vec3d(data.baseX, data.baseY, data.baseZ) : entity.getPos();
        double distToBaseSq = entity.squaredDistanceTo(basePos);

        if (distToBaseSq > 14.0 * 14.0) {
            // Se já está afastado do centro da base, caminha de volta
            entity.getNavigation().startMovingTo(basePos.x, basePos.y, basePos.z, 0.95);
        } else {
            // Patrulha setor aleatório dentro do raio seguro
            patrolToNewPoint(entity, basePos);
        }
    }

    /**
     * Spawna ou Respowna o Jacinto no mundo
     */
    public HuskEntity spawnOrRespawn(MinecraftServer server, boolean announceRespawn) {
        if (server == null) return null;

        JacintoData data = mod.getData();
        ServerWorld world = null;

        // 1. Tenta pegar a dimensão salva da base
        if (data.baseSet && data.baseDimension != null) {
            Identifier dimId = Identifier.tryParse(data.baseDimension);
            if (dimId != null) {
                world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
            }
        }

        // 2. Fallback: mundo Overworld do servidor ou jogador online
        if (world == null) {
            world = server.getOverworld();
        }

        Vec3d spawnPos;
        if ("MINING".equalsIgnoreCase(data.currentTask) && data.miningY != 0.0 && data.miningDimension != null) {
            Identifier dimId = Identifier.tryParse(data.miningDimension);
            if (dimId != null) {
                ServerWorld mineWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
                if (mineWorld != null) {
                    world = mineWorld;
                }
            }
            spawnPos = new Vec3d(data.miningX, data.miningY, data.miningZ);
        } else if (data.baseSet) {
            spawnPos = new Vec3d(data.baseX, data.baseY, data.baseZ);
        } else if (!server.getPlayerManager().getPlayerList().isEmpty()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            world = p.getServerWorld();
            spawnPos = p.getPos();
            data.setBase(spawnPos, world.getRegistryKey().getValue().toString());
        } else {
            spawnPos = Vec3d.ofBottomCenter(world.getSpawnPos());
        }

        // 3. Limpa duplicatas e verifica se já existe um Jacinto ativo
        cleanupDuplicateJacintos(server);
        if (this.jacintoEntity != null && this.jacintoEntity.isAlive()) {
            this.jacintoEntity.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.1, spawnPos.z, 0f, 0f);
            configureJacintoAI(this.jacintoEntity);
            return this.jacintoEntity;
        }

        // Remove entidade antiga se houver
        if (jacintoEntity != null && !jacintoEntity.isRemoved()) {
            jacintoEntity.discard();
        }

        HuskEntity husk = EntityType.HUSK.create(world);
        if (husk == null) return null;

        husk.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.1, spawnPos.z, 0f, 0f);

        // Restaura armaduras e armas salvas
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack saved = data.getSavedEquipment(slot);
            if (!saved.isEmpty()) {
                husk.equipStack(slot, saved.copy());
                husk.setEquipmentDropChance(slot, 0.0f); // 0% de chance de dropar no chão
            }
        }

        // Aplica IA, tags, sem colisão e uniforme de operário
        configureJacintoAI(husk);

        world.spawnEntity(husk);
        this.jacintoEntity = husk;
        data.jacintoUuid = husk.getUuidAsString();
        data.save();

        // Partículas festivas de invocação
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spawnPos.x, spawnPos.y + 1.0, spawnPos.z, 15, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);

        if (announceRespawn) {
            if ("MINING".equalsIgnoreCase(data.currentTask)) {
                String phrase = "§6[Jacinto Itemakino Baú] §eMorri mas voltei com sede de minério! Retomando a picareta e a extração de [" + mod.getMiner().getTargetDisplayName() + "] imediatamente! ⛏️🔥";
                server.getPlayerManager().broadcast(Text.literal(phrase), false);
                mod.getMiner().resumeMining();
            } else {
                String phrase = RESPAWN_PHRASES[random.nextInt(RESPAWN_PHRASES.length)];
                server.getPlayerManager().broadcast(Text.literal(phrase), false);
            }
        } else if ("MINING".equalsIgnoreCase(data.currentTask)) {
            mod.getMiner().resumeMining();
        }

        return husk;
    }

    /**
     * Chamado quando o Jacinto sofre dano fatal
     */
    public void onDeath(HuskEntity entity) {
        if (entity == null) return;

        // Limpa silhueta de inimigo e alvo atual
        clearCombatTarget(entity);

        // Salva os equipamentos atuais na memória para garantir que nada seja perdido
        JacintoData data = mod.getData();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                data.saveEquipment(slot, stack.copy());
            }
        }

        // Limpa a mão e armadura da entidade antes da remoção para evitar qualquer drop no chão
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            entity.equipStack(slot, ItemStack.EMPTY);
        }

        this.jacintoEntity = null;
        this.respawnTicksRemaining = 100; // 5 segundos = 100 ticks

        MinecraftServer server = entity.getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(
                    Text.literal("§c☠ [Jacinto Itemakino Baú] §7O Jacinto caiu em combate! Respawnando em 5 segundos na base..."),
                    false
            );
        }
    }

    /**
     * Interação do jogador ao clicar com o botão direito no Jacinto
     */
    public void handleInteract(PlayerEntity player, HuskEntity jacinto, Hand hand) {
        if (player.getWorld().isClient() || hand != Hand.MAIN_HAND) return;

        ItemStack held = player.getStackInHand(hand);
        JacintoData data = mod.getData();

        // 1. Shift + Clique com mão vazia: Exibe status, ferramentas e equipamentos
        if (player.isSneaking() && held.isEmpty()) {
            player.sendMessage(Text.literal("§6=== 📦 STATUS DO JACINTO ITEMAKINO BAÚ ==="), false);
            player.sendMessage(Text.literal("§fVida: §a" + String.format("%.0f", jacinto.getHealth()) + "/20 ❤"), false);
            player.sendMessage(Text.literal("§fTarefa Atual: §e" + (mod.getMiner().isMining() ? "Minerando " + mod.getMiner().getTargetDisplayName() : (mod.getOrganizer().isBusy() ? "Organizando Baús" : "Patrulhando / Ocioso"))), false);

            ItemStack effSword = data.getEffectiveSword();
            ItemStack effPick = data.getEffectivePickaxe();
            ItemStack effShovel = data.getEffectiveShovel();
            int pickTier = JacintoMiningCompatibility.getPickaxeMiningLevel(effPick);

            player.sendMessage(Text.literal("§f🗡️ Espada de Combate: §e" + getStackDisplayName(effSword) + (JacintoData.isDefaultTool(effSword) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f⛏️ Picareta de Mineração: §e" + getStackDisplayName(effPick) + " §b[Tier " + JacintoMiningCompatibility.getTierName(pickTier) + "]" + (JacintoData.isDefaultTool(effPick) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f🪵 Pá de Escavação: §e" + getStackDisplayName(effShovel) + (JacintoData.isDefaultTool(effShovel) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f🛡️ Escudo/Offhand: §e" + getStackDisplayName(jacinto.getOffHandStack())), false);
            player.sendMessage(Text.literal("§f🦺 Armadura: §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.HEAD)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.CHEST)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.LEGS)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.FEET))), false);
            player.sendMessage(Text.literal("§fTotal de Itens Organizados na Carreira: §a" + data.totalItemsOrganized + " itens"), false);
            player.sendMessage(Text.literal("§fTotal de Minérios Extraídos na Carreira: §b" + data.totalBlocksMined + " blocos"), false);
            player.sendMessage(Text.literal("§7(Dica: Clique com espadas, picaretas, pás ou armaduras para equipá-lo!)"), false);
            jacinto.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            return;
        }

        // 1.5. Jogador segurando Isqueiro, Carga de Fogo ou Balde de Lava (prevenção cômica de agressão com fogo)
        if (held.getItem() == Items.FLINT_AND_STEEL || held.getItem() == Items.FIRE_CHARGE || held.getItem() == Items.LAVA_BUCKET) {
            player.sendMessage(Text.literal("§6[Jacinto Itemakino Baú] §e🔥 Ei patrão §b" + player.getName().getString() +
                    "§e! Nem adianta tentar me tacar fogo ou me queimar! Casca grossa de operário não queima! " +
                    "Se você quer o item (ou o Mjölnir do Jodevil) de volta, pede com carinho no chat que você tem 10% de chance de eu te entregar! 😂🔨"), false);
            jacinto.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 2. Jogador segurando Espada:
        if (held.getItem() instanceof SwordItem) {
            ItemStack oldSword = data.getSavedSword();
            ItemStack toEquip = held.split(1);
            data.saveSword(toEquip);

            if (!mod.getMiner().isMining()) {
                jacinto.equipStack(EquipmentSlot.MAINHAND, toEquip.copy());
            }

            if (!oldSword.isEmpty() && !JacintoData.isDefaultTool(oldSword)) {
                if (!player.getInventory().insertStack(oldSword)) {
                    player.dropItem(oldSword, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa patrão §b" + player.getName().getString() +
                    "§f! Essa espada §b" + toEquip.getName().getString() + "§f vai defender a base com tudo! Quando quebrar, voltarei para a minha de madeira infinita."), false);
            return;
        }

        // 3. Jogador segurando Picareta:
        if (held.getItem() instanceof PickaxeItem) {
            ItemStack oldPick = data.getSavedPickaxe();
            ItemStack toEquip = held.split(1);
            data.savePickaxe(toEquip);

            if (mod.getMiner().isMining()) {
                jacinto.equipStack(EquipmentSlot.MAINHAND, toEquip.copy());
                mod.getMiner().onToolReceived(player, toEquip);
            }

            if (!oldPick.isEmpty() && !JacintoData.isDefaultTool(oldPick)) {
                if (!player.getInventory().insertStack(oldPick)) {
                    player.dropItem(oldPick, false);
                }
            }

            int tier = JacintoMiningCompatibility.getPickaxeMiningLevel(toEquip);
            String tierName = JacintoMiningCompatibility.getTierName(tier);

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fValeu patrão §b" + player.getName().getString() +
                    "§f! Equipei a §b" + toEquip.getName().getString() + " §7(Nível " + tierName + ")§f! Agora consigo minerar minérios desse nível. Ao quebrar, volto para a de madeira infinita."), false);
            return;
        }

        // 4. Jogador segurando Pá:
        if (held.getItem() instanceof ShovelItem) {
            ItemStack oldShovel = data.getSavedShovel();
            ItemStack toEquip = held.split(1);
            data.saveShovel(toEquip);

            if (!oldShovel.isEmpty() && !JacintoData.isDefaultTool(oldShovel)) {
                if (!player.getInventory().insertStack(oldShovel)) {
                    player.dropItem(oldShovel, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, uma pá de qualidade! §b" + toEquip.getName().getString() +
                    "§f guardada para quando eu cavar terra e cascalho!"), false);
            return;
        }

        // 5. Jogador segurando Armadura ou Escudo: Equipa no Jacinto
        EquipmentSlot targetSlot = getSlotForItem(held);
        if (targetSlot != null) {
            ItemStack oldEquip = jacinto.getEquippedStack(targetSlot).copy();
            ItemStack toEquip = held.split(1);

            jacinto.equipStack(targetSlot, toEquip);
            jacinto.setEquipmentDropChance(targetSlot, 0.0f);
            data.saveEquipment(targetSlot, toEquip);

            // Devolve o item antigo ao jogador se não for ferramenta padrão
            if (!oldEquip.isEmpty() && !JacintoData.isDefaultTool(oldEquip)) {
                if (!player.getInventory().insertStack(oldEquip)) {
                    player.dropItem(oldEquip, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);

            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, valeu pelo agrado, patrão §b" +
                    player.getGameProfile().getName() + "§f! Esse(a) §b" + toEquip.getName().getString() + "§f ficou tinindo em mim!"), false);
            return;
        }

        // 3. Clique comum sem item especial: Mensagem amigável de operário
        String[] idleQuotes = {
                "§e[Jacinto Itemakino Baú] §fÀs suas ordens, patrão! Coloque uma placa no baú e digite §b/organizar§f que eu cuido do resto!",
                "§e[Jacinto Itemakino Baú] §fAqui na firma o serviço é rápido. Se precisar parar no meio, só mandar um §b/organizar stop§f!",
                "§e[Jacinto Itemakino Baú] §fDeixa qualquer monstro chegar perto desse baú pra ver se eu não mostro meu serviço!",
                "§e[Jacinto Itemakino Baú] §fJacinto organizando, a firma prosperando! Só me dar as ordens.",
                "§e[Jacinto Itemakino Baú] §fSe estiver procurando pelo seu item (ou pelo Mjölnir do Jodevil), pede com jeitinho no chat! Quem sabe eu não te presenteio? Só não vale tacar fogo em mim! 😂🔨"
        };
        player.sendMessage(Text.literal(idleQuotes[random.nextInt(idleQuotes.length)]), false);
        jacinto.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
    }

    private EquipmentSlot getSlotForItem(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getSlotType();
        }
        if (stack.getItem() instanceof ShieldItem) {
            return EquipmentSlot.OFFHAND;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof MiningToolItem) {
            return EquipmentSlot.MAINHAND;
        }
        return null;
    }

    private String getStackDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "§7[Vazio]";
        return stack.getName().getString();
    }

    /**
     * Trata mensagens enviadas pelos jogadores no chat do servidor direcionadas ao Jacinto
     */
    public void handlePlayerChat(ServerPlayerEntity sender, String rawMessage) {
        if (sender == null || rawMessage == null || rawMessage.isBlank()) return;

        if (isItemReturnRequest(rawMessage)) {
            handleItemReturnRequest(sender);
        }
    }

    public boolean isItemReturnRequest(String message) {
        String msg = message.toLowerCase(java.util.Locale.ROOT).trim();
        msg = msg.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                 .replace("ã", "a").replace("õ", "o").replace("â", "a").replace("ê", "e")
                 .replace("ö", "o").replace("ç", "c");

        boolean mentionsJacinto = msg.contains("jacinto") || msg.contains("itemakino") || msg.contains("peao");
        boolean mentionsAction = msg.contains("devolv") || msg.contains("me da") || msg.contains("da meu")
                || msg.contains("dar meu") || msg.contains("passa") || msg.contains("entrega")
                || msg.contains("de volta") || msg.contains("dropa") || msg.contains("restitui") || msg.contains("pedir");
        boolean mentionsItem = msg.contains("item") || msg.contains("mjolnir") || msg.contains("martelo")
                || msg.contains("arma") || msg.contains("espada") || msg.contains("jodevil");

        // Caso 1: Menciona o Jacinto e pede devolução/item
        if (mentionsJacinto && (mentionsAction || mentionsItem)) {
            return true;
        }

        // Caso 2: Pede devolução mencionando mjolnir ou o jodevil
        if (mentionsAction && (msg.contains("mjolnir") || msg.contains("jodevil"))) {
            return true;
        }

        return false;
    }

    /**
     * Processa a solicitação do jogador para o Jacinto devolver/presentear o item (Mjölnir do mod).
     * Chance de 10% de sucesso.
     * Na falha (90%), encoraja a continuar pedindo e lembra que machucá-lo ou tacar fogo não adianta.
     */
    public void handleItemReturnRequest(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();
        Long lastTime = chatCooldowns.get(uuid);

        if (lastTime != null && now - lastTime < 2500) { // 2.5s de cooldown anti-spam
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §7(Calma patrão! Respira fundo e pede de novo em 2 segundinhos com bastante carinho!)"), false);
            return;
        }
        chatCooldowns.put(uuid, now);

        boolean isJodevil = player.getName().getString().equalsIgnoreCase("Jodevil");
        double roll = random.nextDouble();
        boolean success = roll < 0.10; // 10% de chance

        HuskEntity entity = getEntity();
        if (entity != null && entity.getWorld() == player.getServerWorld()) {
            entity.getLookControl().lookAt(player, 30.0f, 30.0f);
        }

        if (success) {
            // SUCESSO (10%): Devolve ou presenteia com o Mjölnir do mod soulsweapons
            ItemStack mjolnirStack = ItemStack.EMPTY;

            // 1. Tenta recuperar o Mjölnir customizado salvo (ex: o do Jodevil com todos os encantamentos e nome)
            ItemStack savedSword = mod.getData().getSavedSword();
            if (!savedSword.isEmpty() && Registries.ITEM.getId(savedSword.getItem()).getPath().contains("mjolnir")) {
                mjolnirStack = savedSword.copy();
                mod.getData().saveSword(ItemStack.EMPTY);
                if (entity != null) {
                    entity.equipStack(EquipmentSlot.MAINHAND, JacintoData.createDefaultWoodenSword());
                }
            } else if (entity != null && Registries.ITEM.getId(entity.getMainHandStack().getItem()).getPath().contains("mjolnir")) {
                mjolnirStack = entity.getMainHandStack().copy();
                mod.getData().saveSword(ItemStack.EMPTY);
                entity.equipStack(EquipmentSlot.MAINHAND, JacintoData.createDefaultWoodenSword());
            }

            // 2. Se não tinha na mão, cria uma nova do mod soulsweapons
            if (mjolnirStack.isEmpty()) {
                net.minecraft.item.Item item = Registries.ITEM.get(new Identifier("soulsweapons", "mjolnir"));
                if (item != null && item != Items.AIR) {
                    mjolnirStack = new ItemStack(item);
                } else {
                    mjolnirStack = new ItemStack(Items.NETHERITE_AXE);
                    mjolnirStack.setCustomName(Text.literal("§6§lMjölnir dos Deuses"));
                }
            }

            // Entrega no inventário do jogador ou dropa aos pés caso cheio
            if (!player.getInventory().insertStack(mjolnirStack)) {
                player.dropItem(mjolnirStack, false);
            }

            // Efeitos de trovão, fanfarra e partículas
            ServerWorld pWorld = player.getServerWorld();
            pWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.9f, 1.0f);
            pWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            pWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
                    30, 0.5, 0.8, 0.5, 0.1);

            String victoryMsg;
            if (isJodevil) {
                victoryMsg = "§6⚡ [Jacinto Itemakino Baú] §aTÁ BOM, TÁ BOM, JODEVIL! Venceu pelo cansaço! O Jacinto tem coração mole afinal... Toma aqui seu §e§lMjölnir§a de volta! Cuida bem dele agora e para de chorar na firma! 😂📦🔨⚡";
            } else {
                victoryMsg = "§6⚡ [Jacinto Itemakino Baú] §aRapaz, o patrão §b" + player.getName().getString() +
                        "§a pediu com tanto jeito e insistência que o Jacinto se comoveu! Toma aqui esse §e§lMjölnir§a da firma! Não perde de novo, hein patrão! 😂📦🔨⚡";
            }
            player.getServer().getPlayerManager().broadcast(Text.literal(victoryMsg), false);

        } else {
            // FALHA (90%): Encoraja a continuar pedindo e deixa claro que tacar fogo/bater não adianta nada
            String failMsg;
            if (isJodevil) {
                String[] jodevilQuotes = {
                        "§e[Jacinto Itemakino Baú] §fÔ §bJodevil§f, você me deu de presente de coração e agora quer de volta?! O Jacinto já até gravou o nome da firma nele! Mas ó, vou te dar uma colher de chá: continua pedindo educadamente no chat que quem sabe o peão aqui não amolece? E para de tentar me tacar fogo, doido! Fogo no Jacinto é só massagem! 😂📦🔥",
                        "§e[Jacinto Itemakino Baú] §fDevolver agora, §bJodevil§f? Nem pensar! O martelo dos deuses combinou direitinho com o meu uniforme de operário! Continua pedindo com carinho que uma hora a firma libera. E já aviso: bater no Jacinto ou tentar queimar o peão não vai fazer o martelo cair não, viu? Aqui é CLT blindada! 🔥🚫🔨",
                        "§e[Jacinto Itemakino Baú] §fAchado não é roubado, quem deu foi o §bJodevil§f e ficou relaxado! 😂 Mas não desiste não, patrão, continua pedindo no chat que quem sabe na próxima eu não te devolvo? Só guarda esse isqueiro aí que violência contra funcionário dá processo! 📜👷"
                };
                failMsg = jodevilQuotes[random.nextInt(jodevilQuotes.length)];
            } else {
                String[] generalQuotes = {
                        "§e[Jacinto Itemakino Baú] §fÔ §b" + player.getName().getString() + "§f, devolver assim de primeira sem mais nem menos? Tá achando que é festa? Continua pedindo com carinho e insistência que quem sabe eu não amoleço! E já aviso: tacar fogo em mim ou tentar me bater não vai ajudar em nada! Casca de operário não queima! 🔥🚫",
                        "§e[Jacinto Itemakino Baú] §fIh patrão §b" + player.getName().getString() + "§f, o Jacinto tá apegado demais nessa marreta do trovão! Mas não desiste não, vai pedindo aí no chat que uma hora a firma cede! E nem gaste seu fogo nem sua lava, que o peão aqui é à prova de incêndio! 😂🔨",
                        "§e[Jacinto Itemakino Baú] §fNegativo, patrão! Esse Mjölnir agora é patrimônio tombado da firma! Mas continue pedindo com jeitinho... a persistência é o segredo do sucesso! Só não tenta me machucar que a firma não tolera assédio moral nem fogo amigo! 👷📦"
                };
                failMsg = generalQuotes[random.nextInt(generalQuotes.length)];
            }

            player.getServer().getPlayerManager().broadcast(Text.literal(failMsg), false);

            if (entity != null && entity.getWorld() == player.getServerWorld()) {
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                ((ServerWorld) entity.getWorld()).spawnParticles(ParticleTypes.SMOKE,
                        entity.getX(), entity.getY() + 1.2, entity.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }
}
