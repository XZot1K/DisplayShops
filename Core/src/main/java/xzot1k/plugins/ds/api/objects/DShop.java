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
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.events.ShopVisitEvent;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.exceptions.DisplayFailException;

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
    private int[] integerValues;
    private long changeTimeStamp, lastBuyTimeStamp, lastSellTimeStamp;
    private String description, storedBaseBlockMaterial;
    private BigDecimal buyPrice, sellPrice, storedBalance;
    private boolean commandOnlyMode, dynamicPriceChange;
    private List<String> commands;
    private List<UUID> assistants;
    private LocationClone baseLocation;

    public DShop(UUID shopId, UUID ownerUniqueId, LocationClone baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(baseLocation);
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setIntegerValues(new int[]{0, 0, 0, 0, 0, shopItemAmount, 0, 0});
        setStoredBalance(0);
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        setCommands(new ArrayList<>());
        setAssistants(new ArrayList<>());
        setDescription("");
        updateTimeStamp();
    }

    public DShop(UUID shopId, UUID ownerUniqueId, Location baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(new LClone(baseLocation));
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setIntegerValues(new int[]{0, 0, 0, 0, 0, shopItemAmount, 0, 0});
        setStoredBalance(0);
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        setCommands(new ArrayList<>());
        setAssistants(new ArrayList<>());
        setDescription("");
        updateTimeStamp();
    }

    public DShop(UUID shopId, UUID ownerUniqueId, ItemStack shopItem, Location baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(new LClone(baseLocation));
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setIntegerValues(new int[]{0, 0, 0, 0, 0, shopItemAmount, 0, 0});
        setShopItem(shopItem);
        setShopItemAmount(shopItemAmount);
        setStoredBalance(0);
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        setCommands(new ArrayList<>());
        setAssistants(new ArrayList<>());
        setDescription("");
        updateTimeStamp();
    }

    public DShop(UUID shopId, UUID ownerUniqueId, ItemStack shopItem, LocationClone baseLocation, int shopItemAmount, String storedBaseBlockMaterial) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        setShopId(shopId);
        setOwnerUniqueId(ownerUniqueId);
        setBaseLocation(baseLocation);
        setStoredBaseBlockMaterial(storedBaseBlockMaterial);
        setIntegerValues(new int[]{0, 0, 0, 0, 0, shopItemAmount, 0, 0});
        setShopItem(shopItem);
        setShopItemAmount(shopItemAmount);
        setStoredBalance(0);
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        setCommands(new ArrayList<>());
        setAssistants(new ArrayList<>());
        setDescription("");
        updateTimeStamp();
    }

    /**
     * Kills the shop's display packets entirely.
     *
     * @param player The player to send the destroy packets to.
     */
    public synchronized void kill(Player player) {
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
    public synchronized void display(Player player, boolean showHolograms) {
        kill(player);

        if (getBaseLocation() == null) {
            INSTANCE.log(Level.WARNING, "The shop '" + getShopId() + "' is having some issues with its base location (Returning Null).");
            return;
        }

        World world = INSTANCE.getServer().getWorld(getBaseLocation().getWorldName());
        if (world == null) {
            INSTANCE.log(Level.WARNING, "The shop \"" + getShopId() + "\" is having some issues with the world (" + getBaseLocation().getWorldName() + ").");
            return;
        }

        if (!world.isChunkLoaded(((int) getBaseLocation().getX() >> 4), ((int) getBaseLocation().getZ() >> 4))) return;

        try {
            DisplayPacket displayPacket;
            if (INSTANCE.getServerVersion() == 1_19.3)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_19_R3.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_19.2)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_19_R2.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_19.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_19_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_18.2)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_18_R2.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_18.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_18_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_17.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_17_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_16.3)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_16_R3.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_16.2)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_16_R2.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_16.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_16_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_15.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_15_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_14.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_14_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_13.2)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_13_R2.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_13.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_13_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_12.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_12_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_11.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_11_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_10.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_10_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_9.2)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_9_R2.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_9.1)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_9_R1.DPacket(INSTANCE, player, this, showHolograms);
            else if (INSTANCE.getServerVersion() == 1_8.3)
                displayPacket = new xzot1k.plugins.ds.core.packets.v1_8_R3.DPacket(INSTANCE, player, this, showHolograms);
            else throw new DisplayFailException((Math.floor(INSTANCE.getServerVersion()) / 100)
                        + " packets were unable to be found. This spigot/bukkit version is NOT supported.");

            INSTANCE.updateDisplayPacket(this, player, displayPacket);
        } catch (DisplayFailException e) {INSTANCE.log(Level.WARNING, e.getMessage());}
    }

    /**
     * Checks if player is in range of this shop.
     *
     * @param player   The player to check.
     * @param distance The distance between the player and the shop.
     * @return Whether the player is in range or not.
     */
    public boolean isInRange(Player player, double distance) {
        return player != null && getBaseLocation() != null && getBaseLocation().getWorldName().equalsIgnoreCase(player.getWorld().getName())
                && getBaseLocation().distance(player.getLocation(), true) < distance;
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

            INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, () -> {
                RecoveryData recoveryData = new RecoveryData(owner.getUniqueId());
                RecoveryData.updateRecovery(owner.getUniqueId(), recoveryData);
            });
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
                baseLocation.getWorld().dropItemNaturally(baseLocation, currencyItem);
                setStoredBalance(0);
                return;
            }

            Player playerToGive = ((ownerPlayer != null && ownerPlayer.isOnline() && ownerPlayer.getPlayer() != null) ? ownerPlayer.getPlayer() : null);
            if (playerToGive == null) return;

            if (!playerToGive.isOnline()) {
                INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE, () -> {
                    RecoveryData recoveryData = new RecoveryData(playerToGive.getUniqueId());
                    RecoveryData.updateRecovery(playerToGive.getUniqueId(), recoveryData);
                });
                return;
            }

            INSTANCE.getManager().giveItemStacks(playerToGive, currencyItem.clone(), (int) getStoredBalance());
        } else INSTANCE.getVaultEconomy().depositPlayer(ownerPlayer, getStoredBalance());
        setStoredBalance(0);
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
            StringBuilder commands = new StringBuilder(), assistants = new StringBuilder();
            int index = 0;
            for (String command : getCommands()) {
                commands.append(command);
                if (index < getCommands().size()) commands.append(";");
                index++;
            }

            index = 0;
            for (UUID playerUniqueId : getAssistants()) {
                assistants.append(playerUniqueId.toString());
                if (index < getAssistants().size()) assistants.append(";");
                index++;
            }

            String shopItem, tradeItem;
            shopItem = tradeItem = null;

            if (getShopItem() != null || getTradeItem() != null) {
                if (getShopItem() != null) {
                    final ItemStack cloneItem = getShopItem().clone();
                    shopItem = INSTANCE.getPacketManager().toString(cloneItem);
                }

                if (getTradeItem() != null) {
                    final ItemStack cloneItem = getTradeItem().clone();
                    tradeItem = INSTANCE.getPacketManager().toString(cloneItem);
                }
            }

            final String host = INSTANCE.getConfig().getString("mysql.host"), commandString = commands.toString().replace("'", "\\'").replace("\"", "\\\""),
                    extraDataLine = (canDynamicPriceChange() + ";" + getLastBuyTimeStamp() + ":" + getDynamicBuyCounter() + ";" + getLastSellTimeStamp() + ":" + getDynamicSellCounter()), syntax;
            if (host == null || host.isEmpty())
                syntax = "INSERT OR REPLACE INTO shops(id, location, owner, assistants, buy_price, sell_price, stock, shop_item,"
                        + " trade_item, buy_limit, sell_limit, shop_item_amount, buy_counter, sell_counter, balance, command_only_mode, commands,"
                        + " change_time_stamp, description, base_material, extra_data) VALUES('" + getShopId().toString() + "', '" + getBaseLocation().toString() + "',"
                        + " '" + (getOwnerUniqueId() != null ? getOwnerUniqueId().toString() : "") + "', '" + assistants + "', " + getBuyPrice(false)
                        + ", " + getSellPrice(false) + ", " + getStock() + ", '" + (shopItem != null ? shopItem : "") + "', '" + (tradeItem != null ? tradeItem : "")
                        + "', " + getBuyLimit() + ", " + getSellLimit() + ", " + getShopItemAmount() + ", " + getBuyCounter() + ", " + getSellCounter() + ", " + getStoredBalance()
                        + ", '" + (isCommandOnlyMode() ? 1 : 0) + "', '" + commandString + "', '" + getChangeTimeStamp() + "', '" + (getDescription() == null ? ""
                        : getDescription().replace("'", "").replace("\"", "")) + "', '" + getStoredBaseBlockMaterial() + "', '"
                        + extraDataLine.replace("'", "").replace("\"", "") + "');";
            else
                syntax = "INSERT INTO shops(id, location, owner, assistants, buy_price, sell_price, stock, shop_item, trade_item, buy_limit, sell_limit, shop_item_amount, "
                        + "buy_counter, sell_counter, balance, command_only_mode, commands, change_time_stamp, description, base_material, extra_data) VALUES( '"
                        + getShopId().toString() + "', '" + getBaseLocation().toString() + "'," + " '" + (getOwnerUniqueId() != null ? getOwnerUniqueId().toString() : "")
                        + "', '" + assistants + "', " + getBuyPrice(false) + ", " + getSellPrice(false) + ", " + getStock() + ", '"
                        + (shopItem != null ? shopItem : "") + "', '" + (tradeItem != null ? tradeItem : "") + "', " + getBuyLimit() + ", " + getSellLimit() + ", "
                        + getShopItemAmount() + ", " + getBuyCounter() + ", " + getSellCounter() + ", " + getStoredBalance() + ", '" + (isCommandOnlyMode() ? 1 : 0) + "', "
                        + "'" + commandString + "', '" + getChangeTimeStamp() + "', " + "'" + getDescription().replace("'", "").replace("\"", "")
                        + "', '" + getStoredBaseBlockMaterial() + "', '" + extraDataLine.replace("'", "").replace("\"", "") + "') ON DUPLICATE "
                        + "KEY UPDATE id = '" + getShopId().toString() + "', location = '" + getBaseLocation().toString() + "', owner = '" + (getOwnerUniqueId() != null
                        ? getOwnerUniqueId().toString() : "") + "', buy_price = '" + getBuyPrice(false) + "', " + "sell_price = '" + getSellPrice(false)
                        + "', stock = '" + getStock() + "', shop_item = '" + (shopItem != null ? shopItem : "") + "', trade_item = '" + (tradeItem != null ? tradeItem : "")
                        + "', buy_limit = '" + getBuyLimit() + "', sell_limit = '" + getSellLimit() + "', shop_item_amount = '" + getShopItemAmount() + "', buy_counter = '"
                        + getBuyCounter() + "', sell_counter = '" + getSellCounter() + "', balance = '" + getStoredBalance() + "', command_only_mode = '"
                        + (isCommandOnlyMode() ? 1 : 0) + "', commands = '" + commandString + "'," + " change_time_stamp = '" + getChangeTimeStamp() + "', description = '"
                        + (getDescription() == null ? "" : getDescription().replace("'", "").replace("\"", "")) + "', base_material = '"
                        + getStoredBaseBlockMaterial() + "', extra_data = '" + extraDataLine + "';";

            Statement statement = INSTANCE.getDatabaseConnection().createStatement();
            statement.executeUpdate(syntax);
            statement.close();
        } catch (Exception e) {
            e.printStackTrace();
            INSTANCE.log(Level.WARNING, "There was an issue saving the shop '" + getShopId().toString() + "' to the database (" + e.getMessage() + ").");
        }
    }

    /**
     * Runs all commands given to the shop.
     *
     * @param player the player used inside the commands.
     * @param amount The amount for the {amount} placeholder.
     */
    public void runCommands(Player player, int amount) {
        if (getCommands().size() > 0)
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
    public void visit(Player player, boolean charge) {
        if (getBaseLocation() == null) {
            String message = INSTANCE.getLangConfig().getString("shop-unsafe-location");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message.replace("{id}", getShopId().toString()));
            return;
        }

        ShopVisitEvent shopVisitEvent = new ShopVisitEvent(player, this, (!player.hasPermission("displayshops.admin") && !charge)
                ? INSTANCE.getConfig().getDouble("visit-charge") : 0);
        INSTANCE.getServer().getPluginManager().callEvent(shopVisitEvent);
        if (shopVisitEvent.isCancelled()) return;

        if (shopVisitEvent.getChargeAmount() > 0) {
            final boolean useOwnerSyncing = INSTANCE.getConfig().getBoolean("sync-owner-balance"),
                    isSyncing = (useOwnerSyncing && getOwnerUniqueId() != null),
                    useVault = INSTANCE.getConfig().getBoolean("use-vault");
            if (useVault) {
                if (!INSTANCE.getVaultEconomy().has(player, shopVisitEvent.getChargeAmount())) {
                    String message = INSTANCE.getLangConfig().getString("insufficient-funds");
                    if (message != null && !message.equalsIgnoreCase(""))
                        INSTANCE.getManager().sendMessage(player, message.replace("{price}",
                                INSTANCE.getManager().formatNumber(shopVisitEvent.getChargeAmount(), true)));
                    return;
                }

                INSTANCE.getVaultEconomy().withdrawPlayer(player, shopVisitEvent.getChargeAmount());

                if (isSyncing) {
                    OfflinePlayer owner = INSTANCE.getServer().getOfflinePlayer(getOwnerUniqueId());
                    INSTANCE.getVaultEconomy().depositPlayer(owner, shopVisitEvent.getChargeAmount());
                }
            } else {

                final ItemStack currencyItem = (INSTANCE.getConfig().getBoolean("shop-currency-item.force-use")
                        ? INSTANCE.getManager().buildShopCurrencyItem(1)
                        : (getTradeItem() == null ? INSTANCE.getManager().buildShopCurrencyItem(1) : getTradeItem()));

                final int totalCurrencyItems = INSTANCE.getManager().getItemAmount(player.getInventory(), currencyItem);
                if (totalCurrencyItems < shopVisitEvent.getChargeAmount()) {
                    String message = INSTANCE.getLangConfig().getString("insufficient-funds");
                    if (message != null && !message.equalsIgnoreCase(""))
                        INSTANCE.getManager().sendMessage(player, message.replace("{price}",
                                INSTANCE.getManager().formatNumber(shopVisitEvent.getChargeAmount(), true)));
                    return;
                }

                INSTANCE.getManager().removeItem(player.getInventory(), currencyItem, (int) shopVisitEvent.getChargeAmount());
            }

            if (!isSyncing) setStoredBalance(Math.min(INSTANCE.getConfig().getDouble("max-stored-currency"),
                    (getStoredBalance() + shopVisitEvent.getChargeAmount())));

            String message = INSTANCE.getLangConfig().getString("shop-visit-charge");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message.replace("{charge}",
                        INSTANCE.getManager().formatNumber(shopVisitEvent.getChargeAmount(), true)));
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
                INSTANCE.getPacketManager().getParticleHandler().displayParticle(player, particleName,
                        player.getLocation().add(0, 1, 0), 1, 1, 1, 0, 10);

            player.teleport(finalSafeLocation.add(0.5, 1, 0.5));

            if (particleName != null && !particleName.equalsIgnoreCase(""))
                INSTANCE.getPacketManager().getParticleHandler().displayParticle(player, particleName,
                        player.getLocation().add(0, 1, 0), 1, 1, 1, 0, 10);

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
        return (block.getType().name().contains("AIR") && block.getRelative(BlockFace.UP).getType().name().contains("AIR")
                && block.getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType().name().contains("AIR")
                && (!downBlock.getType().name().contains("LAVA") && !downBlock.getType().name().contains("WATER") && !downBlock.getType().name().contains("AIR")
                && !downBlock.getType().name().contains("WEB") && !downBlock.getType().name().contains("PISTON") && !downBlock.getType().name().contains("MAGMA")));
    }

    /**
     * Deletes the shop from the database if found.
     *
     * @param async Whether the shop should be deleted on the main thread or not.
     */
    public synchronized void delete(boolean async) {
        if (async)
            INSTANCE.getServer().getScheduler().runTaskAsynchronously(INSTANCE,
                    (Runnable) this::delete);
        else
            delete();
    }

    private synchronized void delete() {
        try {
            Statement statement = INSTANCE.getDatabaseConnection().createStatement();
            statement.executeUpdate("delete from shops where id = '" + getShopId() + "'");
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            INSTANCE.log(Level.WARNING, "There was an issue deleting the shop " + getShopId() + " from the database (" + e.getMessage() + ").");
        }
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
    public void updateTransactionTimeStamp(EconomyCallType economyCallType) {
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
    public boolean isReadyForDynamicReset(int resetDuration, EconomyCallType economyCallType) {
        return ((economyCallType == EconomyCallType.BUY && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getLastBuyTimeStamp()) >= resetDuration)
                || (economyCallType == EconomyCallType.SELL && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getLastSellTimeStamp()) >= resetDuration));
    }

    /**
     * Deletes the shop's data, deletes the base-block, drops the stock, destroys the display packet, and unregisters the object.
     *
     * @param async Whether the file deletion is async or not.
     */
    public void purge(boolean async) {
        unRegister();
        for (Player p : INSTANCE.getServer().getOnlinePlayers()) {
            kill(p);

            if (p.getOpenInventory().getType() != InventoryType.CRAFTING && (p.getOpenInventory().getTitle().equals(INSTANCE.getManager()
                    .color(INSTANCE.getMenusConfig().getString("shop-visit-menu.title")))
                    || p.getOpenInventory().getTitle().equals(INSTANCE.getManager()
                    .color(INSTANCE.getMenusConfig().getString("base-block-menu.title")))
                    || p.getOpenInventory().getTitle().equals(INSTANCE.getManager()
                    .color(INSTANCE.getMenusConfig().getString("shop-edit-menu.title")))
                    || p.getOpenInventory().getTitle().equals(INSTANCE.getManager()
                    .color(INSTANCE.getMenusConfig().getString("shop-transaction-menu.title"))))) p.closeInventory();
        }
        getBaseLocation().asBukkitLocation().getBlock().setType(Material.AIR);
        dropStock();
        returnBalance();
        delete(async);
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
        return (!canDynamicPriceChange() ? 0 : ((((isBuy ? getDynamicBuyCounter() : getDynamicSellCounter()) / INSTANCE.getConfig().getDouble("dynamic-" + prefix + "-increment"))
                * INSTANCE.getConfig().getDouble("dynamic-" + prefix + "-percentage")) * originalPrice));
    }

    /**
     * Checks to see if the player is able to edit the shop.
     *
     * @param player The player to check access for.
     * @return Whether the player can or can NOT edit the shop.
     */
    public boolean canEdit(Player player) {
        return (player.hasPermission("displayshops.edit") || (getOwnerUniqueId() != null && getOwnerUniqueId().toString().equals(player.getUniqueId().toString()))
                || (!getAssistants().isEmpty() && getAssistants().contains(player.getUniqueId())));
    }

    /**
     * Resets the shop entirely. The time stamp is also updated.
     */
    public void reset() {
        setIntegerValues(new int[]{0, 0, 0, 0, 0, 1, 0, 0});
        setBuyPrice(INSTANCE.getConfig().getDouble("default-buy-price"));
        setSellPrice(INSTANCE.getConfig().getDouble("default-sell-price"));
        setCommandOnlyMode(false);
        setDynamicPriceChange(false);
        getCommands().clear();
        getAssistants().clear();
        setDescription("");
        updateTimeStamp();
        INSTANCE.getInSightTask().refreshShop(this);
    }

    // integer list getters & setters.
    private int[] getIntegerValues() {
        return integerValues;
    }

    private void setIntegerValues(int[] integerValues) {
        this.integerValues = integerValues;
    }

    public int getStock() {
        return getIntegerValues()[0];
    }

    public void setStock(int stock) {
        getIntegerValues()[0] = stock;
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

    public int getBuyLimit() {
        return getIntegerValues()[1];
    }

    public void setBuyLimit(int buyLimit) {
        getIntegerValues()[1] = buyLimit;
    }

    public int getBuyCounter() {
        return getIntegerValues()[2];
    }

    public void setBuyCounter(int buyCounter) {
        getIntegerValues()[2] = buyCounter;
    }

    public int getSellLimit() {
        return getIntegerValues()[3];
    }

    public void setSellLimit(int sellLimit) {
        getIntegerValues()[3] = sellLimit;
    }

    public int getSellCounter() {
        return getIntegerValues()[4];
    }

    public void setSellCounter(int sellCounter) {
        getIntegerValues()[4] = sellCounter;
    }

    public int getShopItemAmount() {
        return getIntegerValues()[5];
    }

    public void setShopItemAmount(int shopItemAmount) {
        getIntegerValues()[5] = shopItemAmount;
    }

    // dynamic price changing getters & setters.
    public boolean canDynamicPriceChange() {
        return dynamicPriceChange;
    }

    public void setDynamicPriceChange(boolean dynamicPriceChange) {
        this.dynamicPriceChange = dynamicPriceChange;
    }

    public int getDynamicBuyCounter() {
        return getIntegerValues()[6];
    }

    public void setDynamicBuyCounter(int shopItemAmount) {
        getIntegerValues()[6] = shopItemAmount;
    }

    public int getDynamicSellCounter() {
        return getIntegerValues()[7];
    }

    public void setDynamicSellCounter(int shopItemAmount) {
        getIntegerValues()[7] = shopItemAmount;
    }

    // general getters & setters.

    public ItemStack getShopItem() {return shopItem;}

    public void setShopItem(ItemStack shopItem) {this.shopItem = shopItem;}

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

    public void setBaseLocation(LocationClone baseLocation) {
        this.baseLocation = baseLocation;
    }

    public UUID getShopId() {
        return shopId;
    }

    public void setShopId(UUID shopId) {
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

    public void setCommands(List<String> commands) {
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

    public void setStoredBaseBlockMaterial(String storedBaseBlockMaterial) {
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
}