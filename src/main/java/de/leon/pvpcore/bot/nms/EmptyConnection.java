package de.leon.pvpcore.bot.nms;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import java.net.InetSocketAddress;

/** A client-less connection that preserves knockback packets addressed to this fake player. */
final class EmptyConnection extends Connection {
    private int ownerId = Integer.MIN_VALUE;
    private Vec3 pendingMotion;

    EmptyConnection() {
        super(PacketFlow.SERVERBOUND);
        this.channel = new EmbeddedChannel();
        this.address = new InetSocketAddress("127.0.0.1", 0);
    }

    void owner(int entityId) {
        ownerId = entityId;
    }

    Vec3 takePendingMotion() {
        Vec3 motion = pendingMotion;
        pendingMotion = null;
        return motion;
    }

    @Override public void send(Packet<?> packet) { capture(packet); }
    @Override public void send(Packet<?> packet, ChannelFutureListener listener) { capture(packet); }
    @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) { capture(packet); }

    private void capture(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == ownerId) {
            pendingMotion = motion.movement();
        }
    }

    @Override public void flushChannel() { }
    @Override public boolean isConnected() { return true; }
}
