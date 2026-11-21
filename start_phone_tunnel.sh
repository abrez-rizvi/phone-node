#!/system/bin/sh
# PhoneNode - On-Device Cloudflare Tunnel Launcher for Android (ARM64)
# Completely untethered 5G/4G Carrier-Grade NAT (CGNAT) bypass

echo "[1/3] Setting up Android DNS resolver (/sdcard/dns.conf)..."
echo "nameserver 1.1.1.1" > /sdcard/dns.conf
echo "nameserver 8.8.8.8" >> /sdcard/dns.conf

echo "[2/3] Configuring TLS root CA certificates..."
export SSL_CERT_FILE=/sdcard/cacert.pem

echo "[3/3] Launching cloudflared tunnel outbound to 127.0.0.1:8080..."
pkill -f "cloudflared tunnel"
sleep 1
nohup /data/local/tmp/cloudflared tunnel --url http://127.0.0.1:8080 < /dev/null > /data/local/tmp/cf_native.log 2>&1 &
echo "Cloudflare on-device tunnel active. Check log with: cat /data/local/tmp/cf_native.log"
