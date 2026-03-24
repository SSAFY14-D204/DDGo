@echo off
setlocal
set SCRIPT_DIR=%~dp0
if "%~1"=="" (
  powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%connect-wireless-device.ps1" -ExpectedModel "SM-R900" -FriendlyName "Watch"
) else (
  powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%connect-wireless-device.ps1" -ExpectedModel "SM-R900" -FriendlyName "Watch" -PreferredEndpoint "%~1"
)
endlocal
