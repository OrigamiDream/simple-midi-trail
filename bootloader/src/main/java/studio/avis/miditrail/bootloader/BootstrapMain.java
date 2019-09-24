package studio.avis.miditrail.bootloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class BootstrapMain {

    public static void main(String[] args) throws IOException, InterruptedException {
        File file = new File("runtime");
        if(!file.exists()) {
            file.mkdirs();
        }

        File backend = new File(file, "backend.jar");
        if(!backend.exists()) {
            System.out.println("Extracting backend jar...");
            FileOutputStream fileOutputStream = new FileOutputStream(backend);
            InputStream inputStream = BootstrapMain.class.getResourceAsStream("/backend.jar");

            byte[] buffer = new byte[8192];
            int read;

            while((read = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, read);
            }
        }

        System.out.println("Launching backend");
        List<String> commands = new ArrayList<>(Arrays.asList("java", "-jar", "backend.jar"));

        // Dark Mode for macOS Mojave
        if(System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[] {"defaults", "read", "-g", "AppleInterfaceStyle"});
                Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\\A");
                String result = scanner.hasNext() ? scanner.next() : "";

                if(result.trim().equalsIgnoreCase("Dark")) {
                    commands.addAll(Arrays.asList("-NSRequiresAquaSystemAppearance", "False"));
                }
            } catch(Exception ignored) {
                // macOS High Sierra or under would cause exception because there is no system parameter 'AppleInterfaceStyle'
                // But, the happening does not tested
            }
        }
        Process process = new ProcessBuilder(commands).directory(file).start();
        int exitCode = process.waitFor();
        if(exitCode != 0) {
            System.out.println("Exit signal: " + exitCode);
        }
        System.exit(exitCode);
    }

}
