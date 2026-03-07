import java.io.*;
import java.util.*;
public class Main {
    private static boolean lastwithtab=false;
    private  static final  List<String> HISTORY=new ArrayList<>();
    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("exit", "echo", "type", "pwd", "cd","history"));

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));
    private static void runHistory(PrintStream out,List<String> args){
        int start=0;
        if(!args.isEmpty()){
            try{
                int n=Integer.parseInt(args.get(0));
                start=Math.max(HISTORY.size()-n,0);
            }catch(NumberFormatException e){
                out.println("history: invalid number: " + args.get(0));
                return;
            }
        }
        for(int i=start;i<HISTORY.size();i++){
            out.printf("%5d %s%n", i+1, HISTORY.get(i));
        }
    }
    public static void main(String[] args) throws Exception {

        setTerminalRawMode();
        Runtime.getRuntime().addShutdownHook(new Thread(Main::restoreTerminal));

        String currentDir = System.getProperty("user.dir");

        while (true) {

            String input = readLineWithAutocomplete();
            if (input == null) break;
            if (input.isEmpty()) continue;
            if(input!=null && !input.isBlank()){
                HISTORY.add(input);
            }
            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) continue;
            //pipeline check
            if(tokens.contains("|")){
                handlepipeline(tokens,currentDir);
                continue;
            }
        
            // ---------------- REDIRECTION ----------------

            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> commandArgs = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);

                switch (t) {
                    case ">", "1>":
                        stdoutFile = tokens.get(++i);
                        appendStdout = false;
                        break;
                    case ">>", "1>>":
                        stdoutFile = tokens.get(++i);
                        appendStdout = true;
                        break;
                    case "2>":
                        stderrFile = tokens.get(++i);
                        appendStderr = false;
                        break;
                    case "2>>":
                        stderrFile = tokens.get(++i);
                        appendStderr = true;
                        break;
                    default:
                        commandArgs.add(t);
                }
            }

            if (commandArgs.isEmpty()) continue;

            String command = commandArgs.get(0);
            List<String> argsList = commandArgs.subList(1, commandArgs.size());
            String joinedArgs = String.join(" ", argsList);

            // ---------------- BUILTINS ----------------

            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("echo")) {
                handleEcho(joinedArgs, stdoutFile, appendStdout, stderrFile);
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDir);
            }

            else if (command.equals("type")) {
                System.out.println(checkType(joinedArgs));
            }

            else if (command.equals("cd")) {
                currentDir = handleCd(argsList, currentDir);
            }
            else if (command.equals("history")) {
                runHistory(System.out,argsList);
            }

            // ---------------- EXTERNAL COMMAND ----------------

            else {
                try {

                    ProcessBuilder pb = new ProcessBuilder(commandArgs);
                    pb.directory(new File(currentDir));
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

                    // stdout
                    if (stdoutFile != null) {
                        File out = new File(stdoutFile);
                        if (appendStdout)
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(out));
                        else
                            pb.redirectOutput(out);
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    // stderr
                    if (stderrFile != null) {
                        File err = new File(stderrFile);
                        if (appendStderr)
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(err));
                        else
                            pb.redirectError(err);
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    pb.start().waitFor();

                } catch (IOException e) {
                    System.out.println(command + ": command not found");
                }
            }
        }

        restoreTerminal();
    }
    // to split the pipeline into separate commands, e.g. "ls -l | grep txt" → [["ls", "-l"], ["grep", "txt"]]
    // ---------------- PIPELINE SPLIT ----------------

private static List<List<String>> splitPipeline(List<String> tokens) {

    List<List<String>> commands = new ArrayList<>();
    List<String> current = new ArrayList<>();

    for (String t : tokens) {
        if (t.equals("|")) {
            commands.add(new ArrayList<>(current));
            current.clear();
        } else {
            current.add(t);
        }
    }

    commands.add(current);

    return commands;
}
private static byte[] runBuiltin(List<String> cmd, byte[] input, String currentDir) throws Exception {

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(out);

    String command = cmd.get(0);
    List<String> args = cmd.subList(1, cmd.size());

    switch (command) {

        case "echo":
            ps.println(String.join(" ", args));
            break;

        case "pwd":
            ps.println(currentDir);
            break;

        case "type":
            if (!args.isEmpty()) {
                ps.println(checkType(args.get(0)));
            }
            break;

        case "cd":
            handleCd(args, currentDir);
            break;
        case "history": 
            runHistory(ps,args);
            break;
    }

    ps.flush();
    return out.toByteArray();
}

private static void handlepipeline(List<String> tokens, String currentDir) {

    List<List<String>> commands = splitPipeline(tokens);

    try {

        Process prevProcess = null;
        byte[] builtinInput = null;

        for (int i = 0; i < commands.size(); i++) {

            List<String> cmd = commands.get(i);
            String command = cmd.get(0);

            boolean builtin = BUILTINS.contains(command);

            // ---------- BUILTIN ----------
            if (builtin) {

                if (prevProcess != null) {

                    ByteArrayOutputStream buf = new ByteArrayOutputStream();

                    try (InputStream in = prevProcess.getInputStream()) {
                        byte[] data = new byte[8192];
                        int len;
                        while ((len = in.read(data)) != -1) {
                            buf.write(data, 0, len);
                        }
                    }

                    prevProcess.waitFor();
                    builtinInput = buf.toByteArray();
                    prevProcess = null;
                }

                builtinInput = runBuiltin(cmd, builtinInput, currentDir);
                continue;
            }

            // ---------- EXTERNAL ----------
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(currentDir));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process p = pb.start();

            if (builtinInput != null) {
                try (OutputStream os = p.getOutputStream()) {
                    os.write(builtinInput);
                }
                builtinInput = null;
            }

            if (prevProcess != null) {

                Process left = prevProcess;
                Process right = p;

                Thread pump = new Thread(() -> {
                    try (
                        InputStream in = left.getInputStream();
                        OutputStream out = right.getOutputStream()
                    ) {
                        byte[] buf = new byte[8192];
                        int len;

                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                            out.flush();
                        }

                    } catch (IOException ignored) {}
                });

                pump.start();
            }

            prevProcess = p;
        }

        // ---------- PRINT FINAL OUTPUT ----------

        if (prevProcess != null) {

            try (InputStream in = prevProcess.getInputStream()) {

                byte[] buf = new byte[8192];
                int len;

                while ((len = in.read(buf)) != -1) {
                    System.out.write(buf, 0, len);
                    System.out.flush();
                }
            }

            prevProcess.waitFor();

        } else if (builtinInput != null) {

            System.out.write(builtinInput);
            System.out.flush();
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}
private static String longestCommonPrefix(List<String> list) {
        if (list.isEmpty()) return "";

        String prefix = list.get(0);

        for (int i = 1; i < list.size(); i++) {
            String current = list.get(i);

            int j = 0;
            while (j < prefix.length() &&
                j < current.length() &&
                prefix.charAt(j) == current.charAt(j)) {
                j++;
            }

            prefix = prefix.substring(0, j);

            if (prefix.isEmpty()) break;
        }

        return prefix;
    }

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

    List<String> matches = getMatches(buffer);
    Collections.sort(matches);

    String current = buffer.toString();

    if (matches.isEmpty()) {
        System.out.print("\007");
        System.out.flush();
        lastwithtab = false;
        continue;
    }

    if (matches.size() == 1) {
        buffer.setLength(0);
        buffer.append(matches.get(0)).append(" ");
        redraw(buffer);
        lastwithtab = false;
        continue;
    }

    String lcp = longestCommonPrefix(matches);

    if (!lcp.equals(current)) {
        buffer.setLength(0);
        buffer.append(lcp);
        redraw(buffer);
        lastwithtab = false;
    } else {
        if (!lastwithtab) {
            System.out.print("\007");
            System.out.flush();
            lastwithtab = true;
        } else {
            System.out.print("\r\n");
            System.out.println(String.join("  ", matches));
            System.out.print("$ " + buffer.toString());
            System.out.flush();
            lastwithtab = false;
        }
    }
}

            else if (c == 127) { // backspace
                if (buffer.length() > 0) {
                    buffer.deleteCharAt(buffer.length() - 1);
                    redraw(buffer);
                    lastwithtab=false;

                }
            }
            else {
                buffer.append(c);
                System.out.print(c);
                System.out.flush();
                lastwithtab=false;
            }
        }
    }

    private static List<String> getMatches(StringBuilder buffer) {

        String current = buffer.toString();
        if(current.contains(" ")){
            return Collections.emptyList();
        }
        Set<String> matches=new HashSet<>();
        //builtin commands
        for (String cmd : BUILTINS) {
            if (cmd.startsWith(current)) {
                matches.add(cmd);
            }
        }
        // external executables command
        String pathenv=System.getenv("PATH");
        if(pathenv!=null){
            String [] dirs=pathenv.split(File.pathSeparator);
            for(String dir:dirs){
                File folder=new File(dir);
                if(!folder.isDirectory())continue;
                File[] files=folder.listFiles();
                if(files==null)continue;
                for(File file:files){
                    if(file.isFile() && file.canExecute()){
                        String name=file.getName();
                        if(name.startsWith(current)){
                            matches.add(name);
                        }
                    }
                }
            }
        }


        return new ArrayList<>(matches);
    }

    private static void redraw(StringBuilder buffer) {
    System.out.print("\r$ " + buffer.toString());
    System.out.flush();
}


    private static void print(String msg) {
        System.out.print("\r" + msg);
        System.out.flush();
    }

    // ---------------- TERMINAL CONTROL ----------------

    private static void setTerminalRawMode() {
        try {
            Process p = new ProcessBuilder("/bin/sh", "-c",
                    "stty -icanon -echo min 1 time 0")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private static void restoreTerminal() {
        try {
            Process p = new ProcessBuilder("/bin/sh", "-c",
                    "stty sane")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    // ---------------- BUILTIN HELPERS ----------------

    private static void handleEcho(String text,
                                   String stdoutFile,
                                   boolean append,
                                   String stderrFile) throws IOException {

        if (stdoutFile != null) {
            try (FileWriter fw = new FileWriter(stdoutFile, append)) {
                fw.write(text + System.lineSeparator());
            }
        } else {
            System.out.println(text);
        }

        if (stderrFile != null) {
            new FileWriter(stderrFile, false).close();
        }
    }

    private static String handleCd(List<String> args, String currentDir) throws IOException {

        String target = args.isEmpty() ? System.getenv("HOME") : args.get(0);
        String home = System.getenv("HOME");

        if (target.equals("~")) {
            target = home;
        } else if (target.startsWith("~" + File.separator)) {
            target = home + target.substring(1);
        }

        File dir = new File(target);
        if (!dir.isAbsolute()) {
            dir = new File(currentDir, target);
        }

        if (dir.exists() && dir.isDirectory()) {
            return dir.getCanonicalPath();
        } else {
            System.out.println("cd: " + target + ": No such file or directory");
            return currentDir;
        }
    }

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

    // ---------------- PARSER WITH QUOTES ----------------

    private static List<String> parseInput(String input) {

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (escape) {
                current.append(ch);
                escape = false;
                continue;
            }

            if (ch == '\\' && !singleQuote) {
                escape = true;
                continue;
            }

            if (ch == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
                continue;
            }

            if (ch == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
                continue;
            }

            if (Character.isWhitespace(ch) && !singleQuote && !doubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}