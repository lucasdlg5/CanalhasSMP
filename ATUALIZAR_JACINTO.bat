@echo off
chcp 65001 >nul
echo ========================================================
echo   Atualizando Jacinto Itemakino Baú para a versão 1.3.0
echo ========================================================
echo.
copy /Y "JacintoItemakino\build\libs\jacinto-itemakino-1.3.0.jar" "CanalhasSMP\mods\jacinto-itemakino-1.3.0.jar"
if exist "CanalhasSMP\mods\jacinto-itemakino-1.0.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.0.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.0.1.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.0.1.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.1.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.1.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.1.1.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.1.1.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.2.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.2.0.jar" 2>nul
echo.
echo Mod atualizado com sucesso na pasta mods do servidor!
echo Reinicie o servidor no painel para carregar as alterações.
pause
