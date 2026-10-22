@echo off
echo ========================================================
echo   Atualizando Jacinto Itemakino Bau para a versao 1.8.0
echo ========================================================
echo.
copy /Y "JacintoItemakino\build\libs\jacinto-itemakino-1.8.0.jar" "CanalhasSMP\mods\jacinto-itemakino-1.8.0.jar"
copy /Y "JacintoItemakino\build\libs\jacinto-itemakino-1.8.0.jar" "CanalhasSMP\world\mods_desenvolvidos\jacinto-itemakino-1.8.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.0.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.0.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.0.1.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.0.1.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.1.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.1.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.1.1.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.1.1.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.2.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.2.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.3.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.3.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.4.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.4.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.5.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.5.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.6.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.6.0.jar" 2>nul
if exist "CanalhasSMP\mods\jacinto-itemakino-1.7.0.jar" del /f /q "CanalhasSMP\mods\jacinto-itemakino-1.7.0.jar" 2>nul
echo.
echo Mod atualizado com sucesso na pasta mods do servidor!
echo Reinicie o servidor no painel para carregar as alteracoes.
pause
