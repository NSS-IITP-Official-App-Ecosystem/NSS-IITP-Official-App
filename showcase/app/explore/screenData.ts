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
      'The entry gateway offering dedicated pathways for <span class="highlight_text">NSS volunteers</span> and <span class="highlight_text">admins</span>. Each route triggers completely separate <span class="highlight_text">validation schemes</span> and interface flows.',
    featureTitle: "Welcome Gate",
    hookLine: "Two doors. Two worlds. One mission.",
    techTags: [
      { emoji: "🔐", label: "Firebase Auth", color: "var(--accent-amber)" },
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-cyan)" },
      { emoji: "🎨", label: "Jetpack Compose", color: "var(--accent-blue)" },
      { emoji: "👤", label: "Role-Based Access", color: "var(--accent-purple)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "🔤",
        title: "Case-Insensitive Roll Lookup",
        description:
          "Checks all 4 department case permutations (e.g. CS/Cs/cS/cs) against Firestore, preventing lockouts due to database typos.",
      },
      {
        icon: "🛡️",
        title: "3-Layer Authentication",
        description:
          "Validates roll number and institute email before hitting Firebase Auth, stopping partially-guessed credential attacks.",
      },
      {
        icon: "🚦",
        title: "Role Gate Enforcement",
        description:
          "Verifies Firestore `userType` against the selected path. Mismatched roles are blocked instantly before token issuance.",
      },
      {
        icon: "💾",
        title: "Persistent Role Memory",
        description:
          "Caches the chosen role locally so returning users skip this gateway and land directly on their dashboard.",
      },
      {
        icon: "🔗",
        title: "Silent Device Binding",
        description:
          "Generates an RSA key in Android Keystore post-login to silently bind the physical device to the user's account.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 6.7, y: 76.9, width: 86.9, height: 7.6,
        targetScreenId: "nss-home",
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

  "admin-dashboard": {
    id: "admin-dashboard",
    screenshot: "/screenshots/logged-in-as-admin.jpg",
    pageName: "Admin Dashboard",
    pageDescription:
      'The unified command center for <span class="highlight_text">administrative tasks</span>. It features a robust caching system for updates and provides tools to <span class="highlight_text">create, edit, and batch-manage</span> announcements efficiently.',
    featureTitle: "Command Center",
    hookLine: "Complete control. Optimized delivery.",
    techTags: [
      { emoji: "🗄️", label: "Cloud Firestore", color: "var(--accent-cyan)" },
      { emoji: "📎", label: "Media Sharing", color: "var(--accent-blue)" },
      { emoji: "🔄", label: "Kotlin Coroutines", color: "var(--accent-emerald)" },
      { emoji: "🎨", label: "Jetpack Compose", color: "var(--accent-blue)" },
    ],
    builtBy: "Ankesh",
    features: [
      {
        icon: "💾",
        title: "Post Caching System",
        description:
          "Implements a local caching mechanism with a discrete TTL to reduce unnecessary Firestore reads upon app initialization.",
      },
      {
        icon: "⚡",
        title: "Concurrent Media Upload",
        description:
          "Leverages Kotlin Coroutines to dispatch asynchronous background jobs, ensuring images and documents upload simultaneously.",
      },
      {
        icon: "🎛️",
        title: "Conditional Post Layouts",
        description:
          "Dynamically toggles input fields depending on post type (Text vs Reel) and enforces rigorous pre-flight validation.",
      },
    ],
    hotspots: [
      {
        shape: "rect",
        x: 6.7,
        y: 76.6,
        width: 87,
        height: 7.6,
        targetScreenId: "nss-profile",
        label: "NSS Wing",
      },
      {
        shape: "rect",
        x: 6.7,
        y: 86,
        width: 86.5,
        height: 7.4,
        targetScreenId: "teaching-tech-wing",
        label: "Teaching & Tech Wing",
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
        x: 26.2,
        y: 93.5,
        width: 7.6,
        height: 3.5,
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
    ],
    laserTheme: "mixed",
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
        x: 26.2,
        y: 93.5,
        width: 7.6,
        height: 3.5,
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
        x: 26.2,
        y: 93.5,
        width: 7.6,
        height: 3.5,
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
      { emoji: "⚡", label: "Live Feed", color: "var(--accent-emerald)" },
      { emoji: "💾", label: "Smart Cache", color: "var(--accent-blue)" },
      { emoji: "☁️", label: "Cloud Uploads", color: "var(--accent-orange)" },
      { emoji: "👥", label: "Role Sync", color: "var(--accent-purple)" },
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
};

export const START_SCREEN_ID = "first-screen";
