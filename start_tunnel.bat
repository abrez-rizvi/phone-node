@echo off
echo ===================================================
echo   PhoneNode - Cloudflare Tunnel Bridge for Android
echo ===================================================
echo.

echo [1/3] Checking ADB device connection...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo Error: ADB not found in PATH. Please ensure Android SDK platform-tools is installed.
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Setting up USB port forwarding (localhost:8080 -^> Phone:8080)...
adb forward tcp:8080 tcp:8080
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to establish adb forward. Make sure your phone is connected with USB Debugging enabled.
    exit /b %ERRORLEVEL%
)
echo Port 8080 forwarded successfully over USB.

echo.
echo [3/3] Launching Cloudflare Tunnel to http://127.0.0.1:8080...
echo (Works across all cellular 5G/4G CGNAT and Wi-Fi networks!)
echo.
cloudflared tunnel --url http://127.0.0.1:8080
