# 🛠️ SCREEN PLAYBOOK — One-Shot Replication Guide

> **Purpose:** This document is a self-contained reference that allows me (the AI) to add a new screen to the `/explore` interactive walkthrough in a single pass. The user only needs to provide: (1) a screenshot, (2) calibrated hotspot coordinates, (3) who built the page. Everything else is extracted from the Android source code and this playbook.

---

## 📁 FILE MAP

| File | Purpose | What Changes Per Screen |
|------|---------|------------------------|
| `showcase/app/explore/screenData.ts` | All screen content (data only) | **ADD** a new entry to the `screens` object |
| `showcase/public/screenshots/<id>.jpg` | Phone screenshot image | **ADD** the screenshot file |
| `showcase/app/explore/page.tsx` | Rendering logic (DO NOT TOUCH) | ❌ No changes needed |
| `showcase/app/explore/explore.module.css` | Styling (DO NOT TOUCH) | ❌ No changes needed |

> **Key insight:** Adding a new screen requires ONLY editing `screenData.ts` and dropping a screenshot into `public/screenshots/`. The page.tsx rendering is fully data-driven.

---

## 📐 DATA SCHEMA — `AppScreen`

Every screen in `screenData.ts` must conform to this exact TypeScript interface:

```typescript
export interface AppScreen {
  id: string;                          // kebab-case key, e.g. "nss-home"
  screenshot: string;                  // path: "/screenshots/<id>.jpg"
  pageName: string;                    // Plain name, e.g. "NSS Home Screen"
  pageDescription: string;             // HTML string (can use <span class="highlight_text">)
  featureTitle: string;                // NOT CURRENTLY RENDERED - legacy field, keep for data
  hookLine: string;                    // NOT CURRENTLY RENDERED - legacy field, keep for data
  techTags: {
    emoji: string;                     // Single emoji
    label: string;                     // Short tech name
    color?: string;                    // CSS var, e.g. "var(--accent-cyan)"
  }[];
  builtBy: "Eshan" | "Ankesh" | "Both";
  features: {                          // "Under the Hood" cards (3-5 recommended)
    icon: string;                      // Single emoji
    title: string;                     // Short bold title
    description: string;               // 1-2 sentence technical explanation
  }[];
  hotspots: {                          // Clickable areas on the phone screenshot
    shape: "rect" | "circle";
    // For rect:
    x?: number;      // left edge (%)
    y?: number;       // top edge (%)
    width?: number;   // width (%)
    height?: number;  // height (%)
    // For circle:
    cx?: number;      // center x (%)
    cy?: number;      // center y (%)
    r?: number;       // radius (%)
    targetScreenId: string;            // Must match an existing screen id
    label: string;                     // Tooltip text on hover
  }[];
}
```

---

## 🎨 RENDERED LAYOUT (What the User Sees)

```
┌──────────────────────────────────────────────────────────────────────┐
│  ← Home                                                             │
│                                                                      │
│  ┌────────────────┐    ┌────────────┐    ┌────────────────┐         │
│  │  LEFT PANEL    │    │            │    │  RIGHT PANEL   │         │
│  │                │    │    📱      │    │                │         │
│  │  pageName (h1) │    │   PHONE    │    │  — UNDER THE   │         │
│  │                │    │   FRAME    │    │    HOOD        │         │
│  │  pageDesc (p)  │    │            │    │                │         │
│  │  ─────────     │    │  screenshot│    │  ┌──────────┐  │         │
│  │  — TECH STACK  │    │  + hotspot │    │  │ feature  │  │         │
│  │  [🔐 Auth]     │    │    overlays│    │  │ card 1   │  │         │
│  │  [🎨 Compose]  │    │            │    │  │──────────│  │         │
│  │  ─────────     │    │            │    │  │ feature  │  │         │
│  │  — BUILT BY    │    │            │    │  │ card 2   │  │         │
│  │  [Eshan]       │    │            │    │  └──────────┘  │         │
│  └────────────────┘    └────────────┘    └────────────────┘         │
│                     [🖱️ Tap highlighted buttons to navigate]         │
└──────────────────────────────────────────────────────────────────────┘
```

### What Maps Where:
| Data Field | Panel | CSS Class | Rendered As |
|------------|-------|-----------|-------------|
| `pageName` | LEFT | `.page_name` | `<h1>` |
| `pageDescription` | LEFT | `.page_description` | `<p>` with `dangerouslySetInnerHTML` |
| `techTags[]` | LEFT | `.tech_section` → `.tech_tags` → `.tech_tag` | Pill chips |
| `builtBy` | LEFT | `.built_by_section` → `.built_by_badge` | Badge |
| `screenshot` | CENTER | `<Image>` inside `.phone_screen` | Phone screen image |
| `hotspots[]` | CENTER | `.hotspot` divs overlaid on screen | Glowing clickable areas |
| `features[]` | RIGHT | `.features_list` → `.feature_card` | Zigzag pipeline cards |

### Layout Constraints & Anti-Jumpiness
- The `explore.module.css` grid uses `align-items: start` combined with an explicit `margin-top` on `.left_panel` (`160px`) and `.right_panel` (`100px`).
- **Why:** This strictly anchors the headline (`h1`) and feature list to fixed vertical coordinates relative to the top of the phone frame.
- **Rule:** If you ever edit the layout CSS, do **NOT** use `align-items: center` for `.explore_layout`. If the panels are vertically centered, adding more text to a screen will cause the text block to expand upwards and downwards, resulting in a jarring "jump" in the header's position during screen transitions.
- **Text Overflow Safeguards:** The `feature_card` layout contains `min-width: 0` and `word-break: break-word` so that extremely long variable or class names (e.g., `ViewTreeObserver.OnGlobalLayoutListener`) will automatically wrap instead of horizontally snapping out of the card boundaries. You do not need to manually break long words.
- **Zigzag Overlap Safety:** The pipeline cards use `margin-top: -2.0rem` (not larger negative numbers like `-4.5rem`) to create a subtle interlocking zigzag. *Never* use deeply negative margins here; if a feature description is short, deeply negative margins will pull the cards up so far that they visually crash into the cards above them.

### Built-In Navigation Features
- **Floating Back Button (`.phone_back_btn`):** The explore UI incorporates an automatic, natively animated "Previous Screen" button that floats at the top-left edge of the phone frame (`top: 24px`, `left: -75px`). 
- **Zero Configuration:** When adding a new screen via `screenData.ts`, you do **not** need to manually add a back button to your hotspots. The architecture automatically tracks navigation history (e.g. `history.length > 1`) and renders the glowing cyan-gradient back button instantly. It pops the most recent `targetScreenId` off the stack.

### Phone Screen Transition — Android Material Z-Axis ✅
The phone screenshot area uses the **Material 3 Shared Z-Axis** transition (the default modern Android/Jetpack Compose page transition). This is **fully automatic** — no per-screen configuration needed.

| Direction | Entering Screen | Exiting Screen |
|-----------|----------------|----------------|
| **Forward** (hotspot tap) | Fades in, scales `0.92 → 1.0` | Fades out, scales `1.0 → 1.08` |
| **Backward** (← button) | Fades in, scales `1.08 → 1.0` | Fades out, scales `1.0 → 0.92` |

- Implemented via `framer-motion` `AnimatePresence` with `custom={direction}` passed down to `screenshotVariants` in `page.tsx`.
- `direction` is `1` when navigating forward, `-1` when going back — already tracked in state.
- **Rule:** Do NOT add any `x`, `y`, or slide offsets to this transition. The Z-axis scale+fade is intentional and matches the actual Android app's navigation feel.

---

## 🔧 TWO-PHASE WORKFLOW — Adding a New Screen

Adding a screen happens in **two phases** because the user needs to see the page live before they can calibrate hotspots.

---

### PHASE 1: BUILD THE PAGE (hotspots empty)

**Trigger:** The user says something like *"Now the page for [button name]. Built by Eshan."*
At this point the screenshot is already in `public/screenshots/`.

#### 1a. Identify the Screen
- The user tells me which button on the PREVIOUS screen leads here
- From that, I know the `targetScreenId` that was used (or will be used) in the parent screen's hotspots
- That target ID becomes THIS screen's `id`
- Confirm the screenshot filename exists in `public/screenshots/`

#### 1b. Extract Content from Android Source Code
Scan these directories for the relevant screen's Kotlin code:

```
Source code root: app/src/main/java/com/phad/chatapp/

Key locations to scan:
├── activities/          # LoginActivity, ChatActivity, GroupChatActivity, etc.
├── fragments/           # HomeFragment, NssHomeFragment, NssCalendarFragment,
│                        # NssQRAttendanceFragment, NssQRScanFragment,
│                        # NssProfileFragment, ProfileFragment, ChatFragment, etc.
├── features/            # calendar/, events/, home/, scheduling/
├── ui/                  # chats/, components/, dialogs/, events/, home/, profile/
├── viewmodels/          # Business logic and state management
├── repositories/        # Data access layer
├── security/            # Security implementations
├── services/            # Background services
└── utils/               # Utility functions
```

**What to extract:**
| Content Field | Where to Find It |
|---------------|------------------|
| `pageName` | Fragment/Activity name → convert to human-readable (e.g. `NssHomeFragment` → "NSS Home Screen") |
| `pageDescription` | Read the code logic, write 2-3 sentences. Use `<span class="highlight_text">` for 2-3 key phrases |
| `techTags` | Identify Android libraries/Firebase services used in that screen's code |
| `laserTheme` | `"dark"` for white screens, `"light"` for black screens, `"mixed"` if both (fuchsia/violet default). |
| `features[]` | Find 3-5 most interesting engineering patterns: algorithms, security, Firestore queries, caching, etc. |

#### 1c. Write the `screenData.ts` Entry (with EMPTY hotspots)

```typescript
"<screen-id>": {
  id: "<screen-id>",
  screenshot: "/screenshots/<screen-id>.jpg",
  pageName: "<Human Readable Screen Name>",
  pageDescription:
    '<2-3 sentences. Use <span class="highlight_text">key phrase</span> for emphasis.>',
  featureTitle: "<2-word catchy title>",
  hookLine: "<One punchy line under 10 words>",
  techTags: [
    { emoji: "🔐", label: "Firebase Auth", color: "var(--accent-amber)" },
    // ... 4-6 tags
  ],
  builtBy: "Eshan",  // or "Ankesh" or "Both"
  laserTheme: "mixed", // "dark" (for white screens) | "light" (for black screens) | "mixed" (default fuchsia)
  features: [
    {
      icon: "🔤",
      title: "<Short Feature Title>",
      description: "<1-2 sentences with specific implementation details>",
    },
    // ... 3-5 features
  ],
  hotspots: [],  // ← EMPTY — will be filled in Phase 2
},
```

> ⚠️ **IMPORTANT:** Set `hotspots: []` (empty array). The page renders fine with no hotspots — there just won't be any clickable areas yet.

#### 1d. Verify & Confirm
- The page should now be live on the dev server
- Tell the user: "Page is ready. Navigate to it and calibrate the buttons when you're ready."

---

### PHASE 2: CALIBRATE HOTSPOTS

**Trigger:** The user pastes calibration coordinates and tells me which button goes where.

#### 2a. Receive Calibration Data
The user uses the built-in calibration tool (⊕ Calibrate Hotspots button under the phone) and provides:
- Coordinates in format: `shape:"rect"  x:6.7%  y:76.9%  w:86.9%  h:7.6%`
- Which shape maps to which target: *"Shape 1 → nss-calendar, Shape 2 → nss-profile"*

#### 2b. Convert & Insert Hotspots
Convert calibration output to hotspot objects:

```typescript
// From: Shape 1: shape:"rect" x:6.7% y:76.9% w:86.9% h:7.6%
// To:
{
  shape: "rect",
  x: 6.7, y: 76.9, width: 86.9, height: 7.6,
  targetScreenId: "nss-calendar",
  label: "Calendar",  // human-readable tooltip
}
```

#### 2c. Update `screenData.ts`
Replace the empty `hotspots: []` with the populated array.

#### 2d. Update Previous Screen (if needed)
If the PREVIOUS screen's hotspot was pointing to a placeholder ID, verify it now matches this screen's actual ID.

---

## 📎 REFERENCE TABLES

### Tech Tag Emoji + Color Reference:
| Technology | Emoji | Color Variable |
|------------|-------|----------------|
| Firebase Auth | 🔐 | `var(--accent-amber)` |
| Cloud Firestore | 🗄️ | `var(--accent-cyan)` |
| Jetpack Compose | 🎨 | `var(--accent-blue)` |
| Role-Based Access | 👤 | `var(--accent-purple)` |
| Kotlin Coroutines | 🔄 | `var(--accent-emerald)` |
| CameraX | 📷 | `var(--accent-blue)` |
| ZXing (QR) | ⚡ | `var(--accent-amber)` |
| LazyColumn / Lists | 📋 | `var(--accent-cyan)` |
| Pull-to-Refresh | 🔄 | `var(--accent-emerald)` |
| Firestore Realtime | 📡 | `var(--accent-cyan)` |
| FCM Push | 🔔 | `var(--accent-rose)` |
| Custom Calendar | 🗓️ | `var(--accent-purple)` |
| Hour Analytics | 📊 | `var(--accent-amber)` |
| Scheduling Algo | 🧠 | `var(--accent-purple)` |
| Firestore Transactions | 🧮 | `var(--accent-cyan)` |
| Encrypted Payload | 🔒 | `var(--accent-rose)` |
| Media Sharing | 📎 | `var(--accent-blue)` |
| Android Keystore | 🔑 | `var(--accent-rose)` |

### Available Color Variables:
```css
--accent-cyan:    #66fcf1   /* Default / Firestore / Data */
--accent-teal:    #45a29e
--accent-purple:  #c084fc   /* UI / Role logic */
--accent-blue:    #60a5fa   /* Compose / Camera */
--accent-rose:    #fb7185   /* Security / Notifications */
--accent-amber:   #fbbf24   /* Auth / Analytics */
--accent-emerald: #34d399   /* Coroutines / Sync */
--accent-orange:  #fb923c   /* Admin features */
```

---

## 📋 SCREEN NAVIGATION GRAPH

This tracks which screens connect to which. Update as screens are added.

```
first-screen ✅
├── [Log In As User] → nss-home
└── [Log In As Admin] → admin-home

nss-home (TODO)
├── [Calendar tab] → nss-calendar
├── [QR tab] → nss-qr-scan
└── [Profile tab] → nss-profile

nss-calendar (TODO)
└── [...buttons...] → ???

admin-dashboard (Phase 1 Complete)
├── [NSS Wing Button] → nss-profile
└── [Teaching & Tech Wing Button] → teaching-tech-wing
```

---

## ✍️ WRITING STYLE GUIDE

### pageName
- Always a simple noun phrase: "Role Selection Screen", "NSS Home Screen", "Teaching Calendar"
- No quotes, no hype

### pageDescription
- 1-2 extremely concise sentences, technical but accessible
- **Always include 1-2 `<span class="highlight_text">` wraps** around key concepts
- Write from the perspective of "what does this screen solve for the user"
- Keep paragraphs short to avoid layout overlap.
- Example tone: *"The entry gateway offering dedicated pathways for <span class="highlight_text">NSS volunteers</span> and <span class="highlight_text">admins</span>. Each route triggers completely separate <span class="highlight_text">validation schemes</span> and interface flows."*

### features[].title
- 2-4 words, bold and descriptive: "Firestore-Resolved Reset", "3-Layer Authentication"
- Focus on the underlying engineering. If highlighting a UI element (like "Forgot Password"), title it by its technical mechanism.
- Technical, not marketing

### features[].description
- **Functional Mapping Required:** You must highlight the functional purpose of key UI elements visible on the screen (e.g., "Forgot Password", "Scan QR").
- **High-Effort Only:** ONLY document features that involve complex, high-effort engineering or clever logic. Do NOT write about basic or low-effort features (like "A button that navigates to the next screen").
- 1-2 sentences max. Keep it concise but do not lose important technical context.
- Mention specific implementation details: Firestore paths, algorithms, cryptographic methods
- Use em dashes (—) for dramatic pauses if needed.
- Example: *"Fetches the `instituteOutlookId` directly from Firestore instead of asking the user, ensuring secure password resets without data entry."*

---

## 🏗️ EXISTING SCREENS (Reference)

### ✅ first-screen (COMPLETE — with hotspots)
- **pageName:** Role Selection Screen
- **builtBy:** Ankesh
- **features:** Case-Insensitive Roll Lookup, 3-Layer Authentication, Role Gate Enforcement, Persistent Role Memory, Silent Device Binding
- **hotspots:** Log In As User → nss-home, Log In As Admin → admin-home
- **techTags:** Firebase Auth, Cloud Firestore, Jetpack Compose, Role-Based Access, Kotlin Coroutines

---

## 🔄 QUICK CHECKLIST (Per New Screen)

### Phase 1 — Build Page
- [ ] User says "Now the page for [button]. Built by X."
- [ ] Confirm screenshot exists in `public/screenshots/<id>.jpg` (user drops it there)
- [ ] Scan Android source for the relevant screen's Kotlin code
- [ ] Write `pageName` (plain name)
- [ ] Write `pageDescription` (1-2 very concise sentences with highlight_text spans)
- [ ] Write `techTags` (MAXIMUM 4 tags to keep them on two lines)
- [ ] Write `features` (3-5 under-the-hood cards)
- [ ] Write `featureTitle` + `hookLine` (legacy fields, still populate)
- [ ] Add entry to `screens` object in `screenData.ts` with **`hotspots: []`**
- [ ] Tell user: "Page is live. Calibrate when ready."

### Phase 2 — Add Hotspots
- [ ] User pastes calibration coords + target mapping
- [ ] Convert coords to hotspot objects
- [ ] Update the screen's `hotspots` array in `screenData.ts`
- [ ] Update navigation graph in this doc
- [ ] Verify all `targetScreenId` references exist or will exist
