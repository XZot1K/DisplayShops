package xzot1k.plugins.ds.core.packets;

import de.tr7zw.changeme.nbtapi.NBTItem;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

import java.util.Arrays;

public class VersionUtil implements xzot1k.plugins.ds.api.VersionUtil
{
    private final boolean redstoneExists = Arrays.stream(Particle.values()).anyMatch(particle -> particle.name().equals("REDSTONE"));

    @Override
    public void sendActionBar(@NotNull Player player, @NotNull String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(DisplayShops.getPluginInstance().getManager().color(message)));
    }

    @Override
    public void displayParticle(@NotNull Player player, @NotNull String particleName, @NotNull Location location, double offsetX, double offsetY, double offsetZ, int speed, int amount) {
        if (location.getWorld() != null) {
            Particle particle = Particle.valueOf(particleName);
            if (particle == (redstoneExists ? Particle.valueOf("REDSTONE") : Particle.valueOf("DUST"))) { player.spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ); }
            else player.spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, 0);
        }
    }

    @Override
    public String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag) {
       /* final net.minecraft.world.item.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.w();
        return tag.l(nbtTag);*/
        NBTItem nbtItem = new NBTItem(itemStack);
        return nbtItem.hasNBTData() ? nbtItem.getString(nbtTag) : null;
    }

    @Override
    public ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value) {
        NBTItem nbtItem = new NBTItem(itemStack);
        nbtItem.setString(nbtTag, value);
        nbtItem.applyNBT(itemStack);
        return itemStack;
    }
}