@echo off
setlocal
cd /d "%~dp0web"

if not exist node_modules (
  echo Installing web dependencies...
  npm install
)

echo Starting StockPilot AI web at http://127.0.0.1:3000
echo Keep this window open while using the app.
npm run dev -- --hostname 127.0.0.1
