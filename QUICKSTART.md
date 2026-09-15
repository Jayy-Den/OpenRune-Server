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
- > **Not the default tool.** For in-game QA, start with the **devtools MCP** (next section) — it reads real game state and handles ~90% of the work. Use this computer-control server only for what devtools can't do: the pre-login screen (synthetic plugin keys don't reach it), driving arbitrary OS windows, or as a fallback while devtools is down.
- The server itself is tool-agnostic (stdio MCP, `uvx computer-control-mcp@latest`) — any agent client just adds it under its own `mcpServers` config with the command below. The Freebuff-specific notes in this section (config locations, approval flow) only apply to Freebuff.
- Config lives in `.agents/mcp.json` and `.freebuff/mcp-server.json` (both written 2026-09-09): launches `uvx computer-control-mcp@latest` (uvx resolves from the user's local hermes bin), screenshots dir `.freebuff/mcp-shots`.
- **Verified working end-to-end (2026-09-09):** stdio handshake OK (server `ComputerControlMCP` v1.13.0), 15 tools listed (`take_screenshot`, `take_screenshot_with_ocr`, `click_screen`, `type_text`, `press_keys`, `list_windows`, `key_down/up`, `mouse_down/up/move`, `drag_mouse`, `activate_window`, `get_screen_size`, `wait_milliseconds`), and a real `take_screenshot` call returned a live PNG (inline base64 — the shots dir stays empty by design).
- **Why it never attached (found 2026-09-09 by reading orchestrator.js):** Freebuff's orchestrator only reads MCP config from the **home directory**: `~/.agents/mcp.json` (`{"mcpServers": {...}}`), with per-server `enabled`/approval state in `~/.freebuff/mcp.json`. The project-level `.agents/mcp.json` and `.freebuff/mcp-server.json` (kept as reference) are **inert** — wrong location.
- **Setup done:** real config written to `%USERPROFILE%\.agents\mcp.json` (server key `computer-control`, stdio via uvx, env as above).
- **Remaining step (app consent flow, by design):** after restarting Freebuff Desktop, the app shows the new connector as `awaiting_launch_approval` → approve the launch (it will display the uvx command) → it discovers the 15 tools → approve the tool manifest → server goes `enabled`. Only then do the tools appear in the agent's toolset. This mirrors how the app gates arbitrary command execution — do not hand-edit `~/.freebuff/mcp.json` to skip it.
- **Manual probe (no client needed):** pipe `initialize` → `notifications/initialized` → `tools/list` (or `tools/call`) JSON-RPC over stdio into `uvx computer-control-mcp@latest` with `COMPUTER_CONTROL_MCP_SCREENSHOT_DIR` set. Verified working: server `ComputerControlMCP` v1.13.0, 15 tools, `take_screenshot` returns a live inline PNG (the shots dir stays empty by design).

- **In-world QA of the content drop via MCP hands (2026-09-10):** logged in fully by MCP (maximized 1920x1080 client: Existing User ~(1022,315), password row ~(980,288), Login ~(865,346)), then drove `::tele` / `::item` / `::npcadd` / `::ifopen` as admin. **Gotchas:** (1) `::tele` is space-separated (`::tele 3059 3495 0`), echoing `Teleported to CoordGrid(...)`; (2) `::npcadd <duration> <npc>` — duration first, in cycles (100 ≈ 60s too short to observe, use 1000); (3) on a maximized client the chat input can swallow a typed `\n` — type the command, verify the input line via OCR, then press Enter separately; (4) `::ifopen <rscm-name>` opens any interface server-side and logs `Opened interface: '<name>' (id=N)` — use it to prove a plugin's interface renders without pixel-hunting (verified: `interface.fairyrings` id=398, `interface.ge_offers` with 8 "Empty" slots, History + Repeat Offer buttons); (5) hover tooltips OCR fine at top-left — use `move_mouse` + screenshot to identify UI elements before clicking; (6) the driver's auto-exit must be generous (now 4h) — a 15m limit killed a session mid-QA.
- **In-world QA round 2 (2026-09-10 afternoon, 1920x1080 maximized):** `::wear` verified live (`Spawned inv obj Dramen staff x 1` → `Equipped 772 (slot: righthand).`). New gotchas: (1) chat commands **must** start with `::` — without it the text sends as public chat (OCR shows `tester: Tele ...`); (2) passwords are never persisted anywhere — if lost, run `java -cp <pg-jar> tools/DeleteAccount.java delete tester`, relaunch the driver with a new `DRIVER_PASS`, then re-grant ADMIN by updating `rights='ADMIN'` on the `accounts` row via the DeleteAccount connection recipe (applies at next login); (3) modals opened via `::ifopen` close client-side after roughly 30-60s — at OCR pace (one screenshot is 20-40s) a verify-then-click flow always loses the race: do the whole modal interaction as one rapid click batch and screenshot after. To inspect a modal without racing that lifetime, use `::ifopenpersist <idOrName>` (ex: `::ifopenpersist bankmain`); it opens the interface the same way but marks it server-side persistent, so the normal modal-auto-close cycle skips it. This is off by default for normal gameplay but is the intended escape hatch for agent-driven UI tests that need to screenshot/OCR a modal before interacting with it; close those modals manually with Escape or an `ifClose` path when you are done. (4) GE offers: click the slot box **body** to open the setup panel (title "Grand Exchange: Set up offer") — the OCR'd "Empty" text is the view child and no-ops; item-pick from the side inventory is a normal click (armed "Use" states need a floor-click to cancel; Escape closes the whole modal); (5) the side inventory is a 4-col grid from (1695,745) at ~36px pitch on 1920x1080 — identify slots by hover tooltip (top-left) plus the item-stats hovercard that renders full bonuses; clicking an unmet-requirement item prints the server gate message ("You need to have an attack level of 40.") in the chatbox — a useful live test of item gates; (6) `press_keys("enter")` + `type_text` from the MCP set is reliable for chat on a fresh driver window; (7) aggression reproduces on a fresh account instantly (`::npcadd 1000 aggressive_black_knight` → `Oh dear, you are dead!`).
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

---

## 🧪 In-game agent tooling (Devtools MCP) — setup, integration, login recipe

**Status (verified 2026-09-15):** full chain working — plugin sideloaded, MCP live on `127.0.0.1:7780`, test account logged in and driven in-world entirely through MCP tool calls (via Freebuff's native connector; every other agent tool reaches the same endpoint through the options below). Nothing here is specific to one agent — the helpers are plain Java/Python/PowerShell and the tools are plain JSON-RPC over HTTP.

> **Two complementary MCP servers, both documented in this file — which to use first:**
> 1. **Devtools MCP (this section) is the default.** It reads the game's real state — inventories, NPCs, widgets, chat, effects/projectiles, client state — with validated schemas and sub-second calls. Do ~90% of QA work here.
> 2. **Computer-control MCP (section above) is the fallback**, for exactly three cases: (a) the pre-login screen — the plugin's synthetic keys don't reach it, so password entry is OS-level by design; (b) anything the devtools tools can't address (e.g. clicking a coordinate on an interface with no server-side hook); (c) devtools being down (client crashed / client not yet logged in).
> 3. **Never substitute OCR/pixel work for a state read** — if a devtools tool can answer the question ("did the bow fire?", "how many arrows left?"), use it. OCR is a ~20s-per-call last resort.
>
> Privacy/security notes are at the end of this section.

**Architecture (3 layers):**
1. **Plugin → client:** OpenRune-Developer-Tools (the `flux-client-mcp` server) runs *inside* the RuneLite client and exposes ~25 tools (inventories, equipment, NPCs, chat, dialogues, widgets, effects/projectiles, screenshots, client state) over HTTP on `127.0.0.1:7780/mcp`. No auth; localhost only.
2. **Agent ↔ plugin:** your coding agent calls those tools the same way any MCP client would — a native HTTP connector, a stdio bridge, or raw HTTP from the shell (see step 2; all three hit the identical endpoint).
3. **Driver rig → proxy:** `.freebuff/driver/` launches a headless second RSProx session (port offset +5 → 43605/43706, never collides with a GUI session) and monitors login events. (`.freebuff/` is only the repo's helpers directory — its scripts work identically from any agent; nothing in them depends on a specific tool.)

**1) Plugin sideload (done once, survives updates):**
- Jar: `%USERPROFILE%\.rlcustom\sideloaded-plugins\OpenRune-Developer-Tools.jar` (a `.bak` of the original sits next to it).
- Auto-enable key in the active RuneLite profile (`%USERPROFILE%\.rlcustom\profiles2\default-*.properties`): `runelite.openrunedeveloptoolsplugin=true`.
- **Java-version gotcha (cost a session):** the client must run RSProx's **bundled JDK 11** (`%USERPROFILE%\AppData\Local\RSProx\jdk`). On host Java 21 the plugin crashes with `LambdaConversionException` and 7780 never binds. Driver/Typer7 classes must be compiled `--release 11` to match.

**2) Agent integration — pick whichever fits your coding tool (all hit the same endpoint):**
- **A. Native MCP HTTP connector** — any agent client that supports remote MCP servers (Freebuff, Claude Desktop/Code, Cursor, Windsurf, Codex, …). Name it e.g. `game-devtools` and register the URL:
  ```json
  { "mcpServers": { "game-devtools": { "type": "http", "url": "http://127.0.0.1:7780/mcp" } } }
  ```
  In Freebuff: Connectors → Add → paste the block → approve. In other tools: add the same entry wherever yours reads `mcpServers` (`~/.agents/mcp.json`, project `.mcp.json`, or its settings UI) and restart/reload if needed.
- **B. stdio bridge** — for MCP clients that only speak stdio:
  ```json
  { "mcpServers": { "game-devtools": { "command": "npx", "args": ["-y", "mcp-remote", "http://127.0.0.1:7780/mcp"] } } }
  ```
- **C. Raw HTTP (no MCP client at all)** — every tool is plain JSON-RPC, so any agent with shell access can drive it directly:
  ```bash
  curl -s -X POST http://127.0.0.1:7780/mcp -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_client_state","arguments":{}}}'
  ```
  Ready-made shim: `python .freebuff/scratch/mcp.py <tool> '<json-args>'` (wraps the same call and prints the result).
- **Lifetime (all options):** the MCP server dies with the game client. After any client restart: relaunch the rig, re-login, then reconnect/retry your integration if it doesn't recover on its own.

**2b) Upstream reference — read the project README for the full picture:**
https://github.com/OpenRune/OpenRune-Developer-Tools (standalone sideloaded plugin for RuneLite-based clients; builds against the Fluxious client's shaded jar, `gradlew jar`, auto-deploys to `~/.runelite|.rsprox|.fluxious/sideloaded-plugins/`). Key things the README documents beyond this file:
- **Live activity dashboard:** while the plugin runs, `http://127.0.0.1:7780/` serves a real-time dashboard of every MCP tool call — tool name, arguments, duration, inline pretty-printed results, screenshot thumbnails with zoom. It polls `GET /log?after=<id>` (same port); the server keeps the last 100 calls, so a refresh replays recent history. Verified working in this setup (2026-09-15). Useful for watching what an agent is doing in-game without attaching a debugger.
- **Tool catalog (~40 tools, grouped):**
  - *Inspection:* `screenshot` (canvas PNG, croppable to widget/interface/region), `get_widget`, `dump_interface` (diff two dumps to spot layout changes), `list_interfaces`, `get_widget_at`, `set_widget` (client-side live edit), `get_client_state`, `get_skills`
  - *Raw input:* `click`, `click_component`, `hover`, `drag`, `type_chat` (works for `::` commands), `press_key` (char, ENTER, ESCAPE, F1-F12, …)
  - *World:* `walk_to`, `list_npcs`, `list_players`, `list_objects`, `list_ground_items`, `pickup_item`, `list_projectiles`, `get_inventory` (any container: 93 inv, 94 worn, 95 bank, custom), `list_inventories`
  - *Interaction (one call, no screenshots):* `interact_npc` (cache-defined option, default Talk-to), `get_npc_menu` (live right-click menu incl. server-added options), `click_menu_option`, `item_action` (right-click + option in one call), `interact_object`, `interact_player`, `widget_action`, `invoke_menu_action` (raw menuAction escape hatch)
  - *Dialogue:* `get_dialogue`, `continue_dialogue` (blocks until state changes), `select_option`, `enter_input` (chatbox input, string/amount)
  - *History & vars:* `get_script_history` (clientscripts fired, with tick+args), `get_var_history` (varbit/varp/varc old→new), `get_var`, `get_projectile_history`, `get_effect_history` (animations/graphics), `get_chat_history`
  - *Waiting:* `wait_for` — blocks until a condition (`idle`, `at_tile`, `npc_dead`, `dialogue`, `interface_open/closed`, `chat_message`), polling every 100ms; replaces most hand-rolled sleep-and-poll loops
- **Claude Code / Claude Desktop integration examples** from the README: `claude mcp add --transport http flux http://127.0.0.1:7780/mcp`, and to skip per-call permission prompts, allow the whole server in `.claude/settings.local.json`: `{ "permissions": { "allow": ["mcp__flux"] } }`.
- **`--developer-mode` not needed here:** the README says to start the client with it and enable the plugin manually; our rig instead sideloads the jar + sets `runelite.openrunedeveloptoolsplugin=true` in the profile, so the plugin auto-enables on every launch (verified 2026-09-15 — driver-launched clients never pass the flag and the server comes up). Don't "fix" the rig to match the README.

**3) Login procedure (the proven recipe, in order):**
1. Prereqs: game server on 43594 (embedded postgres must be up — it listens on **52198**, not 5432, data dir `.data/postgres`).
2. Patch the profile's `loginscreen.username=<test user>` (same string length avoids layout churn) *before* launching.
3. Launch the driver detached (PowerShell `Start-Process` with `-RedirectStandardOutput` hangs — use the `cmd /c ... > log 2>&1` argument form):
   ```
   %USERPROFILE%\AppData\Local\RSProx\jdk\bin\java.exe -Dsun.java2d.d3d=false -cp %USERPROFILE%\.rsprox\launcher\repository\*;%USERPROFILE%\Documents\OpenRune-Server\.freebuff\driver Driver qa01 <pass> 20
   ```
   The third arg is the login watchdog in minutes — it hard-exits (killing the client) if no login happens in time, so **complete the login promptly**.
4. Wait for `127.0.0.1:7780` to LISTEN (plugin up = client booted), then confirm `get_client_state` shows `LOGIN_SCREEN`.
5. Open the login form with a **devtools canvas click** on "Existing User": canvas (473,295) in fixed mode (765×503) / (1022,289) maximized. Find positions deterministically with the pixel classifier `.freebuff/scratch/scan-classes.py` (white = buttons/fields, yellow = form text, red = errors) — never guess offsets; Typer7's hardcoded ones are stale for other window sizes.
6. **The plugin's synthetic keys do NOT reach the pre-login form** — password entry must be OS-level: make the window topmost+foreground (`.freebuff/scratch/topmost.ps1 -GamePid <pid>`), calibrate the canvas↔screen offset (park the cursor at a known screen point, read back `mouseX/mouseY` from `get_client_state`; offset is linear), then one atomic script: OS-click the password row → SendKeys password → Enter (`.freebuff/scratch/login-final.ps1`).
7. Oracles: `get_client_state` → `LOGGED_IN`; server log `Login accepted user='<test user>' ...`; driver log `ON-LOGIN`.

**Gotchas checklist (each cost real time once):**
- Only **one** driver session per `~/.rsprox` — a zombie client holding 43605 silently blocks every new driver launch. Check `netstat -ano | findstr 43605` and kill stale PIDs first.
- RuneLite remembers maximized-vs-restored per profile; the client may come up either way. **Recalibrate the offset per window size** (fixed-mode offset ≈ (562,272) when the window is at its default position; maximized ≈ (0,+23) — but always re-measure, don't trust these numbers).
- A login that bounces back to `LOGIN_SCREEN` ~25s after submit was **rejected by the server** — check the server log. Long-uptime servers can wedge their postgres pool (`Pool is empty, failed to create/setup connection`); a server restart fixes it.
- After `::master`, the queued level-up dialogues swallow all chat input — drain them (click-through) before typing commands.
- In-world, prefer your integration's native tool calls (options A/B); option C works from any shell and is the fallback when a connector is down. `.freebuff/scratch/mcp.py <tool> '<json>'` is the raw-HTTP shim.
- OCR (`take_screenshot_with_ocr`) is a ~20s-per-call last resort; pixel classification via the devtools screenshot is faster and deterministic for known UI colors.

**Privacy/security notes:**
- Both MCP servers are **localhost-only** and unauthenticated by design — anything on this machine can drive the game client or take screenshots. Do not port-forward or expose 7780 / the computer-control stdio bridge beyond the dev machine.
- **No credentials in tracked files:** driver credentials come from environment variables (`run-driver.cmd` defaults are placeholders); the `.freebuff/scratch/` helper scripts (which may contain a throwaway test-account password) and `driver.log` are git-ignored. Only `Driver.java`, `Typer7.java`, and `run-driver.cmd` are tracked — verify with `git ls-files .freebuff` before committing anything new there.
- Use **throwaway test accounts only** (e.g. `qa01`) — accounts driven by these tools are created locally against your own server and never touch real Jagex credentials or third-party services.
