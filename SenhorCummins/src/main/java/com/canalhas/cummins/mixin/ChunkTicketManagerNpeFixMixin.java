package com.canalhas.cummins.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Previne o infame crash vanilla de ChunkTicketManager.handleChunkLeave:
 * NullPointerException ao invocar ObjectSet.remove() quando playersByChunkPos não possui a chunk
 * (desync comum em modo espectador, teleporte rápido de bots/câmeras de TV ou desconexão).
 */
@Mixin(ChunkTicketManager.class)
public abstract class ChunkTicketManagerNpeFixMixin {

    @Shadow
    @Final
    private Long2ObjectMap<ObjectSet<ServerPlayerEntity>> playersByChunkPos;

    @Inject(method = "handleChunkLeave", at = @At("HEAD"), cancellable = true)
    private void canalhas$safeHandleChunkLeave(ChunkSectionPos pos, ServerPlayerEntity player, CallbackInfo ci) {
        if (pos == null || this.playersByChunkPos == null) {
            ci.cancel();
            return;
        }
        long chunkPosLong = pos.toChunkPos().toLong();
        ObjectSet<ServerPlayerEntity> set = this.playersByChunkPos.get(chunkPosLong);
        if (set == null) {
            ci.cancel();
        }
    }
}
