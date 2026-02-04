import java.util.*;
import java.io.File;

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
            String words[] = userinput.split(" ");

            // First word is the command
            String command = words[0];

            // Remaining words are arguments
            String remainwords[] = Arrays.copyOfRange(words, 1, words.length);

            // Join arguments for echo/type
            String result = String.join(" ", remainwords);
           
            // Built-in: exit
            if(command.equals("exit")){
                flag = false;
            }

            // Built-in: echo
            else if(command.equals("echo")){
                System.out.println(result);
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
           else if(command.equals("cd")){
                if(words.length < 2 || words[1].equals("~")){
                    commanddir = System.getenv("HOME");
                } else {
                    if(words[1].startsWith(("~"+File.separator))){
                        words[1]=System.getenv("HOME")+words[1].substring(1);
                    }
                    File dir = new File(words[1]);
                    if(!dir.isAbsolute()){
                         dir = new File(commanddir, words[1]);
                    }

                 if(dir.exists() && dir.isDirectory()){
                     commanddir = dir.getCanonicalPath();
                    } else {
                  System.out.println("cd: " + words[1] + ": No such file or directory");
                 }
                 }
                }

            // External command execution
            else {
                File exe = findExecutable(command);

                // If executable exists
                if(exe != null){

                    // Build command with arguments
                    List<String> cmd = new ArrayList<>();
                    cmd.add(command);
                    cmd.addAll(Arrays.asList(remainwords));

                    // Create process
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(new File(commanddir));
                    // Use same input/output as shell
                    pb.inheritIO();

                    // Run process and wait until it finishes
                    pb.start().waitFor();
                }
                else {
                    // Command not found
                    System.out.println(userinput + ": command not found");
                }
            }
        }
    }
}
