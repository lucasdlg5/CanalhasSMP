@echo off
title CanalhasSMP Dashboard
xcopy /Y /D /Q "%~dp0CanalhasServerDashboard\bin\Release\net10.0-windows\*" "%~dp0CanalhasServerDashboard\publish\" >nul 2>&1
start "" "%~dp0CanalhasServerDashboard\publish\CanalhasServerDashboard.exe"
exit
