/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_8_R3;

import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.handlers.ParticleHandler;

public class PPacket implements ParticleHandler {
    @Override
    public void displayParticle(@NotNull Player player, @NotNull String particleName, @NotNull Location location, double offsetX, double offsetY,
                                double offsetZ, int speed, int amount) {
        if (location.getWorld() != null) {
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(EnumParticle.valueOf(particleName),
                    true, (float) location.getX(), (float) location.getY(), (float) location.getZ(), (float) offsetX,
                    (float) offsetY, (float) offsetZ, speed, amount);
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        }
    }
}
