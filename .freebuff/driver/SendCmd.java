import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Types one line into the game window's chat box using OS-level key events, which the client
 * accepts where synthetic client-side key dispatch does not.
 * Usage: java SendCmd <titleContains> <text>
 */
public class SendCmd {
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

    private static final int SWP_NOMOVE = 0x2, SWP_NOSIZE = 0x1;
    private static final long HWND_TOPMOST = -1, HWND_NOTOPMOST = -2;

    public static void main(String[] args) throws Exception {
        String mustContain = args[0];
        String text = args[1];

        final long[] found = {0};
        User32.I.EnumWindows((hwnd, lp) -> {
            if (!User32.I.IsWindowVisible(hwnd)) return true;
            int len = User32.I.GetWindowTextLengthW(hwnd);
            if (len <= 0) return true;
            char[] buf = new char[len + 1];
            User32.I.GetWindowTextW(hwnd, buf, len + 1);
            String title = new String(buf, 0, len);
            if (title.contains(mustContain)) {
                found[0] = hwnd;
            }
            return true;
        }, null);
        if (found[0] == 0) {
            System.out.println("SENDCMD: no matching window found");
            System.exit(2);
        }
        long hwnd = found[0];
        int[] rect = new int[4];
        User32.I.GetWindowRect(hwnd, rect);
        System.out.println("SENDCMD: hwnd " + hwnd + " rect L=" + rect[0] + " T=" + rect[1]);

        User32.I.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
        Thread.sleep(400);
        User32.I.SetForegroundWindow(hwnd);
        Thread.sleep(900);

        Robot robot = new Robot();
        robot.setAutoDelay(60);
        try {
            int midX = rect[0] + (rect[2] - rect[0]) / 2;
            int midY = rect[1] + (rect[3] - rect[1]) * 3 / 4;
            click(robot, midX, midY);
            Thread.sleep(500);
            tap(robot, KeyEvent.VK_ENTER);
            Thread.sleep(500);
            type(robot, text);
            Thread.sleep(500);
            tap(robot, KeyEvent.VK_ENTER);
            System.out.println("SENDCMD: sent " + text);
        } finally {
            User32.I.SetWindowPos(hwnd, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
        }
    }

    private static void click(Robot r, int screenX, int screenY) {
        User32.I.SetCursorPos(screenX, screenY);
        Thread.yield();
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void tap(Robot robot, int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    /** Keys that need shift held, i.e. the shifted symbol printed on that key. */
    private static final java.util.Map<Character, Integer> SHIFTED =
            java.util.Map.of('_', KeyEvent.VK_MINUS, '(', KeyEvent.VK_9, ')', KeyEvent.VK_0,
                    '*', KeyEvent.VK_8, '!', KeyEvent.VK_1, '+', KeyEvent.VK_EQUALS);

    /** Keys typed as-is. */
    private static final java.util.Map<Character, Integer> PLAIN =
            java.util.Map.of('-', KeyEvent.VK_MINUS, '/', KeyEvent.VK_SLASH, '.', KeyEvent.VK_PERIOD,
                    ',', KeyEvent.VK_COMMA, '\'', KeyEvent.VK_QUOTE, '=', KeyEvent.VK_EQUALS);

    private static void type(Robot robot, String text) {
        for (char c : text.toCharArray()) {
            if (c == ':') {
                robot.keyPress(KeyEvent.VK_SHIFT);
                tap(robot, KeyEvent.VK_SEMICOLON);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else if (c == ' ') {
                tap(robot, KeyEvent.VK_SPACE);
            } else if (Character.isUpperCase(c)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
                tap(robot, KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c)));
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else if (Character.isLetterOrDigit(c)) {
                tap(robot, KeyEvent.getExtendedKeyCodeForChar(c));
            } else {
                Integer shifted = SHIFTED.get(c);
                if (shifted != null) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    tap(robot, shifted);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                    continue;
                }
                Integer plain = PLAIN.get(c);
                if (plain != null) {
                    tap(robot, plain);
                    continue;
                }
                System.out.println("SENDCMD: unmapped char '" + c + "'");
            }
        }
    }
}
