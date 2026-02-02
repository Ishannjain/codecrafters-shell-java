import java.util.Scanner;
import java.util.*;
import java.io.File;
public class Main {
    private static String CheckType(String result){
        String types[]={"exit","echo","type"};
        String path=System.getenv("PATH");
        String path_arr[]=path.split(File.pathSeparator);
        for(int i=0;i<types.length;i++){
            if(result.equals(types[i])){
                return result+ " is a shell builtin";
            }
        }
        for(int i=0;i<path_arr.length;i++){
            File files=new File(path_arr[i],result);
            if(files.exists() && files.canExecute()){
                return  result+" is "+ files.getAbsolutePath();
            }
        }
        return result+": not found";
    }
    public static void main(String[] args) throws Exception {
        Scanner sc=new Scanner(System.in);
        boolean flag=true;
        while(flag){
            System.out.print("$ "); 
            String userinput=sc.nextLine();
            String words[]=userinput.split(" ");
            String command=words[0];
            String remainwords[]=Arrays.copyOfRange(words,1,words.length);
            String result=String.join(" ",remainwords);
        
            if(command.equals("exit")){
                flag=false;
            }
            else if(command.equals("echo")){
                System.out.println(result);
            }
            else if(command.equals("type")){
                System.out.println(CheckType(result));
            }
            else {
            System.out.println(userinput+ ": command not found");
            }
        }
        
    }
}
