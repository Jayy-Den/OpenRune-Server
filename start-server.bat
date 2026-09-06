@echo off
echo ========================================
echo   OpenRune Server Launcher
echo ========================================
echo.
echo Starting server...
echo Port: 43594
echo Revision: 240.2
echo.
cd /d "%USERPROFILE%\Documents\OpenRune-Server"
gradlew :server:app:run
pause
