@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  Bulk Email Pro - Backend Startup
echo ============================================

REM Use Java 17 or 23 (Spring Boot 3.x requires Java 17+)
REM Try common installation paths
SET "JAVA17=C:\Program Files\Java\jdk-17"
SET "JAVA23=C:\Program Files\Java\jdk-23"

IF EXIST "%JAVA23%\bin\java.exe" (
    SET "JAVA_HOME=%JAVA23%"
    echo Using Java 23: %JAVA23%
) ELSE IF EXIST "%JAVA17%\bin\java.exe" (
    SET "JAVA_HOME=%JAVA17%"
    echo Using Java 17: %JAVA17%
) ELSE (
    REM Try current java in PATH
    java -version >nul 2>&1
    IF %errorlevel% neq 0 (
        echo ERROR: Java 17+ not found!
        echo Please install Java 17+ from: https://adoptium.net
        pause
        exit /b 1
    )
    echo WARNING: Using java from PATH. Spring Boot 3.x requires Java 17+.
)

SET "PATH=%JAVA_HOME%\bin;%PATH%"
SET "MVN=%USERPROFILE%\.m2\maven-3.9.6\bin\mvn.cmd"

IF NOT EXIST "%MVN%" (
    echo Downloading Apache Maven 3.9.6...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;(New-Object Net.WebClient).DownloadFile('https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip','%TEMP%\maven.zip')" && powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2' -Force;Rename-Item '%USERPROFILE%\.m2\apache-maven-3.9.6' 'maven-3.9.6' -ErrorAction SilentlyContinue"
)

echo.
echo Starting Spring Boot...
echo URL: http://localhost:8080
echo Press Ctrl+C to stop.
echo.

"%JAVA_HOME%\bin\java.exe" -version 2>&1
echo.

"%MVN%" spring-boot:run -f "%~dp0pom.xml"
