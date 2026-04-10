"use client";

import { useEffect, useState } from "react";
import { MonitorSmartphone } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import styles from "./MobileWarning.module.css";

export default function MobileWarning() {
  const [shouldShow, setShouldShow] = useState(false);

  useEffect(() => {
    // Check if dismissed previously in this session
    const isDismissed = sessionStorage.getItem("mobileWarningDismissed") === "true";
    
    // Only show if we're on a mobile-sized viewport (< 1024px according to global app break points)
    // and hasn't been dismissed
    if (!isDismissed && window.innerWidth <= 1024) {
      setShouldShow(true);
      // Optional: block body scroll while modal is open
      document.body.style.overflow = "hidden";
    }
  }, []);

  const handleDismiss = () => {
    sessionStorage.setItem("mobileWarningDismissed", "true");
    setShouldShow(false);
    document.body.style.overflow = "auto";
  };

  return (
    <AnimatePresence>
      {shouldShow && (
        <motion.div 
          className={styles.overlay}
          initial={{ opacity: 0, backdropFilter: "blur(0px)" }}
          animate={{ opacity: 1, backdropFilter: "blur(12px)" }}
          exit={{ opacity: 0, backdropFilter: "blur(0px)" }}
          transition={{ duration: 0.4 }}
        >
          <motion.div 
            className={styles.modal}
            initial={{ scale: 0.9, y: 20, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.95, y: 10, opacity: 0 }}
            transition={{ type: "spring", damping: 25, stiffness: 300 }}
          >
            <div className={styles.content}>
              <div className={styles.icon_wrapper}>
                <MonitorSmartphone size={32} strokeWidth={2} />
              </div>
              <h2 className={styles.title}>Desktop Recommended</h2>
              <p className={styles.description}>
                This showcase portfolio features custom interactive phone emulation. 
                For the best graphical and interactive experience, please view this site on a PC or Laptop.
              </p>
              <button className={styles.button} onClick={handleDismiss}>
                Continue Anyway
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
