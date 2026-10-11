@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\update-release.ps1" -GitHubProxy "https://gh-proxy.org/"
if errorlevel 1 echo 更新未完成，请查看上方原因。原存档不会被更新包覆盖。
pause
