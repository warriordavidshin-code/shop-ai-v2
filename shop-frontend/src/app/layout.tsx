import type { Metadata } from "next";
import "./globals.css";
import "ckeditor5/ckeditor5.css";

export const metadata: Metadata = {
  title: "BoutiqueCamel",
  description: "쁘띠카멜 — 편안한 데일리룩과 특별한 날의 코디",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="antialiased">{children}</body>
    </html>
  );
}
