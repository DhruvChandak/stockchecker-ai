@echo off
setlocal
cd /d "%~dp0web"

if not exist node_modules (
  echo Installing web dependencies...
  npm install
)

set NEXT_PUBLIC_DEMO_MODE=false
set NEXT_PUBLIC_API_URL=http://127.0.0.1:8080

echo Starting StockPilot AI web in REAL BACKEND mode at http://127.0.0.1:3000
echo Web API target: %NEXT_PUBLIC_API_URL%
echo Demo mode: %NEXT_PUBLIC_DEMO_MODE%
echo Keep this window open while using the app.
npm run dev -- --hostname 127.0.0.1
