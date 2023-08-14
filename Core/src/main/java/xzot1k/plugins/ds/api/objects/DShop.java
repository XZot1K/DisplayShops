/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.eco.EcoHook;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.events.ShopVisitEvent;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DShop implements Shop {

    private final DisplayShops INSTANCE;

    private UUID shopId, ownerUniqueId, currentEditor;
    private ItemStack shopItem, tradeItem;
    private int stock, shopItemAmount, globalBuyCounter, globalSellCounter, playerBuyLimit,
            playerSellLimit, globalBuyLimit, globalSellLimit, dynamicBuyPriceCounter, dynamicSellPriceCounter;
    private long changeTimeStamp, lastBuyTimeStamp, lastSellTimeStamp;
    private String description, storedBaseBlockMaterial, currencyType;
    private BigDecimal buyPrice, sellPrice, storedBalance;
    private boolean commandOnlyMode, dynamicPriceChange;
    private List<String> commands;
    private List<UUID> assistants;
    private LocationClone baseLocation;

    public DShop(UUID shopId, UUID ownerUniqueId, LocationClone baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();

        reset();

        setShopId(shopId);
        setShopItemAmount(shopItemAmount);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(baseLocation);
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
    }

    public DShop(UUID shopId, UUID ownerUniqueId, Location baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();

        reset();

        setShopId(shopId);
        setShopItemAmount(shopItemAmount);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(new LClone(baseLocation));
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
    }

    public DShop(UUID shopId, UUID ownerUniqueId, ItemStack shopItem, Location baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();

        reset();

        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(new LClone(baseLocation));
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setShopItem(shopItem);
        setShopItemAmount(shopItemAmount);
    }

    public DShop(UUID shopId, UUID ownerUniqueId, ItemStack shopItem, LocationClone baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();

        reset();

        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(baseLocation);
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setShopItem(shopItem);
        setShopItemAmount(shopItemAmount);
    }

    /**
     * Kills the shop's display packets entirely.
     *
     * @param player The player to send the destroy packets to.
     */
    public synchronized void kill(@NotNull Player player) {
        DisplayPacket displayPacket = INSTANCE.getDisplayPacket(this, player);
        if (displayPacket != null) displayPacket.hide(player);
        INSTANCE.removeDisplayPacket(this, player);
    }

    /**
     * Kills the shop's display packets entirely for ALL players.
     */
    public synchronized void killAll() {
        for (Player player : INSTANCE.getServer().getOnlinePlayers()) {
            DisplayPacket displayPacket = INSTANCE.getDisplayPacket(this, player);
            if (displayPacket != null) displayPacket.hide(player);
            INSTANCE.removeDisplayPacket(this, player);
        }
    }

    /**
     * Shows the shop display to the player.
     *
     * @param player        The player to show the display to.
     * @param showHolograms Whether the hologram lines are shown or not.
     */
    public synchronized void display(@NotNull Player player, boolean showHolograms) {
        kill(player);

        if (getBaseLocation() == null) {
            INSTANCE.log(Level.WARNING, "The shop '" + getShopId() + "' is having some issues with its base location (Returning Null).");
            return;
        }

        World world = INSTANCE.getServer().getWorld(getBaseLocation().getWorldName());
        if (world == null) {
            INSTANCE.log(Level.WARNING,
                    "The shop \"" + getShopId() + "\" is having some issues with the world (" + getBaseLocation().getWorldName() + ").");
            return;
        }

        if (!world.isChunkLoaded(((int) getBaseLocation().getX() >> 4), ((int) getBaseLocation().getZ() >> 4))) return;

        try {
            Constructor<?> constructor = INSTANCE.displayPacketClass.getDeclaredConstructor(DisplayShops.class, Player.class, Shop.class, boolean.class);
            DisplayPacket displayPacket = (DisplayPacket) constructor.newInstance(INSTANCE, player, this, showHolograms);
            INSTANCE.updateDisplayPacket(this, player, displayPacket);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            INSTANCE.log(Level.WARNING, e.getMessage());
        }
    }

    /**
     * Checks if player is in range of this shop.
     *
     * @param player   The player to check.
     * @param distance The distance between the player and the shop.
     * @return Whether the player is in range or not.
     */
    public boolean isInRange(@NotNull Player player, double distance) {
        return (getBaseLocation() != null && getBaseLocation().getWorldName().equalsIgnoreCase(player.getWorld().getName())
                && getBaseLocation().distance(player.getLocation(), true) < distance);
    }

    /**
     * Obtains the passed shop's max stock based on owner permissions or administrator bypasses.
     *
     * @return The obtained max stock (defaults to configuration value or max possible integer, if the shop is admin).
     */
    public int getMaxStock() {
        int maxStock = ((isAdminShop() && getStock() <= -1) ? Integer.MAX_VALUE : INSTANCE.getConfig().getInt("max-shop-stock"));
        if (!isAdminShop() && getOwnerUniqueId() != null) {
            OfflinePlayer owner = INSTANCE.getServer().getOfflinePlayer(getOwnerUniqueId());
            if (owner.getPlayer() != null) {
                final Player player = owner.getPlayer();
                for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
                    if (permissionAttachmentInfo.getPermission().toLowerCase().startsWith("displayshops.stock.")) {
                        String intValue = permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.stock.", "");

                        if (intValue.equalsIgnoreCase("max")) return Integer.MAX_VALUE;
                        else if (INSTANCE.getManager().isNotNumeric(intValue)) continue;

                        int tempValue = (int) Double.parseDouble(permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.stock.", ""));
                        if (tempValue > maxStock) maxStock = tempValue;
                    }
                }
            }
        }

        return maxStock;
    }

    /**
     * Drops the shop's entire stock onto the ground after calculating stacks.
     */
    public void dropStock() {
        if (getShopItem() == null) return;
        if (!isAdminShop() && getStock() <= 0) return;

        if (getOwnerUniqueId() != null) {
            OfflinePlayer owner = INSTANCE.getServer().getOfflinePlayer(getOwnerUniqueId());
            if (owner.isOnline() && owner.getPlayer() != null) {
                INSTANCE.getManager().giveItemStacks(owner.getPlayer(), getShopItem().clone(), getStock());
                return;
            }

            INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, () ->
                    INSTANCE.getManagementTask().createRecovery(owner.getUniqueId(), this));
        }
    }

    /**
     * Returns the shop's entire stored balance to the owner. If the owner does NOT exist, currency is sent to the passed player.
     */
    public void returnBalance() {
        if (getStoredBalance() <= 0) return;
        final OfflinePlayer ownerPlayer = (getOwnerUniqueId() != null) ? INSTANCE.getServer().getOfflinePlayer(getOwnerUniqueId()) : null;
        final boolean useVault = INSTANCE.getConfig().getBoolean("use-vault");
        final ItemStack currencyItem = useVault ? null : (INSTANCE.getConfig().getBoolean("shop-currency-item.force-use")
                ? INSTANCE.getManager().buildShopCurrencyItem(1) : (getTradeItem() == null
                ? INSTANCE.getManager().buildShopCurrencyItem(1) : getTradeItem()));

        if (!useVault) {
            if (getStoredBalance() == 1) {
                currencyItem.setAmount(1);
                Location baseLocation = getBaseLocation().asBukkitLocation();
                final World world = baseLocation.getWorld();
                if (world != null) world.dropItemNaturally(baseLocation, currencyItem);
                setStoredBalance(0);
                return;
            }

            Player playerToGive = ((ownerPlayer != null && ownerPlayer.isOnline() && ownerPlayer.getPlayer() != null) ? ownerPlayer.getPlayer() :
                    null);
            if (playerToGive == null) return;

            if (!playerToGive.isOnline()) {
                INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, () ->
                        INSTANCE.getManagementTask().createRecovery(playerToGive.getUniqueId(), this));
                return;
            }

            INSTANCE.getManager().giveItemStacks(playerToGive, currencyItem.clone(), (int) getStoredBalance());
        } else if (ownerPlayer != null) INSTANCE.getEconomyHandler().deposit(ownerPlayer, this, getStoredBalance());
        setStoredBalance(0);
    }

    /**
     * Gets the name of the shop's trade currency item (if NULL or "force-use" is enabled in the config.yml, the name of the default currency item will be used).
     *
     * @return The shop's currency itemstack name.
     */
    public String getTradeItemName() {
        if (!getCurrencyType().equals("item-for-item")) return "";

        final String defaultName = INSTANCE.getManager().getItemName(INSTANCE.getManager().defaultCurrencyItem);
        return (INSTANCE.getConfig().getBoolean("shop-currency-item.force-use") ? defaultName
                : ((getTradeItem() != null) ? INSTANCE.getManager().getItemName(getTradeItem()) : defaultName));
    }

    /**
     * Saves the shop to the database.
     *
     * @param async Whether it should be saved on the main thread or not.
     */
    public synchronized void save(boolean async) {
        if (!async) {
            save();
            return;
        }

        INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, (Runnable) this::save);
    }

    private synchronized void save() {
        if (getShopId() == null || getShopId().toString().isEmpty()) return;

        try {
            StringBuilder commands = new StringBuilder(), assistants = new StringBuilder(), limits = new StringBuilder();

            for (int i = -1; ++i < getCommands().size(); ) {
                commands.append(getCommands().get(i));
                if (i < getCommands().size()) commands.append(";");
            }

            for (int i = -1; ++i < getAssistants().size(); ) {
                assistants.append(getAssistants().get(i).toString());
                if (i < getAssistants().size()) assistants.append(";");
            }

            limits.append(getGlobalBuyLimit()).append(";").append(getGlobalBuyCounter()).append(";")
                    .append(getGlobalSellLimit()).append(";").append(getGlobalSellCounter()).append(";")
                    .append(getPlayerBuyLimit()).append(";").append(getPlayerSellLimit());

            String shopItem, tradeItem;
            shopItem = tradeItem = null;

            if (getShopItem() != null || getTradeItem() != null) {
                if (getShopItem() != null) {
                    final ItemStack cloneItem = getShopItem().clone();
                    shopItem = INSTANCE.toString(cloneItem);
                }

                if (getTradeItem() != null) {
                    final ItemStack cloneItem = getTradeItem().clone();
                    tradeItem = INSTANCE.toString(cloneItem);
                }
            }

            final String host = INSTANCE.getConfig().getString("mysql.host"), commandString = commands.toString().replace("'", "\\'").replace("\"",
                    "\\\""), extraDataLine = (canDynamicPriceChange() + ";" + getLastBuyTimeStamp() + ":" + getDynamicBuyCounter() + ";" + getLastSellTimeStamp()
                    + ":" + getDynamicSellCounter() + ";" + getCurrencyType()), syntax,

                    valuesString = (getShopId().toString() + "', '" + getBaseLocation().toString() + "',"
                            + " '" + (getOwnerUniqueId() != null ? getOwnerUniqueId().toString() : "") + "', '" + assistants + "', " + getBuyPrice(false)
                            + ", " + getSellPrice(false) + ", " + getStock() + ", '" + (shopItem != null ? shopItem : "") + "', '" + (tradeItem != null ? tradeItem : "")
                            + "', '" + limits + "', " + getShopItemAmount() + ", " + getStoredBalance() + ", '" + (isCommandOnlyMode() ? 1 : 0) +
                            "', '" + commandString + "', '"
                            + getChangeTimeStamp() + "', '" + (getDescription() == null ? "" : getDescription().replace("'", "").replace("\"", ""))
                            + "', '" + getStoredBaseBlockMaterial() + "', '" + extraDataLine.replace("'", "").replace("\"", ""));

            if (host == null || host.isEmpty())
                syntax = "INSERT OR REPLACE INTO shops(id, location, owner, assistants, buy_price, sell_price, stock, shop_item,"
                        + " trade_item, limits, shop_item_amount, balance, command_only_mode, commands, change_time_stamp,"
                        + " description, base_material, extra_data) VALUES('" + valuesString + "');";
            else
                syntax = "INSERT INTO shops(id, location, owner, assistants, buy_price, sell_price, stock, shop_item, trade_item, limits, " +
                        "shop_item_amount, "
                        + " balance, command_only_mode, commands, change_time_stamp, description, base_material, extra_data) VALUES( '" + valuesString + "')"
                        + " ON DUPLICATE KEY UPDATE id = '" + getShopId().toString() + "', location = '" + getBaseLocation().toString() + "', owner" +
                        " = '" + (getOwnerUniqueId() != null
                        ? getOwnerUniqueId().toString() : "") + "', buy_price = '" + getBuyPrice(false) + "', " + "sell_price = '" + getSellPrice(false)
                        + "', stock = '" + getStock() + "', shop_item = '" + (shopItem != null ? shopItem : "") + "', trade_item = '" + (tradeItem != null ? tradeItem : "")
                        + "', limits = '" + limits + "', shop_item_amount = '" + getShopItemAmount() + "', balance = '" + getStoredBalance() + "', " +
                        "command_only_mode = '"
                        + (isCommandOnlyMode() ? 1 : 0) + "', commands = '" + commandString + "'," + " change_time_stamp = '" + getChangeTimeStamp() + "', description = '"
                        + (getDescription() == null ? "" : getDescription().replace("'", "").replace("\"", "")) + "', base_material = '"
                        + getStoredBaseBlockMaterial() + "', extra_data = '" + extraDataLine + "';";

            Statement statement = INSTANCE.getDatabaseConnection().createStatement();
            statement.executeUpdate(syntax);
            statement.close();
        } catch (Exception e) {INSTANCE.log(Level.WARNING, "There was an issue saving the shop '" + getShopId().toString() + "' to the database (" + e.getMessage() + ").");}
    }

    /**
     * Runs all commands given to the shop.
     *
     * @param player the player used inside the commands.
     * @param amount The amount for the {amount} placeholder.
     */
    public void runCommands(@NotNull Player player, int amount) {
        if (getCommands().isEmpty()) return;

        for (int i = -1; ++i < getCommands().size(); ) {
            String commandLine = getCommands().get(i), command = commandLine.replaceAll("(?i):PLAYER", "")
                    .replaceAll("(?i):CONSOLE", "");
            if (commandLine.toUpperCase().endsWith(":PLAYER"))
                INSTANCE.getServer().dispatchCommand(player, command.replace("{player}", player.getName())
                        .replace("{amount}", String.valueOf(amount)));
            else
                INSTANCE.getServer().dispatchCommand(INSTANCE.getServer().getConsoleSender(),
                        command.replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount)));
        }
    }

    /**
     * Attempts to teleport the passed player to this shop.
     *
     * @param player The player to teleport.
     * @param charge Whether the player should be charged.
     */
    public void visit(@NotNull Player player, boolean charge) {
        if (getBaseLocation() == null) {
            String message = INSTANCE.getLangConfig().getString("shop-unsafe-location");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message.replace("{id}", getShopId().toString()));
            return;
        }

        final ShopVisitEvent shopVisitEvent = new ShopVisitEvent(player, this, (!player.hasPermission("displayshops.admin") && !charge)
                ? INSTANCE.getConfig().getDouble("visit-charge") : 0);
        INSTANCE.getServer().getPluginManager().callEvent(shopVisitEvent);
        if (shopVisitEvent.isCancelled()) return;

        if (shopVisitEvent.getChargeAmount() > 0) {
            final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, this, EconomyCallType.VISIT, shopVisitEvent.getChargeAmount());
            if (economyCallEvent.failed()) {
                player.closeInventory();
                INSTANCE.getManager().getDataPack(player).resetEditData();
                return;
            }

            String message = INSTANCE.getLangConfig().getString("shop-visit-charge");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message.replace("{charge}",
                        INSTANCE.getEconomyHandler().format(this, getCurrencyType(), shopVisitEvent.getChargeAmount())));
        }

        Location safeLocation = null, baseLocation = getBaseLocation().asBukkitLocation();
        final Block block = baseLocation.getBlock();

        if (DisplayShops.getPluginInstance().getServerVersion() >= 1.13) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Directional) {
                org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) block.getBlockData();
                if (directional.getFacing() != BlockFace.UP) {
                    final Block frontBlock = block.getRelative(directional.getFacing()), downBlock = frontBlock.getRelative(BlockFace.DOWN);
                    if (isBlockSafe(frontBlock, downBlock)) {
                        safeLocation = frontBlock.getLocation().clone();

                        switch (directional.getFacing()) {
                            case WEST: {
                                safeLocation.setYaw(-90);
                                break;
                            }
                            case EAST: {
                                safeLocation.setYaw(90);
                                break;
                            }
                            case SOUTH: {
                                safeLocation.setYaw(-180);
                                break;
                            }
                            default: {
                                safeLocation.setYaw(0);
                                break;
                            }
                        }

                        safeLocation.setPitch(0);
                    }
                }
            }
        }

        if (safeLocation == null) {
            final String[] coords = {"0,-2,0", "-2,0,-90", "0,2,180", "2,0,90"};
            for (int i = -1; ++i < 4; ) {
                String[] split = coords[i].split(",");
                int x = Integer.parseInt(split[0]), z = Integer.parseInt(split[1]), yaw = Integer.parseInt(split[2]);

                Block newBlock = baseLocation.getBlock().getRelative(x, 0, z), downBlock = newBlock.getRelative(BlockFace.DOWN);
                if (isBlockSafe(newBlock, downBlock)) {
                    safeLocation = newBlock.getLocation().clone();
                    safeLocation.setYaw(yaw);
                    safeLocation.setPitch(0);
                    break;
                }
            }
        }

        if (safeLocation == null) {
            String message = INSTANCE.getLangConfig().getString("shop-unsafe-location");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message.replace("{id}", getShopId().toString()));
            return;
        }

        final Location finalSafeLocation = safeLocation;
        String message = INSTANCE.getLangConfig().getString("shop-visit-delay");
        if (message != null && !message.equalsIgnoreCase(""))
            INSTANCE.getManager().sendMessage(player, message.replace("{delay}",
                            INSTANCE.getManager().formatNumber(INSTANCE.getConfig().getInt("visit-delay"), false))
                    .replace("{x}", INSTANCE.getManager().formatNumber(getBaseLocation().getX(), false))
                    .replace("{y}", INSTANCE.getManager().formatNumber(getBaseLocation().getY(), false))
                    .replace("{z}", INSTANCE.getManager().formatNumber(getBaseLocation().getZ(), false))
                    .replace("{id}", getShopId().toString()));

        if (!INSTANCE.getTeleportingPlayers().contains(player.getUniqueId()))
            INSTANCE.getTeleportingPlayers().add(player.getUniqueId());
        INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () -> {
            if (!INSTANCE.getTeleportingPlayers().contains(player.getUniqueId())) return;
            INSTANCE.getTeleportingPlayers().remove(player.getUniqueId());

            String soundName = INSTANCE.getConfig().getString("immersion-section.shop-visit-sound"),
                    particleName = INSTANCE.getConfig().getString("immersion-section.shop-visit-particle");
            if (soundName != null)
                soundName = soundName.toUpperCase().replace(" ", "_").replace("-", "_");
            if (particleName != null)
                particleName = particleName.toUpperCase().replace(" ", "_").replace("-", "_");

            if (soundName != null && !soundName.equalsIgnoreCase(""))
                player.getWorld().playSound(player.getLocation().add(0, 1, 0), Sound.valueOf(soundName), 1, 1);

            if (particleName != null && !particleName.equalsIgnoreCase(""))
                INSTANCE.displayParticle(player, particleName, player.getLocation().add(0, 1, 0),
                        1, 1, 1, 0, 10);

            player.teleport(finalSafeLocation.add(0.5, 1, 0.5));

            if (particleName != null && !particleName.equalsIgnoreCase(""))
                INSTANCE.displayParticle(player, particleName, player.getLocation().add(0, 1, 0),
                        1, 1, 1, 0, 10);

            if (soundName != null && !soundName.equalsIgnoreCase(""))
                player.getWorld().playSound(player.getLocation().add(0, 1, 0), Sound.valueOf(soundName), 1, 1);

            String visitMessage = INSTANCE.getLangConfig().getString("shop-visit");
            if (visitMessage != null && !visitMessage.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, visitMessage.replace("{id}", getShopId().toString()));

            if (!player.hasPermission("displayshops.bypass") && getOwnerUniqueId() != null && !player.getUniqueId().toString().equals(getOwnerUniqueId().toString())) {
                Player owner = INSTANCE.getServer().getPlayer(getOwnerUniqueId());
                if (owner != null && owner.isOnline()) {
                    String visitedMessage = INSTANCE.getLangConfig().getString("shop-visited");
                    if (visitedMessage != null && !visitedMessage.equalsIgnoreCase(""))
                        INSTANCE.getManager().sendMessage(owner, visitedMessage.replace("{player}", player.getName())
                                .replace("{x}", INSTANCE.getManager().formatNumber(getBaseLocation().getX(), false))
                                .replace("{y}", INSTANCE.getManager().formatNumber(getBaseLocation().getY(), false))
                                .replace("{z}", INSTANCE.getManager().formatNumber(getBaseLocation().getZ(), false)));
                }
            }
        }, 20L * INSTANCE.getConfig().getInt("visit-delay"));
    }

    private boolean isBlockSafe(@NotNull Block block, @NotNull Block downBlock) {
        return ((block.getType().name().contains("AIR") || block.getType().name().contains("SNOW"))
                && (block.getType().name().contains("AIR") || block.getType().name().contains("SNOW"))
                && block.getRelative(BlockFace.UP).getType().name().contains("AIR")
                && block.getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType().name().contains("AIR")
                && !(downBlock.getType().name().contains("LAVA") && downBlock.getType().name().contains("WATER") && downBlock.getType().name().contains("AIR")
                && downBlock.getType().name().contains("WEB") && downBlock.getType().name().contains("PISTON") && downBlock.getType().name().contains("MAGMA")));
    }

    /**
     * Deletes the shop from the database if found.
     *
     * @param async Whether the shop should be deleted on the main thread or not.
     */
    public synchronized void delete(boolean async) {
        if (async) INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, (Runnable) this::delete);
        else delete();
    }

    private synchronized void delete() {
        try {
            Statement statement = INSTANCE.getDatabaseConnection().createStatement();
            statement.executeUpdate("delete from shops where id = '" + getShopId() + "'");
            statement.close();
        } catch (SQLException e) {INSTANCE.log(Level.WARNING, "There was an issue deleting the shop " + getShopId() + " from the database (" + e.getMessage() + ").");}
    }

    /**
     * Registers the shop into the manager.
     */
    public void register() {
        INSTANCE.getManager().getShopMap().put(getShopId(), this);
    }

    /**
     * Unregisters the shop from the manager.
     */
    public void unRegister() {
        INSTANCE.getManager().getShopMap().remove(getShopId());
    }

    /**
     * Gets if a shop is an admin shop or not.
     *
     * @return Whether the shop is admin or not.
     */
    public boolean isAdminShop() {
        return getOwnerUniqueId() == null;
    }

    /**
     * Sets the owner to null to initiate admin shop mode.
     */
    public void makeAdminShop() {
        setOwnerUniqueId(null);
    }

    /**
     * Updates the shop change time stamp (Used for the purge system).
     */
    public void updateTimeStamp() {
        setChangeTimeStamp(System.currentTimeMillis());
    }

    /**
     * Updates the shop buy or sell time stamp (Used for the dynamic price changing system).
     * (Note: Only use BUY or SELL as the economy call type. Defaults to BUY if invalid or improper.)
     */
    public void updateTransactionTimeStamp(@NotNull EconomyCallType economyCallType) {
        if (economyCallType == EconomyCallType.SELL) setLastSellTimeStamp(System.currentTimeMillis());
        else setLastBuyTimeStamp(System.currentTimeMillis());
    }

    /**
     * Checks if the shop is ready to be purged.
     *
     * @param purgeDuration The duration a shop must be untouched.
     * @return Whether it is ready.
     */
    public boolean isReadyForPurge(int purgeDuration) {
        return (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getChangeTimeStamp()) >= purgeDuration);
    }

    /**
     * Checks if the shop is ready to have its dynamic price counter reset for either BUY or SELL.
     *
     * @param resetDuration   The duration a shop must go without a transaction in the BUY or SELL field.
     * @param economyCallType The type to check readiness for (Note: ONLY use BUY or SELL, by default BUY will be used).
     * @return Whether it is ready.
     */
    public boolean isReadyForDynamicReset(int resetDuration, @NotNull EconomyCallType economyCallType) {
        return ((economyCallType == EconomyCallType.BUY && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getLastBuyTimeStamp()) >= resetDuration)
                || (economyCallType == EconomyCallType.SELL && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getLastSellTimeStamp()) >= resetDuration));
    }

    /**
     * Deletes the shop's data, deletes the base-block, drops the stock, destroys the display packet, and unregisters the object.
     *
     * @param player If provided, some additional effects will be displayed.
     * @param async  Whether the file deletion is async or not.
     */
    public void purge(@Nullable Player player, boolean async) {
        unRegister();

        for (Player p : INSTANCE.getServer().getOnlinePlayers()) {
            kill(p);
            final String invName = INSTANCE.getMenuListener().getInventoryName(p.getOpenInventory().getTopInventory(), p.getOpenInventory());
            if (invName != null && !invName.isEmpty() && INSTANCE.matchesAnyMenu(invName)) p.closeInventory();
        }

        getBaseLocation().asBukkitLocation().getBlock().setType(Material.AIR);
        dropStock();
        returnBalance();
        delete(async);

        if (player != null) {
            if (INSTANCE.getConfig().getBoolean("creation-item-drop")) {
                if (player.getInventory().firstEmpty() == -1)
                    player.getWorld().dropItemNaturally(player.getLocation(), INSTANCE.getManager().buildShopCreationItem(player, 1));
                else player.getInventory().addItem(INSTANCE.getManager().buildShopCreationItem(player, 1));
            }

            Location location = getBaseLocation().asBukkitLocation();
            String soundString = INSTANCE.getConfig().getString("immersion-section.shop-delete-sound");
            if (soundString != null && !soundString.equalsIgnoreCase(""))
                player.playSound(location, Sound.valueOf(soundString), 1, 1);

            String particleString = INSTANCE.getConfig().getString("immersion-section.shop-delete-particle");
            if (particleString != null && !particleString.equalsIgnoreCase(""))
                INSTANCE.displayParticle(player, particleString.toUpperCase().replace(" ", "_")
                        .replace("-", "_"), location.add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, 0, 8);

            String message = INSTANCE.getLangConfig().getString("shop-deleted");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message);

            INSTANCE.runEventCommands("shop-delete", player);
        }
    }

    /**
     * Gets the dynamic price that will be added/subtracted to the buy/sell price.
     *
     * @param originalPrice The original price.
     * @param isBuy         Determines whether the result will use buy or sell values.
     * @return The dynamic value that will
     */
    public double getDynamicPriceValue(double originalPrice, boolean isBuy) {
        final String prefix = (isBuy ? "buy" : "sell");
        return (!canDynamicPriceChange() ? 0 : ((((isBuy ? getDynamicBuyCounter() : getDynamicSellCounter()) / INSTANCE.getConfig().getDouble(
                "dynamic-" + prefix + "-increment"))
                * INSTANCE.getConfig().getDouble("dynamic-" + prefix + "-percentage")) * originalPrice));
    }

    /**
     * Checks to see if the player is able to edit the shop.
     *
     * @param player The player to check access for.
     * @return Whether the player can or can NOT edit the shop.
     */
    public boolean canEdit(@NotNull Player player) {
        return (player.hasPermission("displayshops.adminedit")
                || (player.hasPermission("displayshops.edit") && ((getOwnerUniqueId() != null && getOwnerUniqueId().toString().equals(player.getUniqueId().toString()))
                || (!getAssistants().isEmpty() && getAssistants().contains(player.getUniqueId())))));
    }

    /**
     * Checks to see if the editor is actually editing the shop, if not the current editor data is cleared.
     *
     * @param player Player to check against.
     */
    public void checkCurrentEditor(@Nullable Player player) {

        if (!DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention")) return;

        if (getCurrentEditor() != null) {
            if (player != null && player.getUniqueId().toString().equals(getCurrentEditor().toString())) return;

            OfflinePlayer offlinePlayer = DisplayShops.getPluginInstance().getServer().getOfflinePlayer(getCurrentEditor());
            if (!offlinePlayer.isOnline() || (offlinePlayer.isOnline() && offlinePlayer.getPlayer() == null)) {
                setCurrentEditor(null);
                return;
            }

            final Player op = offlinePlayer.getPlayer();
            if (op == null || op.getOpenInventory().getTopInventory().getType() != InventoryType.ANVIL
                    && DisplayShops.getPluginInstance().matchesAnyMenu(DisplayShops.getPluginInstance().getMenuListener()
                    .getInventoryName(op.getOpenInventory().getTopInventory(), op.getOpenInventory()))) {
                setCurrentEditor(null);
                final DataPack dataPack = DisplayShops.getPluginInstance().getManager().getDataPack(op);
                dataPack.resetEditData();
            }
        }
    }

    /**
     * Resets the shop entirely. The time stamp is also updated.
     */
    public void reset() {
        checkCurrencyType(null);

        setStock(0);
        setStoredBalance(0);
        setShopItemAmount(1);
        setDynamicBuyCounter(0);
        setDynamicSellCounter(0);
        setGlobalBuyLimit(-1);
        setGlobalSellLimit(-1);
        setGlobalBuyCounter(0);
        setGlobalSellCounter(0);
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        setDescription("");

        if (getCommands() != null) getCommands().clear();
        else setCommands(new ArrayList<>());

        if (getAssistants() != null) getAssistants().clear();
        else setAssistants(new ArrayList<>());

        setOwnerUniqueId(null);
        setShopItem(null);
        setTradeItem(null);

        updateTimeStamp();

        if (INSTANCE.getInSightTask() != null) INSTANCE.getInSightTask().refreshShop(this);
    }

    public void checkCurrencyType(@Nullable Player player) {
        if (getCurrencyType() != null) {
            EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(getCurrencyType());
            if (ecoHook != null && (player == null || INSTANCE.getEconomyHandler().canUseCurrency(player, getCurrencyType()))) return;
        }

        String defaultEcoCurrency = INSTANCE.getConfig().getString("default-currency-type");
        if (defaultEcoCurrency != null) {
            EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(defaultEcoCurrency);
            if (ecoHook != null) {
                setCurrencyType(defaultEcoCurrency);
                save(true);
                return;
            }
        }

        setCurrencyType("item-for-item");
        save(true);
    }

    // integer list getters & setters.
    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    /**
     * Obtains the balance of the shop which is used to purchase and store currency collected from invetors.
     *
     * @return The balance being held (currency).
     */
    public double getStoredBalance() {
        return storedBalance.doubleValue();
    }

    /**
     * Sets the balance of a shop. This balance is used to purchase and store currency collected from investors.
     *
     * @param amount The amount to set as the stored balance.
     */
    public void setStoredBalance(double amount) {
        this.storedBalance = new BigDecimal(amount);
    }

    public int getShopItemAmount() {
        return shopItemAmount;
    }

    public void setShopItemAmount(int shopItemAmount) {
        this.shopItemAmount = shopItemAmount;
    }

    // dynamic price changing getters & setters.
    public boolean canDynamicPriceChange() {
        return dynamicPriceChange;
    }

    public void setDynamicPriceChange(boolean dynamicPriceChange) {
        this.dynamicPriceChange = dynamicPriceChange;
    }

    public int getDynamicBuyCounter() {
        return dynamicBuyPriceCounter;
    }

    public void setDynamicBuyCounter(int dynamicBuyPriceCounter) {
        this.dynamicBuyPriceCounter = dynamicBuyPriceCounter;
    }

    public int getDynamicSellCounter() {
        return dynamicSellPriceCounter;
    }

    public void setDynamicSellCounter(int dynamicSellPriceCounter) {
        this.dynamicSellPriceCounter = dynamicSellPriceCounter;
    }

    // general getters & setters.

    public ItemStack getShopItem() {
        return shopItem;
    }

    public void setShopItem(ItemStack shopItem) {
        this.shopItem = shopItem;
    }

    /**
     * Gets the shop's unit buy price.
     *
     * @param useDynamicPricing Whether to apply dynamic price values to the base value or not.
     * @return The total result.
     */
    public double getBuyPrice(boolean useDynamicPricing) {
        return (buyPrice.doubleValue() + ((useDynamicPricing && INSTANCE.getConfig().getBoolean("dynamic-prices"))
                ? getDynamicPriceValue(buyPrice.doubleValue(), true) : 0));
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = new BigDecimal(buyPrice);
    }

    /**
     * Gets the shop's unit sell price.
     *
     * @param useDynamicPricing Whether to apply dynamic price values to the base value or not.
     * @return The total result.
     */
    public double getSellPrice(boolean useDynamicPricing) {
        return (sellPrice.doubleValue() - ((useDynamicPricing && INSTANCE.getConfig().getBoolean("dynamic-prices"))
                ? getDynamicPriceValue(sellPrice.doubleValue(), false) : 0));
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = new BigDecimal(sellPrice);
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public void setOwnerUniqueId(UUID ownerUniqueId) {
        this.ownerUniqueId = ownerUniqueId;
    }

    public LocationClone getBaseLocation() {
        return baseLocation;
    }

    public void setBaseLocation(@NotNull LocationClone baseLocation) {
        this.baseLocation = baseLocation;
    }

    public UUID getShopId() {
        return shopId;
    }

    public void setShopId(@NotNull UUID shopId) {
        this.shopId = shopId;
    }

    public boolean isCommandOnlyMode() {
        return commandOnlyMode;
    }

    public void setCommandOnlyMode(boolean commandOnlyMode) {
        this.commandOnlyMode = commandOnlyMode;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(@NotNull List<String> commands) {
        this.commands = commands;
    }

    public ItemStack getTradeItem() {
        return tradeItem;
    }

    public void setTradeItem(ItemStack tradeItem) {
        this.tradeItem = tradeItem;
    }

    public long getChangeTimeStamp() {
        return changeTimeStamp;
    }

    public void setChangeTimeStamp(long changeTimeStamp) {
        this.changeTimeStamp = changeTimeStamp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStoredBaseBlockMaterial() {
        return storedBaseBlockMaterial;
    }

    public void setStoredBaseBlockMaterial(@NotNull String storedBaseBlockMaterial) {
        this.storedBaseBlockMaterial = (!storedBaseBlockMaterial.contains(":") ? (storedBaseBlockMaterial + ":0") : storedBaseBlockMaterial);
    }

    public long getLastBuyTimeStamp() {
        return lastBuyTimeStamp;
    }

    public void setLastBuyTimeStamp(long lastBuyTimeStamp) {
        this.lastBuyTimeStamp = lastBuyTimeStamp;
    }

    public long getLastSellTimeStamp() {
        return lastSellTimeStamp;
    }

    public void setLastSellTimeStamp(long lastSellTimeStamp) {
        this.lastSellTimeStamp = lastSellTimeStamp;
    }

    public List<UUID> getAssistants() {
        return assistants;
    }

    private void setAssistants(List<UUID> assistants) {
        this.assistants = assistants;
    }

    /**
     * Gets  the player unique ID of whom is currently editing the shop.
     *
     * @return The current editor's unique ID.
     */
    public UUID getCurrentEditor() {
        return currentEditor;
    }

    /**
     * Sets the current editor.
     *
     * @param currentEditor The unique ID of the player editing.
     */
    public void setCurrentEditor(UUID currentEditor) {
        this.currentEditor = currentEditor;
    }

    public int getPlayerBuyLimit() {
        return playerBuyLimit;
    }

    public void setPlayerBuyLimit(int playerBuyLimit) {
        this.playerBuyLimit = playerBuyLimit;
    }

    public int getPlayerSellLimit() {
        return playerSellLimit;
    }

    public void setPlayerSellLimit(int playerSellLimit) {
        this.playerSellLimit = playerSellLimit;
    }

    public int getGlobalBuyLimit() {
        return globalBuyLimit;
    }

    public void setGlobalBuyLimit(int globalBuyLimit) {
        this.globalBuyLimit = globalBuyLimit;
    }

    public int getGlobalSellLimit() {
        return globalSellLimit;
    }

    public void setGlobalSellLimit(int globalSellLimit) {
        this.globalSellLimit = globalSellLimit;
    }

    public int getGlobalBuyCounter() {
        return globalBuyCounter;
    }

    public void setGlobalBuyCounter(int globalBuyCounter) {
        this.globalBuyCounter = globalBuyCounter;
    }

    public int getGlobalSellCounter() {
        return globalSellCounter;
    }

    public void setGlobalSellCounter(int globalSellCounter) {
        this.globalSellCounter = globalSellCounter;
    }

    public String getCurrencyType() {return currencyType;}

    public void setCurrencyType(@NotNull String currencyType) {this.currencyType = currencyType;}

}