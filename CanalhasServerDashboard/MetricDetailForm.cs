using System;
using System.Drawing;
using System.Windows.Forms;

namespace CanalhasServerDashboard
{
    public class MetricDetailForm : Form
    {
        public MetricDetailForm(string metricKey)
        {
            InitializeMetricView(metricKey);
        }

        private void InitializeMetricView(string key)
        {
            this.Size = new Size(740, 700);
            this.MinimumSize = new Size(680, 550);
            this.StartPosition = FormStartPosition.CenterParent;
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.MinimizeBox = false;
            this.ShowInTaskbar = false;
            this.BackColor = Color.FromArgb(20, 20, 20);
            this.ForeColor = Color.FromArgb(235, 235, 235);
            this.Font = new Font("Segoe UI", 9.5f);

            var info = GetMetricInfo(key);
            this.Text = $"CanalhasSMP - Guia de Métrica: {info.Title}";

            // 1. Cabeçalho Superior
            var pnlHeader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 85,
                BackColor = Color.FromArgb(26, 26, 26),
                Padding = new Padding(22, 14, 22, 10)
            };

            var lblTitle = new Label
            {
                Text = info.TitleWithIcon,
                Font = new Font("Segoe UI", 16f, FontStyle.Bold),
                ForeColor = info.AccentColor,
                AutoSize = true,
                Location = new Point(20, 12)
            };

            var lblSubtitle = new Label
            {
                Text = info.Summary,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.FromArgb(170, 170, 170),
                AutoSize = true,
                Location = new Point(22, 48)
            };

            pnlHeader.Controls.AddRange(new Control[] { lblTitle, lblSubtitle });

            // 2. Rodapé com Botão Fechar
            var pnlBottom = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 60,
                BackColor = Color.FromArgb(24, 24, 24),
                Padding = new Padding(20, 10, 20, 10)
            };

            var lblFooterHint = new Label
            {
                Text = "💡 Dica: Você pode ver o gráfico histórico em tempo real na aba '📊 Desempenho'.",
                ForeColor = Color.FromArgb(140, 140, 140),
                Font = new Font("Segoe UI", 8.5f, FontStyle.Italic),
                Location = new Point(20, 18),
                AutoSize = true
            };

            var btnClose = new Button
            {
                Text = "Entendi / Fechar",
                Width = 140,
                Height = 36,
                Anchor = AnchorStyles.Right | AnchorStyles.Top,
                Location = new Point(pnlBottom.Width - 165, 12),
                BackColor = info.AccentColor,
                ForeColor = Color.Black,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                DialogResult = DialogResult.OK
            };
            btnClose.FlatAppearance.BorderSize = 0;
            this.AcceptButton = btnClose;

            pnlBottom.Controls.AddRange(new Control[] { lblFooterHint, btnClose });
            pnlBottom.Resize += (s, e) =>
            {
                btnClose.Location = new Point(pnlBottom.Width - 165, 12);
            };

            // 3. Painel de Conteúdo com Scroll
            var pnlBody = new Panel
            {
                Dock = DockStyle.Fill,
                AutoScroll = true,
                Padding = new Padding(20, 14, 20, 14)
            };

            int currentY = 12;

            // Card 1: Didático / Analogia
            var cardDidactic = CreateSectionCard(
                "💡 O que significa e como funciona (Analogia Simples)",
                info.DidacticExplanation,
                Color.FromArgb(30, 30, 30),
                ref currentY,
                pnlBody);

            // Card 2: Escala de medições (Bom, Médio, Ruim)
            var cardBenchmarks = CreateBenchmarkSection(
                "📊 Tabela de Referência: Quando é Bom, Médio e Ruim",
                info.GoodRange,
                info.MediumRange,
                info.BadRange,
                ref currentY,
                pnlBody);

            // Card 3: Boas Práticas & O que fazer
            var cardBestPractices = CreateSectionCard(
                "🚀 O que fazer para melhorar & Boas Práticas",
                info.BestPractices,
                Color.FromArgb(28, 34, 32),
                ref currentY,
                pnlBody);

            this.Controls.Add(pnlBody);
            this.Controls.Add(pnlBottom);
            this.Controls.Add(pnlHeader);
        }

        private Panel CreateSectionCard(string title, string content, Color backColor, ref int currentY, Panel parent)
        {
            var pnl = new Panel
            {
                Location = new Point(20, currentY),
                Width = parent.ClientSize.Width - 45,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                BackColor = backColor,
                Padding = new Padding(16, 14, 16, 16)
            };

            var lblHeader = new Label
            {
                Text = title,
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 201, 58),
                Dock = DockStyle.Top,
                Height = 26
            };

            var lblText = new Label
            {
                Text = content,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.FromArgb(220, 220, 220),
                Dock = DockStyle.Top,
                AutoSize = true,
                Padding = new Padding(0, 8, 0, 0)
            };

            pnl.Controls.Add(lblText);
            pnl.Controls.Add(lblHeader);

            pnl.Height = lblHeader.Height + lblText.PreferredHeight + 35;
            parent.Controls.Add(pnl);

            currentY += pnl.Height + 14;
            return pnl;
        }

        private Panel CreateBenchmarkSection(string title, BenchmarkItem good, BenchmarkItem medium, BenchmarkItem bad, ref int currentY, Panel parent)
        {
            var pnl = new Panel
            {
                Location = new Point(20, currentY),
                Width = parent.ClientSize.Width - 45,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                BackColor = Color.FromArgb(28, 28, 28),
                Padding = new Padding(16, 14, 16, 16)
            };

            var lblHeader = new Label
            {
                Text = title,
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(78, 201, 176),
                Dock = DockStyle.Top,
                Height = 28
            };
            pnl.Controls.Add(lblHeader);

            int itemY = 36;
            AddBenchmarkRow(pnl, good, Color.FromArgb(46, 125, 50), Color.FromArgb(165, 214, 167), ref itemY);
            AddBenchmarkRow(pnl, medium, Color.FromArgb(230, 81, 0), Color.FromArgb(255, 204, 128), ref itemY);
            AddBenchmarkRow(pnl, bad, Color.FromArgb(198, 40, 40), Color.FromArgb(239, 154, 154), ref itemY);

            pnl.Height = itemY + 14;
            parent.Controls.Add(pnl);

            currentY += pnl.Height + 14;
            return pnl;
        }

        private void AddBenchmarkRow(Panel parent, BenchmarkItem item, Color badgeBg, Color textAccent, ref int curY)
        {
            var rowPanel = new Panel
            {
                Location = new Point(14, curY),
                Width = parent.Width - 28,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                BackColor = Color.FromArgb(22, 22, 22),
                Padding = new Padding(10, 8, 10, 8)
            };

            var lblBadge = new Label
            {
                Text = item.StatusTag,
                BackColor = badgeBg,
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                TextAlign = ContentAlignment.MiddleCenter,
                Location = new Point(10, 10),
                Size = new Size(130, 24)
            };

            var lblRange = new Label
            {
                Text = item.Range,
                ForeColor = textAccent,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Location = new Point(150, 12),
                AutoSize = true
            };

            var lblDesc = new Label
            {
                Text = item.Description,
                ForeColor = Color.FromArgb(200, 200, 200),
                Font = new Font("Segoe UI", 9f),
                Location = new Point(10, 40),
                Width = rowPanel.Width - 20,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                AutoSize = true
            };

            rowPanel.Controls.AddRange(new Control[] { lblBadge, lblRange, lblDesc });
            rowPanel.Height = 44 + lblDesc.PreferredHeight + 10;

            parent.Controls.Add(rowPanel);
            curY += rowPanel.Height + 8;
        }

        private struct BenchmarkItem
        {
            public string StatusTag;
            public string Range;
            public string Description;

            public BenchmarkItem(string tag, string range, string desc)
            {
                StatusTag = tag;
                Range = range;
                Description = desc;
            }
        }

        private class MetricInfo
        {
            public string Title = "";
            public string TitleWithIcon = "";
            public string Summary = "";
            public Color AccentColor = Color.FromArgb(78, 201, 176);
            public string DidacticExplanation = "";
            public BenchmarkItem GoodRange;
            public BenchmarkItem MediumRange;
            public BenchmarkItem BadRange;
            public string BestPractices = "";
        }

        private static MetricInfo GetMetricInfo(string key)
        {
            return key.ToUpperInvariant() switch
            {
                "TPS" => new MetricInfo
                {
                    Title = "TPS (Ticks por Segundo)",
                    TitleWithIcon = "⚡ TPS — Ticks por Segundo",
                    Summary = "O batimento cardíaco do servidor Minecraft: o alvo absoluto é sempre 20.0 TPS.",
                    AccentColor = Color.FromArgb(78, 201, 176),
                    DidacticExplanation =
                        "O Minecraft opera como um relógio que dá 20 badaladas por segundo (20 TPS).\n" +
                        "A cada 'tick', o servidor calcula tudo o que acontece no mundo: movimento dos monstros, " +
                        "crescimento das plantações, física da gravidade, circuitos de redstone e as ações dos jogadores.\n\n" +
                        "• Se o TPS for 20: o tempo do jogo corre perfeitamente sincronizado com o mundo real.\n" +
                        "• Se o TPS cair para 10: o tempo do jogo passa na metade da velocidade (em câmera lenta)!",
                    GoodRange = new BenchmarkItem("🟢 IDEAL / LISO", "19.5 a 20.0 TPS",
                        "O servidor processa tudo sem atraso. Blocos quebram instantaneamente, mobs se movem sem engasgos e flechas voam com física perfeita."),
                    MediumRange = new BenchmarkItem("🟡 ATENÇÃO / ALERTA", "16.0 a 19.4 TPS",
                        "O servidor está com dificuldades para acompanhar o relógio. Pequenos atrasos em redstone começam a aparecer e itens demoram frações de segundo para dropar."),
                    BadRange = new BenchmarkItem("🔴 RUIM / CRÍTICO", "Abaixo de 16.0 TPS",
                        "Lag severo no jogo. Jogadores sofrem 'rubberband' (voltam para trás ao andar), blocos reaparecem ao quebrar ('ghost blocks') e comer comida demora vários segundos."),
                    BestPractices =
                        "1. Reduza a 'simulation-distance' em server.properties (ex: de 10 para 6 ou 8). Isso diminui a área de mobs e redstone ativos sem diminuir a visão do mapa.\n" +
                        "2. Evite acúmulo exagerado de animais e monstros confinados em espaços minúsculos (1x1).\n" +
                        "3. Dispare o profiler do Spark para encontrar qual mod ou entidade está pesando (use o botão '⚡ Spark Profiler' no topo do painel).\n" +
                        "4. Mantenha mods de desempenho como Lithium instalados no servidor."
                },

                "MSPT" => new MetricInfo
                {
                    Title = "MSPT (Milliseconds Per Tick)",
                    TitleWithIcon = "⏱️ MSPT — Tempo de Processamento do Tick",
                    Summary = "O tempo em milissegundos que o processador leva para calcular cada tick do jogo.",
                    AccentColor = Color.FromArgb(79, 193, 255),
                    DidacticExplanation =
                        "Pense no MSPT como o 'tempo que o cérebro do servidor gasta pensando' a cada segundo.\n" +
                        "Como 1 segundo possui 1000 milissegundos e o servidor precisa fazer 20 ticks, o servidor tem um ORÇAMENTO MÁXIMO DE 50ms por tick (1000 ÷ 20 = 50ms).\n\n" +
                        "• Se ele gasta 20ms: sobram 30ms de folga em que o servidor descansa e o TPS fica cravado em 20.0.\n" +
                        "• Se ele gasta 60ms: o tempo estourou! O tick atrasa e o TPS cai automaticamente para ~16.6.",
                    GoodRange = new BenchmarkItem("🟢 EXCELENTE", "Abaixo de 35.0 ms",
                        "Muita folga de processamento. O servidor aguenta explosões de TNT, viagens rápidas pelo Nether e vários jogadores entrando simultaneamente."),
                    MediumRange = new BenchmarkItem("🟡 NO LIMITE", "35.0 ms a 49.9 ms",
                        "O TPS ainda marca 20.0, mas o servidor está no limite de sua capacidade. Qualquer pico (como geração de novo mapa ou mob trap ligada) causará queda de TPS."),
                    BadRange = new BenchmarkItem("🔴 ESTOURADO", "Acima de 50.0 ms",
                        "O orçamento foi ultrapassado. O TPS cai na proporção exata do excesso (ex: 100ms de MSPT = apenas 10 TPS)."),
                    BestPractices =
                        "1. Observe a diferença entre Mediana e Máximo (Max): se a mediana for 25ms mas o Max for 180ms, o problema são picos pontuais (como autosave ou geração de chunks).\n" +
                        "2. Coloque composteiras em cima de funis (hoppers) em farms de redstone para desativar a checagem contínua de itens no ar.\n" +
                        "3. Use o mod Chunky para pré-gerar o terreno do mundo durante a madrugada (evita que o MSPT suba quando jogadores exploram de Elytra)."
                },

                "CPU" => new MetricInfo
                {
                    Title = "CPU Servidor (Processador)",
                    TitleWithIcon = "🖥️ CPU — Consumo do Processador",
                    Summary = "Carga de trabalho dos núcleos do processador exigida pelo processo Java do servidor.",
                    AccentColor = Color.FromArgb(255, 183, 77),
                    DidacticExplanation =
                        "O loop principal do Minecraft roda majoritariamente em UMA ÚNICA THREAD PRINCIPAL (Single-Core).\n\n" +
                        "Isso significa que, mesmo se você tiver um processador moderno de 8 ou 16 núcleos, se aquele núcleo principal onde o mundo roda bater 100%, o jogo lagará, mesmo que o Gerenciador de Tarefas do Windows mostre apenas '20% de CPU total'!",
                    GoodRange = new BenchmarkItem("🟢 SAUDÁVEL", "10% a 50% de uso",
                        "O processador lida com tranquilidade com o mundo, cálculos de IA dos monstros e conexões de rede dos jogadores."),
                    MediumRange = new BenchmarkItem("🟡 CARGA ELEVADA", "50% a 80% de uso",
                        "Muitos cálculos simultâneos: múltiplos jogadores explorando direções diferentes ou batalhas intensas."),
                    BadRange = new BenchmarkItem("🔴 GARGALO / 100%", "Acima de 85% de uso",
                        "O núcleo do servidor atingiu o teto. O MSPT vai disparar e o servidor começará a perder ticks."),
                    BestPractices =
                        "1. No Windows, configure o Plano de Energia para 'Alto Desempenho' para evitar que o clock da CPU sofra quedas (throttling).\n" +
                        "2. O mod C2ME (instalado no CanalhasSMP) descarrega a geração de mapa para núcleos secundários, aliviando o núcleo principal.\n" +
                        "3. Evite rodar renderização de vídeos pesados ou outros jogos exigentes na mesma máquina enquanto hospeda o servidor."
                },

                "RAM" => new MetricInfo
                {
                    Title = "RAM (Heap da JVM)",
                    TitleWithIcon = "🧠 RAM — Memória Heap da JVM",
                    Summary = "A mesa de trabalho temporária do servidor para manter chunks, entidades e dados ativos.",
                    AccentColor = Color.FromArgb(186, 104, 200),
                    DidacticExplanation =
                        "A memória RAM funciona como a MESA DE TRABALHO do servidor:\n" +
                        "Tudo o que o jogo precisa acessar imediatamente fica espalhado em cima da mesa (blocos carregados, inventários, monstros, jogadores).\n\n" +
                        "É normal e saudável ver o gráfico de RAM subir e descer em padrão de 'dente de serra' (vai enchendo com novos dados e, em seguida, o faxineiro limpa e o uso cai).",
                    GoodRange = new BenchmarkItem("🟢 IDEAL", "40% a 75% em uso",
                        "Espaço suficiente na mesa para trabalhar sem sufoco e sem acumular lixo demais."),
                    MediumRange = new BenchmarkItem("🟡 APERTADO", "75% a 88% em uso",
                        "A mesa está cheia. O coletor de lixo (GC) terá que trabalhar com muito mais frequência para evitar esgotamento."),
                    BadRange = new BenchmarkItem("🔴 CRÍTICO / RISCO DE CRASH", "Acima de 90% contínuo",
                        "Memória no limite absoluto. O servidor pode congelar por vários segundos para tentar liberar bytes ou cair com erro 'OutOfMemoryError'."),
                    BestPractices =
                        "1. O CanalhasSMP está configurado com 8GB (-Xmx8G -Xms8G), quantidade ideal para este modpack.\n" +
                        "2. Cuidado: Dar RAM demais (ex: 24GB para 5 jogadores) é PREJUDICIAL, pois acumula gigabytes de lixo que travam o servidor quando o GC finalmente passa!\n" +
                        "3. Mantenha mods como FerriteCore e ModernFix que compactam estruturas de dados em memória."
                },

                "GC" => new MetricInfo
                {
                    Title = "Garbage Collector (GC)",
                    TitleWithIcon = "🧹 Garbage Collector — Faxina da Memória",
                    Summary = "O coletor automático que joga fora objetos descartados na memória RAM pelo Java.",
                    AccentColor = Color.FromArgb(255, 201, 58),
                    DidacticExplanation =
                        "O Garbage Collector é o 'faxineiro' automático do Java:\n" +
                        "Enquanto os jogadores jogam, milhares de objetos descartáveis são criados a cada segundo (uma flecha disparada, uma gota de chuva, um cálculo de colisão).\n\n" +
                        "O GC joga esses restos fora. Contudo, para fazer uma faxina completa, às vezes ele precisa pedir para o mundo parar ('Stop-the-World pause') por alguns milissegundos. Se a pausa for longa, os jogadores sentem um congelamento súbito!",
                    GoodRange = new BenchmarkItem("🟢 SAUDÁVEL", "Pausas abaixo de 100ms",
                        "A faxina é tão rápida e imperceptível que ninguém nota. O jogo segue liso."),
                    MediumRange = new BenchmarkItem("🟡 MICRO-ENGASGOS", "Pausas de 100ms a 350ms",
                        "Jogadores sentem uma mini-travada rápida ('micro-stutter') quando andam ou quebram blocos."),
                    BadRange = new BenchmarkItem("🔴 CONGELAMENTO NOTÁVEL", "Pausas acima de 500ms ou repetidas",
                        "O servidor literalmente congela no tempo e descongela logo em seguida. Se passar de 60s, o Watchdog encerra o servidor achando que ele travou!"),
                    BestPractices =
                        "1. Já incluímos as melhores flags de inicialização do coletor moderno G1GC (-XX:+UseG1GC, -XX:MaxGCPauseMillis=200, -XX:G1HeapRegionSize=8M).\n" +
                        "2. Não deixe milhares de itens jogados no chão soltos em mob traps sem recolhimento rápido em baús.\n" +
                        "3. Mantenha os parâmetros de memória estáticos (-Xms igual a -Xmx) para evitar redimensionamento dinâmico do heap."
                },

                "PLAYERS" => new MetricInfo
                {
                    Title = "Jogadores Online & Radar",
                    TitleWithIcon = "👥 Jogadores Online & Carga de Mundo",
                    Summary = "População conectada e distribuição de áreas ativas no mapa do Minecraft.",
                    AccentColor = Color.FromArgb(129, 199, 132),
                    DidacticExplanation =
                        "Cada jogador conectado age como uma 'âncora' que força o servidor a manter centenas de chunks carregados ao seu redor (um círculo de 8 a 12 chunks em cada direção).\n\n" +
                        "• 4 jogadores juntos na mesma vila: pesam quase o mesmo que 1 jogador só, pois dividem os mesmos chunks carregados!\n" +
                        "• 4 jogadores espalhados em cantos distantes ou dimensões diferentes (Overworld, Nether, End): quadruplicam o trabalho do processador!",
                    GoodRange = new BenchmarkItem("🟢 BAIXA DISPERSÃO", "Jogadores próximos ou agrupados",
                        "Poucos chunks extras ativos. Menor consumo de CPU e RAM, excelente estabilidade."),
                    MediumRange = new BenchmarkItem("🟡 DISPERSÃO MODERADA", "Jogadores em dimensões diferentes",
                        "O servidor precisa manter loops de tick ativos para o Overworld, Nether e End simultaneamente."),
                    BadRange = new BenchmarkItem("🔴 EXPLORAÇÃO INTENSA", "Voos velozes em direções opostas",
                        "Pior cenário possível: força leitura contínua do disco rígido, descompactação de arquivos de região e geração de terreno ao mesmo tempo!"),
                    BestPractices =
                        "1. Use a aba '🗺️ Mapa & Radar' do Dashboard para ver onde cada jogador está e suas coordenadas exatas em tempo real.\n" +
                        "2. Pré-gere um raio de 5.000 a 10.000 blocos com o mod Chunky para que a exploração não exija geração em tempo real.\n" +
                        "3. Incentive jogadores a desligar farms automáticas ativas quando não estiverem usando."
                },

                "TOKENS" => new MetricInfo
                {
                    Title = "Tokens IA (LLM)",
                    TitleWithIcon = "🤖 Tokens IA — Cota & Consumo de LLM",
                    Summary = "Acompanhamento de orçamento de tokens para a IA do Senhor Cummins e entidades do servidor.",
                    AccentColor = Color.FromArgb(171, 71, 188),
                    DidacticExplanation =
                        "Tokens são as unidades de texto e processamento dos modelos de Inteligência Artificial (Google Gemini e OpenAI):\n\n" +
                        "• Em média, 1 palavra ou termo equivale a ~1.3 tokens (tanto no prompt enviado quanto na resposta gerada).\n" +
                        "• O servidor utiliza modelos de alta velocidade (Gemini 2.0 Flash / Flash-Lite) com cotas gratuitas generosas diárias (1.000.000 a 1.500.000 tokens por ciclo).\n" +
                        "• O Dashboard calcula automaticamente o volume de tokens já utilizados, a quantidade restante e o tempo exato até o próximo reset de cota.",
                    GoodRange = new BenchmarkItem("🟢 COTA COM FOLGA", "Abaixo de 70% consumido",
                        "Consumo equilibrado. O Senhor Cummins e pets podem responder a conversas, comandos de voz e piadas com tranquilidade."),
                    MediumRange = new BenchmarkItem("🟡 ALERTA DE CONSUMO", "70% a 90% consumido",
                        "O uso está elevado para o período atual. Recomenda-se moderação em conversas longas até a renovação da cota."),
                    BadRange = new BenchmarkItem("🔴 COTA QUASE ESGOTADA", "Acima de 90% consumido",
                        "Risco iminente de atingir o teto de requisições ou erro 429. O sistema entrará em modo offline com respostas pré-programadas se esgotar."),
                    BestPractices =
                        "1. O ciclo de renovação pode ser configurado em config/senhor-cummins/config.json ('tokenTracking': {'resetCycle': 'daily', 'tokenLimit': 1000000}).\n" +
                        "2. Ciclos suportados: 'daily' (reseta à meia-noite), 'weekly' (domingo) ou intervalos como '5h', '12h'.\n" +
                        "3. Evite colar textos gigantescos no chat do jogo direcionados ao Senhor Cummins para não consumir milhares de tokens num único prompt.\n" +
                        "4. O mod possui fallback automático para modelos secundários caso o modelo principal sofra lentidão transitória."
                },

                _ => new MetricInfo
                {
                    Title = "Métrica do Servidor",
                    TitleWithIcon = "📊 Métrica do Servidor",
                    Summary = "Monitoramento de desempenho do servidor CanalhasSMP.",
                    AccentColor = Color.FromArgb(78, 201, 176),
                    DidacticExplanation = "Informações em tempo real sobre a saúde e estabilidade do servidor.",
                    GoodRange = new BenchmarkItem("🟢 NORMAL", "Operação padrão", "Sem anomalias detectadas."),
                    MediumRange = new BenchmarkItem("🟡 ALERTA", "Oscilação moderada", "Monitore o comportamento."),
                    BadRange = new BenchmarkItem("🔴 CRÍTICO", "Anomalia alta", "Requer intervenção ou diagnóstico."),
                    BestPractices = "Use o botão '⚡ Spark Profiler' para gerar um diagnóstico completo da CPU e memória."
                }
            };
        }
    }
}
