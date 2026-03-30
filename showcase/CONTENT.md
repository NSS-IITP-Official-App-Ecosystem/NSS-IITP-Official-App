# NSS App Showcase Portfolio - Content & Design System

This document serves as the central hub for the NSS IITP Official App showcase site. It contains the established design rules, the interactive walkthrough screen map, and content for every screen.

---

## 🎨 Design System & UI Replicability

To ensure all future pages (NSS Hub, TTW Hub, etc.) look consistent with the completed Home Page, replicate these established UI patterns:

### 1. Colors & Theme
- **Background:** Dark mode base `#0a0a0f` with deep radial gradients (`#1a1a2e`, `#16213e`).
- **Accent Theme:** Neon Cyberpunk (Cyan & Blue).
  - Cyan: `#66fcf1`
  - Blue: `#45a29e`
  - Purple/Teal: `#6f00ff` to `#00f2fe` for specific gradients.
- **Text:** 
  - Primary (Headings): Pure White `#ffffff`
  - Secondary (Body): Light Gray `#c4c4c4`
  - Brand Highlights: Gradients on text using `background: linear-gradient()`, `background-clip: text`.

### 2. Glassmorphism & Cards
All cards and feature containers should use the following glass effect:
- **Background:** `rgba(255, 255, 255, 0.03)`
- **Border:** `1px solid rgba(255, 255, 255, 0.05)`
- **Backdrop Blur:** `backdrop-filter: blur(10px)`
- **Hover Reveal:** Add a radial gradient radial mask on mouse movement (implemented via Framer Motion/CSS) and elevate with `transform: translateY(-5px)`.

### 3. Animations & Motion (Framer Motion)
- **fadeUp:** Standard entrance for text blocks. `y: 40`, `opacity: 0` -> `y: 0`, `opacity: 1` over `0.8s`.
- **slideIn:** Used for images or side elements sliding from left/right.
- **Button Pulse:** `.btn_explore` uses a continuous breathing `transform: scale(1.05)` and `box-shadow` shift.
- **Shine Effect:** Skewed white pseudo-element sliding across buttons continuously (`animation: shine 4s infinite 1s`).
- **Interactive Hover:** Buttons should scale up slightly (`1.05`) and icons inside buttons (like `->`) should transform right `translateX(6px)`.

---

## ✅ Completed Milestones

### 1. The Home Page (`/`) - **COMPLETED**
- **Hero Section:** Animated 3D phone mockup layout, glowing primary CTA, Play Store badge header, and tech stack tags.
- **The Story ("Why We Built It"):** 2-column layout emphasizing the origin story.
- **The Core Team:** Grid layout for the two lead developers using `react-parallax-tilt` and interactive github/linkedin badges.
- **Acknowledgements:** Clean, focused credit section honoring foundational developers (Aditya Onam & Aditya Gupta), with final "Built by Eshan Bhaskar" attribution in the footer.

---

## 🗺️ Interactive Walkthrough — `/explore`

### Concept
When the user clicks **"Explore Features"**, they enter an interactive guided walkthrough. A phone frame sits **center stage** on the screen, flanked by content panels on both sides. The phone displays real app screenshots with **subtly highlighted clickable hotspots** on interactive buttons. Clicking a hotspot transitions the phone to a new screen and updates the surrounding content — simulating the actual app experience.

### Layout: Option A — Phone Center Stage
```
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│  ┌─────────────────┐    ┌──────────┐    ┌─────────────────┐      │
│  │  LEFT PANEL     │    │          │    │  RIGHT PANEL    │      │
│  │                 │    │  📱      │    │                 │      │
│  │  Feature Title  │    │  PHONE   │    │  Tech Tags      │      │
│  │  (gradient)     │    │  FRAME   │    │  Built-By Badge │      │
│  │                 │    │          │    │  Why We Made It │      │
│  │  Hook Line      │    │  Screen  │    │  (2 sentences)  │      │
│  │                 │    │  shot +  │    │                 │      │
│  │                 │    │  hotspots│    │                 │      │
│  └─────────────────┘    │          │    └─────────────────┘      │
│                         └──────────┘                              │
│                         [← Back breadcrumb]                       │
└───────────────────────────────────────────────────────────────────┘
```

### Experience Flow
1. User clicks "Explore Features" on home page → navigates to `/explore`
2. Phone drops in with entrance animation, showing **First Screen** (login)
3. Two buttons on the screenshot glow subtly: "Log In As User" and "Log In As Admin"
4. User clicks a hotspot → phone screen slides to the next screen, content panels transition
5. A mini breadcrumb above the phone tracks navigation: `Login → NSS Home → Calendar`
6. "Back" lets you retrace steps

### Design Rules for `/explore`
- **Phone:** Centered, dominant, slightly floating with ambient glow
- **Left Panel:** Feature title (big gradient text) + 1-line hook
- **Right Panel:** Tech tags (chips/pills) + built-by badge + 2-sentence "why"
- **Hotspots:** NOT dimming the rest — screenshot looks normal, but clickable areas have a subtle pulsing cyan border/glow overlay
- **Background:** Same aurora/particle system from homepage
- **Transitions:** Phone screen slides left/right, content panels fade+slide in

---

## 📱 Screen Map & Content

All screenshots stored in: `showcase/assets/screenshot/`

### Screen 0: First Screen (Login / Landing)

| Field | Content |
|-------|---------|
| **Screenshot** | `First Screen.png` ✅ PROVIDED |
| **Feature Title** | Welcome Gate |
| **Hook Line** | "Two doors. Two worlds. One mission." |
| **Tech Tags** | `🔐 Firebase Auth` · `🎨 Jetpack Compose` · `👤 Role-Based Access` |
| **Built By** | Both |
| **Why** | The app serves two completely different user groups — NSS volunteers and admins. A clean role-selection gateway ensures the right people see the right features from the very first tap. |
| **Hotspots** | "Log In As User" → Screen 1 (NSS Home), "Log In As Admin" → Screen 1-admin (NSS Home Admin view) |

---

### Screen 1: NSS Home Feed

| Field | Content |
|-------|---------|
| **Screenshot** | `nss-home.png` ⏳ NEEDED |
| **Feature Title** | Event Feed |
| **Hook Line** | "Every event, every update. Zero Excel." |
| **Tech Tags** | `📡 Firestore Realtime` · `🔄 Pull-to-Refresh` · `📋 Lazy Lists` |
| **Built By** | Eshan |
| **Why** | Volunteers had no centralized way to see upcoming events. They relied on WhatsApp forwards and word of mouth. This feed gives them a single source of truth — live, always updated, and impossible to miss. |
| **Hotspots** | Bottom nav: Calendar, QR Attendance, Profile |

---

### Screen 2: NSS Calendar

| Field | Content |
|-------|---------|
| **Screenshot** | `nss-calendar.png` ⏳ NEEDED |
| **Feature Title** | Your Hours |
| **Hook Line** | "Every voluntary hour, automatically tracked." |
| **Tech Tags** | `📊 Hour Analytics` · `🗓️ Custom Calendar UI` · `✅ Verified Records` |
| **Built By** | Eshan |
| **Why** | Hours were previously tracked in error-prone spreadsheets that nobody trusted. This calendar gives each volunteer a tamper-proof record of their service hours tied directly to verified QR attendance. |
| **Hotspots** | Events within calendar, bottom nav buttons |

---

### Screen 3: NSS QR Attendance (Admin)

| Field | Content |
|-------|---------|
| **Screenshot** | `nss-qr-admin.png` ⏳ NEEDED |
| **Feature Title** | Proxy Killer |
| **Hook Line** | "No phone? No attendance. That simple." |
| **Tech Tags** | `📷 QR Generation` · `⏱️ Time-Locked Codes` · `🔐 Encrypted Payload` |
| **Built By** | Eshan |
| **Why** | Paper registers allowed rampant proxy attendance — friends signing in for absent volunteers. QR codes are generated live by admins, time-locked, and can only be scanned in person. Proxy era ended. |
| **Hotspots** | Generate QR button, back nav |

---

### Screen 4: NSS QR Scan (Student)

| Field | Content |
|-------|---------|
| **Screenshot** | `nss-qr-student.png` ⏳ NEEDED |
| **Feature Title** | Scan & Go |
| **Hook Line** | "Point. Scan. Done in under 200ms." |
| **Tech Tags** | `📷 CameraX` · `⚡ ZXing Decode` · `🔒 One-Time Use` |
| **Built By** | Eshan |
| **Why** | Students needed a frictionless way to mark attendance. Open the app, point at the QR, and you're verified — faster than raising your hand in a crowd of 350. |
| **Hotspots** | Camera viewfinder area, back nav |

---

### Screen 5: NSS Profile

| Field | Content |
|-------|---------|
| **Screenshot** | `nss-profile.png` ⏳ NEEDED |
| **Feature Title** | Your Journey |
| **Hook Line** | "Every event attended. Every hour earned." |
| **Tech Tags** | `👤 User Profile` · `🕐 Hours Tracker` · `📜 Event History` |
| **Built By** | Both |
| **Why** | Volunteers needed a personal dashboard showing their complete NSS journey — total hours, events attended, and their standing. It's proof of commitment, not just participation. |
| **Hotspots** | Event history list, back nav |

---

### Screen 6: TTW Home Feed

| Field | Content |
|-------|---------|
| **Screenshot** | `ttw-home.png` ⏳ NEEDED |
| **Feature Title** | Teaching Wing |
| **Hook Line** | "Classes, schedules, and chaos — now tamed." |
| **Tech Tags** | `📡 Realtime Feed` · `🔔 Push Notifications` · `📋 Class Cards` |
| **Built By** | Eshan |
| **Why** | The Teaching Wing had no digital infrastructure. Class schedules were posted on notice boards and cancelled via WhatsApp. This feed brings every update to one screen. |
| **Hotspots** | Bottom nav: Chat, Calendar, Schedule (admin), Profile |

---

### Screen 7: TTW Chat

| Field | Content |
|-------|---------|
| **Screenshot** | `ttw-chat.png` ⏳ NEEDED |
| **Feature Title** | Stay Connected |
| **Hook Line** | "Group chats that actually work on campus." |
| **Tech Tags** | `💬 Firestore Messaging` · `📎 Media Sharing` · `🔕 FCM Push` |
| **Built By** | Ankesh |
| **Why** | Scattered WhatsApp groups led to missed messages, confusion about which group to check, and zero accountability. In-app chat keeps all TTW communication organized and searchable. |
| **Hotspots** | Chat threads, back nav |

---

### Screen 8: TTW Teaching Calendar

| Field | Content |
|-------|---------|
| **Screenshot** | `ttw-calendar.png` ⏳ NEEDED |
| **Feature Title** | Leave System |
| **Hook Line** | "Apply, approve, substitute — in 3 taps." |
| **Tech Tags** | `📬 Leave Requests` · `🔄 Auto Substitution` · `🧮 Firestore Transactions` |
| **Built By** | Eshan |
| **Why** | Teachers couldn't apply for leave digitally. When someone was absent, finding a substitute was chaos — random phone calls and last-minute panics. Now it's automated: apply, get a sub, done. |
| **Hotspots** | Calendar cells, leave button, back nav |

---

### Screen 9: TTW Auto-Scheduling (Admin)

| Field | Content |
|-------|---------|
| **Screenshot** | `ttw-schedule.png` ⏳ NEEDED |
| **Feature Title** | Smart Scheduler |
| **Hook Line** | "An algorithm that does what 3 admins couldn't." |
| **Tech Tags** | `🧠 Scheduling Algorithm` · `⚖️ Fair Distribution` · `📊 Constraint Solver` |
| **Built By** | Eshan |
| **Why** | Manually scheduling 30+ teachers across multiple slots, respecting preferences, and distributing load fairly took admins entire weekends. This algorithm does it in seconds with mathematically fair distribution. |
| **Hotspots** | Generate schedule button, settings, back nav |

---

### Screen 10: TTW Profile

| Field | Content |
|-------|---------|
| **Screenshot** | `ttw-profile.png` ⏳ NEEDED |
| **Feature Title** | Teacher Profile |
| **Hook Line** | "Your teaching record, crystal clear." |
| **Tech Tags** | `👤 Profile Data` · `🕐 Teaching Hours` · `📜 History Log` |
| **Built By** | Both |
| **Why** | Teachers had no record of how many classes they'd taken. This profile gives them a transparent, verified log — useful for certificates and internal reporting. |
| **Hotspots** | History list, back nav |

---

## ⏳ Asset Tracking

### Screenshots Directory
**Location:** `showcase/assets/screenshot/`

| # | Screen | Filename | Status |
|---|--------|----------|--------|
| 0 | First Screen (Login) | `First Screen.png` | ✅ Provided |
| 1 | NSS Home Feed | `nss-home.png` | ⏳ Needed |
| 2 | NSS Calendar | `nss-calendar.png` | ⏳ Needed |
| 3 | NSS QR Attendance (Admin) | `nss-qr-admin.png` | ⏳ Needed |
| 4 | NSS QR Scan (Student) | `nss-qr-student.png` | ⏳ Needed |
| 5 | NSS Profile | `nss-profile.png` | ⏳ Needed |
| 6 | TTW Home Feed | `ttw-home.png` | ⏳ Needed |
| 7 | TTW Chat | `ttw-chat.png` | ⏳ Needed |
| 8 | TTW Teaching Calendar | `ttw-calendar.png` | ⏳ Needed |
| 9 | TTW Auto-Scheduling (Admin) | `ttw-schedule.png` | ⏳ Needed |
| 10 | TTW Profile | `ttw-profile.png` | ⏳ Needed |

### Other Assets

| Item | Status |
|------|--------|
| **Dev 1 (Eshan) Links:** GitHub & LinkedIn | ✅ Done |
| **Dev 2 (Ankesh) Links:** GitHub & LinkedIn | ✅ Done |
| **Play Store Link:** Actual URL to the app | ✅ Done |
