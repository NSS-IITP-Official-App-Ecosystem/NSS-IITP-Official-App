# Explore Page — Content Strategy & Decisions

> **Purpose of this doc:** Lock down what each content zone is for, define clear rules, and audit every screen against those rules.  
> Last updated: 2026-04-05

---

## 🎯 The Four Content Zones (Left → Right)

The explore page has **four distinct content areas** that update with every screen. Each zone must serve a different purpose — **zero overlap between zones**.

```
┌──────────────────────┐    ┌──────────┐    ┌──────────────────────┐
│  1. HEADER (pageName) │    │          │    │                      │
│  2. DESCRIPTION       │    │  📱 PHONE │    │  4. UNDER THE HOOD   │
│  3. TECH STACK        │    │  SCREEN  │    │     (feature cards)  │
│     + BUILT BY        │    │          │    │                      │
└──────────────────────┘    └──────────┘    └──────────────────────┘
```

---

## Zone 1: HEADER (`pageName`)

### What it should be
The **actual name of the screen** the user is looking at — as if it were a label in the app's navigation or settings. It anchors the user so they know *where* they are in the app.

### Rules
- ✅ **Name what the user SEES**, not what it does. Think: app screen title.
- ✅ Keep it 2–4 words maximum.
- ✅ Make it feel professional and clear — this is a product showcase, not a marketing pitch.
- ❌ Don't use metaphors or "cool" names here (save that for featureTitle in CONTENT.md).
- ❌ Don't describe what it does — that's the description zone's job.

### Examples
| ✅ Good | ❌ Bad |
|---------|--------|
| `Role Selection Screen` | `Welcome Gate` |
| `Admin Login Screen` | `Admin Gate` |
| `Operations Feed` | `Feed Systems` |
| `QR Attendance Scanner` | `Proxy Killer` |
| `Schedule Generator` | `Generation Engine` |

---

## Zone 2: DESCRIPTION (`pageDescription`)

### What it should be
A **1–2 sentence explanation of what THIS screen does**, written from a technical-product lens. Highlight 2–3 key aspects using `<span class="highlight_text">` tags. The description should tell a recruiter/viewer:
1. What the screen's **primary purpose** is.
2. What makes its **implementation** notable (1 specific technical detail).

### Rules
- ✅ Describe what the screen **does** and one interesting **how**.
- ✅ Use exactly 2–3 `highlight_text` spans to draw the eye to the most important phrases.
- ✅ Keep it to 1–2 sentences (≤ 40 words ideal, 50 max).
- ❌ Don't list features — that's the Under the Hood zone's job.
- ❌ Don't mention generic tech names ("Firebase", "Jetpack Compose") — that's the Tech Stack zone.
- ❌ Don't repeat what the header already says.

### Pattern
> "The/A [what this screen is] for/that [primary purpose]. It [one specific technical detail that makes it interesting]."

### Examples
| Screen | ✅ Good Description |
|--------|---------------------|
| Role Selection | "The entry gateway offering dedicated pathways for **volunteers** and **admins**. Each route triggers completely separate **validation schemes** and interface flows." |
| Admin Login | "The dedicated sign-in form for **NSS admins**. It leverages pre-auth Firestore lookups, enforces a strict **role-gate check**, and silently performs **EC-key device binding** upon success." |
| Operations Feed | "The central hub for organizational updates. Engineered for high performance using a **predictive caching mechanism** and asynchronous **Cloudinary media pipelines**." |

---

## Zone 3: TECH STACK (`techTags`)

### What it should be
**The specific technologies, APIs, and libraries** that power this particular screen. These are badge/pill chips that give a recruiter a quick scan of the engineering stack.

### Rules
- ✅ List the **specific technologies** used on THIS screen — not generic ones.
- ✅ Each tag = a specific library, API, service, or pattern (e.g., "CameraX", "ZXing Decoder", "Kotlin Coroutines").
- ✅ 3–4 tags per screen is ideal. Never more than 5.
- ❌ Don't repeat the same tag across too many screens (e.g., "Cloud Firestore" appears on almost every screen — only include it when Firestore usage is the PRIMARY feature).
- ❌ Don't list vague labels like "Live Feed" or "Settings Panel" — these aren't technologies.
- ❌ The tag should NOT describe a feature — that belongs in Under the Hood.

### Tag Categories (pick from these)
| Category | Example Tags |
|----------|-------------|
| Backend | `Firebase Auth`, `Cloud Firestore`, `Firestore Transactions`, `Cloud Functions` |
| Media | `CameraX`, `ZXing`, `Cloudinary CDN`, `Coil Image Loader` |
| UI | `Jetpack Compose`, `Material3`, `ConcatAdapter`, `RecyclerView` |
| Architecture | `Kotlin Coroutines`, `ViewModel`, `StateFlow`, `DataStore` |
| Security | `Android Keystore`, `EC-256 Keys`, `Geo-Fencing`, `HMAC Tokens` |
| Algorithm | `TFV Algorithm`, `Natural Sort`, `Constraint Solver` |
| Export | `Excel POI`, `PDF Generation`, `CSV Export` |

---

## Zone 4: UNDER THE HOOD (`features`)

### What it should be
A curated mix of **5 cards per screen**, blending two flavors:

### Flavor A — "Hidden Gem" (easy-to-miss, high-impact UX features)
Things that a user might not notice at first glance but save real time or solve real pain points. Written in **plain, accessible language** — no heavy jargon. These make the viewer think: *"Oh wow, they thought of that?"*

**Examples of great Hidden Gems:**
- 📤 **Excel Upload** (Set Availability) — "Admins can upload an Excel sheet of free groups instead of manually entering each one — turns a 2-hour task into a 10-second upload."
- 🔤 **Smart Roll Number Search** (Class Groups) — "Type a number and it searches by group. Type letters and it searches by name. The search bar figures out what you mean automatically."
- 💾 **Remembers Your Role** (First Screen) — "Once you pick 'User' or 'Admin', the app remembers it. Next time you open the app, you skip this screen entirely."
- 📧 **No Email Needed for Password Reset** (Admin Login) — "Forgot your password? No need to type your email — the app already knows it from your roll number and fetches it from the database."

### Flavor B — "Engineering Flex" (impress a senior developer)
Specific patterns, algorithms, or architecture choices that demonstrate real engineering depth. **Technical terms are welcome here** — name actual classes, APIs, and patterns. These make a reviewer think: *"This person knows what they're doing."*

**Examples of great Engineering Flexes:**
- 🧠 **TFV-Scored Slot Assignment** — "Calculates a Total Feasibility Value for every open slot by summing volunteer group counts against expanded group-range availability, then greedily picks the highest-priority slot."
- 🛡️ **Idempotent QR Engine** — "The ingestion pipeline employs idempotent UUID verification on incoming QR packets, instantly neutralizing duplicate scans or replay attacks on the client."
- 🔑 **EC Device Binding** — "Generates a StrongBox-backed secp256r1 keypair post-login to silently bind the device via a SHA256withECDSA server challenge."

---

### Rules
- ✅ **Exactly 5 cards per screen.** Mix of Flavor A and Flavor B.
- ✅ **Flavor A cards:** Plain language. Focus on *what the user gains*. No jargon.
- ✅ **Flavor B cards:** Technical depth. Name specific classes, APIs, algorithms. Show engineering maturity.
- ✅ **Recommended split:** 2 Hidden Gems + 3 Engineering Flexes (adjust per screen — some screens are more UX-heavy, some more engineering-heavy).
- ✅ Each card must explain something that is **visible or implied on the current screenshot** — tie it to what the user can see.
- ❌ Don't repeat anything already said in the Description zone.
- ❌ Don't overlap with Tech Stack — tags name the WHAT, features explain the HOW and WHY.
- ❌ Don't use filler cards. If a screen genuinely only has 3 interesting things, 3 is fine. But aim for 5.

### Card Format
```
icon: [emoji]
title: [Short, punchy name — 2-5 words]
description: [1-2 sentences. Flavor A = plain english. Flavor B = technical.]
```

### Card Quality Checklist
Every card should pass at least 2 of these:
- [ ] Would someone say "I didn't notice that!" (Hidden Gem test)
- [ ] Would a senior dev say "That's a smart approach" (Engineering Flex test)
- [ ] Does it explain something visible on the screenshot?
- [ ] Is it different from every other card on this screen?

---