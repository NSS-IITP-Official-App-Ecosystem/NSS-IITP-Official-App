import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "NSS IITP Official App | Showcase",
  description: "A comprehensive look at the engineering, security, and collaborative effort behind the NSS IITP Official App.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body suppressHydrationWarning>
        <div id="orientation-lock">
          <div className="orientation-lock-content">
            <div className="orientation-icon">🖥️</div>
            <h2>Desktop Experience Recommended</h2>
            <p>This showcase is engineered specifically for PCs and laptops.</p>
            <p className="orientation-subtext">Please view on a desktop, or rotate your device to landscape mode for the best experience.</p>
          </div>
        </div>
        {children}
        <footer className="global-footer">
          <p className="global-footer-text">
            Built with <span style={{ color: 'var(--accent-blue)' }}>{'<'}code{'>'}</span> and no caffeine by{" "}
            <a href="https://www.linkedin.com/in/eshan-bhaskar/" target="_blank" rel="noopener noreferrer">Eshan Bhaskar</a> — IIT Patna
          </p>
        </footer>
      </body>
    </html>
  );
}
