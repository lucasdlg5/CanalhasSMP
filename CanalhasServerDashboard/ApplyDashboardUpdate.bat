@echo off
chcp 65001 >nul
echo [ATUALIZADOR] Atualizando CanalhasServerDashboard com os novos recursos...

:: Encerra o dashboard aberto para liberar os arquivos bloqueados (o servidor Minecraft continua rodando normalmente)
taskkill /IM CanalhasServerDashboard.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul

:: Copia os arquivos compilados da pasta publish_staged para a pasta publish principal
xcopy /Y /E /I "publish_staged\*" "publish\" >nul

echo [ATUALIZADOR] Dashboard atualizado com sucesso!
echo [ATUALIZADOR] Reiniciando CanalhasServerDashboard...
start "" "publish\CanalhasServerDashboard.exe"
exit
