package com.canalhas.jacinto;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Gerenciador de Silhueta Luminosa Verde para o Bloco / Baú que o Jacinto está mirando / tentando alcançar.
 * Utiliza uma entidade com silhueta luminosa (Glowing Outline) vinculada a uma equipe Scoreboard na cor VERDE.
 */
public class JacintoTargetHighlight {
    private static final String TEAM_NAME = "jacinto_green";
    private ItemEntity highlightEntity = null;
    private BlockPos currentTargetPos = null;
    private Item currentItem = null;

    public void update(ServerWorld world, BlockPos pos, Item item) {
        if (world == null || pos == null || item == null || item == Items.AIR) {
            clear();
            return;
        }

        // Se já está apontando para a mesma posição e item, e a entidade está viva, mantém
        if (highlightEntity != null && !highlightEntity.isRemoved() && pos.equals(currentTargetPos) && item == currentItem) {
            return;
        }

        clear();

        this.currentTargetPos = pos;
        this.currentItem = item;

        // Cria a entidade visual na posição central do bloco-alvo
        highlightEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(item));
        highlightEntity.setNoGravity(true);
        highlightEntity.setInvulnerable(true);
        highlightEntity.setNeverDespawn();
        highlightEntity.setPickupDelayInfinite();
        highlightEntity.setSilent(true);
        highlightEntity.setVelocity(0, 0, 0);
        highlightEntity.setGlowing(true);

        // Associa ao time verde do Scoreboard para forçar a silhueta em cor VERDE
        applyGreenTeam(world.getServer(), highlightEntity);

        world.spawnEntity(highlightEntity);
    }

    private void applyGreenTeam(MinecraftServer server, ItemEntity entity) {
        if (server == null || entity == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addTeam(TEAM_NAME);
            team.setColor(Formatting.GREEN);
            team.setDisplayName(Text.literal("Jacinto Alvo"));
        }
        scoreboard.addPlayerToTeam(entity.getEntityName(), team);
    }

    public void clear() {
        if (highlightEntity != null && !highlightEntity.isRemoved()) {
            highlightEntity.discard();
            highlightEntity = null;
        }
        currentTargetPos = null;
        currentItem = null;
    }
}
