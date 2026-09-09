@echo off
rem Headless RSProx session launcher (playtest driver)
rem Credentials come from the environment so nothing personal lands in git.
rem Local override example:  set DRIVER_PASS=yoursecret  before launching.
if "%DRIVER_USER%"=="" set "DRIVER_USER=tester"
if "%DRIVER_PASS%"=="" set "DRIVER_PASS=changeme"
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else set "JAVA=java"
set "_JAVA_OPTIONS=-Dsun.java2d.d3d=false"
set "REP=%USERPROFILE%\.rsprox\launcher\repository"
set "DRIVER_DIR=%USERPROFILE%\Documents\OpenRune-Server\.freebuff\driver"
"%JAVA%" -cp "%REP%\*;%DRIVER_DIR%" Driver %DRIVER_USER% "%DRIVER_PASS%" 20 > "%DRIVER_DIR%\driver.log" 2>&1
