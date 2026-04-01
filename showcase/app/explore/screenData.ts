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
};

export const START_SCREEN_ID = "first-screen";
