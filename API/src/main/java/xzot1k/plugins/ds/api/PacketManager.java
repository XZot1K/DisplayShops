package xzot1k.plugins.ds.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.handlers.ActionBarHandler;
import xzot1k.plugins.ds.api.handlers.ParticleHandler;
import xzot1k.plugins.ds.api.handlers.SerializeUtil;

public interface PacketManager {

    ParticleHandler getParticleHandler();

    ActionBarHandler getActionBarHandler();

    SerializeUtil getSerializeUtil();

    String toString(@NotNull ItemStack itemStack);

    ItemStack toItem(@NotNull String itemString);

    String toJSON(@NotNull ItemStack itemStack);

    String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag);

    ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value);

    void displayParticle(@NotNull Player player, @NotNull String particleName, @NotNull Location location,
                         double offsetX, double offsetY, double offsetZ, int speed, int amount);

    void sendActionBar(@NotNull Player player, @NotNull String message);

}
