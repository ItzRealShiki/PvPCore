package de.leon.customguitest.gui;

import java.util.List;
import java.util.Locale;

public enum GuiPage {
    HOME("nova", "NOVA"),
    DASHBOARD("dashboard", "DASH"),
    PROFILE("profile", "PROFIL"),
    SETTINGS("settings", "STYLE"),
    SHOWCASE("showcase", "ITEMS"),
    STATS("stats", "STATS"),
    LAB("lab", "LAB");

    private final String id;
    private final String title;

    GuiPage(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() { return id; }
    public String title() { return title; }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(GuiPage::id).toList();
    }

    public static GuiPage fromId(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (GuiPage page : values()) {
            if (page.id.equals(normalized)) return page;
        }
        if (normalized.equals("home") || normalized.equals("main") || normalized.equals("open")) return HOME;
        if (normalized.equals("inventory") || normalized.equals("items")) return SHOWCASE;
        if (normalized.equals("info") || normalized.equals("design")) return LAB;
        return null;
    }
}
