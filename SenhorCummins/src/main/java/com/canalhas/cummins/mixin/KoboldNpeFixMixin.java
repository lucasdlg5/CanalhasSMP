package com.canalhas.cummins.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Correção cirúrgica para o bug do Mobs of Mythology (KoboldEntity.java:110).
 * Quando o Kobold perde seu alvo ou está sem alvo ativo, lambda$getFightTasks$1 tenta
 * chamar getTarget().getStackInHand() sem verificar se getTarget() é nulo, disparando
 * NullPointerException no loop de IA do SmartBrainLib.
 */
@Pseudo
@Mixin(targets = "net.pixeldreamstudios.mobs_of_mythology.entity.mobs.KoboldEntity")
public abstract class KoboldNpeFixMixin {

    @Inject(method = "lambda$getFightTasks$1", at = @At("HEAD"), cancellable = true, remap = false)
    private void canalhas$preventKoboldTargetNpe(MobEntity mob, CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity) (Object) this;
        if (self.getTarget() == null) {
            cir.setReturnValue(false);
        }
    }
}
