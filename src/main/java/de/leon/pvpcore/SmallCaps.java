package de.leon.pvpcore;

import java.util.Locale;

/** Converts user-facing plugin text to the same Unicode small-caps style as LifestealCore. */
public final class SmallCaps {
    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() { }

    public static String of(String input) {
        if (input == null || input.isEmpty()) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            int index = FROM.indexOf(c);
            out.append(index >= 0 ? TO.charAt(index) : c);
        }
        return out.toString();
    }
}
