package com.canalhas.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.canalhas.mobile.data.PetLocation
import com.canalhas.mobile.data.PlayerDetail
import java.util.Locale

// Paleta Dark Gamer Oficial (Catppuccin Mocha / Cyberpunk Dark)
private val BgDark = Color(0xFF11111B)
private val CardDark = Color(0xFF1E1E2E)
private val SurfaceDark = Color(0xFF181825)
private val AccentBlue = Color(0xFF89B4FA)
private val AccentGreen = Color(0xFFA6E3A1)
private val AccentYellow = Color(0xFFF9E2AF)
private val AccentRed = Color(0xFFF38BA8)
private val AccentPurple = Color(0xFFCBA6F7)
private val AccentTeal = Color(0xFF94E2D5)
private val TextPrimary = Color(0xFFCDD6F4)
private val TextSecondary = Color(0xFFA6ADC8)

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Painel", Icons.Default.Speed)
    object Mural : Screen("mural", "Mural", Icons.Default.Info)
    object PlayersMap : Screen("map", "Mapa/Radar", Icons.Default.Explore)
    object Chat : Screen("chat", "Chat", Icons.Default.Forum)
    object Console : Screen("console", "Terminal", Icons.Default.Terminal)
    object Config : Screen("config", "Conexão", Icons.Default.SettingsEthernet)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface = CardDark,
                    primary = AccentBlue,
                    secondary = AccentGreen,
                    error = AccentRed,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                CanalhasMobileApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanalhasMobileApp(vm: CanalhasViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Dashboard,
        Screen.Mural,
        Screen.PlayersMap,
        Screen.Chat,
        Screen.Console,
        Screen.Config
    )

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                title = {
                    Column {
                        Text(
                            text = "CanalhasSMP Oficial",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${state.hamachiIp}:${state.port} • Atualizado: ${state.lastUpdatedTime}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (state.isConnected) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isConnected) AccentGreen else AccentRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isConnected) "ONLINE" else "OFFLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isConnected) AccentGreen else AccentRed
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(state, onQuickCommand = { cmd -> vm.sendServerCommand(cmd) })
            }
            composable(Screen.Mural.route) {
                MuralScreen(state, onExecuteCommand = { cmd -> vm.sendServerCommand(cmd) })
            }
            composable(Screen.PlayersMap.route) {
                PlayersMapScreen(state)
            }
            composable(Screen.Chat.route) {
                ChatScreen(state, onSendChat = { msg -> vm.sendServerCommand("say [Mobile] $msg") })
            }
            composable(Screen.Console.route) {
                ConsoleLogsScreen(state, onSendCommand = { cmd -> vm.sendServerCommand(cmd) })
            }
            composable(Screen.Config.route) {
                ConfigScreen(
                    state = state,
                    onSave = { ip, port -> vm.saveConnectionSettings(ip, port) },
                    onQuickCommand = { cmd -> vm.sendServerCommand(cmd) }
                )
            }
        }
    }
}

// ============================================================================
// 1. TELA DE CONFIGURAÇÃO (CONEXÃO WI-FI & RADMIN/HAMACHI)
// ============================================================================
@Composable
fun ConfigScreen(
    state: UiState,
    onSave: (String, String) -> Unit,
    onQuickCommand: (String) -> Unit
) {
    var ipInput by remember(state.hamachiIp) { mutableStateOf(state.hamachiIp) }
    var portInput by remember(state.port) { mutableStateOf(state.port) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Conexão com o Servidor CanalhasSMP", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentBlue)
                Text(
                    "O aplicativo se comunica diretamente via HTTP com o bridge de telemetria e comandos do Senhor Cummins (Porta 25588). Não precisa de senha!",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                // Presets de 1 clique
                Text("Atalhos de Conexão Rápida:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentYellow)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            ipInput = "192.168.18.20"
                            portInput = "25588"
                            onSave(ipInput, portInput)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Wi-Fi Casa (Local)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            ipInput = "26.128.91.185"
                            portInput = "25588"
                            onSave(ipInput, portInput)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Radmin VPN", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("IP do Servidor (ex: 192.168.18.20 ou 26.x.x.x)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Porta Telemetria HTTP (Padrão: 25588)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onSave(ipInput, portInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = BgDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvar e Conectar", fontWeight = FontWeight.Bold)
                }

                if (state.statusBanner != null) {
                    Text(state.statusBanner, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                if (state.lastError != null) {
                    Text("Último erro: ${state.lastError}", color = AccentRed, fontSize = 12.sp)
                }
            }
        }

        // Ações Rápidas de Administração
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ações Rápidas do Servidor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onQuickCommand("save-all") }, modifier = Modifier.weight(1f)) {
                        Text("💾 Salvar Mundo")
                    }
                    OutlinedButton(onClick = { onQuickCommand("cummins backupmods") }, modifier = Modifier.weight(1f)) {
                        Text("📦 Backup Mods")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onQuickCommand("time set day") }, modifier = Modifier.weight(1f)) {
                        Text("☀️ Fazer Dia")
                    }
                    OutlinedButton(onClick = { onQuickCommand("weather clear") }, modifier = Modifier.weight(1f)) {
                        Text("🌦️ Limpar Chuva")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onQuickCommand("spark health") }, modifier = Modifier.weight(1f)) {
                        Text("⚡ Spark Health")
                    }
                    OutlinedButton(onClick = { onQuickCommand("horas") }, modifier = Modifier.weight(1f)) {
                        Text("⏱️ Ranking Horas")
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. TELA DE DESEMPENHO (DASHBOARD COMPLETO ESPELHADO DO DESKTOP)
// ============================================================================
@Composable
fun DashboardScreen(
    state: UiState,
    onQuickCommand: (String) -> Unit
) {
    val t = state.telemetry
    val tpsColor = when {
        t.tps >= 19.5 -> AccentGreen
        t.tps >= 16.0 -> AccentYellow
        else -> AccentRed
    }
    val msptColor = when {
        t.mspt.mean <= 35.0 -> AccentGreen
        t.mspt.mean <= 50.0 -> AccentYellow
        else -> AccentRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Barra Superior de Atalhos Rápidos (Estilo Cabeçalho do Dashboard)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Atalhos do Servidor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionPill("💾 Backup") { onQuickCommand("cummins backupmods") }
                    QuickActionPill("🗺️ Chunky") { onQuickCommand("chunky continue") }
                    QuickActionPill("🐱 Gato TP") { onQuickCommand("cummins tp") }
                    QuickActionPill("⚡ Spark On") { onQuickCommand("spark profiler start") }
                    QuickActionPill("🛑 Spark Off") { onQuickCommand("spark profiler stop") }
                    QuickActionPill("☕ Café") { onQuickCommand("cafe") }
                    QuickActionPill("⏱️ Horas") { onQuickCommand("horas") }
                    QuickActionPill("☀️ Dia") { onQuickCommand("time set day") }
                    QuickActionPill("🌦️ Limpo") { onQuickCommand("weather clear") }
                    QuickActionPill("📦 Malote") { onQuickCommand("malote") }
                }
            }
        }

        // Alerta de Queda de TPS (< 19.8)
        if (state.isConnected && t.tps < 19.8) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.18f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentRed, RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AccentRed)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "ALERTA DE TPS: O servidor está rodando a ${String.format(Locale.ROOT, "%.2f", t.tps)} TPS (abaixo de 20.0)! MSPT Médio: ${t.mspt.mean} ms.",
                        color = AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Alerta de GC
        val activeGcAlert = t.alert ?: t.gc.firstOrNull { it.deltaTimeMs > 100 }?.let {
            "Pausa de GC detectada em ${it.name}: ${it.deltaTimeMs}ms (${it.deltaCount} ciclo)"
        }
        if (activeGcAlert != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentYellow.copy(alpha = 0.16f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentYellow, RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = AccentYellow)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = activeGcAlert,
                        color = AccentYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Cards Principais: TPS e MSPT
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TPS ATUAL", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.ROOT, "%.2f", t.tps),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tpsColor
                    )
                    Text("Meta ideal: 20.00 TPS", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    SparklineGraph(values = state.tpsHistory, maxVal = 20f, lineColor = tpsColor)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MSPT (MÉDIA)", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = "${String.format(Locale.ROOT, "%.1f", t.mspt.mean)} ms",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = msptColor
                    )
                    Text("Teto p/ 20 TPS: 50.0 ms", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    SparklineGraph(values = state.msptHistory, maxVal = 60f, lineColor = msptColor)
                }
            }
        }

        // NOVO: Card de Tokens IA (LLM) - Espelho exato do Dashboard
        val tok = t.tokens
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 Tokens IA (LLM)", fontWeight = FontWeight.Bold, color = AccentTeal, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        if (tok != null) {
                            Text(
                                text = tok.lastModel.replace("-latest", ""),
                                fontSize = 10.sp,
                                color = AccentYellow,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (tok != null) {
                        Text(
                            text = "${formatTokens(tok.used)} / ${formatTokens(tok.limit)} (${String.format(Locale.ROOT, "%.1f", tok.percentUsed)}%)",
                            fontWeight = FontWeight.Bold,
                            color = when {
                                tok.percentUsed >= 90 -> AccentRed
                                tok.percentUsed >= 70 -> AccentYellow
                                else -> AccentTeal
                            },
                            fontSize = 13.sp
                        )
                    }
                }

                if (tok != null) {
                    val progress = (tok.percentUsed.toFloat() / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        color = when {
                            tok.percentUsed >= 90 -> AccentRed
                            tok.percentUsed >= 70 -> AccentYellow
                            else -> AccentTeal
                        },
                        trackColor = SurfaceDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Restam: ${formatTokens(tok.remaining)} (${String.format(Locale.ROOT, "%.1f", tok.percentRemaining)}%)",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Reset em ${tok.resetRemainingFormatted}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentYellow
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Requisições: ${tok.requestCount} • Prompt: ${formatTokens(tok.promptTokens)}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Ciclo: ${tok.resetCycle}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    Text("Aguardando dados de telemetria de tokens...", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // NOVO: Card de Saúde da Internet / Rede do Host (Ping)
        val net = t.network
        if (net != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📶 Conexão & Saúde de Internet (Host)", fontWeight = FontWeight.Bold, color = AccentBlue, fontSize = 14.sp)
                        Text(
                            text = if (net.online) "ONLINE" else "INSTÁVEL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (net.online && net.packetLossPercent5m < 2.0) AccentGreen else AccentRed
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricPill("Ping Atual", "${String.format(Locale.ROOT, "%.0f", net.currentPingMs)} ms", if (net.currentPingMs <= 40) AccentGreen else AccentYellow)
                        MetricPill("Média 5m", "${String.format(Locale.ROOT, "%.1f", net.avgPing5m)} ms", AccentBlue)
                        MetricPill("Perda Pacotes", "${String.format(Locale.ROOT, "%.1f", net.packetLossPercent5m)}%", if (net.packetLossPercent5m > 0) AccentRed else AccentGreen)
                        MetricPill("Quedas (5m)", "${net.outagesLast5m}", if (net.outagesLast5m > 0) AccentRed else TextSecondary)
                    }
                    Text("Alvo: ${net.target} • Diagnóstico: ${net.summary}", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        // Detalhamento MSPT
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Latência Detalhada dos Ticks (MSPT)", fontWeight = FontWeight.Bold, color = AccentBlue)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Mínimo", "${t.mspt.min} ms", AccentGreen)
                    MetricPill("Mediana", "${t.mspt.median} ms", AccentBlue)
                    MetricPill("95% (P95)", "${t.mspt.p95} ms", AccentYellow)
                    MetricPill("Máximo", "${t.mspt.max} ms", if (t.mspt.max > 50) AccentRed else TextPrimary)
                }
            }
        }

        // Consumo de Memória RAM (Heap JVM)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Memória RAM (JVM Heap)", fontWeight = FontWeight.Bold, color = AccentPurple)
                    Text("${t.memory.usedMb} MB / ${t.memory.totalMb} MB (${t.memory.percent}%)", fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = (t.memory.percent.toFloat() / 100f).coerceIn(0f, 1f),
                    color = if (t.memory.percent > 85.0) AccentRed else AccentPurple,
                    trackColor = SurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Text("Alocação Máxima (-Xmx): ${t.memory.maxMb} MB • PID: ${t.pid}", fontSize = 12.sp, color = TextSecondary)
            }
        }

        // Garbage Collector (GC)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletores de Lixo (Garbage Collector)", fontWeight = FontWeight.Bold, color = AccentGreen)
                if (t.gc.isEmpty()) {
                    Text("Aguardando dados de GC...", fontSize = 12.sp, color = TextSecondary)
                } else {
                    t.gc.forEach { gc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(gc.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Ciclos totais: ${gc.count} (Δ +${gc.deltaCount})", fontSize = 11.sp, color = TextSecondary)
                            }
                            Text(
                                text = "Total: ${gc.timeMs}ms (Δ ${gc.deltaTimeMs}ms)",
                                fontSize = 12.sp,
                                color = if (gc.deltaTimeMs > 100) AccentRed else AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionPill(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format(Locale.ROOT, "%.2fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.ROOT, "%.1fk", tokens / 1_000.0)
    else -> tokens.toString()
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SparklineGraph(values: List<Float>, maxVal: Float, lineColor: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(4.dp)
    ) {
        if (values.size < 2) return@Canvas
        val path = Path()
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val normY = (v / maxVal).coerceIn(0f, 1f)
            val y = size.height - (normY * size.height)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
    }
}

// ============================================================================
// 3. TELA DE MURAL DE NOVIDADES (ABA IDÊNTICA AO DASHBOARD)
// ============================================================================
@Composable
fun MuralScreen(
    state: UiState,
    onExecuteCommand: (String) -> Unit
) {
    val mural = state.telemetry.mural
    var selectedFilter by remember { mutableStateOf("novidades") }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabeçalho do Mural
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        mural?.tituloServidor ?: "CanalhasSMP Oficial",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = AccentYellow
                    )
                    Text(
                        text = mural?.versaoMural ?: "v2026-10",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    mural?.subtitulo ?: "Mural de Boas-Vindas & Novidades",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                if (!mural?.mensagemBoasVindas.isNullOrEmpty()) {
                    Text(
                        mural!!.mensagemBoasVindas,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        // Seletor de Categorias (Tabs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTabButton(
                label = "📦 Novidades (${mural?.novidades?.size ?: 0})",
                selected = selectedFilter == "novidades",
                selectedColor = AccentBlue,
                modifier = Modifier.weight(1f)
            ) { selectedFilter = "novidades" }

            FilterTabButton(
                label = "🛠️ Correções (${mural?.correcoes?.size ?: 0})",
                selected = selectedFilter == "correcoes",
                selectedColor = AccentGreen,
                modifier = Modifier.weight(1f)
            ) { selectedFilter = "correcoes" }

            FilterTabButton(
                label = "📜 Comandos (${mural?.comandosUteis?.size ?: 0})",
                selected = selectedFilter == "comandos",
                selectedColor = AccentPurple,
                modifier = Modifier.weight(1f)
            ) { selectedFilter = "comandos" }
        }

        val itemsToShow = when (selectedFilter) {
            "novidades" -> mural?.novidades ?: emptyList()
            "correcoes" -> mural?.correcoes ?: emptyList()
            else -> mural?.comandosUteis ?: emptyList()
        }

        if (itemsToShow.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Conectando ao servidor para carregar o Mural de Novidades...",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(itemsToShow) { itemText ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = itemText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = TextPrimary
                            )
                            if (selectedFilter == "comandos" && itemText.contains("->")) {
                                val cmdPart = itemText.substringBefore("->").trim().split(" ").firstOrNull() ?: ""
                                if (cmdPart.startsWith("/")) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(cmdPart))
                                                onExecuteCommand(cmdPart)
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Executar: $cmdPart", fontSize = 11.sp, color = AccentGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTabButton(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else SurfaceDark,
            contentColor = if (selected) BgDark else TextSecondary
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

// ============================================================================
// 4. TELA DE LOCALIZAÇÃO E MAPA DOS JOGADORES + PETS
// ============================================================================
@Composable
fun PlayersMapScreen(state: UiState) {
    val t = state.telemetry
    val players = t.playerDetails

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Radar 2D Tático em Tempo Real (X, Z) dos Players e Pets
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text(
                    "Radar de Coordenadas (X / Z) • Online: ${t.playerCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
                WorldRadarCanvas(
                    players = players,
                    cummins = t.cummins,
                    vitoria = t.vitoria,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp)
                )
            }
        }

        // Cards dos Pets da IA (Senhor Cummins & Vitória)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            t.cummins?.let { cat ->
                PetLocationCard(pet = cat, emoji = "🐱", tint = AccentYellow, modifier = Modifier.weight(1f))
            }
            t.vitoria?.let { dog ->
                PetLocationCard(pet = dog, emoji = "🐶", tint = AccentPurple, modifier = Modifier.weight(1f))
            }
        }

        Text(
            "Jogadores Online (${players.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary
        )

        if (players.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum jogador online no momento.", color = TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(players, key = { it.uuid.ifEmpty { it.name } }) { p ->
                    PlayerLocationCard(p)
                }
            }
        }
    }
}

@Composable
private fun PetLocationCard(pet: PetLocation, emoji: String, tint: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("$emoji ${pet.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tint)
            Text(formatDimension(pet.dimension), fontSize = 11.sp, color = TextSecondary)
            Text(
                "X: ${pet.x.toInt()} | Y: ${pet.y.toInt()} | Z: ${pet.z.toInt()}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun PlayerLocationCard(p: PlayerDetail) {
    val dimLabel = formatDimension(p.dimension)
    val dimColor = when {
        p.dimension.contains("nether") -> AccentRed
        p.dimension.contains("end") -> AccentPurple
        else -> AccentGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = dimLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = dimColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(dimColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "XYZ: ${p.x} / ${p.y} / ${p.z}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = AccentYellow
                )
                Text(
                    text = "❤️ ${p.health.toInt()}/${p.maxHealth.toInt()} HP",
                    fontSize = 12.sp,
                    color = AccentRed,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "UUID: ${p.uuid}",
                fontSize = 10.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun WorldRadarCanvas(
    players: List<PlayerDetail>,
    cummins: PetLocation?,
    vitoria: PetLocation?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        drawLine(Color.White.copy(alpha = 0.1f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)

        val allX = players.map { it.x } + listOfNotNull(cummins?.x, vitoria?.x)
        val allZ = players.map { it.z } + listOfNotNull(cummins?.z, vitoria?.z)
        if (allX.isEmpty()) return@Canvas

        val avgX = allX.average()
        val avgZ = allZ.average()
        val maxSpan = (allX.maxOrNull()!! - allX.minOrNull()!!)
            .coerceAtLeast(allZ.maxOrNull()!! - allZ.minOrNull()!!)
            .coerceAtLeast(100.0)
        val scale = (minOf(size.width, size.height) * 0.40) / (maxSpan / 2.0)

        cummins?.let { c ->
            val px = (cx + (c.x - avgX) * scale).toFloat().coerceIn(10f, size.width - 10f)
            val pz = (cy + (c.z - avgZ) * scale).toFloat().coerceIn(10f, size.height - 10f)
            drawCircle(AccentYellow, radius = 6.dp.toPx(), center = Offset(px, pz))
        }

        vitoria?.let { v ->
            val px = (cx + (v.x - avgX) * scale).toFloat().coerceIn(10f, size.width - 10f)
            val pz = (cy + (v.z - avgZ) * scale).toFloat().coerceIn(10f, size.height - 10f)
            drawCircle(AccentPurple, radius = 6.dp.toPx(), center = Offset(px, pz))
        }

        players.forEach { p ->
            val px = (cx + (p.x - avgX) * scale).toFloat().coerceIn(10f, size.width - 10f)
            val pz = (cy + (p.z - avgZ) * scale).toFloat().coerceIn(10f, size.height - 10f)
            drawCircle(AccentBlue, radius = 7.dp.toPx(), center = Offset(px, pz))
        }
    }
}

private fun formatDimension(raw: String): String = when {
    raw.contains("overworld") -> "Overworld 🌍"
    raw.contains("the_nether") || raw.contains("nether") -> "Nether 🔥"
    raw.contains("the_end") || raw.contains("end") -> "The End 🐉"
    else -> raw.substringAfter(':')
}

// ============================================================================
// 5. TELA DE CHAT DOS PLAYERS
// ============================================================================
@Composable
fun ChatScreen(
    state: UiState,
    onSendChat: (String) -> Unit
) {
    val messages = state.telemetry.chatMessages
    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            "Chat em Tempo Real do Servidor",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = AccentBlue,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .padding(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Nenhuma mensagem recente no buffer de chat do servidor.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(messages) { msg ->
                    val isCummins = msg.startsWith("Senhor Cummins", ignoreCase = true)
                    val isVitoria = msg.startsWith("Vitória:", ignoreCase = true)
                    val isEvent = msg.startsWith("*")
                    val isConsole = msg.contains("[CONSOLE COMANDO]", ignoreCase = true)
                            || msg.startsWith("[Server]", ignoreCase = true)
                            || msg.startsWith("Server:", ignoreCase = true)

                    val bubbleColor = when {
                        isCummins -> AccentYellow.copy(alpha = 0.14f)
                        isVitoria -> AccentPurple.copy(alpha = 0.14f)
                        isEvent -> AccentGreen.copy(alpha = 0.12f)
                        isConsole -> AccentPurple.copy(alpha = 0.20f)
                        else -> CardDark
                    }
                    val textColor = when {
                        isCummins -> AccentYellow
                        isVitoria -> AccentPurple
                        isEvent -> AccentGreen
                        isConsole -> AccentPurple
                        else -> TextPrimary
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bubbleColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = msg, color = textColor, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                placeholder = { Text("Enviar mensagem no chat do servidor...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (chatInput.isNotBlank()) {
                            onSendChat(chatInput)
                            chatInput = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (chatInput.isNotBlank()) {
                        onSendChat(chatInput)
                        chatInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = BgDark)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar")
            }
        }
    }
}

// ============================================================================
// 6. TELA DE LOGS DO SERVIDOR (CONSOLE ESCURO & TERMINAL)
// ============================================================================
@Composable
fun ConsoleLogsScreen(
    state: UiState,
    onSendCommand: (String) -> Unit
) {
    var commandText by remember { mutableStateOf("") }
    val combinedLogs = remember(state.telemetry.recentLogs, state.localCommandHistory) {
        state.telemetry.recentLogs + state.localCommandHistory
    }
    val listState = rememberLazyListState()

    LaunchedEffect(combinedLogs.size) {
        if (combinedLogs.isNotEmpty()) {
            listState.animateScrollToItem(combinedLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Console do Servidor (logs/latest.log)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AccentGreen
            )
            Text(
                "Endpoint: /command",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B0B12))
                .border(1.dp, Color(0xFF2A2A3C), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            if (combinedLogs.isEmpty()) {
                item {
                    Text(
                        text = "$ Aguardando logs do servidor em ${state.hamachiIp}:${state.port}...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            } else {
                items(combinedLogs) { line ->
                    val logColor = when {
                        line.contains("ERROR") || line.contains("Exception") || line.contains("ERRO:") -> AccentRed
                        line.contains("WARN") -> AccentYellow
                        line.contains("[Cummins]") || line.contains("[Vitória]") -> AccentPurple
                        line.contains("> ") -> AccentBlue
                        else -> Color(0xFFA6E3A1)
                    }
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = logColor
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = { Text("Ex: /say Olá, /time set day, /cummins status...", fontSize = 13.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandText.isNotBlank()) {
                            onSendCommand(commandText)
                            commandText = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    if (commandText.isNotBlank()) {
                        onSendCommand(commandText)
                        commandText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = BgDark),
                modifier = Modifier.height(54.dp)
            ) {
                Text("Enviar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
