@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "server-settings.json" (
  echo Put this updater in your existing server root folder.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\update-release.ps1" -GitHubProxy "https://gh-proxy.org/"
set "update_result=%ERRORLEVEL%"
if not "%update_result%"=="0" echo Update failed. Read the error above.
pause
exit /b %update_result%
