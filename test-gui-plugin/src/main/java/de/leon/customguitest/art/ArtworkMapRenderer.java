package de.leon.customguitest.art;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

public final class ArtworkMapRenderer extends MapRenderer {
    private final MapArtwork artwork;
    private boolean rendered;

    public ArtworkMapRenderer(MapArtwork artwork) {
        super(false);
        this.artwork = artwork;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) return;
        draw(canvas, artwork);
        rendered = true;
    }

    private void draw(MapCanvas c, MapArtwork type) {
        gradient(c, new Color(7, 12, 28), new Color(18, 25, 50));
        switch (type) {
            case PANEL -> drawPanel(c, false);
            case PANEL_VIOLET -> drawPanel(c, true);
            case EDGE -> drawEdge(c, false);
            case EDGE_VIOLET -> drawEdge(c, true);
            case ORNAMENT -> drawOrnament(c, false);
            case ORNAMENT_VIOLET -> drawOrnament(c, true);
            case TITLE_N -> drawTitle(c, "N");
            case TITLE_O -> drawTitle(c, "O");
            case TITLE_V -> drawTitle(c, "V");
            case TITLE_A -> drawTitle(c, "A");
            case TITLE_L -> drawTitle(c, "L");
            case TITLE_B -> drawTitle(c, "B");
            case TITLE_D -> drawTitle(c, "D");
            case TITLE_S -> drawTitle(c, "S");
            case TITLE_H -> drawTitle(c, "H");
            case TITLE_P -> drawTitle(c, "P");
            case TITLE_R -> drawTitle(c, "R");
            case TITLE_F -> drawTitle(c, "F");
            case TITLE_I -> drawTitle(c, "I");
            case TITLE_T -> drawTitle(c, "T");
            case TITLE_Y -> drawTitle(c, "Y");
            case TITLE_E -> drawTitle(c, "E");
            case TITLE_M -> drawTitle(c, "M");
            case PROFILE -> drawProfile(c);
            case DESIGN -> drawDesign(c);
            case THEME -> drawTheme(c);
            case INTERACTION -> drawInteraction(c);
            case STATUS -> drawStatus(c);
            case BACK -> drawBack(c);
            case CLOSE -> drawClose(c);
            case INFO -> drawInfo(c);
        }
        Color frame = switch (type) {
            case EDGE, ORNAMENT, TITLE_N, TITLE_O, TITLE_V, TITLE_A, PROFILE, STATUS, BACK ->
                    new Color(102, 222, 255);
            case EDGE_VIOLET, ORNAMENT_VIOLET, TITLE_L, TITLE_B, DESIGN, THEME ->
                    new Color(194, 146, 255);
            case CLOSE -> new Color(255, 118, 151);
            default -> new Color(85, 130, 190);
        };
        frame(c, frame);
    }

    private void drawPanel(MapCanvas c, boolean violet) {
        Color baseTop = violet ? new Color(23, 12, 40) : new Color(7, 16, 33);
        Color baseBottom = violet ? new Color(41, 20, 62) : new Color(12, 30, 48);
        gradient(c, baseTop, baseBottom);
        Color glow = violet ? new Color(75, 43, 109) : new Color(23, 67, 94);
        for (int i = 0; i < 8; i++) {
            line(c, 8 + i * 18, 0, 0, 8 + i * 18, glow);
            line(c, 127, 8 + i * 17, 8 + i * 17, 127, glow);
        }
        for (int y = 8; y < 120; y += 24)
            for (int x = 8; x < 120; x += 24)
                px(c, x, y, violet ? new Color(111, 76, 151) : new Color(60, 119, 146));
        line(c, 0, 126, 127, 126, glow);
    }

    private void drawEdge(MapCanvas c, boolean violet) {
        gradient(c, violet ? new Color(50, 24, 73) : new Color(13, 35, 60),
                violet ? new Color(90, 33, 103) : new Color(24, 71, 100));
        Color bright = violet ? new Color(218, 161, 255) : new Color(99, 205, 242);
        Color dark = violet ? new Color(106, 57, 139) : new Color(34, 72, 105);
        rect(c, 5, 5, 122, 122, dark, false);
        rect(c, 7, 7, 120, 120, bright, false);
        line(c, 10, 12, 117, 12, new Color(227, 239, 255));
        line(c, 10, 115, 117, 115, dark);
        circle(c, 16, 16, 3, new Color(230, 247, 255), true);
        circle(c, 112, 112, 3, bright, true);
    }

    private void drawOrnament(MapCanvas c, boolean violet) {
        radial(c, 64, 64, 58,
                violet ? new Color(52, 25, 81) : new Color(14, 28, 58),
                violet ? new Color(95, 37, 100) : new Color(52, 23, 75));
        Color ring = violet ? new Color(206, 151, 255) : new Color(92, 194, 231);
        circle(c, 64, 64, 43, new Color(43, 86, 132), false);
        circle(c, 64, 64, 37, ring, false);
        circle(c, 64, 64, 5, new Color(236, 226, 255), true);
        line(c, 64, 13, 64, 115, new Color(51, 87, 131));
        line(c, 13, 64, 115, 64, new Color(51, 87, 131));
        line(c, 28, 28, 100, 100, new Color(38, 70, 112));
        line(c, 100, 28, 28, 100, new Color(38, 70, 112));
    }

    private void drawTitle(MapCanvas c, String letter) {
        radial(c, 64, 64, 65, new Color(14, 46, 70), new Color(49, 24, 78));
        circle(c, 64, 64, 53, new Color(49, 111, 153), false);
        circle(c, 64, 64, 48, new Color(32, 57, 96), false);
        String[] rows = glyph(letter);
        int scale = 9;
        int width = 5 * scale, height = 7 * scale;
        int left = (128 - width) / 2, top = (128 - height) / 2;
        Color glow = new Color(47, 170, 219);
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 5; col++) {
                if (rows[row].charAt(col) != '1') continue;
                fill(c, left + col * scale - 2, top + row * scale - 2,
                        left + (col + 1) * scale + 1, top + (row + 1) * scale + 1, glow);
                fill(c, left + col * scale, top + row * scale,
                        left + (col + 1) * scale - 1, top + (row + 1) * scale - 1,
                        new Color(236, 247, 255));
            }
        }
        circle(c, 20, 20, 3, new Color(196, 166, 255), true);
        circle(c, 108, 108, 3, new Color(103, 229, 255), true);
    }

    private String[] glyph(String value) {
        return switch (value) {
            case "N" -> new String[]{"10001","11001","11001","10101","10011","10011","10001"};
            case "O" -> new String[]{"01110","10001","10001","10001","10001","10001","01110"};
            case "V" -> new String[]{"10001","10001","10001","10001","01010","01010","00100"};
            case "A" -> new String[]{"01110","10001","10001","11111","10001","10001","10001"};
            case "L" -> new String[]{"10000","10000","10000","10000","10000","10000","11111"};
            case "B" -> new String[]{"11110","10001","10001","11110","10001","10001","11110"};
            case "D" -> new String[]{"11110","10001","10001","10001","10001","10001","11110"};
            case "S" -> new String[]{"01111","10000","10000","01110","00001","00001","11110"};
            case "H" -> new String[]{"10001","10001","10001","11111","10001","10001","10001"};
            case "P" -> new String[]{"11110","10001","10001","11110","10000","10000","10000"};
            case "R" -> new String[]{"11110","10001","10001","11110","10100","10010","10001"};
            case "F" -> new String[]{"11111","10000","10000","11110","10000","10000","10000"};
            case "I" -> new String[]{"11111","00100","00100","00100","00100","00100","11111"};
            case "T" -> new String[]{"11111","00100","00100","00100","00100","00100","00100"};
            case "Y" -> new String[]{"10001","10001","01010","00100","00100","00100","00100"};
            case "E" -> new String[]{"11111","10000","10000","11110","10000","10000","11111"};
            case "M" -> new String[]{"10001","11011","10101","10101","10001","10001","10001"};
            default -> new String[]{"00000","00000","00000","00000","00000","00000","00000"};
        };
    }

    private void drawProfile(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(10, 44, 67), new Color(34, 29, 75));
        circle(c, 64, 47, 17, new Color(132, 226, 255), true);
        circle(c, 64, 47, 11, new Color(224, 247, 255), true);
        circle(c, 64, 47, 7, new Color(20, 55, 81), true);
        ellipse(c, 64, 104, 37, 25, new Color(106, 205, 243), true);
        ellipse(c, 64, 105, 27, 17, new Color(17, 42, 73), true);
        arcDots(c, 64, 64, 50, new Color(203, 172, 255));
    }

    private void drawDesign(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(27, 36, 75), new Color(55, 26, 76));
        rounded(c, 31, 24, 101, 105, 13, new Color(71, 120, 174), true);
        rounded(c, 26, 19, 96, 100, 13, new Color(184, 151, 255), true);
        rounded(c, 21, 14, 91, 95, 13, new Color(232, 241, 255), true);
        rounded(c, 27, 22, 85, 89, 8, new Color(24, 38, 71), true);
        line(c, 38, 39, 74, 39, new Color(114, 218, 255));
        line(c, 38, 51, 74, 51, new Color(114, 218, 255));
        line(c, 38, 63, 66, 63, new Color(194, 164, 255));
        line(c, 38, 75, 70, 75, new Color(194, 164, 255));
    }

    private void drawTheme(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(15, 43, 74), new Color(60, 25, 79));
        circle(c, 64, 64, 40, new Color(180, 215, 247), true);
        circle(c, 64, 64, 27, new Color(18, 32, 60), true);
        circle(c, 64, 34, 11, new Color(100, 224, 255), true);
        circle(c, 90, 54, 11, new Color(192, 149, 255), true);
        circle(c, 80, 84, 11, new Color(255, 145, 208), true);
        circle(c, 48, 84, 11, new Color(113, 237, 191), true);
        circle(c, 38, 54, 11, new Color(255, 213, 125), true);
        circle(c, 64, 64, 8, new Color(238, 247, 255), true);
    }

    private void drawInteraction(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(9, 46, 66), new Color(35, 29, 80));
        polygon(c, new int[][]{{72,17},{35,73},{57,73},{49,111},{93,51},{68,51}}, new Color(74,199,255));
        polygon(c, new int[][]{{69,35},{48,67},{63,67},{59,91},{81,55},{65,55}}, new Color(234,248,255));
        circle(c, 64, 64, 53, new Color(90,168,222), false);
    }

    private void drawStatus(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(7, 48, 54), new Color(23, 43, 80));
        circle(c, 64, 64, 42, new Color(63, 204, 171), false);
        line(c, 19, 68, 40, 68, new Color(143, 249, 209));
        line(c, 40, 68, 52, 43, new Color(143, 249, 209));
        line(c, 52, 43, 67, 85, new Color(143, 249, 209));
        line(c, 67, 85, 82, 57, new Color(143, 249, 209));
        line(c, 82, 57, 109, 57, new Color(143, 249, 209));
        circle(c, 52, 43, 4, new Color(232, 255, 250), true);
        circle(c, 67, 85, 4, new Color(232, 255, 250), true);
    }

    private void drawBack(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(9, 44, 68), new Color(35, 32, 81));
        line(c, 24, 64, 100, 64, new Color(123, 222, 255));
        line(c, 24, 64, 52, 36, new Color(123, 222, 255));
        line(c, 24, 64, 52, 92, new Color(123, 222, 255));
        line(c, 34, 64, 108, 64, new Color(229, 244, 255));
        circle(c, 64, 64, 49, new Color(90, 163, 207), false);
    }

    private void drawClose(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(65, 20, 43), new Color(38, 22, 67));
        circle(c, 64, 64, 43, new Color(255, 111, 138), false);
        line(c, 39, 39, 89, 89, new Color(255, 224, 232));
        line(c, 89, 39, 39, 89, new Color(255, 224, 232));
    }

    private void drawInfo(MapCanvas c) {
        radial(c, 64, 64, 61, new Color(23, 37, 78), new Color(49, 25, 79));
        circle(c, 64, 64, 43, new Color(173, 144, 244), false);
        circle(c, 64, 39, 6, new Color(230, 224, 255), true);
        rounded(c, 57, 52, 71, 96, 4, new Color(221, 241, 255), true);
        rounded(c, 54, 52, 68, 96, 4, new Color(104, 209, 246), true);
    }

    private void frame(MapCanvas c, Color color) {
        rect(c, 1, 1, 126, 126, color, false);
        rect(c, 3, 3, 124, 124, new Color(15, 26, 49), false);
    }

    private void gradient(MapCanvas c, Color top, Color bottom) {
        for (int y = 0; y < 128; y++) {
            double t = y / 127.0;
            Color row = mix(top, bottom, t);
            for (int x = 0; x < 128; x++) {
                int grain = ((x * 13 + y * 7) % 17 == 0) ? 4 : 0;
                px(c, x, y, new Color(clamp(row.getRed() + grain),
                        clamp(row.getGreen() + grain), clamp(row.getBlue() + grain)));
            }
        }
    }

    private void radial(MapCanvas c, int cx, int cy, int radius, Color center, Color outer) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius;
                px(c, x, y, mix(center, outer, Math.min(1, distance)));
            }
        }
    }

    private void fill(MapCanvas c, int x1, int y1, int x2, int y2, Color color) {
        for (int y = Math.max(0, y1); y <= Math.min(127, y2); y++)
            for (int x = Math.max(0, x1); x <= Math.min(127, x2); x++) px(c, x, y, color);
    }

    private void rect(MapCanvas c, int x1, int y1, int x2, int y2, Color color, boolean filled) {
        if (filled) { fill(c, x1, y1, x2, y2, color); return; }
        line(c, x1, y1, x2, y1, color);
        line(c, x2, y1, x2, y2, color);
        line(c, x2, y2, x1, y2, color);
        line(c, x1, y2, x1, y1, color);
    }

    private void rounded(MapCanvas c, int x1, int y1, int x2, int y2, int radius, Color color, boolean filled) {
        if (!filled) { rect(c, x1, y1, x2, y2, color, false); return; }
        fill(c, x1 + radius, y1, x2 - radius, y2, color);
        fill(c, x1, y1 + radius, x2, y2 - radius, color);
        circle(c, x1 + radius, y1 + radius, radius, color, true);
        circle(c, x2 - radius, y1 + radius, radius, color, true);
        circle(c, x1 + radius, y2 - radius, radius, color, true);
        circle(c, x2 - radius, y2 - radius, radius, color, true);
    }

    private void ellipse(MapCanvas c, int cx, int cy, int rx, int ry, Color color, boolean filled) {
        for (int y = -ry; y <= ry; y++) {
            for (int x = -rx; x <= rx; x++) {
                double d = (double) x * x / (rx * rx) + (double) y * y / (ry * ry);
                if (filled ? d <= 1 : d >= 0.88 && d <= 1.08) px(c, cx + x, cy + y, color);
            }
        }
    }

    private void circle(MapCanvas c, int cx, int cy, int radius, Color color, boolean filled) {
        int squared = radius * radius;
        for (int y = -radius; y <= radius; y++)
            for (int x = -radius; x <= radius; x++) {
                int distance = x * x + y * y;
                if (filled ? distance <= squared : distance <= squared && distance >= (radius - 2) * (radius - 2))
                    px(c, cx + x, cy + y, color);
            }
    }

    private void line(MapCanvas c, int x1, int y1, int x2, int y2, Color color) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            px(c, x1, y1, color);
            if (x1 == x2 && y1 == y2) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x1 += sx; }
            if (twice <= dx) { error += dx; y1 += sy; }
        }
    }

    private void polygon(MapCanvas c, int[][] points, Color color) {
        int minY = 127, maxY = 0;
        for (int[] point : points) { minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]); }
        for (int y = minY; y <= maxY; y++) {
            int[] intersections = new int[points.length];
            int count = 0;
            for (int i = 0; i < points.length; i++) {
                int[] a = points[i], b = points[(i + 1) % points.length];
                if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y))
                    intersections[count++] = a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
            }
            java.util.Arrays.sort(intersections, 0, count);
            for (int i = 0; i + 1 < count; i += 2)
                line(c, intersections[i], y, intersections[i + 1], y, color);
        }
    }

    private void arcDots(MapCanvas c, int cx, int cy, int radius, Color color) {
        for (int angle = 20; angle <= 340; angle += 40) {
            double radians = Math.toRadians(angle);
            circle(c, cx + (int) (Math.cos(radians) * radius),
                    cy + (int) (Math.sin(radians) * radius), 2, color, true);
        }
    }

    private void px(MapCanvas c, int x, int y, Color color) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) c.setPixelColor(x, y, color);
    }

    private Color mix(Color a, Color b, double t) {
        return new Color(clamp((int) (a.getRed() + (b.getRed() - a.getRed()) * t)),
                clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
                clamp((int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)));
    }

    private int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
