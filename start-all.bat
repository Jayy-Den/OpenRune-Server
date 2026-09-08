@echo off
setlocal
title OpenRune One-Click Launcher

rem ============================================================
rem  Boots the full play stack in order:
rem    1. jav config HTTP server (8765)  - skipped if running
rem    2. game server (43594)            - skipped if running
rem    3. RSProx (with AMD render fix)   - refused if running
rem  See QUICKSTART.md for background on any of these.
rem ============================================================
cd /d "%~dp0"

echo ========================================
echo   OpenRune One-Click Launcher
echo ========================================
echo.

rem --- 1. jav config server ---
netstat -ano | findstr /R /C:":8765 .*LISTENING" >nul
if %errorlevel%==0 goto javup
echo [..] Starting jav config server on 8765...
start "OpenRune Jav Server" /MIN python "%~dp0start-jav-server.py"
timeout /t 2 /nobreak >nul
netstat -ano | findstr /R /C:":8765 .*LISTENING" >nul
if %errorlevel%==0 goto javup
echo [WARN] Jav config server did not come up on 8765 - RSProx will fail to load its target.
goto javdone
:javup
echo [OK] Jav config server running on 8765.
:javdone

rem --- 2. game server ---
netstat -ano | findstr /R /C:":43594 .*LISTENING" >nul
if %errorlevel%==0 goto serverup
echo [..] Starting game server (~35s to initialize)...
echo      A separate window will open; wait for "OpenRune Server Successfully initialized".
start "OpenRune Server" cmd /c "%~dp0start-server.bat"
set /a tries=0
:waitserver
timeout /t 5 /nobreak >nul
netstat -ano | findstr /R /C:":43594 .*LISTENING" >nul
if %errorlevel%==0 goto serverup
set /a tries+=1
if %tries% LSS 24 goto waitserver
echo [ERROR] Game server did not bind 43594 within 2 minutes.
echo         Check the OpenRune Server window for the failure.
pause
exit /b 1
:serverup
echo [OK] Game server running on 43594.

rem --- 3. RSProx ---
echo [..] Launching RSProx (with AMD-safe render fix)...
call "%~dp0start-rsprox.bat"
if errorlevel 1 (
    echo [ERROR] RSProx could not start - see message above.
    pause
    exit /b 1
)
echo.
echo ========================================
echo   All ready. In the RSProx window:
echo     1. Select "OpenRune Server"
echo     2. Click Launch Session
echo ========================================
pause
