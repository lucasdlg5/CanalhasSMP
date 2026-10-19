package com.canalhas.mobile.data

data class TelemetryResponse(
    val timestamp: Long = 0L,
    val pid: Long = 0L,
    val tps: Double = 20.0,
    val mspt: MsptMetrics = MsptMetrics(),
    val memory: MemoryMetrics = MemoryMetrics(),
    val gc: List<GcMetric> = emptyList(),
    val alert: String? = null,
    val players: List<String> = emptyList(),
    val playerDetails: List<PlayerDetail> = emptyList(),
    val playerCount: Int = 0,
    val cummins: PetLocation? = null,
    val vitoria: PetLocation? = null,
    val chatMessages: List<String> = emptyList(),
    val recentLogs: List<String> = emptyList(),
    val tokens: TokenMetrics? = null,
    val network: NetworkMetrics? = null,
    val mural: MuralResponse? = null
)

data class MsptMetrics(
    val min: Double = 0.0,
    val mean: Double = 0.0,
    val median: Double = 0.0,
    val p95: Double = 0.0,
    val max: Double = 0.0
)

data class MemoryMetrics(
    val usedMb: Long = 0L,
    val totalMb: Long = 0L,
    val maxMb: Long = 0L,
    val percent: Double = 0.0
)

data class GcMetric(
    val name: String = "",
    val count: Long = 0L,
    val timeMs: Long = 0L,
    val deltaTimeMs: Long = 0L,
    val deltaCount: Long = 0L
)

data class PlayerDetail(
    val name: String = "",
    val uuid: String = "",
    val dimension: String = "minecraft:overworld",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val health: Double = 20.0,
    val maxHealth: Double = 20.0
)

data class PetLocation(
    val name: String = "",
    val dimension: String = "minecraft:overworld",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val aiEnabled: Boolean? = null
)

data class TokenMetrics(
    val used: Long = 0L,
    val limit: Long = 1_000_000L,
    val remaining: Long = 1_000_000L,
    val percentUsed: Double = 0.0,
    val percentRemaining: Double = 100.0,
    val promptTokens: Long = 0L,
    val candidateTokens: Long = 0L,
    val requestCount: Int = 0,
    val resetCycle: String = "Diário",
    val cycleStartEpochMs: Long = 0L,
    val nextResetEpochMs: Long = 0L,
    val resetRemainingFormatted: String = "calculando...",
    val lastModel: String = "gemini-flash-lite-latest",
    val lastUpdatedEpochMs: Long = 0L
)

data class NetworkMetrics(
    val target: String = "8.8.8.8",
    val online: Boolean = true,
    val currentPingMs: Long = 0L,
    val avgPing5m: Long = 0L,
    val minPing5m: Long = 0L,
    val maxPing5m: Long = 0L,
    val packetLossPercent5m: Double = 0.0,
    val packetLossCount5m: Int = 0,
    val totalSamples5m: Int = 0,
    val outagesLast5m: Int = 0,
    val lastOutageAgoSeconds: Long = -1L,
    val lastOutageDurationSeconds: Long = 0L,
    val summary: String = "Conexão Estável"
)

data class MuralResponse(
    val versaoMural: String = "",
    val tituloServidor: String = "CanalhasSMP",
    val subtitulo: String = "Mural de Novidades",
    val mensagemBoasVindas: String = "",
    val novidades: List<String> = emptyList(),
    val correcoes: List<String> = emptyList(),
    val comandosUteis: List<String> = emptyList()
)
