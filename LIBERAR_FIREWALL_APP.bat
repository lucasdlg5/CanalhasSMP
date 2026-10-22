@echo off
setlocal
title Liberar Conexao do Celular no Firewall

REM Verifica permissao de Administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [FIREWALL] Solicitando permissao de Administrador...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo [FIREWALL] 1. Configurando portas no Firewall do Windows...
netsh advfirewall firewall delete rule name="CanalhasSMP Telemetria HTTP" >nul 2>&1
netsh advfirewall firewall add rule name="CanalhasSMP Telemetria HTTP" dir=in action=allow protocol=TCP localport=25588 profile=any

netsh advfirewall firewall delete rule name="CanalhasSMP Minecraft Server" >nul 2>&1
netsh advfirewall firewall add rule name="CanalhasSMP Minecraft Server" dir=in action=allow protocol=TCP localport=25565 profile=any

echo [FIREWALL] 2. Removendo regras de bloqueio do Java...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetFirewallRule -DisplayName '*Java*' -ErrorAction SilentlyContinue | Where-Object { $_.Action -eq 'Block' -and $_.Direction -eq 'Inbound' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue" >nul 2>&1

echo [FIREWALL] 3. Alterando perfil da rede Wi-Fi para Privada...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetConnectionProfile -ErrorAction SilentlyContinue | Set-NetConnectionProfile -NetworkCategory Private -ErrorAction SilentlyContinue" >nul 2>&1

echo.
echo =====================================================================
echo  [SUCESSO] Portas liberadas e Wi-Fi configurado como Rede Privada!
echo =====================================================================
echo  IP do PC na rede Wi-Fi: 192.168.18.20
echo  Porta da Telemetria:    25588
echo.
echo  Agora:
echo  1. Abra o app no celular (conectado no mesmo Wi-Fi ReznoAsminn).
echo  2. Clique em 'Salvar e Conectar'.
echo =====================================================================
echo.
pause
