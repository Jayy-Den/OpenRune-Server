import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal login submitter: preferences.dat already carries the desired username
 * ("tester"), so this walks the welcome flow, clicks the password row, types
 * the password, and presses Enter.
 * Usage: java Typer7 <titleContains> <excludeContains> <password>
 */
public class Typer7 {
    public interface User32 extends com.sun.jna.Library {
        User32 I = Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);
        boolean EnumWindows(EnumProc cb, Pointer lp);
        int GetWindowTextW(long h, char[] sb, int max);
        int GetWindowTextLengthW(long h);
        boolean IsWindowVisible(long h);
        boolean SetForegroundWindow(long h);
        boolean GetWindowRect(long h, int[] rect);
        boolean SetCursorPos(int x, int y);
        boolean SetWindowPos(long h, long after, int x, int y, int cx, int cy, int flags);
    }

    public interface EnumProc extends com.sun.jna.Callback {
        boolean callback(long hwnd, Pointer lp);
    }

    private static final Map<Character, Integer> SHIFT_MAP = new HashMap<>();
    static {
        SHIFT_MAP.put('!', KeyEvent.VK_1);
        SHIFT_MAP.put('@', KeyEvent.VK_2);
        SHIFT_MAP.put('#', KeyEvent.VK_3);
        SHIFT_MAP.put('$', KeyEvent.VK_4);
        SHIFT_MAP.put('%', KeyEvent.VK_5);
        SHIFT_MAP.put('^', KeyEvent.VK_6);
        SHIFT_MAP.put('&', KeyEvent.VK_7);
        SHIFT_MAP.put('*', KeyEvent.VK_8);
        SHIFT_MAP.put('(', KeyEvent.VK_9);
        SHIFT_MAP.put(')', KeyEvent.VK_0);
        SHIFT_MAP.put('_', KeyEvent.VK_UNDERSCORE);
        SHIFT_MAP.put('+', KeyEvent.VK_EQUALS);
    }

    private static final int BTN_X = 395, BTN_Y = 408;   // welcome/login button center
    private static final int PASS_X = 409, PASS_Y = 311; // password text row
    private static final int SWP_NOMOVE = 0x2, SWP_NOSIZE = 0x1;
    private static final long HWND_TOPMOST = -1, HWND_NOTOPMOST = -2;

    public static void main(String[] args) throws Exception {
        String mustContain = args[0];
        String mustNotContain = args.length > 1 ? args[1] : "";
        String pass = args[2];

        final long[] found = {0};
        User32.I.EnumWindows((hwnd, lp) -> {
            if (!User32.I.IsWindowVisible(hwnd)) return true;
            int len = User32.I.GetWindowTextLengthW(hwnd);
            if (len <= 0) return true;
            char[] buf = new char[len + 1];
            User32.I.GetWindowTextW(hwnd, buf, len + 1);
            String title = new String(buf, 0, len);
            if (title.contains(mustContain) && (mustNotContain.isEmpty() || !title.contains(mustNotContain))) {
                found[0] = hwnd;
            }
            return true;
        }, null);
        if (found[0] == 0) {
            System.out.println("TYPER7: no matching window found");
            System.exit(2);
        }
        long hwnd = found[0];
        System.out.println("TYPER7: hwnd " + hwnd + " -> TOPMOST");
        User32.I.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
        User32.I.SetForegroundWindow(hwnd);
        Thread.sleep(1200);

        int[] rect = new int[4];
        User32.I.GetWindowRect(hwnd, rect);
        System.out.println("TYPER7: rect L=" + rect[0] + " T=" + rect[1]);

        Robot robot = new Robot();
        robot.setAutoDelay(45);
        try {
            System.out.println("TYPER7: clicking welcome button");
            click(robot, rect[0] + BTN_X, rect[1] + BTN_Y);
            Thread.sleep(2500);
            System.out.println("TYPER7: clicking login button");
            click(robot, rect[0] + BTN_X, rect[1] + BTN_Y);
            Thread.sleep(2500);
            System.out.println("TYPER7: clicking password field + typing");
            click(robot, rect[0] + PASS_X, rect[1] + PASS_Y);
            Thread.sleep(600);
            type(robot, pass);
            Thread.sleep(400);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            System.out.println("TYPER7: submitted (username from prefill)");
        } finally {
            User32.I.SetWindowPos(hwnd, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
            System.out.println("TYPER7: topmost released");
        }
    }

    private static void click(Robot r, int screenX, int screenY) {
        User32.I.SetCursorPos(screenX, screenY);
        Thread.yield();
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void type(Robot robot, String text) {
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c) || SHIFT_MAP.containsKey(c)) {
                Integer mapped = SHIFT_MAP.get(c);
                int base = mapped != null ? mapped
                        : KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(base);
                robot.keyRelease(base);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else if (Character.isLetterOrDigit(c)) {
                robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(c));
                robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(c));
            } else {
                System.out.println("TYPER7: skipping unmapped char '" + c + "'");
            }
        }
    }
}
