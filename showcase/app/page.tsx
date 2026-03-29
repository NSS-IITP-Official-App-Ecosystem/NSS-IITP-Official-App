"use client";

import { motion } from "framer-motion";
import {
  ArrowRight,
  Code2,
  ShieldAlert,
  ShieldCheck,
  Users2,
  Code,
  User,
  Zap,
  ChevronDown,
  Terminal,
  ArrowUpRight,
  Smartphone,
  ExternalLink,
  Calendar,
  Bell,
  Database,
  Network,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import styles from "./home.module.css";
import ParticleField from "./components/ParticleField";
import AnimatedCounter from "./components/AnimatedCounter";
import ScrollReveal from "./components/ScrollReveal";
import TiltCard from "./components/TiltCard";
import SplashVideo from "./components/SplashVideo";

/* ---- animation variants ---- */
const stagger = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.12, delayChildren: 0.3 },
  },
};

const fadeUp = {
  hidden: { opacity: 0, y: 40, filter: "blur(10px)" },
  show: {
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.7, ease: [0.25, 0.4, 0.25, 1] },
  },
};

const scaleIn = {
  hidden: { opacity: 0, scale: 0.9 },
  show: {
    opacity: 1,
    scale: 1,
    transition: { duration: 0.6, ease: [0.25, 0.4, 0.25, 1] },
  },
};

export default function Home() {
  return (
    <>
      {/* ---- Ambient layers ---- */}
      <div className="aurora-bg">
        <div className="aurora-orb-center" />
      </div>
      <ParticleField />

      <div className={styles.page}>


        {/* ============ HERO ============ */}
        <section className={styles.hero}>
          <div className={styles.hero_layout}>
            <motion.div
              className={styles.hero_content}
              variants={stagger}
              initial="hidden"
              animate="show"
            >
              <motion.div variants={fadeUp} style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', marginBottom: '0.5rem' }}>
                <Image src="/images/app-logo.png" alt="App Logo" width={88} height={88} style={{ borderRadius: '18px', boxShadow: '0 8px 32px rgba(102, 252, 241, 0.15)' }} />
                <div className={styles.hero_badge} style={{ margin: 0 }}>
                  <span className={styles.hero_badge_dot} />
                  NSS IITP Official App
                </div>
              </motion.div>

              <motion.h1 variants={fadeUp} className={styles.hero_title}>
                The Official{" "}
                <span className="text-gradient-multi">Digital Hub</span>{" "}
                for NSS IIT Patna
              </motion.h1>

              <motion.p variants={fadeUp} className={styles.hero_subtitle}>
                Bridging the gap between technology and <strong style={{ color: "var(--accent-amber)" }}>social impact</strong>. The official NSS IIT Patna app empowers volunteers by seamlessly digitizing <strong style={{ color: "var(--accent-blue)" }}>grassroots community service</strong>, making it easier than ever to organize, coordinate, and leave a lasting mark on society.
              </motion.p>

              <motion.div variants={fadeUp} className={styles.hero_cta}>
                <a
                  href="https://play.google.com/store/apps/details?id=com.phad.chatapp&hl=en_IN"
                  className={styles.btn_glow}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <Smartphone style={{ width: 18, height: 18, marginRight: '4px' }} /> 
                  Get it on Play Store
                </a>
                <Link href="#story" className={styles.btn_outline}>
                  Explore Features <ArrowRight style={{ width: 18, height: 18, marginLeft: '4px' }} />
                </Link>
              </motion.div>




            </motion.div>

            <motion.div 
              className={styles.hero_visual}
              initial={{ opacity: 0, scale: 0.9, x: 20 }}
              animate={{ opacity: 1, scale: 1, x: 0 }}
              transition={{ duration: 0.8, delay: 0.6, ease: [0.25, 0.4, 0.25, 1] }}
            >
              <SplashVideo />

              {/* Technical Overview Card */}
              <div className={`${styles.arch_card} glass-card`}>
                <div className={styles.arch_card_header}>
                  <Code2 style={{ width: 16, height: 16, color: 'var(--accent-glow)' }} />
                  <span>Technical Overview</span>
                </div>
                
                <div className={styles.arch_card_body}>
                  {/* Features */}
                  <div className={styles.arch_card_col}>
                    <div className={styles.arch_section_title}>Core Features</div>
                    <div className={styles.hero_features_grid}>
                      <div className={styles.feature_item}><ShieldCheck style={{ width: 14, height: 14, color: 'var(--accent-glow)' }} /> <span>QR Attendance</span></div>
                      <div className={styles.feature_item}><Calendar style={{ width: 14, height: 14, color: 'var(--accent-glow)' }} /> <span>Role-based Calendar</span></div>
                      <div className={styles.feature_item}><Bell style={{ width: 14, height: 14, color: 'var(--accent-glow)' }} /> <span>Real-Time Sync</span></div>
                      <div className={styles.feature_item}><Database style={{ width: 14, height: 14, color: 'var(--accent-glow)' }} /> <span>Offline-First</span></div>
                      <div className={styles.feature_item}><ShieldCheck style={{ width: 14, height: 14, color: 'var(--accent-glow)' }} /> <span>Secure Access</span></div>
                    </div>
                  </div>

                  {/* Architecture */}
                  <div className={styles.arch_card_col}>
                    <div className={styles.arch_section_title}>Architecture</div>
                    <div className={styles.arch_card_layers}>
                      <div className={styles.arch_layer}>
                        <span className={styles.arch_layer_tag} style={{ color: 'var(--accent-purple)' }}>UI</span>
                        <span className={styles.arch_layer_text}>Jetpack Compose + M3</span>
                      </div>
                      <div className={styles.arch_layer}>
                        <span className={styles.arch_layer_tag} style={{ color: 'var(--accent-blue)' }}>Logic</span>
                        <span className={styles.arch_layer_text}>MVVM + Coroutines</span>
                      </div>
                      <div className={styles.arch_layer}>
                        <span className={styles.arch_layer_tag} style={{ color: 'var(--accent-amber)' }}>Data</span>
                        <span className={styles.arch_layer_text}>Firebase Firestore</span>
                      </div>
                      <div className={styles.arch_layer}>
                        <span className={styles.arch_layer_tag} style={{ color: 'var(--accent-glow)' }}>Auth</span>
                        <span className={styles.arch_layer_text}>Firebase Authentication</span>
                      </div>
                      <div className={styles.arch_layer}>
                        <span className={styles.arch_layer_tag} style={{ color: 'var(--accent-pink, #f472b6)' }}>Net</span>
                        <span className={styles.arch_layer_text}>Retrofit + OkHttp</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className={styles.arch_card_footer}>
                  <div className={styles.arch_section_title} style={{ textAlign: 'center' }}>Built With</div>
                  <div className={styles.hero_tech_pills}>
                    <span className={styles.hero_tech_pill}>
                      <Image src="/kotlin.svg" alt="Kotlin" width={14} height={14} className={styles.tech_logo} /> Kotlin
                    </span>
                    <span className={styles.hero_tech_pill}>
                      <Image src="/compose.svg" alt="Jetpack Compose" width={14} height={14} className={styles.tech_logo} /> Jetpack Compose
                    </span>
                    <span className={styles.hero_tech_pill}>
                      <Image src="/firebase.svg" alt="Firebase" width={14} height={14} className={styles.tech_logo} /> Firebase
                    </span>
                    <span className={styles.hero_tech_pill}>
                      <Image src="/android.svg" alt="Android MVVM" width={14} height={14} className={styles.tech_logo} /> MVVM
                    </span>
                    <span className={styles.hero_tech_pill}>
                      <Network style={{ width: 15, height: 15, color: '#10b981' }} /> Retrofit
                    </span>
                  </div>
                </div>
              </div>
            </motion.div>
          </div>

          {/* Scroll Indicator */}
          <motion.div
            className={styles.hero_scroll_indicator}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 1.8, duration: 0.8 }}
          >
            <div className={styles.scroll_mouse}>
              <div className={styles.scroll_wheel} />
            </div>
            Scroll
          </motion.div>
        </section>

        {/* ============ STATS STRIP ============ */}
        <section className={styles.stats_strip}>
          <AnimatedCounter end={350} suffix="+" label="Students Managed" gradient="linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%)" />
          <AnimatedCounter end={100} suffix="+" label="Events Per Semester" gradient="linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%)" />
          <AnimatedCounter end={4} suffix="" label="User Roles" gradient="linear-gradient(135deg, #c084fc 0%, #a855f7 100%)" />
          <AnimatedCounter end={15} suffix="+" label="Core Features" gradient="linear-gradient(135deg, #34d399 0%, #10b981 100%)" />
        </section>

        {/* ============ WHY WE BUILT IT ============ */}
        <section className={styles.story_section} id="story">
          <div className={styles.story_layout}>
            {/* Left sticky column */}
            <div className={styles.story_sticky}>
              <div className={styles.section_tag}>
                <Terminal style={{ width: 14, height: 14 }} />
                // the_origin_story
              </div>
              <h2 className={styles.section_title}>
                <span className="text-gradient-warm">Why We Built It</span>
              </h2>
              <p className={styles.story_sticky_desc}>
                From pen-and-paper chaos to an elegant, automated solution —
                built inside a technology institute that deserved better.
              </p>
              <div className={styles.story_sticky_stats}>
                <div className={styles.story_stat}>
                  <span className={styles.story_stat_number}>2</span>
                  <span className={styles.story_stat_label}>Developers</span>
                </div>
                <div className={styles.story_stat}>
                  <span className={styles.story_stat_number}>0</span>
                  <span className={styles.story_stat_label}>Prior Android Experience</span>
                </div>
                <div className={styles.story_stat}>
                  <span className={styles.story_stat_number}>1</span>
                  <span className={styles.story_stat_label}>Massive Problem</span>
                </div>
              </div>
            </div>

            {/* Right scrolling column */}
            <div className={styles.story_scroll}>
              <ScrollReveal delay={0}>
                <div className={`${styles.story_block} glass-card`}>
                  <div className={styles.story_block_accent} />
                  <h3 className={styles.story_block_title}>From Paper to Pixels</h3>
                  <p className={styles.story_block_text}>
                    In our first year at IIT Patna, we found ourselves deep in the heart of NSS — volunteering, coordinating, and noticing a glaring issue. <strong>Attendance was still being taken on paper.</strong> In an institute of technology, proxy was rampant, scheduling was a mess of spreadsheets, and managing 350+ volunteers felt like an impossible task.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_rose}`}>
                      <ShieldAlert style={{ width: 14, height: 14 }} /> proxy_issue
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_amber}`}>
                      <Terminal style={{ width: 14, height: 14 }} /> manual_tracking
                    </span>
                  </div>
                </div>
              </ScrollReveal>

              <div className={styles.story_connector}>
                <div className={styles.connector_line} />
              </div>

              <ScrollReveal delay={0.2}>
                <div className={`${styles.story_block} glass-card`}>
                  <div className={`${styles.story_block_accent} ${styles.story_block_accent_alt}`} />
                  <h3 className={styles.story_block_title}>The Phad Project</h3>
                  <p className={styles.story_block_text}>
                    <em>We were engineers. We could fix this.</em> Squeezed into a small room in Aryabhatta Hall with zero prior Android development experience, we decided to build a solution from scratch. It was fueled by late nights, sheer stubbornness, and a massive problem to solve. We called it <strong>The Phad Project</strong>.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_purple}`}>
                      <Code2 style={{ width: 14, height: 14 }} /> zero_experience
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_blue}`}>
                      <Users2 style={{ width: 14, height: 14 }} /> late_nights
                    </span>
                  </div>
                </div>
              </ScrollReveal>

              <div className={styles.story_connector}>
                <div className={styles.connector_line} />
              </div>

              <ScrollReveal delay={0.4}>
                <div className={`${styles.story_block} glass-card`}>
                  <div className={`${styles.story_block_accent} ${styles.story_block_accent_success}`} />
                  <h3 className={styles.story_block_title}>The Result</h3>
                  <p className={styles.story_block_text}>
                    That late-night idea evolved into the <strong>NSS IITP Official App</strong>. Now live on the Play Store, it&apos;s used daily by sub-coordinators and hundreds of volunteers, seamlessly handling QR attendance, multi-role calendars, and real-time syncing.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_emerald}`}>
                      <Zap style={{ width: 14, height: 14 }} /> scalable_architecture
                    </span>
                  </div>
                </div>
              </ScrollReveal>
            </div>
          </div>
        </section>

        {/* ============ TEAM ============ */}
        <section className={styles.section} id="team">
          <ScrollReveal>
            <div className={styles.section_header}>
              <div className={styles.section_tag}>
                <Terminal style={{ width: 14, height: 14 }} />
                // the_team
              </div>
              <h2 className={styles.section_title}>
                <span className="text-gradient-cool">The Developers</span>
              </h2>
              <p className={styles.section_desc}>
                Collaborative engineering at its finest — two developers,
                one vision, zero compromises.
              </p>
            </div>
          </ScrollReveal>

          <div className={styles.team_grid}>
            {/* Dev 1 */}
            <ScrollReveal delay={0} direction="left">
              <TiltCard className={`${styles.team_card} glass-card glass-card-blue`}>
                <div className={`${styles.team_card_accent} ${styles.team_card_accent_blue}`} />
                <div className={styles.team_header}>
                  <div className={styles.avatar}>
                    <Image src="/images/eshan-avatar-new.jpg" alt="Eshan Bhaskar" width={220} height={220} className={styles.avatar_image} />
                  </div>
                  <div className={styles.team_info}>
                    <h3>Eshan Bhaskar</h3>
                    <span className={`${styles.team_role} ${styles.role_blue}`}>Core Developer</span>
                  </div>
                </div>
                <p className={styles.team_bio}>
                  Spearheaded the initial conception and development. Focused on
                  solving core inefficiencies in the Teaching & Technical Wings,
                  architecting robust data flow and security.
                </p>
                <div className={styles.team_links_row}>
                  <a href="https://github.com/EshanBhaskar" target="_blank" rel="noopener noreferrer" className={styles.team_link_btn}>
                    <Code style={{ width: 13, height: 13 }} /> GitHub
                    <ArrowUpRight style={{ width: 11, height: 11 }} />
                  </a>
                  <a href="https://www.linkedin.com/in/eshan-bhaskar/" target="_blank" rel="noopener noreferrer" className={styles.team_link_btn}>
                    <User style={{ width: 13, height: 13 }} /> LinkedIn
                    <ArrowUpRight style={{ width: 11, height: 11 }} />
                  </a>
                </div>
              </TiltCard>
            </ScrollReveal>

            {/* Dev 2 */}
            <ScrollReveal delay={0.15} direction="right">
              <TiltCard className={`${styles.team_card} glass-card glass-card-amber`}>
                <div className={`${styles.team_card_accent} ${styles.team_card_accent_warm}`} />
                <div className={styles.team_header}>
                  <div className={styles.avatar}>
                     <Image src="/images/ankesh-avatar.png" alt="Ankesh Kumar" width={220} height={220} className={styles.avatar_image} />
                  </div>
                  <div className={styles.team_info}>
                    <h3>Ankesh Kumar</h3>
                    <span className={`${styles.team_role} ${styles.role_amber}`}>Core Developer</span>
                  </div>
                </div>
                <p className={styles.team_bio}>
                  Partnered in scaling the application to its current robust
                  architecture. Dedicated to optimizing UX, integrating Jetpack
                  Compose, and expanding feature sets.
                </p>
                <div className={styles.team_links_row}>
                  <a href="https://github.com/arbitcoper" target="_blank" rel="noopener noreferrer" className={styles.team_link_btn}>
                    <Code style={{ width: 13, height: 13 }} /> GitHub
                    <ArrowUpRight style={{ width: 11, height: 11 }} />
                  </a>
                  <a href="https://www.linkedin.com/in/ankesh-kumar-758570284/" target="_blank" rel="noopener noreferrer" className={styles.team_link_btn}>
                    <User style={{ width: 13, height: 13 }} /> LinkedIn
                    <ArrowUpRight style={{ width: 11, height: 11 }} />
                  </a>
                </div>
              </TiltCard>
            </ScrollReveal>
          </div>

          {/* Acknowledgements */}
          <ScrollReveal delay={0.2}>
            <div className={`${styles.ack_card} glass-card`}>
              <h4 className={styles.ack_title}>Acknowledgements</h4>
              <p className={styles.ack_text}>
                Special thanks to former developers{" "}
                <strong>Aditya Onam</strong> and{" "}
                <strong>Aditya Gupta</strong> for their contributions to
                earlier iterations of the platform.
              </p>
            </div>
          </ScrollReveal>
        </section>

        {/* ============ FOOTER ============ */}
        <footer className={styles.footer}>
          <p className={styles.footer_text}>
            Built with precision by{" "}
            <a href="#">Eshan Bhaskar</a> &{" "}
            <a href="#">Ankesh Kumar</a> — IIT Patna
          </p>
        </footer>
      </div>
    </>
  );
}
