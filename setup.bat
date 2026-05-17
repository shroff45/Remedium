@echo off
REM Remedium Setup Script (Windows)
REM Pushes the Gemma 4 E2B model to a connected Android device

SET MODEL_FILE=gemma-4-E2B-it.litertlm
SET DEVICE_PATH=/data/local/tmp/gemma.litertlm

echo ============================================
echo   Remedium - Gemma 4 E2B Model Setup
echo ============================================
echo.

echo [1/3] Checking ADB connection...
adb devices | findstr /C:"device" >nul 2>&1
if errorlevel 1 (
    echo ERROR: No Android device found.
    echo        Connect phone via USB, enable USB debugging.
    pause
    exit /b 1
)
echo       Device connected.

echo [2/3] Looking for model file...
if not exist "%MODEL_FILE%" (
    echo ERROR: %MODEL_FILE% not found in current directory.
    echo.
    echo Download from: https://github.com/shroff45/Remedium/releases/tag/v1.0
    pause
    exit /b 1
)
echo       Found: %MODEL_FILE%

echo [3/3] Pushing model to device (2-5 min)...
adb push "%MODEL_FILE%" "%DEVICE_PATH%"

echo.
echo ============================================
echo   Setup complete!
echo   Model installed at: %DEVICE_PATH%
echo ============================================
pause