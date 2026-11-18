package de.leon.pvpcore.bot;

import org.bukkit.Bukkit;

import java.util.Set;

public final class NmsGuard {
    private static final Set<String> SUPPORTED = Set.of("26.2");

    private NmsGuard() {}

    public static boolean supported() {
        return SUPPORTED.contains(Bukkit.getMinecraftVersion());
    }
}
