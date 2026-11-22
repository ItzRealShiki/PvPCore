package de.leon.customguitest.art;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.entity.Player;

import java.awt.Color;

/**
 * Draws a fully custom 128x128 pixel graphic into a vanilla filled-map item.
 * This is sent by the server as map data, so no client resource pack is required.
 */
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
            case PANEL -> drawPanel(c);
            case EDGE -> drawEdge(c);
            case ORNAMENT -> drawOrnament(c);
            case TITLE_N -> drawTitle(c, "N");
            case TITLE_O -> drawTitle(c, "O");
            case TITLE_V -> drawTitle(c, "V");
            case TITLE_A -> drawTitle(c, "A");
            case TITLE_L -> drawTitle(c, "L");
            case TITLE_B -> drawTitle(c, "B");
            case PROFILE -> drawProfile(c);
            case DESIGN -> drawDesign(c);
            case THEME -> drawTheme(c);
            case INTERACTION -> drawInteraction(c);
            case STATUS -> drawStatus(c);
            case BACK -> drawBack(c);
            case CLOSE -> drawClose(c);
            case INFO -> drawInfo(c);
        }
        frame(c, type == MapArtwork.EDGE ? new Color(102, 222, 255) : new Color(85, 130, 190));
    }

    private void drawPanel(MapCanvas c) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int shimmer = ((x * 3 + y * 5) % 19 == 0) ? 8 : 0;
                int r = 9 + shimmer;
                int g = 15 + shimmer;
                int b = 31 + shimmer * 2;
                if ((x + y) % 24 == 0) { r += 2; g += 5; b += 11; }
                px(c, x, y, new Color(clamp(r), clamp(g), clamp(b)));
            }
        }
        line(c, 0, 126, 127, 126, new Color(23, 43, 72));
        line(c, 126, 0, 126, 127, new Color(23, 43, 72));
        circle(c, 108, 18, 3, new Color(30, 73, 106), true);
        circle(c, 18, 105, 2, new Color(43, 52, 103), true);
    }

    private void drawEdge(MapCanvas c) {
        gradient(c, new Color(13, 35, 60), new Color(44, 24, 74));
        rect(c, 5, 5, 122, 122, new Color(34, 72, 105), false);
        rect(c, 7, 7, 120, 120, new Color(99, 205, 242), false);
        line(c, 10, 12, 117, 12, new Color(193, 240, 255));
        line(c, 10, 115, 117, 115, new Color(117, 102, 190));
        circle(c, 16, 16, 3, new Color(220, 247, 255), true);
        circle(c, 112, 112, 3, new Color(213, 162, 255), true);
        for (int i = 0; i < 5; i++) {
            px(c, 20 + i * 4, 20, new Color(104, 205, 240));
            px(c, 108 - i * 4, 108, new Color(172, 130, 237));
        }
    }

    private void drawOrnament(MapCanvas c) {
        radial(c, 64, 64, 58, new Color(14, 28, 58), new Color(52, 23, 75));
        circle(c, 64, 64, 43, new Color(43, 86, 132), false);
        circle(c, 64, 64, 37, new Color(92, 194, 231), false);
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
        int width = 5 * scale;
        int height = 7 * scale;
        int left = (128 - width) / 2;
        int top = (128 - height) / 2;
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
        int[][] points = {{72,17},{35,73},{57,73},{49,111},{93,51},{68,51}};
        polygon(c, points, new Color(74, 199, 255));
        int[][] inner = {{69,35},{48,67},{63,67},{59,91},{81,55},{65,55}};
        polygon(c, inner, new Color(234, 248, 255));
        circle(c, 64, 64, 53, new Color(90, 168, 222), false);
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
        line(c, 44, 44, 84, 84, new Color(255, 145, 167));
        line(c, 84, 44, 44, 84, new Color(255, 145, 167));
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
            for (int x = Math.max(0, x1); x <= Math.min(127, x2); x++)
                px(c, x, y, color);
    }

    private void rect(MapCanvas c, int x1, int y1, int x2, int y2, Color color, boolean filled) {
        if (filled) { fill(c, x1, y1, x2, y2, color); return; }
        line(c, x1, y1, x2, y1, color);
        line(c, x2, y1, x2, y2, color);
        line(c, x2, y2, x1, y2, color);
        line(c, x1, y2, x1, y1, color);
    }

    private void rounded(MapCanvas c, int x1, int y1, int x2, int y2, int radius, Color color, boolean filled) {
        if (filled) {
            fill(c, x1 + radius, y1, x2 - radius, y2, color);
            fill(c, x1, y1 + radius, x2, y2 - radius, color);
            circle(c, x1 + radius, y1 + radius, radius, color, true);
            circle(c, x2 - radius, y1 + radius, radius, color, true);
            circle(c, x1 + radius, y2 - radius, radius, color, true);
            circle(c, x2 - radius, y2 - radius, radius, color, true);
        } else {
            rect(c, x1, y1, x2, y2, color, false);
        }
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
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                int distance = x * x + y * y;
                if (filled ? distance <= squared : distance <= squared && distance >= (radius - 2) * (radius - 2))
                    px(c, cx + x, cy + y, color);
            }
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
                if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) {
                    intersections[count++] = a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                }
            }
            java.util.Arrays.sort(intersections, 0, count);
            for (int i = 0; i + 1 < count; i += 2) line(c, intersections[i], y, intersections[i + 1], y, color);
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

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
