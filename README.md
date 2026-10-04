# ⚡ AppPulse
### Intelligent, On-Device Android Health & Resource Impact Manager

<p align="center">
  <img src="screenshots/01_dashboard.png" width="280" alt="AppPulse Dashboard" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="screenshots/02_review_flagged.png" width="280" alt="Review Flagged Apps" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="screenshots/03_app_diagnostics.png" width="280" alt="App Diagnostics" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-purple?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Target_SDK-Android_15_(API_35)-brightgreen?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Privacy-100%25_On--Device-teal?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Architecture-Clean_%2B_Pure_Kotlin_Core-orange?style=for-the-badge" />
</p>

---

## 🌟 Overview

**AppPulse** revolutionizes mobile device maintenance by replacing sketchy "cleaner" gimmicks with **rigorous, deterministic mathematics**, **complete privacy**, and **explainable on-device AI**.

Most phone cleaners push deceptive scare tactics, terminate background tasks indiscriminately, or covertly siphon installed application inventories to remote advertising servers. **AppPulse takes the exact opposite approach**:
- 🔒 **Zero Data Leaves Your Device**: 100% of telemetry, storage statistics, and permissions remain in encrypted on-device SQLite storage.
- 📐 **Deterministic Scoring Engine**: App Health (0–100), Resource Impact (0–100), and Phone Health (0–100) are computed via reproducible mathematical formulas.
- 💡 **Explainable Insights**: No app is flagged without showing the exact point deductions and root causes.
- ⚡ **Non-Destructive Actions**: Provides guided shortcuts to standard Android system sheets rather than forceful, destructive kills.

---

## 📸 Visual Showcase

<div align="center">

| ⚡ Dashboard | 🔍 Review Flagged | 📊 Health Diagnostics |
| :---: | :---: | :---: |
| <img src="screenshots/01_dashboard.png" width="230"/> | <img src="screenshots/02_review_flagged.png" width="230"/> | <img src="screenshots/03_app_diagnostics.png" width="230"/> |

| 📱 All Apps Directory | 💬 Ask AI (NLQ) | 🛡️ Privacy & Settings |
| :---: | :---: | :---: |
| <img src="screenshots/04_all_apps.png" width="230"/> | <img src="screenshots/05_ask_ai.png" width="230"/> | <img src="screenshots/06_settings_privacy.png" width="230"/> |

</div>

---

## 🚀 Core Features

### 1. 🎯 Deterministic Scoring Engine
- **App Health Score ($0 - 100$)**: Subtracts explicit penalties for dormancy ($>90$ days unused $\to -90\text{ pts}$), bloated caches ($>60\%$ cache $\to -15\text{ pts}$), and excessive sensitive permissions.
- **Resource Impact Score ($0 - 100$)**: Multi-factor weighted evaluation across **Security & Privacy** (30%), **Storage Impact** (20%), **Usage Relevance** (15%), **Runtime Stability** (20%), and **Platform Maintenance** (15%).
- **Phone Health Score ($0 - 100$)**: Aggregate composite meter providing an instant health check of your entire device.
- **Dynamic Renormalization**: If platform restrictions or OEM limitations hide an API signal (e.g. `ApplicationExitInfo` prior to API 30), weights automatically rebalance dynamically so scores stay mathematically sound.

### 2. 🗂️ "Fix First" Priority Deck
- Identifies the top applications creating the highest impact or risk.
- Presents a fluid card interface with 1-tap quick actions: **App Info**, **Uninstall**, **Keep App**, or **Ignore**.

### 3. 🧠 Ask AI: Natural Language Query Engine
- Offline semantic intent extractor that allows users to ask natural questions:
  - *"What should I fix first?"*
  - *"Which apps over 500 MB haven't I used in 30 days?"*
  - *"Show apps with sensitive permissions"*
- Zero remote LLM latency. Guaranteed instant answers that strictly match on-device metrics.

### 4. 🔬 Deep Health Diagnostics
- Detailed storage breakdowns: **App Code Size**, **User Data**, and **Temporary Cache**.
- Sensitive permission audit covering SMS, Contacts, Location, Camera, Microphone, and Accessibility services.
- Top 3 point-deduction factors clearly displayed on every app detail screen.

### 5. 🛡️ Standalone APK Scanner
- Inspect downloaded or uninstalled APK files before installation to detect target SDK deprecation, sensitive declared permissions, and unusual manifest flags.

---

## 🏗️ Architecture & Clean Design

AppPulse follows strict Clean Architecture guidelines with two isolated modules:

```
AppPulse
├── :core-scoring     # Pure Kotlin module. Zero Android SDK dependencies.
│                     # Contains HealthScorer, ImpactScorer, PhoneHealthScorer.
└── :app              # Android Jetpack Compose application module.
                      # Contains Room DB, System Collectors, UI Screens, and AI engine.
```

- **MVI / Unidirectional Data Flow**: State is exposed via Kotlin `StateFlow` and consumed seamlessly in Compose.
- **Room SQLite Persistence**: Caches snapshots, score histories, metrics logs, and user dismiss decisions locally.
- **Strict Claims Policy**: UI microcopy is certified non-alarmist. We use *"elevated impact"*, *"review recommended"*, and *"dormant app"* instead of fear-mongering terms like *"virus"*, *"critical danger"*, or *"malware"*.

For complete engineering details and mathematical formulas, see [PROJECT_BRAIN.md](PROJECT_BRAIN.md).

---

## 🛠️ Tech Stack

- **Language:** [Kotlin 2.0.21](https://kotlinlang.org/)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **Local Database:** [Room SQLite 2.6.1](https://developer.android.com/training/data-storage/room)
- **Preferences:** [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- **JSON Engine:** [Google Gson](https://github.com/google/gson)
- **Build System:** Gradle 8.13 with Kotlin DSL & Android Gradle Plugin 8.9.0
- **Testing:** JUnit 4 + Compose UI Testing

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or IntelliJ IDEA
- JDK 17 or higher
- Android SDK with API 35/36 installed

### Build & Run
```bash
# Clone the repository
git clone https://github.com/Bit-manipulators/App_pulse.git
cd App_pulse

# Run scoring engine unit tests
./gradlew :core-scoring:test

# Build the debug APK
./gradlew :app:assembleDebug

# Install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
