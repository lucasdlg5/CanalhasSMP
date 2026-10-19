package com.canalhas.mobile.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CanalhasRepository(context: Context) {

    private val prefs = context.getSharedPreferences("canalhas_server_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    fun getSavedIp(): String = prefs.getString(KEY_HAMACHI_IP, "25.0.0.1") ?: "25.0.0.1"

    fun getSavedPort(): String = prefs.getString(KEY_PORT, "25588") ?: "25588"

    fun saveConnectionConfig(ip: String, port: String) {
        prefs.edit()
            .putString(KEY_HAMACHI_IP, ip.trim())
            .putString(KEY_PORT, port.trim().ifEmpty { "25588" })
            .apply()
    }

    private fun buildBaseUrl(ip: String, port: String): String {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val cleanPort = port.trim().ifEmpty { "25588" }
        return "http://$cleanIp:$cleanPort"
    }

    /**
     * Busca o JSON completo em GET /telemetry
     */
    suspend fun fetchTelemetry(ip: String, port: String): Result<TelemetryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${buildBaseUrl(ip, port)}/telemetry"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Falha ao ler /telemetry")
                }
                val body = response.body?.string() ?: throw IllegalStateException("Resposta vazia do servidor")
                gson.fromJson(body, TelemetryResponse::class.java)
            }
        }
    }

    /**
     * Envia um comando diretamente para a Server Thread do Minecraft via POST /command
     */
    suspend fun sendCommand(ip: String, port: String, command: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCmd = command.trim()
            require(cleanCmd.isNotEmpty()) { "Comando vazio" }

            val url = "${buildBaseUrl(ip, port)}/command"
            val requestBody = cleanCmd.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} ao enviar comando")
                }
                true
            }
        }
    }

    companion object {
        private const val KEY_HAMACHI_IP = "hamachi_ip"
        private const val KEY_PORT = "server_port"
    }
}
