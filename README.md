# 🤖 JARVIS Voice Assistant — BGX Team Edition (Parts 1 to 7)

[![License: TAMCL v1.0](https://img.shields.io/badge/License-TAMCL_v1.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-8.9-brightgreen.svg)](https://gradle.org)

**BGX Team Edition** of the JARVIS Android AI Assistant series — rebranded and maintained by
**Biju (BGX Team)** from the original open project by **Tech GPTx**.

Every part is a self-contained, ready-to-run Android Studio project. The original design,
features and functionality are unchanged — only the branding, creator details and theme
(dark + neon-blue accents) have been updated.

**Creator:** Biju · **Team:** BGX Team
**Telegram:** https://t.me/+yRz0HVoA9HU0YTE1 · **Instagram:** https://www.instagram.com/bijugaming26

---

## 📜 ORIGINAL ATTRIBUTION & LICENSE (MUST READ)

> [!IMPORTANT]
> This project is a rebranded build of the **JARVIS Android AI Assistant** originally created by
> **Tech GPTx** — original source: **https://github.com/techgptx/Playlist**.
>
> The original **TAMCL v1.0** license is preserved in full and is **not** modified. Under that
> license, if you use this source code, fork this repository, or build any video, tutorial, Reel,
> Short, livestream or course based on this project, you MUST give 100% visible and verbal
> credit/shoutout to **Tech GPTx** (the original author):
>
> 1. **In video spoken audio:** *"Credit to Tech GPTx for the original JARVIS AI Android source code / project."*
> 2. **In the description (top 3 lines):** *"Original Project by Tech GPTx: https://github.com/techgptx/Playlist"*
> 3. **On-screen:** a visible watermark or on-screen text crediting **Tech GPTx**.
>
> Read the full legal terms in the [LICENSE](LICENSE) file. Removing attribution or failing to
> provide a shoutout is a direct license violation.

---

## 📲 APK Builds (Parts 1 to 7)

Debug APKs for all seven parts are produced by the GitHub Actions workflow
[`Build BGX JARVIS APKs`](.github/workflows/build-apks.yml) (`.github/workflows/build-apks.yml`).
Run it from the **Actions** tab (or push to `main`) and download the artifacts from the run page.

| Part | Name | Features Highlight |
|---|---|---|
| **Part 1** | JARVIS-Part-1 | Gemini Live Bidi WebSocket, PCM Streaming, 3D Canvas Orb |
| **Part 2** | JARVIS-Part-2 | Vosk Offline Hotword Manager, Foreground Services |
| **Part 3** | JARVIS-Part-3 | Offline Wake Word, Accessibility Engine, Memory Room DB |
| **Part 4** | JARVIS-Part-4 | Ultra Low-Latency Audio Pipeline, Natural Barge-In |
| **Part 5** | JARVIS-Part-5 | WhatsApp & YouTube System Automations, UI Polish |
| **Part 6** | JARVIS-Part-6 | Real-time Live Screen-Sharing Vision & Reasoning |
| **Part 7** | JARVIS-Part-7 | Agentic Workflows, 17 Floating Overlays, Sound FX |

> Note: the debug APKs are signed with the standard Android debug key — installable for testing,
> but not store-ready. For a release build, configure the signing properties described in
> `Part 7/app/build.gradle.kts`.

---

## 📁 Repository Structure

```
JARVIS-BGX/
├── Part 1/    # Foundation: Gemini Live Bidi WebSocket, PCM Audio Streaming & Canvas Orb
├── Part 2/    # Offline Vosk Hotword Manager & Standby Background Service
├── Part 3/    # Accessibility Engine, Automation Commands & Room Memory DB
├── Part 4/    # Low-Latency Audio, Natural Turn-Taking & System Integration
├── Part 5/    # Advanced Accessibility Controls, YouTube/WhatsApp Automations & UI Polish
├── Part 6/    # Screen-Sharing Vision, Real-Time Multimodal Reasoning & Bugfixes
└── Part 7/    # Agentic Workflows, Floating Overlays, Sound FX & Production Ready Engine
```

---

## 🚀 How to Run Any Part

1. **Open in Android Studio**
   - Open Android Studio (Ladybug / Koala or newer recommended).
   - Click **Open** and select the folder of the part you want to test (e.g. `Part 1` or `Part 7`).
2. **Configure Local SDK**
   - Android Studio will create `local.properties` pointing to your Android SDK.
   - From the command line, copy `local.properties.example` to `local.properties` and set your SDK path.
3. **Set the Gemini API Key**
   - Launch the app, open **Settings**, and paste your Google Gemini API key.
4. **Build & Run**
   - Run `./gradlew assembleDebug` or click **Run** in Android Studio.

---

*BGX Team Edition — rebranded & maintained by **Biju (BGX Team)**. Original project by **Tech GPTx**.*
