package studio.avis.miditrail.screens;

import studio.avis.juikit.Juikit;

import java.awt.*;

public class EmptyScreen extends AbstractScreen {

    private static final String EMPTY_TEXT = "No MIDI";

    public EmptyScreen(Juikit juikit, ScreenManager screenManager) {
        super(juikit, screenManager);
    }

    @Override
    public void enterPage() {
        juikit.painter((juikitView, g) -> {
            Graphics2D graphics = (Graphics2D) g;

            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikitView.width(), juikitView.height());

            int height = juikitView.height();
            int width = juikitView.width();

            graphics.setFont(new Font(graphics.getFont().getName(), Font.BOLD, 40));
            FontMetrics fontMetrics = graphics.getFontMetrics(graphics.getFont());

            int textWidth = fontMetrics.stringWidth(EMPTY_TEXT);
            int textHeight = fontMetrics.getHeight();

            graphics.setColor(Color.WHITE);
            graphics.drawString(EMPTY_TEXT, (width / 2) - (textWidth / 2), (height / 2) - (textHeight / 2));
        });
    }

    @Override
    public void leavePage() {
        juikit.painter((juikitView, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikitView.width(), juikitView.height());
        });
    }
}
