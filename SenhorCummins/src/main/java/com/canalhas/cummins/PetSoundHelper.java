package com.canalhas.cummins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;

import java.util.Set;

/**
 * Utilitário central para silenciar o barulho repetitivo e incômodo de passos
 * de todas as entidades pets dos jogadores (Senhor Cummins, Vitória, Javidizineo,
 * Pipira, Juninho e quaisquer pets domados por jogadores no servidor).
 */
public final class PetSoundHelper {

    private PetSoundHelper() {}

    public static boolean isSilentStepPet(Entity entity) {
        if (entity == null) return false;

        Set<String> tags = entity.getCommandTags();
        if (tags != null && !tags.isEmpty()) {
            if (tags.contains(CumminsBody.BODY_TAG)
                    || tags.contains(VitoriaBody.VITORIA_TAG)
                    || tags.contains(JavidizineoBody.JAVIDIZINEO_TAG)
                    || tags.contains(PipiraBody.PIPIRA_TAG)
                    || tags.contains(JuninhoBody.JUNINHO_TAG)
                    || tags.contains("cummins_pet")
                    || tags.contains("silent_step")) {
                return true;
            }
        }

        // Também silencia passos de quaisquer pets/animais domados por jogadores (gatos e cachorros)
        if (entity instanceof TameableEntity tameable && tameable.isTamed()) {
            return true;
        }

        return false;
    }
}
