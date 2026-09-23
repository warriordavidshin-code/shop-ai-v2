import type { Metadata } from "next";
import { Cormorant_Garamond, Gowun_Batang } from "next/font/google";
import Script from "next/script";
import { Suspense } from "react";
import { SocialAuthNotice } from "@/components/auth/SocialAuthNotice";
import "./globals.css";
import "ckeditor5/ckeditor5.css";

const cormorant = Cormorant_Garamond({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-cormorant",
  display: "swap",
});

const gowun = Gowun_Batang({
  subsets: ["latin"],
  weight: ["400", "700"],
  variable: "--font-gowun",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://btc-camel.com"),
  title: "BoutiqueCamel",
  description: "쁘띠카멜 — 편안한 데일리룩과 특별한 날의 코디",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${cormorant.variable} ${gowun.variable}`}>
      <head>
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css"
        />
      </head>
      <body className="font-sans antialiased">
        <Script
          id="daum-postcode-script"
          src="https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
          strategy="afterInteractive"
        />
        {children}
        <Suspense fallback={null}>
          <SocialAuthNotice />
        </Suspense>
      </body>
    </html>
  );
}
