import type { Metadata } from "next";
import { ModeBanner } from "@/components/ModeBanner";
import { Providers } from "@/lib/query";
import "./globals.css";

export const metadata: Metadata = {
  title: "StockPilot AI",
  description: "AI-powered inventory, forecasting, and business intelligence for growing retailers and distributors."
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <ModeBanner />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
