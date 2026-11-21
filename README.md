# PhoneNode // Sovereign Personal Cloud & Autonomous Agent Appliance

[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%28API%2029--35%29-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%201.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Server Engine](https://img.shields.io/badge/Server-Ktor%20CIO%202.3.12-F88900?logo=ktor&logoColor=white)](https://ktor.io)
[![LLM Engine](https://img.shields.io/badge/AI-Groq%20%2F%20GPT--OSS%20120B-F55036)](https://groq.com)
[![Streaming](https://img.shields.io/badge/Streaming-RFC%207233%20HTTP%20206-009688)](#-features)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**PhoneNode** repurposes an idle, retired, or active smartphone into a dual-purpose sovereign edge appliance:
1. **Edge Network-Attached Storage (NAS):** High-throughput embedded HTTP server with RFC 7233 Partial Content (`HTTP 206`) range streaming for media seek, file management, and zero-buffering playback.
2. **Onboard Autonomous ReAct Agent (ZeroClaw):** On-device autonomous reasoning runner powered by Groq LLM function calling (`openai/gpt-oss-120b`). Inspects, summarizes, and edits files directly in phone storage.
3. **Universal Remote Access via Cloudflare Tunnel:** Instant HTTPS connectivity from anywhere in the world across Wi-Fi, 5G, and 4G Mobile Data with zero router port-forwarding or dynamic DNS.
4. **Dual Interface:** Native OLED dark Jetpack Compose mobile client with floating pill navigation, alongside an offline single-page web portal.

---

## 📸 Visual Showcase

<div align="center">
  <table>
    <tr>
      <td width="33%" align="center">
        <b>01. Photos Gallery</b><br>
        <img src="docs/screenshots/01_photos_gallery.png" alt="Photos Gallery" width="260"/>
        <br><i>Dense dark gallery, video badges & month groupings</i>
      </td>
      <td width="33%" align="center">
        <b>02. Albums Library</b><br>
        <img src="docs/screenshots/02_albums_grid.png" alt="Albums Grid" width="260"/>
        <br><i>2-column rounded cards with live preview covers & counts</i>
      </td>
      <td width="33%" align="center">
        <b>03. ZeroClaw Agent Chat</b><br>
        <img src="docs/screenshots/03_agent_chat.png" alt="ZeroClaw Agent Chat" width="260"/>
        <br><i>Conversational ReAct loop with tool step pills & 1-tap chips</i>
      </td>
    </tr>
    <tr>
      <td width="33%" align="center">
        <b>04. Fullscreen Media Viewer</b><br>
        <img src="docs/screenshots/05_media_viewer.png" alt="Fullscreen Media Viewer" width="260"/>
        <br><i>Direct document reader with "Analyze with Agent" button</i>
      </td>
      <td width="33%" align="center">
        <b>05. Node Engine Console</b><br>
        <img src="docs/screenshots/04_node_engine.png" alt="Node Engine Console" width="260"/>
        <br><i>Ktor lifecycle, Network type, LAN & Cloudflare Tunnel URLs</i>
      </td>
      <td width="33%" align="center">
        <b>Web Portal Frontend</b><br>
        <img src="https://raw.githubusercontent.com/abrez-rizvi/phone-node/main/app/src/main/assets/web/index.html" alt="Web Portal" width="260" onerror="this.style.display='none'"/>
        <br><i>Offline Tailwind/Marked web portal served directly by Ktor</i>
      </td>
    </tr>
  </table>
</div>

---

## ⚡ Architecture & Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PHONE NODE APPLIANCE (Android APK)                    │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Jetpack Compose OLED Client                        │  │
│  │   [Photos Gallery]    [Albums Grid]    [Agent Chat]    [Node Engine]  │  │
│  │   Floating Pill Nav • Edge-to-Edge IME Handling • Fullscreen Viewer   │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                      │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │               Embedded Ktor CIO HTTP Engine (Port 8080)               │  │
│  │   - RFC 7233 HTTP 206 Partial Content Streamer (Byte-Range Seeking)    │  │
│  │   - JSON REST API (/api/files, /api/status, /api/agent/run)           │  │
│  │   - Multipart File Uploads & Scoped Storage Manager                   │  │
│  │   - Embedded Web SPA Static Host (src/main/assets/web/)               │  │
│  └──────────────────┬─────────────────────────────────┬──────────────────┘  │
│                     │                                 │                     │
│  ┌──────────────────┴───────────────┐ ┌───────────────┴──────────────────┐  │
│  │    Sandboxed Storage Layer       │ │    ZeroClaw Autonomous Agent     │  │
│  │  /sdcard/Android/data/.../files/ │ │  - ReAct Multi-turn Execution    │  │
│  │  Media/                          │ │  - Tools: list, read, write,     │  │
│  │  - Path Traversal Guard (..)     │ │           search                 │  │
│  │  - Scoped 5 MB Safety Ceilings   │ │  - Model: openai/gpt-oss-120b    │  │
│  └──────────────────────────────────┘ └───────────────┬──────────────────┘  │
└───────────────────────────────────────────────────────┼─────────────────────┘
                                                        │ HTTPS
                                            ┌───────────▼───────────┐
                                            │    Groq Cloud API     │
                                            │  (Fast Function Call) │
                                            └───────────────────────┘
```

### Architectural Highlights
- **Zero Termux / Zero C / Zero Root:** 100% native Kotlin compiled with Gradle. Runs on standard unrooted Android devices (Android 10 through Android 16).
- **RFC 7233 HTTP 206 Partial Content:** Native video seeking without downloading whole files. Supports `bytes=0-` and byte-range requests directly via `call.respondFile()`.
- **Security Sandboxing:** All operations are constrained strictly to app-specific external storage (`/sdcard/Android/data/com.androidbot.node/files/Media/`). Paths containing `..` or escaping root throw immediate security exceptions.
- **Background Retention:** Foreground service (`NodeService`) holds a `PARTIAL_WAKE_LOCK` and high-performance Wi-Fi lock to ensure 24/7 NAS serving even when the screen turns off.

---

## 🌐 Network Routing: Wi-Fi vs. Mobile Data (5G/4G CGNAT)

### The Mobile Data Challenge
When testing on local Wi-Fi, the phone receives a local subnet address (e.g., `192.168.1.63`). However, when switching to **Cellular / Mobile Data (5G/4G)**:
1. **IP Migration:** The Wi-Fi interface (`wlan0`) disconnects, dropping the `192.168.x.x` address.
2. **Carrier-Grade NAT (CGNAT):** Cellular carriers (e.g., Jio 5G, Airtel, Verizon) assign non-routable private addresses (RFC 6598 `100.64.0.0/10` or IPv6 CLAT `192.0.0.2`). Carrier firewalls strictly **drop all unsolicited inbound connections**.
3. **Old Tunnel Flaw:** If Cloudflare was pointed at `http://<WIFI_LAN_IP>:8080`, dropping Wi-Fi resulted in **Error 502 Bad Gateway (Host Error)** because the PC origin could no longer route to the mobile device.

### Dual Deployment Options

#### 1. Standalone On-Device Tunnel (100% Untethered Mobile Data)
The phone runs `cloudflared` (ARM64) natively on-device, establishing an outbound encrypted QUIC tunnel (UDP port 7844) directly to Cloudflare's Anycast edge:
```bash
# Push and execute on-device launcher (zero PC required once running)
adb push start_phone_tunnel.sh /data/local/tmp/
adb shell "chmod 755 /data/local/tmp/start_phone_tunnel.sh && /data/local/tmp/start_phone_tunnel.sh"
```
Because the connection originates *outbound* from the device, it completely circumvents cellular carrier firewalls (CGNAT) on 5G/4G networks with zero router configuration.

#### 2. Development USB Loopback Bridge
When plugged into a development machine:
```bash
# 1. Forward TCP port 8080 over USB / ADB loopback
adb forward tcp:8080 tcp:8080

# 2. Expose local loopback directly via Cloudflare
cloudflared tunnel --url http://127.0.0.1:8080
```

##### Quick Start Scripts Included:
- **Phone Standalone:** `./start_phone_tunnel.sh`
- **Windows Batch Bridge:** Simply double-click `start_tunnel.bat`
- **PowerShell Bridge:** Run `./start_tunnel.ps1`

---

## 🛠 Features

### 1. Photos & Albums Media Vault
- Dense dark OLED gallery with square thumbnails and video play badges.
- Category albums: `Camera`, `Favourite`, `Video`, `Screenshots`, `Documents`, `Agent Briefs`.
- Full-screen media viewer with markdown rendering and instant `"✨ Analyze with Agent"` routing.
- Native file import directly from the device or web portal.

### 2. ZeroClaw Autonomous Agent (ReAct)
- Conversational chat UI with speech bubbles, thinking indicators, and timestamped telemetry.
- Executes multi-turn missions using Groq's high-speed `openai/gpt-oss-120b` engine.
- 4 Sandboxed Tools: `list_files`, `read_file`, `write_file`, `search_content`.
- 1-Tap Quick Action Chips: `[▶ Draft Executive Brief]`, `[▶ Energy Audit]`, `[▶ List Storage Files]`.

### 3. Remote Cloudflare Tunnel Integration
- Connects the mobile node to the global internet via Cloudflare Quick Tunnel.
- Enables public HTTPS access with zero firewall or router configuration.
- In-app editable tunnel URL with 1-click clipboard copy.

---

## 🔌 API Endpoints

The embedded Ktor server exposes the following endpoints on port `8080`:

| Endpoint | Method | Format | Description |
| :--- | :---: | :---: | :--- |
| `/` | `GET` | HTML | Serves the embedded web application frontend. |
| `/api/status` | `GET` | JSON | Returns server uptime, port, and IP address. |
| `/api/files` | `GET` | JSON | Lists files, folders, and storage statistics. |
| `/api/stream/{filepath}` | `GET` | Binary Stream | **HTTP 206 Partial Content** stream with byte-range seek. |
| `/api/upload` | `POST` | `multipart/form-data` | Uploads files to storage from browser. |
| `/api/agent/run` | `POST` | `application/json` | Dispatches an autonomous mission to the ReAct agent. |

### Example Agent Request:
```bash
curl -X POST https://used-arnold-authentication-format.trycloudflare.com/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Read quarterly_report.txt and draft executive brief in executive_brief.md"}'
```

---

## 🚀 Building & Running

### Prerequisites
- JDK 17
- Android SDK Platform Tools (`adb`)
- Android device running Android 10+ (API 29+) with USB Debugging enabled

### 1. Build Standalone APK
```bash
./gradlew assembleDebug
```
Output APK is located at: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Deploy to Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Start Cloudflare Tunnel Bridge
```bash
start_tunnel.bat
```

---

## 🌿 Sustainability & Energy Efficiency

| Metric | Traditional x86 NAS (24/7) | PhoneNode Android Appliance | Savings / Benefit |
| :--- | :---: | :---: | :---: |
| **Idle Power Consumption** | ~45 W – 65 W | **~1.8 W – 3.5 W** | **~92% Energy Reduction** |
| **Annual Electricity Cost** | ~$68.00 / year | **~$3.90 / year** | **~$64.10 Savings / year** |
| **Noise & Acoustics** | Fans + Spinning Disks (~35 dB) | **0 dB (Solid-state, silent)** | Completely noiseless |
| **Power Outage Resilience** | Requires external UPS | **Built-in Battery (~8-12 hours)** | Native UPS built-in |
| **E-Waste Repurposing** | New hardware required | **Repurposes retired smartphones** | Circular electronics economy |

---

## 📄 License

MIT License — GreenTech 2026 / PhoneNode Sovereign Storage Appliance.
