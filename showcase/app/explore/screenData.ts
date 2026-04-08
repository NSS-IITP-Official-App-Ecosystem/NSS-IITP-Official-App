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
  tooltipSide?: "left" | "right";
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
  builtBy: "Eshan" | "Ankesh" | "Both" | "Aditya Gupta";
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
    pageName: "Command Center",
    pageDescription:
      'A highly specialized routing and analytical terminal (<code>NssProfileFragment</code>) for Administrators. It bypasses student views to fetch global <span class="highlight_text">meta telemetry</span>, unlocks restricted ledgers, and compiles raw Firestore document arrays into comprehensive <span class="highlight_text">Excel matrix spreadsheets</span> entirely client-side.',
    featureTitle: "Data Hub",
    hookLine: "Analyze globally. Export locally.",
    techTags: [
      { emoji: "🧮", label: "Client Compute", color: "var(--accent-amber)" },
      { emoji: "📊", label: "Matrix Compilation", color: "var(--accent-emerald)" },
      { emoji: "⚖️", label: "Wing-Aware Penalties", color: "var(--accent-cyan)" },
      { emoji: "🔐", label: "Route Gating", color: "var(--accent-purple)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "📡",
        title: "Global Meta Telemetry",
        description:
          "Overrides the student listener logic by securely awaiting the `meta/statistics` singleton document, instantly resolving the operational scale by injecting `totalEvents` across the UI readouts.",
      },
      {
        icon: "🖨️",
        title: "Native Excel Compilation Engine",
        description:
          "Bypasses expensive Cloud Functions by utilizing a native `ExcelGenerator` to parse thousands of Firestore cross-collection documents directly into styled .xls workbook files entirely on the mobile CPU.",
      },
      {
        icon: "⚖️",
        title: "Wing-Aware Penalty Resolution",
        description:
          "Cross-references `user.wings` against specific `event.wings` to apply targeted absentee penalties (`-negativeHours`) specifically for mandatory occurrences during matrix compilation processing.",
      },
      {
        icon: "🔐",
        title: "Role-Gated Routing",
        description:
          "Restricts administrative functions using synchronous checks against `SessionManager.fetchUserType()`, securely intercepting and routing verified traffic to the restricted `EventHistoryActivity`.",
      },
      {
        icon: "🧬",
        title: "Cross-Collection Aggregation",
        description:
          "Stitches massive relational loops by mapping `NSS_Events_Attendence` schemas to `users` arrays (gated by userType 'Student'), asynchronously building a holistic, sorted data map for immediate administrative review.",
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
        label: "Switch to TTW",
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
    pageName: "NSS Administration",
    pageDescription: 'An elevated administrative panel providing rapid access to <span class="highlight_text">core system utilities</span>, including attendance matrix compilation, historical event logs, and secure session management.',
    featureTitle: "System Utilities",
    hookLine: "Extended admin tools. Real-time actions.",
    techTags: [
      { emoji: "📊", label: "ExcelGenerator", color: "var(--accent-emerald)" },
      { emoji: "⚖️", label: "Penalty Engine", color: "var(--accent-amber)" },
      { emoji: "🔐", label: "Session Termination", color: "var(--accent-rose)" },
      { emoji: "🔀", label: "Context Rendering", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📊",
        title: "Matrix Generation Engine",
        description: "Initiates the ExcelGenerator script to compile cross-collection Firestore data into a structured .xlsx spreadsheet ledger that is downloaded directly to local storage.",
      },
      {
        icon: "⚖️",
        title: "Attendance Penalty Engine",
        description: "During Excel generation the script dynamically checks users.eventsList against NSS_Events_Attendence, automatically applying negative hours to students who missed a mandatory event matching their wing.",
      },
      {
        icon: "📅",
        title: "Historical Event Retrieval",
        description: "Routes administrative users to the chronological event history log, enabling targeted 5-axis filtering of past NSS activities and attendance records.",
      },
      {
        icon: "🔀",
        title: "Context-Aware Rendering",
        description: "Dynamically configures action visibility and selectively displays the Event History and Export Attendance utilities only if the current user possesses isAdmin privileges in the active NSS interface.",
      },
      {
        icon: "🔐",
        title: "Robust Session Lifecycle",
        description: "Executes a secure authentication termination lifecycle utilizing Intent.FLAG_ACTIVITY_CLEAR_TASK to destroy the activity stack alongside clearing standard Firebase authentication sessions.",
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

  "nss-event-history": {
    id: "nss-event-history",
    screenshot: "/screenshots/event-history.jpg",
    pageName: "Event History Log",
    pageDescription: 'A searchable archive of all <span class="highlight_text">closed NSS events</span>, powered by a 5-axis filter pipeline. Admins can also <span class="highlight_text">resurrect past events</span> back to live status directly from this screen.',
    featureTitle: "Closed Event Archive",
    hookLine: "Query the past. Reactivate on demand.",
    techTags: [
      { emoji: "🔍", label: "Multi-Predicate Filter", color: "var(--accent-cyan)" },
      { emoji: "📅", label: "Material3 DatePicker", color: "var(--accent-amber)" },
      { emoji: "🏗️", label: "AttendanceViewModel", color: "var(--accent-purple)" },
      { emoji: "♻️", label: "PullToRefreshBox", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔬",
        title: "5-Axis Filter Pipeline",
        description:
          "Every filter operation runs five independent named predicates simultaneously — search, date range, wing, mandatory flag, and attendee visibility — all chained inside a single `remember(closedEvents, searchQuery, fromDate, toDate, selectedWing, mandatoryOnly)` block for zero-redundancy recomputation.",
      },
      {
        icon: "♻️",
        title: "Event Resurrection",
        description:
          "The 'Make Live' button on every card triggers `viewModel.makeEventLive(event)` followed by a forced `loadEvents(true)` refresh — effectively pulling a closed event back into the active QR session pool without any server-side function.",
      },
      {
        icon: "📅",
        title: "Dual-Format Date Parser",
        description:
          "The date range filter parses event timestamps with a two-stage fallback: it first tries the `dd MMM yyyy` display format, then falls back to ISO `yyyy-MM-dd` — silently absorbing both parse failures to prevent list crashes from malformed legacy records.",
      },
      {
        icon: "🎨",
        title: "Mandatory Amber Tinting",
        description:
          "Cards automatically switch their container background to `Color(0xFFFFFDE7)` whenever `event.isMandatory` is true — giving administrators an instant, at-a-glance visual distinction without needing to open any detail view.",
      },
      {
        icon: "🪪",
        title: "Document ID Name Extraction",
        description:
          "Event names are decoded directly from Firestore document IDs (format: `day_month_event_name`) by splitting on `_`, dropping the first two segments (day + month), and rejoining the rest — a compact parser that avoids a redundant display name field entirely.",
      },
    ],
    hotspots: [],
    laserTheme: "mixed",
  },

  "nss-faq": {
    id: "nss-faq",
    screenshot: "/screenshots/faq.jpg",
    pageName: "FAQ Management",
    pageDescription: 'A full <span class="highlight_text">live-editing CMS</span> for the app\'s help content — admins can drill into a 3-level tree of sections, subsections, and Q&A pairs, with <span class="highlight_text">in-place CRUD</span> backed by a real-time Firestore flow.',
    featureTitle: "Live Help CMS",
    hookLine: "Edit the docs. Ship the truth.",
    techTags: [
      { emoji: "🌳", label: "Tree Navigation", color: "var(--accent-emerald)" },
      { emoji: "📡", label: "Firestore Flow", color: "var(--accent-purple)" },
      { emoji: "🗂️", label: "Tab Auto-Select", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "Dual StateFlow", color: "var(--accent-amber)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🌳",
        title: "Universal Tree Cursor",
        description:
          "A single `FaqNode` data class represents any level of the FAQ hierarchy — ROOT_SECTION, SUBSECTION, or QUESTION — via a `FaqNodeType` discriminated union, with optional `section`, `subSection`, and `question` payloads providing full context at each depth without needing separate model types.",
      },
      {
        icon: "📚",
        title: "Stack-Driven Drill-Down",
        description:
          "`AdminFaqUiState` holds a `navigationStack: List<FaqNode>` as the single source of truth for screen depth. Entering a section pushes a node; going back pops it and `loadSectionContentForNode(previousNode)` reloads the parent — emptying the stack returns to the root section list.",
      },
      {
        icon: "🔙",
        title: "BackHandler Dual-Mode Exit",
        description:
          "A `BackHandler(enabled = true)` intercepts the hardware back button with context-aware logic: if the `navigationStack` is non-empty it calls `viewModel.navigateBack()` to go up a level; if at root it calls `onNavigateBack()` to exit the activity entirely.",
      },
      {
        icon: "🗂️",
        title: "Tab Auto-Selection Engine",
        description:
          "After fetching `SectionContent`, the ViewModel evaluates the returned data to auto-select the active tab: 'questions' if only questions exist, 'subsections' if only subsections, or 'subsections' as the default when both are present — eliminating any empty-tab flash.",
      },
      {
        icon: "🔄",
        title: "Dual StateFlow Architecture",
        description:
          "Screen state and dialog state are kept in two separate `MutableStateFlow`s — `_uiState` manages the navigation stack and section data, while `_dialogState` owns CRUD form data and operation type — ensuring dialog mutations never trigger full-screen recomposition.",
      },
    ],
    hotspots: [],
    laserTheme: "light",
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
        label: "Messages",
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
        label: "Scheduling",
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

  "nss-calendar": {
    id: "nss-calendar",
    screenshot: "/screenshots/calender.jpg",
    pageName: "Event Calendar",
    pageDescription:
      'An interactive graphical calendar built with Jetpack Compose that orchestrates upcoming and past NSS events. It enforces <span class="highlight_text">role-based visibility</span>, allowing volunteers to track their schedule while empowering admins to <span class="highlight_text">orchestrate new events</span> directly from the grid.',
    featureTitle: "Graphical Scheduling Engine",
    hookLine: "Visualize impact. Schedule with intent.",
    techTags: [
      { emoji: "📅", label: "LazyVerticalGrid", color: "var(--accent-amber)" },
      { emoji: "🚦", label: "Role Dialogs", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "StateFlows", color: "var(--accent-emerald)" },
      { emoji: "🗄️", label: "Firestore DB", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📅",
        title: "Dynamic Visual Grid",
        description:
          "Constructs a fully interactive scheduling interface using Jetpack Compose's LazyVerticalGrid, mathematically mapping month boundaries and day indices into a performant, scrollable matrix.",
      },
      {
        icon: "🚦",
        title: "Strict Role-Based Filtering",
        description:
          "Implements a multi-layered visibility engine evaluating local SessionManager state. Admins gain unrestricted tracking, while volunteers only see events tied to their assigned wings or attendance history.",
      },
      {
        icon: "🛡️",
        title: "Privileged Action Dialogs",
        description:
          "Contextually alters tap interactions. Selecting a date grants admins immediate access to a 'Create Event' pipeline, whereas standard users are restricted to viewing contextual attendance statuses.",
      },
      {
        icon: "🏗️",
        title: "Architectural State Hoisting",
        description:
          "Leverages a centralized QRAttendanceViewModel combined with a clean repository pattern, funneling Firestore operations through Kotlin StateFlows to assure unidirectional reactivity.",
      },
      {
        icon: "⚡",
        title: "Real-time Event Hydration",
        description:
          "Integrates Material3's PullToRefreshBox to provide an intuitive mechanism for fetching live database updates, instantly re-evaluating wing assignments without tearing down the UI.",
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
    pageName: "Day Events Inspector",
    pageDescription:
      'A modal scheduling inspector activated via the calendar grid. It dynamically renders chronological event cards and empowers admins to <span class="highlight_text">monitor attendee counts</span> or instantly trigger the <span class="highlight_text">Create Event pipeline</span>.',
    featureTitle: "Admin Day View",
    hookLine: "Inspect events. Orchestrate schedules.",
    techTags: [
      { emoji: "🪟", label: "AlertDialog", color: "var(--accent-amber)" },
      { emoji: "🕒", label: "Time Parser", color: "var(--accent-cyan)" },
      { emoji: "📋", label: "LazyColumn", color: "var(--accent-emerald)" },
      { emoji: "🚦", label: "Role Context", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⏱️",
        title: "Chronological Execution Pipeline",
        description:
          "Evaluates the raw list of daily events, invoking a custom AttendanceEventUtils time parser to mathematically sort elements by ascending start time for chronological analysis.",
      },
      {
        icon: "🛡️",
        title: "Restricted Mutation Privilege",
        description:
          "Conditionally mounts a 'Create Event' action button within the AlertDialog footer exclusively if the active session belongs to an administrator, securely gating database writes.",
      },
      {
        icon: "📊",
        title: "Role-Aware Metric Badges",
        description:
          "Radically shifts the component UI based on identity. While standard users see an attendance status icon, admins are presented with a real-time getAttendeeCount() pill-shaped metric block.",
      },
      {
        icon: "🧩",
        title: "Dynamic Hierarchy Mapping",
        description:
          "Renders a constrained Jetpack Compose LazyColumn populated with intelligent EventDetailsCards. It conditionally maps complex metadata like Wing mappings and Mandatory status into cohesive visual tiers.",
      },
      {
        icon: "👆",
        title: "Interactive Detail Expansion",
        description:
          "Implements an expandable description constraint system using Compose's mutableStateOf. It restricts text to a soft character limit while preserving the ability for admins to toggle full-text contexts.",
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
      {
        icon: "🛡️",
        title: "Manual Roll Overrides",
        description:
          "Integrates fail-safe mutation pathways natively within the event card, providing administrators immediate dialogs to process Add/Remove attendance requests without scanning.",
      },
      {
        icon: "🧩",
        title: "Constraint-Aware Dialogs",
        description:
          "Automates data integrity during event creation by programmatically enforcing mutual exclusivity between states (e.g., toggling 'Visible Only to Present' immediately disables 'Mandatory').",
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
      {
        shape: "rect",
        x: 54.2,
        y: 50.0,
        width: 31.9,
        height: 6.0,
        targetScreenId: "attendance-record",
        label: "Attendance Log",
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
        icon: "📊",
        title: "Real-Time Ingress Telemetry",
        description:
          "Projects a native StateFlow binding (`uiState.attendeeCount`) directly into a custom-styled Compose badge, driving instantaneous zero-latency updates to the administrator's dashboard as students scan in.",
      },
      {
        icon: "📽️",
        title: "Projection-Optimized Rendering",
        description:
          "The QR bitmap is structurally bounded by a strict matrix (400.dp fixed size, ContentScale.Fit) heavily engineered to ensure maximum scanning reliability when projected onto large surfaces in lecture halls.",
      },
      {
        icon: "🎨",
        title: "Context-Aware Visual Overlays",
        description:
          "Leverages deep Compose recomposition to globally inject a distinct warning palette (Color 0xFFFFFDE7) across the entire UI background structure strictly if the underlying event struct flags 'isMandatory'.",
      },
      {
        icon: "🛜",
        title: "Pre-Flight Geolocation Gating",
        description:
          "The fragment enforces a rigorous hardware-level permissions gauntlet via `LocationPermissionHelper` before the `ActiveSessionScreen` is permitted to boot, ensuring admin devices cannot spoof origins.",
      },
      {
        icon: "⚖️",
        title: "Multi-Variant Credit Readout",
        description:
          "Dynamically parses complex event schemas within the Compose tree to unify positive (`event.hours`) and negative absentee penalties (`event.negativeHours`) into a singular, prominent metric line.",
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
      'A Jetpack Compose-driven Dialog interface (<code>CreateEventDialog</code>) responsible for configuring and structuring new <span class="highlight_text">QR attendance schemas</span>. It enforces strict temporal validation, mutual exclusivity for mandatory flags, and dedicated wing-based <span class="highlight_text">visibility constraints</span> before pushing data payloads to Firestore.',
    featureTitle: "Session Provisioning",
    hookLine: "Build sessions. Enforce constraints.",
    techTags: [
      { emoji: "🛡️", label: "Regex Input Validation", color: "var(--accent-amber)" },
      { emoji: "⏱️", label: "Temporal Constraints", color: "var(--accent-cyan)" },
      { emoji: "🔀", label: "Mutual Exclusivity", color: "var(--accent-emerald)" },
      { emoji: "🚧", label: "State Blocking", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🛡️",
        title: "Dynamic Input Validation Pipeline",
        description:
          "Enforces real-time validation for hour assignments using dynamic Regex (`^\\d*\\.?\\d*$`) combined with standard limits, locking users out of malformed inputs directly through Jetpack Compose's inline `OutlinedTextField` supporting-text states.",
      },
      {
        icon: "🔀",
        title: "Context-Aware Event Modeling",
        description:
          "Manages complex event parameters by structurally linking `isMandatory` and `visibleOnlyToPresent` booleans with a mutually exclusive `toggleable` modifier switch to prevent logical conflicts upon database submission.",
      },
      {
        icon: "⏱️",
        title: "Atomic Temporal Constraints",
        description:
          "Utilizes local cross-referencing utilities (`AttendanceEventUtils.validateEventTimes`) to guarantee opening times strictly precede closing times, instantly throwing localized errors if constraints are violated prior to Firebase initialization.",
      },
      {
        icon: "🎯",
        title: "Targeted Deployment Scopes",
        description:
          "Implements scoped audience parameters by allowing administrators to check explicit operational wings (e.g., Teaching, Chetna), translating the selection array into specific Firestore visibility indices.",
      },
      {
        icon: "🚧",
        title: "Interactive Scaffold Protection",
        description:
          "Immediately triggers an `isCreating` overlay block encompassing a `CircularProgressIndicator` during network transmission, thoroughly locking all parameter states and preventing duplicate dialog dismissals.",
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
      { emoji: "⚡", label: "StateFlow Rx", color: "var(--accent-purple)" },
      { emoji: "📦", label: "Batch Writes", color: "var(--accent-pink)" },
    ],
    builtBy: "Aditya Gupta",
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
      {
        icon: "⚡",
        title: "StateFlow Reactive Pipeline",
        description: "Volunteers are cached locally in a MutableStateFlow and reactively filtered via a .combine() operator. This decouples network latency from the UI, ensuring instant 60fps list rendering as the administrator types.",
      },
      {
        icon: "📦",
        title: "Atomic Write Batching",
        description: "When applying group assignments, modified volunteer records are pooled and dispatched as a unified Firestore WriteBatch. This minimizes network round-trips and guarantees atomic state integrity across the collection.",
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
        label: "Scheduling",
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
    pageName: "Teaching Calendar",
    pageDescription: 'A personalized scheduling interface for TTW volunteers to manage their school classes. Volunteers can view assigned slots, request localized leave, and proactively apply to cover classes for their peers.',
    featureTitle: "Substitute Management",
    hookLine: "Never leave a class unattended.",
    techTags: [
      { emoji: "🙋", label: "Leave Requests", color: "var(--accent-purple)" },
      { emoji: "🤝", label: "Peer Covering", color: "var(--accent-cyan)" },
      { emoji: "🏫", label: "Class Tracking", color: "var(--accent-emerald)" },
      { emoji: "🛑", label: "Conflict Checks", color: "var(--accent-amber)" },
      { emoji: "📱", label: "Broadcast Logic", color: "var(--accent-pink)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🏫",
        title: "Assigned Class Tracking",
        description: "Volunteers can tap any calendar date to instantly view their specific teaching assignments for the day, complete with subject details, exact timings, and precise venue locations.",
      },
      {
        icon: "🙋",
        title: "Dynamic Sub Requests",
        description: "If a volunteer cannot attend an assigned class, they can flag their slot as 'Open for Sub'. This securely triggers a state change that broadcasts the open slot to the broader TTW volunteer network.",
      },
      {
        icon: "🤝",
        title: "Peer-to-Peer Covering",
        description: "Other volunteers browsing the calendar can instantly discover slots marked as open. With a single tap, they can apply to cover the class, dynamically transferring the teaching responsibility and ensuring continuity.",
      },
      {
        icon: "🛑",
        title: "Conflict Prevention Validation",
        description: "Before a peer can successfully claim an 'Open for Sub' slot, the system validates their existing calendar assignments to prevent double-booking or physically impossible transit times between concurrent school locations.",
      },
      {
        icon: "📱",
        title: "Broadcast Verification",
        description: "When a slot is successfully covered, a backend Cloud Function intercepts the robust Firestore write to dispatch targeted push notifications to both the original assignee and the substitute, finalizing the transfer seamlessly.",
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
        label: "Messages",
      },
      {
        shape: "rect",
        x: 56.3,
        y: 93.1,
        width: 7.2,
        height: 3.5,
        targetScreenId: "ttw-scheduling",
        label: "Scheduling",
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
      { emoji: "🎛️", label: "Component Modularization", color: "var(--accent-emerald)" },
      { emoji: "📍", label: "Parameter Routing", color: "var(--accent-cyan)" },
      { emoji: "📏", label: "Scroll Memory", color: "var(--accent-amber)" },
      { emoji: "🧱", label: "BorderStrokes", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📏",
        title: "Vertical Scroll Preservation",
        description: "Employs 'rememberScrollState()' across the central Column payload to guarantee fluid vertical mobility across the administration actions while gracefully retaining scroll-y offsets during recompositions.",
      },
      {
        icon: "🎛️",
        title: "Rigid Component Delegation",
        description: "The primary options aggressively reduce screen boilerplate by abstracting strictly into the 'StaggeredMenuButton' composable, standardizing the distinct yellow-accented Material theme globally.",
      },
      {
        icon: "📍",
        title: "Deep-Linked Parameter Routing",
        description: "The onClick Lambdas directly invoke the NavController to hurdle into tightly bounded features — seamlessly passing explicit query parameters like '?destination=setAvailability' within the route primitive.",
      },
      {
        icon: "🧱",
        title: "Explicit Outlined Boundaries",
        description: "Execution tasks (like viewing the final schedule) are isolated from configuration tasks via an 'OutlinedButton', utilizing explicit 3dp BorderStrokes and shape-corners to command immediate visual priority.",
      },
      {
        icon: "🛡️",
        title: "Lower-Bound Occlusion Safety",
        description: "Administers a brute-force 'Spacer(modifier = Modifier.height(100.dp))' inject at the lowest vertical bounds, acting as a robust layout hack to guarantee accessibility behind the floating BottomNavigationView.",
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
        label: "Messages",
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

  "teaching-slots": {
    id: "teaching-slots",
    screenshot: "/screenshots/Teaching-slots.jpg",
    pageName: "Teaching Slots",
    pageDescription: 'A preset library for <span class="highlight_text">managing</span> reusable configurations of class times and subject demands.',
    featureTitle: "Preset Registry",
    hookLine: "Architect the curriculum.",
    techTags: [
      { emoji: "⚡", label: "Real-time Sync", color: "var(--accent-purple)" },
      { emoji: "🧩", label: "Dynamic Layout", color: "var(--accent-emerald)" },
      { emoji: "🗑️", label: "Cascade Delete", color: "var(--accent-amber)" },
      { emoji: "🧮", label: "Natural Sort", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⚡",
        title: "Real-Time Coroutine Mapping",
        description: "Executes a 'LaunchedEffect' bound to a 'refreshTrigger', suspending to map unstructured Firestore maps strictly into typed 'TimeSlotInfo' and 'SubjectInfo' dataclasses on the fly.",
      },
      {
        icon: "🧮",
        title: "Zero-Padded Natural Sort Key",
        description: "Injects a Regex algorithm to dynamically zero-pad integers within alphanumeric document IDs, enforcing perfect lexicographical sorting so 'AM 9' flawlessly precedes 'AM 10'.",
      },
      {
        icon: "🗑️",
        title: "Referential Cascade Deletion",
        description: "Document deletion structurally annihilates all nested volunteer availability references inherently tied to it, bypassing the need to check isolated sub-collections across the DB node.",
      },
      {
        icon: "🧩",
        title: "Dynamic Chunked Grids",
        description: "Subject capacity badges are mathematically batched using Kotlin's Iterable '.chunked(4)', programmatically injecting Spacer weights to construct a dense 4-column grid adapting to any DPI.",
      },
      {
        icon: "🛡️",
        title: "Stateful Recomposition Control",
        description: "Decouples network payloads via 'mutableStateOf<List<TeachingSlotItem>>' from the active UI thread, guaranteeing the visual array remains entirely butter-smooth while merging remote data.",
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
      {
        shape: "rect",
        x: 27,
        y: 93.4,
        width: 6.5,
        height: 2.8,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.3,
        y: 93.1,
        width: 7.4,
        height: 3,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.4,
        y: 93,
        width: 6.9,
        height: 3.4,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.5,
        y: 93.2,
        width: 6.6,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "dark",
  },

  "ttw-profile": {
    id: "ttw-profile",
    screenshot: "/screenshots/ttw-profile.jpg",
    pageName: "TTW Admin Command",
    pageDescription: 'The central profile hub tailored specifically for TTW administrators. From this interface, admins can invoke nested routing to manage student rosters, edit subject curriculums, and securely switch back to the main NSS interface.',
    featureTitle: "TTW Administration",
    hookLine: "Manage the teaching network.",
    techTags: [
      { emoji: "👥", label: "Student Bundles", color: "var(--accent-emerald)" },
      { emoji: "📚", label: "Subject Taxonomy", color: "var(--accent-cyan)" },
      { emoji: "🔀", label: "State Purging", color: "var(--accent-purple)" },
      { emoji: "❓", label: "FAQ Routing", color: "var(--accent-amber)" },
      { emoji: "🔐", label: "Session Lifecycle", color: "var(--accent-pink)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "👥",
        title: "Nested Student Management",
        description: "The menu routes administrators directly to the scheduling framework. By dynamically injecting the 'manageStudents' bundle payload, the NavController mounts the student roster configuration state without redundant Fragment creation.",
      },
      {
        icon: "📚",
        title: "Subject Taxonomy Editor",
        description: "Admins can manage curriculums by delegating a 'manageSubjects' argument to the shared scheduling engine. This isolates the capability to define overarching grade levels and tracks for the entire TTW school network.",
      },
      {
        icon: "🔀",
        title: "Cross-Interface Purging",
        description: "The 'Switch Interface' menu option instantly transitions the session from TTW back to the core NSS environment, safely passing 'FLAG_ACTIVITY_CLEAR_TASK' to destroy old back-stack instances and isolate memory states.",
      },
      {
        icon: "❓",
        title: "FAQ Central Routing",
        description: "The profile acts as the launch point for the application's help module. Tapping the FAQ trigger fires an Intent for FaqActivity, granting admins a dedicated environment to construct nested, tree-based help hierarchies.",
      },
      {
        icon: "🔐",
        title: "Graceful Auth Termination",
        description: "The logout workflow handles complete session closure. It safely surfaces a destructive confirmation dialog before sequentially purging local SharedPreferences cache, invalidating the Firebase Auth token, and pushing a clean login state.",
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
        label: "Messages",
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
        label: "Scheduling",
      },
      {
        shape: "rect",
        x: 72.7,
        y: 3.5,
        width: 8.4,
        height: 3.5,
        targetScreenId: "nss-home",
        label: "Switch to NSS",
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
    pageDescription: 'The administrative control panel for the TTW module. It utilizes hoisted Compose state and <span class="highlight_text">SharedPreferences</span> to execute instant <span class="highlight_text">subject overrides</span> without triggering costly network round-trips.',
    featureTitle: "Module Administration",
    hookLine: "Context-aware controls. Instant state mutations.",
    techTags: [
      { emoji: "💬", label: "Compose Dialog", color: "var(--accent-purple)" },
      { emoji: "💾", label: "SharedPreferences", color: "var(--accent-amber)" },
      { emoji: "📨", label: "Intent Routing", color: "var(--accent-cyan)" },
      { emoji: "🧠", label: "SessionManager", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "👻",
        title: "Hidden Admin Interface",
        description: "Regular student volunteers never even see these options. The dropdown automatically hides its administrative actions unless the session detects elevated permissions, keeping the volunteer UI completely clutter-free.",
      },
      {
        icon: "⚡",
        title: "Instant Subject Switching",
        description: "Need to manage Chemistry instead of Physics? Admins can swap their target subject on the fly. The modification applies instantly across all downstream TTW screens without requiring a full app reload.",
      },
      {
        icon: "🧲",
        title: "Sticky Context Memory",
        description: "The Manage Students routing automatically intercepts the active teaching subject from the SessionManager cache, embedding it directly into the Intent payload so downstream screens launch pre-scoped to the active context.",
      },
      {
        icon: "💾",
        title: "Local State Mutations",
        description: "The Change Subjects action surfaces a hoisted Compose dialog that writes selections directly to local SharedPreferences, deliberately skipping expensive backend user-profile updates for purely local filtering changes.",
      },
      {
        icon: "💥",
        title: "Backstack Annihilation",
        description: "Logging out doesn't just sign out the user—it fires an Intent.FLAG_ACTIVITY_CLEAR_TASK command to completely obliterate the activity stack, ensuring no secure administrative data can be accessed via the Android 'Back' button post-logout.",
      }
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
        label: "Messages",
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
        shape: "rect",
        x: 35.7,
        y: 92.9,
        width: 8.3,
        height: 3.6,
        targetScreenId: "class-groups",
        label: "Messages",
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
        label: "Scheduling",
      },
    ],
    laserTheme: "light",
  },

  "manage-students": {
    id: "manage-students",
    screenshot: "/screenshots/manage-students.jpg",
    pageName: "Student Operations",
    pageDescription: 'An administrative interface for <span class="highlight_text">searching and auditing the student roster</span>. It allows Admins to view academic groups, review roll numbers, and selectively modify interview scores based on dynamic search filters.',
    featureTitle: "Roster Management",
    hookLine: "Audit the student base.",
    techTags: [
      { emoji: "⚡", label: "Smart Search", color: "var(--accent-purple)" },
      { emoji: "🔄", label: "Optimistic UI", color: "var(--accent-emerald)" },
      { emoji: "🛡️", label: "Type Safety", color: "var(--accent-amber)" },
      { emoji: "📊", label: "In-Memory Sort", color: "var(--accent-cyan)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⚡",
        title: "Dynamic Search Indexing",
        description: "Features an active LaunchedEffect that instantly filters the student list by matching the search query against both the volunteer's name and their rollNumber, driven by a reactive isSearchActive state.",
      },
      {
        icon: "🔄",
        title: "Optimistic UI Mutations",
        description: "The updateStudentScore method executes local map transformations on the student collection immediately upon receiving a Firestore success callback, ensuring the UI feels exceptionally snappy without needing a costly network refresh.",
      },
      {
        icon: "🛡️",
        title: "Runtime Type Casting",
        description: "The Firestore parsing block safely guards against schema anomalies by dynamically evaluating the interviewScore type (Long vs String) with a fallback to zero, preemptively halting runtime class cast exceptions.",
      },
      {
        icon: "📊",
        title: "Reactive Coroutine Sorting",
        description: "Instead of querying the backend with strict OrderBy clauses, the frontend maintains a memory-efficient 'remember(filteredStudents)' dependency tree to natively sort the dataset by descending interview scores.",
      },
      {
        icon: "🎯",
        title: "Surgical Document Updates",
        description: "When an Admin submits a new score, the client restricts the Database write solely to the 'interviewScore' field on the specific Document ID, preserving bandwidth and preventing accidental overwrites of parallel student data.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.1,
        y: 93.5,
        width: 6.1,
        height: 2.8,
        targetScreenId: "ttw-dashboard",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.6,
        y: 93.1,
        width: 7.5,
        height: 3.1,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.5,
        y: 93.2,
        width: 7.2,
        height: 3.3,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 56.3,
        y: 93.1,
        width: 7.1,
        height: 3.4,
        targetScreenId: "ttw-scheduling",
        label: "Scheduling",
      },
    ],
    laserTheme: "light",
  },

  "manage-subjects": {
    id: "manage-subjects",
    screenshot: "/screenshots/manage-subject.jpg",
    pageName: "Subject Catalogue",
    pageDescription: 'A global admin configuration tool for <span class="highlight_text">defining the TTW curriculum network</span>. Changes here broadcast down the entire database, safely restructuring student preferences on the fly.',
    featureTitle: "Catalog Operations",
    hookLine: "Manage the curriculum structure.",
    techTags: [
      { emoji: "⚡", label: "Firestore Batches", color: "var(--accent-purple)" },
      { emoji: "👁️", label: "DisposableEffect", color: "var(--accent-cyan)" },
      { emoji: "📡", label: "Array Syncing", color: "var(--accent-amber)" },
      { emoji: "🧩", label: "Composable UI", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "👁️",
        title: "Dynamic Nav Hiding",
        description: "The Subject Manager intercepts the view lifecycle using a Jetpack Compose DisposableEffect, forcefully suppressing the BottomNavigationView while active, and cleanly restoring it via onDispose.",
      },
      {
        icon: "📡",
        title: "Global Subject Registry",
        description: "The UI binds directly to the 'TTW_Subjects' Firestore collection via suspending Coroutines. New additions write immediately using the raw subject string as the root Document ID for O(1) reads.",
      },
      {
        icon: "⚡",
        title: "Compound Batch Injections",
        description: "Adding a new subject triggers 'updateAllStudentsOnAdd()', which chunks the 'ttwStudents' registry into arrays of 450 records, parallel-firing Firebase WriteBatches with 'FieldValue.arrayUnion' to update every remote device.",
      },
      {
        icon: "🔥",
        title: "Synchronous Purging",
        description: "On subject destruction, 'FieldValue.arrayRemove' systematically scrubs the deprecated string identifier from every active volunteer's 'subjectPreferences' array, preventing phantom scheduling assignments.",
      },
      {
        icon: "🧩",
        title: "Declarative Validation",
        description: "Client-side Composable states actively guard against duplicate subject injections and empty string payloads, preempting network calls and surfacing styled Material3 warnings before committing bad data.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "teaching-slots-preset": {
    id: "teaching-slots-preset",
    screenshot: "/screenshots/teaching-slots-preset.jpg",
    pageName: "Teaching Slots Presets",
    pageDescription: 'A live-fetched dashboard listing every <span class="highlight_text">Teaching Slot Preset</span>. Each card shows the preset name alongside a chunked grid of group frequency chips — built directly from the Firestore availability map.',
    featureTitle: "Preset Management Hub",
    hookLine: "Every slot. Every group. At a glance.",
    techTags: [
      { emoji: "📊", label: "GroupFrequencyDisplay", color: "var(--accent-purple)" },
      { emoji: "🔢", label: "Natural Sort", color: "var(--accent-cyan)" },
      { emoji: "🗑️", label: "FieldValue.delete()", color: "var(--accent-rose)" },
      { emoji: "🧮", label: "Range Expansion", color: "var(--accent-emerald)" },
      { emoji: "⚡", label: "LazyColumn", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📊",
        title: "Group Frequency Chips",
        description: "The `GroupFrequencyDisplay` composable reads the nested availability map, calls `calculateGroupFrequenciesFromMap()` to count how many slots each volunteer group covers, then renders the results as chunked rows of 5 yellow chips each.",
      },
      {
        icon: "🔢",
        title: "Natural Sort Algorithm",
        description: "The `naturalSortKey()` function pads all digit sequences in preset names to 10 characters before comparing, ensuring 'AM 9B' sorts before 'AM 10G' — standard lexicographic sorting would invert this order.",
      },
      {
        icon: "🗑️",
        title: "Surgical Field Deletion",
        description: "The `deleteAvailabilityData()` suspend function uses `FieldValue.delete()` — not a full document overwrite — to atomically remove only the `availability` nested map from the preset document, leaving all other fields untouched.",
      },
      {
        icon: "🧮",
        title: "Legacy Range Expansion",
        description: "The `expandGroupRanges()` function detects the old compressed 'start-end' regex pattern (e.g. '4-8') and explodes it into individual group strings. Modern entries in expanded comma format pass through directly, ensuring backward compatibility.",
      },
      {
        icon: "⚡",
        title: "Dual-Mode Navigation",
        description: "The screen accepts an optional `destination` parameter. When set to `'setAvailability'`, tapping a preset card navigates to `SetAvailabilityScreen` instead of the edit flow — enabling the same list to serve two distinct admin workflows.",
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
        label: "Edit Free Group",
      },
      {
        shape: "rect",
        x: 27.1,
        y: 93,
        width: 5.9,
        height: 2.9,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.6,
        y: 92.8,
        width: 7,
        height: 3.3,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.5,
        y: 92.7,
        width: 7,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.6,
        y: 92.9,
        width: 6.5,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "volunteers-preset": {
    id: "volunteers-preset",
    screenshot: "/screenshots/volunteers-preset.jpg",
    pageName: "Volunteer Presets",
    pageDescription: 'A management dashboard for <span class="highlight_text">Volunteer Preset</span> groups. Admins can create, rename, delete, and deep-merge presets — with class count aggregation resolved at commit time.',
    featureTitle: "Preset Orchestration",
    hookLine: "Merge. Rename. Orchestrate. Zero data loss.",
    techTags: [
      { emoji: "🔀", label: "Class Count Merge", color: "var(--accent-cyan)" },
      { emoji: "✏️", label: "Doc-ID Rename", color: "var(--accent-amber)" },
      { emoji: "🏷️", label: "GroupChips View", color: "var(--accent-purple)" },
      { emoji: "🔢", label: "Natural Sort", color: "var(--accent-emerald)" },
      { emoji: "🛡️", label: "Duplicate Guard", color: "var(--accent-rose)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔀",
        title: "Class Count Merge Engine",
        description: "When merging, `mergePresets()` fetches each selected preset's raw `volunteers` array from Firestore, then iterates using `rollNo` as the unique key. Duplicate volunteers have their `classCount` values summed rather than overwritten, ensuring zero data loss across presets.",
      },
      {
        icon: "✏️",
        title: "Document-ID Rename Strategy",
        description: "Firestore document IDs are immutable. The `renamePreset()` function works around this by reading the full source document, writing it under the new name as the document ID, then deleting the stale original — a three-step atomic rename.",
      },
      {
        icon: "🏷️",
        title: "Live Group Count Chips",
        description: "On load, `groupCounts` is first fetched from the stored Firestore field. If absent (legacy presets), it falls back to iterating the raw `volunteers` array in-memory, grouping by the 'group' key to derive live counts for the chip display.",
      },
      {
        icon: "🔢",
        title: "Natural Sort for Mixed Names",
        description: "The shared `naturalSortKey()` pads numeric substrings in preset names with leading zeros before sorting, guaranteeing that 'AM 9B' precedes 'AM 10G' — a critical correctness fix over default string comparison.",
      },
      {
        icon: "🛡️",
        title: "Merge Preview Guard",
        description: "Before committing, the UI requires the admin to type a new `mergedPresetName` and renders a live `mergePreviewText` summary of which presets will be combined. The merge call is blocked until this string binding is non-empty, preventing accidental overwrites.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 4.6,
        y: 9.4,
        width: 91.1,
        height: 36.9,
        targetScreenId: "add-free-groups",
        label: "Preset Details",
      },
      {
        shape: "rect",
        x: 27.1,
        y: 93,
        width: 6.1,
        height: 3,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.7,
        y: 92.7,
        width: 7.1,
        height: 3.1,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.7,
        y: 92.6,
        width: 6.8,
        height: 3.5,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 67,
        y: 93,
        width: 6,
        height: 3,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "generate-schedule": {
    id: "generate-schedule",
    screenshot: "/screenshots/generate-schedule.jpg",
    pageName: "Schedule Generator",
    pageDescription: 'A two-step preset configurator where admins pick one <span class="highlight_text">Volunteer Preset</span> and one or more <span class="highlight_text">Availability Presets</span> before firing the schedule engine — with Firestore filtering and natural sorting built in.',
    featureTitle: "Preset Configurator",
    hookLine: "Pick. Pair. Proceed.",
    techTags: [
      { emoji: "🔘", label: "Single-Select Radio", color: "var(--accent-purple)" },
      { emoji: "☑️", label: "Multi-Select Checkbox", color: "var(--accent-cyan)" },
      { emoji: "🔢", label: "Natural Sort", color: "var(--accent-amber)" },
      { emoji: "🔍", label: "Availability Filter", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔘",
        title: "Radio-Button Volunteer Preset Picker",
        description: "Volunteer presets render as a single-select radio list. Each row highlights its background with YellowAccent at 15% opacity and swaps the icon from RadioButtonUnchecked to RadioButtonChecked when tapped, making the active selection immediately obvious.",
      },
      {
        icon: "☑️",
        title: "Multi-Select Availability Preset Checkboxes",
        description: "Availability presets use CheckCircle / CheckCircleOutline toggle icons. Tapping a row adds or removes the preset from a remembered List<PresetItem>, allowing any number of school presets to be combined into a single schedule run.",
      },
      {
        icon: "🔍",
        title: "Availability-Map Firestore Filter",
        description: "The availability loader queries the teachingSlotPresets collection and silently drops any document whose 'availability' field is null or empty. Only presets that contain real day-slot data are surfaced to the admin, preventing empty schedule configurations.",
      },
      {
        icon: "🔢",
        title: "Natural Sort Algorithm",
        description: "Both preset lists are sorted by padding all embedded digit sequences to 10 characters via a Regex replace on the sort key. This prevents lexicographic mis-ordering where 'AM 10G' would otherwise appear before 'AM 9B'.",
      },
      {
        icon: "✅",
        title: "Validation-Gated FAB",
        description: "The green arrow FAB only becomes actionable once both a VP and at least one VA preset are selected. Tapping without a complete selection triggers a Snackbar with a specific error message rather than silently navigating to an empty creation screen.",
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
      {
        shape: "rect",
        x: 27.1,
        y: 93.5,
        width: 6.3,
        height: 2.8,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.5,
        y: 93.3,
        width: 7.2,
        height: 2.9,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.7,
        y: 93.3,
        width: 6.7,
        height: 3.1,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.8,
        y: 93.3,
        width: 6.6,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile",
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
      { emoji: "🔍", label: "Live Search", color: "var(--accent-cyan)" },
      { emoji: "📄", label: "PDF & Excel Export", color: "var(--accent-amber)" },
      { emoji: "🇺🇸", label: "School Pivot", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🟩",
        title: "Synchronised Dual-Axis Grid",
        description: "The assignment table renders with two independent horizontalScrollState instances sharing the same rememberScrollState(), so the header row and data rows scroll in perfect lockstep regardless of how many time-slot columns exist.",
      },
      {
        icon: "🔍",
        title: "First/Last-Name Prefix Search",
        description: "A LaunchedEffect watches searchQuery and fires a real-time filter that checks both the first and last token of a volunteer's name with startsWith(), plus subjectName, subjectCode, and rollNo — highlighting matching cells amber while dimming non-matches.",
      },
      {
        icon: "🇺🇸",
        title: "School Pivot with Section Chips",
        description: "A DropdownMenu lets the admin switch active schools; each switch triggers a LaunchedEffect that auto-selects all sections for that school. Sections then render as horizontally scrollable RoundedCorner Button chips, each toggling membership in a remembered Set<String>.",
      },
      {
        icon: "📄",
        title: "Dual-Format Export (PDF & XLSX)",
        description: "A FAB opens an AlertDialog presenting two export branches: Excel via ScheduleExcelGenerator.generateAndShareExcel() and PDF via a native PdfDocument Canvas renderer — both run inside a coroutine scope on Dispatchers.IO, sharing the file via FileProvider for cross-app compatibility.",
      },
      {
        icon: "🔢",
        title: "Alphanumeric Section Comparator",
        description: "Section labels ('10N', '9G') are sorted by a custom Comparator that splits on ' - ', extracts the integer prefix separately from the alphabetic suffix, and chains compareBy{} + thenBy{} chains, ensuring '9G' always precedes '10N' in both header and row order.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.9,
        y: 93.7,
        width: 6.6,
        height: 3.1,
        targetScreenId: "ttw-updates",
        label: "Home"
      },
      {
        shape: "rect",
        x: 36.3,
        y: 93.5,
        width: 7,
        height: 3.2,
        targetScreenId: "class-groups",
        label: "Messages"
      },
      {
        shape: "rect",
        x: 46.9,
        y: 93.5,
        width: 6.4,
        height: 3.2,
        targetScreenId: "ttw-calender",
        label: "Calendar"
      },
      {
        shape: "rect",
        x: 66.3,
        y: 93.6,
        width: 6.6,
        height: 3.2,
        targetScreenId: "ttw-profile",
        label: "Profile"
      },
      {
        shape: "rect",
        x: 79.3,
        y: 88.4,
        width: 14.5,
        height: 6.9,
        targetScreenId: "schedule",
        label: "Export Options"
      }
    ],
    laserTheme: "light",
  },

  "schedule": {
    id: "schedule",
    screenshot: "/screenshots/schedule.jpg",
    pageName: "Export Options",
    pageDescription: 'A native multi-format <span class="highlight_text">document renderer</span> that serializes real-time scheduling maps into portable sheets and reports directly on the device.',
    featureTitle: "Export Engine",
    hookLine: "Generate. Package. Share.",
    techTags: [
      { emoji: "🖨️", label: "Native PDF Canvas", color: "var(--accent-cyan)" },
      { emoji: "📊", label: "Apache POI Excel", color: "var(--accent-emerald)" },
      { emoji: "🔄", label: "Intent.createChooser", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "⚡",
        title: "Asynchronous IO Dispatch",
        description: "Heavy document rendering runs securely off the main thread via coroutineScope.launch { withContext(Dispatchers.IO) }, ensuring zero frame drops while an isExporting state locks the UI.",
      },
      {
        icon: "📐",
        title: "Native Canvas Formatting",
        description: "Constructs A4 dimensions using Android's native PdfDocument. Custom Paint objects map header backgrounds, stroke-weighted borders, and distinct typographies directly onto the Canvas without requiring external PDF libraries.",
      },
      {
        icon: "🔤",
        title: "Alphanumeric Token Sort",
        description: "Prior to plotting the table, a custom Comparator regroups sections by isolating numeric prefixes ('9' from '9G'). This guarantees multi-grade layouts always render sequential ascending grades (9, 10, 11) rather than raw string orders (10 before 9).",
      },
      {
        icon: "📈",
        title: "Localized Excel Compilation",
        description: "ScheduleExcelGenerator compiles the mapping payloads into stylized .xlsx workbooks completely on-device, bypassing API constraints or cloud dependency for private administrative exports.",
      },
      {
        icon: "🔗",
        title: "Secure FileProvider Beam",
        description: "Generated files are written to safe cache directories, wrapped as URIs via FileProvider, and broadcasted to Android's Intent.ACTION_SEND chooser — seamlessly hooking into WhatsApp, Email, or cloud drives.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "dark",
  },

  "edit-slots": {
    id: "edit-slots",
    screenshot: "/screenshots/edit-slots.jpg",
    pageName: "Edit Teaching Slot",
    pageDescription: 'A granular configuration overlay allowing admins to <span class="highlight_text">fine-tune slot boundaries</span>, active days, and designated time blocks for a specific session.',
    featureTitle: "Slot Mutator",
    hookLine: "Precision scheduling details.",
    techTags: [
      { emoji: "🎛️", label: "SwipeWheelPicker", color: "var(--accent-purple)" },
      { emoji: "🧬", label: "Regex Mutators", color: "var(--accent-cyan)" },
      { emoji: "🛡️", label: "Constraint Validation", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🎛️",
        title: "Coroutine Scroll Snapping",
        description: "The custom 'SwipeWheelPicker' leverages Compose's 'rememberLazyListState' with an active 'LaunchedEffect', executing a coroutine payload off-thread to fluidly snap to the closest focal index when scroll velocity terminates.",
      },
      {
        icon: "🔄",
        title: "Compound Wheel Delegation",
        description: "The 'TimeWheelPicker' completely abstracts the complex logic of bridging independent hour and minute Lazylists, pushing state symmetrically up the tree using an injected 'onTimeChange: (Int, Int) -> Unit' lambda.",
      },
      {
        icon: "🧬",
        title: "Regex String Deconstruction",
        description: "Re-entering edit mode reverse-engineers formatted persistence tokens (like '09:00-10:00') directly into discrete integer pairs using rigorous Regex matcher operations to populate the hoisted state variables.",
      },
      {
        icon: "🧊",
        title: "Immutable Array Swapping",
        description: "Deep array updates are executed by chaining '.toMutableList().apply { ... }.toList()', forcibly stripping reference memory to guarantee flawless UI recomposition triggers across the heavy GridView.",
      },
      {
        icon: "🛡️",
        title: "Synchronous Boundary Bounds",
        description: "Client-side numerical validation actively rejects temporal paradoxes (like ensuring end times strictly evaluate mathematically greater than start times) before permitting state merges or Firebase patch sequences.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.9,
        y: 93.6,
        width: 6.3,
        height: 3,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.5,
        y: 93.3,
        width: 7.3,
        height: 3.2,
        targetScreenId: "class-groups",
        label: "Notifications",
      },
      {
        shape: "rect",
        x: 46.3,
        y: 93.2,
        width: 7.5,
        height: 3.5,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.3,
        y: 93.4,
        width: 6.9,
        height: 3.3,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
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
      { emoji: "🔀", label: "Aggregation Logic", color: "var(--accent-cyan)" },
      { emoji: "⚡", label: "Async Math", color: "var(--accent-emerald)" },
      { emoji: "🛡️", label: "Safe Merging", color: "var(--accent-rose)" },
      { emoji: "📡", label: "State Hoisting", color: "var(--accent-amber)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🏷️",
        title: "Declarative Chip Grids",
        description: "The GroupChipsFlowLayout dynamically chunks the 'nonZeroGroups' list into uniform rows of five. This guarantees strict visual hierarchy for volunteer assignments before rendering the styled Material cards.",
      },
      {
        icon: "🔀",
        title: "Multi-Preset Aggregation",
        description: "Admins can select two or more existing presets. The system recursively traverses the inner volunteer arrays, gracefully resolving object overlaps before preparing the final merge payload.",
      },
      {
        icon: "⚡",
        title: "Asynchronous Mapping",
        description: "Data transformation functions (e.g., groupBy, mapValues) process the merged records natively in memory. They sum up active class counts natively without generating blocking thread delays.",
      },
      {
        icon: "🛡️",
        title: "Safeguarded Checkouts",
        description: "The confirmation layer enforces a strict barrier, displaying a live preview 'mergePreviewText' buffer. This demands an explicit new string binding before triggering any destructive 'setDocument' commands.",
      },
      {
        icon: "📡",
        title: "Deterministic Fallbacks",
        description: "Upon batch execution, the success callback automatically inspects a derived 'duplicateCount' integer. A SnackBar injects context-sensitive error strings detailing exact array reduction states.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.1,
        y: 93,
        width: 5.9,
        height: 2.9,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.6,
        y: 92.8,
        width: 7,
        height: 3.3,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.5,
        y: 92.7,
        width: 7,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.6,
        y: 92.9,
        width: 6.5,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "day": {
    id: "day",
    screenshot: "/screenshots/add-free-groups.jpg",
    pageName: "Set Availability",
    pageDescription: 'A grid-based interface for admins to configure group availability against class times within an <span class="highlight_text">Availability Preset</span>. Supports Excel ingestion, preset cloning, and surgical Firestore writes.',
    featureTitle: "Availability Configuration",
    hookLine: "Slot-level precision. Zero redundant writes.",
    techTags: [
      { emoji: "🧮", label: "Range Compressor", color: "var(--accent-purple)" },
      { emoji: "📂", label: "Excel Ingestion", color: "var(--accent-cyan)" },
      { emoji: "🔄", label: "Preset Cloning", color: "var(--accent-emerald)" },
      { emoji: "📡", label: "Surgical Updates", color: "var(--accent-amber)" },
      { emoji: "🔁", label: "Bidirectional Parsing", color: "var(--accent-rose)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🧮",
        title: "Range Compression Engine",
        description: "The `compressNumberRangesForDisplay()` function scans sorted integer lists and collapses sequences of 3+ consecutive group numbers into compact 'start-end' notation while deliberately leaving pairs as individual comma values.",
      },
      {
        icon: "📂",
        title: "Excel Slot Ingestion",
        description: "A file picker registered via `rememberLauncherForActivityResult` fires `ExcelAvailabilityParser.parseExcelAndGetFreeGroups()` in a coroutine. Parsed slots are surgically merged into the active mutable list by matching on exact `dayIndex` and `slotIndex` keys.",
      },
      {
        icon: "🔄",
        title: "Cross-Preset Cloning",
        description: "The `copyFromPreset()` function deliberately re-fetches the source document from Firestore rather than reading the in-memory list, which lacks the nested `availability` map. Slots are then remapped by day name to match the target preset's schedule structure.",
      },
      {
        icon: "📡",
        title: "Surgical Firestore Writes",
        description: "The `saveAvailabilityData()` function serializes selections into a nested `Map<String, Map<String, String>>` keyed by day name then slot index. It then fires a targeted `.update('availability', availabilityMap)` to avoid overwriting sibling fields on the preset document.",
      },
      {
        icon: "🔁",
        title: "Bidirectional Format Parsing",
        description: "The `expandNumberRanges()` function simultaneously handles the legacy hyphenated range format ('1-5') and the modern expanded comma format ('1,2,3,4'), enabling full backward compatibility as the app migrated its storage schema without a database migration.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 26.9,
        y: 93.5,
        width: 7,
        height: 3.3,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.7,
        y: 93.1,
        width: 6.9,
        height: 3.5,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.5,
        y: 93.2,
        width: 7.1,
        height: 3.6,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.2,
        y: 93.3,
        width: 7,
        height: 3.5,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "create-schedule": {
    id: "create-schedule",
    screenshot: "/screenshots/create-schedule.jpg",
    pageName: "Create Schedule",
    pageDescription: 'The live assignment workspace where admins manually fill or <span class="highlight_text">auto-assign volunteers to teaching slots</span> using the TFV round-robin engine — with real-time toast feedback and a crash-proof save flow.',
    featureTitle: "Assignment Workspace",
    hookLine: "Assign. Verify. Save.",
    techTags: [
      { emoji: "🧠", label: "TFV Round-Robin", color: "var(--accent-purple)" },
      { emoji: "🎨", label: "Urgency Coloring", color: "var(--accent-amber)" },
      { emoji: "📜", label: "Algorithm Logs", color: "var(--accent-cyan)" },
      { emoji: "💾", label: "Triple-Save Flow", color: "var(--accent-emerald)" },
      { emoji: "⚡", label: "Adjacency Guard", color: "var(--accent-rose)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🎨",
        title: "TFV-Driven Slot Urgency Coloring",
        description: "Every unassigned SlotItem reads its tfv score and renders a matching background: red (TFV ≤ 3), brown (≤ 5), blue-gray (≤ 10), or dark surface (> 10). A color-matched Badge overlays the exact TFV score, surfacing the most constrained slots at a glance without any admin manual triage.",
      },
      {
        icon: "🧠",
        title: "Round-Robin Auto-Assign FAB",
        description: "The AutoAwesome FAB calls assignNextSlotInRoundRobin() on the ViewModel, which picks the lowest-TFV unassigned slot and applies the adjacency + daily-limit guard before writing. If no valid volunteer exists, it sets showAutoAssignError instead of silently skipping, showing an explicit 'Assignment Failed' dialog with a YellowAccent OK button.",
      },
      {
        icon: "🪄",
        title: "Sliding Assignment Toast",
        description: "On every successful placement, lastAssignmentMessage emits a string via StateFlow. A LaunchedEffect catches it, sets showMessage = true, waits 3 seconds with delay(), then slides the card back out via slideOutHorizontally. The message slot is cleared with clearAssignmentMessage() after the exit animation so no stale text leaks into the next assignment.",
      },
      {
        icon: "📜",
        title: "Algorithm Log Viewer Dialog",
        description: "A LibraryBooks icon FAB opens AlgorithmLogDialog, which renders the ViewModel's algorithmLogs StateFlow as a scrollable LazyColumn of color-coded LogEntry rows. Each entry is typed (INFO, WARNING, ERROR) with a matching color, giving admins a full paper trail of every placement decision and rejection reason.",
      },
      {
        icon: "💾",
        title: "Triple-Preset Atomic Save Flow",
        description: "The Save FAB opens FinishDialog for a preset name input. On confirm, a coroutine fires three sequential writes: saveSchedule(), saveAssignedVolunteersPresets() (one preset per teaching slot), and saveUnassignedVolunteersPreset(presetName). isSavingSchedule locks the UI with a full-screen CircularProgressIndicator overlay until all three succeed, then pops the back stack to the scheduling dashboard.",
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
        label: "Volunteers Info",
        tooltipSide: "left",
      },
      {
        shape: "rect",
        x: 83.7,
        y: 2.0,
        width: 10.3,
        height: 4.9,
        targetScreenId: "assignment-log",
        label: "Assignment Logs",
        tooltipSide: "left",
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
      {
        shape: "rect",
        x: 27.1,
        y: 93.7,
        width: 5.8,
        height: 2.8,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.6,
        y: 93.3,
        width: 7.1,
        height: 3.2,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.6,
        y: 93.3,
        width: 7.1,
        height: 3.4,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.5,
        y: 93.5,
        width: 6,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "add-volunteers": {
    id: "add-volunteers",
    screenshot: "/screenshots/add-volunteers.jpg",
    pageName: "Volunteers Info",
    pageDescription: 'A full-screen roster dialog showing all volunteers with their <span class="highlight_text">assignment status</span> — filterable by state (All / Assigned / Unassigned), group, and sortable by group or name — giving admins a live assignment audit during schedule creation.',
    featureTitle: "Volunteer Roster Panel",
    hookLine: "Who's in. Who's out.",
    techTags: [
      { emoji: "🔁", label: "Tri-State FilterMode", color: "var(--accent-amber)" },
      { emoji: "👥", label: "VolunteersListDialog", color: "var(--accent-purple)" },
      { emoji: "📋", label: "LazyColumn", color: "var(--accent-cyan)" },
      { emoji: "🔍", label: "Group Grid Filter", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔁",
        title: "Tri-State Assignment Filter",
        description: "A single cycling Button toggles filterMode through 0 (All, YellowAccent), 1 (Assigned, green #2E7D32), and 2 (Unassigned, red #C62828). The button's containerColor and text color switch with each state via when() expressions, making the active filter immediately obvious without a dropdown.",
      },
      {
        icon: "🔍",
        title: "Multi-Group Grid Filter",
        description: "A Group button opens a grid dialog (75% width, max 450dp height) containing all distinct group values as square chips. Tapping a chip toggles its membership in a selectedGroups Set<String>. The button label dynamically reads 'All Gps', 'Gp X', 'Gps X,Y', or 'N Gps' depending on selection size.",
      },
      {
        icon: "📊",
        title: "Sort Toggle (Name vs Group)",
        description: "A FilterChip labeled 'Sort' drives sortByGroup: Boolean. When true, filteredVolunteers is sorted first by group number (parsed to Int with a fallback to Int.MAX_VALUE for non-numeric groups), then alphabetically by name. When false, results sort purely by name — matching the default admin workflow.",
      },
      {
        icon: "👥",
        title: "VolunteerListItem Dual-Column Card",
        description: "Each card in the LazyColumn splits into 45% (volunteer info: name, group+roll chips, class count + score) and 55% (assignment info: assigned subject in YellowAccent with rank suffix '#N', school/day in success green, or plain preference list when unassigned). Both columns use CenterHorizontally alignment and TextOverflow.Ellipsis.",
      },
      {
        icon: "🔢",
        title: "Numeric Group Sort Comparator",
        description: "Groups are sorted using a compareBy comparator that tries Integer.parseInt() on each group string. Non-numeric groups (e.g. 'A', 'B') receive Int.MAX_VALUE and sort last. The same comparator is reused in both the group dropdown and the main volunteer list to ensure consistent ordering across all filter views.",
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
    pageDescription: 'A transparent audit trail showing all <span class="highlight_text">algorithmic attempts</span>, constraint checks, and manual overrides applied during the current generation.',
    featureTitle: "Algorithmic Trace Card",
    hookLine: "Trace every modifier.",
    techTags: [
      { emoji: "🧮", label: "Stateful Grouping", color: "var(--accent-purple)" },
      { emoji: "📊", label: "Custom Scrollbar", color: "var(--accent-yellow)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🔗",
        title: "Stateful Log Grouping",
        description: "Logs are aggregated into 'LogGroup' or 'FailedBatch' structures inside a remember block. Skipped attempts are accumulated and natively attached as 'precedingSkippedAttempts' to the next successful assignment, preserving exact chronological context without cluttering the main list.",
      },
      {
        icon: "💳",
        title: "Premium Log Card Structure",
        description: "A dual-column card (35%/65% split) built with Row and Column weights. The left column displays slot context (school, day, time, TFV) using stylized surface chips. The right column parses complex string payloads into Name, Roll, Subject, Preference, Score, and dynamic [MANUAL] tags.",
      },
      {
        icon: "⏬",
        title: "Expandable Skipped Accordion",
        description: "When an assignment succeeds after multiple failures, a clickable indicator toggles an AnimatedVisibility block (with expandVertically / shrinkVertically) to reveal underlying SkippedSlotRow items, keeping the UI dense while preserving full failure details.",
      },
      {
        icon: "📜",
        title: "Dynamic Canvas Scrollbar",
        description: "A custom vertical scrollbar drawn natively via drawWithContent on a 6dp side-box. It calculates thumb height and offset dynamically using LazyListState.layoutInfo (viewport size vs total items count) to ensure smooth tracking across potentially thousands of generated logs.",
      },
      {
        icon: "🔘",
        title: "Round & Iteration Separators",
        description: "Visual boundaries (ITERATION_START and ROUND_START) are parsed and injected as standalone TopLevelLogItem elements within the LazyColumn payload. They render custom circle-badges and dividers to clearly delineate scheduling phases.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27,
        y: 93.8,
        width: 6.6,
        height: 2.8,
        targetScreenId: "ttw-updates",
        label: "Home"
      },
      {
        shape: "rect",
        x: 36.7,
        y: 93.5,
        width: 6.8,
        height: 3,
        targetScreenId: "class-groups",
        label: "Messages"
      },
      {
        shape: "rect",
        x: 46.9,
        y: 93.6,
        width: 6.3,
        height: 3.1,
        targetScreenId: "ttw-calender",
        label: "Calendar"
      },
      {
        shape: "rect",
        x: 66.7,
        y: 93.7,
        width: 6.3,
        height: 3.1,
        targetScreenId: "ttw-profile",
        label: "Profile"
      }
    ],
    laserTheme: "dark",
  },

  "assigned-volunteer": {
    id: "assigned-volunteer",
    screenshot: "/screenshots/assigned-volunteer.jpg",
    pageName: "Assigned Volunteer",
    pageDescription: 'A full-screen overlay card showing the <span class="highlight_text">complete volunteer assignment</span> — name, roll number, group, interview score, subject preferences, slot details, and available groups — all resolved live from the ViewModel state.',
    featureTitle: "Assignment Detail Panel",
    hookLine: "The full picture. One tap.",
    techTags: [
      { emoji: "✅", label: "AssignmentPanel", color: "var(--accent-emerald)" },
      { emoji: "🏷️", label: "YellowAccent Chips", color: "var(--accent-amber)" },
      { emoji: "🌊", label: "FlowRow Layout", color: "var(--accent-cyan)" },
      { emoji: "🟩", label: "Group Expander", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "✅",
        title: "Dimmed Overlay with Success Header",
        description: "Tapping an assigned green slot opens a full-screen Box with a 70% opaque black overlay. The card centers within it and leads with a CircleShape surface (success green) containing a Check icon alongside 'Volunteer Assigned' bold title — providing instant visual confirmation before the admin reads any data.",
      },
      {
        icon: "🏷️",
        title: "Triple-Chip Identity Row",
        description: "The volunteer's full roll number, group number, and interview score each render as separate YellowAccent Surface chips with RoundedCornerShape(8dp). The score chip only appears when interviewScore > 0, keeping the layout clean for volunteers without a recorded score.",
      },
      {
        icon: "🌊",
        title: "Preference-Rank FlowRow",
        description: "The volunteer's full subject preferences list renders in a FlowRow with maxItemsInEachRow = 4. The currently assigned subject gets a solid YellowAccent background with black bold text; all other preferences display as transparent-bordered chips with white text — immediately showing whether the volunteer received their first-choice subject.",
      },
      {
        icon: "🟩",
        title: "Group Range Expander",
        description: "Available groups are stored as compressed range strings (e.g. '1-5'). A local expandGroupRangesForFlow() function applies a Regex pattern to split start and end integers and re-emit each individually. expandAllGroupRangesForFlow() chains this over every comma-separated part, producing a flat distinct list fed into GroupsFlowLayout.",
      },
      {
        icon: "📍",
        title: "Slot Detail Surface",
        description: "A DarkSurface-tinted Surface row below the volunteer card renders schoolName, dayName, and timeLabel in a single formatted string. This anchors the volunteer detail to its exact teaching context without requiring the admin to navigate back to the schedule grid.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 27.3,
        y: 93.6,
        width: 6.3,
        height: 2.7,
        targetScreenId: "ttw-updates",
        label: "Home",
      },
      {
        shape: "rect",
        x: 36.6,
        y: 93.4,
        width: 7,
        height: 2.9,
        targetScreenId: "class-groups",
        label: "Messages",
      },
      {
        shape: "rect",
        x: 46.8,
        y: 93.5,
        width: 6.4,
        height: 2.9,
        targetScreenId: "ttw-calender",
        label: "Calendar",
      },
      {
        shape: "rect",
        x: 66.9,
        y: 93.5,
        width: 6,
        height: 2.8,
        targetScreenId: "ttw-profile",
        label: "Profile",
      },
    ],
    laserTheme: "light",
  },

  "unassigned-slot": {
    id: "unassigned-slot",
    screenshot: "/screenshots/unassigned-slot.jpg",
    pageName: "Unassigned Slot",
    pageDescription: 'A bottom-sheet assignment panel for an <span class="highlight_text">unfilled teaching slot</span> — showing available groups, manual volunteer search, and a one-tap automatic fallback — driven live by the ViewModel\'s TFV engine.',
    featureTitle: "Gap Resolution Panel",
    hookLine: "Empty slot. Two paths to fill it.",
    techTags: [
      { emoji: "🔍", label: "ManualVolunteerDialog", color: "var(--accent-amber)" },
      { emoji: "⚡", label: "AutoAwesome FAB", color: "var(--accent-purple)" },
      { emoji: "🟡", label: "YellowAccent Buttons", color: "var(--accent-cyan)" },
      { emoji: "📋", label: "LazyRow Groups", color: "var(--accent-emerald)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "🏷️",
        title: "Slot Context Header",
        description: "The panel header dynamically renders 'Assign Volunteer' with the slot's school, day, and time in a subtitle row. A close IconButton sits at the trailing end. Both fields are bound directly to the Slot object passed into AssignmentPanel — no extra state needed.",
      },
      {
        icon: "📋",
        title: "Available Groups LazyRow",
        description: "A nested Card renders the slot's availableGroups list through expandAllGroupRangesForUI() — a helper that splits comma-separated entries and expands numeric ranges (e.g. '3-7' → ['3','4','5','6','7']) into individual YellowAccent Surface chips displayed in a horizontally scrollable LazyRow.",
      },
      {
        icon: "🔍",
        title: "Manual Assignment via Search Dialog",
        description: "The YellowAccent primary button ('Assign Volunteer Manually') flips showManualSelection = true, launching ManualVolunteerSelectionDialog. The dialog receives the unfiltered volunteer list and slot context, letting the admin pick any available volunteer and subject — bypassing the TFV algorithm entirely.",
      },
      {
        icon: "⚡",
        title: "Automatic TFV Assignment Button",
        description: "An OutlinedButton with a YellowAccent border and AutoAwesome icon calls onAssignAutomatic, which proxies to assignNextSlotInRoundRobin() on the ViewModel. The function selects the lowest-TFV unassigned volunteer satisfying the adjacency and daily-limit constraints, then writes the assignment atomically to the roster StateFlow.",
      },
      {
        icon: "🚫",
        title: "Empty-Roster Guard Card",
        description: "When availableVolunteers.isEmpty() is true, the assignment buttons are replaced with a full-width Card filled with Color(0xFFB71C1C) (deep red), showing 'No volunteers available for assignment' centered in bold white text. This prevents the admin from triggering an assignment call that would immediately fail.",
      },
    ],
    hotspots: [
    ],
    laserTheme: "light",
  },

  "attendance-record": {
    id: "attendance-record",
    screenshot: "/screenshots/attendance-record.jpg",
    pageName: "Attendance Report",
    pageDescription: 'An on-device PDF manifest auto-compiled after each session — rendering a <span class="highlight_text">structured, wing-sorted attendee table</span> with event metadata, roll numbers, and a timestamped generation footer.',
    featureTitle: "PDF Report Engine",
    hookLine: "Every session. One tap. Instant PDF.",
    techTags: [
      { emoji: "📄", label: "iText PDF Library", color: "var(--accent-amber)" },
      { emoji: "🗂️", label: "Wing Grouping", color: "var(--accent-cyan)" },
      { emoji: "💾", label: "Downloads Export", color: "var(--accent-purple)" },
    ],
    builtBy: "Eshan",
    features: [
      {
        icon: "📋",
        title: "Event Metadata Header",
        description: "The PDF opens with a bold 'NSS Attendance Report' title followed by a 2-column details table. createCell() alternates label cells (light blue background, bold font) against data cells (white background) to render Event, Date, Time, Location, and Hours in a scannable grid format.",
      },
      {
        icon: "🗂️",
        title: "Wing-Sorted Attendee Table",
        description: "groupAttendeesByWing() performs an async Firestore lookup on the 'users' collection per attendee roll number to resolve their Wing. Attendees are then bucketed by wing, sorted alphabetically within each group, and rendered in a 3-column Wing / Name / Roll Number table using toSortedMap() for lexicographic wing ordering.",
      },
      {
        icon: "📄",
        title: "iText Document Assembly",
        description: "Built on the iText library chain: PdfWriter → PdfDocument → Document. Header cells use Helvetica-Bold with white text on a #336699 blue background, while data cells use plain Helvetica on white. createHeaderCell() and createCell() abstract all styling, keeping row-rendering logic clean.",
      },
      {
        icon: "💾",
        title: "Timestamped File Export",
        description: "The output filename is sanitized from the event name via regex ([^a-zA-Z0-9.-] → _) and suffixed with a yyyyMMdd_HHmmss timestamp. The file is written via FileOutputStream to Downloads/NSS_Reports/, creating the folder with mkdirs() if absent, making every report uniquely addressable and instantly findable.",
      },
      {
        icon: "⚙️",
        title: "IO-Threaded Coroutine Suspension",
        description: "generateAttendanceReport() is a suspend fun dispatched entirely on Dispatchers.IO via withContext(Dispatchers.IO). All Firestore cross-reference fetches for wing resolution and the final file write execute off the main thread — ensuring zero UI jank during heavy document compilation.",
      },
    ],
    hotspots: [],
    laserTheme: "light",
  }
};

export const START_SCREEN_ID = "first-screen";
