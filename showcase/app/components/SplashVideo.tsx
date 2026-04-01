"use client";

import styles from "./SplashVideo.module.css";

export default function SplashVideo() {
  return (
    <div className={styles.phone_wrapper}>
      {/* Glow behind the phone */}
      <div className={styles.phone_glow} />

      {/* Phone frame */}
      <div className={styles.phone_frame}>
        {/* Notch */}
        <div className={styles.phone_notch} />

        {/* Screen */}
        <div className={styles.phone_screen}>
          <video
            className={styles.splash_video}
            src="/videos/nss_splash_anim.mp4"
            autoPlay
            muted
            loop
            playsInline
          />
        </div>

        {/* Home bar */}
        <div className={styles.phone_bar} />
      </div>

      {/* Floating badges */}
      <div className={`${styles.float_badge} ${styles.badge_tl}`}>
        <span className={styles.badge_dot} style={{ background: "var(--accent-cyan)" }} />
        Live on Play Store
      </div>
      <div className={`${styles.float_badge} ${styles.badge_br}`}>
        <span className={styles.badge_dot} style={{ background: "var(--accent-emerald)" }} />
        350+ Active Users
      </div>
    </div>
  );
}
