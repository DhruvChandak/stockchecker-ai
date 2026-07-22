import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#10201b",
        moss: "#326657",
        mint: "#dff5eb",
        amber: "#f6b84b",
        coral: "#df6b57"
      },
      boxShadow: {
        soft: "0 14px 40px rgba(16, 32, 27, 0.10)"
      }
    }
  },
  plugins: []
};

export default config;
