@echo off
setlocal
cd /d "%~dp0web"

if not exist node_modules (
  echo Installing web dependencies...
  npm install
)

set NEXT_PUBLIC_DEMO_MODE=true
set NEXT_PUBLIC_API_URL=http://127.0.0.1:8080

echo Starting StockPilot AI WEB-ONLY demo at http://127.0.0.1:3000
echo Backend, Docker, Java, and PostgreSQL are not required in this mode.
echo Keep this window open while using the app.
npm run dev -- --hostname 127.0.0.1
