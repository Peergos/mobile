import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.*;

public class BuildAndroid {

    public static void main(String[] a) throws Exception {

        // extract web assets
        String baseDir = "src/main/resources";
        new File(baseDir).mkdirs();
        runCommand("unzip -o lib/Peergos.jar webroot/* -d " + baseDir);

        // run native-image
        runCommand("mvn " +
                   "-Pandroid " +
                   "gluonfx:build " +
                   "gluonfx:package"
                   );
    }

    public static int runCommand(String command) throws Exception {
        System.out.println(command);
        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return pb.start().waitFor();
    }
}
