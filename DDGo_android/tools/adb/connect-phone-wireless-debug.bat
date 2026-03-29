@echo off
setlocal
set SCRIPT_DIR=%~dp0
if "%~1"=="" (
  powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%connect-wireless-device.ps1" -ExpectedModel "SM-S901N" -FriendlyName "Phone"
) else (
  powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%connect-wireless-device.ps1" -ExpectedModel "SM-S901N" -FriendlyName "Phone" -PreferredEndpoint "%~1"
)
endlocal
