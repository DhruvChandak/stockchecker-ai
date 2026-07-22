@echo off
setlocal
cd /d "%~dp0mobile"

if not exist node_modules (
  echo Installing mobile dependencies...
  npm install
)

set EXPO_PUBLIC_DEMO_MODE=true
set EXPO_PUBLIC_API_URL=http://127.0.0.1:8080

echo Starting StockPilot AI mobile demo with Expo.
echo Docker, Java, PostgreSQL, and backend are not required in this mode.
echo Keep this window open and scan the QR code with Expo Go.
npm start -- --lan
