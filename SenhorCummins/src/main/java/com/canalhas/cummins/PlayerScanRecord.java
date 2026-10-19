package com.canalhas.cummins;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Registro do último scan de item realizado com sucesso para um jogador.
 * Permite que o Senhor Cummins continue apontando e dê detalhes adicionais
 * quando o jogador diz "não tô achando" / "não achei".
 */
public class PlayerScanRecord {
    public final UUID playerUuid;
    public final String playerName;
    public final String rawQuery;
    public final String matchedItemNamePt;
    public final String matchedItemNameEn;
    public final int itemCount;
    public final BlockPos containerPos;
    public final BlockPos standPos;
    public final boolean inContainer;
    public final String containerDescription;
    public final long scanTimestamp;

    public PlayerScanRecord(UUID playerUuid, String playerName, String rawQuery,
                            String matchedItemNamePt, String matchedItemNameEn,
                            int itemCount, BlockPos containerPos, BlockPos standPos,
                            boolean inContainer, String containerDescription,
                            long scanTimestamp) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rawQuery = rawQuery;
        this.matchedItemNamePt = matchedItemNamePt;
        this.matchedItemNameEn = matchedItemNameEn;
        this.itemCount = itemCount;
        this.containerPos = containerPos;
        this.standPos = standPos;
        this.inContainer = inContainer;
        this.containerDescription = containerDescription;
        this.scanTimestamp = scanTimestamp;
    }

    public boolean isExpired(long maxAgeMs) {
        return (System.currentTimeMillis() - scanTimestamp) > maxAgeMs;
    }
}
