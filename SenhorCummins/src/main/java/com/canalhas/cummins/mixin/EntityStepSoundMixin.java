package com.canalhas.cummins.mixin;

import com.canalhas.cummins.PetSoundHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void onPlayStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (PetSoundHelper.isSilentStepPet((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "playStepSounds", at = @At("HEAD"), cancellable = true)
    private void onPlayStepSounds(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (PetSoundHelper.isSilentStepPet((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
