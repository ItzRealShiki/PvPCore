package de.leon.pvpcore;

import java.util.Locale;

/** Converts user-facing text to LifestealCore-style Unicode small caps while preserving color codes. */
public final class SmallCaps {
    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO   = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String COLORS = "0123456789abcdefklmnor";

    private SmallCaps() { }

    public static String of(String input) {
        if (input == null || input.isEmpty()) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < lower.length()
                    && COLORS.indexOf(Character.toLowerCase(lower.charAt(i + 1))) >= 0) {
                out.append(c).append(lower.charAt(++i));
                continue;
            }
            int index = FROM.indexOf(c);
            out.append(index >= 0 ? TO.charAt(index) : c);
        }
        return out.toString();
    }
}
