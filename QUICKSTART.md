# OpenRune Server - Quickstart Guide

## 🎮 Play Right Now

### Step 1: Start the Server
```bash
cd %USERPROFILE%\Documents\OpenRune-Server
./gradlew :server:app:run
```
Wait for: `OpenRune Server Successfully initialized`

### Step 2: Connect with RSProx
1. Make sure RSProx is installed (you have `RSProxSetup.exe` in Downloads)
2. Double-click `%USERPROFILE%\Downloads\rsprox-launcher.jar`
   - Or run: `java -jar %USERPROFILE%\Downloads\rsprox-launcher.jar`
3. Your OSRS client should auto-detect RSProx

**Server Details:**
- Port: 43594
- World: 255
- Revision: 240.2

---

## 🛠️ Edit & Commit

### Make Changes
1. Edit files in `%USERPROFILE%\Documents\OpenRune-Server`
2. Check what changed: `git status`
3. Stage changes: `git add .`
4. Commit: `git commit -m "Your message"`
5. Push: `git push origin main`

### Get Updates
```bash
git pull upstream main
```

---

## 📁 File Locations

| What | Where |
|---|---|
| Source code | `%USERPROFILE%\Documents\OpenRune-Server` |
| Cache (local) | `%USERPROFILE%\Documents\OpenRune-Server\.data\cache` |
| RSProx config | `%USERPROFILE%\.rsprox\proxy-targets.yaml` |
| RSProx launcher | `%USERPROFILE%\Downloads\rsprox-launcher.jar` |
| Server jar (built) | `build\libs\openrune-server-release.jar` |

---

## 🚨 Troubleshooting

**Server won't start?**
- Check Java 21 is installed: `java -version`
- Kill existing Java processes: `taskkill /F /IM java.exe`
- Port conflict: `netstat -an | findstr 43594`

**RSProx can't connect?**
- Server must be running first
- Check modulus matches: `%USERPROFILE%\Documents\OpenRune-Server\.data\client.key`
- RSProx only works on Windows/Linux

**Need new cache?**
```bash
cd %USERPROFILE%\Documents\OpenRune-Server
./gradlew install
```
