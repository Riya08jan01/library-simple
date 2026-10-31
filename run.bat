@echo off
title LibraSys - Library Management System
cd /d "%~dp0"

echo Stopping any old LibraSys server still holding port 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1

echo Compiling... this can take up to 30 seconds on first run.
javac -encoding UTF-8 LibraryServer.java
if errorlevel 1 (
  echo.
  echo COMPILATION FAILED - make sure Java ^(JDK^) is installed and on PATH.
  pause
  exit /b 1
)

echo Code compiled successfully.
echo.
echo Starting server... open http://localhost:8080 in your browser.
echo.
echo Librarian login:  admin@libra.com / admin123    (http://localhost:8080/admin/login)
echo Reader login:     john@email.com / john123     (http://localhost:8080/reader/login)
echo.
echo KEEP THIS WINDOW OPEN - closing it stops the website.
java LibraryServer
pause
