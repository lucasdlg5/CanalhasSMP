package com.canalhas.cummins.mixin;

import com.canalhas.cummins.PetSoundHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WolfEntity.class)
public abstract class WolfStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void onWolfPlayStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (PetSoundHelper.isSilentStepPet((WolfEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
