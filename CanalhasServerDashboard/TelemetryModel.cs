using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace CanalhasServerDashboard
{
    public class TelemetryModel
    {
        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; }

        [JsonPropertyName("pid")]
        public int Pid { get; set; }

        [JsonPropertyName("tps")]
        public double Tps { get; set; } = 20.0;

        [JsonPropertyName("mspt")]
        public MsptInfo Mspt { get; set; } = new();

        [JsonPropertyName("memory")]
        public MemoryInfo Memory { get; set; } = new();

        [JsonPropertyName("gc")]
        public List<GcInfo> Gc { get; set; } = new();

        [JsonPropertyName("alert")]
        public string? Alert { get; set; }

        [JsonPropertyName("players")]
        public List<string> Players { get; set; } = new();

        [JsonPropertyName("playerDetails")]
        public List<PlayerData> PlayerDetails { get; set; } = new();

        [JsonPropertyName("cummins")]
        public CumminsData? Cummins { get; set; }

        [JsonPropertyName("vitoria")]
        public CumminsData? Vitoria { get; set; }

        [JsonPropertyName("playerCount")]
        public int PlayerCount { get; set; }

        [JsonPropertyName("tokens")]
        public TokenInfo? Tokens { get; set; }
    }

    public class PlayerData
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = "";

        [JsonPropertyName("uuid")]
        public string Uuid { get; set; } = "";

        [JsonPropertyName("dimension")]
        public string Dimension { get; set; } = "minecraft:overworld";

        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("z")]
        public double Z { get; set; }

        [JsonPropertyName("yaw")]
        public double Yaw { get; set; }

        [JsonPropertyName("health")]
        public double Health { get; set; } = 20.0;

        [JsonPropertyName("maxHealth")]
        public double MaxHealth { get; set; } = 20.0;
    }

    public class CumminsData
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = "Senhor Cummins";

        [JsonPropertyName("dimension")]
        public string Dimension { get; set; } = "minecraft:overworld";

        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("z")]
        public double Z { get; set; }

        [JsonPropertyName("yaw")]
        public double Yaw { get; set; }

        [JsonPropertyName("aiEnabled")]
        public bool AiEnabled { get; set; }
    }

    public class MsptInfo
    {
        [JsonPropertyName("min")]
        public double Min { get; set; }

        [JsonPropertyName("mean")]
        public double Mean { get; set; }

        [JsonPropertyName("median")]
        public double Median { get; set; }

        [JsonPropertyName("p95")]
        public double P95 { get; set; }

        [JsonPropertyName("max")]
        public double Max { get; set; }
    }

    public class MemoryInfo
    {
        [JsonPropertyName("usedMb")]
        public long UsedMb { get; set; }

        [JsonPropertyName("totalMb")]
        public long TotalMb { get; set; }

        [JsonPropertyName("maxMb")]
        public long MaxMb { get; set; }

        [JsonPropertyName("percent")]
        public double Percent { get; set; }
    }

    public class GcInfo
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = "";

        [JsonPropertyName("count")]
        public long Count { get; set; }

        [JsonPropertyName("timeMs")]
        public long TimeMs { get; set; }

        [JsonPropertyName("deltaTimeMs")]
        public long DeltaTimeMs { get; set; }

        [JsonPropertyName("deltaCount")]
        public long DeltaCount { get; set; }
    }

    public class MuralDataModel
    {
        [JsonPropertyName("versaoMural")]
        public string VersaoMural { get; set; } = "";

        [JsonPropertyName("tituloServidor")]
        public string TituloServidor { get; set; } = "CanalhasSMP";

        [JsonPropertyName("subtitulo")]
        public string Subtitulo { get; set; } = "Mural de Boas-Vindas & Novidades";

        [JsonPropertyName("mensagemBoasVindas")]
        public string MensagemBoasVindas { get; set; } = "";

        [JsonPropertyName("novidades")]
        public List<string> Novidades { get; set; } = new();

        [JsonPropertyName("correcoes")]
        public List<string> Correcoes { get; set; } = new();

        [JsonPropertyName("comandosUteis")]
        public List<string> ComandosUteis { get; set; } = new();
    }

    public class TokenInfo
    {
        [JsonPropertyName("used")]
        public long Used { get; set; }

        [JsonPropertyName("limit")]
        public long Limit { get; set; } = 1_000_000;

        [JsonPropertyName("remaining")]
        public long Remaining { get; set; }

        [JsonPropertyName("percentUsed")]
        public double PercentUsed { get; set; }

        [JsonPropertyName("percentRemaining")]
        public double PercentRemaining { get; set; }

        [JsonPropertyName("promptTokens")]
        public long PromptTokens { get; set; }

        [JsonPropertyName("candidateTokens")]
        public long CandidateTokens { get; set; }

        [JsonPropertyName("requestCount")]
        public int RequestCount { get; set; }

        [JsonPropertyName("resetCycle")]
        public string ResetCycle { get; set; } = "Diário (00:00)";

        [JsonPropertyName("cycleStartEpochMs")]
        public long CycleStartEpochMs { get; set; }

        [JsonPropertyName("nextResetEpochMs")]
        public long NextResetEpochMs { get; set; }

        [JsonPropertyName("resetRemainingFormatted")]
        public string ResetRemainingFormatted { get; set; } = "--";

        [JsonPropertyName("lastModel")]
        public string LastModel { get; set; } = "gemini-flash-lite";

        [JsonPropertyName("lastUpdatedEpochMs")]
        public long LastUpdatedEpochMs { get; set; }
    }
}
