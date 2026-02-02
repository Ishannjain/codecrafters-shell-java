import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc=new Scanner(System.in);
        while(true){
            System.out.print("$ "); 
            String command=sc.nextLine();
            if(command.equals("exit")){
                break;
            }
            if(command.startsWith("echo ")){
                String substr=command.substring(5);
                System.out.println(substr);
                continue;
            }
            if(command.startsWith("type ")){
                String substr=command.substring(5);
                if(substr.equals("echo") || substr.equals("exit") || substr.equals("type")){
                    System.out.println(substr+" is a shell builtin");
                    continue;
                }
                else{
                    System.out.println(substr+": not found");
                    continue;
                }
            }
            System.out.println(command + ": command not found");
        }
    }
}
