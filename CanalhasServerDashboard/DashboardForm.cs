using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

namespace CanalhasServerDashboard
{
    public partial class DashboardForm : Form
    {
        private readonly string _serverDir;
        private readonly ServerProcessManager _server;
        private readonly PerformanceSampler _sampler;
        private readonly System.Windows.Forms.Timer _timer;

        // Histórico de comandos para navegação com Setas Cima/Baixo
        private readonly List<string> _commandHistory = new();
        private int _commandHistoryIndex = -1;

        // Tooltip para métricas interativas
        private readonly ToolTip _metricToolTip = new()
        {
            AutoPopDelay = 15000,
            InitialDelay = 250,
            ReshowDelay = 100,
            ShowAlways = true
        };

        // Controles de UI
        private Label _lblStatusBadge = null!;
        private Label _lblAlertBanner = null!;
        private Label _lblTpsVal = null!;
        private Label _lblTpsSub = null!;
        private Label _lblMsptVal = null!;
        private Label _lblMsptSub = null!;
        private Label _lblCpuVal = null!;
        private Label _lblCpuSub = null!;
        private Label _lblRamVal = null!;
        private Label _lblRamSub = null!;
        private Panel _panelRamBar = null!;
        private Label _lblGcVal = null!;
        private Label _lblGcSub = null!;
        private Label _lblPlayersVal = null!;
        private Label _lblPlayersSub = null!;
        private Label _lblTokensVal = null!;
        private Label _lblTokensSub = null!;
        private Panel _panelTokensBar = null!;

        private RichTextBox _rtbChat = null!;
        private RichTextBox _rtbLogs = null!;
        private TextBox _txtCommand = null!;
        private TextBox _txtSay = null!;
        private TextBox _txtLogFilter = null!;
        private CheckBox _chkAutoScroll = null!;
        private CheckBox _chkOnlyErrors = null!;
        private PictureBox _picGraph = null!;
        private PictureBox _picMap = null!;
        private FlowLayoutPanel _pnlMapPlayers = null!;
        private Label _lblMapCoords = null!;
        private ComboBox _cmbDimension = null!;
        private CheckBox _chkShowChunkyCircle = null!;
        private CheckBox _chkShowTerrain = null!;
        private AnvilMapTileRenderer _tileRenderer = null!;
        private DateTime _lastTileInvalidate = DateTime.MinValue;
        private Panel _pnlMuralContent = null!;
        private Label _lblMuralVersion = null!;
        private int _tickCount = 0;
        private double _mapPanX = 0;
        private double _mapPanZ = 0;
        private float _mapZoom = 0.05f;
        private bool _isPanning = false;
        private Point _lastMousePos;
        private HashSet<(int rx, int rz)> _overworldRegions = new();
        private HashSet<(int rx, int rz)> _netherRegions = new();
        private HashSet<(int rx, int rz)> _endRegions = new();
        private DateTime _lastRegionScan = DateTime.MinValue;
        private string _lastPlayersSidebarSignature = "";

        private Button _btnStart = null!;
        private Button _btnStop = null!;
        private Button _btnRestart = null!;
        private Button _btnSpark = null!;
        private Button _btnVitoriaAi = null!;
        private bool _sparkRunning = false;
        private bool _vitoriaAiEnabled = false;

        public DashboardForm()
        {
            // Descobre o diretório do servidor CanalhasSMP
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string candidate = Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", "CanalhasSMP"));
            if (!Directory.Exists(candidate))
            {
                candidate = Path.GetFullPath(Path.Combine(baseDir, "..", "CanalhasSMP"));
            }
            if (!Directory.Exists(candidate))
            {
                candidate = @"c:\Users\Asminn\Documents\CanalhasSMP\CanalhasSMP";
            }
            _serverDir = candidate;

            _server = new ServerProcessManager(_serverDir);
            _sampler = new PerformanceSampler(_serverDir);
            _tileRenderer = new AnvilMapTileRenderer(_serverDir, OnTileRendered);

            InitializeComponent();

            _server.OnRawLogReceived += Server_OnRawLogReceived;
            _server.OnChatMessageReceived += Server_OnChatMessageReceived;
            _server.OnServerStateChanged += Server_OnServerStateChanged;
            _server.OnServerStartedSuccessfully += Server_OnServerStartedSuccessfully;
            _server.OnServerStopped += Server_OnServerStopped;

            _timer = new System.Windows.Forms.Timer { Interval = 1000 };
            _timer.Tick += Timer_Tick;
            _timer.Start();

            // Tenta detectar processo existente ou inicia
            _server.CheckOrAttachExisting();
        }

        private void OnTileRendered()
        {
            if (this.IsDisposed || _picMap == null || _picMap.IsDisposed) return;
            var now = DateTime.UtcNow;
            if ((now - _lastTileInvalidate).TotalMilliseconds < 120) return;
            _lastTileInvalidate = now;
            try
            {
                this.BeginInvoke(new Action(() =>
                {
                    if (!_picMap.IsDisposed) _picMap.Invalidate();
                }));
            }
            catch { }
        }

        private void InitializeComponent()
        {
            this.Text = "CanalhasSMP • Monitor de Desempenho & Console";
            this.Size = new Size(1280, 820);
            this.MinimumSize = new Size(1000, 650);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.FromArgb(24, 24, 24);
            this.ForeColor = Color.FromArgb(224, 224, 224);
            this.Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);

            // Carrega o ícone oficial do servidor Minecraft diretamente do caminho original (sem duplicar arquivo)
            LoadServerIcon();

            // 1. Barra de Cabeçalho Superior
            var topBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 55,
                BackColor = Color.FromArgb(30, 30, 30),
                Padding = new Padding(15, 0, 15, 0)
            };

            // Ícone oficial do Servidor Minecraft exibido no topo
            string serverIconPath = GetServerIconPath();
            var picServerIcon = new PictureBox
            {
                Size = new Size(38, 38),
                Location = new Point(14, 8),
                SizeMode = PictureBoxSizeMode.Zoom,
                BackColor = Color.Transparent,
                Cursor = Cursors.Hand
            };
            if (File.Exists(serverIconPath))
            {
                try
                {
                    picServerIcon.Image = Image.FromFile(serverIconPath);
                    _metricToolTip.SetToolTip(picServerIcon, "Ícone oficial do servidor (server-icon.png)\nO mesmo exibido na lista multijogador do Minecraft.");
                }
                catch { }
            }

            var lblTitle = new Label
            {
                Text = "CANALHAS SMP",
                Font = new Font("Segoe UI", 13f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 201, 58),
                AutoSize = true,
                Location = new Point(58, 15)
            };

            _lblStatusBadge = new Label
            {
                Text = "● PARADO",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(160, 160, 160),
                AutoSize = true,
                Location = new Point(220, 18)
            };

            // Atalhos rápidos no cabeçalho (usando FlowLayoutPanel ancorado à direita para suporte total a maximização)
            var topButtonsFlow = new FlowLayoutPanel
            {
                Dock = DockStyle.Right,
                AutoSize = true,
                AutoSizeMode = AutoSizeMode.GrowAndShrink,
                FlowDirection = FlowDirection.LeftToRight,
                WrapContents = false,
                BackColor = Color.Transparent,
                Padding = new Padding(0, 11, 15, 0)
            };

            _btnSpark = CreateHeaderButton("⚡ Spark", 88, (s, e) => ToggleSpark());
            var btnBackup = CreateHeaderButton("💾 Backup", 80, (s, e) => _server.SendCommand("textilebackup run"));
            var btnChunky = CreateHeaderButton("🗺️ Chunky", 84, (s, e) => _server.SendCommand("chunky progress"));
            var btnCumminsGato = CreateHeaderButton("🐱 Gato TP", 86, (s, e) => _server.SendCommand("cummins gato tp"));
            var btnVitoria = CreateHeaderButton("🐶 Vitória", 86, (s, e) => _server.SendCommand("cummins vitoria"));
            _btnVitoriaAi = CreateHeaderButton("🧠 Vitória: OFF", 110, (s, e) => ToggleVitoriaAi());
            var btnMural = CreateHeaderButton("📜 Mural", 80, (s, e) => _server.SendCommand("cummins mural todos"));
            var btnCumminsPerola = CreateHeaderButton("🎲 Pérola", 80, (s, e) => _server.SendCommand("cummins perola"));
            var btnGc = CreateHeaderButton("🧹 GC", 60, (s, e) => _server.SendCommand("spark gc"));
            var btnFirewall = CreateHeaderButton("🛡️ Firewall", 86, (s, e) => LiberarFirewallDoCelular());

            topButtonsFlow.Controls.AddRange(new Control[] { _btnSpark, btnBackup, btnChunky, btnCumminsGato, btnVitoria, _btnVitoriaAi, btnMural, btnCumminsPerola, btnGc, btnFirewall });
            topBar.Controls.AddRange(new Control[] { picServerIcon, lblTitle, _lblStatusBadge, topButtonsFlow });

            // 2. Banner de Alertas (GC / Travamento)
            _lblAlertBanner = new Label
            {
                Dock = DockStyle.Top,
                Height = 32,
                BackColor = Color.FromArgb(90, 48, 16),
                ForeColor = Color.FromArgb(255, 204, 128),
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                TextAlign = ContentAlignment.MiddleLeft,
                Padding = new Padding(15, 0, 15, 0),
                Visible = false
            };

            // 3. Painel de Cards de Métricas (Estilo Spark)
            var metricsPanel = new TableLayoutPanel
            {
                Dock = DockStyle.Top,
                Height = 90,
                ColumnCount = 7,
                RowCount = 1,
                BackColor = Color.FromArgb(20, 20, 20),
                Padding = new Padding(8, 6, 8, 6)
            };
            for (int i = 0; i < 7; i++) metricsPanel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 14.285f));

            var cardTps = CreateMetricCard("TPS (Ticks/s)", "TPS",
                "⚡ TPS (Ticks por Segundo)\n" +
                "Mede a estabilidade e ritmo de cálculo do mundo.\n" +
                "• 20.0 TPS: Tempo real perfeito (alvo absoluto)\n" +
                "• 16.0 - 19.4 TPS: Alerta (pequenos atrasos)\n" +
                "• < 16.0 TPS: Lag severo evidente",
                out _lblTpsVal, out _lblTpsSub, "20.00", "Estabilidade (Alvo: 20)");

            var cardMspt = CreateMetricCard("MSPT (Tick Time)", "MSPT",
                "⏱️ MSPT (Milissegundos por Tick)\n" +
                "Tempo que o servidor leva calculando cada tick.\n" +
                "• < 35ms: Excelente folga de processador\n" +
                "• 35 - 50ms: Próximo do limite de 20 TPS\n" +
                "• > 50ms: Orçamento estourado (gera lag)",
                out _lblMsptVal, out _lblMsptSub, "-- ms", "Mediana / 95th / Max");

            var cardCpu = CreateMetricCard("CPU Servidor", "CPU",
                "🖥️ CPU Servidor (Processador)\n" +
                "Uso de CPU pelo processo Java do servidor.\n" +
                "O Minecraft depende muito de uma única thread principal.\n" +
                "• < 50%: Saudável\n" +
                "• > 80%: Gargalo de processamento",
                out _lblCpuVal, out _lblCpuSub, "-- %", "Processo Java");

            var cardRam = CreateMetricCardWithBar("RAM (Heap JVM)", "RAM",
                "🧠 RAM (Heap da JVM)\n" +
                "Memória ocupada por chunks carregados e entidades.\n" +
                "• 40% - 75%: Operação ideal (padrão dente de serra)\n" +
                "• > 88%: Atenção (memória quase cheia)\n" +
                "• > 95%: Risco iminente de congelamento ou crash",
                out _lblRamVal, out _lblRamSub, out _panelRamBar, "-- GB", "-- % em uso");

            var cardGc = CreateMetricCard("Garbage Collector", "GC",
                "🧹 Garbage Collector (Limpeza de Memória)\n" +
                "Faxina automática de objetos temporários do Java.\n" +
                "• Saudável: Pausas imperceptíveis (< 100ms)\n" +
                "• Pausas longas geram micro-travamentos (stutters)",
                out _lblGcVal, out _lblGcSub, "Saudável", "0 pausas anômalas");

            var cardPlayers = CreateMetricCard("Jogadores Online", "PLAYERS",
                "👥 Jogadores Online & Dispersão\n" +
                "População conectada e impacto no mapa.\n" +
                "Jogadores em locais distantes aumentam chunks ativos e CPU.",
                out _lblPlayersVal, out _lblPlayersSub, "0 Online", "Nenhum no momento");

            var cardTokens = CreateMetricCardWithBar("Tokens IA (LLM)", "TOKENS",
                "🤖 Tokens LLM (Inteligência Artificial)\n" +
                "Consumo de tokens do Senhor Cummins e pets IA.\n" +
                "• Utilizados: Mostra o gasto acumulado no ciclo.\n" +
                "• Restantes: Saldo da cota até a renovação.\n" +
                "• Reset: Contagem regressiva até a renovação da cota.",
                out _lblTokensVal, out _lblTokensSub, out _panelTokensBar, "-- / --", "Carregando...",
                (s, e) =>
                {
                    if (s is not Panel p) return;
                    var telem = _sampler.LatestTelemetry;
                    var tok = telem?.Tokens;
                    double pct = tok != null ? Math.Clamp(tok.PercentUsed, 0.0, 100.0) : 0;
                    int w = (int)((p.Width * pct) / 100.0);
                    Color barColor = pct >= 90 ? Color.FromArgb(241, 76, 76) : (pct >= 70 ? Color.FromArgb(255, 201, 58) : Color.FromArgb(78, 201, 176));
                    e.Graphics.FillRectangle(new SolidBrush(barColor), 0, 0, w, p.Height);
                });

            metricsPanel.Controls.Add(cardTps, 0, 0);
            metricsPanel.Controls.Add(cardMspt, 1, 0);
            metricsPanel.Controls.Add(cardCpu, 2, 0);
            metricsPanel.Controls.Add(cardRam, 3, 0);
            metricsPanel.Controls.Add(cardGc, 4, 0);
            metricsPanel.Controls.Add(cardPlayers, 5, 0);
            metricsPanel.Controls.Add(cardTokens, 6, 0);

            // 4. Barra Inferior de Comandos
            var bottomBar = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 60,
                BackColor = Color.FromArgb(30, 30, 30),
                Padding = new Padding(12, 10, 12, 10)
            };

            _btnStart = new Button
            {
                Text = "▶ Iniciar Servidor",
                Width = 135,
                Height = 38,
                Location = new Point(12, 11),
                BackColor = Color.FromArgb(46, 125, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            _btnStart.FlatAppearance.BorderSize = 0;
            _btnStart.Click += (s, e) =>
            {
                _rtbLogs.Clear();
                _server.StartServer();
            };

            _btnStop = new Button
            {
                Text = "⏹ Parar (Stop)",
                Width = 115,
                Height = 38,
                Location = new Point(152, 11),
                BackColor = Color.FromArgb(198, 40, 40),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            _btnStop.FlatAppearance.BorderSize = 0;
            _btnStop.Click += (s, e) =>
            {
                if (!_server.IsRunning || _server.IsShuttingDownWithCountdown) return;
                _btnStop.Enabled = false;
                _btnStop.Text = "⏳ Fechando (5s)...";
                _btnRestart.Enabled = false;
                _server.StopServer();
            };

            _btnRestart = new Button
            {
                Text = "🔄 Reiniciar",
                Width = 100,
                Height = 38,
                Location = new Point(272, 11),
                BackColor = Color.FromArgb(21, 101, 192),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            _btnRestart.FlatAppearance.BorderSize = 0;
            _btnRestart.Click += (s, e) =>
            {
                if (!_server.IsRunning || _server.IsShuttingDownWithCountdown) return;
                _btnRestart.Enabled = false;
                _btnRestart.Text = "⏳ Reiniciando (5s)...";
                _btnStop.Enabled = false;
                _rtbLogs.Clear();
                _server.RestartServer();
            };

            var lblCmdPrefix = new Label
            {
                Text = "Console >",
                AutoSize = true,
                Location = new Point(385, 20),
                ForeColor = Color.FromArgb(255, 201, 58),
                Font = new Font("Consolas", 11f, FontStyle.Bold)
            };

            _txtCommand = new TextBox
            {
                Location = new Point(475, 16),
                Height = 30,
                Width = 640,
                Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top,
                BackColor = Color.FromArgb(42, 42, 42),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Consolas", 10.5f)
            };
            _txtCommand.KeyDown += TxtCommand_KeyDown;

            var btnSend = new Button
            {
                Text = "Enviar",
                Width = 85,
                Height = 30,
                Location = new Point(1125, 15),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                BackColor = Color.FromArgb(55, 55, 55),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnSend.FlatAppearance.BorderSize = 0;
            btnSend.Click += (s, e) => SendTypedCommand();

            bottomBar.Controls.AddRange(new Control[] { _btnStart, _btnStop, _btnRestart, lblCmdPrefix, _txtCommand, btnSend });

            // 5. Abas Centrais: Chat In-Game / Logs do Sistema / Gráfico
            var tabControl = new TabControl
            {
                Dock = DockStyle.Fill,
                Padding = new Point(16, 8),
                Font = new Font("Segoe UI", 10f, FontStyle.Regular)
            };

            // ABA 1: Chat In-Game
            var tabChat = new TabPage("💬 Chat In-Game (Limpo)") { BackColor = Color.FromArgb(20, 20, 20) };
            SetupChatTab(tabChat);

            // ABA 2: Logs do Sistema
            var tabLogs = new TabPage("📜 Logs do Sistema (Completo)") { BackColor = Color.FromArgb(20, 20, 20) };
            SetupLogsTab(tabLogs);

            // ABA 3: Gráficos de Performance
            var tabGraph = new TabPage("📈 Gráficos em Tempo Real") { BackColor = Color.FromArgb(20, 20, 20) };
            SetupGraphTab(tabGraph);

            // ABA 4: Mapa do Mundo & Radar de Jogadores
            var tabMap = new TabPage("🗺️ Mapa do Mundo & Jogadores") { BackColor = Color.FromArgb(20, 20, 20) };
            SetupMapTab(tabMap);

            // ABA 5: Mural de Novidades In-Game
            var tabMural = new TabPage("📜 Mural de Novidades") { BackColor = Color.FromArgb(20, 20, 20) };
            SetupMuralTab(tabMural);

            tabControl.TabPages.AddRange(new TabPage[] { tabChat, tabLogs, tabGraph, tabMap, tabMural });

            // Adiciona na ordem correta
            this.Controls.Add(tabControl);
            this.Controls.Add(bottomBar);
            this.Controls.Add(metricsPanel);
            this.Controls.Add(_lblAlertBanner);
            this.Controls.Add(topBar);
        }

        private void SetupChatTab(TabPage tab)
        {
            _rtbChat = new RichTextBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(22, 22, 22),
                ForeColor = Color.FromArgb(220, 220, 220),
                Font = new Font("Segoe UI", 10.5f, FontStyle.Regular),
                BorderStyle = BorderStyle.None,
                ReadOnly = true
            };

            var bottomChatBar = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 44,
                BackColor = Color.FromArgb(28, 28, 28),
                Padding = new Padding(8, 6, 8, 6)
            };

            var lblSay = new Label
            {
                Text = "Falar no Jogo:",
                AutoSize = true,
                Location = new Point(10, 12),
                ForeColor = Color.FromArgb(78, 201, 176),
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold)
            };

            _txtSay = new TextBox
            {
                Location = new Point(115, 8),
                Height = 28,
                Width = 950,
                Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top,
                BackColor = Color.FromArgb(42, 42, 42),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 10f)
            };
            _txtSay.KeyDown += (s, e) =>
            {
                if (e.KeyCode == Keys.Enter)
                {
                    e.SuppressKeyPress = true;
                    SendSayMessage();
                }
            };

            var btnSendSay = new Button
            {
                Text = "Falar [say]",
                Width = 100,
                Height = 28,
                Location = new Point(1075, 8),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                BackColor = Color.FromArgb(78, 201, 176),
                ForeColor = Color.Black,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnSendSay.FlatAppearance.BorderSize = 0;
            btnSendSay.Click += (s, e) => SendSayMessage();

            bottomChatBar.Controls.AddRange(new Control[] { lblSay, _txtSay, btnSendSay });

            tab.Controls.Add(_rtbChat);
            tab.Controls.Add(bottomChatBar);
        }

        private void SetupLogsTab(TabPage tab)
        {
            var topLogFilter = new Panel
            {
                Dock = DockStyle.Top,
                Height = 38,
                BackColor = Color.FromArgb(28, 28, 28),
                Padding = new Padding(10, 5, 10, 5)
            };

            var lblFilter = new Label { Text = "Filtrar:", AutoSize = true, Location = new Point(10, 9), ForeColor = Color.Gray };
            _txtLogFilter = new TextBox
            {
                Location = new Point(65, 6),
                Width = 280,
                Height = 26,
                BackColor = Color.FromArgb(42, 42, 42),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle
            };

            _chkAutoScroll = new CheckBox
            {
                Text = "Auto-Scroll",
                Checked = true,
                AutoSize = true,
                Location = new Point(365, 8),
                ForeColor = Color.LightGray
            };

            _chkOnlyErrors = new CheckBox
            {
                Text = "Apenas Avisos/Erros",
                AutoSize = true,
                Location = new Point(475, 8),
                ForeColor = Color.LightGray
            };

            var btnClear = new Button
            {
                Text = "Limpar Logs",
                Width = 95,
                Height = 26,
                Location = new Point(650, 5),
                BackColor = Color.FromArgb(50, 50, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnClear.FlatAppearance.BorderSize = 0;
            btnClear.Click += (s, e) => _rtbLogs.Clear();

            topLogFilter.Controls.AddRange(new Control[] { lblFilter, _txtLogFilter, _chkAutoScroll, _chkOnlyErrors, btnClear });

            _rtbLogs = new RichTextBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(16, 16, 16),
                ForeColor = Color.FromArgb(200, 200, 200),
                Font = new Font("Consolas", 9.5f, FontStyle.Regular),
                BorderStyle = BorderStyle.None,
                ReadOnly = true
            };

            tab.Controls.Add(_rtbLogs);
            tab.Controls.Add(topLogFilter);
        }

        private void SetupGraphTab(TabPage tab)
        {
            _picGraph = new PictureBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(18, 18, 18)
            };
            _picGraph.Paint += PicGraph_Paint;
            tab.Controls.Add(_picGraph);
        }

        // ========================================================================= MAPA DO MUNDO & RADAR
        private void SetupMapTab(TabPage tab)
        {
            // Barra superior de controle do mapa
            var mapToolbar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                BackColor = Color.FromArgb(28, 28, 28),
                Padding = new Padding(10, 6, 10, 6)
            };

            var lblDim = new Label
            {
                Text = "Dimensão:",
                AutoSize = true,
                Location = new Point(10, 12),
                ForeColor = Color.FromArgb(200, 200, 200),
                Font = new Font("Segoe UI", 9f, FontStyle.Bold)
            };

            _cmbDimension = new ComboBox
            {
                Location = new Point(85, 8),
                Width = 210,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(42, 42, 42),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f)
            };
            _cmbDimension.Items.AddRange(new object[] {
                "🌍 Overworld (Mundo Principal)",
                "🔥 Nether (Submundo)",
                "🌌 The End (O Fim)"
            });
            _cmbDimension.SelectedIndex = 0;
            _cmbDimension.SelectedIndexChanged += (s, e) => _picMap?.Invalidate();

            _chkShowChunkyCircle = new CheckBox
            {
                Text = "Raio Chunky (6.000)",
                Checked = true,
                AutoSize = true,
                Location = new Point(305, 11),
                ForeColor = Color.FromArgb(255, 201, 58),
                Font = new Font("Segoe UI", 9f)
            };
            _chkShowChunkyCircle.CheckedChanged += (s, e) => _picMap?.Invalidate();

            _chkShowTerrain = new CheckBox
            {
                Text = "🗺️ Terreno Real (Minimap)",
                Checked = true,
                AutoSize = true,
                Location = new Point(450, 11),
                ForeColor = Color.FromArgb(78, 201, 176),
                Font = new Font("Segoe UI", 9f, FontStyle.Bold)
            };
            _chkShowTerrain.CheckedChanged += (s, e) => _picMap?.Invalidate();

            var btnZoomIn = new Button
            {
                Text = "➕",
                Width = 32,
                Height = 28,
                Location = new Point(635, 7),
                BackColor = Color.FromArgb(50, 50, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnZoomIn.FlatAppearance.BorderSize = 0;
            btnZoomIn.Click += (s, e) => { _mapZoom = Math.Min(14.0f, _mapZoom * 1.4f); _picMap?.Invalidate(); };

            var btnZoomOut = new Button
            {
                Text = "➖",
                Width = 32,
                Height = 28,
                Location = new Point(672, 7),
                BackColor = Color.FromArgb(50, 50, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnZoomOut.FlatAppearance.BorderSize = 0;
            btnZoomOut.Click += (s, e) => { _mapZoom = Math.Max(0.003f, _mapZoom / 1.4f); _picMap?.Invalidate(); };

            var btnReset = new Button
            {
                Text = "↺ Resetar Centro",
                Width = 115,
                Height = 28,
                Location = new Point(710, 7),
                BackColor = Color.FromArgb(50, 50, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f),
                Cursor = Cursors.Hand
            };
            btnReset.FlatAppearance.BorderSize = 0;
            btnReset.Click += (s, e) => { _mapPanX = 0; _mapPanZ = 0; _mapZoom = 0.05f; _picMap?.Invalidate(); };

            var btnReloadRegions = new Button
            {
                Text = "🔄 Atualizar Mapa",
                Width = 135,
                Height = 28,
                Location = new Point(831, 7),
                BackColor = Color.FromArgb(50, 50, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f),
                Cursor = Cursors.Hand
            };
            btnReloadRegions.FlatAppearance.BorderSize = 0;
            btnReloadRegions.Click += (s, e) =>
            {
                LoadRegions(true);
                _tileRenderer.ClearCacheAndReload();
                RefreshActivePlayerRegions(forceReRender: true);
                _picMap?.Invalidate();
            };

            _lblMapCoords = new Label
            {
                Text = "Cursor: X: 0, Z: 0 | Zoom: 50%",
                AutoSize = true,
                Location = new Point(975, 12),
                ForeColor = Color.FromArgb(160, 160, 160),
                Font = new Font("Segoe UI", 9f)
            };

            mapToolbar.Controls.AddRange(new Control[] {
                lblDim, _cmbDimension, _chkShowChunkyCircle, _chkShowTerrain, btnZoomIn, btnZoomOut, btnReset, btnReloadRegions, _lblMapCoords
            });

            // Painel lateral direito de jogadores
            var pnlSidebar = new Panel
            {
                Dock = DockStyle.Right,
                Width = 270,
                BackColor = Color.FromArgb(24, 24, 24),
                Padding = new Padding(8)
            };

            var lblSidebarTitle = new Label
            {
                Dock = DockStyle.Top,
                Height = 28,
                Text = "👥 JOGADORES & RADAR",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(78, 201, 176),
                TextAlign = ContentAlignment.MiddleLeft
            };

            _pnlMapPlayers = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.TopDown,
                WrapContents = false,
                AutoScroll = true,
                BackColor = Color.Transparent
            };

            pnlSidebar.Controls.Add(_pnlMapPlayers);
            pnlSidebar.Controls.Add(lblSidebarTitle);

            // Canvas do Mapa
            _picMap = new PictureBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(14, 16, 18)
            };
            _picMap.Paint += PicMap_Paint;
            _picMap.MouseDown += PicMap_MouseDown;
            _picMap.MouseMove += PicMap_MouseMove;
            _picMap.MouseUp += PicMap_MouseUp;
            _picMap.MouseWheel += PicMap_MouseWheel;

            tab.Controls.Add(_picMap);
            tab.Controls.Add(pnlSidebar);
            tab.Controls.Add(mapToolbar);

            // Carrega regiões iniciais
            LoadRegions(true);
        }

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern bool DestroyIcon(IntPtr handle);

        public string GetServerIconPath()
        {
            string[] candidates = new[]
            {
                Path.Combine(_serverDir, "server-icon.png"),
                Path.Combine(_serverDir, "CanalhasSMP", "server-icon.png"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "CanalhasSMP", "CanalhasSMP", "server-icon.png"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "CanalhasSMP", "server-icon.png"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "server-icon.png"),
                @"C:\Users\Asminn\Documents\CanalhasSMP\CanalhasSMP\server-icon.png",
                @"C:\Users\Asminn\Documents\CanalhasSMP\server-icon.png"
            };

            foreach (var path in candidates)
            {
                try
                {
                    string full = Path.GetFullPath(path);
                    if (File.Exists(full)) return full;
                }
                catch { }
            }

            return Path.Combine(_serverDir, "server-icon.png");
        }

        private void LoadServerIcon()
        {
            try
            {
                string iconPath = GetServerIconPath();
                if (File.Exists(iconPath))
                {
                    using var bmp = new Bitmap(iconPath);
                    IntPtr hIcon = bmp.GetHicon();
                    try
                    {
                        using var tempIcon = Icon.FromHandle(hIcon);
                        this.Icon = (Icon)tempIcon.Clone();
                    }
                    finally
                    {
                        DestroyIcon(hIcon);
                    }
                }
            }
            catch { }
        }

        private string GetMuralFilePath()
        {
            string[] candidates = new[]
            {
                Path.Combine(_serverDir, "MURAL_NOVIDADES.json"),
                Path.Combine(_serverDir, "CanalhasSMP", "MURAL_NOVIDADES.json"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "CanalhasSMP", "CanalhasSMP", "MURAL_NOVIDADES.json"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "CanalhasSMP", "MURAL_NOVIDADES.json"),
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "MURAL_NOVIDADES.json"),
                @"C:\Users\Asminn\Documents\CanalhasSMP\CanalhasSMP\MURAL_NOVIDADES.json",
                @"C:\Users\Asminn\Documents\CanalhasSMP\MURAL_NOVIDADES.json"
            };

            foreach (var path in candidates)
            {
                try
                {
                    string full = Path.GetFullPath(path);
                    if (File.Exists(full)) return full;
                }
                catch { }
            }

            return Path.Combine(_serverDir, "MURAL_NOVIDADES.json");
        }

        private void SetupMuralTab(TabPage tab)
        {
            tab.BackColor = Color.FromArgb(18, 18, 18);

            // Barra Superior de Controle do Mural
            var muralToolbar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                BackColor = Color.FromArgb(28, 28, 28),
                Padding = new Padding(12, 6, 12, 6)
            };

            var lblMuralTitle = new Label
            {
                Text = "📜 MURAL DE NOVIDADES DO SERVIDOR",
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 201, 58),
                Location = new Point(12, 11),
                AutoSize = true
            };

            _lblMuralVersion = new Label
            {
                Text = "Versão: --",
                Font = new Font("Segoe UI", 9f, FontStyle.Italic),
                ForeColor = Color.FromArgb(160, 160, 160),
                Location = new Point(340, 13),
                AutoSize = true
            };

            var btnReloadMural = new Button
            {
                Text = "↺ Recarregar",
                Width = 110,
                Height = 30,
                Location = new Point(500, 7),
                BackColor = Color.FromArgb(45, 55, 65),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnReloadMural.FlatAppearance.BorderSize = 0;
            btnReloadMural.Click += (s, e) => LoadAndRenderMural();

            var btnBroadcastMural = new Button
            {
                Text = "📢 Transmitir In-Game",
                Width = 165,
                Height = 30,
                Location = new Point(620, 7),
                BackColor = Color.FromArgb(46, 125, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnBroadcastMural.FlatAppearance.BorderSize = 0;
            btnBroadcastMural.Click += (s, e) =>
            {
                _server.SendCommand("cummins mural todos");
                MessageBox.Show(this, "Comando enviado! O Mural de Novidades foi aberto na tela de todos os jogadores online.", "Mural Transmitido", MessageBoxButtons.OK, MessageBoxIcon.Information);
            };

            var btnEditMural = new Button
            {
                Text = "📂 Abrir Arquivo JSON",
                Width = 165,
                Height = 30,
                Location = new Point(795, 7),
                BackColor = Color.FromArgb(60, 60, 60),
                ForeColor = Color.FromArgb(240, 240, 240),
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f),
                Cursor = Cursors.Hand
            };
            btnEditMural.FlatAppearance.BorderSize = 0;
            btnEditMural.Click += (s, e) =>
            {
                string path = GetMuralFilePath();
                if (File.Exists(path))
                {
                    try
                    {
                        Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show(this, "Não foi possível abrir o arquivo: " + ex.Message, "Erro", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }
                }
                else
                {
                    MessageBox.Show(this, "Arquivo MURAL_NOVIDADES.json não encontrado.", "Aviso", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            };

            muralToolbar.Controls.AddRange(new Control[] { lblMuralTitle, _lblMuralVersion, btnReloadMural, btnBroadcastMural, btnEditMural });

            // Painel Principal com Scroll
            _pnlMuralContent = new Panel
            {
                Dock = DockStyle.Fill,
                AutoScroll = true,
                BackColor = Color.FromArgb(18, 18, 18),
                Padding = new Padding(20, 16, 20, 20)
            };

            tab.Controls.Add(_pnlMuralContent);
            tab.Controls.Add(muralToolbar);

            // Carrega inicialmente
            LoadAndRenderMural();
        }

        private void LoadAndRenderMural()
        {
            if (_pnlMuralContent == null) return;
            _pnlMuralContent.SuspendLayout();
            _pnlMuralContent.Controls.Clear();

            string path = GetMuralFilePath();
            MuralDataModel? mural = null;

            if (File.Exists(path))
            {
                try
                {
                    string json = File.ReadAllText(path, Encoding.UTF8);
                    mural = System.Text.Json.JsonSerializer.Deserialize<MuralDataModel>(json, new System.Text.Json.JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    });
                }
                catch (Exception ex)
                {
                    var lblErr = new Label
                    {
                        Text = "Erro ao carregar MURAL_NOVIDADES.json: " + ex.Message,
                        ForeColor = Color.FromArgb(241, 76, 76),
                        Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                        Dock = DockStyle.Top,
                        Height = 40
                    };
                    _pnlMuralContent.Controls.Add(lblErr);
                    _pnlMuralContent.ResumeLayout();
                    return;
                }
            }

            mural ??= new MuralDataModel
            {
                TituloServidor = "CanalhasSMP do Asmi",
                Subtitulo = "Mural de Boas-Vindas & Novidades",
                MensagemBoasVindas = "Arquivo MURAL_NOVIDADES.json não encontrado."
            };

            _lblMuralVersion.Text = $"Versão: {mural.VersaoMural}";

            int currentY = 16;
            int contentWidth = Math.Max(700, _pnlMuralContent.ClientSize.Width - 48);

            // 1. Card de Boas-Vindas (Cabeçalho estilizado de Pergaminho/Ouro)
            var pnlWelcome = new Panel
            {
                Location = new Point(16, currentY),
                Width = contentWidth,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                BackColor = Color.FromArgb(32, 28, 20),
                Padding = new Padding(20, 16, 20, 18)
            };
            pnlWelcome.Paint += (s, e) =>
            {
                using var p = new Pen(Color.FromArgb(255, 201, 58), 2f);
                e.Graphics.DrawRectangle(p, 0, 0, pnlWelcome.Width - 1, pnlWelcome.Height - 1);
            };

            var lblServidor = new Label
            {
                Text = "🏰 " + mural.TituloServidor.ToUpperInvariant(),
                Font = new Font("Segoe UI", 16f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 215, 64),
                Dock = DockStyle.Top,
                Height = 34
            };

            var lblSub = new Label
            {
                Text = mural.Subtitulo,
                Font = new Font("Segoe UI", 11f, FontStyle.Italic),
                ForeColor = Color.FromArgb(255, 236, 179),
                Dock = DockStyle.Top,
                Height = 26
            };

            var lblMsg = new Label
            {
                Text = mural.MensagemBoasVindas,
                Font = new Font("Segoe UI", 10f),
                ForeColor = Color.FromArgb(220, 220, 220),
                Dock = DockStyle.Top,
                AutoSize = true,
                Padding = new Padding(0, 8, 0, 4)
            };

            string iconPath = GetServerIconPath();
            if (File.Exists(iconPath))
            {
                try
                {
                    var picMuralIcon = new PictureBox
                    {
                        Size = new Size(54, 54),
                        Location = new Point(pnlWelcome.Width - 74, 14),
                        Anchor = AnchorStyles.Top | AnchorStyles.Right,
                        SizeMode = PictureBoxSizeMode.Zoom,
                        Image = Image.FromFile(iconPath),
                        BackColor = Color.Transparent
                    };
                    pnlWelcome.Controls.Add(picMuralIcon);
                }
                catch { }
            }

            pnlWelcome.Controls.Add(lblMsg);
            pnlWelcome.Controls.Add(lblSub);
            pnlWelcome.Controls.Add(lblServidor);
            pnlWelcome.Height = Math.Max(76, lblServidor.Height + lblSub.Height + lblMsg.PreferredHeight + 38);

            _pnlMuralContent.Controls.Add(pnlWelcome);
            currentY += pnlWelcome.Height + 20;

            // 2. Seção: Novidades & Implementações Recentes
            if (mural.Novidades != null && mural.Novidades.Count > 0)
            {
                CreateMuralSection("✨ NOVIDADES & ATUALIZAÇÕES RECENTES", mural.Novidades,
                    Color.FromArgb(78, 201, 176), Color.FromArgb(24, 34, 30), ref currentY, _pnlMuralContent, contentWidth);
            }

            // 3. Seção: Correções de Bugs & Melhorias Técnicas
            if (mural.Correcoes != null && mural.Correcoes.Count > 0)
            {
                CreateMuralSection("🛠️ CORREÇÕES & ESTABILIDADE DO MODPACK", mural.Correcoes,
                    Color.FromArgb(79, 193, 255), Color.FromArgb(22, 28, 36), ref currentY, _pnlMuralContent, contentWidth);
            }

            // 4. Seção: Comandos & Dicas Úteis
            if (mural.ComandosUteis != null && mural.ComandosUteis.Count > 0)
            {
                CreateMuralSection("💡 COMANDOS & DICAS ÚTEIS PARA OS JOGADORES", mural.ComandosUteis,
                    Color.FromArgb(206, 145, 120), Color.FromArgb(32, 26, 24), ref currentY, _pnlMuralContent, contentWidth);
            }

            _pnlMuralContent.ResumeLayout();
        }

        private void CreateMuralSection(string sectionTitle, List<string> items, Color accentColor, Color cardBg, ref int currentY, Panel parent, int width)
        {
            var pnlSection = new Panel
            {
                Location = new Point(16, currentY),
                Width = width,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                BackColor = Color.FromArgb(22, 22, 22),
                Padding = new Padding(16, 12, 16, 16)
            };

            var lblTitle = new Label
            {
                Text = $"{sectionTitle} ({items.Count})",
                Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                ForeColor = accentColor,
                Dock = DockStyle.Top,
                Height = 30
            };
            pnlSection.Controls.Add(lblTitle);

            int itemY = 38;
            for (int i = 0; i < items.Count; i++)
            {
                string text = items[i];
                var itemCard = new Panel
                {
                    Location = new Point(12, itemY),
                    Width = pnlSection.Width - 24,
                    Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                    BackColor = cardBg,
                    Padding = new Padding(14, 10, 14, 10)
                };

                // Borda esquerda destacada com a cor da seção
                itemCard.Paint += (s, e) =>
                {
                    using var b = new SolidBrush(accentColor);
                    e.Graphics.FillRectangle(b, 0, 0, 4, itemCard.Height);
                };

                string titlePart = "";
                string descPart = text;
                int colonIdx = text.IndexOf(':');
                if (colonIdx > 0 && colonIdx < 50)
                {
                    titlePart = text.Substring(0, colonIdx).Trim();
                    descPart = text.Substring(colonIdx + 1).Trim();
                }

                if (!string.IsNullOrEmpty(titlePart))
                {
                    var lblItemTitle = new Label
                    {
                        Text = titlePart,
                        Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                        ForeColor = Color.White,
                        Dock = DockStyle.Top,
                        AutoSize = true
                    };

                    var lblItemDesc = new Label
                    {
                        Text = descPart,
                        Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                        ForeColor = Color.FromArgb(210, 210, 210),
                        Dock = DockStyle.Top,
                        AutoSize = true,
                        Padding = new Padding(0, 4, 0, 0)
                    };

                    itemCard.Controls.Add(lblItemDesc);
                    itemCard.Controls.Add(lblItemTitle);
                    itemCard.Height = lblItemTitle.PreferredHeight + lblItemDesc.PreferredHeight + 24;
                }
                else
                {
                    var lblItemFull = new Label
                    {
                        Text = text,
                        Font = new Font("Segoe UI", 9.5f),
                        ForeColor = Color.FromArgb(220, 220, 220),
                        Dock = DockStyle.Top,
                        AutoSize = true
                    };
                    itemCard.Controls.Add(lblItemFull);
                    itemCard.Height = lblItemFull.PreferredHeight + 20;
                }

                pnlSection.Controls.Add(itemCard);
                itemY += itemCard.Height + 8;
            }

            pnlSection.Height = itemY + 10;
            parent.Controls.Add(pnlSection);
            currentY += pnlSection.Height + 18;
        }

        private void PicMap_Paint(object? sender, PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            int w = _picMap.Width;
            int h = _picMap.Height;
            if (w <= 0 || h <= 0) return;

            g.Clear(Color.FromArgb(14, 16, 18));

            float cx = w / 2f;
            float cy = h / 2f;

            int dimIndex = _cmbDimension.SelectedIndex;
            string selectedDimStr = dimIndex switch
            {
                1 => "minecraft:the_nether",
                2 => "minecraft:the_end",
                _ => "minecraft:overworld"
            };

            HashSet<(int rx, int rz)> currentRegions = dimIndex switch
            {
                1 => _netherRegions,
                2 => _endRegions,
                _ => _overworldRegions
            };

            // Grade Cartográfica de Fundo
            int gridStep = _mapZoom < 0.015f ? 2000 : (_mapZoom < 0.08f ? 1000 : (_mapZoom < 0.3f ? 500 : (_mapZoom < 1.5f ? 100 : 16)));
            double minWx = _mapPanX - (cx / _mapZoom);
            double maxWx = _mapPanX + (cx / _mapZoom);
            double minWz = _mapPanZ - (cy / _mapZoom);
            double maxWz = _mapPanZ + (cy / _mapZoom);

            int startGx = (int)(Math.Floor(minWx / gridStep) * gridStep);
            int startGz = (int)(Math.Floor(minWz / gridStep) * gridStep);

            // Regiões Geradas (512x512 blocos por arquivo .mca) + Terreno Real (Minimap)
            Color regFillColor = dimIndex switch
            {
                1 => Color.FromArgb(50, 70, 25, 25),
                2 => Color.FromArgb(50, 45, 30, 65),
                _ => Color.FromArgb(50, 32, 60, 42)
            };
            Color regBorderColor = dimIndex switch
            {
                1 => Color.FromArgb(65, 110, 45, 45),
                2 => Color.FromArgb(65, 75, 55, 105),
                _ => Color.FromArgb(55, 50, 95, 65)
            };

            using var regBrush = new SolidBrush(regFillColor);
            using var regPen = new Pen(regBorderColor, 1);

            float regScreenSize = 512f * _mapZoom;
            bool drawBorders = regScreenSize > 16f && (!_chkShowTerrain.Checked || _mapZoom < 0.4f);
            bool showTerrain = _chkShowTerrain != null && _chkShowTerrain.Checked;

            var oldInterp = g.InterpolationMode;
            var oldPixelOffset = g.PixelOffsetMode;
            if (showTerrain)
            {
                g.InterpolationMode = _mapZoom >= 0.85f ? InterpolationMode.NearestNeighbor : InterpolationMode.Bilinear;
                g.PixelOffsetMode = _mapZoom >= 0.85f ? PixelOffsetMode.Half : PixelOffsetMode.Default;
            }

            var telem = _sampler.LatestTelemetry;

            // Prioriza pré-carregar as regiões ao redor dos jogadores ativos primeiro
            if (showTerrain && telem?.PlayerDetails != null)
            {
                foreach (var p in telem.PlayerDetails)
                {
                    if (!DimMatches(p.Dimension, selectedDimStr)) continue;
                    int prx = (int)Math.Floor(p.X / 512.0);
                    int prz = (int)Math.Floor(p.Z / 512.0);
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        for (int dx = -1; dx <= 1; dx++)
                        {
                            if (currentRegions.Contains((prx + dx, prz + dz)))
                            {
                                _tileRenderer.RequestTile(dimIndex, prx + dx, prz + dz, (dx * dx + dz * dz) * 10.0);
                            }
                        }
                    }
                }
            }

            foreach (var (rx, rz) in currentRegions)
            {
                double rMinX = rx * 512.0;
                double rMinZ = rz * 512.0;

                if (rMinX + 512 < minWx || rMinX > maxWx || rMinZ + 512 < minWz || rMinZ > maxWz)
                    continue;

                float rxScreen = cx + (float)((rMinX - _mapPanX) * _mapZoom);
                float rzScreen = cy + (float)((rMinZ - _mapPanZ) * _mapZoom);

                if (showTerrain && _tileRenderer.TryGetTile(dimIndex, rx, rz, out Bitmap? tileBmp) && tileBmp != null)
                {
                    g.DrawImage(tileBmp, rxScreen, rzScreen, regScreenSize + 0.5f, regScreenSize + 0.5f);
                }
                else
                {
                    g.FillRectangle(regBrush, rxScreen, rzScreen, regScreenSize, regScreenSize);
                    if (showTerrain)
                    {
                        double rCenterX = rMinX + 256.0;
                        double rCenterZ = rMinZ + 256.0;
                        double distSq = (rCenterX - _mapPanX) * (rCenterX - _mapPanX) + (rCenterZ - _mapPanZ) * (rCenterZ - _mapPanZ);
                        _tileRenderer.RequestTile(dimIndex, rx, rz, distSq);
                    }
                }

                if (drawBorders)
                {
                    g.DrawRectangle(regPen, rxScreen, rzScreen, regScreenSize, regScreenSize);
                }
            }

            g.InterpolationMode = oldInterp;
            g.PixelOffsetMode = oldPixelOffset;

            // Linhas de Grade Cartográfica e Coordenadas nas Bordas
            using var gridPen = new Pen(Color.FromArgb(showTerrain ? 38 : 25, 180, 195, 210), 1);
            using var fontGrid = new Font("Segoe UI", 7.5f);
            using var brushGridText = new SolidBrush(Color.FromArgb(130, 140, 150));

            for (double gx = startGx; gx <= maxWx; gx += gridStep)
            {
                float sx = cx + (float)((gx - _mapPanX) * _mapZoom);
                g.DrawLine(gridPen, sx, 0, sx, h);
                if (h > 40)
                {
                    g.DrawString($"{gx:F0}", fontGrid, brushGridText, sx + 2, h - 20);
                }
            }

            for (double gz = startGz; gz <= maxWz; gz += gridStep)
            {
                float sy = cy + (float)((gz - _mapPanZ) * _mapZoom);
                g.DrawLine(gridPen, 0, sy, w, sy);
                if (w > 60)
                {
                    g.DrawString($"{gz:F0}", fontGrid, brushGridText, 5, sy + 2);
                }
            }

            // Eixos Centrais (0, 0)
            float axisX = cx + (float)((0 - _mapPanX) * _mapZoom);
            float axisZ = cy + (float)((0 - _mapPanZ) * _mapZoom);

            using (var axisPen = new Pen(Color.FromArgb(75, 100, 130, 160), 1))
            {
                if (axisX >= 0 && axisX <= w) g.DrawLine(axisPen, axisX, 0, axisX, h);
                if (axisZ >= 0 && axisZ <= h) g.DrawLine(axisPen, 0, axisZ, w, axisZ);
            }

            // Limite Chunky (Círculo de Pré-geração de Raio 6000 no Overworld)
            if (_chkShowChunkyCircle.Checked && dimIndex == 0)
            {
                float chunkyRadius = 6000f * _mapZoom;
                using var chunkyPen = new Pen(Color.FromArgb(255, 201, 58), 1.5f) { DashStyle = DashStyle.Dash };
                g.DrawEllipse(chunkyPen, axisX - chunkyRadius, axisZ - chunkyRadius, chunkyRadius * 2, chunkyRadius * 2);

                using var chunkyFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
                using var chunkyBrush = new SolidBrush(Color.FromArgb(255, 201, 58));
                g.DrawString("⚡ Chunky Pregen Boundary (R: 6000)", chunkyFont, chunkyBrush, axisX - 110, axisZ - chunkyRadius - 18);
            }

            // Marcador de Origem (0, 0)
            using (var originBrush = new SolidBrush(Color.FromArgb(140, 180, 220)))
            {
                g.FillEllipse(originBrush, axisX - 3, axisZ - 3, 6, 6);
            }

            // Desenha Senhor Cummins (se estiver nesta dimensão)
            if (telem?.Cummins != null && DimMatches(telem.Cummins.Dimension, selectedDimStr))
            {
                float catX = cx + (float)((telem.Cummins.X - _mapPanX) * _mapZoom);
                float catZ = cy + (float)((telem.Cummins.Z - _mapPanZ) * _mapZoom);

                using var haloBrush = new SolidBrush(Color.FromArgb(70, 255, 201, 58));
                g.FillEllipse(haloBrush, catX - 12, catZ - 12, 24, 24);

                using var catBrush = new SolidBrush(Color.FromArgb(255, 201, 58));
                g.FillEllipse(catBrush, catX - 6, catZ - 6, 12, 12);
                using var catPen = new Pen(Color.Black, 1.5f);
                g.DrawEllipse(catPen, catX - 6, catZ - 6, 12, 12);

                string catTag = $"🐱 Senhor Cummins ({telem.Cummins.X:F0}, {telem.Cummins.Z:F0})";
                using var tagFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
                var tagSize = g.MeasureString(catTag, tagFont);
                using var tagBg = new SolidBrush(Color.FromArgb(210, 30, 25, 10));
                g.FillRectangle(tagBg, catX - tagSize.Width / 2f - 4, catZ - 26, tagSize.Width + 8, 18);
                using var tagBorder = new Pen(Color.FromArgb(255, 201, 58), 1);
                g.DrawRectangle(tagBorder, catX - tagSize.Width / 2f - 4, catZ - 26, tagSize.Width + 8, 18);
                g.DrawString(catTag, tagFont, catBrush, catX - tagSize.Width / 2f, catZ - 25);
            }

            // Desenha Vitória (Cachorrinha Fox Paulistinha do ArzanLion)
            if (telem?.Vitoria != null && DimMatches(telem.Vitoria.Dimension, selectedDimStr))
            {
                float dogX = cx + (float)((telem.Vitoria.X - _mapPanX) * _mapZoom);
                float dogZ = cy + (float)((telem.Vitoria.Z - _mapPanZ) * _mapZoom);

                using var dogHalo = new SolidBrush(Color.FromArgb(75, 236, 112, 214));
                using var dogBrush = new SolidBrush(Color.FromArgb(236, 112, 214));
                using var dogBorder = new Pen(Color.White, 1.5f);
                g.FillEllipse(dogHalo, dogX - 11, dogZ - 11, 22, 22);
                g.FillEllipse(dogBrush, dogX - 5, dogZ - 5, 10, 10);
                g.DrawEllipse(dogBorder, dogX - 5, dogZ - 5, 10, 10);

                using var tagFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
                using var tagBg = new SolidBrush(Color.FromArgb(200, 35, 18, 35));
                string dogTag = "🐶 Vitória";
                var tagSize = g.MeasureString(dogTag, tagFont);
                g.FillRectangle(tagBg, dogX - tagSize.Width / 2f - 4, dogZ - 26, tagSize.Width + 8, 18);
                using var tagBorder = new Pen(Color.FromArgb(236, 112, 214), 1);
                g.DrawRectangle(tagBorder, dogX - tagSize.Width / 2f - 4, dogZ - 26, tagSize.Width + 8, 18);
                g.DrawString(dogTag, tagFont, dogBrush, dogX - tagSize.Width / 2f, dogZ - 25);
            }

            // Desenha Jogadores Ativos
            if (telem?.PlayerDetails != null)
            {
                using var playerHalo = new SolidBrush(Color.FromArgb(80, 78, 201, 176));
                using var playerBrush = new SolidBrush(Color.FromArgb(78, 201, 176));
                using var playerBorder = new Pen(Color.White, 1.5f);
                using var arrowPen = new Pen(Color.FromArgb(255, 255, 255), 2.2f);
                arrowPen.EndCap = LineCap.ArrowAnchor;
                using var fovBrush = new SolidBrush(Color.FromArgb(55, 255, 255, 255));
                using var nameFont = new Font("Segoe UI", 9f, FontStyle.Bold);
                using var coordFont = new Font("Segoe UI", 8f);
                using var whiteBrush = new SolidBrush(Color.White);
                using var grayBrush = new SolidBrush(Color.FromArgb(190, 190, 190));
                using var hpGreen = new SolidBrush(Color.FromArgb(76, 175, 80));
                using var hpBg = new SolidBrush(Color.FromArgb(60, 60, 60));

                foreach (var p in telem.PlayerDetails)
                {
                    if (!DimMatches(p.Dimension, selectedDimStr)) continue;

                    float px = cx + (float)((p.X - _mapPanX) * _mapZoom);
                    float pz = cy + (float)((p.Z - _mapPanZ) * _mapZoom);

                    // No Minecraft: Yaw 180 = Norte (-Z), Yaw 0 = Sul (+Z), Yaw 90 = Oeste (-X), Yaw -90 = Leste (+X).
                    // Portanto o vetor frontal (frente/olhar do jogador) é: dX = -Sin(yaw), dZ = +Cos(yaw).
                    double yawRad = p.Yaw * Math.PI / 180.0;
                    float lookDx = -(float)Math.Sin(yawRad);
                    float lookDz = (float)Math.Cos(yawRad);

                    // Cone suave de visão + Linha branca apontando exatamente para onde o jogador olha
                    double leftRad = (p.Yaw - 28.0) * Math.PI / 180.0;
                    double rightRad = (p.Yaw + 28.0) * Math.PI / 180.0;
                    PointF[] fovCone = new PointF[]
                    {
                        new PointF(px, pz),
                        new PointF(px - (float)Math.Sin(leftRad) * 24f, pz + (float)Math.Cos(leftRad) * 24f),
                        new PointF(px - (float)Math.Sin(rightRad) * 24f, pz + (float)Math.Cos(rightRad) * 24f)
                    };
                    g.FillPolygon(fovCone.Length == 3 ? fovBrush : playerHalo, fovCone);

                    g.FillEllipse(playerHalo, px - 14, pz - 14, 28, 28);
                    g.FillEllipse(playerBrush, px - 7, pz - 7, 14, 14);
                    g.DrawEllipse(playerBorder, px - 7, pz - 7, 14, 14);

                    float dirX = px + lookDx * 20.0f;
                    float dirZ = pz + lookDz * 20.0f;
                    g.DrawLine(arrowPen, px, pz, dirX, dirZ);

                    string nameText = p.Name;
                    string coordText = $"X: {p.X:F0}  Y: {p.Y:F0}  Z: {p.Z:F0}";
                    var nameSz = g.MeasureString(nameText, nameFont);
                    var coordSz = g.MeasureString(coordText, coordFont);
                    float boxW = Math.Max(nameSz.Width, coordSz.Width) + 16;
                    float boxH = 38;
                    float boxX = px - boxW / 2f;
                    float boxY = pz - boxH - 12;

                    using var boxBrush = new SolidBrush(Color.FromArgb(215, 20, 24, 28));
                    using var boxBorder = new Pen(Color.FromArgb(78, 201, 176), 1);
                    g.FillRectangle(boxBrush, boxX, boxY, boxW, boxH);
                    g.DrawRectangle(boxBorder, boxX, boxY, boxW, boxH);

                    g.DrawString(nameText, nameFont, whiteBrush, boxX + 6, boxY + 2);
                    g.DrawString(coordText, coordFont, grayBrush, boxX + 6, boxY + 16);

                    float hpPercent = Math.Clamp((float)(p.Health / Math.Max(1.0, p.MaxHealth)), 0f, 1f);
                    g.FillRectangle(hpBg, boxX + 6, boxY + 31, boxW - 12, 3);
                    g.FillRectangle(hpGreen, boxX + 6, boxY + 31, (boxW - 12) * hpPercent, 3);
                }
            }

            // HUD / Mini Rosa dos Ventos + Status de Renderização do Minimap
            using (var compassBg = new SolidBrush(Color.FromArgb(195, 20, 20, 20)))
            {
                g.FillRectangle(compassBg, w - 85, 12, 75, 42);
                using var compassPen = new Pen(Color.FromArgb(60, 60, 60));
                g.DrawRectangle(compassPen, w - 85, 12, 75, 42);
                using var compassFont = new Font("Segoe UI", 9f, FontStyle.Bold);
                using var northBrush = new SolidBrush(Color.FromArgb(241, 76, 76));
                g.DrawString("▲ NORTE", compassFont, northBrush, w - 80, 16);
                using var subFont = new Font("Segoe UI", 7.5f);
                g.DrawString("(-Z Cima)", subFont, brushGridText, w - 75, 33);
            }

            int pendingTiles = _tileRenderer.PendingCount;
            if (pendingTiles > 0 && showTerrain)
            {
                string loadingText = $"🗺️ Renderizando terreno: {pendingTiles} regiões...";
                using var loadFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
                var sz = g.MeasureString(loadingText, loadFont);
                using var loadBg = new SolidBrush(Color.FromArgb(210, 20, 28, 34));
                using var loadBorder = new Pen(Color.FromArgb(78, 201, 176), 1);
                using var loadBrush = new SolidBrush(Color.FromArgb(78, 201, 176));
                g.FillRectangle(loadBg, 14, 12, sz.Width + 16, 26);
                g.DrawRectangle(loadBorder, 14, 12, sz.Width + 16, 26);
                g.DrawString(loadingText, loadFont, loadBrush, 22, 16);
            }

            DrawScaleBar(g, w, h);
        }

        private static bool DimMatches(string entityDim, string currentDim)
        {
            if (string.IsNullOrEmpty(entityDim)) return currentDim.Contains("overworld");
            if (entityDim.Contains("nether") && currentDim.Contains("nether")) return true;
            if (entityDim.Contains("end") && currentDim.Contains("end")) return true;
            if (entityDim.Contains("overworld") && currentDim.Contains("overworld")) return true;
            return entityDim.Equals(currentDim, StringComparison.OrdinalIgnoreCase);
        }

        private void DrawScaleBar(Graphics g, int w, int h)
        {
            double targetPx = 100.0;
            double targetBlocks = targetPx / _mapZoom;
            int niceBlocks = 100;
            if (targetBlocks >= 5000) niceBlocks = 5000;
            else if (targetBlocks >= 2000) niceBlocks = 2000;
            else if (targetBlocks >= 1000) niceBlocks = 1000;
            else if (targetBlocks >= 500) niceBlocks = 500;
            else if (targetBlocks >= 200) niceBlocks = 200;
            else if (targetBlocks >= 100) niceBlocks = 100;
            else if (targetBlocks >= 50) niceBlocks = 50;
            else if (targetBlocks >= 20) niceBlocks = 20;
            else if (targetBlocks >= 10) niceBlocks = 10;
            else niceBlocks = 5;

            float barPx = (float)(niceBlocks * _mapZoom);
            float startX = 20;
            float startY = h - 25;

            using var barPen = new Pen(Color.White, 2);
            g.DrawLine(barPen, startX, startY, startX + barPx, startY);
            g.DrawLine(barPen, startX, startY - 4, startX, startY + 4);
            g.DrawLine(barPen, startX + barPx, startY - 4, startX + barPx, startY + 4);

            using var font = new Font("Segoe UI", 8f, FontStyle.Bold);
            using var brush = new SolidBrush(Color.White);
            g.DrawString($"{niceBlocks} blocos", font, brush, startX + 5, startY - 18);
        }

        private void PicMap_MouseDown(object? sender, MouseEventArgs e)
        {
            if (e.Button == MouseButtons.Left || e.Button == MouseButtons.Middle)
            {
                _isPanning = true;
                _lastMousePos = e.Location;
                _picMap.Cursor = Cursors.SizeAll;
            }
        }

        private void PicMap_MouseMove(object? sender, MouseEventArgs e)
        {
            int cx = _picMap.Width / 2;
            int cy = _picMap.Height / 2;

            double wx = _mapPanX + (e.X - cx) / _mapZoom;
            double wz = _mapPanZ + (e.Y - cy) / _mapZoom;
            _lblMapCoords.Text = $"Cursor: X: {wx:F0}, Z: {wz:F0} | Zoom: {(_mapZoom * 1000):F0}%";

            if (_isPanning)
            {
                int dx = e.X - _lastMousePos.X;
                int dy = e.Y - _lastMousePos.Y;
                _mapPanX -= dx / _mapZoom;
                _mapPanZ -= dy / _mapZoom;
                _lastMousePos = e.Location;
                _picMap.Invalidate();
            }
        }

        private void PicMap_MouseUp(object? sender, MouseEventArgs e)
        {
            _isPanning = false;
            _picMap.Cursor = Cursors.Default;
        }

        private void PicMap_MouseWheel(object? sender, MouseEventArgs e)
        {
            int cx = _picMap.Width / 2;
            int cy = _picMap.Height / 2;

            double oldWx = _mapPanX + (e.X - cx) / _mapZoom;
            double oldWz = _mapPanZ + (e.Y - cy) / _mapZoom;

            float factor = e.Delta > 0 ? 1.25f : 0.8f;
            _mapZoom = Math.Clamp(_mapZoom * factor, 0.003f, 14.0f);

            _mapPanX = oldWx - (e.X - cx) / _mapZoom;
            _mapPanZ = oldWz - (e.Y - cy) / _mapZoom;

            _picMap.Invalidate();
        }

        private void RefreshActivePlayerRegions(bool forceReRender = false)
        {
            var telem = _sampler.LatestTelemetry;
            if (telem?.PlayerDetails == null || _tileRenderer == null) return;

            foreach (var p in telem.PlayerDetails)
            {
                int dimIdx = p.Dimension.Contains("nether") ? 1 : (p.Dimension.Contains("end") ? 2 : 0);
                int rx = (int)Math.Floor(p.X / 512.0);
                int rz = (int)Math.Floor(p.Z / 512.0);
                if (forceReRender)
                {
                    _tileRenderer.InvalidateRegion(dimIdx, rx, rz, 0);
                }
                else
                {
                    _tileRenderer.RequestTile(dimIdx, rx, rz, 0);
                }
            }
        }

        private void LoadRegions(bool force = false)
        {
            if (!force && (DateTime.Now - _lastRegionScan).TotalSeconds < 30) return;
            _lastRegionScan = DateTime.Now;

            try
            {
                string worldDir = Path.Combine(_serverDir, "world");
                _overworldRegions = ScanRegionDir(Path.Combine(worldDir, "region"));
                _netherRegions = ScanRegionDir(Path.Combine(worldDir, "DIM-1", "region"));
                _endRegions = ScanRegionDir(Path.Combine(worldDir, "DIM1", "region"));
            }
            catch { }
        }

        private static HashSet<(int rx, int rz)> ScanRegionDir(string dir)
        {
            var set = new HashSet<(int rx, int rz)>();
            if (!Directory.Exists(dir)) return set;

            try
            {
                foreach (var file in Directory.EnumerateFiles(dir, "r.*.*.mca"))
                {
                    var name = Path.GetFileName(file);
                    var parts = name.Split('.');
                    if (parts.Length >= 4 && int.TryParse(parts[1], out int rx) && int.TryParse(parts[2], out int rz))
                    {
                        set.Add((rx, rz));
                    }
                }
            }
            catch { }
            return set;
        }

        private void UpdateMapPlayersSidebar(TelemetryModel? t)
        {
            if (_pnlMapPlayers == null || _pnlMapPlayers.IsDisposed) return;

            var players = t?.PlayerDetails ?? new List<PlayerData>();
            var cummins = t?.Cummins;
            var vitoria = t?.Vitoria;

            if (vitoria != null && vitoria.AiEnabled != _vitoriaAiEnabled)
            {
                _vitoriaAiEnabled = vitoria.AiEnabled;
                UpdateVitoriaAiButtonVisual();
            }

            var sb = new StringBuilder();
            sb.Append(players.Count).Append('|');
            foreach (var p in players)
            {
                sb.Append($"{p.Name}:{p.Dimension}:{p.X:F0}:{p.Z:F0}:{p.Health:F0}|");
            }
            if (cummins != null)
            {
                sb.Append($"cummins:{cummins.Dimension}:{cummins.X:F0}:{cummins.Z:F0}|");
            }
            if (vitoria != null)
            {
                sb.Append($"vitoria:{vitoria.Dimension}:{vitoria.X:F0}:{vitoria.Z:F0}:{vitoria.AiEnabled}");
            }
            string sig = sb.ToString();
            if (sig == _lastPlayersSidebarSignature) return;
            _lastPlayersSidebarSignature = sig;

            _pnlMapPlayers.SuspendLayout();
            _pnlMapPlayers.Controls.Clear();

            if (cummins != null)
            {
                var cardC = new Panel
                {
                    Width = 245,
                    Height = 85,
                    BackColor = Color.FromArgb(36, 32, 20),
                    Margin = new Padding(0, 0, 0, 8),
                    Padding = new Padding(8)
                };

                var lblCName = new Label
                {
                    Text = "🐱 Senhor Cummins",
                    Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                    ForeColor = Color.FromArgb(255, 201, 58),
                    AutoSize = true,
                    Location = new Point(8, 6)
                };

                string cleanDim = cummins.Dimension.Replace("minecraft:", "");
                var lblCCoords = new Label
                {
                    Text = $"XYZ: {cummins.X:F0}, {cummins.Y:F0}, {cummins.Z:F0}\nDim: {cleanDim}",
                    Font = new Font("Consolas", 8.5f),
                    ForeColor = Color.FromArgb(200, 200, 180),
                    AutoSize = true,
                    Location = new Point(8, 25)
                };

                var btnCFocus = new Button
                {
                    Text = "🎯 Focar",
                    Width = 70,
                    Height = 24,
                    Location = new Point(95, 54),
                    BackColor = Color.FromArgb(60, 50, 25),
                    ForeColor = Color.White,
                    FlatStyle = FlatStyle.Flat,
                    Font = new Font("Segoe UI", 8f, FontStyle.Bold),
                    Cursor = Cursors.Hand
                };
                btnCFocus.FlatAppearance.BorderSize = 0;
                btnCFocus.Click += (s, e) =>
                {
                    _mapPanX = cummins.X;
                    _mapPanZ = cummins.Z;
                    _mapZoom = 1.25f;
                    if (cummins.Dimension.Contains("nether")) _cmbDimension.SelectedIndex = 1;
                    else if (cummins.Dimension.Contains("end")) _cmbDimension.SelectedIndex = 2;
                    else _cmbDimension.SelectedIndex = 0;
                    _picMap.Invalidate();
                };

                var btnCTp = new Button
                {
                    Text = "🐾 Gato TP",
                    Width = 75,
                    Height = 24,
                    Location = new Point(170, 54),
                    BackColor = Color.FromArgb(60, 50, 25),
                    ForeColor = Color.White,
                    FlatStyle = FlatStyle.Flat,
                    Font = new Font("Segoe UI", 8f),
                    Cursor = Cursors.Hand
                };
                btnCTp.FlatAppearance.BorderSize = 0;
                btnCTp.Click += (s, e) => _server.SendCommand("cummins gato tp");

                cardC.Controls.AddRange(new Control[] { lblCName, lblCCoords, btnCFocus, btnCTp });
                _pnlMapPlayers.Controls.Add(cardC);
            }

            if (vitoria != null)
            {
                var cardV = new Panel
                {
                    Width = 245,
                    Height = 85,
                    BackColor = Color.FromArgb(38, 24, 38),
                    Margin = new Padding(0, 0, 0, 8),
                    Padding = new Padding(8)
                };

                var lblVName = new Label
                {
                    Text = "🐶 Vitória (Fox Paulistinha)",
                    Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                    ForeColor = Color.FromArgb(236, 112, 214),
                    AutoSize = true,
                    Location = new Point(8, 6)
                };

                string cleanDim = vitoria.Dimension.Replace("minecraft:", "");
                var lblVCoords = new Label
                {
                    Text = $"XYZ: {vitoria.X:F0}, {vitoria.Y:F0}, {vitoria.Z:F0}\nDim: {cleanDim} | IA: {(vitoria.AiEnabled ? "ON" : "OFF")}",
                    Font = new Font("Consolas", 8.5f),
                    ForeColor = Color.FromArgb(215, 190, 215),
                    AutoSize = true,
                    Location = new Point(8, 25)
                };

                var btnVFocus = new Button
                {
                    Text = "🎯 Focar",
                    Width = 70,
                    Height = 24,
                    Location = new Point(95, 54),
                    BackColor = Color.FromArgb(65, 35, 65),
                    ForeColor = Color.White,
                    FlatStyle = FlatStyle.Flat,
                    Font = new Font("Segoe UI", 8f, FontStyle.Bold),
                    Cursor = Cursors.Hand
                };
                btnVFocus.FlatAppearance.BorderSize = 0;
                btnVFocus.Click += (s, e) =>
                {
                    _mapPanX = vitoria.X;
                    _mapPanZ = vitoria.Z;
                    _mapZoom = 1.25f;
                    if (vitoria.Dimension.Contains("nether")) _cmbDimension.SelectedIndex = 1;
                    else if (vitoria.Dimension.Contains("end")) _cmbDimension.SelectedIndex = 2;
                    else _cmbDimension.SelectedIndex = 0;
                    _picMap.Invalidate();
                };

                var btnVTp = new Button
                {
                    Text = "🐶 Chamar",
                    Width = 75,
                    Height = 24,
                    Location = new Point(170, 54),
                    BackColor = Color.FromArgb(65, 35, 65),
                    ForeColor = Color.White,
                    FlatStyle = FlatStyle.Flat,
                    Font = new Font("Segoe UI", 8f),
                    Cursor = Cursors.Hand
                };
                btnVTp.FlatAppearance.BorderSize = 0;
                btnVTp.Click += (s, e) => _server.SendCommand("cummins vitoria");

                cardV.Controls.AddRange(new Control[] { lblVName, lblVCoords, btnVFocus, btnVTp });
                _pnlMapPlayers.Controls.Add(cardV);
            }

            if (players.Count == 0)
            {
                var lblEmpty = new Label
                {
                    Text = "Nenhum jogador online.",
                    Font = new Font("Segoe UI", 9f, FontStyle.Italic),
                    ForeColor = Color.FromArgb(140, 140, 140),
                    AutoSize = true,
                    Padding = new Padding(6)
                };
                _pnlMapPlayers.Controls.Add(lblEmpty);
            }
            else
            {
                foreach (var p in players)
                {
                    var cardP = new Panel
                    {
                        Width = 245,
                        Height = 88,
                        BackColor = Color.FromArgb(32, 35, 40),
                        Margin = new Padding(0, 0, 0, 8),
                        Padding = new Padding(8)
                    };

                    var lblPName = new Label
                    {
                        Text = "👤 " + p.Name,
                        Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                        ForeColor = Color.FromArgb(78, 201, 176),
                        AutoSize = true,
                        Location = new Point(8, 6)
                    };

                    string cleanDim = p.Dimension.Replace("minecraft:", "");
                    var lblPCoords = new Label
                    {
                        Text = $"XYZ: {p.X:F0}, {p.Y:F0}, {p.Z:F0} ({cleanDim})\nVida: {p.Health:F1} / {p.MaxHealth:F1} HP",
                        Font = new Font("Consolas", 8.5f),
                        ForeColor = Color.FromArgb(200, 200, 200),
                        AutoSize = true,
                        Location = new Point(8, 25)
                    };

                    var btnPFocus = new Button
                    {
                        Text = "🎯 Focar",
                        Width = 70,
                        Height = 24,
                        Location = new Point(95, 58),
                        BackColor = Color.FromArgb(45, 60, 75),
                        ForeColor = Color.White,
                        FlatStyle = FlatStyle.Flat,
                        Font = new Font("Segoe UI", 8f, FontStyle.Bold),
                        Cursor = Cursors.Hand
                    };
                    btnPFocus.FlatAppearance.BorderSize = 0;
                    btnPFocus.Click += (s, e) =>
                    {
                        _mapPanX = p.X;
                        _mapPanZ = p.Z;
                        _mapZoom = 1.25f;
                        if (p.Dimension.Contains("nether")) _cmbDimension.SelectedIndex = 1;
                        else if (p.Dimension.Contains("end")) _cmbDimension.SelectedIndex = 2;
                        else _cmbDimension.SelectedIndex = 0;
                        _picMap.Invalidate();
                    };

                    var btnPTp = new Button
                    {
                        Text = "⚡ TP",
                        Width = 70,
                        Height = 24,
                        Location = new Point(170, 58),
                        BackColor = Color.FromArgb(45, 60, 75),
                        ForeColor = Color.White,
                        FlatStyle = FlatStyle.Flat,
                        Font = new Font("Segoe UI", 8f),
                        Cursor = Cursors.Hand
                    };
                    btnPTp.FlatAppearance.BorderSize = 0;
                    btnPTp.Click += (s, e) =>
                    {
                        _txtCommand.Text = $"tp {p.Name} ";
                        _txtCommand.Focus();
                        _txtCommand.SelectionStart = _txtCommand.TextLength;
                    };

                    cardP.Controls.AddRange(new Control[] { lblPName, lblPCoords, btnPFocus, btnPTp });
                    _pnlMapPlayers.Controls.Add(cardP);
                }
            }

            _pnlMapPlayers.ResumeLayout();
        }

        private Panel CreateMetricCard(string title, string metricKey, string tooltipText, out Label valLabel, out Label subLabel, string initVal, string initSub)
        {
            var pnl = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(28, 28, 28),
                Margin = new Padding(4),
                Cursor = Cursors.Hand
            };

            var lblTitle = new Label
            {
                Text = title,
                ForeColor = Color.FromArgb(140, 140, 140),
                Font = new Font("Segoe UI", 8.5f, FontStyle.Regular),
                Location = new Point(10, 8),
                AutoSize = true,
                Cursor = Cursors.Hand
            };

            var lblInfoIcon = new Label
            {
                Text = "ℹ️",
                Font = new Font("Segoe UI", 8f),
                ForeColor = Color.FromArgb(110, 110, 110),
                Location = new Point(pnl.Width - 24, 7),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                AutoSize = true,
                Cursor = Cursors.Hand
            };

            valLabel = new Label
            {
                Text = initVal,
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 16f, FontStyle.Bold),
                Location = new Point(8, 26),
                AutoSize = true,
                Cursor = Cursors.Hand
            };

            subLabel = new Label
            {
                Text = initSub,
                ForeColor = Color.FromArgb(120, 120, 120),
                Font = new Font("Segoe UI", 8f),
                Location = new Point(10, 58),
                AutoSize = true,
                Cursor = Cursors.Hand
            };

            pnl.Controls.AddRange(new Control[] { lblTitle, lblInfoIcon, valLabel, subLabel });

            // Efeito Hover elegante com highlight na borda e fundo
            bool isHovered = false;
            pnl.Paint += (s, e) =>
            {
                if (isHovered)
                {
                    using var pen = new Pen(Color.FromArgb(78, 201, 176), 1.5f);
                    e.Graphics.DrawRectangle(pen, 0, 0, pnl.Width - 1, pnl.Height - 1);
                }
            };

            void SetHover(bool hover)
            {
                if (isHovered == hover) return;
                isHovered = hover;
                pnl.BackColor = hover ? Color.FromArgb(36, 42, 48) : Color.FromArgb(28, 28, 28);
                lblTitle.ForeColor = hover ? Color.FromArgb(200, 220, 220) : Color.FromArgb(140, 140, 140);
                lblInfoIcon.ForeColor = hover ? Color.FromArgb(78, 201, 176) : Color.FromArgb(110, 110, 110);
                pnl.Invalidate();
            }

            void OnChildMouseEnter(object? s, EventArgs e) => SetHover(true);
            void OnChildMouseLeave(object? s, EventArgs e)
            {
                var mousePos = pnl.PointToClient(Cursor.Position);
                if (!pnl.ClientRectangle.Contains(mousePos))
                {
                    SetHover(false);
                }
            }

            pnl.MouseEnter += (s, e) => SetHover(true);
            pnl.MouseLeave += OnChildMouseLeave;
            lblTitle.MouseEnter += OnChildMouseEnter;
            lblTitle.MouseLeave += OnChildMouseLeave;
            lblInfoIcon.MouseEnter += OnChildMouseEnter;
            lblInfoIcon.MouseLeave += OnChildMouseLeave;
            valLabel.MouseEnter += OnChildMouseEnter;
            valLabel.MouseLeave += OnChildMouseLeave;
            subLabel.MouseEnter += OnChildMouseEnter;
            subLabel.MouseLeave += OnChildMouseLeave;

            // Configuração do ToolTip
            string fullTooltip = tooltipText + "\n\n💡 Clique para abrir a explicação detalhada e boas práticas!";
            _metricToolTip.SetToolTip(pnl, fullTooltip);
            _metricToolTip.SetToolTip(lblTitle, fullTooltip);
            _metricToolTip.SetToolTip(lblInfoIcon, fullTooltip);
            _metricToolTip.SetToolTip(valLabel, fullTooltip);
            _metricToolTip.SetToolTip(subLabel, fullTooltip);

            // Clique para abrir modal explicativa completa
            void OnCardClick(object? s, EventArgs e)
            {
                using var dlg = new MetricDetailForm(metricKey);
                dlg.ShowDialog(this);
            }

            pnl.Click += OnCardClick;
            lblTitle.Click += OnCardClick;
            lblInfoIcon.Click += OnCardClick;
            valLabel.Click += OnCardClick;
            subLabel.Click += OnCardClick;

            return pnl;
        }

        private Panel CreateMetricCardWithBar(string title, string metricKey, string tooltipText, out Label valLabel, out Label subLabel, out Panel barPanel, string initVal, string initSub, PaintEventHandler? customPaint = null)
        {
            var pnl = CreateMetricCard(title, metricKey, tooltipText, out valLabel, out subLabel, initVal, initSub);

            var panel = new Panel
            {
                Location = new Point(10, 56),
                Size = new Size(Math.Max(20, pnl.Width - 20), 6),
                BackColor = Color.FromArgb(45, 45, 45),
                Cursor = Cursors.Hand
            };

            if (customPaint != null)
            {
                panel.Paint += customPaint;
            }
            else
            {
                panel.Paint += (s, e) =>
                {
                    var telem = _sampler.LatestTelemetry;
                    double pct = telem?.Memory != null ? telem.Memory.Percent : 0;
                    int w = (int)((panel.Width * pct) / 100.0);
                    Color barColor = pct > 85 ? Color.FromArgb(241, 76, 76) : Color.FromArgb(78, 201, 176);
                    e.Graphics.FillRectangle(new SolidBrush(barColor), 0, 0, w, panel.Height);
                };
            }

            pnl.Resize += (s, e) =>
            {
                panel.Width = Math.Max(20, pnl.Width - 20);
                panel.Invalidate();
            };

            subLabel.Location = new Point(10, 64);
            pnl.Controls.Add(panel);
            barPanel = panel;

            // Hover e clique no painel da barra
            void OnBarMouseEnter(object? s, EventArgs e)
            {
                pnl.BackColor = Color.FromArgb(36, 42, 48);
                pnl.Invalidate();
            }
            void OnBarMouseLeave(object? s, EventArgs e)
            {
                var mousePos = pnl.PointToClient(Cursor.Position);
                if (!pnl.ClientRectangle.Contains(mousePos))
                {
                    pnl.BackColor = Color.FromArgb(28, 28, 28);
                    pnl.Invalidate();
                }
            }
            panel.MouseEnter += OnBarMouseEnter;
            panel.MouseLeave += OnBarMouseLeave;
            panel.Click += (s, e) =>
            {
                using var dlg = new MetricDetailForm(metricKey);
                dlg.ShowDialog(this);
            };
            _metricToolTip.SetToolTip(panel, tooltipText + "\n\n💡 Clique para abrir a explicação detalhada e boas práticas!");

            return pnl;
        }

        private Button CreateHeaderButton(string text, int width, EventHandler onClick)
        {
            var btn = new Button
            {
                Text = text,
                Width = width,
                Height = 32,
                Margin = new Padding(3, 0, 3, 0),
                BackColor = Color.FromArgb(45, 45, 45),
                ForeColor = Color.FromArgb(240, 240, 240),
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Regular),
                Cursor = Cursors.Hand
            };
            btn.FlatAppearance.BorderSize = 0;
            btn.Click += onClick;
            return btn;
        }

        // ========================================================================= LOGS & CHAT
        private void Server_OnServerStartedSuccessfully(string info)
        {
            if (this.IsDisposed) return;
            this.BeginInvoke(new Action(() =>
            {
                // Mensagem em verde indicando que o servidor está online (posicionada no final do console)
                Color green = Color.FromArgb(46, 204, 113);
                Color emerald = Color.FromArgb(78, 201, 176);
                Color gray = Color.FromArgb(190, 190, 190);

                string timestamp = DateTime.Now.ToString("HH:mm:ss");

                AppendTextToLogs("\n", green);
                AppendTextToLogs("================================================================================\n", green);
                AppendTextToLogs("  ✔ O SERVIDOR ESTÁ ONLINE!\n", green);
                AppendTextToLogs($"  [{timestamp}] Inicialização concluída com sucesso.\n", emerald);
                if (!string.IsNullOrWhiteSpace(info))
                {
                    AppendTextToLogs($"  Tempo de carregamento: {info}\n", gray);
                }
                AppendTextToLogs("  Pronto para receber conexões dos jogadores no CanalhasSMP!\n", green);
                AppendTextToLogs("================================================================================\n\n", green);

                _rtbLogs.SelectionStart = _rtbLogs.TextLength;
                _rtbLogs.ScrollToCaret();

                _lblStatusBadge.Text = "● ONLINE (PRONTO)";
                _lblStatusBadge.ForeColor = green;

                _lblAlertBanner.Text = "✔ O SERVIDOR ESTÁ ONLINE! Inicialização concluída com sucesso.";
                _lblAlertBanner.BackColor = Color.FromArgb(20, 75, 40);
                _lblAlertBanner.ForeColor = Color.FromArgb(170, 255, 180);
                _lblAlertBanner.Visible = true;

                Task.Delay(8000).ContinueWith(_ =>
                {
                    if (!this.IsDisposed)
                    {
                        this.BeginInvoke(new Action(() =>
                        {
                            if (_lblAlertBanner.Text.StartsWith("✔ O SERVIDOR ESTÁ ONLINE"))
                            {
                                _lblAlertBanner.Visible = false;
                            }
                        }));
                    }
                });
            }));
        }

        private void Server_OnServerStopped(ServerShutdownDetails details)
        {
            if (this.IsDisposed) return;
            this.BeginInvoke(new Action(() =>
            {
                string timestamp = DateTime.Now.ToString("HH:mm:ss");
                Color green = Color.FromArgb(46, 204, 113);
                Color emerald = Color.FromArgb(78, 201, 176);
                Color gray = Color.FromArgb(190, 190, 190);
                Color gold = Color.FromArgb(255, 201, 58);
                Color red = Color.FromArgb(241, 76, 76);

                if (details.IsSuccess)
                {
                    AppendTextToLogs("\n", green);
                    AppendTextToLogs("================================================================================\n", green);
                    AppendTextToLogs("  ✔ O SERVIDOR FOI FINALIZADO COM SUCESSO!\n", green);
                    AppendTextToLogs($"  [{timestamp}] Desligamento gracioso concluído com segurança.\n", emerald);
                    AppendTextToLogs($"  Motivo: {details.CauseDescription}\n", green);
                    AppendTextToLogs($"  Status: Chunks salvos • Threads paradas • Código de saída: {details.ExitCode} (Normal)\n", gray);
                    AppendTextToLogs("================================================================================\n\n", green);

                    _lblAlertBanner.Text = $"✔ Servidor finalizado: {details.CauseDescription}";
                    _lblAlertBanner.BackColor = Color.FromArgb(20, 75, 40);
                    _lblAlertBanner.ForeColor = Color.FromArgb(170, 255, 180);
                    _lblAlertBanner.Visible = true;
                }
                else if (details.Cause == ShutdownCause.CrashOrException)
                {
                    AppendTextToLogs("\n", red);
                    AppendTextToLogs("================================================================================\n", red);
                    AppendTextToLogs("  ❌ O SERVIDOR FOI ENCERRADO DEVIDO A UMA EXCEÇÃO / CRASH!\n", red);
                    AppendTextToLogs($"  [{timestamp}] Encerramento anormal detectado no processo Java.\n", Color.FromArgb(255, 150, 150));
                    AppendTextToLogs($"  Motivo: {details.CauseDescription}\n", red);
                    if (!string.IsNullOrWhiteSpace(details.ExceptionDetail))
                    {
                        AppendTextToLogs($"  Detalhe: {details.ExceptionDetail}\n", gold);
                    }
                    AppendTextToLogs($"  Código de saída: {details.ExitCode} • Verifique a pasta crash-reports/ ou logs/latest.log\n", gray);
                    AppendTextToLogs("================================================================================\n\n", red);

                    _lblAlertBanner.Text = $"⚠️ Servidor encerrou com erro! Código {details.ExitCode}. Verifique crash-reports.";
                    _lblAlertBanner.BackColor = Color.FromArgb(100, 20, 20);
                    _lblAlertBanner.ForeColor = Color.FromArgb(255, 180, 180);
                    _lblAlertBanner.Visible = true;
                }
                else // ForcedTermination
                {
                    AppendTextToLogs("\n", gold);
                    AppendTextToLogs("================================================================================\n", gold);
                    AppendTextToLogs("  ⚠️ O SERVIDOR FOI ENCERRADO FORÇADAMENTE (KILL)!\n", gold);
                    AppendTextToLogs($"  [{timestamp}] Processo do servidor interrompido de forma imediata pelo usuário.\n", Color.FromArgb(255, 215, 130));
                    AppendTextToLogs($"  Motivo: {details.CauseDescription}\n", gold);
                    AppendTextToLogs($"  Código de saída: {details.ExitCode}\n", gray);
                    AppendTextToLogs("================================================================================\n\n", gold);

                    _lblAlertBanner.Text = "⚠️ Processo do servidor encerrado forçadamente.";
                    _lblAlertBanner.BackColor = Color.FromArgb(80, 60, 15);
                    _lblAlertBanner.ForeColor = Color.FromArgb(255, 220, 150);
                    _lblAlertBanner.Visible = true;
                }

                _rtbLogs.SelectionStart = _rtbLogs.TextLength;
                _rtbLogs.ScrollToCaret();

                Task.Delay(10000).ContinueWith(_ =>
                {
                    if (!this.IsDisposed)
                    {
                        this.BeginInvoke(new Action(() =>
                        {
                            if (_lblAlertBanner.Text.StartsWith("✔ Servidor finalizado") ||
                                _lblAlertBanner.Text.StartsWith("⚠️ Processo do servidor"))
                            {
                                _lblAlertBanner.Visible = false;
                            }
                        }));
                    }
                });
            }));
        }

        private void Server_OnRawLogReceived(string line)
        {
            if (this.IsDisposed) return;
            this.BeginInvoke(new Action(() =>
            {
                // Filtro
                if (!string.IsNullOrEmpty(_txtLogFilter?.Text))
                {
                    if (!line.Contains(_txtLogFilter.Text, StringComparison.OrdinalIgnoreCase)) return;
                }
                if (_chkOnlyErrors != null && _chkOnlyErrors.Checked)
                {
                    if (!line.Contains("WARN", StringComparison.OrdinalIgnoreCase) &&
                        !line.Contains("ERROR", StringComparison.OrdinalIgnoreCase) &&
                        !line.Contains("Exception", StringComparison.OrdinalIgnoreCase)) return;
                }

                Color color = Color.FromArgb(200, 200, 200);
                if (line.Contains("/WARN")) color = Color.FromArgb(255, 183, 77); // Amarelo/Laranja
                else if (line.Contains("/ERROR") || line.Contains("Exception")) color = Color.FromArgb(239, 83, 80); // Vermelho
                else if (line.Contains("Cummins")) color = Color.FromArgb(255, 201, 58); // Ouro

                AppendTextToLogs(line + "\n", color);
            }));
        }

        private void AppendTextToLogs(string text, Color color)
        {
            _rtbLogs.SelectionStart = _rtbLogs.TextLength;
            _rtbLogs.SelectionLength = 0;
            _rtbLogs.SelectionColor = color;
            _rtbLogs.AppendText(text);

            if (_chkAutoScroll.Checked)
            {
                _rtbLogs.ScrollToCaret();
            }
        }

        private void Server_OnChatMessageReceived(ChatMessageItem item)
        {
            if (this.IsDisposed) return;
            this.BeginInvoke(new Action(() =>
            {
                _rtbChat.SelectionStart = _rtbChat.TextLength;
                _rtbChat.SelectionLength = 0;

                // Timestamp
                _rtbChat.SelectionColor = Color.FromArgb(120, 120, 120);
                _rtbChat.AppendText($"[{item.Time}] ");

                switch (item.Type)
                {
                    case ChatType.CumminsChat:
                        _rtbChat.SelectionColor = Color.FromArgb(255, 201, 58); // Ouro
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Bold);
                        _rtbChat.AppendText("<Senhor Cummins da Silva> ");
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Regular);
                        _rtbChat.SelectionColor = Color.FromArgb(255, 236, 179);
                        _rtbChat.AppendText(item.Content + "\n");
                        break;

                    case ChatType.PlayerChat:
                        _rtbChat.SelectionColor = Color.FromArgb(79, 193, 255); // Aqua
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Bold);
                        _rtbChat.AppendText($"<{item.Sender}> ");
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Regular);
                        _rtbChat.SelectionColor = Color.White;
                        _rtbChat.AppendText(item.Content + "\n");
                        break;

                    case ChatType.Advancement:
                        _rtbChat.SelectionColor = Color.FromArgb(206, 145, 120); // Dourado suave
                        _rtbChat.AppendText($"★ {item.Sender} {item.Content}\n");
                        break;

                    case ChatType.Death:
                        _rtbChat.SelectionColor = Color.FromArgb(241, 76, 76); // Vermelho
                        _rtbChat.AppendText($"{item.Content}\n");
                        break;

                    case ChatType.Join:
                        _rtbChat.SelectionColor = Color.FromArgb(106, 153, 85); // Verde
                        _rtbChat.AppendText($"➜ {item.Sender} entrou no servidor\n");
                        break;

                    case ChatType.Leave:
                        _rtbChat.SelectionColor = Color.FromArgb(140, 140, 140); // Cinza
                        _rtbChat.AppendText($"✖ {item.Sender} saiu do jogo\n");
                        break;

                    case ChatType.ConsoleCommand:
                        _rtbChat.SelectionColor = Color.FromArgb(179, 157, 219); // Roxo suave / Console
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Bold);
                        _rtbChat.AppendText($"{item.Sender} ");
                        _rtbChat.SelectionFont = new Font(_rtbChat.Font, FontStyle.Regular);
                        _rtbChat.SelectionColor = Color.FromArgb(235, 235, 235); // Texto claro nítido
                        _rtbChat.AppendText($"{item.Content}\n");
                        break;

                    default:
                        _rtbChat.SelectionColor = Color.White;
                        _rtbChat.AppendText($"{item.Sender}: {item.Content}\n");
                        break;
                }

                _rtbChat.ScrollToCaret();
            }));
        }

        private void Server_OnServerStateChanged(bool running)
        {
            if (this.IsDisposed) return;
            this.BeginInvoke(new Action(() =>
            {
                if (running)
                {
                    _lblStatusBadge.Text = "● RODANDO (ONLINE)";
                    _lblStatusBadge.ForeColor = Color.FromArgb(78, 201, 176);
                    _btnStart.Enabled = false;
                    _btnStop.Enabled = true;
                    _btnRestart.Enabled = true;
                }
                else
                {
                    _lblStatusBadge.Text = "● PARADO (OFFLINE)";
                    _lblStatusBadge.ForeColor = Color.FromArgb(160, 160, 160);
                    _btnStart.Enabled = true;
                    _btnStop.Enabled = false;
                    _btnStop.Text = "⏹ Parar (Stop)";
                    _btnRestart.Enabled = false;
                    _btnRestart.Text = "🔄 Reiniciar";
                    if (_lblAlertBanner.Text.StartsWith("✔ O SERVIDOR ESTÁ ONLINE"))
                    {
                        _lblAlertBanner.Visible = false;
                    }
                }
            }));
        }

        // ========================================================================= TIMER & METRICS
        private async void Timer_Tick(object? sender, EventArgs e)
        {
            await _sampler.SampleAsync(_server.CurrentProcess);

            var t = _sampler.LatestTelemetry;

            // 0. Auto-recuperação e sincronização de status / PID
            if (_sampler.IsServerAlive)
            {
                if (!_server.IsRunning && t.Pid > 0)
                {
                    _server.AttachToPid(t.Pid);
                }

                string pidText = t.Pid > 0 ? $" [PID: {t.Pid}]" : "";
                _lblStatusBadge.Text = _server.IsAttached ? $"● RODANDO (ONLINE • ANEXADO{pidText})" : $"● RODANDO (ONLINE{pidText})";
                _lblStatusBadge.ForeColor = Color.FromArgb(78, 201, 176);
                _btnStart.Enabled = false;
                _btnStop.Enabled = true;
                _btnRestart.Enabled = true;
            }
            else if (!_server.IsRunning)
            {
                _lblStatusBadge.Text = "● PARADO (OFFLINE)";
                _lblStatusBadge.ForeColor = Color.FromArgb(160, 160, 160);
                _btnStart.Enabled = true;
                _btnStop.Enabled = false;
                _btnRestart.Enabled = false;
                _lblAlertBanner.Visible = false;
            }

            // 1. TPS
            double tps = t?.Tps ?? 0;
            _lblTpsVal.Text = $"{tps:F2}";
            if (tps >= 19.5) _lblTpsVal.ForeColor = Color.FromArgb(78, 201, 176);
            else if (tps >= 15.0) _lblTpsVal.ForeColor = Color.FromArgb(255, 201, 58);
            else _lblTpsVal.ForeColor = Color.FromArgb(241, 76, 76);

            // 2. MSPT
            if (t?.Mspt != null)
            {
                _lblMsptVal.Text = $"{t.Mspt.Median:F2} ms";
                _lblMsptSub.Text = $"95%: {t.Mspt.P95:F1}ms | Max: {t.Mspt.Max:F1}ms";
            }

            // 3. CPU
            _lblCpuVal.Text = $"{_sampler.CurrentJavaCpuPercent:F1}%";
            _lblCpuSub.Text = $"Processo Java ({Environment.ProcessorCount} cores)";

            // 4. RAM Heap
            if (t?.Memory != null)
            {
                double usedGb = t.Memory.UsedMb / 1024.0;
                double totalGb = t.Memory.TotalMb / 1024.0;
                _lblRamVal.Text = $"{usedGb:F1} / {totalGb:F1} GB";
                _lblRamSub.Text = $"{t.Memory.Percent:F1}% em uso";
                _panelRamBar.Invalidate();
            }

            // 5. GC & Alertas
            if (t?.Alert != null)
            {
                _lblAlertBanner.Text = "⚠️ " + t.Alert;
                _lblAlertBanner.Visible = true;
                _lblGcVal.Text = "Alerta GC!";
                _lblGcVal.ForeColor = Color.FromArgb(241, 76, 76);
            }
            else
            {
                _lblAlertBanner.Visible = false;
                _lblGcVal.Text = "Saudável";
                _lblGcVal.ForeColor = Color.FromArgb(78, 201, 176);
            }

            if (t?.Gc != null && t.Gc.Count > 0)
            {
                long youngCount = t.Gc[0].Count;
                long oldCount = t.Gc.Count > 1 ? t.Gc[1].Count : 0;
                _lblGcSub.Text = $"Young: {youngCount} | Old (Full): {oldCount}";
            }

            // 6. Jogadores
            int count = t?.PlayerCount ?? 0;
            _lblPlayersVal.Text = $"{count} Online";
            if (t?.Players != null && t.Players.Count > 0)
            {
                _lblPlayersSub.Text = string.Join(", ", t.Players);
            }
            else
            {
                _lblPlayersSub.Text = "Nenhum no momento";
            }

            // 7. Tokens LLM (Inteligência Artificial)
            UpdateTokensCard(t?.Tokens);

            // Atualiza gráfico e radar de mapa se estiverem ativos
            _tickCount++;
            if (_tickCount % 45 == 0)
            {
                LoadRegions();
                RefreshActivePlayerRegions(forceReRender: true);
            }
            _picGraph.Invalidate();
            _picMap?.Invalidate();
            UpdateMapPlayersSidebar(t);
        }

        private void UpdateTokensCard(TokenInfo? tok)
        {
            if (tok == null)
            {
                tok = TryLoadOfflineTokens();
            }

            if (tok != null)
            {
                string usedStr = tok.Used >= 1_000_000 ? $"{tok.Used / 1_000_000.0:F2}M" : $"{tok.Used / 1_000.0:F1}k";
                string limitStr = tok.Limit >= 1_000_000 ? $"{tok.Limit / 1_000_000.0:F1}M" : $"{tok.Limit / 1_000.0:F0}k";
                string remStr = tok.Remaining >= 1_000_000 ? $"{tok.Remaining / 1_000_000.0:F2}M" : $"{tok.Remaining / 1_000.0:F1}k";

                _lblTokensVal.Text = $"{usedStr} ({tok.PercentUsed:F1}%)";
                if (tok.PercentUsed >= 90) _lblTokensVal.ForeColor = Color.FromArgb(241, 76, 76);
                else if (tok.PercentUsed >= 70) _lblTokensVal.ForeColor = Color.FromArgb(255, 201, 58);
                else _lblTokensVal.ForeColor = Color.FromArgb(78, 201, 176);

                string resetRemaining = tok.ResetRemainingFormatted;
                if (string.IsNullOrWhiteSpace(resetRemaining) || resetRemaining == "--")
                {
                    if (tok.NextResetEpochMs > 0)
                    {
                        long diffMs = Math.Max(0, tok.NextResetEpochMs - DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
                        long diffMin = diffMs / 60000;
                        long h = diffMin / 60;
                        long m = diffMin % 60;
                        resetRemaining = h > 0 ? $"{h}h {m:D2}m" : $"{m}m";
                    }
                    else
                    {
                        resetRemaining = tok.ResetCycle;
                    }
                }

                _lblTokensSub.Text = $"Restam {remStr} ({tok.PercentRemaining:F1}%) • Reset em {resetRemaining}";
                _panelTokensBar.Invalidate();
            }
            else
            {
                _lblTokensVal.Text = "-- / --";
                _lblTokensSub.Text = "Aguardando telemetria";
                _lblTokensVal.ForeColor = Color.FromArgb(160, 160, 160);
                _panelTokensBar.Invalidate();
            }
        }

        private TokenInfo? TryLoadOfflineTokens()
        {
            try
            {
                string tokenPath = Path.Combine(_serverDir, "config", "senhor-cummins", "token_usage.json");
                if (File.Exists(tokenPath))
                {
                    using var fs = new FileStream(tokenPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                    using var reader = new StreamReader(fs);
                    string json = reader.ReadToEnd();
                    return System.Text.Json.JsonSerializer.Deserialize<TokenInfo>(json);
                }
            }
            catch { }
            return null;
        }

        private void PicGraph_Paint(object? sender, PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            int w = _picGraph.Width;
            int h = _picGraph.Height;

            g.Clear(Color.FromArgb(18, 18, 18));

            // Linhas de Grade
            using var gridPen = new Pen(Color.FromArgb(35, 35, 35), 1);
            for (int y = 50; y < h - 40; y += 40)
            {
                g.DrawLine(gridPen, 50, y, w - 20, y);
            }

            // Legenda
            using var font = new Font("Segoe UI", 9f);
            using var brushTps = new SolidBrush(Color.FromArgb(78, 201, 176));
            using var brushMspt = new SolidBrush(Color.FromArgb(255, 183, 77));
            using var brushCpu = new SolidBrush(Color.FromArgb(79, 193, 255));

            g.DrawString("● TPS (0-20)", font, brushTps, 60, 15);
            g.DrawString("● MSPT Mediana (0-50ms)", font, brushMspt, 180, 15);
            g.DrawString("● CPU Java (0-100%)", font, brushCpu, 370, 15);

            List<SamplePoint> samples;
            lock (_sampler.History)
            {
                samples = new List<SamplePoint>(_sampler.History);
            }

            if (samples.Count < 2) return;

            int plotW = w - 80;
            int plotH = h - 90;
            int plotY0 = 50;
            int plotX0 = 60;

            float stepX = (float)plotW / Math.Max(59, samples.Count - 1);

            // Linha limite de 20 TPS
            using var limitPen = new Pen(Color.FromArgb(40, 80, 70), 1) { DashStyle = DashStyle.Dash };
            g.DrawLine(limitPen, plotX0, plotY0, plotX0 + plotW, plotY0);

            // Desenha TPS (0 a 20)
            using var penTps = new Pen(Color.FromArgb(78, 201, 176), 2);
            for (int i = 0; i < samples.Count - 1; i++)
            {
                float x1 = plotX0 + i * stepX;
                float y1 = plotY0 + plotH - (float)((samples[i].Tps / 20.0) * plotH);

                float x2 = plotX0 + (i + 1) * stepX;
                float y2 = plotY0 + plotH - (float)((samples[i + 1].Tps / 20.0) * plotH);

                g.DrawLine(penTps, x1, y1, x2, y2);
            }

            // Desenha MSPT (0 a 50ms)
            using var penMspt = new Pen(Color.FromArgb(255, 183, 77), 2);
            for (int i = 0; i < samples.Count - 1; i++)
            {
                float x1 = plotX0 + i * stepX;
                float y1 = plotY0 + plotH - (float)(Math.Min(1.0, samples[i].MsptMedian / 50.0) * plotH);

                float x2 = plotX0 + (i + 1) * stepX;
                float y2 = plotY0 + plotH - (float)(Math.Min(1.0, samples[i + 1].MsptMedian / 50.0) * plotH);

                g.DrawLine(penMspt, x1, y1, x2, y2);
            }
        }

        private static readonly string[] AutocompleteCommands = {
            "chunky start", "chunky pause", "chunky progress", "chunky radius 6000", "chunky shape circle",
            "spark profiler start", "spark profiler stop", "spark tps", "spark health", "spark gc",
            "cummins dizer ", "cummins gato tp", "cummins perola", "cummins cantada", "cummins humor ", "cummins status",
            "textilebackup run", "save-all", "say ", "stop", "whitelist list", "whitelist add ",
            "op ", "deop ", "tp ", "gamemode creative ", "gamemode survival ",
            "time set day", "time set night", "weather clear",
            "cummins vitoria", "cummins Vitoria ligado", "cummins Vitoria desligado"
        };

        // ========================================================================= AÇÕES E COMANDOS
        private void TxtCommand_KeyDown(object? sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Enter)
            {
                e.SuppressKeyPress = true;
                SendTypedCommand();
            }
            else if (e.KeyCode == Keys.Tab)
            {
                e.SuppressKeyPress = true;
                string cur = _txtCommand.Text;
                if (!string.IsNullOrWhiteSpace(cur))
                {
                    string trimmed = cur.TrimStart('/');
                    var match = AutocompleteCommands.FirstOrDefault(c => c.StartsWith(trimmed, StringComparison.OrdinalIgnoreCase));
                    if (match != null)
                    {
                        _txtCommand.Text = match;
                        _txtCommand.SelectionStart = _txtCommand.Text.Length;
                    }
                }
            }
            else if (e.KeyCode == Keys.Up)
            {
                e.SuppressKeyPress = true;
                if (_commandHistory.Count > 0 && _commandHistoryIndex > 0)
                {
                    _commandHistoryIndex--;
                    _txtCommand.Text = _commandHistory[_commandHistoryIndex];
                    _txtCommand.SelectionStart = _txtCommand.Text.Length;
                }
            }
            else if (e.KeyCode == Keys.Down)
            {
                e.SuppressKeyPress = true;
                if (_commandHistoryIndex < _commandHistory.Count - 1)
                {
                    _commandHistoryIndex++;
                    _txtCommand.Text = _commandHistory[_commandHistoryIndex];
                    _txtCommand.SelectionStart = _txtCommand.Text.Length;
                }
                else
                {
                    _commandHistoryIndex = _commandHistory.Count;
                    _txtCommand.Text = "";
                }
            }
        }

        private void SendTypedCommand()
        {
            string cmd = _txtCommand.Text.Trim();
            if (string.IsNullOrEmpty(cmd)) return;

            _commandHistory.Add(cmd);
            _commandHistoryIndex = _commandHistory.Count;

            _server.SendCommand(cmd);
            _txtCommand.Clear();
        }

        private void SendSayMessage()
        {
            string msg = _txtSay.Text.Trim();
            if (string.IsNullOrEmpty(msg)) return;

            _server.SendCommand("say " + msg);
            _txtSay.Clear();
        }

        private void ToggleSpark()
        {
            if (!_sparkRunning)
            {
                _server.SendCommand("spark profiler start");
                _btnSpark.Text = "⏹ Spark Stop";
                _btnSpark.BackColor = Color.FromArgb(198, 40, 40);
                _sparkRunning = true;
            }
            else
            {
                _server.SendCommand("spark profiler stop");
                _btnSpark.Text = "⚡ Spark Start";
                _btnSpark.BackColor = Color.FromArgb(45, 45, 45);
                _sparkRunning = false;
            }
        }

        private void ToggleVitoriaAi()
        {
            _vitoriaAiEnabled = !_vitoriaAiEnabled;
            _server.SendCommand(_vitoriaAiEnabled ? "cummins Vitoria ligado" : "cummins Vitoria desligado");
            UpdateVitoriaAiButtonVisual();
        }

        private void UpdateVitoriaAiButtonVisual()
        {
            if (_btnVitoriaAi == null || _btnVitoriaAi.IsDisposed) return;
            if (_vitoriaAiEnabled)
            {
                _btnVitoriaAi.Text = "🧠 Vitória: ON";
                _btnVitoriaAi.BackColor = Color.FromArgb(142, 36, 170);
            }
            else
            {
                _btnVitoriaAi.Text = "🧠 Vitória: OFF";
                _btnVitoriaAi.BackColor = Color.FromArgb(45, 45, 45);
            }
        }

        private void LiberarFirewallDoCelular()
        {
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = "/c netsh advfirewall firewall delete rule name=\"CanalhasSMP Telemetria HTTP\" >nul 2>&1 & netsh advfirewall firewall add rule name=\"CanalhasSMP Telemetria HTTP\" dir=in action=allow protocol=TCP localport=25588 profile=any & netsh advfirewall firewall delete rule name=\"CanalhasSMP Minecraft Server\" >nul 2>&1 & netsh advfirewall firewall add rule name=\"CanalhasSMP Minecraft Server\" dir=in action=allow protocol=TCP localport=25565 profile=any & powershell -NoProfile -Command \"Get-NetConnectionProfile | Set-NetConnectionProfile -NetworkCategory Private; Get-NetFirewallRule -DisplayName '*Java*' | Where-Object { $_.Action -eq 'Block' -and $_.Direction -eq 'Inbound' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue\"",
                    Verb = "runas",
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                var proc = Process.Start(psi);
                proc?.WaitForExit();

                MessageBox.Show("Portas 25588 (App Celular) e 25565 (Minecraft) liberadas com sucesso no Firewall do Windows!\n\nAgora seu celular poderá se conectar no IP 192.168.18.20.", "Firewall Liberado", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Não foi possível liberar o firewall automaticamente:\n" + ex.Message + "\n\nVocê também pode executar o arquivo 'LIBERAR_FIREWALL_APP.bat' na pasta do servidor como Administrador.", "Aviso", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }
    }
}
