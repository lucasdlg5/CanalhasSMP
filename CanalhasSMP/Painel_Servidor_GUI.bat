@echo off
title CanalhasSMP Dashboard
xcopy /Y /D /Q "%~dp0..\CanalhasServerDashboard\bin\Release\net10.0-windows\*" "%~dp0..\CanalhasServerDashboard\publish\" >nul 2>&1
start "" "%~dp0..\CanalhasServerDashboard\publish\CanalhasServerDashboard.exe"
exit
