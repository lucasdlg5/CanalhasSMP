using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace CanalhasServerDashboard
{
    public class PerformanceSampler
    {
        private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMilliseconds(800) };
        private readonly string _telemetryPath;

        private TimeSpan _lastCpuTime = TimeSpan.Zero;
        private DateTime _lastCpuSampleTime = DateTime.MinValue;
        private DateTime _lastSysSampleTime = DateTime.MinValue;
        private TimeSpan _lastSysCpuTime = TimeSpan.Zero;

        public double CurrentJavaCpuPercent { get; private set; }
        public double CurrentSystemCpuPercent { get; private set; }
        public TelemetryModel LatestTelemetry { get; private set; } = new();

        public bool IsServerAlive
        {
            get
            {
                if (LatestTelemetry == null || LatestTelemetry.Timestamp <= 0) return false;
                long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                return (nowMs - LatestTelemetry.Timestamp) < 6000;
            }
        }

        public List<SamplePoint> History { get; } = new();
        private const int MaxHistory = 60;

        public PerformanceSampler(string serverFolder)
        {
            _telemetryPath = Path.Combine(serverFolder, "server_telemetry.json");
        }

        public async Task SampleAsync(Process? javaProcess)
        {
            if ((javaProcess == null || javaProcess.HasExited) && LatestTelemetry != null && LatestTelemetry.Pid > 0)
            {
                try
                {
                    var proc = Process.GetProcessById(LatestTelemetry.Pid);
                    if (!proc.HasExited) javaProcess = proc;
                }
                catch { }
            }

            // 1. Amostra CPU do Processo Java
            if (javaProcess != null && !javaProcess.HasExited)
            {
                try
                {
                    TimeSpan newCpuTime = javaProcess.TotalProcessorTime;
                    DateTime now = DateTime.UtcNow;

                    if (_lastCpuSampleTime != DateTime.MinValue && now > _lastCpuSampleTime)
                    {
                        double elapsed = (now - _lastCpuSampleTime).TotalMilliseconds;
                        double cpuElapsed = (newCpuTime - _lastCpuTime).TotalMilliseconds;
                        double cpu = (cpuElapsed / (elapsed * Environment.ProcessorCount)) * 100.0;
                        CurrentJavaCpuPercent = Math.Clamp(Math.Round(cpu, 1), 0.0, 100.0);
                    }

                    _lastCpuTime = newCpuTime;
                    _lastCpuSampleTime = now;
                }
                catch
                {
                    CurrentJavaCpuPercent = 0;
                }
            }
            else
            {
                CurrentJavaCpuPercent = 0;
            }

            // 2. Amostra Telemetria (HTTP ou Arquivo)
            TelemetryModel? telemetry = null;
            try
            {
                string json = await Http.GetStringAsync("http://127.0.0.1:25588/telemetry");
                telemetry = JsonSerializer.Deserialize<TelemetryModel>(json);
            }
            catch
            {
                // Fallback para arquivo
                if (File.Exists(_telemetryPath))
                {
                    try
                    {
                        using var fs = new FileStream(_telemetryPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        using var reader = new StreamReader(fs);
                        string json = await reader.ReadToEndAsync();
                        telemetry = JsonSerializer.Deserialize<TelemetryModel>(json);
                    }
                    catch { }
                }
            }

            if (telemetry != null)
            {
                LatestTelemetry = telemetry;
            }

            // 3. Adiciona ao histórico do gráfico
            lock (History)
            {
                History.Add(new SamplePoint
                {
                    Timestamp = DateTime.Now,
                    Tps = LatestTelemetry?.Tps ?? 20.0,
                    MsptMedian = LatestTelemetry?.Mspt?.Median ?? 0.0,
                    MsptMax = LatestTelemetry?.Mspt?.Max ?? 0.0,
                    CpuPercent = CurrentJavaCpuPercent,
                    MemoryPercent = LatestTelemetry?.Memory?.Percent ?? 0.0
                });

                while (History.Count > MaxHistory)
                {
                    History.RemoveAt(0);
                }
            }
        }
    }

    public class SamplePoint
    {
        public DateTime Timestamp { get; set; }
        public double Tps { get; set; }
        public double MsptMedian { get; set; }
        public double MsptMax { get; set; }
        public double CpuPercent { get; set; }
        public double MemoryPercent { get; set; }
    }
}
