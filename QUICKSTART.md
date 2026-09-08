# OpenRune Server - Quickstart Guide

## 🎮 Play Right Now

### Step 1: Start the Server
```bash
cd %USERPROFILE%\Documents\OpenRune-Server
./gradlew :server:app:run
```
Wait for: `OpenRune Server Successfully initialized`

> **One-click alternative:** run `start-all.bat` (repo root) — it starts the jav config server, the game server (with port guards), and RSProx with the render fix, in order.

### Step 2: Connect with RSProx
1. RSProx reads its target list from `%USERPROFILE%\.rsprox\proxy-targets.yaml` — it must point at the local jav config server and use the server's client key modulus:
   ```yaml
   config:
     - name: OpenRune Server
       jav_config_url: http://127.0.0.1:8765/javconfig.ws
       varp_count: 15000
       revision: 240.2
       modulus: <contents of .data/client.key modulus>
   ```
2. **Launch RSProx with the bundled script** (repo root) — it permanently applies the AMD-safe rendering fix (`_JAVA_OPTIONS=-Dsun.java2d.d3d=false`) to every JVM it spawns, so the client never black-screens:
   ```bat
   start-rsprox.bat
   ```
   (RSProx's built-in launcher defaults to Java2D DirectDraw on Windows, which black-screens on some AMD drivers. Patching RSProx's jars isn't durable — it hash-verifies and re-downloads them — so the env-var script is the permanent fix.)
3. **Dedicated GPU (optional):** Windows may route Java to the integrated GPU by default. Pin it once in PowerShell:
   ```powershell
   $gp = 'HKCU:\Software\Microsoft\DirectX\UserGpuPreferences'
   Set-ItemProperty $gp 'C:\Program Files\Amazon Corretto\jdk21.0.7_6\bin\javaw.exe' 'GpuPreference=2;'
   Set-ItemProperty $gp 'C:\Program Files\Amazon Corretto\jdk21.0.7_6\bin\java.exe' 'GpuPreference=2;'
   ```
4. In the RSProx window, select "OpenRune Server" and click **Launch Session** — the RuneLite client boots, downloads the rev-240 cache, and shows the login screen.
5. If the login screen stays black: set `runelite.plugins.GpuPlugin=false` in the active profile (`%USERPROFILE%\.rlcustom\profiles2\*.properties`) and relaunch — that forces the CPU software renderer, which always works.

> **Verified (pixel-capture):** with the GPU plugin enabled, `d3d=false`, and Java pinned to the dedicated GPU, the client window renders correctly (login screen + world). The fallback in step 5 is only needed if your driver reproduces the old AMD black-screen.

> **Known limitation:** two RSProx sessions cannot run concurrently from one `~/.rsprox` — the second collides on the hardcoded worldlist port (`43600 + sessionId`, sessionId derived from `proxy.port.min`). `start-rsprox.bat` runs the standard single-session flow, where this never matters.

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

**Client window is black (cache loaded, no login screen)?**
- This is a rendering issue, not networking — check the client's JVM args contain `-Dsun.java2d.d3d=false` (see Step 2)
- Disable the GPU plugin: `runelite.plugins.GpuPlugin=false` in the active profile (AMD compatibility-profile drivers are known to black-screen with it)
- RSProx rewrites the client's javconfig to fetch the world list at `/worldlist.ws` — leave `endpoints.worldlist` in `%USERPROFILE%\.rsprox\proxy.properties` at its default (`worldlist.ws`); changing it desyncs the served path from the fetched one and hangs the login screen

**Verifying the JS5/cache path works:**
- The game server loads ~117,000 JS5 responses at startup (`Loaded ... JS5 responses` in the log)
- RSProx log (`%USERPROFILE%\.rsprox\logs\all.log`) should show: `Init JS5 remote connection` → `Js5 login successful` → `Js5 group response: archive 255, group 255` (master index), then goes silent (normal detached relaying)

**Need new cache?**
```bash
cd %USERPROFILE%\Documents\OpenRune-Server
./gradlew install
```
