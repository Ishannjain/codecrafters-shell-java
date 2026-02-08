import java.util.*;
import java.io.File;
import java.io.FileWriter;

public class Main {

    // Checks whether a command is:
    // 1) a shell builtin
    // 2) an executable in PATH
    // 3) not found
    private static String CheckType(String result){

        // Built-in shell commands
        String types[] = {"exit","echo","type","pwd","cd"};

        // Get PATH environment variable
        String path = System.getenv("PATH");

        // Split PATH into individual directories
        String path_arr[] = path.split(File.pathSeparator);

        // Check if command is a builtin
        for(int i = 0; i < types.length; i++){
            if(result.equals(types[i])){
                return result + " is a shell builtin";
            }
        }

        // Search for executable in PATH directories
        for(int i = 0; i < path_arr.length; i++){
            File files = new File(path_arr[i], result);
            if(files.exists() && files.canExecute()){
                return result + " is " + files.getAbsolutePath();
            }
        }

        // If nothing matches
        return result + ": not found";
    }

    // Finds the executable file for a command in PATH
    private static File findExecutable(String command){

        String path = System.getenv("PATH");
        String path_arr[] = path.split(File.pathSeparator);

        // Loop through PATH directories
        for(String str : path_arr){
            File files = new File(str, command);

            // If executable exists and is runnable
            if(files.exists() && files.canExecute()){
                return files;
            }
        }

        // Command not found
        return null;
    }
    private static List<String> parseInput(String input){
        List<String> token=new ArrayList<>();
        StringBuilder curr=new StringBuilder();
        boolean issinglequotes=false;
        boolean isdoublequotes=false;
        boolean escape=false;
        for(int i=0;i<input.length();i++){
            char ch=input.charAt(i);
             // If previous char was backslash, take this char literally
            if(escape){
                curr.append(ch);
                escape=false;
                continue;
            }
            // Backslash outside quotes starts escape
            if (ch == '\\' && !issinglequotes && !isdoublequotes) {
                escape= true;
                continue;
            }
            //backslash inside double quotes
            if(ch=='\\' && isdoublequotes){
                if(i+1<input.length()){
                    char next=input.charAt(i+1);
                    if(next=='"' ||next=='\\' ||next=='$' || next=='`'){
                        curr.append(next);
                        i++;
                        continue;
                    }
                    //backslash new line continuation
                    if(next=='\n'){
                        i++;
                        continue;
                    }
                }
                curr.append('\\');
                continue;
            }
            // Toggle single quotes (only if not in double quotes)
            if(ch=='\'' && !isdoublequotes){
                issinglequotes=!issinglequotes;
                continue;
            }
            //toggle duble quotes(only if not in single quotes)
            else if(ch=='"' && !issinglequotes){
                isdoublequotes=!isdoublequotes;
                continue;
            }
            else if(Character.isWhitespace(ch) && !issinglequotes && !isdoublequotes){
                if(curr.length()>0){
                    token.add(curr.toString());
                    curr.setLength(0);
                }
            }else{
                curr.append(ch);
            }
        }
          // Trailing backslash → treat as literal '\'
        if (escape) {
            curr.append('\\');
        }
        if(curr.length() > 0){
            token.add(curr.toString());
        }
        return token;
    }

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        boolean flag = true;
        String commanddir=System.getProperty("user.dir");
        // Shell loop
        while(flag){
            System.out.print("$ ");

            // Read full user input
            String userinput = sc.nextLine();

            // Split input by spaces
            List<String> token=parseInput(userinput);
            // detect redirection 
            String redirectfile=null;
            List<String> finalargs=new ArrayList<>();
            for(int i=0;i<token.size();i++){
                String tmp=token.get(i);
                if(tmp.equals(">")|| tmp.equals("1>")){
                    if(i+1<token.size()){
                        redirectfile=token.get(i+1);
                        break;
                    }
                }else{
                    finalargs.add(tmp);
                }
            }
            // First word is the command
            String command = finalargs.get(0);

            // Remaining words are arguments
            String remainwords[] = token.subList(1,finalargs.size()).toArray(new String[0]);

            // Join arguments for echo/type
            String result = String.join(" ", remainwords);
           
            // Built-in: exit
            if(command.equals("exit")){
                flag = false;
                continue;
            }

            // Built-in: echo
            if (command.equals("echo")) {
                    if (redirectfile != null) {
                        try (FileWriter fw = new FileWriter(redirectfile)) {
                            fw.write(result + System.lineSeparator());
                        }
                    } else {
                        System.out.println(result);
                    }
                }

            // Built-in: type
            else if(command.equals("type")){
                System.out.println(CheckType(result));
            }

            // Built-in: pwd
            else if(command.equals("pwd")){
                System.out.println(commanddir);
            }
            //Built-in: cd
          else if (command.equals("cd")) {

                String home = System.getenv("HOME");

                if (token.size() < 2 || token.get(1).equals("~")) {
                    commanddir = home;
                } 
                else {
                    String target = token.get(1);  //  copy into variable

                    // Handle ~/something
                    if (target.startsWith("~" + File.separator)) {
                        target = home + target.substring(1);  // reassign variable
                    }

                    File dir = new File(target);

                    if (!dir.isAbsolute()) {
                        dir = new File(commanddir, target);
                    }

                    if (dir.exists() && dir.isDirectory()) {
                        commanddir = dir.getCanonicalPath();
                    } else {
                        System.out.println("cd: " + token.get(1) + ": No such file or directory");
                    }
                }
          }
           // External command execution
            else {
                File exe = findExecutable(command);

                if (exe != null) {

                    List<String> cmd = new ArrayList<>();
                    cmd.add(command);
                    cmd.addAll(Arrays.asList(remainwords));

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(new File(commanddir));

                    // stdin always from terminal
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

                    // stderr stays on terminal
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                    // stdout redirection
                    if (redirectfile != null) {
                        pb.redirectOutput(new File(redirectfile));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    pb.start().waitFor();
                }
                else {
                    System.out.println(userinput + ": command not found");
                }
            }

        }
    }
}
