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
const stagger: any = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.12, delayChildren: 0.3 },
  },
};

const fadeUp: any = {
  hidden: { opacity: 0, y: 40, filter: "blur(10px)" },
  show: {
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.7, ease: [0.25, 0.4, 0.25, 1] },
  },
};

const scaleIn: any = {
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
              <motion.div variants={fadeUp} style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
                <Image src="/images/app-logo.png" alt="App Logo" width={88} height={88} style={{ borderRadius: '18px', boxShadow: '0 8px 32px rgba(102, 252, 241, 0.15)' }} />
                <div className={styles.hero_badge} style={{ margin: 0 }}>
                  <span className={styles.hero_badge_dot} />
                  NSS IITP Official App
                </div>
                <a
                  href="https://play.google.com/store/apps/details?id=com.phad.chatapp&hl=en_IN"
                  className={styles.play_store_badge}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <Smartphone style={{ width: 14, height: 14, marginRight: '4px' }} /> Get it on Play Store
                </a>
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
                <Link href="/explore" className={styles.btn_explore}>
                  Explore Features <ArrowRight className={styles.btn_explore_icon} style={{ width: 18, height: 18, marginLeft: '4px' }} />
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

            </div>

            {/* Right scrolling column */}
            <div className={styles.story_scroll}>
              <ScrollReveal delay={0}>
                <div className={`${styles.story_block} glass-card`}>
                  <div className={styles.story_block_accent} />
                  <h3 className={styles.story_block_title}>Death by Excel</h3>
                  <p className={styles.story_block_text}>
                    As first-year volunteers, we learned quickly that the hardest part of NSS wasn&apos;t the actual social work—it was the spreadsheets. Tracking 350+ students across 100+ events meant horizontally scrolling through a chaotic mess of columns just to verify one person. Hours calculations were constantly breaking, anyone could edit the sheets without a trace, and paper registers meant proxy attendance was rampant. With zero centralized way to even view upcoming events, the system was practically begging to be replaced.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_amber}`}>
                      <Terminal style={{ width: 14, height: 14 }} /> spreadsheet_chaos
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_purple}`}>
                      <Database style={{ width: 14, height: 14 }} /> data_nightmare
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_rose}`}>
                      <ShieldAlert style={{ width: 14, height: 14 }} /> rampant_proxy
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
                  <h3 className={styles.story_block_title}>Enter &quot;The Phad Project&quot;</h3>
                  <p className={styles.story_block_text}>
                    We squeezed into a dorm room in Aryabhatta Hostel with a clear goal but absolutely no roadmap. We didn&apos;t know how to actually fix the problem, what language to learn first, or even whom to ask for help. We decided to build a full Android app anyway and named it <strong>The Phad Project</strong> (because <em>phatne wali thi ise banane me</em> 😅). We had zero Android experience—just a massive problem, a lot of late nights, and the wildly naive stubbornness to ultimately fix it.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_amber}`}>
                      <ArrowRight style={{ width: 14, height: 14 }} /> zero_roadmap
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_blue}`}>
                      <Users2 style={{ width: 14, height: 14 }} /> late_nights
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_purple}`}>
                      <Code2 style={{ width: 14, height: 14 }} /> naive_stubbornness
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
                  <h3 className={styles.story_block_title}>From Prototype to Play Store</h3>
                  <p className={styles.story_block_text}>
                    We actually figured it out. That clueless late-night experiment is now the <strong>NSS IITP Official App</strong>. Live on the Play Store, it empowers sub-coordinators to effortlessly manage proxy-proof QR attendance, while giving 350+ volunteers a transparent record of their hours. Packing features like automated teaching schedules, leave approvals, event history, and real-time feeds, it completely modernized how our campus coordinates social service.
                  </p>
                  <div className={styles.story_block_tags}>
                    <span className={`${styles.story_tag} ${styles.tag_rose}`}>
                      <ShieldCheck style={{ width: 14, height: 14 }} /> battle_tested
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_blue}`}>
                      <Smartphone style={{ width: 14, height: 14 }} /> play_store_live
                    </span>
                    <span className={`${styles.story_tag} ${styles.tag_emerald}`}>
                      <Zap style={{ width: 14, height: 14 }} /> proxy_proof_qr
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
            <ScrollReveal delay={0} direction="left" className={styles.team_card_reveal}>
              <TiltCard className={`${styles.team_card} glass-card glass-card-blue`} innerClassName={styles.team_card_inner}>
                <div className={`${styles.team_card_accent} ${styles.team_card_accent_blue}`} />
                <div className={styles.team_header}>
                  <div className={styles.avatar}>
                    <Image src="/images/eshan-avatar-new.jpg" alt="Eshan Bhaskar" width={220} height={220} className={styles.avatar_image} quality={100} unoptimized={true} />
                  </div>
                  <div className={styles.team_info}>
                    <h3>Eshan Bhaskar</h3>
                    <span className={`${styles.team_role} ${styles.role_blue}`}>Core Developer</span>
                  </div>
                </div>
                <p className={styles.team_bio}>
                  The mastermind single-handedly responsible for ending the golden era of proxy attendance. Built the QR system, calendar, and event management features that forced volunteers to <em>actually</em> show up. Also engineered the TTW scheduling algorithm—which he remains disturbingly overconfident about. Highly dedicated, overly enthusiastic, and the absolute bane of lazy students.
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
            <ScrollReveal delay={0.15} direction="right" className={styles.team_card_reveal}>
              <TiltCard className={`${styles.team_card} glass-card glass-card-amber`} innerClassName={styles.team_card_inner}>
                <div className={`${styles.team_card_accent} ${styles.team_card_accent_warm}`} />
                <div className={styles.team_header}>
                  <div className={styles.avatar}>
                     <Image src="/images/ankesh-avatar-new.jpg" alt="Ankesh Kumar" width={220} height={220} className={styles.avatar_image} quality={100} unoptimized={true} />
                  </div>
                  <div className={styles.team_info}>
                    <h3>Ankesh Kumar</h3>
                    <span className={`${styles.team_role} ${styles.role_amber}`}>Core Developer</span>
                  </div>
                </div>
                <p className={styles.team_bio}>
                  The calm, composed all-rounder who kept the app from spontaneously combusting. Mastered the logic for logins, in-app updates, FAQs, and notifications faster than anyone thought possible. Possesses a terrifying ability to learn new tech overnight and remains completely unfazed whenever the other developers inevitably break something.
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
                A deep nod of gratitude to the original catalysts of this project. Special thanks to <strong>Aditya Onam</strong>, who sparked the initial idea, assembled the team, and fueled our momentum, and to <strong>Aditya Gupta</strong>, who laid down the foundational database architecture and handled early code integrations. Their contributions to the initial iterations paved the way for the platform we have today.
              </p>
            </div>
          </ScrollReveal>
        </section>

      </div>
    </>
  );
}
