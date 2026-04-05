export interface Hotspot {
  shape: "rect" | "circle";
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  cx?: number;
  cy?: number;
  r?: number;
  targetScreenId: string;
  label: string;
}

export interface FeatureHighlight {
  icon: string;
  title: string;
  description: string;
}

export interface AppScreen {
  id: string;
  screenshot: string;
  /** Actual screen name shown plainly, e.g. "Role Selection Screen" */
  pageName: string;
  /** Plain description paragraph shown below the page name */
  pageDescription: string;
  featureTitle: string;
  hookLine: string;
  techTags: { emoji: string; label: string; color?: string }[];
  builtBy: "Eshan" | "Ankesh" | "Both";
  features: FeatureHighlight[];
  hotspots: Hotspot[];
  laserTheme?: "light" | "dark" | "mixed";
}

export const screens: Record<string, AppScreen> = {
  "first-screen": {
    id: "first-screen",
    screenshot: "/screenshots/first-screen.jpg",
    pageName: "Role Selection Screen",
    pageDescription:
      'The entry gateway offering dedicated pathways for <span class="highlight_text">volunteers</span> and <span class="highlight_text">admins</span>. The screen automatically bypasses itself for returning users by reading the <span class="highlight_text">cached interface choice</span> from local memory.',
    featureTitle: "Welcome Gate",
    hookLine: "Two doors. Two worlds. One mission.",
    techTags: [
      { emoji: "📦", label: "ViewBinding", color: "var(--accent-purple)" },
      { emoji: "💾", label: "SharedPreferences", color: "var(--accent-amber)" },
      { emoji: "🔀", label: "Android Intents", color: "var(--accent-cyan)" },
      { emoji: "🎬", label: "XML Animations", color: "var(--accent-rose)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "💾",
        title: "Skip the Gate",
        description:
          "Once logged in, the app remembers your role. The next time you open the app, you skip this screen entirely and land right on your dashboard.",
      },
      {
        icon: "🕵️",
        title: "Secret Debug Menu",
        description:
          "A hidden long-press gesture on the background opens a developer bypass mode, allowing QA testers to skip Firebase Auth when testing offline.",
      },
      {
        icon: "🎭",
        title: "Dynamic Form Injection",
        description:
          "Both buttons launch the exact same LoginFormActivity class, passing a LOGIN_TYPE Intent extra that dynamically reconfigures the next screen's validation logic.",
      },
      {
        icon: "🧱",
        title: "Module Isolation Routing",
        description:
          "Returning users are routed to entirely separate host activities (NssMainActivity vs MainActivity) based on their token, enforcing a hard context boundary.",
      },
      {
        icon: "🎬",
        title: "Custom Activity Transitions",
        description:
          "Replaces the default Android screen switch with an overridePendingTransition sequence, stacking a custom XML slide-up animation over a system fade-out.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 6.7, y: 76.9, width: 86.9, height: 7.6,
        targetScreenId: "vol-log-in",
        label: "Log In As User",
      },
      {
        shape: "rect",
        x: 7.1, y: 86.4, width: 86.1, height: 7.2,
        targetScreenId: "admin-home",
        label: "Log In As Admin",
      },
    ],
    laserTheme: "mixed",
  },

  "admin-home": {
    id: "admin-home",
    screenshot: "/screenshots/log-in-as-admin.jpg",
    pageName: "Admin Login Screen",
    pageDescription:
      'The dedicated sign-in form for <span class="highlight_text">NSS admins</span>. It leverages pre-auth Firestore lookups, enforces a strict <span class="highlight_text">role-gate check</span>, and silently performs <span class="highlight_text">EC-key device binding</span> upon success.',
    featureTitle: "Admin Gate",
    hookLine: "Verified identity. Bound device. Zero compromise.",
    techTags: [
      { emoji: "🔐", label: "Firebase Auth", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-cyan)" },
      { emoji: "🔑", label: "Android Keystore", color: "var(--accent-rose)" },
      { emoji: "🔄", label: "Kotlin Coroutines", color: "var(--accent-emerald)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🔤",
        title: "Case-Insensitive Roll Lookup",
        description:
          "Checks 4 department permutations (e.g., CS/Cs/cS/cs) against Firestore to prevent lockouts due to database caps inconsistencies.",
      },
      {
        icon: "🚦",
        title: "Pre-Auth Role Gate",
        description:
          "Verifies `users/{roll}.userType` before calling Firebase. Non-admins hit a hard block instantly.",
      },
      {
        icon: "🔑",
        title: "EC Device Binding",
        description:
          "Generates a StrongBox-backed `secp256r1` keypair post-login to silently bind the device via a `SHA256withECDSA` server challenge.",
      },
      {
        icon: "🎹",
        title: "Keyboard-Aware Layout",
        description:
          "Uses a ViewTreeObserver to dynamically shift the UI upward when the soft keyboard appears, maintaining button visibility.",
      },
      {
        icon: "📧",
        title: "Firestore-Resolved Reset",
        description:
          "Fetches the `instituteOutlookId` directly from Firestore instead of asking the user, ensuring secure password resets.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 8.1,
        y: 91.6,
        width: 84.0,
        height: 7.0,
        targetScreenId: "admin-dashboard",
        label: "Log In",
      },
    ],
    laserTheme: "dark",
  },

  "vol-log-in": {
    id: "vol-log-in",
    screenshot: "/screenshots/vol-log-in.jpg",
    pageName: "Volunteer Form",
    pageDescription:
      'The primary authentication interface for students, featuring a <span class="highlight_text">dynamic bottom-curve UI</span> that seamlessly shifts to accommodate the system keyboard. Validation enforces strict role boundaries against a unified <span class="highlight_text">Firestore architecture</span> before issuing secure tokens.',
    featureTitle: "Volunteer Gate",
    hookLine: "Verify identity. Access your dashboard.",
    techTags: [
      { emoji: "🔐", label: "Firebase Auth", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-cyan)" },
      { emoji: "⌨️", label: "ViewTreeObserver", color: "var(--accent-purple)" },
      { emoji: "🔑", label: "Android Keystore", color: "var(--accent-emerald)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🌊",
        title: "Liquid Keyboard Shift",
        description:
          "A custom ViewTreeObserver listener watches the system keyboard. When the keyboard deploys, the bottom curved container gracefully animates upward to ensure the login button is never fully obscured.",
      },
      {
        icon: "✉️",
        title: "Inbox-Free Password Reset",
        description:
          "Forget trying to remember your exact institute email. Enter your roll number, and the backend silently maps it to your hidden Outlook address to trigger a secure Firebase password reset.",
      },
      {
        icon: "🔤",
        title: "Fault-Tolerant Lookup",
        description:
          "Programmatically generates all 4 string case permutations of a user's department code (e.g. CS/Cs/cS/cs), avoiding silent Firestore lookup failures due to user input typos.",
      },
      {
        icon: "🚦",
        title: "Pre-Flight Permission Gates",
        description:
          "Validates the incoming user token against a unified Firestore user document before calling Firebase Auth, physically isolating the admin interface from curious volunteers.",
      },
      {
        icon: "🔗",
        title: "Device Context Binding",
        description:
          "Completes a cryptographic backend nonce challenge using the Android Keystore during the login sequence, securely binding the user's physical hardware signature to their profile.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 8.5,
        y: 91.5,
        width: 83.5,
        height: 7.2,
        targetScreenId: "vol-dashboard",
        label: "Log In",
      },
    ],
    laserTheme: "dark",
  },

  "admin-dashboard": {
    id: "admin-dashboard",
    screenshot: "/screenshots/logged-in-as-admin.jpg",
    pageName: "Routing Gateway",
    pageDescription:
      'A conditional interceptor screen that only appears if the user has overlapping roles. It acts as a <span class="highlight_text">dynamic router</span>, letting dual-role members choose their active workspace while strictly enforcing <span class="highlight_text">state clearance</span>.',
    featureTitle: "Path Selector",
    hookLine: "Dual access. Zero confusion.",
    techTags: [
      { emoji: "🔀", label: "Android Intents", color: "var(--accent-cyan)" },
      { emoji: "📦", label: "ViewBinding", color: "var(--accent-purple)" },
      { emoji: "💾", label: "SessionManager", color: "var(--accent-amber)" },
      { emoji: "🏁", label: "Intent Flags", color: "var(--accent-rose)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🧬",
        title: "Conditional Injection",
        description:
          "This screen isn't hardcoded into the standard login flow. It is dynamically injected only if the Firestore profile reveals the user possesses dual-wing privileges.",
      },
      {
        icon: "🧹",
        title: "Task Stack Clearing",
        description:
          "Uses FLAG_ACTIVITY_CLEAR_TASK flags upon selection to completely wipe the Android backstack, mathematically ensuring users cannot 'swipe back' into the wrong module.",
      },
      {
        icon: "💾",
        title: "Persistent Choice Memory",
        description:
          "Whichever wing you select is instantly logged into local memory. The next time you open the application, this choice automatically fast-tracks you into your preferred environment.",
      },
      {
        icon: "🎛️",
        title: "Dynamic View Culling",
        description:
          "If a user somehow navigates here without explicit Teaching credentials, the layout automatically self-censors by hiding restricted pathways before the screen is even rendered.",
      },
      {
        icon: "🚀",
        title: "Native Inflation Speed",
        description:
          "Intentionally bypasses Jetpack Compose in favor of legacy XML Android ViewBinding, shaving off inflation milliseconds for this critical transition gateway.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 6.7,
        y: 76.6,
        width: 87,
        height: 7.6,
        targetScreenId: "nss-home",
        label: "NSS",
      },
      {
        shape: "rect",
        x: 6.7,
        y: 86,
        width: 86.5,
        height: 7.4,
        targetScreenId: "ttw-updates",
        label: "Teaching & Tech Wing",
      },
    ],

    laserTheme: "dark",
  },

  "vol-dashboard": {
    id: "vol-dashboard",
    screenshot: "/screenshots/logged-in-as-admin.jpg",
    pageName: "Routing Gateway",
    pageDescription:
      'A conditional interceptor screen that only appears if the user has overlapping roles. It acts as a <span class="highlight_text">dynamic router</span>, letting dual-role members choose their active workspace while strictly enforcing <span class="highlight_text">state clearance</span>.',
    featureTitle: "Path Selector",
    hookLine: "Dual access. Zero confusion.",
    techTags: [
      { emoji: "🔀", label: "Android Intents", color: "var(--accent-cyan)" },
      { emoji: "📦", label: "ViewBinding", color: "var(--accent-purple)" },
      { emoji: "💾", label: "SessionManager", color: "var(--accent-amber)" },
      { emoji: "🏁", label: "Intent Flags", color: "var(--accent-rose)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🧬",
        title: "Conditional Injection",
        description:
          "This screen isn't hardcoded into the standard login flow. It is dynamically injected only if the Firestore profile reveals the user possesses dual-wing privileges.",
      },
      {
        icon: "🧹",
        title: "Task Stack Clearing",
        description:
          "Uses FLAG_ACTIVITY_CLEAR_TASK flags upon selection to completely wipe the Android backstack, mathematically ensuring users cannot 'swipe back' into the wrong module.",
      },
      {
        icon: "💾",
        title: "Persistent Choice Memory",
        description:
          "Whichever wing you select is instantly logged into local memory. The next time you open the application, this choice automatically fast-tracks you into your preferred environment.",
      },
      {
        icon: "🎛️",
        title: "Dynamic View Culling",
        description:
          "If a user somehow navigates here without explicit Teaching credentials, the layout automatically self-censors by hiding restricted pathways before the screen is even rendered.",
      },
      {
        icon: "🚀",
        title: "Native Inflation Speed",
        description:
          "Intentionally bypasses Jetpack Compose in favor of legacy XML Android ViewBinding, shaving off inflation milliseconds for this critical transition gateway.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 6.7,
        y: 76.6,
        width: 87,
        height: 7.6,
        targetScreenId: "vol-updates",
        label: "NSS",
      },
      {
        shape: "rect",
        x: 6.7,
        y: 86,
        width: 86.5,
        height: 7.4,
        targetScreenId: "vol-ttw-home",
        label: "Teaching & Tech Wing",
      },
    ],
    laserTheme: "dark",
  },

  "vol-updates": {
    id: "vol-updates",
    screenshot: "/screenshots/vol-updates.jpg",
    pageName: "Volunteer Updates Feed",
    pageDescription: 'The central news hub for NSS volunteers — a <span class="highlight_text">real-time feed</span> of announcements, events, and documents published by wing administrators.',
    featureTitle: "Volunteer Feed",
    hookLine: "Stay in the loop.",
    techTags: [
      { emoji: "⚛️", label: "Jetpack Compose", color: "var(--accent-cyan)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-emerald)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "⚡",
        title: "Smart Data Caching",
        description: "Features a custom 5-minute TTL cache engine leveraging SharedPreferences to drastically cut Firestore read volumes while enabling near-instant optimistic UI loads upon cold starts.",
      },
      {
        icon: "⚛️",
        title: "Compose Interoperability",
        description: "Demonstrates modern Android migration via a ComposeView bridge, rendering the complex, reactive feed using declarative Jetpack Compose within a traditional legacy Fragment.",
      },
      {
        icon: "👆",
        title: "Seamless Media Splitting",
        description: "Intelligently parses a single unified Firestore dataset and splits it into separated 'Text' and 'Reels' feeds, wrapped in a smooth HorizontalPager swipe gesture.",
      },
      {
        icon: "🛡️",
        title: "Defensive Type Parsing",
        description: "Employs strict nullable assertions and manual type casting when deserializing NoSQL payloads to safely merge legacy text posts with new rich multi-media arrays without crashing.",
      },
      {
        icon: "📱",
        title: "Density-Adaptive Styling",
        description: "Monitors screen height via Compose's LocalConfiguration to dynamically scale typography and adjust layout padding boundaries, ensuring optimal readability across varying hardware sizes.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 39.2,
        y: 93.5,
        width: 7.5,
        height: 3.3,
        targetScreenId: "vol-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 52.0,
        y: 93.2,
        width: 7.7,
        height: 3.7,
        targetScreenId: "vol-QR",
        label: "QR Scan",
      },
      {
        shape: "rect",
        x: 64.5,
        y: 93.4,
        width: 7.8,
        height: 3.4,
        targetScreenId: "vol-profile",
        label: "Profile",
      },
    ],
    laserTheme: "dark",
  },

  "vol-calender": {
    id: "vol-calender",
    screenshot: "/screenshots/vol-calender.jpg",
    pageName: "Volunteer Calendar",
    pageDescription: 'A dynamic, interactive calendar grid that unifies past attendance history and future schedule drops based on <span class="highlight_text">real-time Firestore event triggers</span> and strict wing-access rules.',
    featureTitle: "Dynamic Attendance Grid",
    hookLine: "Your schedule, color-coded.",
    techTags: [
      { emoji: "⚛️", label: "Jetpack Compose", color: "var(--accent-purple)" },
      { emoji: "🎨", label: "State-Driven UI", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⚛️",
        title: "Compose Interoperability",
        description: "Uses a ComposeView bridge to render complex, adaptive Jetpack Compose calendar grids and pull-to-refresh logic inside a traditional XML Fragment architecture.",
      },
      {
        icon: "🛡️",
        title: "Context-Aware Filtering",
        description: "Enforces strict local filtering by matching the volunteer's assigned wings against Firebase event targets, preventing irrelevant schedule clutter.",
      },
      {
        icon: "🎨",
        title: "Dynamic Status Encoding",
        description: "Evaluates complex attendance history arrays on the fly to calculate cell background hex colors (e.g., Green for Present, Red for Absent) for instant data visualization.",
      },
      {
        icon: "🏗️",
        title: "Unified Role Architecture",
        description: "Reuses QRAttendanceViewModel and Admin Dialogs to let event managers create schedule drops directly within the calendar feed, keeping the Fragment DRY while handling diverse privileges.",
      },
      {
        icon: "⚡",
        title: "Coroutine-Powered Sync",
        description: "Implements Kotlin structured concurrency via refreshScope.launch paired with Compose's PullToRefreshBox to safely run non-blocking Network calls and gracefully bypass local caching.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 31.1,
        y: 46.0,
        width: 11.7,
        height: 5.7,
        targetScreenId: "vol-day",
        label: "View Day",
      },
      {
        shape: "rect",
        x: 27.3,
        y: 93.4,
        width: 6.8,
        height: 2.9,
        targetScreenId: "vol-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 52.0,
        y: 93.0,
        width: 7.5,
        height: 3.6,
        targetScreenId: "vol-QR",
        label: "QR Scan",
      },
      {
        shape: "rect",
        x: 64.5,
        y: 93.2,
        width: 7.4,
        height: 3.5,
        targetScreenId: "vol-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "vol-day": {
    id: "vol-day",
    screenshot: "/screenshots/vol-day.jpg",
    pageName: "Day Schedule View",
    pageDescription: 'A detailed dynamic dialog showing chronologically sorted events for a selected calendar date with <span class="highlight_text">device-adaptive layout bounds</span> and interactive data truncation.',
    featureTitle: "Day Agenda Dialog",
    hookLine: "Your day, perfectly bounded.",
    techTags: [
      { emoji: "⚛️", label: "Jetpack Compose", color: "var(--accent-purple)" },
      { emoji: "📱", label: "Adaptive Layout", color: "var(--accent-blue)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⏱️",
        title: "Dynamic String Time Sorting",
        description: "Parses wild-card time strings locally at runtime to calculate Long epoch bounds, ensuring chronologically accurate list sorting while gracefully dropping fallback errors to the stack bottom.",
      },
      {
        icon: "📱",
        title: "Device-Adaptive Bounding",
        description: "Utilizes Compose's intrinsic widthIn(min = 340.dp, max = 500.dp) modifiers to ensure the dialog maintains readable margins across both compact mobile displays and expansive tablet form-factors.",
      },
      {
        icon: "✂️",
        title: "Interactive Data Truncation",
        description: "Implements a dynamic 'Show more' expansion system using Compose state hoisting (isDescriptionExpanded) to preserve strict layout max-heights and prevent accidental scroll overflow.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.3,
        y: 93.4,
        width: 6.8,
        height: 2.9,
        targetScreenId: "vol-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 52.0,
        y: 93.0,
        width: 7.5,
        height: 3.6,
        targetScreenId: "vol-QR",
        label: "QR Scan",
      },
      {
        shape: "rect",
        x: 64.5,
        y: 93.2,
        width: 7.4,
        height: 3.5,
        targetScreenId: "vol-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "vol-QR": {
    id: "vol-QR",
    screenshot: "/screenshots/vol-QR.jpg",
    pageName: "QR Attendance Scanner",
    pageDescription: 'A <span class="highlight_text">server-verified QR scanner</span> that gates attendance marking behind Play Integrity checks, multi-layer client validation, and real-time Firestore session confirmation — making attendance spoofing structurally impossible.',
    featureTitle: "QR Check-In",
    hookLine: "Scan in. Get counted.",
    techTags: [
      { emoji: "📷", label: "CameraX", color: "var(--accent-purple)" },
      { emoji: "🛡️", label: "Play Integrity", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-emerald)" },
      { emoji: "🤖", label: "ML Kit", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🛡️",
        title: "Play Integrity Gating",
        description: "Blocks QR scanning until Google's Play Integrity API confirms the app is unmodified and running on a non-rooted device. A 24-hour SharedPreferences cache bypasses the network round-trip on crowded-event re-opens.",
      },
      {
        icon: "🤖",
        title: "ML Kit Analysis Pipeline",
        description: "Feeds CameraX ImageAnalysis frames into ML Kit BarcodeScanning at 100ms intervals via STRATEGY_KEEP_ONLY_LATEST backpressure, with a 1-second same-code debounce preventing duplicate submissions from a held camera.",
      },
      {
        icon: "🔐",
        title: "5-Layer Client Validation",
        description: "QRSecurityValidator runs a sequential gauntlet: format check → 10-second timestamp window → SHA-256 signature verification → ConcurrentHashMap replay-attack cache → 10-scans-per-minute rate limiter — all before touching Firestore.",
      },
      {
        icon: "📍",
        title: "Dual-Permission Location Guard",
        description: "Requires CAMERA and ACCESS_FINE_LOCATION before the scanner activates. If GPS is disabled, a blocking dialog redirects to system settings. Location refreshes every 10 seconds via onResume/onPause lifecycle hooks.",
      },
      {
        icon: "⚡",
        title: "Instant Camera Exit UX",
        description: "On valid QR detection, the CameraX provider unbinds and the PreviewView is hidden immediately. A DimmedHomeBackground overlay replaces the feed during Firestore writes, eliminating the jarring black-screen flash.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.7,
        y: 93.4,
        width: 6.1,
        height: 3.0,
        targetScreenId: "vol-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 39.5,
        y: 93.2,
        width: 7.0,
        height: 3.4,
        targetScreenId: "vol-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 65.4,
        y: 93.4,
        width: 6.6,
        height: 3.0,
        targetScreenId: "vol-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "vol-profile": {
    id: "vol-profile",
    screenshot: "/screenshots/vol-profile.jpg",
    pageName: "Volunteer Profile",
    pageDescription: 'A personal dashboard with <span class="highlight_text">real-time semester stats, wing-aware attendance scoring, and admin-grade Excel export</span> — all derived from a live Firestore listener without a single page refresh.',
    featureTitle: "Your NSS Record",
    hookLine: "Your impact, quantified.",
    techTags: [
      { emoji: "📡", label: "Realtime Firestore", color: "var(--accent-amber)" },
      { emoji: "📊", label: "Semester Analytics", color: "var(--accent-purple)" },
      { emoji: "📁", label: "Apache POI (xlsx)", color: "var(--accent-emerald)" },
      { emoji: "🏷️", label: "Wing Logic", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📡",
        title: "Real-Time Stat Listener",
        description: "ProfileRepository opens a Firestore snapshotListener on the student's users document. Any attendance event write — even by an admin on a different device — propagates instantly to the profile card via StateFlow without a manual refresh.",
      },
      {
        icon: "📅",
        title: "Semester-Aware Stats Engine",
        description: "AttendanceStatsCalculator partitions events by date into Sem 1 (Jul–Dec 10) and Sem 2 (Dec 11–Jun). It computes earned hours, deducts mandatory-event penalties, and formats them as 'earned/total' strings for both semesters independently.",
      },
      {
        icon: "🏷️",
        title: "Wing-Aware Mandatory Penalties",
        description: "When a volunteer misses a mandatory event, the calculator checks wing membership using set intersection. Deductions only apply if the event's wing list overlaps the volunteer's assigned wings — preventing cross-wing false penalties.",
      },
      {
        icon: "📁",
        title: "Multi-Sheet Apache POI Export",
        description: "Admin export builds an .xlsx workbook with Apache POI: one 'Open & DNC Events' summary sheet and individual sheets per wing (Chetna, Environmental, Prayatna, Rural Dev, TTW). A ClaimResult resolver prevents double-counting hours across sheets.",
      },
      {
        icon: "🔎",
        title: "Per-Semester Event Drilldown",
        description: "Tapping Sem 1 or Sem 2 hours launches EventsListActivity with a semester intent parameter, filtering the student's attended events and generating a dated CSV export via FileProvider — scoped to the NSS_Reports subdirectory.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 28.0,
        y: 93.1,
        width: 6.3,
        height: 3.0,
        targetScreenId: "vol-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 39.8,
        y: 92.9,
        width: 7.0,
        height: 3.3,
        targetScreenId: "vol-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 51.9,
        y: 92.8,
        width: 7.6,
        height: 3.7,
        targetScreenId: "vol-QR",
        label: "QR Scan",
      },
      {
        shape: "rect",
        x: 73.1,
        y: 3.2,
        width: 7.4,
        height: 2.9,
        targetScreenId: "vol-ttw-home",
        label: "Switch to TTW",
      },
      {
        shape: "rect",
        x: 70.8,
        y: 44.5,
        width: 20.1,
        height: 8.1,
        targetScreenId: "vol-sem2",
        label: "Sem 2",
      },
    ],
    laserTheme: "light",
  },

  "vol-sem2": {
    id: "vol-sem2",
    screenshot: "/screenshots/vol-sem2.jpg",
    pageName: "Semester 2 Events",
    pageDescription: 'A <span class="highlight_text">semester-partitioned event drilldown</span> showing each attended event\'s hours for Dec 11 – Jun, with mandatory-event penalty deductions and a CSV export scoped to NSS_Reports.',
    featureTitle: "Per-Semester View",
    hookLine: "Your event history, cleanly partitioned.",
    techTags: [
      { emoji: "📅", label: "Date Partitioning", color: "var(--accent-purple)" },
      { emoji: "📁", label: "CSV Export", color: "var(--accent-emerald)" },
      { emoji: "🏷️", label: "Wing Logic", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📅",
        title: "Semester Date Partitioning",
        description: "AttendanceStatsCalculator maps each event's date string to Sem 1 (Jul–Dec 10) or Sem 2 (Dec 11–Jun) by parsing month and day. This view shows only Sem 2 events — including attendance from cross-wing open events that overlap the window.",
      },
      {
        icon: "🏷️",
        title: "Wing-Aware Penalty Deductions",
        description: "For mandatory events the student missed, negative hours are deducted only if the event's wing set intersects the student's assigned wings. This prevents penalising volunteers for events that were irrelevant to their cohort.",
      },
      {
        icon: "📁",
        title: "FileProvider CSV Export",
        description: "Tapping export generates a timestamped CSV in Downloads/NSS_Reports/ via ExcelGenerator, writing event name, date, and hours per row. The file is shared through a FileProvider URI, keeping private storage paths off the system clipboard.",
      },
    ],
    hotspots: [],
    laserTheme: "dark",
  },

  "vol-ttw-home": {
    id: "vol-ttw-home",
    screenshot: "/screenshots/vol-ttw-home.jpg",
    pageName: "TTW Volunteer View",
    pageDescription: 'The <span class="highlight_text">Teaching & Tech Wing\'s isolated update feed</span> — backed by a dual-layer 5-minute TTL cache, a rich post composer, and a cross-wing posting bridge to the NSS interface.',
    featureTitle: "TTW Feed",
    hookLine: "Teach, Code, Inspire.",
    techTags: [
      { emoji: "⚡", label: "5-min TTL Cache", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Firestore Feed", color: "var(--accent-emerald)" },
      { emoji: "🎬", label: "Reel Embeds", color: "var(--accent-purple)" },
      { emoji: "☁️", label: "Cloudinary CDN", color: "var(--accent-cyan)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "⚡",
        title: "Dual-Layer 5-Min TTL Cache",
        description: "On open, the feed checks a SharedPreferences timestamp against a 5-minute TTL. If valid, it serves an in-memory CachedUpdate list instantly. Only on expiry does it fire the Firestore query — a manual pull-to-refresh forces both layers to clear.",
      },
      {
        icon: "🗄️",
        title: "Wing-Isolated Firestore Feed",
        description: "Updates are fetched exclusively from the 'ttw_updates' collection, ordered by timestamp descending with a limit of 20. This hard isolation means TTW volunteers never see unrelated NSS wing content — and vice versa.",
      },
      {
        icon: "🎬",
        title: "Dual Post-Type Composer",
        description: "Admins choose between a Text post (title, body, multi-image, multi-doc, external links) or a Reel post (Instagram URL embed). Switching type toggles input field visibility and independently validates the Instagram URL format before publishing.",
      },
      {
        icon: "🌐",
        title: "Cross-Wing Posting Bridge",
        description: "A 'Also post to NSS Interface' checkbox sets updateType=3 on the Firestore document. The NSS home feed query reads all updateType values — so a single admin action simultaneously publishes to both TTW and NSS audiences.",
      },
      {
        icon: "☁️",
        title: "Cloudinary Multi-Upload Pipeline",
        description: "Attachments are uploaded sequentially to Cloudinary with per-item progress reported directly to a dialog ProgressBar. On edit, a ClipData multi-select picker pre-fills existing Remote URLs and tracks deleted attachments for precise cleanup.",
      },
    ],
    hotspots: [

      {
        shape: "rect",
        x: 40.0,
        y: 93.5,
        width: 7.4,
        height: 3.1,
        targetScreenId: "vol-ttw-grp",
        label: "Groups",
      },
      {
        shape: "rect",
        x: 52.4,
        y: 93.3,
        width: 7.8,
        height: 3.6,
        targetScreenId: "vol-ttw-cal",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 65.1,
        y: 93.4,
        width: 7.0,
        height: 3.4,
        targetScreenId: "vol-ttw-pro",
        label: "Profile",
      },
    ],
    laserTheme: "dark",
  },

  "vol-ttw-grp": {
    id: "vol-ttw-grp",
    screenshot: "/screenshots/vol-ttw-grp.jpg",
    pageName: "TTW Class Communities",
    pageDescription: 'Auto-generated <span class="highlight_text">teaching chat groups</span> based on timetable assignments, enabling instant coordination between teaching partners and admins.',
    featureTitle: "Teaching Communities",
    hookLine: "Auto-synced class chats.",
    techTags: [
      { emoji: "🔄", label: "Two-Way Sync", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-purple)" },
      { emoji: "🛡️", label: "Admin Permissions", color: "var(--accent-amber)" },
      { emoji: "👥", label: "Cross-Collection", color: "var(--accent-cyan)" },
    ],
    builtBy: "Aditya Gupta",
    features: [
      {
        icon: "🔄",
        title: "Two-Way Subject Sync Engine",
        description: "SubjectAssignmentService runs a two-phase sync. Phase 1 pushes pendingAdd/pendingRemove mutations from group documents directly into the global subjectAssignments collection. Phase 2 acts as a reverse sync, ensuring group participants perfectly mirror the timetable assignments.",
      },
      {
        icon: "👥",
        title: "Cross-Collection Hydration",
        description: "When a new participant is added to a class community, the sync engine queries the 'Student' collection using volunteerRollNo to fetch 'Name' and 'NSS_gro' attributes, fully hydrating the new subjectAssignment map entry before writing.",
      },
      {
        icon: "🗄️",
        title: "Subject-Isolated Feeds",
        description: "ChatFragment loads communities via GroupRepository by querying the 'groups' collection where the 'participants' array contains the current user's roll number. Subject groups are marked with 'subject: true' to invoke the sync engine.",
      },
      {
        icon: "🛡️",
        title: "Role-Based Visibility",
        description: "The Chat UI enforces student/admin boundaries at the query level. While admins can search and query the entire 'users' collection, volunteers execute a bounded query (`whereEqualTo(\"userType\", \"Admin\")`) to restrict visibility only to authority figures and their own class communities.",
      },
      {
        icon: "🔍",
        title: "Unified Search Architecture",
        description: "The search bar merges two async queries—one for Users and one for Groups—into a unified ChatSearchResult. Results are alphanumerically sorted and intelligently bounded by the user's role and existing community memberships.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 28.1,
        y: 93.3,
        width: 6.6,
        height: 3.2,
        targetScreenId: "vol-ttw-home",
        label: "Home",
      },
      {
        shape: "rect",
        x: 52.9,
        y: 93.4,
        width: 6.9,
        height: 3.2,
        targetScreenId: "vol-ttw-cal",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 65.1,
        y: 93.2,
        width: 6.7,
        height: 3.3,
        targetScreenId: "vol-ttw-pro",
        label: "Profile",
      },
    ],
    laserTheme: "dark",
  },

  "vol-ttw-cal": {
    id: "vol-ttw-cal",
    screenshot: "/screenshots/vol-ttw-cal.jpg",
    pageName: "TTW Substitute Scheduler",
    pageDescription: 'A dual-role calendar system managing <span class="highlight_text">Leave Substitutions and Schedule Syncing</span> enforced through real-time Firestore triggers and role-based gating.',
    featureTitle: "Dynamic Scheduling",
    hookLine: "Automated peer-to-peer substitutions.",
    techTags: [
      { emoji: "🏛️", label: "MVVM Pattern", color: "var(--accent-amber)" },
      { emoji: "🔄", label: "Leave Pipeline", color: "var(--accent-purple)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-emerald)" },
      { emoji: "🔐", label: "Role Gating", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔄",
        title: "Leave Substitution Pipeline",
        description: "Implements a state machine for teaching absences (PENDING → APPROVED → ACCEPTED/SUBSTITUTED). Approved leaves become available 'virtual classes' that other volunteers can accept to cover the slot.",
      },
      {
        icon: "🛡️",
        title: "Role-Context Action Menus",
        description: "The UI dynamically generates Date Action dialogs based on `_currentUserRole`. Admins receive management actions (Add Teaching, Approve Leaves, Delete), while Volunteers see actions like 'Accept Class' and 'Book Slot'.",
      },
      {
        icon: "⏳",
        title: "Temporal Gating API",
        description: "Enforces strict chronological boundaries using `Calendar.getInstance()`. Selecting any date before 'today' triggers a read-only fallback mode, blocking all event creation, modifications, and leave substitutions.",
      },
      {
        icon: "🛑",
        title: "Substitution Verification",
        description: "When accepting a substitution class, the logic compares the input roll number against the original `LeaveApplication.rollNumber`, throwing a validation error if the applicant attempts to accept their own leave.",
      },
      {
        icon: "🏛️",
        title: "MVVM Architecture",
        description: "Built strictly on the MVVM pattern. The `CalendarFragment` observes `CalendarViewModel` via LiveData, which coordinates with `CalendarRepository` and `CalendarSessionManager` to execute Firestore mutations effortlessly.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 28.2,
        y: 93.8,
        width: 6.4,
        height: 2.8,
        targetScreenId: "vol-ttw-home",
        label: "Home",
      },
      {
        shape: "rect",
        x: 39.9,
        y: 93.4,
        width: 7.5,
        height: 3.2,
        targetScreenId: "vol-ttw-grp",
        label: "Groups",
      },
      {
        shape: "rect",
        x: 65.1,
        y: 93.7,
        width: 6.9,
        height: 3.1,
        targetScreenId: "vol-ttw-pro",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "vol-ttw-pro": {
    id: "vol-ttw-pro",
    screenshot: "/screenshots/vol-ttw-pro.jpg",
    pageName: "TTW Impact Dashboard",
    pageDescription: 'A unified profile view syncing <span class="highlight_text">centralized volunteer hours</span> with a detailed, real-time drilldown of wing-specific technical sessions and community events.',
    featureTitle: "Historical Drilldown",
    hookLine: "Complete visibility into your engagements.",
    techTags: [
      { emoji: "⚡", label: "Coroutines", color: "var(--accent-amber)" },
      { emoji: "🔄", label: "PullToRefresh", color: "var(--accent-purple)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-emerald)" },
      { emoji: "🔐", label: "State Hoisting", color: "var(--accent-cyan)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🔐",
        title: "Strict Content Gating",
        description: "Engineered a client-side visibility filter that validates the current user's roll number against the document's attendees array, ensuring volunteers only see their authorized TTW sessions.",
      },
      {
        icon: "🔄",
        title: "Pull-to-Refresh Architecture",
        description: "Implemented the modern Material3 PullToRefreshBox API with state hoisting to seamlessly force-sync the local UI cache with live Firestore TTW data on demand.",
      },
      {
        icon: "🔍",
        title: "Multi-Parameter Filter Engine",
        description: "Constructed a dynamic Compose sequence filter processing 5+ real-time constraints (Regex Search, Date Range, Wing Designation, Mandatory Flags) inside a remember block for zero-lag UI updates.",
      },
      {
        icon: "⚡",
        title: "Coroutines Data Layer",
        description: "Utilized coroutineScope.launch tied to the Compose lifecycle to safely execute network calls on background threads, preventing UI lockups during large TTW history retrievals.",
      },
      {
        icon: "🛡️",
        title: "Secure Admin Overrides",
        description: "Integrated an encrypted SessionManager state that identifies isAdmin flags globally, instantly overriding local visibility constraints to grant core team members full auditing access.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 72.3,
        y: 3.4,
        width: 8.5,
        height: 3.3,
        targetScreenId: "vol-updates",
        label: "Switch to NSS",
      },
      {
        shape: "rect",
        x: 28.3,
        y: 93.4,
        width: 6.4,
        height: 3.1,
        targetScreenId: "vol-ttw-home",
        label: "Home",
      },
      {
        shape: "rect",
        x: 39.7,
        y: 92.9,
        width: 8.2,
        height: 3.6,
        targetScreenId: "vol-ttw-grp",
        label: "Groups",
      },
      {
        shape: "rect",
        x: 52.1,
        y: 93.1,
        width: 8.2,
        height: 3.4,
        targetScreenId: "vol-ttw-cal",
        label: "Calendar",
      },
    ],
    laserTheme: "dark",
  },

  "nss-profile": {
    id: "nss-profile",
    screenshot: "/screenshots/nss-profile.jpg",
    pageName: "Volunteer Profile",
    pageDescription:
      'A personalized hub for <span class="highlight_text">attendance tracking</span> and event history. It dynamically aggregates user statistics and allows admins to generate comprehensive <span class="highlight_text">Excel matrix reports</span> natively.',
    featureTitle: "Data Hub",
    hookLine: "Your impact, quantified.",
    techTags: [
      { emoji: "📊", label: "Hour Analytics", color: "var(--accent-amber)" },
      { emoji: "🔄", label: "Real-time Sync", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Firestore", color: "var(--accent-cyan)" },
      { emoji: "📎", label: "Excel Export", color: "var(--accent-blue)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "⚡",
        title: "Real-time Attendance Sync",
        description:
          "Maintains an active Firestore listener powered by Kotlin Coroutines to automatically push attendance recalculations without requiring manual refreshes.",
      },
      {
        icon: "🧮",
        title: "On-Device Excel Generation",
        description:
          "Compiles a complete attendance matrix from thousands of Firestore documents into a formatted .xls file directly on the phone, bypassing cloud function costs.",
      },
      {
        icon: "🧬",
        title: "Hybrid Cache Resolution",
        description:
          "Instantly renders the UI using cached generic SessionManager traits before asynchronously overwriting them with enhanced Firestore profile data.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 39.3,
        y: 93.5,
        width: 7.6,
        height: 3.6,
        targetScreenId: "nss-calendar",
        label: "Calendar Tab",
      },
      {
        shape: "rect",
        x: 52.4,
        y: 93.7,
        width: 7.3,
        height: 3.1,
        targetScreenId: "nss-qr-scan",
        label: "QR Tab",
      },
      {
        shape: "rect",
        x: 72.8,
        y: 3.6,
        width: 7.7,
        height: 3.0,
        targetScreenId: "ttw-updates",
        label: "Settings",
      },
      {
        shape: "rect",
        x: 85.5,
        y: 3.2,
        width: 6.6,
        height: 3.3,
        targetScreenId: "nss-profile-right-action",
        label: "Actions",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-profile-left-action": {
    id: "nss-profile-left-action",
    screenshot: "/screenshots/first-screen.jpg",
    pageName: "Profile Options",
    pageDescription: "Awaiting final screenshot and feature data. This is a generic placeholder for the secondary settings pane.",
    featureTitle: "Under Construction",
    hookLine: "Screenshot required.",
    techTags: [],
    builtBy: "Eshan",
    features: [],
    hotspots: [
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
      {
        shape: "rect",
        x: 20.0,
        y: 20.0,
        width: 60.0,
        height: 8.0,
        targetScreenId: "ttw-updates",
        label: "TTW Schedule",
      }
    ],
    laserTheme: "mixed",
  },

  "nss-profile-right-action": {
    id: "nss-profile-right-action",
    screenshot: "/screenshots/more-option.jpg",
    pageName: "System Settings",
    pageDescription: 'A centralized control panel providing access to <span class="highlight_text">account preferences</span>, system cache clearing, privacy configurations, and active session termination processes.',
    featureTitle: "Control Panel",
    hookLine: "Manage preferences. Control sessions.",
    techTags: [
      { emoji: "⚙️", label: "Preferences DataStore", color: "var(--accent-amber)" },
      { emoji: "🧹", label: "Cache Management", color: "var(--accent-cyan)" },
      { emoji: "🔐", label: "Session Termination", color: "var(--accent-emerald)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🔐",
        title: "Secure Session Revocation",
        description: "Executes a robust sign-out sequence that proactively clears localized SessionManager states, purges SharedPreferences caches, and gracefully severs active WebSocket listeners to ensure complete termination of access tokens.",
      },
      {
        icon: "⚙️",
        title: "Preferences Persistence",
        description: "Interacts natively with Android's secure DataStore API to instantly commit application-wide accessibility and thematic preferences without dropping frames.",
      }
    ],
    hotspots: [
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
      {
        shape: "rect",
        x: 38.7,
        y: 15.0,
        width: 36.3,
        height: 4.5,
        targetScreenId: "nss-event-history",
        label: "Event History",
      },
      {
        shape: "rect",
        x: 38.9,
        y: 20.8,
        width: 38.2,
        height: 4.7,
        targetScreenId: "nss-faq",
        label: "FAQ",
      }
    ],
    laserTheme: "mixed",
  },

  "ttw-updates": {
    id: "ttw-updates",
    screenshot: "/screenshots/ttw-updates.jpg",
    pageName: "TTW Teaching Slots",
    pageDescription: 'A dark-themed scheduling hub for the <span class="highlight_text">Teaching & Technical Wing</span>. Admins manage teaching slot presets backed by a custom <span class="highlight_text">TFV-based assignment algorithm</span> and natural sort engine.',
    featureTitle: "Scheduling Engine",
    hookLine: "Allocate. Assign. Optimize.",
    techTags: [
      { emoji: "🧠", label: "TFV Algorithm", color: "var(--accent-purple)" },
      { emoji: "🗄️", label: "Firestore Presets", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "Kotlin Coroutines", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧠",
        title: "TFV-Scored Slot Assignment",
        description: "Calculates a Total Feasibility Value for every open slot by summing volunteer group counts against expanded group-range availability, then greedily picks the highest-priority slot to minimize unfilled assignments.",
      },
      {
        icon: "🔢",
        title: "Natural Sort Engine",
        description: "Pads all numeric substrings in preset names to 10 digits before comparison, ensuring 'AM 9B' always sorts before 'AM 10G' without manual ordering.",
      },
      {
        icon: "📅",
        title: "Adjacency Constraint Enforcement",
        description: "Blocks a volunteer from a second daily slot unless it's at the same school prefix AND within a ≤20 minute gap—enforced via 'isAdjacentSlotAllowed' with per-day slot tracking.",
      },
      {
        icon: "📊",
        title: "Batched Student Data Prefetch",
        description: "Chunks all volunteer roll numbers into groups of 10 and fires parallel Firestore 'whereIn' queries to resolve interview scores and subject preferences in a single round-trip instead of N individual reads.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 36.2,
        y: 92.8,
        width: 7.6,
        height: 3.5,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 46.4,
        y: 92.8,
        width: 7.3,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 56.2,
        y: 92.7,
        width: 7.4,
        height: 3.8,
        targetScreenId: "ttw-scheduling",
        label: "History",
      },
      {
        shape: "rect",
        x: 65.8,
        y: 92.8,
        width: 7.6,
        height: 3.5,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "nss-faq": {
    id: "nss-faq",
    screenshot: "/screenshots/faq.jpg",
    pageName: "Dynamic Knowledge Base",
    pageDescription: 'A dual-pane interactive knowledge base featuring a <span class="highlight_text">Conversation Panel</span> and an Expandable Question Tree, integrated with real-time text parsing.',
    featureTitle: "Active QnA",
    hookLine: "Find answers instantly.",
    techTags: [
      { emoji: "🗄️", label: "Firestore Nested State", color: "var(--accent-cyan)" },
      { emoji: "🔗", label: "Link Detection Regex", color: "var(--accent-amber)" },
      { emoji: "🔒", label: "Admin Content Gate", color: "var(--accent-rose)" }
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🧠",
        title: "Intelligent State Search",
        description: "Executes client-side deep searches simultaneously querying hierarchical Question Trees and parsed Answer text via 'performSearch(query, uiState)'.",
      },
      {
        icon: "🔗",
        title: "Regex Link Parsing",
        description: "Leverages a custom 'LinkDetector' to map plain-text URLs inside multi-line Markdown-like answers into safely interactive 'ClickableTextWithLinks' elements.",
      },
      {
        icon: "👑",
        title: "Admin Edit Gateway",
        description: "Conditionally renders the 'AdminFaqActivity' edit controls exclusively for validated SessionManager administrative roles, blocking all unauthorized content manipulation.",
      }
    ],
    hotspots: [
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      }
    ],
    laserTheme: "dark",
  },

  "nss-event-history": {
    id: "nss-event-history",
    screenshot: "/screenshots/event-history.jpg",
    pageName: "Event History Matrix",
    pageDescription: 'A multi-parameter analytics dashboard enabling complex sorting of archived events based on <span class="highlight_text">Attendance Visibility</span>, wing allocation, and mandatory hours matrices.',
    featureTitle: "Historical Log",
    hookLine: "Audit trails. Re-live past events.",
    techTags: [
      { emoji: "🔄", label: "Pull-To-Refresh", color: "var(--accent-emerald)" },
      { emoji: "🗓️", label: "Instant Date Parsing", color: "var(--accent-amber)" },
      { emoji: "🧮", label: "Visibility Filters", color: "var(--accent-cyan)" }
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧮",
        title: "Multi-parameter Pipeline Filters",
        description: "Channels historic payloads through 5 distinct predicate layers simultaneously (Search, DateRange, Wing, Mandatory, Identity Access) without stuttering Compose Recomposition.",
      },
      {
        icon: "🔒",
        title: "Strict Visibility Control",
        description: "Dynamically audits the 'visibleOnlyToPresent' boolean—forcing the rendering tree to cross-reference the user's specific roll number against the historical Attendance List registry before building the card.",
      },
      {
        icon: "🔄",
        title: "Make Live Idempotency",
        description: "Empowers Admins to rapidly resurrect closed events via the 'makeEventLive' suspend block, safely cloning and restructuring legacy payload schemas back into active Listeners.",
      }
    ],
    hotspots: [
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      }
    ],
    laserTheme: "light",
  },

  "nss-calendar": {
    id: "nss-calendar",
    screenshot: "/screenshots/calender.jpg",
    pageName: "Interactive Calendar",
    pageDescription:
      'A comprehensive scheduling hub that unifies <span class="highlight_text">teaching substitution management</span> and general event booking. It enforces contextual <span class="highlight_text">role-based dialogue options</span> and strict validation rules instantly.',
    featureTitle: "Scheduling Engine",
    hookLine: "Organize chaos. Delegate seamlessly.",
    techTags: [
      { emoji: "📅", label: "Smart Calendar", color: "var(--accent-amber)" },
      { emoji: "🚦", label: "Role Dialogs", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "Substitution Logic", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Firestore DB", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🚦",
        title: "Role-Aware Action Dialogs",
        description:
          "Dynamically constructs contextual options based on admin vs user states derived from a local SessionManager—allowing admins to define slots while users book or substitute.",
      },
      {
        icon: "🔄",
        title: "Class Substitution Pipeline",
        description:
          "Facilitates an end-to-end leave substitution mechanism, algorithmically enforcing constraints like prohibiting self-acceptance and validating explicit availability slots via Coroutines.",
      },
      {
        icon: "🧬",
        title: "Tabbed Data Filtering",
        description:
          "Strictly partitions scheduling data into 'Teaching' and 'General' domains via fragmented instances, ensuring isolated data streams and avoiding UI thread blocks.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 52.4,
        y: 93.7,
        width: 7.3,
        height: 3.1,
        targetScreenId: "nss-qr-scan",
        label: "QR Tab",
      },
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
      {
        shape: "rect",
        x: 44.1,
        y: 39.8,
        width: 12.0,
        height: 5.6,
        targetScreenId: "nss-calendar-day",
        label: "Day View",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-calendar-day": {
    id: "nss-calendar-day",
    screenshot: "/screenshots/day.jpg",
    pageName: "Daily Schedule",
    pageDescription:
      'A focused daily schedule interface. It dynamically renders <span class="highlight_text">teaching assignments</span> and available <span class="highlight_text">leave applications</span> tailored to the active user\'s role.',
    featureTitle: "Day View Engine",
    hookLine: "Your day, perfectly organized.",
    techTags: [
      { emoji: "📅", label: "Live Schedule", color: "var(--accent-amber)" },
      { emoji: "⚡", label: "ConcatAdapter", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "Virtual Events", color: "var(--accent-emerald)" },
      { emoji: "🚦", label: "Conflict Res", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧠",
        title: "Virtual Event Generation",
        description:
          "Dynamically constructs un-persisted CalendarEvents from raw Schedule Assignments, avoiding redundant database queries while mapping daily constraints.",
      },
      {
        icon: "🎯",
        title: "Conflict Resolution",
        description:
          "Algorithmically detects and warns against overlapping schedule commitments before allowing a user to accept a substitute teaching request.",
      },
      {
        icon: "🏎️",
        title: "ConcatAdapter Implementation",
        description:
          "Utilizes an isolated ConcatAdapter to seamlessly combine distinct event and leave application lists into a single, highly performant scrollable Recyclerview.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 52.4,
        y: 93.7,
        width: 7.3,
        height: 3.1,
        targetScreenId: "nss-qr-scan",
        label: "QR Tab",
      },
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-qr-scan": {
    id: "nss-qr-scan",
    screenshot: "/screenshots/QR.jpg",
    pageName: "QR Attendance Scanner",
    pageDescription:
      'A highly secure, <span class="highlight_text">location-gated</span> attendance manager. It empowers admins to create sessions, verify physical presence via QR, and generate <span class="highlight_text">PDF manifests</span> instantly.',
    featureTitle: "Attendance Engine",
    hookLine: "Fast scans. Verified presence.",
    techTags: [
      { emoji: "📷", label: "QR Engine", color: "var(--accent-amber)" },
      { emoji: "📍", label: "Location Gating", color: "var(--accent-cyan)" },
      { emoji: "📎", label: "PDF Extraction", color: "var(--accent-blue)" },
      { emoji: "⚡", label: "Live Filtering", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📍",
        title: "Geo-Verified Sessions",
        description:
          "Enforces rigorous pre-flight location checks via the native LocationPermissionHelper before allowing admins to activate an attendance session, preventing spoofing.",
      },
      {
        icon: "📄",
        title: "Native PDF Matrix",
        description:
          "Executes on-device compilation of attendee data into heavily formatted, exportable PDF reports directly from memory, fully bypassing backend function processing.",
      },
      {
        icon: "🔍",
        title: "Multi-Dimensional Filtering",
        description:
          "Combines dynamic date parsing (LocalDate), case-insensitive text searching, and bitwise mandatory toggles to instantaneously filter thousands of local event records on the main thread.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 39.3,
        y: 93.5,
        width: 7.6,
        height: 3.6,
        targetScreenId: "nss-calendar",
        label: "Calendar Tab",
      },
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
      {
        shape: "rect",
        x: 81.8,
        y: 92.0,
        width: 14.5,
        height: 7.2,
        targetScreenId: "nss-create-event",
        label: "Create Event",
      },
      {
        shape: "rect",
        x: 18.5,
        y: 43.1,
        width: 34.4,
        height: 6.0,
        targetScreenId: "nss-start-attendance",
        label: "Start Attendance",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-start-attendance": {
    id: "nss-start-attendance",
    screenshot: "/screenshots/start-attendence.jpg",
    pageName: "Live Session Engine",
    pageDescription:
      'The operations command center for an active <span class="highlight_text">QR attendance stream</span>. It seamlessly binds real-time location metrics and snapshot listeners to track incoming <span class="highlight_text">student registrations</span> instantaneously.',
    featureTitle: "Active Session Manager",
    hookLine: "Live ingress. Geospatial lock.",
    techTags: [
      { emoji: "⚡", label: "Real-time Streams", color: "var(--accent-amber)" },
      { emoji: "🛜", label: "Geo-Fencing", color: "var(--accent-cyan)" },
      { emoji: "🛡️", label: "Anti-Spoofing", color: "var(--accent-emerald)" },
      { emoji: "👁️", label: "Live Dashboard", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🛜",
        title: "Dynamic Geo-Locking",
        description:
          "Integrates deeply with native Android location services and the robust FusedLocationProviderClient to enforce an unyielding geospatial perimeter before allowing any QR ingress.",
      },
      {
        icon: "⚡",
        title: "WebSocket Snapshot Binding",
        description:
          "Hooks natively into Firestore's underlying WebSocket implementation, projecting a high-performance RecyclerView stream of incoming attendee updates without blocking the main rendering thread.",
      },
      {
        icon: "🛡️",
        title: "Idempotent QR Engine",
        description:
          "The ingestion pipeline employs idempotent UUID verification strategies on incoming QR packets, instantly neutralizing duplicate scans or replay attacks natively on the client.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 39.3,
        y: 93.5,
        width: 7.6,
        height: 3.6,
        targetScreenId: "nss-calendar",
        label: "Calendar Tab",
      },
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-create-event": {
    id: "nss-create-event",
    screenshot: "/screenshots/create-event.jpg",
    pageName: "Event Creation Hub",
    pageDescription:
      'A secure gateway for defining new <span class="highlight_text">QR attendance sessions</span>. Validates input parameters instantly and provisions designated Firestore collections for <span class="highlight_text">live attendee tracking</span>.',
    featureTitle: "Session Provisioning",
    hookLine: "Deploy sessions. Track instantly.",
    techTags: [
      { emoji: "🧮", label: "UUID Engine", color: "var(--accent-amber)" },
      { emoji: "🚦", label: "Client Validation", color: "var(--accent-cyan)" },
      { emoji: "🔑", label: "Admin Gated", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Schema Prep", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔑",
        title: "Role-Gated Integration",
        description:
          "The initialization suite is strictly tied to a local SessionManager validation pipeline, ensuring only verified administrators can construct live events.",
      },
      {
        icon: "⚡",
        title: "Synchronous Verification",
        description:
          "Executes immediate client-side sanity checks to guarantee data integrity (title, dates, constraints) before initiating potentially costly network writes.",
      },
      {
        icon: "🗄️",
        title: "Scaffold Provisioning",
        description:
          "Asynchronously initializes a root event document while simultaneously scaffolding designated subcollection routes to prep for heavy QR ingress traffic.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.8,
        y: 93.8,
        width: 6.6,
        height: 2.9,
        targetScreenId: "nss-home",
        label: "Home Tab",
      },
      {
        shape: "rect",
        x: 39.3,
        y: 93.5,
        width: 7.6,
        height: 3.6,
        targetScreenId: "nss-calendar",
        label: "Calendar Tab",
      },
      {
        shape: "rect",
        x: 65.0,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
    ],
    laserTheme: "mixed",
  },

  "nss-home": {
    id: "nss-home",
    screenshot: "/screenshots/home.jpg",
    pageName: "Operations Feed",
    pageDescription:
      "The central hub for organizational updates. Engineered for high performance using a <span class=\"highlight_text\">predictive caching mechanism</span> and asynchronous <span class=\"highlight_text\">Cloudinary media pipelines</span>.",
    featureTitle: "Feed Systems",
    hookLine: "Zero lag. Instant updates.",
    techTags: [
      { emoji: "⚛️", label: "Jetpack Compose", color: "var(--accent-cyan)" },
      { emoji: "☁️", label: "Async Media", color: "var(--accent-orange)" },
      { emoji: "🛡️", label: "Role Base UI", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-purple)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "💾",
        title: "Predictive Request Caching",
        description:
          "Maintains local SharedPreferences state with TTL evaluation to drastically eliminate redundant Firestore network reads during active sessions.",
      },
      {
        icon: "☁️",
        title: "Async Media Engine",
        description:
          "Pipes multi-format payloads (Bulk Images, PDFs, Reels) directly to Cloudinary edge nodes via Dispatchers.IO, ensuring the UI thread remains entirely unblocked.",
      },
      {
        icon: "👥",
        title: "Dual Content Syndication",
        description:
          "Uses Firebase Auth role checking to dynamically render 'Cross-Post' options, allowing dual-role administrators to broadcast to multiple wings simultaneously.",
      },
      {
        icon: "🛡️",
        title: "Role-Based UI Injections",
        description:
          "Dynamically overlays floating action menus for 'Create Update' and 'Batch Delete'—alongside a hidden notification panel—strictly when the user's view state verifies admin properties.",
      },
      {
        icon: "📱",
        title: "Immersive Detail Expansion",
        description:
          "Programmatically intercepts the hardware BackHandler and triggers bottom navigation culling when a post is expanded, giving maximum screen real estate to the UpdateDetailScreen.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 39.3,
        y: 93.5,
        width: 7.6,
        height: 3.6,
        targetScreenId: "nss-calendar",
        label: "Calendar Tab",
      },
      {
        shape: "rect",
        x: 52.4,
        y: 93.7,
        width: 7.3,
        height: 3.1,
        targetScreenId: "nss-qr-scan",
        label: "QR Tab",
      },
      {
        shape: "rect",
        x: 65,
        y: 93.6,
        width: 7.2,
        height: 3.5,
        targetScreenId: "nss-profile",
        label: "Profile Tab",
      },
    ],
    laserTheme: "mixed",
  },

  "class-groups": {
    id: "class-groups",
    screenshot: "/screenshots/class-groups.jpg",
    pageName: "Volunteer Class Groups",
    pageDescription: 'A multi-strategy volunteer roster manager. Admins assign <span class="highlight_text">class counts per volunteer</span> and sync directly from the <span class="highlight_text">Firestore ttwStudents collection</span> with a long-press.',
    featureTitle: "Roster Management",
    hookLine: "Assign. Filter. Deploy.",
    techTags: [
      { emoji: "🔍", label: "Fuzzy Search", color: "var(--accent-cyan)" },
      { emoji: "🗄️", label: "Firestore Sync", color: "var(--accent-emerald)" },
      { emoji: "🧮", label: "Class Count Engine", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔍",
        title: "Context-Aware Fuzzy Search",
        description: "Automatically detects the search intent—numeric input queries by group, alphanumeric by roll number, ALL-CAPS by subject priority, and plain text by name prefix—routing each through a dedicated priority-sorted comparator chain.",
      },
      {
        icon: "🧮",
        title: "Class Count Assignment",
        description: "Each volunteer's classCount is independently configurable via increment/decrement controls. The 'Increment All' action batch-applies +1 to every currently visible filtered volunteer simultaneously.",
      },
      {
        icon: "🗄️",
        title: "Long-Press Firebase Sync",
        description: "Long-pressing the save button triggers a live Firestore read from the 'ttwStudents' collection to pull the latest 'classesPerWeek' values for all volunteers, safely overwriting stale local state.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.7,
        y: 93.5,
        width: 6.8,
        height: 3.0,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 46.4,
        y: 93.4,
        width: 6.7,
        height: 3.3,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 56.4,
        y: 93.2,
        width: 7.1,
        height: 3.5,
        targetScreenId: "ttw-scheduling",
        label: "History",
      },
      {
        shape: "rect",
        x: 66.4,
        y: 93.1,
        width: 6.5,
        height: 3.6,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "ttw-calender": {
    id: "ttw-calender",
    screenshot: "/screenshots/ttw-calender.jpg",
    pageName: "Teaching Slot Presets",
    pageDescription: 'A preset management hub for <span class="highlight_text">teaching slot configurations</span>. Each card visualizes group-frequency availability chips, fetched and sorted using the <span class="highlight_text">natural sort engine</span>.',
    featureTitle: "Preset Library",
    hookLine: "Configure. Load. Schedule.",
    techTags: [
      { emoji: "🔢", label: "Natural Sort", color: "var(--accent-purple)" },
      { emoji: "📊", label: "Group Frequency", color: "var(--accent-cyan)" },
      { emoji: "🗄️", label: "Firestore Presets", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔢",
        title: "Natural Sort Engine",
        description: "All preset names are sorted by padding every numeric substring to 10 digits before comparison, ensuring 'AM 9B' always precedes 'AM 10G' without any manual ordering.",
      },
      {
        icon: "📊",
        title: "Group Frequency Chips",
        description: "For each preset with uploaded availability data, the card dynamically renders a FlowRow of YellowAccent chips showing 'Gp N: count' — expanding compressed group ranges (e.g. '4-8') into individual frequency tallies.",
      },
      {
        icon: "🗑️",
        title: "Availability Data Purge",
        description: "Admins can delete stale availability data per-preset using the ErrorRed icon button, which fires a targeted Firestore 'FieldValue.delete()' call to surgically remove only the availability field.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.9,
        y: 93.3,
        width: 6.6,
        height: 3.2,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.3,
        y: 93.0,
        width: 7.3,
        height: 3.5,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 56.3,
        y: 93.1,
        width: 7.2,
        height: 3.5,
        targetScreenId: "ttw-scheduling",
        label: "History",
      },
      {
        shape: "rect",
        x: 66.3,
        y: 93.2,
        width: 7.0,
        height: 3.4,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "ttw-scheduling": {
    id: "ttw-scheduling",
    screenshot: "/screenshots/ttw-scheduling.jpg",
    pageName: "Schedule Maker Hub",
    pageDescription: 'The central command center for TTW administration. Admins can configure <span class="highlight_text">teaching slots</span>, manage volunteer <span class="highlight_text">availability presets</span>, and initiate the schedule generation algorithm.',
    featureTitle: "Hub Navigation",
    hookLine: "Centralized configuration.",
    techTags: [
      { emoji: "🎛️", label: "StaggeredMenuButton", color: "var(--accent-emerald)" },
      { emoji: "📱", label: "Compose Navigation", color: "var(--accent-cyan)" },
      { emoji: "🛠️", label: "Task Workflows", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧠",
        title: "TFV Heat Badges",
        description: "Every unassigned slot renders a live TFV badge color-mapped from red (TFV ≤ 3, most urgent) to green (TFV > 10, plenty of options), giving admins instant visual triage of scheduling bottlenecks.",
      },
      {
        icon: "⚡",
        title: "Round-Robin Auto-Assignment",
        description: "The AutoAwesome FAB triggers 'assignNextSlotInRoundRobin()'—greedily picking the highest-priority unassigned slot and running the TFV algorithm to find the optimal volunteer in a single coroutine step.",
      },
      {
        icon: "📋",
        title: "Real-Time Algorithm Log Viewer",
        description: "A dedicated log dialog streams the ViewModel's 'algorithmLogs' StateFlow live, letting admins inspect every scoring decision and rejection reason made during the auto-assignment process.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.0,
        y: 92.8,
        width: 6.3,
        height: 2.9,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.4,
        y: 92.4,
        width: 7.4,
        height: 3.5,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 46.1,
        y: 92.4,
        width: 7.3,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.4,
        y: 92.5,
        width: 7.1,
        height: 3.3,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
      {
        shape: "rect",
        x: 5.0,
        y: 19.7,
        width: 90.2,
        height: 11.0,
        targetScreenId: "teaching-slots",
        label: "Create Teaching Slots",
      },
      {
        shape: "rect",
        x: 4.6,
        y: 32.4,
        width: 90.8,
        height: 10.9,
        targetScreenId: "teaching-slots-preset",
        label: "Set Availability",
      },
      {
        shape: "rect",
        x: 4.8,
        y: 45.0,
        width: 90.5,
        height: 11.1,
        targetScreenId: "volunteers-preset",
        label: "Manage Volunteers",
      },
      {
        shape: "rect",
        x: 5.1,
        y: 57.4,
        width: 90.3,
        height: 11.1,
        targetScreenId: "generate-schedule",
        label: "Generate Schedule",
      },
      {
        shape: "rect",
        x: 6.0,
        y: 71.5,
        width: 88.4,
        height: 7.7,
        targetScreenId: "view-schedule",
        label: "View Assignments",
      },
    ],
    laserTheme: "light",
  },

  "ttw-profile": {
    id: "ttw-profile",
    screenshot: "/screenshots/ttw-profile.jpg",
    pageName: "Volunteer TTW Profile",
    pageDescription: 'A personalized view for TTW volunteers showing their <span class="highlight_text">subject preferences</span>, assigned teaching slot presets, and <span class="highlight_text">availability configuration</span> within the scheduling system.',
    featureTitle: "Volunteer Settings",
    hookLine: "Your profile. Your schedule.",
    techTags: [
      { emoji: "📚", label: "Subject Preferences", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Firestore Profile", color: "var(--accent-emerald)" },
      { emoji: "⚙️", label: "Availability Config", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📚",
        title: "Subject Preference Ranking",
        description: "Volunteers drag-rank their subject preferences in an ordered list that is persisted back to the 'ttwStudents' Firestore document, directly influencing the TFV algorithm's volunteer-to-slot matching.",
      },
      {
        icon: "📅",
        title: "Slot Availability Declaration",
        description: "Volunteers mark their availability against each teaching slot preset's schedule grid, writing a structured day-slot map to Firestore that the ScheduleGenerationViewModel reads during assignment scoring.",
      },
      {
        icon: "🔄",
        title: "Live Profile Sync",
        description: "Profile data is fetched fresh on every composition from the 'ttwStudents' collection, ensuring the UI always reflects the latest group assignment, class count, and subject priority list without stale cache reads.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.9,
        y: 93.5,
        width: 6.5,
        height: 3.1,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.2,
        y: 93.2,
        width: 7.6,
        height: 3.3,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 46.5,
        y: 92.9,
        width: 6.8,
        height: 3.8,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 56.3,
        y: 93.0,
        width: 7.3,
        height: 3.7,
        targetScreenId: "ttw-scheduling",
        label: "History",
      },
      {
        shape: "rect",
        x: 72.7,
        y: 3.5,
        width: 8.4,
        height: 3.5,
        targetScreenId: "nss-home",
        label: "Operations Feed",
      },
      {
        shape: "rect",
        x: 85.6,
        y: 3.4,
        width: 6.3,
        height: 3.5,
        targetScreenId: "ttw-options",
        label: "Options",
      },
    ],
    laserTheme: "light",
  },

  "ttw-options": {
    id: "ttw-options",
    screenshot: "/screenshots/options.jpg",
    pageName: "TTW Options",
    pageDescription: 'A dedicated settings panel for the TTW interface, providing access to <span class="highlight_text">account preferences</span>, notification controls, and module-level configurations.',
    featureTitle: "Module Settings",
    hookLine: "Configure your experience.",
    techTags: [
      { emoji: "⚙️", label: "Settings Panel", color: "var(--accent-cyan)" },
      { emoji: "🔔", label: "Notifications", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⚙️",
        title: "Module-Level Config",
        description: "TTW-specific preferences are stored separately from the global app settings, allowing volunteers to customise their teaching interface without affecting other module states.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 39.0,
        y: 8.6,
        width: 50.6,
        height: 5.6,
        targetScreenId: "manage-students",
        label: "Manage Students",
      },
      {
        shape: "rect",
        x: 38.2,
        y: 14.8,
        width: 51.3,
        height: 5.6,
        targetScreenId: "manage-subjects",
        label: "Manage Subjects",
      },
      {
        shape: "rect",
        x: 27.0,
        y: 93.4,
        width: 6.5,
        height: 3.1,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.4,
        y: 93.1,
        width: 7.2,
        height: 3.5,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 46.8,
        y: 93.0,
        width: 6.7,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        x: 35.7,
        y: 92.9,
        width: 8.3,
        height: 3.6,
        targetScreenId: "class-groups",
        label: "Feed",
      },
      {
        shape: "rect",
        x: 46.2,
        y: 92.8,
        width: 7.8,
        height: 3.9,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 55.8,
        y: 92.7,
        width: 8.1,
        height: 3.9,
        targetScreenId: "ttw-scheduling",
        label: "History",
      },
    ],
    laserTheme: "light",
  },

  "manage-subjects": {
    id: "manage-subjects",
    screenshot: "/screenshots/manage-subject.jpg",
    pageName: "Manage Subjects",
    pageDescription: 'A configuration screen for <span class="highlight_text">defining and editing the subject catalogue</span> used across the TTW scheduling system — each subject tied to slot compatibility rules.',
    featureTitle: "Subject Catalogue",
    hookLine: "Define what gets taught.",
    techTags: [
      { emoji: "📚", label: "Subject Config", color: "var(--accent-amber)" },
      { emoji: "🔗", label: "Slot Binding", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📚",
        title: "Subject Definitions",
        description: "Each subject entry maps to a set of compatible teaching slot types, constraining the TFV algorithm to only match volunteers who have listed that subject as a preference.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },


  "teaching-slots": {
    id: "teaching-slots",
    screenshot: "/screenshots/Teaching-slots.jpg",
    pageName: "Create Teaching Slots",
    pageDescription: 'Admin interface for configuring the core <span class="highlight_text">teaching session building blocks</span>. Defines the base structure that volunteers will map against.',
    featureTitle: "Slot Engine",
    hookLine: "Define the backbone of sessions.",
    techTags: [
      { emoji: "🧩", label: "TeachingSlotsScreen", color: "var(--accent-purple)" },
      { emoji: "🔥", label: "Firestore Slots Core", color: "var(--accent-cyan)" },
      { emoji: "⚡", label: "Suspending Deletes", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧩",
        title: "Dynamic Slot Construction",
        description: "Admins dynamically spin up teaching slots that behave as atomic chunks for the assignment algorithm, mapped securely inside Firebase Collections.",
      },
      {
        icon: "⚡",
        title: "Suspending Mutators",
        description: "Firestore mutations (like deleteTeachingSlot) are wrapped tightly in Kotlin coroutines, ensuring the UI remains buttery smooth while remote data syncing is processed.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 4.7,
        y: 9.5,
        width: 91.0,
        height: 32.8,
        targetScreenId: "edit-slots",
        label: "Edit Slot",
      },
    ],
    laserTheme: "light",
  },

  "teaching-slots-preset": {
    id: "teaching-slots-preset",
    screenshot: "/screenshots/teaching-slots-preset.jpg",
    pageName: "Set Availability",
    pageDescription: 'This interface enables admins to configure <span class="highlight_text">Availability Presets</span> across designated days, giving volunteers a rigid template to select their free time against.',
    featureTitle: "Availability Configurator",
    hookLine: "Templated volunteer schedules.",
    techTags: [
      { emoji: "🧮", label: "SetAvailabilityScreen", color: "var(--accent-emerald)" },
      { emoji: "👥", label: "preset-templates", color: "var(--accent-purple)" },
      { emoji: "🗄️", label: "Firebase Integration", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "👥",
        title: "Interactive Presets",
        description: "Availability templates are treated as first-class presets. An admin can load, copy, or overwrite preset matrices before flushing the resulting multi-dimensional arrays to Firestore.",
      },
      {
        icon: "🧮",
        title: "Matrix Update Logic",
        description: "Instead of complex multi-modal flows, this component utilizes intelligent Set semantics to instantly update 'AvailabilitySlots' when time chunks are selected or deselected.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 4.6,
        y: 8.3,
        width: 91.1,
        height: 34.3,
        targetScreenId: "day",
        label: "Edit Day",
      },
    ],
    laserTheme: "light",
  },

  "volunteers-preset": {
    id: "volunteers-preset",
    screenshot: "/screenshots/volunteers-preset.jpg",
    pageName: "Manage Volunteers",
    pageDescription: 'An intuitive management dashboard for manipulating <span class="highlight_text">Volunteer Presets</span>. Essential for orchestrating varying workforce counts across complex assignments.',
    featureTitle: "Preset Grouping",
    hookLine: "Team orchestration.",
    techTags: [
      { emoji: "🙋", label: "VolunteerPresetsScreen", color: "var(--accent-cyan)" },
      { emoji: "🔀", label: "Merging Logic", color: "var(--accent-amber)" },
      { emoji: "🏷️", label: "GroupChips View", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔀",
        title: "Advanced Set Merging",
        description: "Enables admins to perform deep merges on selected combinations of VolunteerPreset IDs directly, resolving grouping conflicts at runtime.",
      },
      {
        icon: "🏷️",
        title: "Reactive Groups",
        description: "Data chips reflect live-bound UI updates via Compose Flow observers. Real-time class capacity counts stay fully synchronized.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 4.6,
        y: 8.3,
        width: 91.1,
        height: 34.3,
        targetScreenId: "add-free-groups",
        label: "View Groups",
      },
    ],
    laserTheme: "light",
  },

  "generate-schedule": {
    id: "generate-schedule",
    screenshot: "/screenshots/generate-schedule.jpg",
    pageName: "Schedule Generator",
    pageDescription: 'The core assignment workspace where <span class="highlight_text">ScheduleGenerationViewModel</span> leverages the Teacher Fulfillment Value (TFV) to automatically optimize placements, resolving conflicts before they happen.',
    featureTitle: "Generation Engine",
    hookLine: "Score. Assign. Optimize.",
    techTags: [
      { emoji: "🧠", label: "TFV Algorithm", color: "var(--accent-purple)" },
      { emoji: "⚡", label: "Auto-Assign Core", color: "var(--accent-cyan)" },
      { emoji: "📋", label: "Algorithm Tracing", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧠",
        title: "TFV Triage Engine",
        description: "Volunteers and available blocks are evaluated using an intensive TFV formula, enabling the app to 'weigh' assignment success probabilities dynamically.",
      },
      {
        icon: "⚡",
        title: "Algorithmic Coroutines",
        description: "The intensive auto-assignment loop operates cleanly inside a dedicated background thread on the ViewModel, preserving frame rates on the main UI even during intense pathfinding.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 79.4,
        y: 88.1,
        width: 14.4,
        height: 6.8,
        targetScreenId: "create-schedule",
        label: "Next",
      },
    ],
    laserTheme: "light",
  },

  "view-schedule": {
    id: "view-schedule",
    screenshot: "/screenshots/view-schedule.jpg",
    pageName: "View Assignments",
    pageDescription: 'Provides a highly visual, mapped-out <span class="highlight_text">TFV Grid View</span> showing the resulting assignments from the generation engine.',
    featureTitle: "Assignment Grid",
    hookLine: "The final blueprint.",
    techTags: [
      { emoji: "📊", label: "ViewAssignmentsScreen", color: "var(--accent-purple)" },
      { emoji: "🟩", label: "TFVGridView", color: "var(--accent-emerald)" },
      { emoji: "🧭", label: "Spatial Mapping", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🟩",
        title: "Custom Grid Renderers",
        description: "Uses a highly performant generic lazy-grid wrapped in TFVGridView mapping coordinates back to assignments without jitter.",
      },
      {
        icon: "📊",
        title: "Legend Key Generation",
        description: "Colors and statuses on the resultant grid are automatically translated by a dynamic composable legend, enforcing accessibility standards.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "edit-slots": {
    id: "edit-slots",
    screenshot: "/screenshots/edit-slots.jpg",
    pageName: "Edit Teaching Slot",
    pageDescription: 'A granular configuration overlay allowing admins to <span class="highlight_text">fine-tune slot boundaries</span>, active days, and designated time blocks for a specific session.',
    featureTitle: "Slot Mutator",
    hookLine: "Precision scheduling details.",
    techTags: [
      { emoji: "⚙️", label: "State Hoisting", color: "var(--accent-purple)" },
      { emoji: "🕒", label: "TimePicker API", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🕒",
        title: "Material Time selection",
        description: "Utilizes Compose Material3 TimePickers seamlessly integrated into the schedule modifier form for robust input validation.",
      },
      {
        icon: "🔄",
        title: "Immutable State Updates",
        description: "Form inputs are hoisted and mapped to immutable data models before they are synchronously patched to Firebase.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "add-free-groups": {
    id: "add-free-groups",
    screenshot: "/screenshots/edit-volunteers.jpg",
    pageName: "Merge Volunteer Groups",
    pageDescription: 'A focused selection interface giving admins visibility into real-time <span class="highlight_text">group distribution counts</span>, aiding in preset integration or combination tracking.',
    featureTitle: "Group Assigner",
    hookLine: "Intelligent cluster visualization.",
    techTags: [
      { emoji: "🏷️", label: "GroupChips View", color: "var(--accent-purple)" },
      { emoji: "⚡", label: "Real-time Metrics", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🏷️",
        title: "Dynamic Capacity Chips",
        description: "Group chips are rendered using Compose Flow, constantly updating their inner count (e.g. 'Gp 1: 5') based on the selected availability intersections in the ViewModel.",
      },
      {
        icon: "🛡️",
        title: "Conflict Avoidance",
        description: "Admins receive direct visual cues on capacity limits per group block before flushing any changes, guaranteeing scheduling bounds.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "day": {
    id: "day",
    screenshot: "/screenshots/add-free-groups.jpg",
    pageName: "Day Availability Editor",
    pageDescription: 'A micro-level view for a specific day inside an Availability Preset. Admins can toggle individual <span class="highlight_text">availability slots</span> on and off before finalizing the preset.',
    featureTitle: "Granular Time Config",
    hookLine: "Slot by slot precision.",
    techTags: [
      { emoji: "🧮", label: "AvailabilitySlot API", color: "var(--accent-purple)" },
      { emoji: "⚡", label: "State Hoisting", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "👆",
        title: "Toggle Architecture",
        description: "Uses hoisted Sets to instantly reflect selected availability timeslots without round-tripping to the database for every single click.",
      },
      {
        icon: "🗄️",
        title: "Batched Writing",
        description: "All selections for the day are batched and written synchronously to Firebase, significantly saving on unnecessary write operations.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "create-schedule": {
    id: "create-schedule",
    screenshot: "/screenshots/create-schedule.jpg",
    pageName: "Finalize Schedule",
    pageDescription: 'The final parameter check before triggering the TFV logic. Admins can <span class="highlight_text">configure fallback weights</span> or generation mode before executing.',
    featureTitle: "TFV Trigger",
    hookLine: "Commit to the match.",
    techTags: [
      { emoji: "⚡", label: "Runtime Logic", color: "var(--accent-orange)" },
      { emoji: "🛠️", label: "Parameter Tuning", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🚦",
        title: "Execution Sandbox",
        description: "Variables are held safely in a staging environment. The TFV engine only fires when this explicit intent is provided.",
      },
      {
        icon: "🔄",
        title: "Coroutine Dispatch",
        description: "Clicking Generate kicks off a long-running ViewModel coroutine on the Default dispatcher, avoiding main-thread blockages while paths calculate.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 71.3,
        y: 2.0,
        width: 10.5,
        height: 4.9,
        targetScreenId: "add-volunteers",
        label: "Add Volunteers",
      },
      {
        shape: "rect",
        x: 83.7,
        y: 2.0,
        width: 10.3,
        height: 4.9,
        targetScreenId: "assignment-log",
        label: "Assignment Logs",
      },
      {
        shape: "rect",
        x: 26.0,
        y: 73.4,
        width: 23.5,
        height: 6.4,
        targetScreenId: "assigned-volunteer",
        label: "Assigned Volunteer",
      },
      {
        shape: "rect",
        x: 52.6,
        y: 36.5,
        width: 23.4,
        height: 6.4,
        targetScreenId: "unassigned-slot",
        label: "Unassigned Slot",
      },
    ],
    laserTheme: "light",
  },

  "add-volunteers": {
    id: "add-volunteers",
    screenshot: "/screenshots/add-volunteers.jpg",
    pageName: "Add Volunteers",
    pageDescription: 'Admin console to <span class="highlight_text">assign specific roles</span> and manually inject selected volunteers directly into the schedule.',
    featureTitle: "Volunteer Manager",
    hookLine: "Manual insertion override.",
    techTags: [
      { emoji: "👤", label: "Profile Directory", color: "var(--accent-purple)" },
      { emoji: "📝", label: "Manual Override", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📝",
        title: "Direct Assignments",
        description: "Bypass the automatic generation constraints by manually pushing specific volunteers into required teaching slots.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "assignment-log": {
    id: "assignment-log",
    screenshot: "/screenshots/assignment-log.jpg",
    pageName: "Assignment Logs",
    pageDescription: 'A transparent audit trail showing all <span class="highlight_text">administrative changes</span> and schedule mutators applied during the current generation.',
    featureTitle: "Action Tracker",
    hookLine: "Trace every modifier.",
    techTags: [
      { emoji: "📋", label: "LazyColumn", color: "var(--accent-cyan)" },
      { emoji: "🗄️", label: "Session History", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📋",
        title: "Immutable Logging",
        description: "All assignments, swaps, and overrides are logged chronologically, providing full traceability for the finalized schedule.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "assigned-volunteer": {
    id: "assigned-volunteer",
    screenshot: "/screenshots/assigned-volunteer.jpg",
    pageName: "Assigned Volunteer",
    pageDescription: 'A detailed <span class="highlight_text">assignment card</span> showing a specific volunteer\'s placement and tracking their fulfillment value.',
    featureTitle: "Placement details",
    hookLine: "View the match.",
    techTags: [
      { emoji: "👤", label: "Volunteer Profile", color: "var(--accent-purple)" },
      { emoji: "🔗", label: "Runtime Binding", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔗",
        title: "Dynamic Resolutions",
        description: "Clicking a populated grid block dynamically resolves the associated Volunteer ID against the local roster cache.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "unassigned-slot": {
    id: "unassigned-slot",
    screenshot: "/screenshots/unassigned-slot.jpg",
    pageName: "Unassigned Slot",
    pageDescription: 'Highlights an <span class="highlight_text">unfilled block</span> in the generated schedule, allowing admins to invoke manual overrides or adjust fallback constraints.',
    featureTitle: "Gap Resolution",
    hookLine: "Fill the void.",
    techTags: [
      { emoji: "⚠️", label: "Conflict State", color: "var(--accent-amber)" },
      { emoji: "🧩", label: "Fallback Engine", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧩",
        title: "Fallback Options",
        description: "Empty slots immediately trigger the fallback suggestion engine to provide next-best alternatives based on TFV scores.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  }
};

export const START_SCREEN_ID = "first-screen";
