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
3. **Dedicated GPU (recommended, once per machine):** Windows may route Java to the integrated GPU by default — the adapter whose driver black-screens. Run `setup-gpu-pin.bat` (repo root) to pin Java to the high-performance GPU; takes effect for newly started RSProx/client processes.
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

**Computer-control MCP (agent "eyes/hands" for QA):**
- Config lives in `.agents/mcp.json` and `.freebuff/mcp-server.json` (both written 2026-09-09): launches `uvx computer-control-mcp@latest` (uvx resolves from the user's local hermes bin), screenshots dir `.freebuff/mcp-shots`.
- **Verified working end-to-end (2026-09-09):** stdio handshake OK (server `ComputerControlMCP` v1.13.0), 15 tools listed (`take_screenshot`, `take_screenshot_with_ocr`, `click_screen`, `type_text`, `press_keys`, `list_windows`, `key_down/up`, `mouse_down/up/move`, `drag_mouse`, `activate_window`, `get_screen_size`, `wait_milliseconds`), and a real `take_screenshot` call returned a live PNG (inline base64 — the shots dir stays empty by design).
- **Why it never attached (found 2026-09-09 by reading orchestrator.js):** Freebuff's orchestrator only reads MCP config from the **home directory**: `~/.agents/mcp.json` (`{"mcpServers": {...}}`), with per-server `enabled`/approval state in `~/.freebuff/mcp.json`. The project-level `.agents/mcp.json` and `.freebuff/mcp-server.json` (kept as reference) are **inert** — wrong location.
- **Setup done:** real config written to `%USERPROFILE%\.agents\mcp.json` (server key `computer-control`, stdio via uvx, env as above).
- **Remaining step (app consent flow, by design):** after restarting Freebuff Desktop, the app shows the new connector as `awaiting_launch_approval` → approve the launch (it will display the uvx command) → it discovers the 15 tools → approve the tool manifest → server goes `enabled`. Only then do the tools appear in the agent's toolset. This mirrors how the app gates arbitrary command execution — do not hand-edit `~/.freebuff/mcp.json` to skip it.
- **Manual probe (no client needed):** pipe `initialize` → `notifications/initialized` → `tools/list` (or `tools/call`) JSON-RPC over stdio into `uvx computer-control-mcp@latest` with `COMPUTER_CONTROL_MCP_SCREENSHOT_DIR` set. Verified working: server `ComputerControlMCP` v1.13.0, 15 tools, `take_screenshot` returns a live inline PNG (the shots dir stays empty by design).

- **In-world QA of the content drop via MCP hands (2026-09-10):** logged in fully by MCP (maximized 1920x1080 client: Existing User ~(1022,315), password row ~(980,288), Login ~(865,346)), then drove `::tele` / `::item` / `::npcadd` / `::ifopen` as admin. **Gotchas:** (1) `::tele` is space-separated (`::tele 3059 3495 0`), echoing `Teleported to CoordGrid(...)`; (2) `::npcadd <duration> <npc>` — duration first, in cycles (100 ≈ 60s too short to observe, use 1000); (3) on a maximized client the chat input can swallow a typed `\n` — type the command, verify the input line via OCR, then press Enter separately; (4) `::ifopen <rscm-name>` opens any interface server-side and logs `Opened interface: '<name>' (id=N)` — use it to prove a plugin's interface renders without pixel-hunting (verified: `interface.fairyrings` id=398, `interface.ge_offers` with 8 "Empty" slots, History + Repeat Offer buttons); (5) hover tooltips OCR fine at top-left — use `move_mouse` + screenshot to identify UI elements before clicking; (6) the driver's auto-exit must be generous (now 4h) — a 15m limit killed a session mid-QA.
- **Computer-control MCP attached and proven (2026-09-09 17:27):** with the config in `~/.agents/mcp.json` and the connector approved in Freebuff's UI, the agent's toolset gains the 15 tools and can drive the whole playtest itself: launch `run-driver.cmd`, `activate_window "OpenRune Server"`, OCR the login screen, click Existing User → password → Login, and verify via `Login accepted` in the server log + OCR of the in-world chatbox. **Gotchas learned:** (1) OCR works regardless of z-order but clicks need the game window foreground — `activate_window` immediately before every click; (2) RuneLite prefills the username, and Ctrl+A doesn't select-all in the OSRS login fields — don't touch the username field, just type into the empty password field; (3) on the error screen, clicking "Try again" (window-scoped coords) returns to the welcome screen; (4) window-scoped OCR takes ~20s per call and can time out — retry.

**Automated login (headless testing):**
`.freebuff/driver/` contains a working playtest rig (agent tooling — not needed to play):
- `Driver.java` launches a second RSProx session programmatically (`ProxyService.start → allocatePort()+5 → initializeHttpServer → launchRuneLiteClient`) so it never collides with a GUI session (worldlist port is hardcoded `43600 + sessionId`), and reports login events from the proxy's `SessionMonitor`.
- `Typer7.java` walks the login UI: it makes the client window topmost (synthetic clicks otherwise get swallowed by overlapping windows), clicks the welcome/login button (fixed offset ~x395,y408 in an 820×542 window), then clicks the password row and types the password with Enter.
- **The gotcha that cost a session:** RuneLite remembers the last username in the active profile (`%USERPROFILE%\.rlcustom\profiles2\default-*.properties`, key `loginscreen.username`) and pre-fills the login box, overriding the vanilla `jagexcache/.../preferences.dat` username. For a test account, patch that key to the test name (same length avoids layout churn) *before* launching.
- **Success oracle:** the RSProx log shows `Game login received, re-encrypting RSA` then *either* `Login failed with response code: <X>` or **nothing** (accepted). `SessionMonitor.onLogin` + `onNameUpdate <name>` in the driver log = player entered the world. The game server log now also records world entry: `Login accepted user='<name>' characterId=<id> world=<world> slot=<slot>` (with `(new account)` on first-ever login).
- **Verified full cycle (2026-09-09):** run `.freebuff/driver/run-driver.cmd` detached, wait until `Client initialization took` appears in `driver.log` (~30s), then run Typer7 with JNA on the classpath (plain `-cp .` fails with `NoClassDefFoundError: com/sun/jna/Library`):
  ```bat
  cd .freebuff\driver
  set "REP=%USERPROFILE%\.rsprox\launcher\repository"
  java -cp ".;%REP%\jna-5.13.0.jar;%REP%\jnagmp-3.0.0.jar" Typer7 "OpenRune Server" "<your-gui-title-suffix>" <password-from-run-driver.cmd>
  ```
  Pass the exclude filter as a string unique to your own logged-in GUI window (its title suffix), so Typer7 never grabs it. Confirmed: `Login accepted user='<test user>' ... slot=N` → clean `Logout completed` ~100s later.
