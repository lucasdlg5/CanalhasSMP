using System;

using System.Collections.Generic;

using System.Diagnostics;

using System.IO;

using System.Management;

using System.Text;

using System.Text.RegularExpressions;

using System.Threading;

using System.Threading.Tasks;



namespace CanalhasServerDashboard

{

    public enum ChatType

    {

        PlayerChat,

        CumminsChat,

        Join,

        Leave,

        Advancement,

        Death,

        ConsoleCommand,

        Other

    }



    public enum ShutdownCause

    {

        Unknown,

        PanelStopButton,

        PanelRestartButton,

        UserCommandStop,

        ForcedTermination,

        CrashOrException

    }



    public class ServerShutdownDetails

    {

        public int ExitCode { get; set; }

        public ShutdownCause Cause { get; set; }

        public string CauseDescription { get; set; } = "";

        public string? ExceptionDetail { get; set; }

        public bool IsSuccess => ExitCode == 0 && Cause != ShutdownCause.CrashOrException && Cause != ShutdownCause.ForcedTermination;

    }



    public class ChatMessageItem

    {

        public string Time { get; set; } = "";

        public string Sender { get; set; } = "";

        public string Content { get; set; } = "";

        public ChatType Type { get; set; } = ChatType.PlayerChat;

    }



    public class ServerProcessManager

    {

        private static readonly Regex ChatRegex = new(@"^\[(\d{2}:\d{2}:\d{2})\]\s+\[Server thread\/INFO\]:\s+<([^>]+)>\s+(.*)$", RegexOptions.Compiled);

        private static readonly Regex ServerSayRegex = new(@"^\[(\d{2}:\d{2}:\d{2})\]\s+\[Server thread\/INFO\]:\s+\[Server\]\s+(.*)$", RegexOptions.Compiled);

        private static readonly Regex JoinRegex = new(@"^\[(\d{2}:\d{2}:\d{2})\]\s+\[Server thread\/INFO\]:\s+([a-zA-Z0-9_]+)\s+joined the game$", RegexOptions.Compiled);

        private static readonly Regex LeaveRegex = new(@"^\[(\d{2}:\d{2}:\d{2})\]\s+\[Server thread\/INFO\]:\s+([a-zA-Z0-9_]+)\s+(left the game|lost connection:.*)$", RegexOptions.Compiled);

        private static readonly Regex AdvRegex = new(@"^\[(\d{2}:\d{2}:\d{2})\]\s+\[Server thread\/INFO\]:\s+([a-zA-Z0-9_]+)\s+has made the advancement\s+(\[.*\])$", RegexOptions.Compiled);



        private static readonly string[] DeathKeywords = {

            "was slain by", "was shot by", "was blown up", "fell from", "hit the ground", "fell off",

            "drowned", "burned to death", "went up in flames", "tried to swim in lava", "suffocated",

            "starved", "was killed by", "withered away", "froze to death", "died"

        };



        private readonly string _serverDirectory;

        private readonly object _tailerLock = new();

        private readonly object _lineDedupLock = new();

        private string _lastProcessedLine = "";

        private long _lastProcessedTickMs = 0;



        private Process? _process;

        private bool _isAttached = false;

        private bool _restartPending = false;

        private FileStream? _logTailerStream;

        private StreamReader? _logTailerReader;

        private CancellationTokenSource? _logTailerCts;

        private string? _pendingStartupInfo;

        private bool _awaitingFinalStartup = false;

        private CancellationTokenSource? _startupTimeoutCts;

        private ShutdownCause _lastShutdownCause = ShutdownCause.Unknown;

        private string _lastExceptionDetail = "";

        private bool _stopCommandIssued = false;

        private bool _isShuttingDownWithCountdown = false;

        public bool IsShuttingDownWithCountdown => _isShuttingDownWithCountdown;



        public event Action<string>? OnRawLogReceived;

        public event Action<ChatMessageItem>? OnChatMessageReceived;

        public event Action<bool>? OnServerStateChanged; // true = running, false = stopped

        public event Action<string>? OnServerStartedSuccessfully;

        public event Action<ServerShutdownDetails>? OnServerStopped;



        public bool IsRunning => _process != null && !_process.HasExited;

        public bool IsAttached => _isAttached;

        public Process? CurrentProcess => _process;

        public string ServerDirectory => _serverDirectory;



        public ServerProcessManager(string serverDirectory)

        {

            _serverDirectory = serverDirectory;

        }



        public void AttachToPid(int pid)

        {

            if (pid <= 0) return;

            if (_process != null && !_process.HasExited)

            {

                // Se já estamos gerenciando o processo iniciado pelo próprio Dashboard (com StandardOutput ativo) ou já anexados ao mesmo PID, não duplica!

                if (!_isAttached || _process.Id == pid) return;

            }



            try

            {

                var proc = Process.GetProcessById(pid);

                if (!proc.HasExited)

                {

                    StopLogTailer();

                    _process = proc;

                    _isAttached = true;

                    _lastShutdownCause = ShutdownCause.Unknown;

                    _lastExceptionDetail = "";

                    _stopCommandIssued = false;

                    try

                    {

                        _process.EnableRaisingEvents = true;

                        _process.Exited += OnProcessExited;

                    }

                    catch { }



                    OnServerStateChanged?.Invoke(true);

                    StartLogTailer();

                    OnRawLogReceived?.Invoke($"[DASHBOARD] Conectado com sucesso ao processo do servidor (PID {pid}).");

                }

            }

            catch { }

        }



        public List<int> FindServerPids()

        {

            var pids = new List<int>();

            try

            {

                using var searcher = new ManagementObjectSearcher(

                    "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name = 'java.exe'");



                foreach (var obj in searcher.Get())

                {

                    string cmd = obj["CommandLine"]?.ToString() ?? "";

                    if (string.IsNullOrWhiteSpace(cmd)) continue;



                    int pid = Convert.ToInt32(obj["ProcessId"]);

                    if (pid == Environment.ProcessId) continue;



                    if (cmd.Contains("fabric-server-launcher.jar", StringComparison.OrdinalIgnoreCase) ||

                        (!string.IsNullOrEmpty(_serverDirectory) && cmd.Contains(_serverDirectory, StringComparison.OrdinalIgnoreCase)))

                    {

                        pids.Add(pid);

                    }

                }

            }

            catch { }

            return pids;

        }



        public bool IsServerProcess(int pid)

        {

            if (pid <= 0 || pid == Environment.ProcessId) return false;

            try

            {

                var proc = Process.GetProcessById(pid);

                if (proc.HasExited) return false;



                using var searcher = new ManagementObjectSearcher(

                    $"SELECT CommandLine FROM Win32_Process WHERE ProcessId = {pid}");



                foreach (var obj in searcher.Get())

                {

                    string cmd = obj["CommandLine"]?.ToString() ?? "";

                    if (cmd.Contains("fabric-server-launcher.jar", StringComparison.OrdinalIgnoreCase) ||

                        (!string.IsNullOrEmpty(_serverDirectory) && cmd.Contains(_serverDirectory, StringComparison.OrdinalIgnoreCase)))

                    {

                        return true;

                    }

                }

            }

            catch { }

            return false;

        }



        public void KillOrphanedServerInstances()

        {

            try

            {

                // 1. Se já gerenciamos um processo que ainda está rodando

                if (_process != null && !_process.HasExited)

                {

                    try

                    {

                        OnRawLogReceived?.Invoke($"[DASHBOARD] Finalizando processo anterior gerenciado (PID {_process.Id})...");

                        _process.Kill(entireProcessTree: true);

                        _process.WaitForExit(3000);

                    }

                    catch { }

                    _process = null;

                    _isAttached = false;

                }



                // 2. Procura processos java.exe com CommandLine do fabric-server-launcher

                var pidsToKill = new HashSet<int>(FindServerPids());



                // 3. Verifica também PID salvo no server_telemetry.json

                try

                {

                    string telemetryPath = Path.Combine(_serverDirectory, "server_telemetry.json");

                    if (File.Exists(telemetryPath))

                    {

                        string json = File.ReadAllText(telemetryPath);

                        using var doc = System.Text.Json.JsonDocument.Parse(json);

                        if (doc.RootElement.TryGetProperty("pid", out var pidProp) && pidProp.TryGetInt32(out int tPid))

                        {

                            if (tPid != Environment.ProcessId)

                            {

                                try

                                {

                                    var p = Process.GetProcessById(tPid);

                                    if (!p.HasExited && p.ProcessName.Equals("java", StringComparison.OrdinalIgnoreCase))

                                    {

                                        pidsToKill.Add(tPid);

                                    }

                                }

                                catch { }

                            }

                        }

                    }

                }

                catch { }



                // 4. Encerra com segurança apenas as instâncias do nosso servidor

                if (pidsToKill.Count > 0)

                {

                    OnRawLogReceived?.Invoke($"[DASHBOARD] {pidsToKill.Count} instância(s) anterior(es) do servidor detectada(s). Limpando antes de iniciar...");

                    foreach (int pid in pidsToKill)

                    {

                        try

                        {

                            var proc = Process.GetProcessById(pid);

                            if (!proc.HasExited)

                            {

                                OnRawLogReceived?.Invoke($"[DASHBOARD] Encerrando processo servidor órfão/zumbi (PID {pid})...");

                                proc.Kill(entireProcessTree: true);

                                proc.WaitForExit(3000);

                                OnRawLogReceived?.Invoke($"[DASHBOARD] Instância PID {pid} encerrada com sucesso.");

                            }

                        }

                        catch (Exception ex)

                        {

                            OnRawLogReceived?.Invoke($"[DASHBOARD/AVISO] Falha ao encerrar PID {pid}: {ex.Message}");

                        }

                    }

                }

            }

            catch (Exception ex)

            {

                OnRawLogReceived?.Invoke($"[DASHBOARD/AVISO] Erro na limpeza preventiva de instâncias: {ex.Message}");

            }

        }



        public void EnsureSessionLockReleased()

        {

            string lockPath = Path.Combine(_serverDirectory, "world", "session.lock");

            if (!File.Exists(lockPath)) return;



            int attempts = 0;

            while (attempts < 10)

            {

                try

                {

                    using var stream = new FileStream(lockPath, FileMode.Open, FileAccess.ReadWrite, FileShare.None);

                    // Arquivo liberado com sucesso pelo SO

                    return;

                }

                catch (IOException)

                {

                    attempts++;

                    OnRawLogReceived?.Invoke($"[DASHBOARD] Aguardando liberação de world/session.lock... ({attempts}/10)");

                    Thread.Sleep(400);

                }

                catch

                {

                    return;

                }

            }



            OnRawLogReceived?.Invoke("[DASHBOARD/AVISO] world/session.lock ainda parece ocupado, prosseguindo com inicialização...");

        }



        public void CheckOrAttachExisting()

        {

            if (IsRunning) return;



            try

            {

                // Verifica primeiro se server_telemetry.json tem um PID ativo pertencente ao servidor

                string telemetryPath = Path.Combine(_serverDirectory, "server_telemetry.json");

                if (File.Exists(telemetryPath))

                {

                    try

                    {

                        string json = File.ReadAllText(telemetryPath);

                        using var doc = System.Text.Json.JsonDocument.Parse(json);

                        if (doc.RootElement.TryGetProperty("pid", out var pidProp) && pidProp.TryGetInt32(out int pid))

                        {

                            if (IsServerProcess(pid))

                            {

                                AttachToPid(pid);

                                if (IsRunning) return;

                            }

                        }

                    }

                    catch { }

                }



                // Fallback: Procura processo Java que pertença especificamente ao CanalhasSMP

                foreach (int pid in FindServerPids())

                {

                    AttachToPid(pid);

                    if (IsRunning) return;

                }

            }

            catch { }

        }



        public bool StartServer()

        {

            // 1. Limpeza preventiva de instâncias órfãs/zumbis antes de subir o servidor

            KillOrphanedServerInstances();



            // 2. Garante que o session.lock foi liberado para evitar o crash de SessionLock.create

            EnsureSessionLockReleased();



            StopLogTailer();

            _isAttached = false;

            _process = null;

            _awaitingFinalStartup = false;

            _lastShutdownCause = ShutdownCause.Unknown;

            _lastExceptionDetail = "";

            _stopCommandIssued = false;

            try { _startupTimeoutCts?.Cancel(); } catch { }



            string jarPath = Path.Combine(_serverDirectory, "fabric-server-launcher.jar");

            if (!File.Exists(jarPath))

            {

                OnRawLogReceived?.Invoke("[DASHBOARD/ERRO] Arquivo fabric-server-launcher.jar não encontrado em: " + _serverDirectory);

                return false;

            }



            string javaArgs = "-Xmx8G -Xms8G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dlog4j2.formatMsgNoLookups=true -jar fabric-server-launcher.jar nogui";



            var psi = new ProcessStartInfo

            {

                FileName = "java",

                Arguments = javaArgs,

                WorkingDirectory = _serverDirectory,

                RedirectStandardInput = true,

                RedirectStandardOutput = true,

                RedirectStandardError = true,

                UseShellExecute = false,

                CreateNoWindow = true,

                StandardOutputEncoding = Encoding.UTF8,

                StandardErrorEncoding = Encoding.UTF8

            };



            try

            {

                _process = new Process { StartInfo = psi, EnableRaisingEvents = true };

                _process.OutputDataReceived += (s, e) => { if (e.Data != null) ProcessLine(e.Data); };

                _process.ErrorDataReceived += (s, e) => { if (e.Data != null) ProcessLine(e.Data); };

                _process.Exited += OnProcessExited;



                _process.Start();

                _process.BeginOutputReadLine();

                _process.BeginErrorReadLine();



                OnServerStateChanged?.Invoke(true);

                OnRawLogReceived?.Invoke("[DASHBOARD] Servidor iniciado com sucesso! PID: " + _process.Id);

                return true;

            }

            catch (Exception ex)

            {

                OnRawLogReceived?.Invoke("[DASHBOARD/ERRO] Falha ao iniciar processo Java: " + ex.Message);

                _process = null;

                return false;

            }

        }



        public void SendCommand(string command)

        {

            if (string.IsNullOrWhiteSpace(command)) return;



            string trimmed = command.Trim();

            if (trimmed.Equals("stop", StringComparison.OrdinalIgnoreCase) ||

                trimmed.Equals("/stop", StringComparison.OrdinalIgnoreCase))

            {

                if (_lastShutdownCause == ShutdownCause.Unknown)

                {

                    _lastShutdownCause = ShutdownCause.UserCommandStop;

                }

                _stopCommandIssued = true;

            }



            // 1. Se o processo foi iniciado pelo Dashboard e StandardInput está disponível, usa diretamente

            if (_process != null && !_process.HasExited && !_isAttached)

            {

                try

                {

                    _process.StandardInput.WriteLine(command);

                    OnRawLogReceived?.Invoke("[CONSOLE COMANDO] > " + command);

                    CheckAndNotifyConsoleChat(command);

                    return;

                }

                catch (InvalidOperationException)

                {

                    _isAttached = true;

                }

                catch (Exception ex)

                {

                    OnRawLogReceived?.Invoke("[DASHBOARD/AVISO] StandardInput indisponível (" + ex.Message + "). Tentando via Bridge...");

                }

            }



            // 2. Se o processo está anexado ou StandardInput falhou, envia pela Bridge HTTP / Arquivo

            Task.Run(async () =>

            {

                bool success = await SendCommandViaBridgeAsync(command);

                if (!success)

                {

                    if (_process == null || _process.HasExited)

                    {

                        OnRawLogReceived?.Invoke("[DASHBOARD] O servidor está offline. Clique em '▶ Iniciar Servidor'.");

                    }

                    else

                    {

                        OnRawLogReceived?.Invoke("[DASHBOARD/ERRO] Falha ao enviar comando para o servidor.");

                    }

                }

            });

        }



        public async Task<bool> SendCommandViaBridgeAsync(string command)

        {

            // 1. Tenta HTTP Bridge no mod Senhor Cummins

            try

            {

                using var client = new System.Net.Http.HttpClient { Timeout = TimeSpan.FromSeconds(2) };

                var content = new System.Net.Http.StringContent(command, Encoding.UTF8, "text/plain");

                var resp = await client.PostAsync("http://127.0.0.1:25588/command", content);

                if (resp.IsSuccessStatusCode)

                {

                    OnRawLogReceived?.Invoke("[CONSOLE COMANDO] > " + command);

                    CheckAndNotifyConsoleChat(command);

                    return true;

                }

            }

            catch { }



            // 2. Fallback: Fila em arquivo server_command.queue

            try

            {

                string queueFile = Path.Combine(_serverDirectory, "server_command.queue");

                await File.AppendAllTextAsync(queueFile, command + Environment.NewLine, Encoding.UTF8);

                OnRawLogReceived?.Invoke("[CONSOLE COMANDO] > " + command);

                CheckAndNotifyConsoleChat(command);

                return true;

            }

            catch (Exception ex)

            {

                OnRawLogReceived?.Invoke("[DASHBOARD/ERRO] Falha ao gravar comando na fila: " + ex.Message);

                return false;

            }

        }



        private string _lastConsoleSay = "";

        private long _lastConsoleSayTick = 0;



        private void CheckAndNotifyConsoleChat(string command)

        {

            if (string.IsNullOrWhiteSpace(command)) return;

            string trimmed = command.Trim();



            // Detecta comando 'say' ou '/say'

            if (trimmed.StartsWith("say ", StringComparison.OrdinalIgnoreCase) ||

                trimmed.Equals("say", StringComparison.OrdinalIgnoreCase) ||

                trimmed.StartsWith("/say ", StringComparison.OrdinalIgnoreCase) ||

                trimmed.Equals("/say", StringComparison.OrdinalIgnoreCase))

            {

                string time = DateTime.Now.ToString("HH:mm:ss");

                string content = trimmed.StartsWith("/") ? trimmed.Substring(1) : trimmed;



                _lastConsoleSay = content.StartsWith("say ", StringComparison.OrdinalIgnoreCase)

                    ? content.Substring(4).Trim()

                    : content.Trim();

                _lastConsoleSayTick = Environment.TickCount64;



                OnChatMessageReceived?.Invoke(new ChatMessageItem

                {

                    Time = time,

                    Sender = "[CONSOLE COMANDO]",

                    Content = "> " + content,

                    Type = ChatType.ConsoleCommand

                });

            }

        }



        public void StopServer(bool withCountdown = true)

        {

            if (!IsRunning || _isShuttingDownWithCountdown) return;



            if (!withCountdown)

            {

                _lastShutdownCause = ShutdownCause.PanelStopButton;

                _stopCommandIssued = true;

                SendCommand("stop");

                OnRawLogReceived?.Invoke("[DASHBOARD] Enviado comando 'stop' para desligamento imediato.");

                return;

            }



            _isShuttingDownWithCountdown = true;

            _lastShutdownCause = ShutdownCause.PanelStopButton;



            Task.Run(async () =>

            {

                try

                {

                    OnRawLogReceived?.Invoke("[DASHBOARD] ⚠ Iniciando aviso global e contagem regressiva de 5 segundos para desligamento...");

                    SendCommand("title @a times 5 20 5");

                    SendCommand("save-all");



                    for (int i = 5; i >= 1; i--)

                    {

                        if (!IsRunning) break;



                        string color = i switch

                        {

                            5 => "gold",

                            4 => "yellow",

                            3 => "yellow",

                            2 => "red",

                            _ => "dark_red"

                        };



                        string soundPitch = (0.75f + (5 - i) * 0.25f).ToString(System.Globalization.CultureInfo.InvariantCulture);



                        SendCommand($"title @a subtitle {{\"text\":\"O servidor será desligado em {i} segundo{(i > 1 ? "s" : "")}!\",\"color\":\"{color}\"}}");

                        SendCommand($"title @a title {{\"text\":\"⚠ {i} ⚠\",\"color\":\"{color}\",\"bold\":true}}");

                        SendCommand($"say §c[AVISO] ⚠ O servidor será desligado em {i} segundo{(i > 1 ? "s" : "")}! Salvem seus itens e fiquem em local seguro!");

                        SendCommand($"playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 {soundPitch}");



                        await Task.Delay(1000);

                    }



                    if (IsRunning)

                    {

                        _stopCommandIssued = true;

                        SendCommand("save-all");

                        SendCommand("stop");

                        OnRawLogReceived?.Invoke("[DASHBOARD] Contagem regressiva concluída. Enviado comando 'stop' final.");

                    }

                }

                catch (Exception ex)

                {

                    OnRawLogReceived?.Invoke($"[DASHBOARD/ERRO] Falha durante contagem regressiva: {ex.Message}");

                    _stopCommandIssued = true;

                    SendCommand("stop");

                }

                finally

                {

                    _isShuttingDownWithCountdown = false;

                }

            });

        }



        public void KillServer()

        {

            _lastShutdownCause = ShutdownCause.ForcedTermination;

            try

            {

                if (_process != null && !_process.HasExited)

                {

                    OnRawLogReceived?.Invoke($"[DASHBOARD] Forçando encerramento imediato do processo (PID {_process.Id})...");

                    _process.Kill(entireProcessTree: true);

                }

                else

                {

                    KillOrphanedServerInstances();

                }

            }

            catch (Exception ex)

            {

                OnRawLogReceived?.Invoke($"[DASHBOARD/ERRO] Falha ao forçar encerramento: {ex.Message}");

            }

        }



        public void RestartServer(bool withCountdown = true)
        {
            if (!IsRunning)
            {
                StartServer();
                return;
            }

            if (!withCountdown)
            {
                _restartPending = true;
                _lastShutdownCause = ShutdownCause.PanelRestartButton;
                _stopCommandIssued = true;
                SendCommand("stop");
                OnRawLogReceived?.Invoke("[DASHBOARD] Reiniciando servidor: enviado 'stop' imediato, aguardando finalização...");
                return;
            }

            if (_isShuttingDownWithCountdown) return;
            _isShuttingDownWithCountdown = true;
            _restartPending = true;
            _lastShutdownCause = ShutdownCause.PanelRestartButton;

            Task.Run(async () =>
            {
                try
                {
                    OnRawLogReceived?.Invoke("[DASHBOARD] 🔄 Iniciando aviso global e contagem regressiva de 5 segundos para REINICIALIZAÇÃO...");
                    SendCommand("title @a times 5 20 5");
                    SendCommand("save-all");

                    for (int i = 5; i >= 1; i--)
                    {
                        if (!IsRunning) break;

                        string color = i switch
                        {
                            5 => "gold",
                            4 => "yellow",
                            3 => "yellow",
                            2 => "red",
                            _ => "dark_red"
                        };

                        string soundPitch = (0.75f + (5 - i) * 0.25f).ToString(System.Globalization.CultureInfo.InvariantCulture);

                        SendCommand($"title @a subtitle {{\"text\":\"O servidor reiniciará em {i} segundo{(i > 1 ? "s" : "")}!\",\"color\":\"{color}\"}}");
                        SendCommand($"title @a title {{\"text\":\"🔄 {i} 🔄\",\"color\":\"{color}\",\"bold\":true}}");
                        SendCommand($"say §e[REINÍCIO] 🔄 O servidor será reiniciado em {i} segundo{(i > 1 ? "s" : "")}! Salvem seus itens e fiquem em local seguro!");
                        SendCommand($"playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 {soundPitch}");

                        await Task.Delay(1000);
                    }

                    if (IsRunning)
                    {
                        _stopCommandIssued = true;
                        SendCommand("save-all");
                        SendCommand("stop");
                        OnRawLogReceived?.Invoke("[DASHBOARD] Contagem concluída. Enviado comando 'stop' para reinício.");
                    }
                }
                catch (Exception ex)
                {
                    OnRawLogReceived?.Invoke($"[DASHBOARD/ERRO] Falha durante contagem regressiva de reinício: {ex.Message}");
                    _stopCommandIssued = true;
                    SendCommand("stop");
                }
                finally
                {
                    _isShuttingDownWithCountdown = false;
                }
            });

            // Timeout de segurança: se após 25s o servidor travar ao encerrar, força o término
            var currentProc = _process;
            Task.Run(async () =>
            {
                await Task.Delay(25000);
                if (_restartPending && currentProc != null && !currentProc.HasExited)
                {
                    OnRawLogReceived?.Invoke("[DASHBOARD/AVISO] O servidor demorou mais de 25s para encerrar. Forçando término para prosseguir com o reinício...");
                    try
                    {
                        currentProc.Kill(entireProcessTree: true);
                    }
                    catch { }
                }
            });
        }

        private void OnProcessExited(object? sender, EventArgs e)

        {

            _awaitingFinalStartup = false;

            try { _startupTimeoutCts?.Cancel(); } catch { }

            StopLogTailer();



            int exitCode = _process?.ExitCode ?? 0;

            _process = null;

            _isAttached = false;

            OnServerStateChanged?.Invoke(false);

            OnRawLogReceived?.Invoke($"[DASHBOARD] Servidor finalizado com código {exitCode}.");



            // Determina o motivo do desligamento com máxima clareza

            ShutdownCause finalCause = _lastShutdownCause;

            string causeDesc;



            if (exitCode != 0 && finalCause != ShutdownCause.ForcedTermination)

            {

                finalCause = ShutdownCause.CrashOrException;

            }



            switch (finalCause)

            {

                case ShutdownCause.PanelStopButton:

                    causeDesc = "Botão 'Parar (Stop)' clicado no Dashboard (Desligamento gracioso)";

                    break;

                case ShutdownCause.PanelRestartButton:

                    causeDesc = "Botão 'Reiniciar' acionado no Dashboard (Reinício automático em andamento)";

                    break;

                case ShutdownCause.UserCommandStop:

                    causeDesc = "Comando 'stop' enviado pelo console ou por jogador";

                    break;

                case ShutdownCause.ForcedTermination:

                    causeDesc = "Processo encerrado forçadamente pelo usuário (Kill)";

                    break;

                case ShutdownCause.CrashOrException:

                    causeDesc = !string.IsNullOrWhiteSpace(_lastExceptionDetail)

                        ? "Exceção não tratada ou Crash inesperado do servidor"

                        : $"Encerramento anormal do processo Java com código de erro {exitCode}";

                    break;

                default:

                    if (_stopCommandIssued || exitCode == 0)

                    {

                        finalCause = ShutdownCause.UserCommandStop;

                        causeDesc = "Desligamento seguro e gracioso concluído";

                    }

                    else

                    {

                        finalCause = ShutdownCause.CrashOrException;

                        causeDesc = $"Queda inesperada do processo Java (Código de erro: {exitCode})";

                    }

                    break;

            }



            var details = new ServerShutdownDetails

            {

                ExitCode = exitCode,

                Cause = finalCause,

                CauseDescription = causeDesc,

                ExceptionDetail = _lastExceptionDetail

            };



            OnServerStopped?.Invoke(details);



            if (_restartPending)

            {

                _restartPending = false;

                Task.Delay(2000).ContinueWith(_ => StartServer());

            }

        }



        private void StartLogTailer()

        {

            lock (_tailerLock)

            {

                StopLogTailer();



                string logFile = Path.Combine(_serverDirectory, "logs", "latest.log");

                if (!File.Exists(logFile)) return;



                var cts = new CancellationTokenSource();

                _logTailerCts = cts;



                Task.Run(async () =>

                {

                    FileStream? fs = null;

                    StreamReader? reader = null;

                    try

                    {

                        fs = new FileStream(logFile, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);

                        reader = new StreamReader(fs, Encoding.UTF8);

                        lock (_tailerLock)

                        {

                            if (cts.IsCancellationRequested)

                            {

                                reader.Dispose();

                                fs.Dispose();

                                return;

                            }

                            _logTailerStream = fs;

                            _logTailerReader = reader;

                        }



                        // Pula para o final do arquivo

                        fs.Seek(0, SeekOrigin.End);



                        while (!cts.Token.IsCancellationRequested)

                        {

                            string? line = await reader.ReadLineAsync(cts.Token);

                            if (line != null)

                            {

                                ProcessLine(line);

                            }

                            else

                            {

                                await Task.Delay(250, cts.Token);

                            }

                        }

                    }

                    catch { }

                    finally

                    {

                        try { reader?.Dispose(); } catch { }

                        try { fs?.Dispose(); } catch { }

                    }

                }, cts.Token);

            }

        }



        private void StopLogTailer()

        {

            lock (_tailerLock)

            {

                try { _logTailerCts?.Cancel(); } catch { }

                try { _logTailerReader?.Dispose(); } catch { }

                try { _logTailerStream?.Dispose(); } catch { }

                _logTailerCts = null;

                _logTailerReader = null;

                _logTailerStream = null;

            }

        }



        public void ProcessLine(string line)

        {

            if (string.IsNullOrEmpty(line)) return;



            // Filtro anti-duplicação caso dois fluxos (ex: StandardOutput + LogTailer) emitam a mesma linha simultaneamente

            lock (_lineDedupLock)

            {

                long nowMs = Environment.TickCount64;

                if (line == _lastProcessedLine && (nowMs - _lastProcessedTickMs) < 80)

                {

                    return;

                }

                _lastProcessedLine = line;

                _lastProcessedTickMs = nowMs;

            }



            // 0. Detecta quando o servidor conclui a inicialização preliminar (ex: Done (14.285s)! For help, type "help")

            if (line.Contains("[Server thread/INFO]: Done (") ||

                (line.Contains("Done (") && line.Contains("For help, type \"help\"")))

            {

                string info = "";

                var m = Regex.Match(line, @"Done \(([^)]+)\)");

                if (m.Success)

                {

                    info = m.Groups[1].Value;

                }



                _pendingStartupInfo = info;

                _awaitingFinalStartup = true;



                // Emite a linha "Done (...)" normalmente nos logs gerais

                OnRawLogReceived?.Invoke(line);



                // Timeout de segurança: caso o mod Xaero não esteja presente ou falhe, dispara em 3.5 segundos

                _startupTimeoutCts?.Cancel();

                _startupTimeoutCts = new CancellationTokenSource();

                var token = _startupTimeoutCts.Token;

                Task.Delay(3500, token).ContinueWith(t =>

                {

                    if (!t.IsCanceled && _awaitingFinalStartup)

                    {

                        _awaitingFinalStartup = false;

                        OnServerStartedSuccessfully?.Invoke(_pendingStartupInfo ?? "");

                    }

                }, TaskScheduler.Default);



                return;

            }



            // 1. Emite para logs gerais

            OnRawLogReceived?.Invoke(line);



            // 1.1 Se estamos aguardando a finalização completa pós-Done (ex: Xaero's World Map)

            if (_awaitingFinalStartup)

            {

                if (line.Contains("Loading Xaero's World Map - Stage 2/2", StringComparison.OrdinalIgnoreCase) ||

                    line.Contains("Xaero's World Map - Stage 2/2", StringComparison.OrdinalIgnoreCase))

                {

                    _awaitingFinalStartup = false;

                    try { _startupTimeoutCts?.Cancel(); } catch { }

                    OnServerStartedSuccessfully?.Invoke(_pendingStartupInfo ?? "");

                }

            }



            // 1.2 Detecção de início de desligamento (comando stop ou fechamento)

            if (line.Contains("Stopping the server", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("[Server thread/INFO]: Stopping server", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("Stopping server", StringComparison.OrdinalIgnoreCase))

            {

                if (!_stopCommandIssued)

                {

                    _stopCommandIssued = true;

                    if (_lastShutdownCause == ShutdownCause.Unknown)

                    {

                        _lastShutdownCause = ShutdownCause.UserCommandStop;

                    }

                }

            }



            // 1.3 Detecção de exceções graves e crashes

            if (line.Contains("Encountered an unexpected exception", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("This crash report has been saved to", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("OutOfMemoryError", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("A fatal error has been detected", StringComparison.OrdinalIgnoreCase) ||

                line.Contains("Minecraft has crashed", StringComparison.OrdinalIgnoreCase))

            {

                _lastExceptionDetail = line.Trim();

                if (!_stopCommandIssued)

                {

                    _lastShutdownCause = ShutdownCause.CrashOrException;

                }

            }



            // 2. Filtra se é chat in-game

            var matchChat = ChatRegex.Match(line);

            if (matchChat.Success)

            {

                string time = matchChat.Groups[1].Value;

                string sender = matchChat.Groups[2].Value;

                string msg = matchChat.Groups[3].Value;



                bool isCummins = sender.Contains("Cummins", StringComparison.OrdinalIgnoreCase);



                OnChatMessageReceived?.Invoke(new ChatMessageItem

                {

                    Time = time,

                    Sender = sender,

                    Content = msg,

                    Type = isCummins ? ChatType.CumminsChat : ChatType.PlayerChat

                });

                return;

            }



            var matchServerSay = ServerSayRegex.Match(line);

            if (matchServerSay.Success)

            {

                string time = matchServerSay.Groups[1].Value;

                string msg = matchServerSay.Groups[2].Value;



                // Evita duplicata se o comando say do console já tiver acabado de ser emitido

                long elapsed = Environment.TickCount64 - _lastConsoleSayTick;

                if (elapsed > 2500 || !msg.Trim().Equals(_lastConsoleSay, StringComparison.OrdinalIgnoreCase))

                {

                    OnChatMessageReceived?.Invoke(new ChatMessageItem

                    {

                        Time = time,

                        Sender = "[CONSOLE COMANDO]",

                        Content = "> say " + msg,

                        Type = ChatType.ConsoleCommand

                    });

                }

                return;

            }



            var matchAdv = AdvRegex.Match(line);

            if (matchAdv.Success)

            {

                OnChatMessageReceived?.Invoke(new ChatMessageItem

                {

                    Time = matchAdv.Groups[1].Value,

                    Sender = matchAdv.Groups[2].Value,

                    Content = "desbloqueou a conquista " + matchAdv.Groups[3].Value,

                    Type = ChatType.Advancement

                });

                return;

            }



            var matchJoin = JoinRegex.Match(line);

            if (matchJoin.Success)

            {

                OnChatMessageReceived?.Invoke(new ChatMessageItem

                {

                    Time = matchJoin.Groups[1].Value,

                    Sender = matchJoin.Groups[2].Value,

                    Content = "entrou no servidor",

                    Type = ChatType.Join

                });

                return;

            }



            var matchLeave = LeaveRegex.Match(line);

            if (matchLeave.Success)

            {

                OnChatMessageReceived?.Invoke(new ChatMessageItem

                {

                    Time = matchLeave.Groups[1].Value,

                    Sender = matchLeave.Groups[2].Value,

                    Content = "saiu do jogo",

                    Type = ChatType.Leave

                });

                return;

            }



            // Verifica mortes

            if (line.Contains("[Server thread/INFO]:"))

            {

                foreach (string kw in DeathKeywords)

                {

                    if (line.Contains(kw, StringComparison.OrdinalIgnoreCase))

                    {

                        string clean = line.Substring(line.IndexOf("[Server thread/INFO]:") + 21).Trim();

                        string time = line.Length >= 10 && line.StartsWith("[") ? line.Substring(1, 8) : "";

                        OnChatMessageReceived?.Invoke(new ChatMessageItem

                        {

                            Time = time,

                            Sender = "Servidor",

                            Content = "☠ " + clean,

                            Type = ChatType.Death

                        });

                        break;

                    }

                }

            }

        }

    }

}

