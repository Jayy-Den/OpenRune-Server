@echo off
rem Headless RSProx session launcher (playtest driver)
set "_JAVA_OPTIONS=-Dsun.java2d.d3d=false"
set "REP=C:\Users\yourname\.rsprox\launcher\repository"
"C:\Program Files\Amazon Corretto\jdk21.0.7_6\bin\java.exe" -cp "%REP%\*;C:\Users\yourname\Documents\OpenRune-Server\.freebuff\driver" Driver tester "changeme" 20 > "C:\Users\yourname\Documents\OpenRune-Server\.freebuff\driver\driver.log" 2>&1
