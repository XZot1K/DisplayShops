package xzot1k.plugins.ds.api.objects;

import com.plotsquared.core.command.Clear;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Display {

    private final Shop shop;
    private final List<ArmorStand> lines = new ArrayList<>();
    private ArmorStand glass, itemHolder;
    private Item item;

    public Display(@NotNull Shop shop) {
        this.shop = shop;
        update();
    }

    public void update() {
        if (getShop() == null || getShop().getShopItem() == null) return;

        World world = DisplayShops.getPluginInstance().getServer().getWorld(getShop().getBaseLocation().getWorldName());
        if (world == null) {return;}

        Location baseLocation = getShop().getBaseLocation().asBukkitLocation();
        if (baseLocation == null) return;

        updateItem(world, baseLocation);
        updateGlass(world, baseLocation);
        updateLines(world, baseLocation);
    }

    private void updateItem(World world, Location baseLocation) {
        Location destination = baseLocation.clone().add(0.5, 1, 0.5);

        if (getItemHolder() == null || getItemHolder().isDead()) {itemHolder = world.spawn(destination, ArmorStand.class);} else {getItemHolder().teleport(destination);}

        getItemHolder().setMarker(true);
        getItemHolder().setGravity(false);
        getItemHolder().setSmall(true);
        getItemHolder().setVisible(false);
        getItemHolder().setAI(false);

        if (getItem() == null || getItem().isDead()) {
            item = world.dropItemNaturally(destination, shop.getShopItem());
            //getItem().teleport(destination); // ensure the item goes where it needs to
        } else {getItem().setItemStack(shop.getShopItem());}

        getItem().setMetadata("DisplayShops-Entity", new FixedMetadataValue(DisplayShops.getPluginInstance(), ""));

        // Set properties to make it not tick
        getItem().setGravity(false);
        getItem().setPickupDelay(Integer.MAX_VALUE); // Prevent pickup
        getItem().setPersistent(true);
        getItem().setUnlimitedLifetime(true);
        getItem().setVisualFire(false);
        getItem().setInvulnerable(true);

        getItemHolder().addPassenger(getItem()); // add item as passenger
    }

    private void updateGlass(World world, Location baseLocation) {
        Appearance appearance = Appearance.getAppearance(shop.getAppearanceId());
        if (appearance == null) return;

        final double[] offsets = appearance.getOffset();
        final double offsetX = offsets[0], offsetY = offsets[1], offsetZ = offsets[2];

        // return if glass is supposed to be hidden
        if (DisplayShops.getPluginInstance().getConfig().getBoolean("hide-glass")) return;

        double x = (shop.getBaseLocation().getX() + offsetX),
                y = (shop.getBaseLocation().getY() + offsetY),
                z = (shop.getBaseLocation().getZ() + offsetZ);

        Location newLocation = new Location(world, x, y, z);

        if (getGlass() == null || getGlass().isDead()) {glass = world.spawn(newLocation, ArmorStand.class);} else {getGlass().teleport(newLocation);}

        if(getGlass().getEquipment() != null) {getGlass().getEquipment().setHelmet(new ItemStack(Material.GLASS));}
        getGlass().addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

        getGlass().setMarker(true);
        getGlass().setGravity(false);
        getGlass().setVisible(false);
        getGlass().setAI(false);
        getGlass().setVisibleByDefault(false);
    }

    private void updateLines(World world, Location baseLocation) {
        ClearLines(); // remove entities

        Appearance appearance = Appearance.getAppearance(shop.getAppearanceId());
        if (appearance == null) return;

        final double[] offsets = appearance.getOffset();
        final double offsetX = offsets[0], offsetY = offsets[1], offsetZ = offsets[2];

        List<String> hologramFormat;
        if (shop.getShopItem() != null) {
            if (shop.getOwnerUniqueId() == null)
                hologramFormat = DisplayShops.getPluginInstance().getConfig().getStringList("admin-shop-format");
            else hologramFormat = DisplayShops.getPluginInstance().getConfig().getStringList("valid-item-format");
        } else {
            if (shop.getOwnerUniqueId() == null) hologramFormat = DisplayShops.getPluginInstance().getConfig().getStringList("admin-invalid-item-format");
            else hologramFormat = DisplayShops.getPluginInstance().getConfig().getStringList("invalid-item-format");
        }

        final String colorCode = DisplayShops.getPluginInstance().getConfig().getString("default-description-color");
        final boolean hidePriceLine = DisplayShops.getPluginInstance().getConfig().getBoolean("price-disabled-hide");

        double x = (baseLocation.getX() + offsetX), y = (baseLocation.getY() + (1.9 + offsetY)), z = (baseLocation.getZ() + offsetZ);

        for (int i = hologramFormat.size(); --i >= 0; ) {
            String line = hologramFormat.get(i);

            if ((hidePriceLine && ((line.contains("buy-price") && shop.getBuyPrice(true) < 0)
                    || (line.contains("sell-price") && shop.getSellPrice(true) < 0)))
                    || ((line.contains("{description}") && (shop.getDescription() == null || shop.getDescription().equalsIgnoreCase("")))))
                continue;

            Location location = new Location(world, x, y, z);

            ArmorStand lineEntity = CreateStand(location);
            if (lineEntity == null) {continue;}

            if (line.contains("{description}") && !(shop.getDescription() == null || shop.getDescription().equalsIgnoreCase(""))) {
                final String[] otherContents = line.split("\\{description}");
                final String prefix = (otherContents.length >= 1 ? otherContents[0] : ""),
                        suffix = (otherContents.length >= 2 ? otherContents[1] : "");

                List<String> descriptionLines = DisplayShops.getPluginInstance().getManager().wrapString(shop.getDescription());
                Collections.reverse(descriptionLines);
                for (int j = -1; ++j < descriptionLines.size(); ) {
                    String descriptionLine = DisplayShops.getPluginInstance().getManager().color(descriptionLines.get(j));
                    descriptionLine = (descriptionLine.contains(ChatColor.COLOR_CHAR + "") ? descriptionLine : (DisplayShops.getPluginInstance().getManager().color(colorCode + descriptionLine)));

                    lineEntity.setCustomName(DisplayShops.getPluginInstance().getManager().color((prefix + descriptionLine + suffix)));
                    getLines().add(lineEntity);

                    y += 0.3;
                }
                continue;
            }

            lineEntity.setCustomName(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().getManager().applyShopBasedPlaceholders(line, shop)));
            getLines().add(lineEntity);

            y += 0.3;
        }
    }

    /**
     * Reveals a display to a player given certain circumstances.
     *
     * @param player  The player to show the display to.
     * @param focused Whether the player is looking directly at the shop
     */
    public void show(@NotNull Player player, boolean focused) {
        if (!shouldSee(player)) {
            hide(player, true);
            return;
        }

        for (int i = -1; ++i < lines.size(); ) {
            ArmorStand lineEntity = lines.get(i);
            if (lineEntity == null || lineEntity.isDead()) {continue;}


            if (focused && !player.canSee(lineEntity)) {player.showEntity(DisplayShops.getPluginInstance(), lineEntity);} else if (!focused && player.canSee(lineEntity)) {
                player.hideEntity(DisplayShops.getPluginInstance(), lineEntity);
            }
        }

        if (getItem() != null) {
            if (!player.canSee(getItem())) {player.showEntity(DisplayShops.getPluginInstance(), getItem());}
        }

        if (getGlass() != null) {
            if (!player.canSee(getGlass())) {player.showEntity(DisplayShops.getPluginInstance(), getGlass());}
        }
    }

    /**
     * Hides the display from the player given certain circumstances.
     *
     * @param player  The player to hide the display from.
     * @param hideAll Whether to hide everything or not.
     */
    public void hide(@NotNull Player player, boolean hideAll) {
        for (int i = -1; ++i < lines.size(); ) {
            ArmorStand lineEntity = lines.get(i);
            if (lineEntity == null || lineEntity.isDead()) {continue;}

            if (player.canSee(lineEntity)) {player.hideEntity(DisplayShops.getPluginInstance(), lineEntity);}
        }

        if (hideAll) {
            if (getItem() != null) {
                if (player.canSee(getItem())) {player.hideEntity(DisplayShops.getPluginInstance(), getItem());}
            }

            if (getGlass() != null) {
                if (player.canSee(getGlass())) {player.hideEntity(DisplayShops.getPluginInstance(), getGlass());}
            }
        }
    }

    /**
     * @param player The player to run checks with.
     * @return Whether the player should be able to see the display
     */
    public boolean shouldSee(@NotNull Player player) {
        return (shop.getBaseLocation().getWorldName().equalsIgnoreCase(player.getWorld().getName()) &&
                shop.getBaseLocation().distance(player.getLocation(), false) <= DisplayShops.getPluginInstance().getConfig().getInt("view-distance"));
    }

    private ArmorStand CreateStand(@NotNull Location location) {
        if (location.getWorld() == null) {return null;}

        ArmorStand lineEntity = location.getWorld().spawn(location, ArmorStand.class);
        lineEntity.setMarker(true);
        lineEntity.setGravity(false);
        lineEntity.setSmall(true);
        lineEntity.setVisible(false);
        lineEntity.setAI(false);
        lineEntity.setCustomNameVisible(true);
        lineEntity.setVisibleByDefault(false);
        lineEntity.setMetadata("DisplayShops-Entity", new FixedMetadataValue(DisplayShops.getPluginInstance(), ""));

        getLines().add(lineEntity);
        return lineEntity;
    }

    public void ClearLines() {
        for (int i = -1; ++i < getLines().size(); ++i) {
            ArmorStand lineEntity = getLines().get(i);
            if (lineEntity == null || lineEntity.isDead()) {continue;}

            lineEntity.remove();
        }
    }

    public static void ClearAllEntities() {
        DisplayShops.getPluginInstance().getServer().getWorlds().forEach(world -> {
            world.getEntities().forEach(entity -> {
                if ((entity.getType() == EntityType.ARMOR_STAND || entity.getType() == EntityType.ITEM) && entity.hasMetadata("DisplayShops-Entity")) {entity.remove();}
            });
        });

        DisplayShops.getPluginInstance().getDisplayManager().getShopDisplays().values().forEach(display -> {
            if (display.getItem() != null) {display.getItem().remove();}
            if (display.getGlass() != null) {display.getGlass().remove();}
            if (display.getLines() != null) {display.getLines().forEach(entity -> {if (entity != null) {entity.remove();}});}
        });
    }

    // getters & setters
    public List<ArmorStand> getLines() {return lines;}

    public Item getItem() {return item;}

    public Shop getShop() {return shop;}

    public ArmorStand getGlass() {return glass;}

    public ArmorStand getItemHolder() {return itemHolder;}
}