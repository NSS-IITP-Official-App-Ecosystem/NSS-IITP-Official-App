# NSS App Showcase Portfolio - Content & Discussion Doc

This document is our central hub. Everything discussed and decided goes here.

---

## ✅ Final Decisions

| # | Decision | Value |
|---|----------|-------|
| 1 | Target Audience | Recruiters, hiring managers, developers |
| 2 | Tech Stack | **Next.js (React)** + CSS Modules + Framer Motion |
| 3 | Design Vibe | Premium dark-mode, glassmorphism, micro-animations |
| 4 | Feature Scope | **Every single feature** (15-20+ pages) |
| 5 | Structure | Multi-page nested routing mirroring app navigation |
| 6 | UX | Interactive prototype with clickable hot-spots on screenshots |
| 7 | Developer Credits | On every feature page, revealed as we build each page |
| 8 | Color Palette | Chosen to complement the app's own UI screenshots |
| 9 | App Name | **NSS IITP Official App** |
| 10 | Hosting | **Firebase Hosting** |
| 11 | Architecture Diagram | ✅ Yes — include it |
| 12 | Code Snippets | ✅ Yes — include key highlights (security rules, scheduling algo, etc.) |
| 13 | Mobile Experience | Responsive: scale everything down to fit on phone screens |

---

## 👥 The Development Team

### Dev 1 — Eshan Bhaskar
- **Photo:** `[PLACEHOLDER — to be provided later]`
- **GitHub:** `[PLACEHOLDER — to be provided later]`
- **LinkedIn:** `[PLACEHOLDER — to be provided later]`

### Dev 2 — Ankesh Kumar
- **Photo:** `[PLACEHOLDER — to be provided later]`
- **GitHub:** `[PLACEHOLDER — to be provided later]`
- **LinkedIn:** `[PLACEHOLDER — to be provided later]`

### Former Dev 3 — Aditya Onam  
*(Initial development team, credited on Home page — name only, no photo)*

### Former Dev 4 — Aditya Gupta  
*(Initial development team, credited on Home page — name only, no photo)*

> **Note:** Feature-level developer attribution (who built what) will be decided page-by-page as we build each section.

---

## 📖 The Story — "Why We Built It" (Home Page Narrative)

*Draft — keeping the raw emotion of the original story:*

---

In his first year at IIT Patna, Eshan found himself deep in the heart of NSS — volunteering, coordinating, and slowly noticing a problem no one was talking about.

Attendance was still being taken on paper — in an *institute of technology*. Proxy was rampant. The Teaching Wing's class scheduling was a mess of spreadsheets and guesswork. 350+ volunteers across 100+ events a semester were being tracked in Excel sheets. There was no way for a student to see their progress, no record of past events, no central place for anything.

*We were engineers. We could fix this.*

In their second year, Eshan and Ankesh — along with Aditya Onam and Aditya Gupta — squeezed into a small room in Aryabhatta Hall. Zero experience. Zero prior knowledge of Android development. Just a big problem and the stubbornness to solve it. They called it **The Phad Project**.

That late-night, from-scratch idea is now the **NSS IITP Official App** — live on the Play Store, used daily by 20+ sub-coordinators and 350+ volunteers.

---

## 🗺️ App Feature Map (Full — Every Feature)

### Entry Point
- Splash Screen
- Login / Authentication (Firebase Auth, role-based routing)
- NSS-or-TeachingWing Selection Screen

### NSS Interface (`/nss`)
1. **Home Feed** — Instagram-style reel & post feed for announcements
2. **QR Attendance System** — Admin creates events, generates QR; students scan to mark attendance. Full proxy prevention.
3. **QR Scanner** — In-app camera scanner
4. **Attendance Results** — Per-event attendance report
5. **NSS Calendar** — Browse and filter NSS events
6. **NSS Profile** — Student profile and personal stats
7. **Events List** — Complete history of events

### Teaching Wing Interface (`/ttw`)
1. **Home Feed** — Posts, reels, updates for the teaching wing
2. **Teaching Calendar** — Full calendar grid with event management, leave applications and approval workflow
3. **Leave Application System** — Apply for class leave, admin approval
4. **Accepted Leaves Viewer** — Track leave status
5. **Scheduling System** — Automated, algorithm-based class schedule generation
6. **TTW Profile** — Teaching profile and stats

### Shared Features (`/shared`)
1. **Private Chat** — Real-time 1-on-1 messaging (Firebase Firestore)
2. **Group Chat** — Multi-user group messaging
3. **Media Sharing** — Image sharing with full-screen viewer
4. **Group Management** — Create/delete groups, manage participants & permissions
5. **Push Notifications** — Firebase Cloud Messaging (FCM)
6. **Event History** — Full searchable past event log

### Security & Architecture (`/engineering`)
1. **Firestore Security Rules** — RBAC (Admin/Student), owner-based permissions
2. **Cloud Functions (Backend)** — Server-side logic and automation
3. **Architecture Diagram** — Full MVVM + Clean Architecture visual
4. **Code Snippets** — Security rules, scheduling algorithm highlights

---

## 📸 Screenshots — How to Share Properly

**Best approach to preserve quality:**

**Option A (Recommended): Use Physical Phone**
1. Open the app on your phone.
2. Take screenshots using your phone's hardware buttons.
3. **Transfer without compression:** Connect phone to PC with USB cable → Copy the raw `.png` files directly from `DCIM/Screenshots` folder. Do NOT share via WhatsApp (it compresses images). Do NOT share via Google Photos download (compresses). Use USB cable or Google Drive upload from phone (downloads original quality).

**Option B: Use Emulator**
1. Run the app on Android Studio emulator.
2. Use Android Studio's built-in screenshot tool (camera icon in the emulator toolbar).
3. It saves as a full-resolution `.png` automatically.
4. This is actually the cleanest option as screenshots are perfectly pixel-sharp with no camera noise.

**Recommended folder:** Drop all screenshots into `showcase/public/images/` in the project. Name them descriptively (e.g., `nss-home-feed.png`, `qr-attendance-admin.png`).

---

## ⏳ Pending Items — Things to Provide Later

| # | Item | Who | Priority |
|---|------|-----|----------|
| 1 | Photo for Eshan Bhaskar | Eshan | Before launch |
| 2 | Photo for Ankesh Kumar | Ankesh | Before launch |
| 3 | ~~Photo for Aditya Onam~~ | — | ❌ Not needed — name only |
| 4 | ~~Photo for Aditya Gupta~~ | — | ❌ Not needed — name only |
| 5 | GitHub link for Eshan | Eshan | Before launch |
| 6 | GitHub link for Ankesh | Eshan | Before launch |
| 7 | LinkedIn link for Eshan | Eshan | Before launch |
| 8 | LinkedIn link for Ankesh | Eshan | Before launch |
| 9 | Play Store link | Eshan | Before launch |
| 10 | GitHub repo link (if public) | Eshan | Before launch |
| 11 | App screenshots (per feature) | Eshan | As we build each page |
| 12 | Developer attribution per feature | Eshan | As we build each page |

---

## 📋 Build Progress — Page-by-Page

| # | Page | Route | Status | Screenshot | Dev Credit |
|---|------|--------|--------|------------|------------|
| 1 | Home / Landing | `/` | ⏳ Up Next | — | All 4 devs |
| 2 | Login System | `/login` | 🔒 Pending | ⏳ Needed | TBD |
| 3 | NSS Hub | `/nss` | 🔒 Pending | ⏳ Needed | TBD |
| 4 | NSS Home Feed | `/nss/home` | 🔒 Pending | ⏳ Needed | TBD |
| 5 | QR Attendance | `/nss/attendance` | 🔒 Pending | ⏳ Needed | TBD |
| 6 | NSS Calendar | `/nss/calendar` | 🔒 Pending | ⏳ Needed | TBD |
| 7 | NSS Profile | `/nss/profile` | 🔒 Pending | ⏳ Needed | TBD |
| 8 | NSS Events | `/nss/events` | 🔒 Pending | ⏳ Needed | TBD |
| 9 | TTW Hub | `/ttw` | 🔒 Pending | ⏳ Needed | TBD |
| 10 | TTW Home Feed | `/ttw/home` | 🔒 Pending | ⏳ Needed | TBD |
| 11 | Teaching Calendar | `/ttw/calendar` | 🔒 Pending | ⏳ Needed | TBD |
| 12 | Leave System | `/ttw/leave` | 🔒 Pending | ⏳ Needed | TBD |
| 13 | Scheduling System | `/ttw/scheduling` | 🔒 Pending | ⏳ Needed | TBD |
| 14 | Private Chat | `/shared/chat` | 🔒 Pending | ⏳ Needed | TBD |
| 15 | Group Chat | `/shared/group-chat` | 🔒 Pending | ⏳ Needed | TBD |
| 16 | Notifications | `/shared/notifications` | 🔒 Pending | ⏳ Needed | TBD |
| 17 | Engineering / Architecture | `/engineering` | 🔒 Pending | — | Both devs |

---

## 💬 Discussion Area

