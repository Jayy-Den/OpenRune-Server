@echo off
setlocal
title OpenRune Server Launcher

rem Always run from the repo root, regardless of where the bat was invoked from
cd /d "%~dp0"

echo ========================================
echo   OpenRune Server Launcher
echo ========================================
echo.

rem --- Guard: refuse to start a second game server instance ---
netstat -ano | findstr /R /C:":43594 .*LISTENING" >nul
if %errorlevel%==0 (
    echo [ERROR] Port 43594 is already in use - a game server instance is running.
    echo         Starting another one would kill the shared embedded database.
    echo         Run stop-server.bat first if you want a fresh start.
    pause
    exit /b 1
)

rem --- Start jav config server on 8765 only if not already running ---
netstat -ano | findstr /R /C:":8765 .*LISTENING" >nul
if %errorlevel%==0 (
    echo [INFO] Jav config server already running on port 8765 - skipping.
) else (
    echo Starting HTTP server for jav config on port 8765...
    start "OpenRune Jav Server" /MIN python "%~dp0start-jav-server.py"
    timeout /t 2 /nobreak >nul
)

echo.
echo Starting game server...
echo Port: 43594    World: 255    Revision: 240
echo Wait for: OpenRune Server Successfully initialized
echo.

call gradlew.bat :server:app:run

pause
