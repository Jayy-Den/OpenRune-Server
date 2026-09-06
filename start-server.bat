@echo off
echo ========================================
echo   OpenRune Server Launcher
echo ========================================
echo.
echo Starting HTTP server for jav config...
start /B python "%USERPROFILE%\Documents\OpenRune-Server\start-jav-server.py"
timeout /t 2 /nobreak >nul
echo.
echo Starting game server...
echo Port: 43594
echo Revision: 240.2
echo.
cd /d "%USERPROFILE%\Documents\OpenRune-Server"
gradlew :server:app:run
pause
