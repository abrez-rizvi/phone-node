# PhoneNode // Sovereign Personal Cloud Web UI Frontend

This repository contains the complete static web UI frontend for the **AndroidBot / PhoneNode** sovereign appliance, designed to run directly inside an Android application (`app/src/main/assets/web/`) as well as standalone HTTP/HTTPS web servers.

---

## 📁 Repository Structure

```text
phone-node/
├── web/                               # Complete static bundle for Android WebView
│   ├── index.html                     # Entry point (Unified Single-Page App with Media, Agent, Feed, Console tabs)
│   ├── agent.html                     # Standalone Autonomous Agent Mission interface
│   ├── feed.html                      # Standalone Daily Task & Telemetry Feed
│   ├── phone.html                     # Standalone Nothing Phone (2) Hardware Console
│   ├── css/
│   │   └── style.css                  # Core Nothing OS dark theme, scrollbars, typography, markdown
│   └── js/
│       ├── config.js                  # Dynamic API_BASE resolver for file:///android_asset/ and HTTP
│       ├── marked.min.js              # Offline Markdown parser (zero external CDN needed)
│       └── tailwind.min.js            # Offline Tailwind runtime engine
│
└── app/src/main/assets/web/           # Pre-mirrored exact target path for Android projects
    └── (identical copy of web/ assets ready for immediate Gradle build)
```

---

## 🚀 Key Features

1. **Android Asset Ready (`file:///android_asset/web/`):**
   - Uses relative paths for all assets (`./css/style.css`, `./js/config.js`, `./js/marked.min.js`, `./js/tailwind.min.js`).
   - All internal links between tabs and standalone pages use `./` relative navigation.
   - Zero external CDN dependencies required at runtime — runs 100% offline.

2. **Unified Single-Page App (`index.html`) & Standalone Modes:**
   - `index.html` provides instant tab switching between **Media Vault**, **ZeroClaw Agent**, **Daily Feed**, and **Device Console** without page reloads.
   - Individual standalone pages (`agent.html`, `feed.html`, `phone.html`) are also provided if your Android activity loads specific views directly.

3. **Built-in Backend Configuration (`config.js`):**
   - Automatically detects when running from Android `file:///android_asset/` and directs API traffic to local server `http://localhost:8000` (or custom port).
   - Can be overridden dynamically by Android via `window.PHONENODE_API_BASE = 'http://localhost:8080'` or the in-app **Settings (⚙)** modal.

---

## 🔌 Backend API Specification

The frontend connects to the following endpoints:

| Endpoint | Method | Format | Description |
| :--- | :---: | :---: | :--- |
| `/api/files` | `GET` | JSON | Retrieves list of files and folders in sovereign storage. |
| `/api/stream/{filepath}` | `GET` | Binary Stream | **HTTP 206 Partial Content** range streaming for video and audio playback. |
| `/api/upload` | `POST` | `multipart/form-data` | Uploads new files (`file` and optional `folder`). |
| `/api/agent/run` | `POST` | `application/json` | Dispatches an autonomous mission to the agent runtime. |

### `POST /api/agent/run` Payload & Response Schema

**Request:**
```json
{
  "prompt": "Read quarterly_report.txt and draft executive brief in executive_brief.md",
  "session_id": "session_1795171572420"
}
```

**Response (supports both Android Agent and Python FastAPI schemas):**
```json
{
  "success": true,
  "turnsUsed": 3,
  "finalAnswer": "### Executive Brief\n\n- Zero buffering on HTTP 206 streaming.\n- 92% idle energy reduction...",
  "executionLog": [
    "list_files",
    "read_file (quarterly_report.txt)",
    "write_file (executive_brief.md)"
  ]
}
```

---

## 📱 Android WebView Integration

Copy the `web/` folder directly into your Android project's asset directory:
```bash
# In your Android repository:
cp -r phone-node/web app/src/main/assets/
```

### Kotlin Setup in Android Activity / Fragment

```kotlin
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

val webView: WebView = findViewById(R.id.webView)

webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = true
    allowContentAccess = true
    mediaPlaybackRequiresUserGesture = false
}

webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Optionally inject custom backend port if running on a different port (e.g., 8080)
        // view?.evaluateJavascript("window.PHONENODE_API_BASE = 'http://127.0.0.1:8080';", null)
    }
}

// Load the entry point from assets
webView.loadUrl("file:///android_asset/web/index.html")
```

---

## 📄 License
MIT License — GreenTech 2026 / PhoneNode Sovereign Storage Appliance.
