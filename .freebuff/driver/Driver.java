import com.sun.jna.Native;
import io.netty.buffer.PooledByteBufAllocator;
import net.rsprox.cache.api.CacheProvider;
import net.rsprox.proxy.ProxyService;
import net.rsprox.proxy.binary.BinaryHeader;
import net.rsprox.proxy.target.ProxyTarget;
import net.rsprox.shared.SessionMonitor;
import net.rsprox.shared.account.JagexCharacter;
import net.rsprox.shared.property.RootProperty;

import java.awt.Robot;

/**
 * Headless playtest driver: full Launch Session flow + automated login.
 * 1) ProxyService.start (init) -> allocatePort(+5 offset) -> initializeHttpServer -> launchRuneLiteClient
 * 2) Waits for the login screen, focuses the client window, Robot-types the credentials
 * 3) Reports proxy-side login events (SessionMonitor.onLogin = proxy decoded a login)
 * Usage: java Driver <username> <password> [maxMinutes]
 */
public class Driver {
    public interface User32 extends com.sun.jna.Library {
        User32 I = Native.load("user32", User32.class);
        long FindWindowW(String cls, String title);
        boolean SetForegroundWindow(long hwnd);
    }

    private static volatile boolean loggedIn = false;

    public static void main(String[] args) throws Exception {
        String user = args.length > 0 ? args[0] : "TestPlayer01";
        String pass = args.length > 1 ? args[1] : "Passw0rd123!";
        long maxMinutes = args.length > 2 ? Long.parseLong(args[2]) : 20;

        ProxyService service = new ProxyService(PooledByteBufAllocator.DEFAULT);
        System.out.println("== Driver: initializing ProxyService ==");
        service.start(user, pass, (p, t, m1, m2) -> { });

        System.out.println("== Driver: allocating port + initializing HTTP server ==");
        // sessionId = port - proxy.port.min; live GUI session owns sessionId 0 (ports 43600/43701).
        // Use offset +5 so this headless session binds 43605/43706 instead of colliding.
        int port = service.allocatePort() + 5;
        ProxyTarget target = service.initializeHttpServer(port);
        System.out.println("== Driver: session HTTP port = " + port);

        SessionMonitor<BinaryHeader> monitor = new SessionMonitor<BinaryHeader>() {
            @Override public void onLogin(BinaryHeader h) {
                loggedIn = true;
                System.out.println("== Driver: ON-LOGIN (proxy decoded the game login) ==");
            }
            @Override public void onLogout(BinaryHeader h) { System.out.println("== Driver: onLogout"); }
            @Override public void onCacheUpdate(CacheProvider c) { }
            @Override public void onIncomingBytesPerSecondUpdate(long b) { }
            @Override public void onOutgoingBytesPerSecondUpdate(long b) { }
            @Override public void onNameUpdate(String n) { System.out.println("== Driver: onNameUpdate " + n); }
            @Override public void onUserInformationUpdate(long id, long hash) {
                System.out.println("== Driver: onUserInformationUpdate id=" + id + " hash=" + hash);
            }
            @Override public void onTranscribe(int i, RootProperty r) { }
        };

        JagexCharacter character = new JagexCharacter(1, user, 0L);
        System.out.println("== Driver: launching RuneLite client ==");
        service.launchRuneLiteClient(monitor, character, port, target);
        System.out.println("== Driver: launchRuneLiteClient returned; waiting for login screen ==");
        System.out.println("TYPING-READY");

        // Wait for the login screen to render. All input is done by Typer7
        // (external process) — this driver only launches and monitors.
        Thread.sleep(60_000);

        // Diagnostic only: does the expected client window exist?
        long hwnd = User32.I.FindWindowW(null, "OpenRune Server");
        System.out.println("== Driver: client hwnd = " + hwnd + " (typing handled by Typer7)");

        long deadline = System.currentTimeMillis() + maxMinutes * 60_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5_000);
            System.out.println(loggedIn ? "LOGIN-CONFIRMED-BY-PROXY" : "== Driver: no login yet ==");
            if (loggedIn) {
                Thread.sleep(4 * 60 * 60_000); // stay alive so the agent can drive in-world QA with MCP hands
                System.out.println("== Driver: done, exiting ==");
                System.exit(0);
            }
        }
        System.out.println("== Driver: timeout without login ==");
        System.exit(1);
    }
}
