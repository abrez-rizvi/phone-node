# PhoneNode - Cloudflare Tunnel Bridge for Android
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  PhoneNode - Cloudflare Tunnel Bridge for Android" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

Write-Host "`n[1/3] Checking ADB device connection..." -ForegroundColor Yellow
adb devices

Write-Host "`n[2/3] Setting up USB port forwarding (localhost:8080 -> Phone:8080)..." -ForegroundColor Yellow
adb forward tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to establish adb forward. Ensure phone is connected with USB Debugging enabled."
    exit 1
}
Write-Host "Port 8080 forwarded successfully over USB." -ForegroundColor Green

Write-Host "`n[3/3] Launching Cloudflare Tunnel to http://127.0.0.1:8080..." -ForegroundColor Yellow
Write-Host "(Works seamlessly across 5G/4G Mobile Data CGNAT and Wi-Fi networks!)`n" -ForegroundColor Green
cloudflared tunnel --url http://127.0.0.1:8080
