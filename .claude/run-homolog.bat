@echo off
set "TEMP=C:\gtmp"
set "TMP=C:\gtmp"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd /d "%~dp0.."
call .\gradlew.bat bootRun --args="--spring.profiles.active=homolog --server.port=8080"
