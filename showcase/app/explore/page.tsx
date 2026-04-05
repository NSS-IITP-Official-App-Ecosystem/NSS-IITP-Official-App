"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ArrowLeft, Pointer } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import styles from "./explore.module.css";
import { screens, START_SCREEN_ID } from "./screenData";
import type { Hotspot } from "./screenData";
import ParticleField from "../components/ParticleField";

/* ---- Animation variants ---- */
const fadeSlideLeft: any = {
  initial: { opacity: 0, x: -50, filter: "blur(8px)" },
  animate: {
    opacity: 1,
    x: 0,
    filter: "blur(0px)",
    transition: { duration: 0.7, ease: [0.25, 0.4, 0.25, 1] },
  },
  exit: {
    opacity: 0,
    x: -30,
    filter: "blur(6px)",
    transition: { duration: 0.35 },
  },
};

const fadeSlideRight: any = {
  initial: { opacity: 0, x: 50, filter: "blur(8px)" },
  animate: {
    opacity: 1,
    x: 0,
    filter: "blur(0px)",
    transition: { duration: 0.7, ease: [0.25, 0.4, 0.25, 1], delay: 0.1 },
  },
  exit: {
    opacity: 0,
    x: 30,
    filter: "blur(6px)",
    transition: { duration: 0.35 },
  },
};

const phoneEntrance: any = {
  initial: { opacity: 0, scale: 0.85, y: 40 },
  animate: {
    opacity: 1,
    scale: 1,
    y: 0,
    transition: { duration: 0.8, ease: [0.25, 0.4, 0.25, 1], delay: 0.2 },
  },
};

const staggerChildren: any = {
  animate: {
    transition: { staggerChildren: 0.08, delayChildren: 0.15 },
  },
};

const fadeUp: any = {
  initial: { opacity: 0, y: 20 },
  animate: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, ease: [0.25, 0.4, 0.25, 1] },
  },
};

const screenshotVariants: any = {
  initial: (direction: number) => ({
    opacity: 0,
    scale: direction > 0 ? 0.92 : 1.08,
  }),
  animate: {
    opacity: 1,
    scale: 1,
    transition: { duration: 0.4, ease: [0.25, 0.4, 0.25, 1] },
  },
  exit: (direction: number) => ({
    opacity: 0,
    scale: direction > 0 ? 1.08 : 0.92,
    transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] },
  }),
};

/* ---- Shape fitting types ---- */
type Pt = { x: number; y: number };
type FittedRect = { kind: "rect"; x: number; y: number; w: number; h: number };
type FittedCircle = { kind: "circle"; cx: number; cy: number; r: number };
type FittedShape = FittedRect | FittedCircle;

type SavedShape = FittedShape & { label: string };

/* ---- Shape detection algorithm ---- */
function fitShape(pts: Pt[]): FittedShape | null {
  if (pts.length < 3) return null;

  const xs = pts.map((p) => p.x);
  const ys = pts.map((p) => p.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const w = maxX - minX, h = maxY - minY;
  if (w < 1 || h < 1) return null;

  const cx = (minX + maxX) / 2;
  const cy = (minY + maxY) / 2;

  // Distances from centroid for each point
  const dists = pts.map((p) => Math.sqrt((p.x - cx) ** 2 + (p.y - cy) ** 2));
  const avgR = dists.reduce((a, b) => a + b, 0) / dists.length;
  const maxR = Math.max(...dists);
  const stdDev = Math.sqrt(
    dists.reduce((sum, d) => sum + (d - avgR) ** 2, 0) / dists.length
  );

  // Aspect ratio of bounding box (close to 1 = square-ish = more likely circle)
  const aspect = Math.min(w, h) / Math.max(w, h);
  // Low standard deviation = consistent distance from center = circular
  const circularity = aspect * (1 - stdDev / (maxR + 0.001));

  if (circularity > 0.55) {
    return { kind: "circle", cx, cy, r: avgR };
  }
  return { kind: "rect", x: minX, y: minY, w, h };
}

function r1(n: number) { return Math.round(n * 10) / 10; }

function shapeToString(s: FittedShape): string {
  if (s.kind === "circle") {
    return `shape:"circle"  cx:${r1(s.cx)}%  cy:${r1(s.cy)}%  r:${r1(s.r)}%`;
  }
  return `shape:"rect"  x:${r1(s.x)}%  y:${r1(s.y)}%  w:${r1(s.w)}%  h:${r1(s.h)}%`;
}

/* ---- Hotspot renderer ---- */
function HotspotEl({ hotspot, onClick }: { hotspot: Hotspot; onClick: () => void }) {
  if (hotspot.shape === "circle" && hotspot.cx != null && hotspot.cy != null && hotspot.r != null) {
    const d = hotspot.r * 2;
    return (
      <div
        className={styles.hotspot}
        style={{
          left: `${hotspot.cx - hotspot.r}%`,
          top: `${hotspot.cy - hotspot.r}%`,
          width: `${d}%`,
          height: `${d}%`,
          borderRadius: "50%",
        }}
        onClick={onClick}
      >
        <span className={styles.hotspot_tooltip}>{hotspot.label}</span>
      </div>
    );
  }
  // rect (default)
  return (
    <div
      className={styles.hotspot}
      style={{
        left: `${hotspot.x}%`,
        top: `${hotspot.y}%`,
        width: `${hotspot.width}%`,
        height: `${hotspot.height}%`,
      }}
      onClick={onClick}
    >
      <span className={styles.hotspot_tooltip}>{hotspot.label}</span>
    </div>
  );
}

/* ============================================================ */
export default function ExplorePage() {
  const [currentScreenId, setCurrentScreenId] = useState(START_SCREEN_ID);
  const [history, setHistory] = useState<string[]>([START_SCREEN_ID]);
  const [direction, setDirection] = useState(1);

  // --- Laser border single-spinner fade state ---
  const [displayedLaserTheme, setDisplayedLaserTheme] = useState<string>(screens[START_SCREEN_ID].laserTheme || "mixed");
  const [laserOpacity, setLaserOpacity] = useState(1);

  // --- Calibration state ---
  const [calibMode, setCalibMode] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isDrawing, setIsDrawing] = useState(false);
  const [liveStrokes, setLiveStrokes] = useState<Pt[][]>([]);
  const [savedShapes, setSavedShapes] = useState<SavedShape[]>([]);
  const shapeCount = useRef(0);
  const screenRef = useRef<HTMLDivElement>(null);

  const resetCalib = () => {
    setCalibMode(false);
    setIsRecording(false);
    setIsDrawing(false);
    setLiveStrokes([]);
    setSavedShapes([]);
    shapeCount.current = 0;
  };

  // --- Hybrid proportional scaling (Stop shrinking at 1024px) ---
  const DESIGN_WIDTH = 1760; // px — the exact width of the .explore_layout grid
  const MOBILE_BREAKPOINT = 1024;
  const [scaleZoom, setScaleZoom] = useState(1);
  
  useEffect(() => {
    const update = () => {
      if (window.innerWidth <= MOBILE_BREAKPOINT) {
        setScaleZoom(1); // Let CSS media queries take over stacking
      } else {
        const ratio = window.innerWidth / DESIGN_WIDTH;
        setScaleZoom(Math.min(1, ratio));
      }
    };
    update();
    window.addEventListener("resize", update);
    return () => window.removeEventListener("resize", update);
  }, []);

  const currentScreen = screens[currentScreenId];

  const getPct = (e: React.PointerEvent<HTMLDivElement>): Pt => {
    const rect = e.currentTarget.getBoundingClientRect();
    return {
      x: r1(((e.clientX - rect.left) / rect.width) * 100),
      y: r1(((e.clientY - rect.top) / rect.height) * 100),
    };
  };

  // Laser border: fade out → swap theme → fade in (single spinner, no rotation interruption)
  useEffect(() => {
    const newTheme = screens[currentScreenId]?.laserTheme || "mixed";
    if (newTheme === displayedLaserTheme) return;
    // Phase 1: fade out
    setLaserOpacity(0);
    const swapTimer = setTimeout(() => {
      // Phase 2: swap theme while invisible
      setDisplayedLaserTheme(newTheme);
      // Phase 3: fade back in
      setLaserOpacity(1);
    }, 180);
    return () => clearTimeout(swapTimer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentScreenId]);

  const navigateTo = useCallback(
    (targetId: string) => {
      if (!screens[targetId]) return;
      setDirection(1);
      setCurrentScreenId(targetId);
      setHistory((prev) => [...prev, targetId]);
    },
    []
  );

  const navigateBack = useCallback(() => {
    if (history.length <= 1) return;
    setDirection(-1);

    const tabs = ["nss-home", "nss-calendar", "nss-qr-scan", "nss-profile"];
    if (tabs.includes(currentScreenId)) {
      const idx = history.lastIndexOf("admin-dashboard");
      if (idx !== -1) {
        const newHistory = history.slice(0, idx + 1);
        setHistory(newHistory);
        setCurrentScreenId("admin-dashboard");
        return;
      } else {
        setHistory([START_SCREEN_ID, "admin-dashboard"]);
        setCurrentScreenId("admin-dashboard");
        return;
      }
    }

    const ttwScreens = [
      "ttw-updates", "ttw-profile", "ttw-calender", "ttw-scheduling",
      "class-groups", "ttw-options", "manage-students",
      "teaching-slots", "teaching-slots-preset", "volunteers-preset",
      "generate-schedule", "view-schedule", "edit-slots",
      "add-free-groups", "day", "create-schedule",
      "add-volunteers", "assignment-log", "assigned-volunteer", "unassigned-slot",
    ];
    const volScreens = [
      "vol-updates", "vol-calender", "vol-QR", "vol-profile", "vol-day", "vol-ttw-home",
      "vol-ttw-grp", "vol-ttw-cal", "vol-ttw-pro"
    ];
    
    if (ttwScreens.includes(currentScreenId)) {
      setHistory([START_SCREEN_ID, "admin-dashboard"]);
      setCurrentScreenId("admin-dashboard");
      return;
    }
    
    if (currentScreenId === "vol-day") {
      setHistory([START_SCREEN_ID, "vol-log-in", "vol-dashboard", "vol-calender"]);
      setCurrentScreenId("vol-calender");
      return;
    }

    if (volScreens.includes(currentScreenId)) {
      setHistory([START_SCREEN_ID, "vol-log-in", "vol-dashboard"]);
      setCurrentScreenId("vol-dashboard");
      return;
    }

    const newHistory = history.slice(0, -1);
    setHistory(newHistory);
    setCurrentScreenId(newHistory[newHistory.length - 1]);
  }, [history, currentScreenId]);

  if (!currentScreen) return null;

  /* SVG polyline string for freehand stroke */
  const polylineDefs = liveStrokes.map(stroke => stroke.map(p => `${p.x},${p.y}`).join(" "));

  return (
    <>
      {/* Ambient layers */}
      <div className="aurora-bg">
        <div className="aurora-orb-center" />
      </div>
      <ParticleField />

      <div className={styles.explore_page}>
        {/* Back to Home */}
        <Link href="/" className={styles.back_link}>
          <ArrowLeft style={{ width: 15, height: 15 }} />
          Home
        </Link>

        {/* ====== THREE-COLUMN LAYOUT (inside zoom scale shim) ====== */}
        <div
          className={styles.scale_shim}
          style={{ zoom: scaleZoom } as React.CSSProperties}
        >
        <div className={styles.explore_layout}>
          {/* LEFT PANEL — Page name + description + Tech Stack + Built By */}
          <AnimatePresence mode="wait">
            <motion.div
              key={`left-${currentScreenId}`}
              className={styles.left_panel}
              variants={fadeSlideLeft}
              initial="initial"
              animate="animate"
              exit="exit"
            >

              {/* Plain page name */}
              <h1 className={styles.page_name}>{currentScreen.pageName}</h1>

              {/* Description with highlighted phrases */}
              <p
                className={styles.page_description}
                dangerouslySetInnerHTML={{ __html: currentScreen.pageDescription }}
              />

              <div className={styles.left_divider} />

              {/* Tech Stack */}
              <motion.div
                className={styles.tech_section}
                variants={staggerChildren}
                initial="initial"
                animate="animate"
              >
                <span className={styles.tech_section_label}>Tech Stack</span>
                <div className={styles.tech_tags}>
                  {currentScreen.techTags.map((tag, i) => (
                    <motion.span
                      key={i}
                      className={styles.tech_tag}
                      variants={fadeUp}
                      style={tag.color ? { borderColor: `color-mix(in srgb, ${tag.color} 20%, transparent)` } : undefined}
                    >
                      <span className={styles.tech_tag_emoji}>{tag.emoji}</span>
                      {tag.label}
                    </motion.span>
                  ))}
                </div>
              </motion.div>

              <div className={styles.left_divider} />

              {/* Built By */}
              <div className={styles.built_by_section}>
                <span className={styles.built_by_label}>Built By</span>
                <div className={styles.built_by_badge}>
                  {currentScreen.builtBy === "Both"
                    ? "Eshan & Ankesh"
                    : currentScreen.builtBy}
                </div>
              </div>
            </motion.div>
          </AnimatePresence>

          {/* CENTER — Phone */}
          <motion.div
            className={styles.phone_column}
            variants={phoneEntrance}
            initial="initial"
            animate="animate"
          >
            <div className={styles.phone_wrapper}>
              
              {/* Floating Back Button (Visible if there's history) */}
              {history.length > 1 && (
                <button
                  className={styles.phone_back_btn}
                  onClick={navigateBack}
                  aria-label="Previous Screen"
                  title="Go back"
                >
                  <ArrowLeft size={18} strokeWidth={2.5} />
                </button>
              )}

              {/* Ambient glow */}
              <div className={styles.phone_glow} />

              {/* Orbiting animated laser border — single spinner, fade-swap on theme change */}
              <div className={styles.phone_animated_border}>
                <div
                  className={styles.phone_animated_border_spinner}
                  data-laser-theme={displayedLaserTheme}
                  style={{
                    opacity: laserOpacity,
                    transition: "opacity 0.18s ease",
                  }}
                />
              </div>

              {/* Phone image container */}
              <div className={styles.phone_frame}>

                {/* Screen area */}
                <div
                  className={styles.phone_screen}
                  ref={screenRef}
                  style={{
                    cursor: calibMode && isRecording ? "crosshair" : (calibMode ? "default" : "pointer"),
                    userSelect: "none",
                    touchAction: calibMode ? "none" : "auto",
                  }}
                  onPointerDown={(e) => {
                    if (!calibMode || !isRecording) return;
                    e.preventDefault();
                    const pt = getPct(e);
                    setIsDrawing(true);
                    setLiveStrokes((prev) => [...prev, [pt]]);
                  }}
                  onPointerMove={(e) => {
                    if (!calibMode || !isRecording || !isDrawing) return;
                    e.preventDefault();
                    const pt = getPct(e);
                    setLiveStrokes((prev) => {
                      if (prev.length === 0) return [[pt]]; // Safety fallback
                      const newStrokes = [...prev];
                      const lastStroke = newStrokes[newStrokes.length - 1];
                      newStrokes[newStrokes.length - 1] = [...lastStroke, pt];
                      return newStrokes;
                    });
                  }}
                  onPointerUp={() => {
                    if (!calibMode || !isRecording) return;
                    setIsDrawing(false);
                  }}
                  onPointerLeave={() => {
                    if (isDrawing) setIsDrawing(false);
                  }}
                >
                  <AnimatePresence custom={direction}>
                    <motion.div
                      key={currentScreen.id}
                      custom={direction}
                      variants={screenshotVariants}
                      initial="initial"
                      animate="animate"
                      exit="exit"
                      style={{ width: "100%", height: "100%", position: "absolute", top: 0, left: 0, transformOrigin: "center center" }}
                    >
                      <img
                        src={currentScreen.screenshot}
                        alt={currentScreen.featureTitle}
                        className={styles.screenshot_image}
                        draggable={false}
                      />
                    </motion.div>
                  </AnimatePresence>

                  {/* ── HOTSPOTS (Visible unless drawing) ── */}
                  {!isDrawing && !isRecording && currentScreen.hotspots.map((hs, i) => (
                    <HotspotEl
                      key={i}
                      hotspot={hs}
                      onClick={() => navigateTo(hs.targetScreenId)}
                    />
                  ))}

                  {/* ── CALIBRATION OVERLAY ── */}
                  {calibMode && (
                    <>
                      {/* SVG layer: freehand stroke (live, using % viewBox) */}
                      <svg
                        style={{
                          position: "absolute", inset: 0,
                          width: "100%", height: "100%",
                          pointerEvents: "none", zIndex: 40,
                          overflow: "visible",
                        }}
                        viewBox="0 0 100 100"
                        preserveAspectRatio="none"
                      >
                        {/* Render all active strokes */}
                        {polylineDefs.map((pointsStr, idx) => (
                           pointsStr.length > 0 && (
                             <polyline
                               key={idx}
                               points={pointsStr}
                               fill="none"
                               stroke="#D32F2F"
                               strokeWidth="0.4"
                               strokeLinecap="round"
                               strokeLinejoin="round"
                             />
                           )
                        ))}

                        {/* Saved shapes */}
                        {savedShapes.map((s, i) =>
                          s.kind === "circle" ? (
                            <g key={i}>
                              <circle
                                cx={s.cx} cy={s.cy} r={s.r}
                                fill="rgba(255,107,107,0.12)"
                                stroke="#ff6b6b" strokeWidth="0.7"
                              />
                              <text
                                x={s.cx} y={s.cy - s.r - 1}
                                textAnchor="middle"
                                fontSize="3" fill="#ff6b6b"
                                fontFamily="monospace"
                              >
                                {s.label}
                              </text>
                            </g>
                          ) : (
                            <g key={i}>
                              <rect
                                x={s.x} y={s.y} width={s.w} height={s.h} rx="1.5"
                                fill="rgba(255,107,107,0.12)"
                                stroke="#ff6b6b" strokeWidth="0.7"
                              />
                              <text
                                x={s.x + s.w / 2} y={s.y - 1}
                                textAnchor="middle"
                                fontSize="3" fill="#ff6b6b"
                                fontFamily="monospace"
                              >
                                {s.label}
                              </text>
                            </g>
                          )
                        )}
                      </svg>

                      {/* Instruction banner */}
                      {!isDrawing && !isRecording && savedShapes.length === 0 && (
                        <div style={{
                          position: "absolute", top: "50%", left: "50%",
                          transform: "translate(-50%, -50%)",
                          background: "rgba(0,0,0,0.75)",
                          color: "#66FCF1", fontFamily: "monospace", fontSize: 11,
                          padding: "8px 14px", borderRadius: 8,
                          border: "1px solid rgba(102,252,241,0.3)",
                          pointerEvents: "none", textAlign: "center",
                          lineHeight: 1.6,
                        }}>
                          ✋ Click 'New Shape' to start drawing<br />
                          <span style={{ opacity: 0.6 }}>Multi-stroke supported</span>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* ── Controls bar ── */}
            <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", marginTop: "0.75rem", flexWrap: "wrap", justifyContent: "center" }}>
              <button
                onClick={() => calibMode ? resetCalib() : setCalibMode(true)}
                style={{
                  background: calibMode ? "rgba(255,107,107,0.15)" : "rgba(102,252,241,0.08)",
                  border: `1px solid ${calibMode ? "rgba(255,107,107,0.5)" : "rgba(102,252,241,0.2)"}`,
                  color: calibMode ? "#ff6b6b" : "#66FCF1",
                  fontFamily: "monospace", fontSize: 12,
                  padding: "5px 14px", borderRadius: 8, cursor: "pointer",
                }}
              >
                {calibMode ? "✕ Exit Calibration" : "⊕ Calibrate Hotspots"}
              </button>

              {calibMode && (
                <button
                  onClick={() => {
                    if (isRecording) {
                      // Finish recording
                      setIsRecording(false);
                      const allPoints = liveStrokes.flat();
                      if (allPoints.length > 2) {
                        shapeCount.current += 1;
                        const shape = fitShape(allPoints);
                        if (shape) {
                          setSavedShapes((prev) => [
                            ...prev,
                            { ...shape, label: `Shape ${shapeCount.current}` },
                          ]);
                        }
                      }
                      setLiveStrokes([]);
                    } else {
                      // Start recording
                      setIsRecording(true);
                      setLiveStrokes([]);
                    }
                  }}
                  style={{
                    background: isRecording ? "rgba(59, 130, 246, 0.2)" : "rgba(255, 255, 255, 0.05)",
                    border: `1px solid ${isRecording ? "rgba(59, 130, 246, 0.5)" : "rgba(255,255,255,0.15)"}`,
                    color: isRecording ? "#3b82f6" : "rgba(255,255,255,0.7)",
                    fontFamily: "monospace", fontSize: 12,
                    padding: "5px 14px", borderRadius: 8, cursor: "pointer",
                  }}
                >
                  {isRecording ? "⏹ Finish Shape" : "▶ New Shape"}
                </button>
              )}

              {calibMode && savedShapes.length > 0 && (
                <button
                  onClick={() => { setSavedShapes([]); shapeCount.current = 0; }}
                  style={{
                    background: "transparent", border: "1px solid rgba(255,255,255,0.15)",
                    color: "rgba(255,255,255,0.5)", fontFamily: "monospace", fontSize: 11,
                    padding: "5px 10px", borderRadius: 8, cursor: "pointer",
                  }}
                >
                  Clear
                </button>
              )}

              {!calibMode && (
                <div className={styles.nav_hint}>
                  <span className={styles.nav_hint_dot} />
                  <Pointer style={{ width: 13, height: 13 }} />
                  Tap the highlighted buttons to navigate
                </div>
              )}
            </div>

            {/* ── Coordinate readout panel ── */}
            {calibMode && savedShapes.length > 0 && (
              <div style={{
                marginTop: "0.75rem",
                background: "rgba(0,0,0,0.7)",
                border: "1px solid rgba(255,107,107,0.3)",
                borderRadius: 10, padding: "10px 14px",
                fontFamily: "monospace", fontSize: 11,
                color: "#ff6b6b", lineHeight: 1.8,
                maxWidth: 420,
              }}>
                <div style={{ color: "rgba(255,255,255,0.4)", marginBottom: 4, fontSize: 10 }}>
                  📋 COPY THESE COORDS → tell me the shape + values:
                </div>
                {savedShapes.map((s, i) => (
                  <div key={i}>{s.label}: {shapeToString(s)}</div>
                ))}
              </div>
            )}
          </motion.div>

          {/* RIGHT PANEL — Feature highlights */}
          <AnimatePresence mode="wait">
            <motion.div
              key={`right-${currentScreenId}`}
              className={styles.right_panel}
              variants={fadeSlideRight}
              initial="initial"
              animate="animate"
              exit="exit"
            >
              <span className={styles.features_label}>Under the Hood</span>
              <motion.div
                className={styles.features_list}
                variants={staggerChildren}
                initial="initial"
                animate="animate"
              >
                <div className={styles.features_track_container}>
                  {currentScreen.features.map((feat, i) => {
                    const isEven = i % 2 === 0;
                    const staggerClass = isEven ? styles.feature_card_left : styles.feature_card_right;
                    
                    return (
                      <motion.div key={i} className={`${styles.feature_card} ${staggerClass}`} variants={fadeUp}>
                        <span className={styles.feature_icon}>{feat.icon}</span>
                        <div className={styles.feature_body}>
                          <h3 className={styles.feature_name}>{feat.title}</h3>
                          <p className={styles.feature_desc}>{feat.description}</p>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              </motion.div>
            </motion.div>
          </AnimatePresence>
        </div> {/* end explore_layout */}
        </div> {/* end scale_shim */}
      </div>
    </>
  );
}
