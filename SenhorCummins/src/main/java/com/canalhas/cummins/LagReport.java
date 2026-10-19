package com.canalhas.cummins;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro de um relato de lag / travamento / lentidão enviado por um jogador.
 * Definido como classe pública top-level para evitar falhas de classloading dinâmico no servidor.
 */
public class LagReport {
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String timestamp;
    public long epochMs;
    public String playerName;
    public String playerUuid;
    public String rawMessage;
    public String dimension;
    public double x, y, z;
    public double tps;
    public double meanMspt;
    public double maxMspt;
    public long ramUsedMb;
    public double ramPercent;
    public int netPingMs = 0;
    public double netPacketLossPercent = 0.0;
    public boolean netOutageRecent = false;

    public LagReport() {
    }

    public LagReport(String playerName, String playerUuid, String rawMessage,
                     String dimension, double x, double y, double z,
                     double tps, double meanMspt, double maxMspt,
                     long ramUsedMb, double ramPercent,
                     int netPingMs, double netPacketLossPercent, boolean netOutageRecent) {
        ZonedDateTime brNow = CumminsBrain.getBrasiliaTime();
        this.timestamp = brNow.format(ISO_FMT) + " BRT";
        this.epochMs = System.currentTimeMillis();
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.rawMessage = rawMessage;
        this.dimension = dimension;
        this.x = Math.round(x * 10.0) / 10.0;
        this.y = Math.round(y * 10.0) / 10.0;
        this.z = Math.round(z * 10.0) / 10.0;
        this.tps = Math.round(tps * 100.0) / 100.0;
        this.meanMspt = Math.round(meanMspt * 100.0) / 100.0;
        this.maxMspt = Math.round(maxMspt * 100.0) / 100.0;
        this.ramUsedMb = ramUsedMb;
        this.ramPercent = Math.round(ramPercent * 10.0) / 10.0;
        this.netPingMs = netPingMs;
        this.netPacketLossPercent = Math.round(netPacketLossPercent * 10.0) / 10.0;
        this.netOutageRecent = netOutageRecent;
    }
}
