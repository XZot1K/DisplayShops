package xzot1k.plugins.ds.api.objects;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Display {

    private final Shop shop;
    private final List<ArmorStand> lines = new ArrayList<>();
    private ArmorStand glass, itemHolder;
    private Item item;

    public Display(@NotNull Shop shop) {
        this.shop = shop;

        updateItem();
        updateGlass();
        updateLines();
    }

    public void updateItem() {
        if (getShop() == null || getShop().getShopItem() == null) return;

        World world = DisplayShops.getPluginInstance().getServer().getWorld(getShop().getBaseLocation().getWorldName());
        if (world == null) {return;}

        Location baseLocation = getShop().getBaseLocation().asBukkitLocation();
        if (baseLocation == null) return;

        Location destination = baseLocation.clone().add(0.5, 1, 0.5);

        if (getItemHolder() == null) {itemHolder = world.spawn(destination, ArmorStand.class);} else {getItemHolder().teleport(destination);}

        getItemHolder().setMarker(true);
        getItemHolder().setGravity(false);
        getItemHolder().setSmall(true);
        getItemHolder().setVisible(false);
        getItemHolder().setAI(false);

        if (getItem() == null) {
            item = world.dropItemNaturally(destination, shop.getShopItem());
            //getItem().teleport(destination); // ensure the item goes where it needs to
        } else {getItem().setItemStack(shop.getShopItem());}

        // Set properties to make it not tick
        getItem().setGravity(false);
        getItem().setPickupDelay(Integer.MAX_VALUE); // Prevent pickup
        getItem().setPersistent(true);
        getItem().setUnlimitedLifetime(true);
        getItem().setTicksLived(-1);
        getItem().setInvulnerable(true);

        getItemHolder().addPassenger(getItem()); // add item as passenger
    }

    public void updateGlass() {
        if (getShop() == null) return;

        World world = DisplayShops.getPluginInstance().getServer().getWorld(getShop().getBaseLocation().getWorldName());
        if (world == null) {return;}

        Location baseLocation = getShop().getBaseLocation().asBukkitLocation();
        if (baseLocation == null) return;

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

        if (getGlass() == null) {glass = world.spawn(newLocation, ArmorStand.class);} else {getGlass().teleport(newLocation);}

        getGlass().setMarker(true);
        getGlass().setGravity(false);
        getGlass().setSmall(true);
        getGlass().setVisible(false);
        getGlass().setAI(false);
        getGlass().setVisibleByDefault(false);
    }

    public void updateLines() {
        if (getShop() == null) return;

        World world = DisplayShops.getPluginInstance().getServer().getWorld(getShop().getBaseLocation().getWorldName());
        if (world == null) {return;}

        Location baseLocation = getShop().getBaseLocation().asBukkitLocation();
        if (baseLocation == null) return;

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

        double x = (shop.getBaseLocation().getX() + offsetX), y = (shop.getBaseLocation().getY() + (1.9 + offsetY)), z = (shop.getBaseLocation().getZ() + offsetZ);
        for (int i = hologramFormat.size(); --i >= 0; ) {
            String line = hologramFormat.get(i);

            if ((hidePriceLine && ((line.contains("buy-price") && shop.getBuyPrice(true) < 0)
                    || (line.contains("sell-price") && shop.getSellPrice(true) < 0)))
                    || ((line.contains("{description}") && (shop.getDescription() == null || shop.getDescription().equalsIgnoreCase("")))))
                continue;

            Location location = new Location(world, x, y, z);
            ArmorStand lineEntity = world.spawn(location, ArmorStand.class);
            lineEntity.setMarker(true);
            lineEntity.setGravity(false);
            lineEntity.setSmall(true);
            lineEntity.setVisible(false);
            lineEntity.setAI(false);
            lineEntity.setCustomNameVisible(true);
            lineEntity.setVisibleByDefault(false);

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

    // getters & setters
    public List<ArmorStand> getLines() {return lines;}

    public Item getItem() {return item;}

    public Shop getShop() {return shop;}

    public ArmorStand getGlass() {return glass;}

    public ArmorStand getItemHolder() {return itemHolder;}
}