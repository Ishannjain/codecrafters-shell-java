import java.io.*;
import java.util.*;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("exit", "echo", "type", "pwd", "cd"));

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws Exception {

        setTerminalRawMode();
        Runtime.getRuntime().addShutdownHook(new Thread(Main::restoreTerminal));

        String currentDir = System.getProperty("user.dir");

        while (true) {

            String input = readLineWithAutocomplete();
            if (input == null) break;
            if (input.isEmpty()) continue;

            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) continue;

            String command = tokens.get(0);
            List<String> argsList = tokens.subList(1, tokens.size());
            String joinedArgs = String.join(" ", argsList);

            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("echo")) {
                System.out.println(joinedArgs);
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDir);
            }

            else if (command.equals("type")) {
                System.out.println(checkType(joinedArgs));
            }

            else {
                System.out.println(command + ": command not found");
            }
        }

        restoreTerminal();
    }

    // ---------------- CHAR-BY-CHAR INPUT ----------------

    private static String readLineWithAutocomplete() throws IOException {

        StringBuilder buffer = new StringBuilder();
        print("$ ");

        while (true) {

            int r = reader.read();
            if (r == -1) return null;

            char c = (char) r;

            if (c == '\r' || c == '\n') {
                System.out.print("\r\n");
                return buffer.toString().trim();
            }

            else if (c == '\t') {
                handleAutocomplete(buffer);
            }

            else if (c == 127) { // backspace
                if (buffer.length() > 0) {
                    buffer.deleteCharAt(buffer.length() - 1);
                    redraw(buffer);
                }
            }

            else {
                buffer.append(c);
                System.out.print(c);
                System.out.flush();
            }
        }
    }

    private static void handleAutocomplete(StringBuilder buffer) {

        String current = buffer.toString();

        List<String> matches = new ArrayList<>();
        for (String cmd : BUILTINS) {
            if (cmd.startsWith(current)) {
                matches.add(cmd);
            }
        }

        if (matches.size() == 1) {
            buffer.setLength(0);
            buffer.append(matches.get(0)).append(" ");
            redraw(buffer);
        }
    }

    private static void redraw(StringBuilder buffer) {
        print("$ " + buffer.toString() + " ");
        print("$ " + buffer.toString());
    }

    // ---------------- TERMINAL CONTROL ----------------

    private static void setTerminalRawMode() {
        try {
            Runtime.getRuntime()
                    .exec(new String[]{"/bin/sh", "-c",
                            "stty -icanon -echo min 1 time 0"})
                    .waitFor();
        } catch (Exception ignored) {}
    }

    private static void restoreTerminal() {
        try {
            Runtime.getRuntime()
                    .exec(new String[]{"/bin/sh", "-c", "stty sane"})
                    .waitFor();
        } catch (Exception ignored) {}
    }

    private static void print(String msg) {
        System.out.print("\r" + msg);
        System.out.flush();
    }

    // ---------------- YOUR EXISTING METHODS ----------------

    private static String checkType(String command) {

        if (BUILTINS.contains(command)) {
            return command + " is a shell builtin";
        }

        String path = System.getenv("PATH");
        if (path == null) return command + ": not found";

        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return command + " is " + file.getAbsolutePath();
            }
        }

        return command + ": not found";
    }

    private static List<String> parseInput(String input) {
        return Arrays.asList(input.split(" "));
    }
}
