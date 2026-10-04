# 🧠 PROJECT BRAIN: AppPulse (App Health & Impact Manager)

Welcome to the internal engineering and architectural blueprint of **AppPulse** — an intelligent, on-device Android application health, resource impact, and hygiene platform.

---

## 1. Vision & Core Philosophy

Traditional device "cleaners" and "security optimizers" have earned a poor reputation on Android due to:
1. **Aggressive, non-actionable alarmism** (e.g., claiming harmless background cache is "malware" or "critical danger").
2. **Aggressive process-killing** (e.g., terminating background services that immediately restart, wasting more battery).
3. **Data harvesting** (e.g., uploading the user's complete installed app inventory to remote advertising servers).

### The AppPulse Tenets:
- **Deterministic Transparency**: Every score (0–100) is governed by an open, reproducible mathematical equation with zero black-box "magic".
- **Absolute Privacy by Default**: 100% of telemetry, storage stats, and permissions reside exclusively in on-device SQLite storage. Zero app lists or identifiers are ever transmitted externally.
- **Explainability**: No app is flagged without displaying its top contributing factors and exact point deductions.
- **Graceful Degradation**: If an OEM or Android version restricts an API (e.g., `ApplicationExitInfo` or `UsageStats`), the engine renormalizes weights automatically rather than reporting misleading zeroes.
- **Strict Claims Policy**: We use factual, non-alarmist microcopy ("elevated impact", "review recommended", "dormant app") rather than sensationalist phrasing ("danger", "virus", "threat").

---

## 2. Mathematical Scoring Engine (`:core-scoring`)

The scoring engine is isolated as a pure Kotlin module with **zero dependencies on Android framework classes**, guaranteeing 100% JVM unit testability.

### 2.1 App Health Score ($H \in [0, 100]$)
Measures the overall hygiene, maintenance, and risk profile of an individual app. Starts at **100** with penalties subtracted:

$$H = \max\left(0, 100 - \sum \text{Penalties}\right)$$

#### Penalty Matrix:
1. **Dormancy Penalties**:
   - $\text{Days Unused} > 90 \implies -90\text{ pts}$ (Severely dormant)
   - $\text{Days Unused} > 60 \implies -60\text{ pts}$
   - $\text{Days Unused} > 30 \implies -30\text{ pts}$
   - $\text{Days Unused} > 14 \implies -10\text{ pts}$
2. **Storage Footprint Penalties**:
   - $\text{Total Size} > 2\text{ GB} \implies -25\text{ pts}$
   - $\text{Total Size} > 1\text{ GB} \implies -15\text{ pts}$
   - $\text{Total Size} > 500\text{ MB} \implies -5\text{ pts}$
   - $\text{Cache Ratio} > 0.6 \text{ and Cache} > 200\text{ MB} \implies -15\text{ pts}$
3. **Sensitive Permissions**:
   - Location (Background / Precise): $-20\text{ pts}$
   - SMS / Call Logs: $-25\text{ pts}$
   - Contacts / Microphone / Camera: $-15\text{ pts}$ each
   - Accessibility / Notification Listener: $-30\text{ pts}$ each
4. **Maintenance & Modernity**:
   - Outdated Target SDK ($\le \text{Android 11}$): $-20\text{ pts}$
   - Legacy Target SDK ($\le \text{Android 12}$): $-10\text{ pts}$

---

### 2.2 App Resource Impact Score ($I \in [0, 100]$)
Calculates real-world drain across 5 normalized dimensions:

$$I = \sum_{k=1}^5 \left( w_k \cdot S_k \right) \quad \text{where } \sum w_k = 1.0$$

| Dimension | Default Weight ($w$) | Metric Inputs |
| :--- | :---: | :--- |
| **Security & Privacy** | $0.30$ | Sensitive granted permissions, background permissions, plausibility mismatches |
| **Storage Impact** | $0.20$ | Code footprint, user data, reviewable temporary cache |
| **Usage Relevance** | $0.15$ | Screen time, foreground launches, days dormant |
| **Runtime Stability** | $0.20$ | Crash frequency, ANR exits, low-memory background kills (`ApplicationExitInfo`) |
| **Platform Maintenance**| $0.15$ | Target SDK level, system support currency |

#### Dynamic Weight Renormalization
When an API signal is unavailable on a specific device (e.g., `ApplicationExitInfo` is absent prior to API 30, or restricted by OEM):

$$w_i' = \frac{w_i}{\sum_{j \in \text{Available}} w_j} \quad \forall i \in \text{Available}$$

---

### 2.3 Aggregate Phone Health Score ($P \in [0, 100]$)
The overarching score displayed on the main dashboard:

$$P = 0.50 \cdot \bar{H}_{\text{apps}} + 0.30 \cdot (100 - \bar{I}_{\text{apps}}) + 0.20 \cdot S_{\text{storage\_avail}}$$

Where:
- $\bar{H}_{\text{apps}}$ is the average health score of all evaluated launchable apps.
- $\bar{I}_{\text{apps}}$ is the average resource impact score across all apps.
- $S_{\text{storage\_avail}} = \min(100, \text{Free Storage \%} \times 2)$.

---

## 3. Data Collection Architecture

```
                  ┌─────────────────────────────────────────┐
                  │          AppPulse System Scanners       │
                  └─────────────────────────────────────────┘
                                       │
     ┌──────────────────┬──────────────┴─────┬──────────────────┐
     ▼                  ▼                    ▼                  ▼
┌──────────────┐ ┌──────────────┐     ┌──────────────┐   ┌──────────────┐
│  Package     │ │  Storage     │     │  UsageStats  │   │  Permission  │
│  Collector   │ │  Stats (OEM) │     │  Manager     │   │  Auditor     │
└──────────────┘ └──────────────┘     └──────────────┘   └──────────────┘
     │                  │                    │                  │
     └──────────────────┼────────────────────┼──────────────────┘
                        ▼                    ▼
                  ┌─────────────────────────────────────────┐
                  │          Room SQLite Database           │
                  │   - AppSnapshots   - ScoreResults       │
                  │   - MetricsLog     - UserDecisions      │
                  └─────────────────────────────────────────┘
```

1. **PackageCollector**: Queries launchable packages via `<queries>` intent filter to avoid full package query flags where possible, extracting Target SDK, version codes, install timestamps, and component counts.
2. **StorageCollector**: Interfaces with `StorageStatsManager` to measure Code Bytes, User Data Bytes, and Cache Bytes.
3. **UsageCollector**: Queries `UsageStatsManager` for 30-day foreground times, last timestamp used, and launch frequencies.
4. **PermissionCollector**: Evaluates declared vs granted runtime permissions, tagging sensitive categories (SMS, Contacts, Camera, Mic, Location, Accessibility).
5. **ExitCollector**: Inspects `ApplicationExitInfo` (Android 11+) to capture tombstone crashes, ANRs, and memory pressure kills.

---

## 4. UI Architecture & Design System

The application is built using **100% Jetpack Compose** with Material 3 theming:

- **Theme Palette**: Deep Space Navy (`#0A0E17`), Surface Blue (`#131B2E`), Electric Cyan (`#00E5FF`), Neon Accent Blue (`#2979FF`).
- **Health Bands**:
  - Healthy ($\ge 80$): Neon Emerald (`#00E676`)
  - Fair ($60 - 79$): Electric Amber (`#FFD600`)
  - Review Recommended ($40 - 59$): Sunset Orange (`#FF9100`)
  - Attention Needed ($< 40$): Coral Red (`#FF5252`)
- **Key Screens**:
  - `DashboardScreen`: Animated circular health meter, storage utilization progress, Attention card, and quick tool grid.
  - `ReviewScreen`: Priority-sorted swipeable card deck presenting individual apps with non-destructive actions.
  - `AppDetailScreen`: Granular telemetry, Top 3 reasons card, AI insights, and permission breakdown.
  - `AllAppsScreen`: Filter chips, dynamic search bar, and sorted listings.
  - `AskAppPulseScreen`: Natural Language Query (NLQ) intent extractor supporting freeform user queries offline.
  - `SettingsScreen`: Privacy controls, offline template engine toggles, and data deletion.
  - `ApkScanScreen`: Standalone APK manifest inspector.

---

## 5. Privacy, AI Guardrails & Claims Validation

AppPulse integrates an **On-Device Natural Language Query & Template Explainer Engine**:
- **Offline Template Engine**: Generates clear, non-alarmist explanations using verified, deterministic parameters without querying remote LLMs.
- **Cloud Proxy Toggle (Optional)**: If cloud AI is enabled by the user, raw identifiers (package names, app labels, user data) are completely redacted. Only abstract numerical vectors are sent.
- **Validation Guardrail (`OutputValidator`)**: Runs a post-processing regex and dictionary check to block any alarmist words ("dangerous", "malware", "virus", "hacked") and ensures all cited numbers match real device values within $\pm 5\%$.

---

## 6. Project Directory Hierarchy

```
AppPulse/
├── core-scoring/                   # Pure Kotlin scoring module (zero Android dependencies)
│   └── src/
│       ├── main/java/com/apppulse/scoring/
│       │   ├── config/ScoringConfig.kt
│       │   ├── engine/HealthScorer.kt
│       │   ├── engine/ImpactScorer.kt
│       │   ├── engine/PhoneHealthScorer.kt
│       │   └── model/ScoringModels.kt
│       └── test/java/com/apppulse/scoring/
│           └── ScoringEngineTest.kt
├── app/                            # Android Jetpack Compose application module
│   └── src/main/java/com/apppulse/app/
│       ├── data/
│       │   ├── collectors/         # System metric collectors
│       │   ├── local/              # Room SQLite database, DAOs, Entities
│       │   └── repository/         # AppPulseRepository
│       ├── domain/
│       │   ├── ai/                 # Template explainer, NLQ engine, Output validator
│       │   ├── ranking/            # FixFirstRanker
│       │   ├── rules/              # Permission plausibility rules
│       │   └── scanner/            # Standalone APK inspector
│       └── ui/
│           ├── components/         # Reusable widgets (HealthScoreRing, Badges, etc.)
│           ├── navigation/         # Compose Navigation host & destinations
│           ├── screens/            # Dashboard, Review, Detail, AllApps, AskAI, Settings
│           └── theme/              # Color, Type, Theme tokens
├── screenshots/                    # High-res device screenshots
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── PROJECT_BRAIN.md
```

---

## 7. On-Demand Diagnostics & Ollama (Qwen 2.5 Coder) Integration

### 7.1 Architecture Shift: Zero-Batch Lag
Rather than running full background evaluations and batch-scoring all 80+ installed applications on startup (which wastes battery and CPU cycles), AppPulse implements an **on-demand selective diagnostic architecture**:
1. **Startup Synchronization**: Only basic package metadata (names, icons, package identifiers) is indexed in milliseconds.
2. **Dashboard Sequence**:
   - **Section 1**: Real-Time Mobile Phone Performance (RAM usage & headroom, Internal Storage, CPU Cores, Device Model, Vitality Score).
   - **Section 2**: Application Access & Sensitive Permissions (Aggregated counts for Location, Camera, Mic, SMS, Contacts).
   - **Section 3**: Performance AI Assistant (Context-aware local Qwen chat).
   - **Section 4**: Choose App for Testing & Analysis (Targeted app picker).
3. **Selective Parameter Isolation**: Users choose exactly which facets to evaluate for the selected app:
   - 🛡️ *Security & Permissions Risk*
   - 💾 *Storage Footprint*
   - ⏱️ *Usage & Inactivity*
   - ⚡ *Stability & Background Impact*

### 7.2 Local Qwen Model via Ollama
- **Local Engine**: Connects to the local host machine running `ollama run qwen2.5-coder:3b`.
- **Zero Cloud Leakage**: Hardware metrics and permissions are processed on the local machine via `http://10.0.2.2:11434` (emulator) or ADB reversed port `11434`.
- **Factual, Structured Generation**: Queries are structured with ground-truth device telemetry to generate objective, 3-part actionable assessments without alarmism or hallucinations.

---

## 8. Authentic Hardware Telemetry Engine (`DevicePerformanceCollector`)

To provide genuine, non-mocked hardware insights, AppPulse executes direct low-level probes without root privileges:
1. **CPU SoC Identification & Clock Frequencies**:
   - Queries `Build.SOC_MODEL` (Android 12+) and parses `/proc/cpuinfo` for authentic chipset identifiers (e.g., MediaTek Dimensity 6300, Qualcomm Snapdragon).
   - Scans `/sys/devices/system/cpu/cpu[0..N]/cpufreq/cpuinfo_max_freq` to compute authentic maximum CPU clock speeds (e.g., 2.40 GHz).
   - Queries display refresh rates via `WindowManager` (e.g., 120Hz, 90Hz, 60Hz).
2. **Authentic Headless GPU Extraction via EGL14**:
   - Creates a temporary, off-screen 1x1 pbuffer surface using `EGL14.eglCreatePbufferSurface`.
   - Queries OpenGL ES directly with `GLES20.glGetString(GLES20.GL_RENDERER)` and `GLES20.GL_VENDOR` (resolving real GPU hardware like `Mali-G57 MC2` or `Adreno (TM) 619`), then cleanly terminates the EGL context.
   - Assesses Vulkan and GLES version capabilities from `ActivityManager.deviceConfigurationInfo`.

---

## 9. Reactive User Decision Loop ("Keep App" & "Ignore")

To ensure user decisions have real, immediate system-wide effects:
1. **Room Persistence**: Tapping "Keep App" writes a `UserDecisionEntity` with decision type `KEEP` (or `IGNORE`) into the SQLite database.
2. **Immediate Deck Elimination**: `ReviewScreen` filters out both `KEEP` and `IGNORE` records from its active `flaggedApps` flow in real time.
3. **Dashboard Reactivity**: `DashboardScreen` observes `decisionsFlow` and excludes all kept/ignored applications from `attentionApps`, immediately decrementing the "Apps Needing Attention" counter without requiring a restart or manual rescan.
4. **Transparent User Feedback**: Emits immediate confirmation toasts (`"[App Name] marked as kept & trusted"`).

---

## 10. 3-Phase On-Demand App Diagnostics (`AppTestScreen`)

Diagnostics follow a structured 3-phase state machine:
```
┌──────────────────┐      Tap Analyze      ┌─────────────────────────────┐      Done      ┌───────────────────────────┐
│ Phase 1: CONFIG  │ ───────────────────>  │ Phase 2: DIAGNOSING         │ ────────────>  │ Phase 3: OUTPUT           │
│ App details,     │                       │ Circular loading ring with  │                │ Dedicated report, dual    │
│ Parameter boxes  │                       │ 4 animated step completion  │                │ health & impact scores,   │
│ (Security, RAM,  │                       │ progress bars (Package,     │                │ real measurements, Qwen   │
│ Storage, Battery)│                       │ Storage, Perms, AI Qwen)    │                │ assessment, action buttons│
└──────────────────┘                       └─────────────────────────────┘                └───────────────────────────┘
```

---

## 11. Universal Real App Icon Architecture (`AppIconImage`)

- **Direct Extraction**: Loads authentic application icons directly from `PackageManager.getApplicationIcon(packageName)` rather than displaying generic placeholder icons.
- **In-Memory BitMap Caching**: Employs an in-memory `ImageBitmap` cache (`iconCache`) to prevent repeated IPC calls to `PackageManager`, ensuring 60fps buttery scrolling across the Dashboard, Top Search, Review Deck, All Apps listing, and Diagnostic reports.
- **Fallback Resilience**: Gracefully renders a themed fallback vector only if an uninstalled or system package fails icon extraction.

---

## 12. Full Interactive Ollama Qwen Chatbot (`AskAppPulseScreen`)

- **Conversational Interface**: Dual-sided chat bubbles (user right-aligned in primary theme, AI left-aligned in surface variant).
- **Telemetry System Prompt Injection**: Automatically injects live phone model, Android version, RAM/Storage headroom, CPU/GPU details, and top impact apps into the conversation prompt.
- **Offline Fallback Intelligence**: If local Ollama is offline or unreachable, seamlessly falls back to the deterministic on-device `NaturalLanguageQueryEngine` without throwing errors or breaking user flow.
- **Prompt Suggestions & History Clearing**: Interactive quick-suggestion chips for common diagnostic inquiries and an instant session reset option.


