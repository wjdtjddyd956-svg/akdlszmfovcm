@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0life-tools\Stop-LifeServer.ps1"
if errorlevel 1 pause
