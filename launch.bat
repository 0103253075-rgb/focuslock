@echo off
title FocusLock Studio Launcher
color 0b

cd /d "D:\download 2\FocusLock\server"

echo ========================================================
echo          SHIELD ACTIVE - FOCUSLOCK STUDIO
echo ========================================================
echo.
echo Laptop Wi-Fi IP: 192.168.29.197
echo Opening Cockpit at http://localhost:4000...
echo.

:: Launch Cockpit in browser
start "" http://localhost:4000

:: Start Node.js sync server
node server.js
pause
