@echo off
echo ========================================
echo   OpenRune Server Launcher
echo ========================================
echo.
echo Checking for existing server...
netstat -an | findstr "43594" | findstr LISTENING >nul 2>&1
if %errorlevel%==0 (
    echo ERROR: Port 43594 is already in use!
    echo Killing existing java processes...
    taskkill /F /IM java.exe 2>nul
    timeout /t 3 /nobreak >nul
)
echo.
echo Starting server...
echo Port: 43594
echo Revision: 240.2
echo.
cd /d "%USERPROFILE%\Documents\OpenRune-Server"
gradlew :server:app:run
pause
