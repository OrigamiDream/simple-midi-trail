package studio.avis.miditrail.screens.views;

import studio.avis.juikit.internal.JuikitView;
import studio.avis.miditrail.TrackReader;
import studio.avis.miditrail.sheet.SheetRenderer;
import java.awt.*;
import static studio.avis.miditrail.MIDITrail.TICK;

/** Thin host adapter; engraving itself uses only standard Java2D. */
public class TrackSheetView extends TrackView {
    public static final int MEASURES_PER_PAGE = SheetRenderer.MEASURES_PER_PAGE;
    public static final double HEIGHT_FRACTION = .60;
    private final TrackReader reader;
    private volatile SheetRenderer renderer;

    public TrackSheetView(TrackReader reader) { this.reader = reader; }
    public void prepare() { renderer = new SheetRenderer(reader); }
    public int getMaximumHeight(JuikitView view) { return (int) (view.height() * HEIGHT_FRACTION); }
    @Override public void draw(JuikitView view, Graphics graphics) {
        if (renderer == null) renderer = new SheetRenderer(reader);
        renderer.paint((Graphics2D) graphics, view.width(), getMaximumHeight(view), view.data(TICK, Long.class));
    }
    public int getCurrentBarIndex(long tick) {
        if (renderer == null) renderer = new SheetRenderer(reader);
        return renderer.getScore().firstSoundingMeasure + renderer.pageAt(tick) * MEASURES_PER_PAGE + 1;
    }
}
