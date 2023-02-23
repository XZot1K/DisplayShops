/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_18_R1;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.handlers.ParticleHandler;

public class PPacket implements ParticleHandler {
    @Override
    public void displayParticle(@NotNull Player player, @NotNull String particleName, @NotNull Location location,
                                double offsetX, double offsetY, double offsetZ, int speed, int amount) {
        if (location.getWorld() != null) {
            Particle particle = Particle.valueOf(particleName);
            if (particle == Particle.REDSTONE)
                player.spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ);
            else player.spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, 0);
        }
    }
}
