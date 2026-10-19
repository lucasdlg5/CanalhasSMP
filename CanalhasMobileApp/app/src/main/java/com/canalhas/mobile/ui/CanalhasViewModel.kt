package com.canalhas.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canalhas.mobile.data.CanalhasRepository
import com.canalhas.mobile.data.TelemetryResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val hamachiIp: String = "25.0.0.1",
    val port: String = "25588",
    val isConnected: Boolean = false,
    val lastError: String? = null,
    val lastUpdatedTime: String = "--:--:--",
    val telemetry: TelemetryResponse = TelemetryResponse(),
    val tpsHistory: List<Float> = emptyList(),
    val msptHistory: List<Float> = emptyList(),
    val localCommandHistory: List<String> = emptyList(),
    val statusBanner: String? = null
)

class CanalhasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CanalhasRepository(application.applicationContext)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _uiState = MutableStateFlow(
        UiState(
            hamachiIp = repository.getSavedIp(),
            port = repository.getSavedPort()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        startContinuousPolling()
    }

    /**
     * Loop de polling contínuo a cada 2 segundos via Coroutines
     */
    private fun startContinuousPolling() {
        viewModelScope.launch {
            while (isActive) {
                val current = _uiState.value
                val result = repository.fetchTelemetry(current.hamachiIp, current.port)

                result.onSuccess { data ->
                    val nowStr = timeFmt.format(Date())
                    _uiState.update { prev ->
                        val updatedTps = (prev.tpsHistory + data.tps.toFloat()).takeLast(30)
                        val updatedMspt = (prev.msptHistory + data.mspt.mean.toFloat()).takeLast(30)
                        prev.copy(
                            isConnected = true,
                            lastError = null,
                            lastUpdatedTime = nowStr,
                            telemetry = data,
                            tpsHistory = updatedTps,
                            msptHistory = updatedMspt
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { prev ->
                        prev.copy(
                            isConnected = false,
                            lastError = err.message ?: "Sem conexão com ${prev.hamachiIp}:${prev.port}"
                        )
                    }
                }

                delay(2000L) // Atualiza a cada 2 segundos
            }
        }
    }

    fun saveConnectionSettings(newIp: String, newPort: String) {
        val cleanIp = newIp.trim()
        val cleanPort = newPort.trim().ifEmpty { "25588" }
        repository.saveConnectionConfig(cleanIp, cleanPort)
        _uiState.update {
            it.copy(
                hamachiIp = cleanIp,
                port = cleanPort,
                statusBanner = "Configuração salva: $cleanIp:$cleanPort"
            )
        }
    }

    fun sendServerCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return

        viewModelScope.launch {
            val state = _uiState.value
            val timestamp = timeFmt.format(Date())
            val res = repository.sendCommand(state.hamachiIp, state.port, cmd)

            res.onSuccess {
                _uiState.update { prev ->
                    prev.copy(
                        localCommandHistory = (prev.localCommandHistory + "[$timestamp] > $cmd (Enviado ✅)").takeLast(50),
                        statusBanner = "Comando enviado: $cmd"
                    )
                }
            }.onFailure { err ->
                _uiState.update { prev ->
                    prev.copy(
                        localCommandHistory = (prev.localCommandHistory + "[$timestamp] > $cmd (ERRO: ${err.message})").takeLast(50),
                        statusBanner = "Erro ao enviar comando: ${err.message}"
                    )
                }
            }
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
