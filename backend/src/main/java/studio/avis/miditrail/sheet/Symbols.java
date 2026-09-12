package studio.avis.miditrail.sheet;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Classical engraving glyphs extracted once, then drawn exclusively as cached Java2D paths. */
final class Symbols {
    private Symbols() {}
    private static final Map<Integer, Glyph> GLYPHS = loadGlyphs();
    private static final class Glyph {
        final Shape outline;
        final Rectangle2D bounds;
        final double advance;
        Glyph(GlyphVector vector) {
            outline = new Path2D.Double(vector.getOutline());
            bounds = outline.getBounds2D();
            advance = vector.getGlyphMetrics(0).getAdvanceX();
        }
    }
    private static Map<Integer, Glyph> loadGlyphs() {
        Map<Integer, Glyph> result = new HashMap<>();
        try (InputStream input = Symbols.class.getResourceAsStream("/Bravura.otf")) {
            if (input == null) throw new IllegalStateException("Bundled Bravura.otf is missing");
            Font font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(32f);
            FontRenderContext context = new FontRenderContext(null, true, true);
            int[] singles = {0xe000, 0xe050, 0xe062, 0xe0a2, 0xe0a3, 0xe0a4, 0xe260, 0xe261, 0xe262};
            for (int code : singles) addGlyph(result, font, context, code);
            for (int code = 0xe080; code <= 0xe089; code++) addGlyph(result, font, context, code);
            for (int code = 0xe240; code <= 0xe24b; code++) addGlyph(result, font, context, code);
            for (int code = 0xe4e3; code <= 0xe4eb; code++) addGlyph(result, font, context, code);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load classical notation outlines", e);
        }
        return result;
    }
    private static void addGlyph(Map<Integer, Glyph> result, Font font, FontRenderContext context, int code) {
        if (!font.canDisplay(code)) throw new IllegalStateException("Missing notation glyph " + Integer.toHexString(code));
        result.put(code, new Glyph(font.createGlyphVector(context, new char[]{(char) code})));
    }
    // Called on the score-loading thread. No Font/GlyphVector operations occur during painting.
    static void initialize() { if (GLYPHS.isEmpty()) throw new IllegalStateException("No notation outlines"); }
    private static Shape translate(Shape shape, double x, double y) {
        return AffineTransform.getTranslateInstance(x, y).createTransformedShape(shape);
    }
    private static void centered(Graphics2D g, int code, double x, double y) {
        Glyph glyph = GLYPHS.get(code);
        g.fill(translate(glyph.outline, x - glyph.bounds.getCenterX(), y));
    }
    static Shape head(double x, double y, int denominator) {
        Glyph glyph = GLYPHS.get(denominator == 1 ? 0xe0a2 : denominator == 2 ? 0xe0a3 : 0xe0a4);
        return translate(glyph.outline, x - glyph.bounds.getCenterX(), y);
    }
    static void line(Graphics2D g, double x1, double y1, double x2, double y2) { g.draw(new Line2D.Double(x1, y1, x2, y2)); }
    static void dot(Graphics2D g, double x, double y) { g.fill(new Ellipse2D.Double(x - 1.4, y - 1.4, 2.8, 2.8)); }
    static void accidental(Graphics2D g, double x, double y, int alteration) {
        centered(g, alteration == 1 ? 0xe262 : alteration == -1 ? 0xe260 : 0xe261, x, y);
    }
    static void flag(Graphics2D g, double x, double y, boolean up, int beams) {
        if (beams == 0) return;
        Glyph glyph = GLYPHS.get(0xe240 + (beams - 1) * 2 + (up ? 0 : 1));
        double anchor = up ? glyph.bounds.getMinY() : glyph.bounds.getMaxY();
        g.fill(translate(glyph.outline, x, y - anchor));
    }
    static void rest(Graphics2D g, double x, double middle, Score.Rhythm rhythm, boolean whole) {
        int d = whole ? 1 : rhythm.denominator;
        centered(g, 0xe4e3 + Integer.numberOfTrailingZeros(d), x, middle - (d == 1 ? 8 : 0));
        if (!whole) for (int i = 0; i < rhythm.dots; i++) dot(g, x + 10 + i * 4, middle - 4);
    }
    static double numberWidth(int value) {
        double width = 0;
        for (char digit : Integer.toString(value).toCharArray()) width += GLYPHS.get(0xe080 + digit - '0').advance;
        return width;
    }
    static void number(Graphics2D g, int value, double x, double y) {
        for (char digit : Integer.toString(value).toCharArray()) {
            Glyph glyph = GLYPHS.get(0xe080 + digit - '0');
            g.fill(translate(glyph.outline, x, y)); x += glyph.advance;
        }
    }
    static void tie(Graphics2D g, double x1, double y1, double x2, double y2, boolean above) {
        if (x2 <= x1) return;
        double sign = above ? -1 : 1;
        double bend = sign * Math.min(12, 4 + (x2 - x1) * .12);
        Path2D p = new Path2D.Double(); p.moveTo(x1, y1);
        p.curveTo(x1 + (x2-x1)*.25, y1+bend, x1+(x2-x1)*.75, y2+bend, x2, y2);
        p.curveTo(x1+(x2-x1)*.75, y2+bend-sign*1.8, x1+(x2-x1)*.25, y1+bend-sign*1.8, x1, y1);
        g.fill(p);
    }
    static void brace(Graphics2D g, double x, double top, double bottom) {
        Glyph glyph = GLYPHS.get(0xe000);
        double sx = 12 / glyph.bounds.getWidth(), sy = (bottom - top) / glyph.bounds.getHeight();
        AffineTransform transform = new AffineTransform();
        transform.translate(x - 8, top); transform.scale(sx, sy);
        transform.translate(-glyph.bounds.getX(), -glyph.bounds.getY());
        g.fill(transform.createTransformedShape(glyph.outline));
    }
    static void clef(Graphics2D g, double x, double bottom, int staff) {
        Glyph glyph = GLYPHS.get(staff == 0 ? 0xe050 : 0xe062);
        g.fill(translate(glyph.outline, x, bottom - (staff == 0 ? 8 : 24)));
    }
}
