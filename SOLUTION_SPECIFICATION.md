# AndroidBot: System & Technical Specification

**Document Version:** 2.0.0-native  
**Target Platform:** Android 10+ (API Level 29–35), ARM64  
**Development Method:** Headless CLI / GitHub Actions (Zero Android Studio)  
**Tone & Style:** Pragmatic, standard software engineering (no promotional buzzwords)

---

## 1. System Overview & Objectives

AndroidBot repurposes an otherwise retired or idle smartphone into a dual-purpose edge appliance:
1. **Network-Attached Storage (NAS):** A local HTTP server with RFC 7233 Partial Content (HTTP 206) streaming for media, directory management, and browser-based upload/download.
2. **Onboard Autonomous Agent:** A local task runner implementing the ReAct (Reasoning + Acting) pattern. The agent interacts with the file repository using scoped filesystem tools (`list_files`, `read_file`, `write_file`, `search_content`), utilizing external LLM inference via structured function calling.

The primary engineering objective is **architectural simplicity**: eliminating multi-layered wrappers (no Termux dependency, no cross-compiled C/Rust binaries, no JNI bindings) by consolidating all functionality into a single native Android APK written in Kotlin.

---

## 2. Architectural Blueprint

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ANDROID APPLICATION (`AndroidBot.apk`)                │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ USER INTERFACE (Jetpack Compose)                                      │  │
│  │ • Server Lifecycle: [ Start / Stop ]                                  │  │
│  │ • Directory Selector (Target storage path on device)                  │  │
│  │ • Telemetry Panel: Local IP, Port, Active Sockets, Battery, Temp      │  │
│  │ • Embedded Console: Real-time event log & owner chat interface        │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │ Controls                             │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ FOREGROUND SERVICE (`NodeService`)                                    │  │
│  │ Holds partial wake-lock & persistent notification to prevent OS kill │  │
│  │                                                                       │  │
│  │  ┌──────────────────────────────┐   ┌──────────────────────────────┐  │  │
│  │  │ EMBEDDED KTOR HTTP SERVER    │   │ ONBOARD AGENT RUNTIME        │  │  │
│  │  │ • Static assets from assets/ │   │ (Kotlin Coroutines)          │  │  │
│  │  │ • RFC 7233 HTTP 206 Streamer │   │ • API Client (Groq / Gemini) │  │  │
│  │  │ • REST API: /api/files       │   │ • Tool Dispatcher:           │  │  │
│  │  │ • REST API: /api/upload      │   │   - list_files()             │  │  │
│  │  │ • WebSocket: /ws/telemetry   │   │   - read_file()              │  │  │
│  │  │ • Agent Endpoint: /api/agent │   │   - write_file()             │  │  │
│  │  │                              │   │   - search_content()         │  │  │
│  │  └──────────────┬───────────────┘   └──────────────┬───────────────┘  │  │
│  │                 │                                  │                  │  │
│  │                 └─────────────────┬────────────────┘                  │  │
│  │                                   ▼                                   │  │
│  │                     STORAGE ACCESS LAYER                              │  │
│  │                     `/sdcard/AndroidBot/Media/`                       │  │
│  │                     (Scoped sandboxed filesystem)                     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Local Wi-Fi (802.11)
                                       ▼
                       [ CLIENT WEB BROWSER (Laptop / PC) ]
                        • URL: http://<PHONE-LOCAL-IP>:8080
                        • Instant video seek via HTTP 206
                        • File management & agent task submission
```

---

## 3. Component Breakdown

### 3.1 Embedded Ktor HTTP Server
- **Engine:** Ktor Server Netty/CIO engine embedded directly within the Android process.
- **Port:** Default `8080` (configurable).
- **HTTP 206 Partial Content Implementation:**
  - Intercepts `Range: bytes=start-end` headers.
  - Opens target media file via `RandomAccessFile` or NIO `FileChannel`.
  - Seeks directly to the `start` byte offset.
  - Yields chunks of 512 KB to 1 MB with `Content-Range: bytes start-end/total` and HTTP status `206 Partial Content`.
  - Enables sub-second scrubbing on 1080p video files without full-file pre-buffering.
- **REST Endpoints:**
  - `GET /`: Serves static web portal from APK `assets/web/`.
  - `GET /api/files`: Recursively enumerates files, MIME types, sizes, and last modified timestamps.
  - `GET /api/stream/{filepath}`: Dispatches partial content streaming.
  - `POST /api/upload`: Multipart file ingest into the active storage directory.
  - `POST /api/agent/run`: Ingests task prompt from web client and forwards to Agent Runtime.
  - `GET /ws/telemetry`: WebSocket streaming server events and live device metrics.

### 3.2 Onboard Agent Engine
- **Pattern:** ReAct (Reasoning + Acting) loop implemented using Kotlin Coroutines.
- **Inference Delegation:** Calls cloud LLM inference APIs (Groq Llama 3.3 70B or Gemini Flash) via standard HTTPS POST with JSON schema function definitions.
- **Tool Protocol:**
  - `list_files(subfolder: String?)`: Returns file metadata tree.
  - `read_file(filename: String)`: Reads UTF-8 contents of text, markdown, or code files.
  - `write_file(filename: String, content: String)`: Commits generated briefs or structured notes.
  - `search_content(query: String)`: Scans text files in workspace for regex or keyword matches.
- **Sandboxing & Path Traversal Prevention:**
  - All requested file paths are resolved against the canonical base path (`/sdcard/AndroidBot/Media/`).
  - Any path containing `..` or attempting to resolve outside the root throws an immediate security exception.

### 3.3 Foreground Service & Lifecycle Management
- **Service Name:** `NodeService` (extends Android `Service`).
- **Notification:** Persistent low-priority notification showing server status and LAN URL.
- **Power Management:** Acquires a `PARTIAL_WAKE_LOCK` to keep the CPU active while serving streams or running agent loops, even when the screen is turned off.

---

## 4. Headless Build Pipeline (Zero Android Studio)

Development and builds are conducted without the Android Studio IDE:

### Method A: Local Gradle CLI
1. Use standard Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
2. Outputs standalone APK to `app/build/outputs/apk/debug/app-debug.apk`.
3. Deploy directly to device over Wi-Fi or USB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Method B: Automated GitHub Actions Cloud Build
A declarative workflow (`.github/workflows/build.yml`) compiles the APK on a clean Ubuntu runner on every commit:
- Sets up JDK 17.
- Sets up Android SDK command-line tools.
- Executes `./gradlew assembleRelease`.
- Uploads the signed APK artifact for direct phone download.

---

## 5. Security & Containment Architecture

1. **Storage Sandboxing:** The service requests `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (or `MANAGE_EXTERNAL_STORAGE` for Android 11+) strictly scoped to `/sdcard/AndroidBot/`.
2. **No Hardware Sensor Leaks:** The agent toolset does not expose camera, microphone, GPS, contacts, or telephony APIs.
3. **Budget & Rate Limiting:** Agent loops are constrained to a maximum turn depth of 6 turns per mission to prevent runaway API billing or infinite loops.
