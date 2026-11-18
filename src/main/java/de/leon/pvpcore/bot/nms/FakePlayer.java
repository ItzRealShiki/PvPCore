package de.leon.pvpcore.bot.nms;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.UUID;

/** A real ServerPlayer without a connected client. All calls must run on the main server thread. */
@SuppressWarnings("UnstableApiUsage")
public final class FakePlayer {
    private final ServerPlayer handle;
    private final EmptyConnection connection;

    private FakePlayer(ServerPlayer handle, EmptyConnection connection) {
        this.handle = handle;
        this.connection = connection;
    }

    public static FakePlayer spawn(Location at, String name, Player skinFrom) {
        ServerLevel level = ((CraftWorld) at.getWorld()).getHandle();
        MinecraftServer server = level.getServer();
        ServerPlayer skinOwner = ((CraftPlayer) skinFrom).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name, skinOwner.getGameProfile().properties());

        ServerPlayer handle = new Body(server, level, profile, skinOwner.clientInformation());
        handle.getAdvancements().clearTriggers();

        EmptyConnection connection = new EmptyConnection();
        connection.owner(handle.getId());
        handle.connection = new ServerGamePacketListenerImpl(
                server, connection, handle, CommonListenerCookie.createInitial(profile, false));
        handle.snapTo(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        handle.setYHeadRot(at.getYaw());

        FakePlayer fake = new FakePlayer(handle, connection);
        for (Player viewer : Bukkit.getOnlinePlayers()) fake.showTo(viewer);

        if (!level.addFreshEntity(handle, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            fake.hideFromAll();
            throw new IllegalStateException("Fake player spawn was cancelled: " + name);
        }

        // Keep the bot out of the real-player list and the tab list.
        level.players().remove(handle);
        return fake;
    }

    public Player bukkit() {
        return handle.getBukkitEntity();
    }

    public boolean isValid() {
        return !handle.isRemoved() && handle.isAlive();
    }

    /** Run vanilla player ticking so gravity, collisions and server-side movement work. */
    public void tick() {
        if (!isValid()) return;
        if (handle.level() instanceof ServerLevel level) level.players().remove(handle);
        Vec3 knockback = connection.takePendingMotion();
        if (knockback != null) handle.setDeltaMovement(knockback);
        handle.doTick();
    }

    public void clearSpawnInvulnerability() {
        handle.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    }

    public void showTo(Player viewer) {
        send(viewer, ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(handle, false));
    }

    public void remove() {
        handle.discard();
        handle.getAdvancements().clearTriggers();
        hideFromAll();
    }

    private void hideFromAll() {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(handle.getUUID()));
        for (Player viewer : Bukkit.getOnlinePlayers()) send(viewer, packet);
    }

    private static void send(Player viewer, Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }

    private static final class Body extends ServerPlayer {
        Body(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
            super(server, level, profile, info);
        }

        @Override public boolean isClientAuthoritative() { return false; }
        @Override public Vec3 getKnownMovement() { return getDeltaMovement(); }
        @Override public Vec3 getKnownSpeed() { return getDeltaMovement(); }
    }
}
