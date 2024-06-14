package xzot1k.plugins.ds.api.objects;

import io.papermc.lib.PaperLib;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Display {
    public static NamespacedKey key = new NamespacedKey(DisplayShops.getPluginInstance(), "DisplayShops-Entity");

    private final Shop shop;
    private final List<ArmorStand> lines = new ArrayList<>();
    private ArmorStand glass, itemHolder;

    private final List<Integer> usedLines = new ArrayList<>();

    //private ItemFrame itemFrame;
    //private Item item;

    public Display(@NotNull Shop shop) {
        this.shop = shop;
        update();
    }
    private double currentAngle = 0;

    public static void ClearAllEntities() {
        DisplayShops.getPluginInstance().getServer().getWorlds().forEach(world -> {
            world.getEntities().forEach(entity -> {
                if ((entity.getType() == EntityType.ARMOR_STAND || entity.getType() == EntityType.ITEM_FRAME) && (entity.hasMetadata("DisplayShops-Entity")
                        || entity.getPersistentDataContainer().has(key))) {entity.remove();}
            });
        });

        DisplayShops.getPluginInstance().getDisplayManager().getShopDisplays().values().forEach(display -> {
            //if (display.getItem() != null) {display.getItem().remove();}
            if (display.getItemHolder() != null) {display.getItemHolder().remove();}
            if (display.getGlass() != null) {display.getGlass().remove();}
            if (display.getLines() != null) {display.getLines().forEach(entity -> {if (entity != null) {entity.remove();}});}
        });
    }

    public void update() {
        if (getShop() == null) return;

        World world = DisplayShops.getPluginInstance().getServer().getWorld(getShop().getBaseLocation().getWorldName());
        if (world == null) {return;}

        Location baseLocation = getShop().getBaseLocation().asBukkitLocation();
        if (baseLocation == null) return;

        updateItem(world, baseLocation);
        updateGlass(world, baseLocation);
        updateLines(world, baseLocation);
    }

    public void rotate() {
        if (getItemHolder() == null || getItemHolder().isDead() || !DisplayShops.getPluginInstance().getConfig().getBoolean("allow-item-spinning")) {return;}

        if (getItemHolder().getEquipment() != null && getItemHolder().getEquipment().getHelmet() != null) {
            ItemStack item = getItemHolder().getEquipment().getHelmet();

            List<String> denySpinningList = DisplayShops.getPluginInstance().getConfig().getStringList("deny-item-spinning");
            for (int i = -1; ++i < denySpinningList.size(); ) {
                String line = denySpinningList.get(i);
                if (!line.contains(":")) {continue;}

                String[] args = line.split(":");
                if (args.length < 2) {continue;}

                final String material = args[0];
                boolean matches = false;

                if (DisplayShops.getPluginInstance().isItemAdderInstalled()) {
                    if (dev.lone.itemsadder.api.CustomStack.isInRegistry(material)) {matches = true;}
                }

                if (DisplayShops.getPluginInstance().isOraxenInstalled()) {
                    io.th0rgal.oraxen.items.ItemBuilder itemBuilder = io.th0rgal.oraxen.api.OraxenItems.getItemById(material);
                    if (itemBuilder != null) {matches = true;}
                }

                if (matches || args[0].equalsIgnoreCase(item.getType().name())) {return;}
            }
        }

        currentAngle += Math.PI / 48; // Increment the angle (adjust the speed as needed)
        if (currentAngle >= 2 * Math.PI) {
            currentAngle = 0;
        }

        // Create a new EulerAngle for the head's pose
        EulerAngle headPose = new EulerAngle(0, currentAngle, 0);
        getItemHolder().setHeadPose(headPose);
    }

    public void delete() {
        DisplayShops.getPluginInstance().getDisplayManager().getShopDisplays().remove(shop.getOwnerUniqueId());

        if (getItemHolder() != null) {getItemHolder().remove();}
        if (getGlass() != null) {getGlass().remove();}
        if (getLines() != null) {getLines().forEach(entity -> {if (entity != null) {entity.remove();}});}
    }

    private void updateItem(World world, Location baseLocation) {
        final float yaw = baseLocation.getYaw();
        final ItemStack item = (shop.getShopItem() != null ? shop.getShopItem() : new ItemStack(Material.BARRIER));
        final boolean isBlock = item.getType().isBlock() && item.getType() != Material.BARRIER,
                allowsSpinning = DisplayShops.getPluginInstance().getConfig().getBoolean("allow-item-spinning");

        float addYaw = 0f, addPitch = 0f;
        Location destination = baseLocation.clone();

        if (isBlock) {destination.add(0.5, 0.4, 0.5);}

        if ((yaw > 145 && yaw <= 180) || (yaw < -145 && yaw >= -180)) { // north

            if (!isBlock) destination.add(0.5, 0, allowsSpinning ? 0.5 : 0);
            addYaw = 0f; // opposite direction
            //System.out.println(item.getType() + " | NORTH");

        } else if (yaw <= 45 && yaw >= -45) { // south

            if (!isBlock) destination.add(0.5, 0, allowsSpinning ? 0.5 : 0);
            addYaw = 180f; // opposite direction
            //System.out.println(item.getType() + " | SOUTH");

        } else if (yaw > 45 && yaw < 145) { // west

            if (!isBlock) destination.add(allowsSpinning ? 0.5 : 0, 0, 0.5);
            addYaw = -90f; // opposite direction

            //System.out.println(item.getType() + " | WEST");

        } else if (yaw < -45 && yaw >= -145) { // east

            if (!isBlock) destination.add(allowsSpinning ? 0.5 : 0, 0, 0.5);
            addYaw = 90f; // opposite direction
            //System.out.println(item.getType() + " | EAST");

        }

        // handle offset
        List<String> itemOffsets = DisplayShops.getPluginInstance().getConfig().getStringList("item-display-offsets");
        for (int i = -1; ++i < itemOffsets.size(); ) {
            String line = itemOffsets.get(i);
            if (!line.contains(":")) {continue;}

            String[] args = line.split(":");
            if (args.length < 2 || !args[1].contains(",")) {continue;}

            final String material = args[0];
            boolean matches = false;

            if (DisplayShops.getPluginInstance().isItemAdderInstalled()) {
                if (dev.lone.itemsadder.api.CustomStack.isInRegistry(material)) {matches = true;}
            }

            if (DisplayShops.getPluginInstance().isOraxenInstalled()) {
                io.th0rgal.oraxen.items.ItemBuilder itemBuilder = io.th0rgal.oraxen.api.OraxenItems.getItemById(material);
                if (itemBuilder != null) {matches = true;}
            }

            if (!matches && !item.getType().name().contains(args[0].toUpperCase().replace(" ", "_").replace("-", "_"))) {continue;}

            String[] offsets = args[1].split(",");
            if (offsets.length < 5) {continue;}

            destination.add(Double.parseDouble(offsets[0]), Double.parseDouble(offsets[1]), Double.parseDouble(offsets[2]));
            addYaw = Float.parseFloat(offsets[3]);
            addPitch = Float.parseFloat(offsets[4]);
        }

        if (getItemHolder() == null || getItemHolder().isDead()) {itemHolder = world.spawn(destination, ArmorStand.class);} else {
            if (getItemHolder().getEquipment() != null && getItemHolder().getEquipment().getHelmet() != null
                    && !DisplayShops.getPluginInstance().getManager().isSimilar(getItemHolder().getEquipment().getHelmet(), shop.getShopItem())) {
                PaperLib.teleportAsync(getItemHolder(), destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }

        getItemHolder().setMarker(true);
        getItemHolder().setGravity(false);
        getItemHolder().setSmall(true);
        getItemHolder().setVisible(false);
        getItemHolder().setAI(false);
        getItemHolder().setSmall(true);
        getItemHolder().setRemoveWhenFarAway(false);
        getItemHolder().setRotation(baseLocation.getYaw() + addYaw, baseLocation.getPitch() + addPitch);

        getItemHolder().setMetadata("DisplayShops-Entity", new FixedMetadataValue(DisplayShops.getPluginInstance(), ""));
        getItemHolder().getPersistentDataContainer().set(key, PersistentDataType.STRING, "item");

        if (getItemHolder().getEquipment() != null) {
            getItemHolder().getEquipment().setHelmet(item, true);
            getItemHolder().addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        /*if (getItem() == null || getItem().isDead()) {
            item = world.spawn(destination, Item.class);
            getItem().setItemStack(shop.getShopItem());
            //getItem().teleport(destination); // ensure the item goes where it needs to
        } else {getItem().setItemStack(shop.getShopItem());}

        getItem().setMetadata("DisplayShops-Entity", new FixedMetadataValue(DisplayShops.getPluginInstance(), ""));
        getItem().getPersistentDataContainer().set(key, PersistentDataType.STRING, "item");

        // Set properties to make it not tick
        getItem().setGravity(false);
        getItem().setPickupDelay(Integer.MAX_VALUE); // Prevent pickup
        getItem().setPersistent(true);
        getItem().setUnlimitedLifetime(true);
        getItem().setVisualFire(false);
        getItem().setInvulnerable(true);

        getItemHolder().addPassenger(getItem()); // add item as passenger*/
    }

    private void updateLines(World world, Location baseLocation) {
        ClearLines(); // remove entities

        Appearance appearance = Appearance.getAppearance(shop.getAppearanceId());
        if (appearance == null) return;

        final double[] offsets = appearance.getOffset();
        final double offsetX = offsets[0], offsetY = offsets[1], offsetZ = offsets[2];

        List<String> hologramFormat;
        if (shop.getShopItem() != null) {
            if (shop.getOwnerUniqueId() == null) hologramFormat = DisplayShops.getPluginInstance().getConfig().getStringList("admin-shop-format");
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
                    || ((line.contains("{description}") && (shop.getDescription() == null || shop.getDescription().equalsIgnoreCase(""))))) {
                continue;
            }

            Location location = new Location(world, x, y, z);
            ArmorStand lineEntity = getNextUnusedLine();
            if (lineEntity == null) {lineEntity = CreateStand(location);}

            getLines().add(lineEntity);

            if (lineEntity == null || lineEntity.isDead()) {continue;}

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
                    y += 0.3;
                }
                continue;
            }

            lineEntity.setCustomName(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().getManager().applyShopBasedPlaceholders(line, shop)));
            //getLines().add(lineEntity);

            y += 0.3;
        }

        // remove all unused
        getLines().removeIf(stand -> usedLines.contains(stand.getEntityId()));
        usedLines.clear();
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

        /*if (getItem() != null) {
            if (!player.canSee(getItem())) {player.showEntity(DisplayShops.getPluginInstance(), getItem());}
        }*/

        if (getItemHolder() != null) {
            if (!player.canSee(getItemHolder())) {player.showEntity(DisplayShops.getPluginInstance(), getItemHolder());}
        }

        if (getGlass() != null) {
            if (!player.canSee(getGlass())) {player.showEntity(DisplayShops.getPluginInstance(), getGlass());}
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
            /*if (getItem() != null) {
                if (player.canSee(getItem())) {player.hideEntity(DisplayShops.getPluginInstance(), getItem());}
            }*/

            if (getItemHolder() != null) {
                if (player.canSee(getItemHolder())) {player.hideEntity(DisplayShops.getPluginInstance(), getItemHolder());}
            }

            if (getGlass() != null) {
                if (player.canSee(getGlass())) {player.hideEntity(DisplayShops.getPluginInstance(), getGlass());}
            }
        }
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

        if (getGlass().getEquipment() != null) {getGlass().getEquipment().setHelmet(new ItemStack(Material.GLASS));}
        getGlass().addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

        getGlass().getPersistentDataContainer().set(key, PersistentDataType.STRING, "glass");
        getGlass().setMarker(true);
        getGlass().setGravity(false);
        getGlass().setVisible(false);
        getGlass().setAI(false);
        getGlass().setVisibleByDefault(false);
        getGlass().setRemoveWhenFarAway(false);
    }

    public void ClearLines() {
        for (int i = -1; ++i < getLines().size(); ) {
            ArmorStand lineEntity = getLines().get(i);
            if (lineEntity == null || lineEntity.isDead()) {continue;}

            lineEntity.remove();
        }
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
        lineEntity.setRemoveWhenFarAway(false);
        lineEntity.setMetadata("DisplayShops-Entity", new FixedMetadataValue(DisplayShops.getPluginInstance(), ""));
        lineEntity.getPersistentDataContainer().set(key, PersistentDataType.STRING, "line");

        return lineEntity;
    }

    private ArmorStand getNextUnusedLine() {
        for (int i = -1; ++i < getLines().size(); ) {
            ArmorStand lineEntity = getLines().get(i);
            if (usedLines.contains(lineEntity.getEntityId())) {return lineEntity;}
        }
        return null;
    }

    // getters & setters
    public List<ArmorStand> getLines() {return lines;}

    //public Item getItem() {return item;}

    public Shop getShop() {return shop;}

    public ArmorStand getGlass() {return glass;}

    public ArmorStand getItemHolder() {return itemHolder;}

    //public ItemFrame getItemFrame() {return itemFrame;}
}