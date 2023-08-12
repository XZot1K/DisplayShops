package xzot1k.plugins.ds.nms.v1_14_R1;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.server.v1_14_R1.NBTTagCompound;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_14_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.VersionUtil;

public class VUtil implements VersionUtil {


    @Override
    public void sendActionBar(@NotNull Player player, @NotNull String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(DisplayShops.getPluginInstance().getManager().color(message)));
    }

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

    @Override
    public String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag) {
        final net.minecraft.server.v1_14_R1.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.getOrCreateTag();
        return tag.getString(nbtTag);
    }

    @Override
    public ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value) {
        final net.minecraft.server.v1_14_R1.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.getOrCreateTag(); // getOrCreateTag()
        tag.setString(nbtTag, value); // putString()
        item.save(tag); // save()

        return CraftItemStack.asBukkitCopy(item);
    }

}
