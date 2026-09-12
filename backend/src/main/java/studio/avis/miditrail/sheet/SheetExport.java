package studio.avis.miditrail.sheet;

import studio.avis.miditrail.TrackReader;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Headless export uses exactly the same renderer as playback. No synthesizer needed. */
public final class SheetExport {
    private SheetExport() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 5) {
            System.err.println("Usage: SheetExport input.mid output.png [tick] [width] [height]");
            System.exit(2);
        }
        long tick = args.length > 2 ? Long.parseLong(args[2]) : 0;
        int width = args.length > 3 ? Integer.parseInt(args[3]) : 1600;
        int height = args.length > 4 ? Integer.parseInt(args[4]) : 650;
        if (width < 1 || height < 1 || width > 8192 || height > 8192) throw new IllegalArgumentException("Image dimensions must be 1..8192");
        TrackReader reader = new TrackReader(new File(args[0])); reader.loadTrack();
        long start = System.nanoTime();
        SheetRenderer renderer = new SheetRenderer(reader);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        renderer.paint(g, width, height, tick);
        long built = System.nanoTime();
        for (int i = 0; i < 120; i++) renderer.paint(g, width, height, tick);
        long painted = System.nanoTime(); g.dispose();
        ImageIO.write(image, "png", new File(args[1]));
        System.out.printf("Layout + first frame: %.1f ms; cached frame average: %.2f ms; page builds: %d%n", (built-start)/1e6, (painted-built)/120e6, renderer.getBuildCount());
    }
}
