@echo off
setlocal
title OpenRune GPU Pin Setup

rem ============================================================
rem  Pins java.exe / javaw.exe to the high-performance GPU via
rem  Windows' per-app GPU preference (Settings > Display > Graphics).
rem  Without this, Windows may route Java to the integrated GPU -
rem  the adapter whose driver caused the RSProx black screens.
rem  Idempotent: safe to run any time. Takes effect for NEW processes.
rem  See QUICKSTART.md "Dedicated GPU (optional)".
rem ============================================================

set "JAVADIR=C:\Program Files\Amazon Corretto\jdk21.0.7_6\bin"
if exist "%JAVA_HOME%\bin\java.exe" set "JAVADIR=%JAVA_HOME%\bin"
if not exist "%JAVADIR%\java.exe" (
    echo [ERROR] Could not find java.exe.
    echo         Set JAVA_HOME or edit JAVADIR in this script.
    pause
    exit /b 1
)

echo Pinning GPU preference for:
echo   %JAVADIR%\java.exe
echo   %JAVADIR%\javaw.exe
echo.

powershell -NoProfile -Command ^
  "$gp = 'HKCU:\Software\Microsoft\DirectX\UserGpuPreferences';" ^
  "if (-not (Test-Path $gp)) { New-Item -Path $gp -Force | Out-Null };" ^
  "Set-ItemProperty -Path $gp -Name ('%JAVADIR%\java.exe')  -Value 'GpuPreference=2;';" ^
  "Set-ItemProperty -Path $gp -Name ('%JAVADIR%\javaw.exe') -Value 'GpuPreference=2;';" ^
  "$v = (Get-ItemProperty -Path $gp).PSObject.Properties | Where-Object { $_.Name -like '*java*.exe' };" ^
  "foreach ($e in $v) { Write-Host ('  [OK] ' + $e.Name + ' = ' + $e.Value) }"

echo.
echo Done. Restart any running RSProx / game client for it to take effect.
pause
