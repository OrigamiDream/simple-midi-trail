package studio.avis.miditrail.screens;

import studio.avis.juikit.Juikit;
import studio.avis.miditrail.MIDITrail;

import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class AbstractScreen {

    public static final Color[] LOADING_COLORS = { Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE };

    protected final Juikit juikit;
    protected final ScreenManager screenManager;

    public AbstractScreen(Juikit juikit, ScreenManager screenManager) {
        this.juikit = juikit;
        this.screenManager = screenManager;
    }

    public abstract void enterPage();

    public abstract void leavePage();

    public boolean isCommandPressed(KeyEvent event) {
        return juikit.macOS() && event.isMetaDown() || !juikit.macOS() && event.isControlDown();
    }

    public int textWidth(Graphics graphics, String text) {
        return graphics.getFontMetrics(graphics.getFont()).stringWidth(text);
    }

    public int textHeight(Graphics graphics) {
        return graphics.getFontMetrics(graphics.getFont()).getHeight();
    }

    public void startDimming() {
        juikit.data(MIDITrail.DONE, false);
    }

    public boolean isDimming() {
        return !juikit.data(MIDITrail.DONE, Boolean.class);
    }

    public boolean isDone() {
        return !isDimming();
    }

    public void displayLoading(Graphics graphics, boolean running, Runnable callback) {
        int centerX = juikit.width() / 2;
        int centerY = juikit.height() / 2;

        int loading = juikit.data(MIDITrail.LOADING);
        int opacity = juikit.data(MIDITrail.OPACITY);

        boolean run = juikit.data(MIDITrail.RUNNING);
        boolean done = false;

        if(running) {
            opacity = Math.min(255, opacity + 7);
            juikit.data(MIDITrail.RUNNING, true);
        } else {
            if(run) {
                callback.run();
                juikit.data(MIDITrail.RUNNING, false);
            }
            opacity = Math.max(0, opacity - 7);
            if(opacity == 0) {
                juikit.data(MIDITrail.DONE, true);
                loading = 0;
                done = true;
            }
        }

        boolean display = !done && running;

        graphics.setColor(new Color(128, 128, 128, opacity));
        graphics.fillRect(0, 0, juikit.width(), juikit.height());

        int height = 30;
        int flip = 30;

        int i = loading / flip;
        int yPixels = (int) ((loading % flip) * ((double) flip / (double) height));
        if(display) {
            Color prev = LOADING_COLORS[Math.max(0, i - 1) % LOADING_COLORS.length];
            graphics.setColor(prev);
            graphics.fillRect(centerX - 15, centerY - 15, 30, height);
        } else {
            yPixels = height;
        }

        Color color = LOADING_COLORS[i % LOADING_COLORS.length];
        Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), running ? 255 : opacity);
        graphics.setColor(newColor);
        graphics.fillRect(centerX - 15, centerY - 15, 30, Math.min(height, yPixels));

        i = loading / 50;
        int count = i % 3;
        graphics.setColor(new Color(255, 255, 255, run ? 255 : opacity));
        if(running) {
            StringBuilder builder = new StringBuilder();
            for(int j = 0; j < count; j++) {
                builder.append(".");
            }
            graphics.drawString("Loading" + builder.toString(), centerX - 25, centerY + 40);
        } else {
            graphics.drawString("Completed", centerX - 33, centerY + 40);
        }

        if(display) {
            loading++;
        }

        juikit.data(MIDITrail.LOADING, loading);
        juikit.data(MIDITrail.OPACITY, opacity);
    }

}
