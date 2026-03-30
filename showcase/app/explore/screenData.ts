export interface Hotspot {
  shape: "rect" | "circle";
  /** For rect: top-left X as % of screenshot width */
  x?: number;
  /** For rect: top-left Y as % of screenshot height */
  y?: number;
  /** For rect: width as % of screenshot width */
  width?: number;
  /** For rect: height as % of screenshot height */
  height?: number;
  /** For circle: center X as % */
  cx?: number;
  /** For circle: center Y as % */
  cy?: number;
  /** For circle: radius as % of screenshot width */
  r?: number;
  /** which screen clicking this goes to */
  targetScreenId: string;
  /** tooltip label */
  label: string;
}

export interface AppScreen {
  id: string;
  screenshot: string;
  featureTitle: string;
  hookLine: string;
  techTags: { emoji: string; label: string; color?: string }[];
  builtBy: "Eshan" | "Ankesh" | "Both";
  whyBuilt: string;
  hotspots: Hotspot[];
}

/**
 * All screens for the interactive walkthrough.
 * Currently only the first screen — more will be added
 * once screenshots are provided.
 */
export const screens: Record<string, AppScreen> = {
  "first-screen": {
    id: "first-screen",
    screenshot: "/screenshots/first-screen.jpg",
    featureTitle: "Welcome Gate",
    hookLine: "Two doors. Two worlds. One mission.",
    techTags: [
      { emoji: "🔐", label: "Firebase Auth", color: "var(--accent-amber)" },
      { emoji: "🎨", label: "Jetpack Compose", color: "var(--accent-blue)" },
      { emoji: "👤", label: "Role-Based Access", color: "var(--accent-purple)" },
    ],
    builtBy: "Both",
    whyBuilt:
      "The app serves two different user groups — NSS volunteers and admins. A clean role-selection gateway ensures the right people see the right features from the very first tap.",
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
  },
};

export const START_SCREEN_ID = "first-screen";
