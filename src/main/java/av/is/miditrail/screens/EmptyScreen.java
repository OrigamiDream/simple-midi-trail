package av.is.miditrail.screens;

import avis.juikit.Juikit;

import java.awt.*;

public class EmptyScreen extends AbstractScreen {

    private static final String EMPTY_TEXT = "No MIDI";

    public EmptyScreen(Juikit juikit, ScreenManager screenManager) {
        super(juikit, screenManager);
    }

    @Override
    public void enterPage() {
        juikit.painter((uikit, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());

            int height = uikit.height();
            int width = uikit.width();

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
        juikit.painter((uikit, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());
        });
    }
}
