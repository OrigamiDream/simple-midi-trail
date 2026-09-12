package studio.avis.miditrail.sheet;

import studio.avis.miditrail.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Java2D renderer shared by the live player and headless PNG export. Caches one page. */
public final class SheetRenderer {
    public static final int MEASURES_PER_PAGE = 4;
    private static final Color INK = new Color(24, 23, 22);
    private static final Color ACTIVE = new Color(206, 34, 47);
    private final TrackReader reader;
    private final Score score;
    private BufferedImage cached;
    private int page = -1, cachedWidth, cachedHeight;
    private double cachedDensity, scale, logicalWidth, treble, bass, pedalY;
    private final List<Bar> bars = new ArrayList<>();
    final List<Placed> placed = new ArrayList<>();
    private final List<Highlight> highlights = new ArrayList<>();
    private int buildCount;

    private static final class Bar {
        Score.Measure measure;
        double left, right, content;
        TreeMap<Long, Double> positions = new TreeMap<>();
        Map<Long, Double> signatureWidths = new TreeMap<>();
        double x(long tick) {
            Map.Entry<Long, Double> before = positions.floorEntry(tick), after = positions.ceilingEntry(tick);
            if (before == null) return content;
            if (after == null) return right - 10;
            if (before.getKey().equals(after.getKey())) return before.getValue();
            return before.getValue() + (after.getValue() - before.getValue()) *
                    (tick - before.getKey()) / (double) (after.getKey() - before.getKey());
        }
    }
    static final class Placed {
        Score.Chord chord; Bar bar;
        double x, low, high, stemX, stemEnd;
        boolean up, beamed;
        List<Shape> heads = new ArrayList<>();
        List<Double> headX = new ArrayList<>();
    }
    private static final class Highlight {
        Shape shape; long start, end;
        Highlight(Shape shape, long start, long end) { this.shape = shape; this.start = start; this.end = end; }
    }

    public SheetRenderer(TrackReader reader) { Symbols.initialize(); this.reader = reader; this.score = new Score(reader); }
    public Score getScore() { return score; }
    public int getBuildCount() { return buildCount; }
    public int pageAt(long tick) {
        return score.measures.isEmpty() ? 0 : Math.max(0, score.measureAt(tick) - score.firstSoundingMeasure) / MEASURES_PER_PAGE;
    }

    public void paint(Graphics2D graphics, int width, int height, long tick) {
        if (width <= 0 || height <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clipRect(0, 0, width, height);
            if (!score.metrical || score.measures.isEmpty()) {
                g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
                g.setColor(INK); g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
                g.drawString(score.metrical ? "No notation available" : "Sheet notation requires PPQ MIDI timing (this file uses SMPTE).", 24, 40);
                return;
            }
            double density = Math.max(1, Math.min(3, Math.abs(g.getTransform().getScaleX())));
            int nextPage = pageAt(tick);
            if (cached == null || page != nextPage || cachedWidth != width || cachedHeight != height || cachedDensity != density)
                build(nextPage, width, height, density);
            g.drawImage(cached, 0, 0, width, height, null);
            g.scale(scale, scale);
            quality(g);
            g.setColor(ACTIVE);
            for (Highlight highlight : highlights) if (highlight.start <= tick && tick < highlight.end) g.fill(highlight.shape);
            for (Bar bar : bars) {
                if (bar.measure.start <= tick && tick < bar.measure.end) {
                    double x = bar.x(tick);
                    g.setStroke(new BasicStroke(1.25f));
                    Symbols.line(g, x, 16, x, height / scale - 12);
                    break;
                }
            }
        } finally { g.dispose(); }
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f));
    }

    private void build(int page, int width, int height, double density) {
        this.page = page; cachedWidth = width; cachedHeight = height; cachedDensity = density; buildCount++;
        bars.clear(); placed.clear(); highlights.clear();
        int first = score.firstSoundingMeasure + page * MEASURES_PER_PAGE;
        int end = Math.min(first + MEASURES_PER_PAGE, score.measures.size());
        double[] min = {-64, -64}, max = {16, 32};
        for (int i = first; i < end; i++) for (Score.Chord chord : score.measures.get(i).chords) {
            for (Score.Head head : chord.heads) {
                double y = relativeY(head.pitch.step, chord.staff);
                min[chord.staff] = Math.min(min[chord.staff], y - (chord.staff == 0 ? 60 : 38));
                max[chord.staff] = Math.max(max[chord.staff], y + (chord.staff == 0 ? 8 : 60));
            }
        }
        treble = 70 - min[0];
        bass = treble + max[0] - min[1] + 12;
        pedalY = bass + max[1] + 23;
        double logicalHeight = bass + max[1] + 58;
        scale = Math.min(height / logicalHeight, width / 1000.0);
        scale = Math.min(1.5, scale);
        logicalWidth = width / scale;
        double header = 65 + keyWidth(null, score.measures.get(first).key) + 34;
        double available = logicalWidth - header - 26;
        double[] weights = new double[end - first];
        double sum = 0;
        for (int i = first; i < end; i++) {
            Set<Long> ticks = new HashSet<>();
            for (Score.Chord c : score.measures.get(i).chords) ticks.add(c.start);
            weights[i - first] = Math.max(8, Math.sqrt(ticks.size() + 1) * 4);
            sum += weights[i - first];
        }
        double x = header;
        double minimumBarWidth = Math.min(190, available / (end - first));
        double flexibleWidth = Math.max(0, available - minimumBarWidth * (end - first));
        for (int i = first; i < end; i++) {
            Bar bar = new Bar(); bar.measure = score.measures.get(i); bar.left = x;
            bar.right = x + minimumBarWidth + flexibleWidth * weights[i - first] / sum; x = bar.right;
            layoutBar(bar, i == first);
            bars.add(bar);
        }
        cached = new BufferedImage((int) Math.ceil(width * density), (int) Math.ceil(height * density), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cached.createGraphics();
        try {
            g.setColor(new Color(252, 251, 247)); g.fillRect(0, 0, cached.getWidth(), cached.getHeight());
            g.scale(density * scale, density * scale); quality(g); g.setColor(INK);
            for (int staff = 0; staff < 2; staff++) {
                double bottom = bottom(staff);
                g.setStroke(new BasicStroke(.65f));
                for (int j = 0; j < 5; j++) Symbols.line(g, 28, bottom - j * 8, logicalWidth - 20, bottom - j * 8);
                g.setStroke(new BasicStroke(1f));
                Symbols.clef(g, 35, bottom, staff);
                double keyEnd = drawKey(g, null, score.measures.get(first).key, 67, staff);
                drawMeter(g, score.measures.get(first).meter, keyEnd + 7, staff);
            }
            Symbols.brace(g, 19, treble - 32, bass);
            Symbols.line(g, 28, treble - 32, 28, bass);
            for (Bar bar : bars) drawBar(g, bar, bar == bars.get(0));
            placeChords();
            drawBeams(g);
            placeHeads();
            for (Placed p : placed) drawChord(g, p);
            drawAccidentals(g);
            drawTies(g);
            drawMarks(g);
        } finally { g.dispose(); }
    }

    private void layoutBar(Bar bar, boolean first) {
        Score.Measure m = bar.measure;
        if (!first && m.index > 0) {
            Score.Measure previous = score.measures.get(m.index - 1);
            double reserve = 0;
            if (previous.key.getSignature() != m.key.getSignature()) reserve += keyWidth(previous.key, m.key) + 6;
            if (previous.meter.getNumerator() != m.meter.getNumerator() || previous.meter.getDenominator() != m.meter.getDenominator()) reserve += 30;
            if (reserve > 0) bar.signatureWidths.put(m.start, reserve);
        }
        for (KeySignature key : reader.getKeySignatures()) if (key.getTick() > m.start && key.getTick() < m.end)
            bar.signatureWidths.put(key.getTick(), keyWidth(reader.findKeySignatureBefore(key.getTick() - 1), key) + 6);
        TreeMap<Long, Double> slots = new TreeMap<>();
        slots.put(m.start, 18.0);
        for (Score.Chord chord : m.chords) {
            int accidentals = 0;
            for (Score.Head head : chord.heads) if (head.accidental != Score.NONE) accidentals++;
            double weight = 14 + Math.min(45, accidentals * 5) + chord.rhythm.dots * 4;
            slots.merge(chord.start, weight, Math::max);
        }
        for (Long change : bar.signatureWidths.keySet()) slots.putIfAbsent(change, 18.0);
        slots.put(m.end, 14.0);
        double total = 0;
        long last = m.start;
        for (Map.Entry<Long, Double> slot : slots.entrySet()) {
            double gap = Math.sqrt((slot.getKey() - last) / (double) score.resolution) * 24;
            slot.setValue(slot.getValue() + gap); total += slot.getValue(); last = slot.getKey();
        }
        double fixed = 0;
        for (double reserved : bar.signatureWidths.values()) fixed += reserved;
        double factor = Math.max(1, bar.right - bar.left - 12 - fixed) / total;
        double x = bar.left;
        for (Map.Entry<Long, Double> slot : slots.entrySet()) {
            x += bar.signatureWidths.getOrDefault(slot.getKey(), 0.0) + slot.getValue() * factor;
            bar.positions.put(slot.getKey(), x);
        }
        bar.content = bar.positions.firstEntry().getValue();
    }

    private static double relativeY(int step, int staff) { return -(step - (staff == 0 ? 30 : 18)) * 4.0; }
    private double bottom(int staff) { return staff == 0 ? treble : bass; }
    private double y(Score.Head head, int staff) { return bottom(staff) + relativeY(head.pitch.step, staff); }

    private void placeChords() {
        for (Bar bar : bars) {
            for (Score.Chord chord : bar.measure.chords) {
                Placed p = new Placed(); p.bar = bar; p.chord = chord;
                p.x = chord.wholeRest ? (bar.content + bar.right) / 2 : bar.x(chord.start);
                p.x += Math.min(18, chord.voice * 3);
                if (!chord.rest) {
                    p.high = y(chord.heads.get(chord.heads.size() - 1), chord.staff);
                    p.low = y(chord.heads.get(0), chord.staff);
                    p.up = chord.staff == 0 || (p.high + p.low) / 2 >= bottom(chord.staff) - 16;
                    p.stemX = p.x + (p.up ? 4.5 : -4.5);
                    p.stemEnd = p.up ? p.high - stemLength(chord.rhythm) : p.low + stemLength(chord.rhythm);
                }
                placed.add(p);
            }
        }
    }

    private void placeHeads() {
        for (Placed p : placed) {
            boolean shifted = false; int previous = Integer.MIN_VALUE;
            for (Score.Head head : p.chord.heads) {
                if (previous != Integer.MIN_VALUE && head.pitch.step - previous <= 1) shifted = !shifted; else shifted = false;
                previous = head.pitch.step;
                double hx = p.x + (shifted ? (p.up ? 9 : -9) : 0);
                Shape shape = Symbols.head(hx, y(head, p.chord.staff), p.chord.rhythm.denominator);
                p.heads.add(shape); p.headX.add(hx);
                highlights.add(new Highlight(shape, p.chord.start, head.tieOut ? Math.min(p.chord.end, head.source.getEndTick()) : head.source.getEndTick()));
            }
        }
    }

    private void drawChord(Graphics2D g, Placed p) {
        Score.Chord c = p.chord;
        g.setColor(INK); g.setStroke(new BasicStroke(1.1f));
        if (c.rest) {
            Symbols.rest(g, p.x, bottom(c.staff) - 16, c.rhythm, c.wholeRest);
            if (c.rhythm.tuplet > 0 && !c.wholeRest) tuplet(g, p.x - 6, p.x + 8, bottom(c.staff) - 44, c.rhythm.tuplet, true);
            return;
        }
        for (int i = 0; i < c.heads.size(); i++) {
            Score.Head head = c.heads.get(i);
            double hy = y(head, c.staff), hx = p.headX.get(i);
            double top = bottom(c.staff) - 32;
            for (double ly = top - 8; ly >= hy - .1; ly -= 8) Symbols.line(g, hx - 8, ly, hx + 8, ly);
            for (double ly = bottom(c.staff) + 8; ly <= hy + .1; ly += 8) Symbols.line(g, hx - 8, ly, hx + 8, ly);
            g.fill(p.heads.get(i));
            double dy = hy;
            if (head.pitch.step % 2 == 0) dy -= 4;
            for (int dot = 0; dot < c.rhythm.dots; dot++) Symbols.dot(g, p.x + 15 + dot * 4, dy);
        }
        if (c.rhythm.denominator > 1) {
            Symbols.line(g, p.stemX, p.up ? p.low : p.high, p.stemX, p.stemEnd);
            if (!p.beamed) Symbols.flag(g, p.stemX, p.stemEnd, p.up, c.rhythm.beams());
        }
        if (!p.beamed && c.rhythm.tuplet > 0) tuplet(g, p.x - 7, p.x + 12, p.up ? p.stemEnd - 9 : p.stemEnd + 14, c.rhythm.tuplet, true);
    }

    private void drawAccidentals(Graphics2D g) {
        Map<String, List<List<Double>>> columns = new HashMap<>();
        for (Placed p : placed) for (int i = p.chord.heads.size() - 1; i >= 0; i--) {
            Score.Head head = p.chord.heads.get(i);
            if (head.accidental == Score.NONE) continue;
            double hy = y(head, p.chord.staff);
            String id = p.chord.staff + ":" + p.chord.start;
            List<List<Double>> used = columns.computeIfAbsent(id, k -> new ArrayList<>());
            int column = 0;
            // Each column holds a list of y coordinates; use a conservative vertical separation.
            while (column < used.size()) {
                boolean collision = false;
                for (double occupied : used.get(column)) if (Math.abs(occupied - hy) < 24) collision = true;
                if (!collision) break;
                column++;
            }
            if (column == used.size()) used.add(new ArrayList<>());
            used.get(column).add(hy);
            Symbols.accidental(g, p.bar.x(p.chord.start) - 12 - column * 9, hy, head.accidental);
        }
    }

    private void drawBeams(Graphics2D g) {
        Map<String, List<Placed>> voices = new LinkedHashMap<>();
        for (Placed p : placed) {
            String id = p.bar.measure.index + ":" + p.chord.staff + ":" + p.chord.voice;
            voices.computeIfAbsent(id, k -> new ArrayList<>()).add(p);
        }
        for (List<Placed> voice : voices.values()) {
            List<Placed> group = new ArrayList<>();
            Placed previous = null;
            for (Placed p : voice) {
                long beat = p.bar.measure.beatTicks(score.resolution);
                boolean join = previous != null && !p.chord.rest && p.chord.rhythm.beams() > 0 &&
                        (p.chord.start - p.bar.measure.start) / beat == (previous.chord.start - p.bar.measure.start) / beat &&
                        p.chord.start <= previous.chord.end + Math.max(1, score.resolution / 32) &&
                        p.chord.rhythm.tuplet == previous.chord.rhythm.tuplet;
                if (join && p.chord.rhythm.tuplet > 0) {
                    int normal = p.chord.rhythm.tuplet == 3 ? 2 : 4;
                    double span = score.resolution * 4.0 / p.chord.rhythm.denominator * normal;
                    join = Math.floor((p.chord.start - p.bar.measure.start) / span + 1e-8) ==
                            Math.floor((previous.chord.start - p.bar.measure.start) / span + 1e-8);
                }
                if (!join) { beamGroup(g, group); group.clear(); }
                if (!p.chord.rest && p.chord.rhythm.beams() > 0) { group.add(p); previous = p; }
                else previous = null;
            }
            beamGroup(g, group);
        }
    }

    private void beamGroup(Graphics2D g, List<Placed> group) {
        if (group.size() < 2) return;
        Placed first = group.get(0), last = group.get(group.size() - 1);
        boolean up = first.chord.staff == 0;
        for (Placed p : group) {
            p.up = up;
            p.stemX = p.x + (up ? 4.5 : -4.5);
            p.stemEnd = up ? p.high - stemLength(p.chord.rhythm) : p.low + stemLength(p.chord.rhythm);
        }
        double slope = Math.max(-.15, Math.min(.15, (last.stemEnd - first.stemEnd) / Math.max(1, last.stemX - first.stemX)));
        double intercept = first.stemEnd;
        for (Placed p : group) {
            double target = up ? p.high - stemLength(p.chord.rhythm) : p.low + stemLength(p.chord.rhythm);
            double required = target - slope * (p.stemX - first.stemX);
            intercept = up ? Math.min(intercept, required) : Math.max(intercept, required);
        }
        for (Placed p : group) { p.beamed = true; p.up = up; p.stemEnd = intercept + slope * (p.stemX - first.stemX); }
        int max = 0;
        for (Placed p : group) max = Math.max(max, p.chord.rhythm.beams());
        for (int level = 0; level < max; level++) {
            for (int i = 0; i < group.size(); i++) {
                Placed a = group.get(i);
                if (a.chord.rhythm.beams() <= level) continue;
                boolean left = i > 0 && group.get(i - 1).chord.rhythm.beams() > level;
                boolean right = i + 1 < group.size() && group.get(i + 1).chord.rhythm.beams() > level;
                double toX;
                if (right) toX = group.get(i + 1).stemX;
                else if (!left) {
                    double gap = i + 1 < group.size() ? group.get(i + 1).stemX - a.stemX : a.stemX - group.get(i - 1).stemX;
                    toX = a.stemX + (i + 1 < group.size() ? 1 : -1) * Math.min(8, gap / 2);
                } else continue;
                double offset = (up ? 1 : -1) * level * 5;
                double fromY = a.stemEnd + offset, toY = fromY + slope * (toX - a.stemX);
                Path2D beam = new Path2D.Double(); beam.moveTo(a.stemX, fromY); beam.lineTo(toX, toY);
                beam.lineTo(toX, toY + (up ? 3.2 : -3.2)); beam.lineTo(a.stemX, fromY + (up ? 3.2 : -3.2)); beam.closePath(); g.fill(beam);
            }
        }
        if (first.chord.rhythm.tuplet > 0) {
            // Label each actual n:normal duration; a partial group must not imply a complete tuplet.
            int n = first.chord.rhythm.tuplet;
            double sum = 0;
            for (Placed p : group) sum += p.chord.rhythm.ticks;
            double unit = score.resolution * 4.0 / first.chord.rhythm.denominator * (n == 3 ? 2 : 4);
            boolean complete = Math.abs(sum - unit) <= 2 && group.size() == n && first.chord.rhythm.dots == 0;
            if (complete) tuplet(g, first.stemX, last.stemX, up ? Math.min(first.stemEnd, last.stemEnd) - 9 : Math.max(first.stemEnd, last.stemEnd) + 16, n, false);
            else for (Placed p : group) tuplet(g, p.x - 5, p.x + 9, up ? p.stemEnd - 9 : p.stemEnd + 16, n, true);
        }
    }

    private static double stemLength(Score.Rhythm rhythm) { return 30 + Math.max(0, rhythm.beams() - 1) * 5; }

    private void tuplet(Graphics2D g, double from, double to, double y, int n, boolean ratio) {
        String text = ratio ? n + ":" + (n == 3 ? 2 : 4) : Integer.toString(n);
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 11));
        int w = g.getFontMetrics().stringWidth(text);
        double mid = (from + to) / 2;
        g.drawString(text, (float) (mid - w / 2.0), (float) y);
        if (to - from > w + 16) {
            Symbols.line(g, from, y - 3, mid - w / 2.0 - 3, y - 3);
            Symbols.line(g, mid + w / 2.0 + 3, y - 3, to, y - 3);
        }
    }

    private void drawTies(Graphics2D g) {
        Map<Note, List<Placed>> occurrences = new IdentityHashMap<>();
        for (Placed p : placed) for (Score.Head head : p.chord.heads) occurrences.computeIfAbsent(head.source, k -> new ArrayList<>()).add(p);
        for (Map.Entry<Note, List<Placed>> entry : occurrences.entrySet()) {
            List<Placed> notes = entry.getValue();
            for (int i = 0; i < notes.size(); i++) {
                Placed p = notes.get(i);
                Score.Head head = null; double hx = p.x;
                for (int j = 0; j < p.chord.heads.size(); j++) if (p.chord.heads.get(j).source == entry.getKey()) { head = p.chord.heads.get(j); hx = p.headX.get(j); break; }
                if (head == null) continue;
                double hy = y(head, p.chord.staff) + (p.up ? 5 : -5);
                if (i == 0 && head.tieIn) Symbols.tie(g, p.bar.left + 1, hy, hx - 5, hy, !p.up);
                if (head.tieOut) {
                    if (i + 1 < notes.size()) {
                        Placed next = notes.get(i + 1);
                        Symbols.tie(g, hx + 5, hy, next.x - 5, hy, !p.up);
                    } else Symbols.tie(g, hx + 5, hy, p.bar.right - 2, hy, !p.up);
                }
            }
        }
    }

    private void drawBar(Graphics2D g, Bar bar, boolean first) {
        Score.Measure m = bar.measure;
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        g.drawString(Integer.toString(m.index - score.firstSoundingMeasure + 1), (float) (bar.left + 2), 20);
        if (!first) {
            Symbols.line(g, bar.left, treble - 32, bar.left, bass);
            Score.Measure before = score.measures.get(m.index - 1);
            if (bar.signatureWidths.containsKey(m.start)) for (int staff = 0; staff < 2; staff++) {
                double x = bar.left + 6;
                if (before.key.getSignature() != m.key.getSignature()) x = drawKey(g, before.key, m.key, x, staff) + 5;
                if (before.meter.getNumerator() != m.meter.getNumerator() || before.meter.getDenominator() != m.meter.getDenominator()) drawMeter(g, m.meter, x, staff);
            }
        }
        for (KeySignature key : reader.getKeySignatures()) if (key.getTick() > m.start && key.getTick() < m.end) {
            for (int staff = 0; staff < 2; staff++) drawKey(g, reader.findKeySignatureBefore(key.getTick() - 1), key, bar.x(key.getTick()) - bar.signatureWidths.get(key.getTick()), staff);
        }
        if (bar == bars.get(bars.size() - 1)) {
            Symbols.line(g, bar.right, treble - 32, bar.right, bass);
            if (m.index == score.measures.size() - 1) {
                Symbols.line(g, bar.right - 5, treble - 32, bar.right - 5, bass);
                g.fill(new Rectangle2D.Double(bar.right - 1, treble - 32, 3, bass - treble + 32));
            }
        }
    }
    private static double keyWidth(KeySignature before, KeySignature after) {
        int old = before == null ? 0 : before.getSharps() + before.getFlats();
        boolean cancel = before != null && before.getSignature() != after.getSignature();
        return (after.getSharps() + after.getFlats() + (cancel ? old : 0)) * 8;
    }
    private double drawKey(Graphics2D g, KeySignature before, KeySignature after, double x, int staff) {
        int[] sharpSteps = {38, 35, 39, 36, 33, 37, 34};
        int[] flatSteps = {34, 37, 33, 36, 32, 35, 31};
        int shift = staff == 0 ? 0 : -14;
        if (before != null && before.getSignature() != after.getSignature()) {
            int[] positions = before.getSharps() > 0 ? sharpSteps : flatSteps;
            int count = before.getSharps() + before.getFlats();
            for (int i = 0; i < count; i++) { Symbols.accidental(g, x, bottom(staff) + relativeY(positions[i] + shift, staff), 0); x += 8; }
        }
        int[] positions = after.getSharps() > 0 ? sharpSteps : flatSteps;
        int count = after.getSharps() + after.getFlats();
        for (int i = 0; i < count; i++) { Symbols.accidental(g, x, bottom(staff) + relativeY(positions[i] + shift, staff), after.getSharps() > 0 ? 1 : -1); x += 8; }
        return x;
    }
    private void drawMeter(Graphics2D g, TimeSignature meter, double x, int staff) {
        double topWidth = Symbols.numberWidth(meter.getNumerator()), bottomWidth = Symbols.numberWidth(meter.getDenominator());
        double width = Math.max(topWidth, bottomWidth);
        Symbols.number(g, meter.getNumerator(), x + (width - topWidth) / 2, bottom(staff) - 24);
        Symbols.number(g, meter.getDenominator(), x + (width - bottomWidth) / 2, bottom(staff) - 8);
    }
    private void drawMarks(Graphics2D g) {
        long start = bars.get(0).measure.start, end = bars.get(bars.size() - 1).measure.end;
        Score.Mark currentTempo = null;
        for (Score.Mark mark : score.marks) if (mark.tempo && mark.tick <= start) currentTempo = mark;
        double previousEnd = -100;
        int lane = 0;
        List<Score.Mark> visible = new ArrayList<>();
        if (currentTempo != null) visible.add(currentTempo);
        for (Score.Mark mark : score.marks) if (mark.tick >= start && mark.tick < end && mark != currentTempo) visible.add(mark);
        for (Score.Mark mark : visible) {
            double x = xAt(Math.max(start, mark.tick));
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 11));
            lane = x < previousEnd + 8 ? (lane + 1) % 3 : 0;
            double y = 44 + lane * 13;
            if (mark.tempo) {
                g.fill(Symbols.head(x + 3, y - 2, 4)); Symbols.line(g, x + 7, y - 2, x + 7, y - 18); x += 15;
            }
            String text = mark.text.length() > 70 ? mark.text.substring(0, 67) + "..." : mark.text;
            g.drawString(text, (float) x, (float) y);
            previousEnd = x + g.getFontMetrics().stringWidth(text);
        }
        double py = pedalY;
        for (Score.Pedal pedal : score.pedals) if (pedal.start < end && pedal.end > start) {
            double x1 = xAt(Math.max(start, pedal.start)), x2 = xAt(Math.min(end, pedal.end));
            Symbols.line(g, x1, py, x2, py);
            if (pedal.start >= start) { Symbols.line(g, x1, py, x1, py - 7); g.setFont(new Font(Font.SERIF, Font.ITALIC, 11)); g.drawString("Ped.", (float) x1, (float) (py - 9)); }
            if (pedal.end <= end) Symbols.line(g, x2, py, x2, py - 7);
        }
    }
    private double xAt(long tick) {
        for (Bar bar : bars) if (tick >= bar.measure.start && tick < bar.measure.end) return bar.x(tick);
        return tick < bars.get(0).measure.start ? bars.get(0).content : bars.get(bars.size() - 1).right - 8;
    }
}
