@echo off
chcp 65001 >nul
title Liberar Conexão do Celular no Firewall

:: Verifica permissão de Administrador; se não tiver, eleva automaticamente
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [FIREWALL] Solicitando permissão de Administrador...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo [FIREWALL] Configurando regras de entrada no Firewall do Windows...
netsh advfirewall firewall delete rule name="CanalhasSMP Telemetria HTTP" >nul 2>&1
netsh advfirewall firewall add rule name="CanalhasSMP Telemetria HTTP" dir=in action=allow protocol=TCP localport=25588 profile=any

netsh advfirewall firewall delete rule name="CanalhasSMP Minecraft Server" >nul 2>&1
netsh advfirewall firewall add rule name="CanalhasSMP Minecraft Server" dir=in action=allow protocol=TCP localport=25565 profile=any

echo.
echo =====================================================================
echo  [SUCESSO] Portas 25588 (App Celular) e 25565 (Jogo) LIBERADAS!
echo =====================================================================
echo  Agora seu celular (192.168.18.37) pode se conectar livremente ao
echo  IP 192.168.18.20 na porta 25588.
echo.
timeout /t 5
