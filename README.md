<div align="center">

# NSS-App — Campus Operations Platform

**A full-stack Android + Web platform for NSS (National Service Scheme) campus management**

[![Android](https://img.shields.io/badge/Android-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Next.js](https://img.shields.io/badge/Web-Next.js-000000?logo=next.js&logoColor=white)](https://nextjs.org/)
[![Version](https://img.shields.io/badge/Version-1.1.3%20(build%2058)-blue)](./app/build.gradle.kts)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange)](https://developer.android.com/topic/architecture)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpack&logoColor=white)](https://developer.android.com/compose)

</div>

---

## 🔍 What is this?

NSS-App is a production-grade campus management system used for real-world NSS operations. It covers the full lifecycle of a volunteer programme — from onboarding and messaging to event attendance with hardware-level security, and ends with an automated teaching assignment scheduler backed by a custom constraint-satisfaction algorithm.

The project spans:
- **185+ Kotlin source files** across a clean MVVM + feature-module architecture
- **Firebase Cloud Functions** (Node.js) with Play Integrity API integration
- **Python admin toolkit** with 17 scripts for Firestore data management
- **Next.js public website** deployed on Vercel
- **Serverless FCM backend** deployed independently

---

## ✨ Feature Highlights

### 📱 Real-Time Messaging
- Private 1-to-1 and group chat with `Firebase Realtime Database`
- Media sharing: images, documents via **Cloudinary**
- Push notifications via FCM + a standalone **Vercel serverless backend**
- Read receipts and typing indicators

### 🔐 Hardware-Bound Attendance (QR)
A multi-layer anti-spoofing pipeline for QR-code based event attendance:

| Layer | Mechanism |
|---|---|
| **App Attestation** | Google Play Integrity API (server-side decode) |
| **Device Binding** | Asymmetric key pair (EC), public key stored in Firestore |
| **Challenge-Response** | ECDSA nonce signature verified in Cloud Function |
| **Emulator Block** | `VIRTUAL_DEVICE` verdict check |
| **Location Stamp** | GPS coordinates attached to every scan |

Attendance records trigger Cloud Functions that atomically update user hour totals, per-semester breakdowns, and global event counters.

### 📅 Calendar & Scheduling Module
- Full calendar UI with event filtering by type/status
- Leave application management with role-based approval
- Teaching slot presets per school

### 🧠 Auto-Scheduling Algorithm (`TFV` — Total Free Volunteers)
A custom greedy-with-backtracking scheduler for assigning volunteers to teaching slots. See [`SCHEDULING_ALGORITHM.md`](./SCHEDULING_ALGORITHM.md) for the full design.

```
Key constraints solved:
✔ Max 2 classes per volunteer per day
✔ Same-school + ≤20 min adjacency rule for double-slots
✔ Subject priority rounds (1st pref → 2nd pref → ...)
✔ Subject-per-day uniqueness per school
✔ "Hardest First" (lowest TFV) slot ordering to avoid deadlocks
✔ TFV recalculation after every assignment (live feedback loop)
```

### 👥 User & Role Management
- Role-based access: **Student / Admin / Super Admin**
- Firebase Auth + institutional email binding
- Device identity verification via public-key fingerprint
- FAQ system with Admin CMS (Jetpack Compose)

### 🏠 Home Feed
- Reel-style post viewer (similar to Instagram Stories)
- Text and image post creation by admins
- In-app update prompts via Play Core API

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9, JavaScript (Node.js) |
| Android UI | Jetpack Compose + XML (ViewBinding) |
| Architecture | MVVM, Repository pattern, Coroutines + Flow |
| Backend | Firebase Firestore, Realtime DB, Auth, Cloud Functions, FCM |
| Media | Cloudinary (uploads), ExoPlayer (splash video) |
| Security | Play Integrity API, ECDSA device binding, reCAPTCHA |
| QR | ZXing + ML Kit Barcode Scanning |
| Camera | CameraX |
| Export | Apache POI (Excel), iText 7 (PDF) |
| Web | Next.js 14 (TypeScript), deployed on Vercel |
| Python Admin | 17 scripts — Firestore queries, attendance analytics, schedule export |
| Build | Gradle KTS, 16 KB page-size aligned NDK |

---

## 🏗️ Project Structure

```
NSS-App/
├── app/src/main/java/com/phad/chatapp/
│   ├── activities/          # Legacy Activity screens (Chat, Login, etc.)
│   ├── adapters/            # RecyclerView adapters (11 adapters)
│   ├── features/
│   │   ├── calendar/        # Calendar module (MVVM, Repository, Compose UI)
│   │   ├── events/          # Event history
│   │   ├── home/faqs/       # FAQ CMS (full Admin + User Compose screens)
│   │   └── scheduling/      # Scheduling module (20+ screens, TFV algorithm)
│   │       └── schedule/    # AlgorithmLogComponents, TFVGridView, etc.
│   ├── fragments/           # Navigation fragments (Chat, NSS Home, QR, Profile)
│   ├── models/              # Data models (15 Kotlin data classes)
│   ├── network/             # BackendApi (FCM Vercel endpoint)
│   ├── repositories/        # Data layer (6 repositories)
│   ├── security/            # SecurityKeyManager (ECDSA key generation/storage)
│   ├── services/            # Background services (Location, QR Attendance)
│   ├── ui/                  # Compose screens (Home feed, Profile, Chat, Events)
│   ├── utils/               # 20+ utility classes
│   └── viewmodels/          # ViewModels per feature
│
├── functions/               # Firebase Cloud Functions (Node.js)
│   └── index.js             # Play Integrity verify, device bind, markAttendance
│
├── web/                     # Next.js public website (Vercel)
│
├── scripts/                 # Python admin utilities (17 scripts)
│   ├── check_event_attendance.py
│   ├── export_schedule.py
│   ├── group_attendees_by_time.py
│   ├── preference_matrix.py
│   └── ... (12 more)
│
├── SCHEDULING_ALGORITHM.md  # Full algorithm design doc
├── firestore.rules          # Firestore security rules
└── firestore.indexes.json   # Composite index definitions
```

---

## 🚀 Getting Started

### Android App

1. **Clone the repo:**
   ```bash
   git clone https://github.com/EshanBhaskar/NSS-App.git
   cd NSS-App
   ```

2. **Firebase setup:**
   - Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
   - Enable: **Authentication**, **Firestore**, **Realtime Database**, **Cloud Messaging**, **Cloud Functions**
   - Download `google-services.json` → place in `app/`

3. **Local secrets (`local.properties`):**
   ```properties
   cloudinary.cloud_name=YOUR_CLOUD_NAME
   cloudinary.api_key=YOUR_API_KEY
   cloudinary.api_secret=YOUR_API_SECRET
   key.store=app/campus-code-0.jks
   key.store.password=...
   key.alias=...
   key.alias.password=...
   ```

4. **Build & Run** in Android Studio (Hedgehog or later), targeting SDK 35.

### Cloud Functions

```bash
cd functions
npm install
firebase deploy --only functions
```

### Python Admin Scripts

```bash
python -m venv .venv
.venv\Scripts\activate
pip install firebase-admin
python scripts/export_schedule.py
```

---

## 📐 Architecture Decision Notes

- **Why both Compose and XML?** The project migrated progressively. Legacy screens remain in XML with ViewBinding; all new features (Scheduling, FAQ, Profile) are 100% Compose.
- **Why device binding over simple QR?** Plain QR codes can be screenshotted and reused. The ECDSA challenge-response with Play Integrity makes attendance unforgeable even for rooted devices.
- **Why a custom scheduling algorithm?** Off-the-shelf schedulers don't handle the specific constraints (adjacency, subject-per-day uniqueness, preference rounds). The TFV-based greedy approach guarantees the hardest slots are filled first, preventing deadlocks.
- **Why Python scripts?** Firestore's console has no bulk query/export capability. The scripts fill that gap for admins without code access.

---

## 📊 By the Numbers

| Metric | Count |
|---|---|
| Kotlin source files | 185+ |
| Jetpack Compose screens | 20+ |
| Firebase Cloud Functions | 8 |
| Python admin scripts | 17 |
| App version builds | 58 |
| minSdk / targetSdk | 26 / 35 |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">
Built for the NSS unit of a technical campus. Developed with ❤️ by <a href="https://github.com/EshanBhaskar">Eshan Bhaskar</a>.
</div>
