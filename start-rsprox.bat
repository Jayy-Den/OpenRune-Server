@echo off
rem ============================================================
rem  RSProx launcher with AMD-safe rendering settings
rem  (see QUICKSTART.md "Client window is black" for background)
rem
rem  Why this exists: RSProx's bundled RuneLite launcher defaults
rem  to Java2D DirectDraw on Windows (-Dsun.java2d.d3d=true),
rem  which black-screens on some AMD drivers. Setting _JAVA_OPTIONS
rem  here makes the fix permanent: every JVM RSProx spawns (the
rem  proxy GUI and the game client) inherits it, and _JAVA_OPTIONS
rem  overrides command-line -D flags.
rem ============================================================
setlocal

set "_JAVA_OPTIONS=-Dsun.java2d.d3d=false"
set "RS_REPO=%USERPROFILE%\.rsprox\launcher\repository"

set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=C:\Program Files\Amazon Corretto\jdk21.0.7_6\bin\javaw.exe"
if not exist "%JAVA_EXE%" (
    echo Could not find javaw.exe. Edit this script to point JAVA_EXE at your Java 21 install.
    pause
    exit /b 1
)

if not exist "%RS_REPO%\proxy-1.0.5.jar" (
    echo RSProx repository not found at "%RS_REPO%".
    echo Run RSProx once via its own installer/launcher first, then use this script.
    pause
    exit /b 1
)

start "RSProx" "%JAVA_EXE%" -cp "%RS_REPO%\*" net.rsprox.gui.ProxyToolGuiKt
endlocal
