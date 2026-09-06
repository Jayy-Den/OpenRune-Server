# OpenRune Server Setup

## Overview
OpenRune Server is an OSRS private server project. You now have:
- Source code with git history (latest commits)
- Pre-built cache (rev 240.2)
- RSProx configured for client connection

## Location
- **Source code:** `%USERPROFILE%\Documents\OpenRune-Server`
- **RSProx installer:** `%USERPROFILE%\Downloads\RSProxSetup.exe`
- **RSProx launcher:** `%USERPROFILE%\Downloads\rsprox-launcher.jar`

## Git Setup
Your fork: `https://github.com/Jayy-Den/OpenRune-Server`
Upstream: `https://github.com/OpenRune/OpenRune-Server`

**Pull updates:** `git pull upstream main`
**Push changes:** `git add . && git commit -m "message" && git push origin main`

## Starting the Server

### Option 1: Using Gradle (recommended)
```bash
cd %USERPROFILE%\Documents\OpenRune-Server
./gradlew :server:app:run
```

### Option 2: Double-click `start-server.bat`

Wait for the message: `OpenRune Server Successfully initialized`

## Connecting with RSProx

1. **Install RSProx** (if you want a proper install):
   - Run `%USERPROFILE%\Downloads\RSProxSetup.exe`

2. **Or use the launcher directly:**
   - Ensure `%USERPROFILE%\.rsprox\proxy-targets.yaml` exists
   - Run: `java -jar %USERPROFILE%\Downloads\rsprox-launcher.jar`

3. **In your OSRS client, point to RSProx** (it should auto-detect)

## Configuration
- **Port:** 43594
- **Revision:** 240.2
- **World:** 255
- **Host:** 127.0.0.1

## Security
- `game.yml` is gitignored — never commit it
- `.data/client.key` and `.data/game.key` are gitignored
- Your edits in source code can be committed normally

## Useful Commands
```bash
# Pull latest from upstream
git pull upstream main

# Check status
git status

# View recent commits
git log --oneline -10

# Check what you've changed
git diff
```

## Troubleshooting

**Server won't start?**
- Make sure Java 21 is installed (you have Corretto 21)
- Check if port 43594 is already in use: `netstat -an | findstr 43594`
- Kill any existing java processes: `taskkill /F /IM java.exe`

**Cache errors?**
- Delete `%APPDATA%\openrune\caches\oldschool\240`
- Re-run `./gradlew install`

**RSProx can't connect?**
- Ensure server is running and listening on 43594
- Check modulus in `proxy-targets.yaml` matches `.data/client.key`
- RSProx only works on Windows/Linux, not macOS
# Test
Make a change to test
