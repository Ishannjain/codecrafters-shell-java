import java.io.*;
import java.util.*;
public class Main {
    private static boolean lastwithtab=false;
    private  static final  List<String> HISTORY=new ArrayList<>();
    private static final Set<String> BUILTINS =new HashSet<>(Arrays.asList("exit", "echo", "type", "pwd", "cd","history"));
    static int historyIndex=-1;
    static int historySavedIndex=0;
    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

//     private static String getLastToken(String input) {
//     int lastSpace = input.lastIndexOf(' ');
//     if (lastSpace == -1) return input;
//     return input.substring(lastSpace + 1);
//        }   
    
//     private static List<String> getFileMatches(String prefix) {

//     List<String> matches = new ArrayList<>();
//     File dir = new File(".");

//     File[] files = dir.listFiles();
//     if (files == null) return matches;

//     for (File f : files) {
//         if (f.getName().startsWith(prefix)) {
//             matches.add(f.getName());
//         }
//     }

//     return matches;
// }
    
    private static void readHistoryFromFile(String path) {

    try (BufferedReader br = new BufferedReader(new FileReader(path))) {

        String line;
        while ((line = br.readLine()) != null) {

            line = line.trim();
            if (!line.isEmpty()) {
                HISTORY.add(line);
            }
        }

    } catch (IOException e) {
        System.err.println("history: " + path + ": No such file or directory");
    }
}
    
    private static void runHistory(PrintStream out,List<String> args){
        if(args.size()>=2 && args.get(0).equals("-r")){
            String file=args.get(1);
            readHistoryFromFile(file);
            return;
        }
        else if(args.size()>=2 && args.get(0).equals("-w")){
            String file=args.get(1);
            try(PrintWriter pw=new PrintWriter(new FileWriter(file))){
                for(String cmd:HISTORY){
                    pw.println(cmd);
                }
            }catch(IOException e){
                out.println("history: " + file + ": No such file or directory");
            }
            return;
        }
        else if(args.size()>=2 && args.get(0).equals("-a")){
            String file=args.get(1);
            try(PrintWriter pw=new PrintWriter(new FileWriter(file,true))){
                for(int i = historySavedIndex; i < HISTORY.size(); i++){
            pw.println(HISTORY.get(i));
        }

        historySavedIndex = HISTORY.size();
            }catch(IOException e){
                out.println("history: " + file + ": No such file or directory");
            }
            return;
        }
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
        
    private static void loadHistoryFromHistFile() {

    String histFile = System.getenv("HISTFILE");

    if (histFile == null || histFile.isEmpty()) {
        return;
    }

    File file = new File(histFile);

    if (!file.exists()) {
        return;
    }

    try (BufferedReader br = new BufferedReader(new FileReader(file))) {

        String line;
        while ((line = br.readLine()) != null) {

            line = line.trim();

            if (!line.isEmpty()) {
                HISTORY.add(line);
            }
        }

        historySavedIndex = HISTORY.size();

    } catch (IOException ignored) {
    }
}
    public static void main(String[] args) throws Exception {

        setTerminalRawMode();
        Runtime.getRuntime().addShutdownHook(new Thread(Main::restoreTerminal));

        String currentDir = System.getProperty("user.dir");
        loadHistoryFromHistFile();
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
                    saveHistoryToHistFile();
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
    
    private static void saveHistoryToHistFile() {

    String histFile = System.getenv("HISTFILE");

    if (histFile == null || histFile.isEmpty()) {
        return;
    }

    try (PrintWriter pw = new PrintWriter(new FileWriter(histFile))) {

        for (String cmd : HISTORY) {
            pw.println(cmd);
        }

    } catch (IOException ignored) {
    }
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

    String current = buffer.toString();

    int lastSpace = current.lastIndexOf(' ');
    String prefix = (lastSpace == -1) ? current : current.substring(lastSpace + 1);
    String before = (lastSpace == -1) ? "" : current.substring(0, lastSpace + 1);

    List<String> matches = getMatches(buffer);
    Collections.sort(matches);

    if (matches.isEmpty()) {
        System.out.print("\007");
        System.out.flush();
        lastwithtab = false;
        continue;
    }

    // ✅ SINGLE MATCH
    if (matches.size() == 1) {
    String match = matches.get(0);

    buffer.setLength(0);
    buffer.append(before).append(match);

    // ✅ only add space if NOT directory
    if (!match.endsWith("/")) {
        buffer.append(" ");
    }

    redraw(buffer);
    lastwithtab = false;
    continue;
}

    // ✅ MULTIPLE MATCHES → LCP
    String lcp = longestCommonPrefix(matches);

    if (!lcp.equals(prefix)) {
    buffer.setLength(0);
    buffer.append(before).append(lcp);
    redraw(buffer);
    lastwithtab = false;
}else {
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
            else if(c==27){
                int next1=reader.read();
                if(next1==91){ // arrow keys start with ESC [
                    int next2=reader.read();
                    if(next2==65){ // up arrow
                        if(historyIndex==-1){
                            historyIndex=HISTORY.size()-1;
                        }else if(historyIndex>0){
                            historyIndex--;
                        }
                        if(historyIndex>=0 && historyIndex<HISTORY.size()){
                            buffer.setLength(0);
                            buffer.append(HISTORY.get(historyIndex));
                            redraw(buffer);
                        }
                    }else if(next2==66){ // down arrow
                        if(historyIndex!=-1){
                            historyIndex++;
                            if(historyIndex>=HISTORY.size()){
                                historyIndex=-1;
                                buffer.setLength(0);
                            }else{
                                buffer.setLength(0);
                                buffer.append(HISTORY.get(historyIndex));
                            }
                            redraw(buffer);
                        }
                    }
                }
            }
            else {
                buffer.append(c);
                System.out.print(c);
                System.out.flush();
                historyIndex=-1;
                lastwithtab=false;
            }
        }
    }

    private static List<String> getMatches(StringBuilder buffer) {

    String current = buffer.toString();

    int lastSpace = current.lastIndexOf(' ');
    String prefix = (lastSpace == -1) ? current : current.substring(lastSpace + 1);

    Set<String> matches = new HashSet<>();

    // ---------------- COMMAND COMPLETION ----------------
    if (lastSpace == -1) {

        // builtins
        for (String cmd : BUILTINS) {
            if (cmd.startsWith(prefix)) {
                matches.add(cmd);
            }
        }

        // PATH executables
        String pathenv = System.getenv("PATH");
        if (pathenv != null) {
            String[] dirs = pathenv.split(File.pathSeparator);

            for (String dir : dirs) {
                File folder = new File(dir);
                if (!folder.isDirectory()) continue;

                File[] files = folder.listFiles();
                if (files == null) continue;

                for (File file : files) {
                    if (file.isFile() && file.canExecute()) {
                        String name = file.getName();
                        if (name.startsWith(prefix)) {
                            matches.add(name);
                        }
                    }
                }
            }
        }
    }

    // ---------------- FILENAME COMPLETION ----------------
    else {

    String token = (lastSpace == -1) ? current : current.substring(lastSpace + 1);

    String dirPath;
    

    int lastSlash = token.lastIndexOf('/');

    if (lastSlash == -1) {
        // normal current directory
        dirPath = ".";
        prefix = token;
    } else {
        dirPath = token.substring(0, lastSlash + 1); // include '/'
        prefix = token.substring(lastSlash + 1);
    }

    File dir = new File(dirPath.isEmpty() ? "." : dirPath);

    if (!dir.exists() || !dir.isDirectory()) {
        return Collections.emptyList();
    }

    File[] files = dir.listFiles();
    if (files == null) return Collections.emptyList();

    for (File f : files) {
    String name = f.getName();

    if (name.startsWith(prefix)) {

        if (f.isDirectory()) {
            name += "/";   // 🔥 key change
        }

        if (lastSlash == -1) {
            matches.add(name);
        } else {
            matches.add(dirPath + name);
        }
    }
}
}

    return new ArrayList<>(matches);
}
    private static void redraw(StringBuilder buffer) {
    System.out.print("\r");           // move cursor to start
    System.out.print("\033[K");       // clear line
    System.out.print("$ " + buffer.toString());
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