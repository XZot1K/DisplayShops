/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api;

import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.ChatInteractionType;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.enums.StageType;
import xzot1k.plugins.ds.api.events.ChatInteractionStageEvent;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.objects.*;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DManager implements Manager {

    private DisplayShops pluginInstance;

    private HashMap<UUID, Shop> shopMap;
    private List<MarketRegion> marketRegions;
    private final List<Pair<Shop, ItemStack>> shopVisitItemList;
    private final Pattern hexPattern;
    private HashMap<UUID, DataPack> dataPackMap;

    public DManager(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
        setShopMap(new HashMap<>());
        setDataPackMap(new HashMap<>());
        setMarketRegions(new ArrayList<>());
        shopVisitItemList = new ArrayList<>();
        hexPattern = Pattern.compile("#[a-fA-F\\d]{6}");
    }

    /**
     * Loads the passed player's data pack. If not found, a new data pack module is created. (CAN RETURN NULL)
     *
     * @param player The player to load the data pack for.
     */
    public DataPack loadDataPack(Player player) {
        DataPack dataPack = null;
        try (PreparedStatement statement = getPluginInstance().getDatabaseConnection().prepareStatement(
                "SELECT * FROM player_data WHERE uuid = '" + player.getUniqueId() + "';");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                dataPack = new DDataPack(getPluginInstance(), resultSet.getString("bbm_unlocks"));

                final String cooldownLine = resultSet.getString("cooldowns");
                String[] cdArgs = cooldownLine.split(",");

                final String transactionLimitLine = resultSet.getString("transaction_limits");
                if (transactionLimitLine != null && !transactionLimitLine.isEmpty()) {
                    if (transactionLimitLine.contains(",")) {
                        String[] tlArgs = transactionLimitLine.split(",");
                        try {
                            if (tlArgs.length > 0) for (int i = -1; ++i < tlArgs.length; ) {
                                final String[] tlLineArgs = tlArgs[i].split(":"), tlInnerArgs = tlLineArgs[1].split(";");

                                String uuid = tlLineArgs[0];
                                if (uuid == null || uuid.isEmpty()) continue;
                                final UUID id = UUID.fromString(uuid);

                                Shop shop = getShopById(id);
                                if (shop == null) continue;
                                dataPack.updateCurrentTransactionLimitCounter(shop, true, Long.parseLong(tlInnerArgs[0]));
                                dataPack.updateCurrentTransactionLimitCounter(shop, false, Long.parseLong(tlInnerArgs[1]));
                            }
                        } catch (ArrayIndexOutOfBoundsException ignored) {
                        }
                    } else {
                        final String[] args = transactionLimitLine.split(":");
                        final String[] innerArgs = args[1].split(";");
                        final UUID id = UUID.fromString(args[0]);
                        final Shop shop = getShopById(id);
                        if (shop != null) {
                            dataPack.updateCurrentTransactionLimitCounter(shop, true, Long.parseLong(innerArgs[0]));
                            dataPack.updateCurrentTransactionLimitCounter(shop, false, Long.parseLong(innerArgs[1]));
                        }
                    }
                }

                try {
                    if (cdArgs.length > 0) for (int i = -1; ++i < cdArgs.length; ) {
                        final String[] cdLineArgs = cdArgs[i].split(":");
                        dataPack.getCooldownMap().put(cdLineArgs[0], Long.parseLong(cdLineArgs[1]));
                    }
                } catch (ArrayIndexOutOfBoundsException ignored) {
                }

                String notify = resultSet.getString("notify");
                if (notify != null && !notify.isEmpty())
                    dataPack.setTransactionNotify(notify.equalsIgnoreCase("1"));
            }

            resultSet.close();
            statement.close();

            if (dataPack == null) dataPack = new DDataPack(getPluginInstance(), null);
            getDataPackMap().put(player.getUniqueId(), dataPack);
            return dataPack;
        } catch (SQLException e) {
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING, "There was an issue loading existing data for \"" + player.getName() + "\".");
        }

        if (dataPack == null) dataPack = new DDataPack(getPluginInstance(), null);
        getDataPackMap().put(player.getUniqueId(), dataPack);
        return dataPack;
    }

    /**
     * Attempts to remove any player data from the database when the player's last seen exceeds one year.
     */
    public void cleanUpDataPacks() {
        try (PreparedStatement statement = getPluginInstance().getDatabaseConnection().prepareStatement("SELECT * FROM player_data;");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidString = resultSet.getString("uuid");
                if (uuidString == null || uuidString.isEmpty()) {
                    Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                    deleteStatement.executeUpdate("DELETE FROM player_data where uuid = '" + uuidString + "';");
                    deleteStatement.close();
                    getPluginInstance().log(Level.WARNING, "[CLEANING] A data pack was removed due to its invalid UUID.");
                    continue;
                }

                UUID playerId = UUID.fromString(resultSet.getString("uuid"));
                Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                deleteStatement.executeUpdate("DELETE FROM player_data where uuid = '" + uuidString + "';");
                deleteStatement.close();
                getPluginInstance().log(Level.WARNING, "[CLEANING] A data pack was removed due to its untranslatable UUID.");

                OfflinePlayer offlinePlayer = getPluginInstance().getServer().getOfflinePlayer(playerId);
                deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                deleteStatement.executeUpdate("DELETE FROM player_data where uuid = '" + uuidString + "';");
                getPluginInstance().log(Level.WARNING, "[CLEANING] The data pack for the player \"" + playerId
                        + "\" was removed due to the offline player being invalid.");

                if (!offlinePlayer.hasPlayedBefore() || TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - offlinePlayer.getLastPlayed()) >= 365) {
                    deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                    deleteStatement.executeUpdate("DELETE FROM player_data where uuid = '" + uuidString + "';");
                    deleteStatement.close();
                    getPluginInstance().log(Level.WARNING, "[CLEANING] The data pack for the player \""
                            + playerId + "\" (" + offlinePlayer.getName() + ") was removed due to last seen exceeding 365 days (One Year).");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING, "This is just a warning stating that a market region has failed to load.");
        }
    }

    /**
     * Saves the player's data pack to the database.
     *
     * @param statement      The database statement to use.
     * @param playerUniqueId The player's UUID for the data pack to save under.
     * @param dataPack       The data pack itself.
     * @param async          Whether it should be saved on the main thread or not.
     * @param closeStatement Closes the database statement when task is complete.
     */
    public void saveDataPack(Statement statement, UUID playerUniqueId, DataPack dataPack, boolean async, boolean closeStatement) {
        if (async)
            getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () ->
                    saveDataPack(statement, playerUniqueId, dataPack, closeStatement));
        else saveDataPack(statement, playerUniqueId, dataPack, closeStatement);
    }

    private synchronized void saveDataPack(Statement statement, UUID playerUniqueId, DataPack dataPack, boolean closeStatement) {
        if (playerUniqueId == null) return;
        try {
            if (statement == null) statement = getPluginInstance().getDatabaseConnection().createStatement();

            final String host = getPluginInstance().getConfig().getString("mysql.host"), syntax;
            if (host == null || host.isEmpty())
                syntax = "INSERT OR REPLACE INTO player_data(uuid, bbm_unlocks, cooldowns, transaction_limits, notify) VALUES('"
                        + playerUniqueId + "', '" + dataPack.getBBMString() + "', '" + dataPack.cooldownsToString() + "', '"
                        + dataPack.transactionLimitsToString() + "', '" + (dataPack.isTransactionNotify() ? 1 : 0) + "');";
            else
                syntax = "INSERT INTO player_data(uuid, bbm_unlocks, cooldowns, transaction_limits, notify) VALUES( '"
                        + playerUniqueId + "', '" + dataPack.getBBMString() + "', '" + dataPack.cooldownsToString() + "', '"
                        + dataPack.transactionLimitsToString() + "', '" + (dataPack.isTransactionNotify() ? 1 : 0)
                        + "') ON DUPLICATE KEY UPDATE uuid = '" + playerUniqueId + "', bbm_unlocks = '"
                        + dataPack.getBaseBlockUnlocks() + "', cooldowns = '" + dataPack.cooldownsToString()
                        + "', transaction_limits = '" + dataPack.transactionLimitsToString() + "', notify = '"
                        + (dataPack.isTransactionNotify() ? 1 : 0) + "';";

            statement.executeUpdate(syntax);
            if (closeStatement) statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING, "There was an issue saving the data pack for '"
                    + playerUniqueId + "' to the database (" + e.getMessage() + ").");
        }
    }

    /**
     * Obtains the designated data pack for the passed player.
     *
     * @param player The player to obtain the data pack for.
     * @return The data pack object (Cooldowns, interactions, unlocks, etc.). (Should never return NULL)
     */
    public DataPack getDataPack(Player player) {
        DataPack dataPack;
        if (getDataPackMap().isEmpty() || !getDataPackMap().containsKey(player.getUniqueId())) {
            getDataPackMap().put(player.getUniqueId(), dataPack = loadDataPack(player));
        } else dataPack = getDataPackMap().get(player.getUniqueId());
        return dataPack;
    }

    /**
     * Ray traces from the provided vectors to obtain a shop from the locations it passes through.
     *
     * @param worldName       The world to ray trace in.
     * @param eyeVector       The eye location of the entity or just the general origin location.
     * @param directionVector The direction at which the vector should go.
     * @param distance        The distance at which the ray should travel.
     * @return The found shop, returns NULL if NOT found.
     */
    public Shop getShopRayTraced(String worldName, Vector eyeVector, Vector directionVector, double distance) {
        Shop[] shopList = getShopMap().values().toArray(new Shop[0]);
        for (int i = -1; ++i < distance; ) {
            final Vector newPositionVector = eyeVector.clone().add(directionVector.clone().multiply(i));
            for (int j = -1; ++j < shopList.length; ) {
                final Shop shop = shopList[j];
                if (shop == null || shop.getBaseLocation() == null || !shop.getBaseLocation().getWorldName().equals(worldName)
                        || !((shop.getBaseLocation().getX() + 1.2) > newPositionVector.getX()
                        && (shop.getBaseLocation().getX() - 0.2) < newPositionVector.getX())
                        || !((shop.getBaseLocation().getY() + 2) > newPositionVector.getY()
                        && (shop.getBaseLocation().getY() - 0.2) < newPositionVector.getY())
                        || !((shop.getBaseLocation().getZ() + 1.2) > newPositionVector.getZ()
                        && (shop.getBaseLocation().getZ() - 0.2) < newPositionVector.getZ()))
                    continue;
                return shop;
            }
        }
        return null;
    }

    /**
     * Sends a color translated message to the players as either a normal chat message or action bar message.
     *
     * @param player  The player to send the message to.
     * @param message The message to send (color codes accepted, if the message contains {bar} at the front it will be sent to the action bar).
     */
    public void sendMessage(@NotNull Player player, @Nullable String message) {
        if (message == null || message.isEmpty()) return;

        final String replacedMessage = (getPluginInstance().getPapiHelper() != null
                ? getPluginInstance().getPapiHelper().replace(player, message) : message);
        if (!message.toLowerCase().startsWith("{bar}")) player.sendMessage(color(replacedMessage));
        else getPluginInstance().getPacketManager().sendActionBar(player, replacedMessage.substring(5));
    }

    /**
     * Initiates a withdrawal/deposit transaction directed at an investor and owner automating messages, taxing, and more (The stopInTacks() method must be called still).
     *
     * @param investor        The buyer.
     * @param producer        The seller.
     * @param shop            The shop in use.
     * @param economyCallType The economy type being processed Buy, Sell, etc.
     * @param price           The price in use.
     * @return the economy call event
     */
    public EconomyCallEvent initiateShopEconomyTransaction(Player investor, OfflinePlayer producer, Shop shop, EconomyCallType economyCallType, double price) {
        if (investor == null) return null;

        final EconomyCallEvent economyCallEvent = new EconomyCallEvent(investor, producer, economyCallType, shop, price);
        getPluginInstance().getServer().getPluginManager().callEvent(economyCallEvent);
        if (economyCallEvent.isCancelled()) {
            if (price <= 0) {
                economyCallEvent.setCanProducerAfford(true);
                economyCallEvent.setCanInvestorAfford(true);
                economyCallEvent.setWillSucceed(true);
            }

            return economyCallEvent;
        } else if (price <= 0) {
            economyCallEvent.setCanProducerAfford(true);
            economyCallEvent.setCanInvestorAfford(true);
            economyCallEvent.setWillSucceed(true);
            return economyCallEvent;
        }

        if (producer != null && !economyCallEvent.canProducerAfford()) {
            String message = getPluginInstance().getLangConfig().getString("owner-insufficient-funds");
            if (message != null && !message.equalsIgnoreCase("")) sendMessage(investor, message);
        }

        if (!investor.hasPermission("displayshops.bypass")) {
            if (economyCallType != EconomyCallType.SELL && !economyCallEvent.canInvestorAfford()) {
                investor.closeInventory();
                String tradeItemName = "";

                final boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null);
                if (!useVault) {
                    final boolean forceUseCurrency = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");
                    final ItemStack forceCurrencyItem = getPluginInstance().getManager().buildShopCurrencyItem(1);
                    final String defaultName = getPluginInstance().getManager().getItemName(forceCurrencyItem);
                    tradeItemName = (forceUseCurrency ? (forceCurrencyItem != null ? defaultName : "")
                            : (shop.getTradeItem() != null ? getPluginInstance().getManager().getItemName(shop.getTradeItem()) : defaultName));
                }

                final String message = getPluginInstance().getLangConfig().getString("insufficient-funds");
                if (message != null && !message.equalsIgnoreCase(""))
                    sendMessage(investor, message.replace("{trade-item}", tradeItemName)
                            .replace("{price}", getPluginInstance().getManager().formatNumber(economyCallEvent.getTaxedPrice(), true)));
                return economyCallEvent;
            }

            if (economyCallEvent.willSucceed()) economyCallEvent.performCurrencyTransfer(true);
            return economyCallEvent;
        }

        if (economyCallEvent.willSucceed()) economyCallEvent.performCurrencyTransfer(false);
        return economyCallEvent;
    }

    /**
     * Runs the chat interaction operation as normal using the given parameters.
     *
     * @param player           The player of who is in the chat interaction.
     * @param playerEntryValue The value/message the player entered.
     * @return Returns true if the interaction completes successfully; otherwise, the return is false.
     */
    public boolean initiateChatInteractionOperation(Player player, ChatInteractionType chatInteractionType, String playerEntryValue) {
        final DataPack dataPack = getDataPack(player);

        if (chatInteractionType == null) {
            dataPack.resetEditData();
            return false;
        }

        final String fixedEntry = chatInteractionType.isNumericalEntry()
                ? playerEntryValue.replaceAll("/[^\\d.\\-]/g", "").replace(",", ".") : playerEntryValue;
        final boolean isNumeric = !getPluginInstance().getManager().isNotNumeric(fixedEntry),
                useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null),
                isAll = ChatColor.stripColor(fixedEntry).equalsIgnoreCase("all");

        final Shop shop = dataPack.getSelectedShop();
        if (shop == null && dataPack.getChatInteractionType() != ChatInteractionType.VISIT_FILTER_ENTRY) {
            dataPack.resetEditData();
            sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-invalid"));
            return false;
        }

        final boolean isCancelled = playerEntryValue.equalsIgnoreCase(getPluginInstance().getConfig().getString("chat-interaction-cancel"));

        if (dataPack.getChatInteractionType() == ChatInteractionType.VISIT_FILTER_ENTRY) {

            dataPack.resetEditData();

            if (isCancelled) {
                ChatInteractionStageEvent chatInteractionStageEvent = new ChatInteractionStageEvent(player, StageType.CANCELLED, playerEntryValue);
                getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionStageEvent);

                sendMessage(player, getPluginInstance().getLangConfig().getString("chat-interaction-cancelled"));
            } else {
                ChatInteractionStageEvent stageEvent = new ChatInteractionStageEvent(player, StageType.FINISH, fixedEntry);
                getPluginInstance().getServer().getPluginManager().callEvent(stageEvent);
            }

            player.openInventory(getPluginInstance().getManager().buildVisitMenu(player,
                    (isAll || ChatColor.stripColor(fixedEntry).equalsIgnoreCase(getPluginInstance()
                            .getConfig().getString("chat-interaction-cancel"))) ? null : fixedEntry));
            return true;
        }

        if (isCancelled) {
            dataPack.resetEditData();

            ChatInteractionStageEvent chatInteractionStageEvent = new ChatInteractionStageEvent(player, StageType.CANCELLED, playerEntryValue);
            getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionStageEvent);

            sendMessage(player, getPluginInstance().getLangConfig().getString("chat-interaction-cancelled"));
            return true;
        }

        if (shop == null) return false;

        double amount;
        String message, tradeItemName = "";
        ItemStack tradeItem = null;
        EconomyCallEvent economyCallEvent;
        Player enteredPlayer;
        switch (dataPack.getChatInteractionType()) {
            case ASSISTANTS_ADD: {
                enteredPlayer = getPluginInstance().getServer().getPlayer(fixedEntry);
                if (enteredPlayer == null || !enteredPlayer.isOnline()) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("player-invalid"));
                    return false;
                }

                if (shop.getOwnerUniqueId() != null && enteredPlayer.getUniqueId().toString().equals(shop.getOwnerUniqueId().toString())) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-owner"))
                            .replace("{player}", enteredPlayer.getName()));
                    return false;
                }

                if (shop.getAssistants().contains(enteredPlayer.getUniqueId())) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-access"))
                            .replace("{player}", enteredPlayer.getName()));
                    return false;
                }

                shop.getAssistants().add(enteredPlayer.getUniqueId());
                shop.updateTimeStamp();
                sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-added"))
                        .replace("{player}", enteredPlayer.getName()));
                break;
            }

            case ASSISTANTS_REMOVE: {
                enteredPlayer = getPluginInstance().getServer().getPlayer(fixedEntry);
                if (enteredPlayer == null || !enteredPlayer.isOnline()) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("player-invalid"));
                    return false;
                }

                if (shop.getOwnerUniqueId() != null && enteredPlayer.getUniqueId().toString().equals(shop.getOwnerUniqueId().toString())) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-owner"))
                            .replace("{player}", enteredPlayer.getName()));
                    return false;
                }

                if (!shop.getAssistants().contains(enteredPlayer.getUniqueId())) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-no-access"))
                            .replace("{player}", enteredPlayer.getName()));
                    return false;
                }

                shop.getAssistants().remove(enteredPlayer.getUniqueId());
                shop.updateTimeStamp();
                sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("assistants-removed"))
                        .replace("{player}", enteredPlayer.getName()));
                break;
            }

            case DEPOSIT_BALANCE: {
                if (!isNumeric && !isAll) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = isAll ? getPluginInstance().getVaultEconomy().getBalance(player) : Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (!useVault) {
                    tradeItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use") ?
                            getPluginInstance().getManager().buildShopCurrencyItem(1)
                            : (shop.getTradeItem() != null ? shop.getTradeItem() : getPluginInstance().getManager().buildShopCurrencyItem(1)));
                    if (tradeItem != null) tradeItemName = getPluginInstance().getManager().getItemName(tradeItem);
                }

                if (useVault ? !getPluginInstance().getVaultEconomy().has(player, amount) : getItemAmount(player.getInventory(), tradeItem) < amount) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("insufficient-funds"))
                            .replace("{trade-item}", tradeItemName).replace("{price}", formatNumber(amount, true)));
                    return false;
                }

                if ((shop.getStoredBalance() + amount) >= getPluginInstance().getConfig().getLong("max-stored-currency")) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("max-stored-currency"))
                            .replace("{trade-item}", tradeItemName).replace("{amount}", formatNumber(amount, true)));
                    return false;
                }

                if (useVault) getPluginInstance().getVaultEconomy().withdrawPlayer(player, amount);
                else removeItem(player.getInventory(), tradeItem, (int) amount);

                shop.setStoredBalance(shop.getStoredBalance() + amount);
                shop.updateTimeStamp();
                sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("balance-deposited"))
                        .replace("{trade-item}", tradeItemName).replace("{amount}", formatNumber(amount, true)));
                break;
            }

            case WITHDRAW_BALANCE: {
                if (!isNumeric && !isAll) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                if (!useVault) {
                    tradeItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use")
                            ? getPluginInstance().getManager().buildShopCurrencyItem(1) : (shop.getTradeItem() != null
                            ? shop.getTradeItem() : getPluginInstance().getManager().buildShopCurrencyItem(1)));
                    if (tradeItem != null) tradeItemName = getPluginInstance().getManager().getItemName(tradeItem);
                }

                amount = isAll ? shop.getStoredBalance() : Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if ((shop.getStoredBalance() - amount) < 0 && shop.getStoredBalance() != amount) {
                    sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("balance-withdraw-fail"))
                            .replace("{trade-item}", tradeItemName).replace("{balance}", formatNumber(shop.getStoredBalance(), true)));
                    return false;
                }

                if (useVault) getPluginInstance().getVaultEconomy().depositPlayer(player, amount);
                else if (tradeItem != null) {
                    int stackCount = (int) (amount / tradeItem.getType().getMaxStackSize()), remainder = (int) (amount % tradeItem.getType().getMaxStackSize());

                    tradeItem.setAmount(tradeItem.getType().getMaxStackSize());
                    for (int i = -1; ++i < stackCount; )
                        if (player.getInventory().firstEmpty() == -1)
                            player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                        else player.getInventory().addItem(tradeItem);

                    if (remainder > 0) {
                        tradeItem.setAmount(remainder);
                        if (player.getInventory().firstEmpty() == -1)
                            player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                        else player.getInventory().addItem(tradeItem);
                    }
                }

                shop.setStoredBalance(shop.getStoredBalance() - amount);
                shop.updateTimeStamp();
                sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("balance-withdrawn"))
                        .replace("{trade-item}", tradeItemName).replace("{amount}", formatNumber(amount, true)));
                break;
            }

            case EDIT_DESCRIPTION: {
                String filteredEntry = fixedEntry.substring(0, Math.min(fixedEntry.length(), getPluginInstance().getConfig().getInt("description-character-limit")));
                List<String> filterList = getPluginInstance().getConfig().getStringList("description-filter");
                for (int i = -1; ++i < filterList.size(); )
                    filteredEntry = filteredEntry.replaceAll("(?i)" + filterList.get(i), "");
                filteredEntry = net.md_5.bungee.api.ChatColor.stripColor(color(filteredEntry.replace("'", "").replace("\"", "")));

                if (filteredEntry.equalsIgnoreCase(shop.getDescription())) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-too-similar"));
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.promotion-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setDescription(filteredEntry);
                shop.updateTimeStamp();

                message = getPluginInstance().getLangConfig().getString("description-set");
                if (message != null && !message.isEmpty())
                    sendMessage(player, message.replace("{description}", shop.getDescription()));
                getPluginInstance().runEventCommands("shop-description", player);
                break;
            }

            case SHOP_ITEM_AMOUNT: {
                if (!isNumeric) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount > getPluginInstance().getConfig().getInt("max-item-stack-size") || amount < 1) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-stack-size"));
                    return false;
                }

                if (amount == shop.getShopItemAmount()) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-too-similar"));
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed()) {
                    return false;
                }

                shop.setShopItemAmount((int) amount);
                shop.updateTimeStamp();

                message = getPluginInstance().getLangConfig().getString("stack-size-set");
                if (message != null && !message.isEmpty())
                    sendMessage(player, message.replace("{amount}", formatNumber(amount, false)));
                getPluginInstance().runEventCommands("shop-amount", player);
                break;
            }

            case SELL_LIMIT: {
                if (!isNumeric) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount > getMaxStock(shop)) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-limit"));
                    return false;
                }

                if (amount == shop.getSellLimit()) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-too-similar"));
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.limits.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setSellLimit((int) amount);
                shop.updateTimeStamp();
                message = getPluginInstance().getLangConfig().getString("sell-limit-set");
                if (message != null && !message.isEmpty())
                    sendMessage(player, message.replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
                getPluginInstance().runEventCommands("shop-sell-limit", player);
                break;
            }

            case BUY_LIMIT: {
                if (!isNumeric) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount > getMaxStock(shop)) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-limit"));
                    return false;
                }

                if (amount == shop.getBuyLimit()) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-too-similar"));
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.limits.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setBuyLimit((int) amount);
                shop.updateTimeStamp();
                sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("buy-limit-set"))
                        .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
                getPluginInstance().runEventCommands("shop-buy-limit", player);
                break;
            }

            case BUY_PRICE: {
                if (!isNumeric) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < -1) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount > -1) {
                    double foundBuyPriceMax = (shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMaxPrice(shop.getShopItem(), true) : 0),
                            foundBuyPriceMin = shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMinPrice(shop.getShopItem(), true) : 0;
                    if (amount < (foundBuyPriceMin * shop.getShopItemAmount()) || amount > (foundBuyPriceMax * shop.getShopItemAmount())) {
                        sendMessage(player, getPluginInstance().getLangConfig().getString("buy-price-limit"));
                        return false;
                    }

                    if (amount < shop.getSellPrice(false) && shop.getSellPrice(false) >= 0) {
                        sendMessage(player, getPluginInstance().getLangConfig().getString("buy-price-less"));
                        return false;
                    }
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-price-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setBuyPrice(amount);
                shop.updateTimeStamp();

                if (amount <= -1) message = getPluginInstance().getLangConfig().getString("buying-disabled");
                else
                    message = getPluginInstance().getLangConfig().getString("buy-price-set");
                sendMessage(player, Objects.requireNonNull(message).replace("{price}", formatNumber(amount, true)));
                getPluginInstance().runEventCommands("shop-buy-price", player);
                break;
            }

            case SELL_PRICE: {
                if (!isNumeric) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("invalid-amount"));
                    return false;
                }

                amount = Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < -1) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount > -1) {
                    double foundSellPriceMax = (shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMaxPrice(shop.getShopItem(), false) : 0),
                            foundSellPriceMin = shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMinPrice(shop.getShopItem(), false) : 0;
                    if (amount < (foundSellPriceMin * shop.getShopItemAmount()) || amount > (foundSellPriceMax * shop.getShopItemAmount())) {
                        sendMessage(player, getPluginInstance().getLangConfig().getString("sell-price-limit"));
                        return false;
                    }

                    if (amount > shop.getBuyPrice(false) && shop.getBuyPrice(false) >= 0) {
                        message = getPluginInstance().getLangConfig().getString("sell-price-greater");
                        if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                        return false;
                    }
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-price-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setSellPrice(amount);
                shop.updateTimeStamp();

                if (amount <= -1) message = getPluginInstance().getLangConfig().getString("selling-disabled");
                else message = getPluginInstance().getLangConfig().getString("sell-price-set");
                if (message != null && !message.equalsIgnoreCase(""))
                    sendMessage(player, message.replace("{price}", getPluginInstance().getManager().formatNumber(amount, true)));
                getPluginInstance().runEventCommands("shop-sell-price", player);
                break;
            }

            case WITHDRAW_STOCK: {
                if (!isNumeric && !isAll) {
                    message = getPluginInstance().getLangConfig().getString("invalid-amount");
                    if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                    return false;
                }

                amount = isAll ? shop.getStock() : (int) Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount < 1) {
                    message = getPluginInstance().getLangConfig().getString("invalid-amount");
                    if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                    return false;
                }

                if (amount > shop.getStock()) {
                    message = getPluginInstance().getLangConfig().getString("stock-withdraw-fail");
                    if (message != null && !message.equalsIgnoreCase(""))
                        sendMessage(player, message.replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
                    return false;
                }

                final int availableSpace = Math.min(getInventorySpaceForItem(player, shop.getShopItem()), (36 * shop.getShopItem().getMaxStackSize()));
                amount = Math.min(amount, availableSpace);

                if (player.getInventory().firstEmpty() == -1 || availableSpace < amount) {
                    message = getPluginInstance().getLangConfig().getString("insufficient-space");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{space}", formatNumber(availableSpace, false)));
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.stock-manage-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                shop.setStock(shop.getStock() - ((int) amount));
                shop.updateTimeStamp();

                final ItemStack itemStack = shop.getShopItem().clone();
                final double finalAmount = amount;
                getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () -> giveItemStacks(player, itemStack, (int) finalAmount));

                message = getPluginInstance().getLangConfig().getString("withdrawn-stock");
                if (message != null && !message.equalsIgnoreCase(""))
                    sendMessage(player, message.replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
                getPluginInstance().runEventCommands("shop-withdraw", player);
                break;
            }

            case DEPOSIT_STOCK: {
                if (!isNumeric && !isAll) {
                    message = getPluginInstance().getLangConfig().getString("invalid-amount");
                    if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                    return false;
                }

                amount = isAll ? getItemAmount(player.getInventory(), shop.getShopItem()) : (int) Double.parseDouble(fixedEntry.replace(",", "."));
                if (amount < 0) {
                    sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
                    return false;
                }

                if (amount < 1) {
                    message = getPluginInstance().getLangConfig().getString("invalid-amount");
                    if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                    return false;
                }

                final int maxStock = getMaxStock(shop);
                int totalItemCount = getItemAmount(player.getInventory(), shop.getShopItem());
                if (totalItemCount <= 0 || totalItemCount < amount) {
                    message = getPluginInstance().getLangConfig().getString("insufficient-items");
                    if (message != null && !message.equalsIgnoreCase("")) sendMessage(player, message);
                    return false;
                }

                economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.stock-manage-item.price"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed())
                    return false;

                int difference = (maxStock - shop.getStock()), amountToRemove = (difference > 0 && difference >= amount ? (int) amount : Math.max(difference, 0));
                if (amountToRemove == 0 || shop.getStock() >= maxStock) {
                    message = getPluginInstance().getLangConfig().getString("stock-deposit-fail");
                    if (message != null && !message.equalsIgnoreCase(""))
                        sendMessage(player, message.replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
                    return false;
                }

                getPluginInstance().getManager().removeItem(player.getInventory(), shop.getShopItem(), amountToRemove);
                shop.setStock(shop.getStock() + amountToRemove);
                shop.updateTimeStamp();

                message = getPluginInstance().getLangConfig().getString("deposited-stock");
                if (message != null && !message.equalsIgnoreCase(""))
                    sendMessage(player, message.replace("{amount}", getPluginInstance().getManager().formatNumber(amountToRemove, false)));
                getPluginInstance().runEventCommands("shop-deposit", player);
                break;
            }
        }

        getPluginInstance().getInSightTask().refreshShop(shop);
        dataPack.resetEditData();

        ChatInteractionStageEvent stageEvent = new ChatInteractionStageEvent(player, StageType.FINISH, fixedEntry);
        getPluginInstance().getServer().getPluginManager().callEvent(stageEvent);
        return true;
    }

    /**
     * Retrieve a market region, if the passed location is inside it.
     *
     * @param location The location to check.
     * @return The MarketRegion object found.
     */
    public MarketRegion getMarketRegion(Location location) {
        if (getMarketRegions().size() <= 0) return null;
        for (int i = -1; ++i < getMarketRegions().size(); ) {
            MarketRegion marketRegion = getMarketRegions().get(i);
            if (marketRegion.isInRegion(location)) return marketRegion;
        }
        return null;
    }

    /**
     * Retrieve a market region by Id, if it exists.
     *
     * @param marketId The id to check for.
     * @return The MarketRegion object.
     */
    public MarketRegion getMarketRegion(String marketId) {
        if (getMarketRegions().size() <= 0) return null;
        for (int i = -1; ++i < getMarketRegions().size(); ) {
            MarketRegion marketRegion = getMarketRegions().get(i);
            if (marketRegion.getMarketId().equalsIgnoreCase(marketId)) return marketRegion;
        }
        return null;
    }

    /**
     * Checks if a market region already exists with the passed id.
     *
     * @param marketId The id to check.
     * @return The result as a boolean value.
     */
    public boolean doesMarketRegionExist(String marketId) {
        if (getMarketRegions().size() <= 0) return false;
        for (int i = -1; ++i < getMarketRegions().size(); ) {
            MarketRegion marketRegion = getMarketRegions().get(i);
            if (marketRegion.getMarketId().equalsIgnoreCase(marketId)) return true;
        }

        return false;
    }

    /**
     * Gets if a location is too close to a shop based on the distance value in the configuration.
     *
     * @param location The location to check around.
     * @return The result as a boolean.
     */
    public boolean isTooClose(Location location) {
        if (location == null || location.getWorld() == null) return false;
        double distance = getPluginInstance().getConfig().getDouble("required-shop-distance");

        for (Map.Entry<UUID, Shop> entry : getShopMap().entrySet()) {
            if (entry.getValue().getBaseLocation() != null && entry.getValue().getBaseLocation().getWorldName().equals(location.getWorld().getName())
                    && entry.getValue().getBaseLocation().distance(location, true) <= distance)
                return true;
        }

        return false;
    }

    /**
     * Gets the name of the item. If the item name is not custom, then either the original or translated name will be used.
     *
     * @param itemStack The item to get the name of.
     * @return The item name.
     */
    public String getItemName(ItemStack itemStack) {
        if (itemStack == null) return "";
        if (itemStack.hasItemMeta() && itemStack.getItemMeta() != null && itemStack.getItemMeta().hasDisplayName())
            return itemStack.getItemMeta().getDisplayName();//.replace("\"", "\\\"").replace("'", "\\'");
        else return getPluginInstance().getManager().getTranslatedName(itemStack.getType());
        //.replace("\"", "\\\"").replace("'", "\\'");
    }

    /**
     * Obtains any translation created for the passed material found in the "lang.yml".
     *
     * @param material The material to obtain the translation for.
     * @return The translated version.
     */
    public String getTranslatedName(Material material) {
        ConfigurationSection cs = getPluginInstance().getLangConfig().getConfigurationSection("translated-material-names");
        if (cs != null) {
            Collection<String> keys = cs.getKeys(false);
            if (!keys.isEmpty())
                for (String key : keys) {
                    if (key.toUpperCase().replace(" ", "_")
                            .replace("-", "_").equalsIgnoreCase(material.name()))
                        return cs.getString(key);
                }
        }

        return WordUtils.capitalize(material.name().toLowerCase().replace("_", " "));
    }

    /**
     * Obtains any translation created for the passed enchantment found in the "lang.yml".
     *
     * @param enchantment The enchantment to obtain the translation for.
     * @return The translated version.
     */
    public String getTranslatedName(Enchantment enchantment) {
        final boolean isNew = (Math.floor(getPluginInstance().getServerVersion()) > 1_12);
        ConfigurationSection cs = getPluginInstance().getLangConfig().getConfigurationSection("translated-enchantment-names");
        if (cs != null) {
            Collection<String> keys = cs.getKeys(false);
            if (!keys.isEmpty())
                for (String key : keys) {
                    if (key.toUpperCase().replace(" ", "_").replace("-", "_").equalsIgnoreCase((isNew ? enchantment.getKey().getKey() : enchantment.getName())))
                        return cs.getString(key);
                }
        }

        return WordUtils.capitalize((isNew ? enchantment.getKey().getKey() : enchantment.getName()).toLowerCase().replace("_", " "));
    }

    /**
     * Obtains any translation created for the passed potion type found in the "translation.yml".
     *
     * @param potionType The potion type to obtain the translation for.
     * @return The translated version.
     */
    public String getTranslatedName(PotionType potionType) {
        ConfigurationSection cs = getPluginInstance().getLangConfig().getConfigurationSection("translated-potion-names");
        if (cs != null) {
            Collection<String> keys = cs.getKeys(false);
            if (!keys.isEmpty())
                for (String key : keys) {
                    if (key.toUpperCase().replace(" ", "_").replace("-", "_").equalsIgnoreCase(potionType.name()))
                        return cs.getString(key);
                }
        }

        return WordUtils.capitalize(potionType.name().toLowerCase().replace("_", " "));
    }

    /**
     * Gets the names of the enchantments and formats them into a neat format.
     *
     * @param itemStack The item to get the enchants from.
     * @return The new formatted line.
     */
    public String getEnchantmentLine(ItemStack itemStack) {
        if (itemStack == null) return "";
        StringBuilder enchantLine = new StringBuilder();

        int totalEnchantments, currentCount = 0, cutCount = getPluginInstance().getConfig().getInt("enchantment-cut-count");
        Set<Map.Entry<Enchantment, Integer>> enchantEntries;
        if (itemStack.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) itemStack.getItemMeta();
            enchantEntries = bookMeta.getStoredEnchants().entrySet();
            totalEnchantments = bookMeta.getStoredEnchants().size();
        } else {
            enchantEntries = itemStack.getEnchantments().entrySet();
            totalEnchantments = itemStack.getEnchantments().size();
        }

        if (totalEnchantments > 0) for (Map.Entry<Enchantment, Integer> enchantEntry : enchantEntries) {
            if (currentCount >= cutCount) break;
            enchantLine.append(color(getTranslatedName(enchantEntry.getKey()) + " " + getRomanNumeral(enchantEntry.getValue())));
            if (currentCount < totalEnchantments) enchantLine.append(", ");
            currentCount++;
        }

        final int remainder = (totalEnchantments - currentCount);
        return enchantLine + (remainder > 0 ? Objects.requireNonNull(getPluginInstance().getConfig().getString("enchantment-description-format"))
                .replace("{remainder}", getPluginInstance().getManager().formatNumber(remainder, false)) : "");
    }

    /**
     * Gets the names of the potion effects and formats them into a neat format.
     *
     * @param itemStack The item to get the potion effects from.
     * @return The new formatted line.
     */
    public String getPotionLine(ItemStack itemStack) {
        if (itemStack == null || !(itemStack.getItemMeta() instanceof PotionMeta)) return "";
        PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
        final int totalEffects = potionMeta.getCustomEffects().size();
        final String extended = getPluginInstance().getConfig().getString("potion-description-extended"),
                upgraded = getPluginInstance().getConfig().getString("potion-description-upgraded");

        return (color(getTranslatedName(potionMeta.getBasePotionData().getType()) + (potionMeta.getBasePotionData().isExtended()
                ? (" " + extended) : "") + (potionMeta.getBasePotionData().isUpgraded() ? (" " + upgraded) : ""))
                + (totalEffects > 0 ? (" " + getPluginInstance().getConfig().getString("potion-description-format"))
                .replace("{remainder}", getPluginInstance().getManager().formatNumber(totalEffects, false)) : ""));
    }

    /**
     * Gets roman numeral value from 1 - 10.
     *
     * @param number The number between 1 and 10.
     * @return The roman numeral.
     */
    public String getRomanNumeral(int number) {
        switch (number) {
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            case 7:
                return "VII";
            case 8:
                return "VIII";
            case 9:
                return "IX";
            case 10:
                return "X";
            default:
                return "I";
        }
    }

    /**
     * Obtains the price associated with the material and durability via base-block appearances.
     *
     * @param type       The material or ItemsAdder block id to check for.
     * @param durability The durability to check.
     * @return The price found.
     */
    public double getBBMPrice(String type, int durability) {
        double foundPrice = 0;

        final List<String> availableMaterialList = getPluginInstance().getMenusConfig().getStringList("base-block-menu.available-materials");
        for (int i = -1; ++i < availableMaterialList.size(); ) {
            final String line = availableMaterialList.get(i);
            if (!line.contains(":")) continue;

            String[] lineArgs = line.split(":");
            if (isNotNumeric(lineArgs[1])) continue;
            final int foundDurability = Integer.parseInt(lineArgs[1]);

            final String materialName = lineArgs[0].replace(" ", "_").replace("-", "_");
            if (!type.equalsIgnoreCase(materialName) || !(foundDurability <= -1 || foundDurability == durability))
                continue;

            if (isNotNumeric(lineArgs[2])) continue;
            foundPrice = Double.parseDouble(lineArgs[2]);
            break;
        }

        return foundPrice;
    }

    /**
     * Colors the text passed.
     *
     * @param message The message to translate.
     * @return The colored text.
     */
    public String color(String message) {
        if (message == null || message.isEmpty()) return message;
        if (Math.floor(getPluginInstance().getServerVersion()) >= 1_16) {
            Matcher matcher = hexPattern.matcher(message);
            while (matcher.find()) {
                final ChatColor hexColor = ChatColor.of(matcher.group());
                final String before = message.substring(0, matcher.start()), after = message.substring(matcher.end());
                matcher = hexPattern.matcher(message = (before + hexColor + after));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Formats a given number based on the locale.
     *
     * @param value     The numerical value.
     * @param isDecimal Whether the number contains a decimal.
     * @return The formatted number as a string.
     */
    public String formatNumber(double value, boolean isDecimal) {
        String formatted;
        int decimalPlaces = getPluginInstance().getConfig().getInt("minimum-fraction-digits");
        final boolean useUKFormatting = getPluginInstance().getConfig().getBoolean("use-uk-format");
        if (decimalPlaces > 0 && isDecimal) formatted = String.format("%,." + decimalPlaces + "f", value);
        else formatted = String.format("%,.0f", value);

        formatted = formatted.replace("\\s", "").replace("_", "");
        return getPluginInstance().getConfig().getBoolean("short-number-format") ? format((long) Double.parseDouble(formatted.replace(",", "")), useUKFormatting) : (useUKFormatting ?
                formatted.replace(
                        ".", "_COMMA_").replace(",", "_PERIOD_").replace("_PERIOD_", ".").replace("_COMMA_", ",") : formatted);
    }

    private String format(long value, boolean useUKFormatting) {
        String[] arr = {"", "K", "M", "B", "T", "Q", "E"};
        int index = 0;
        while ((value / 1000) >= 1) {
            value = value / 1000;
            index++;
        }

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        String format = String.format("%s%s", decimalFormat.format(value), arr[index]);
        return (useUKFormatting ? format.replace(".", ",") : format);
    }

    /**
     * Gets the max buy or sell price of a selected material.
     *
     * @param itemStack The item stack to check for.
     * @param isBuy     Whether it is buy or sell.
     * @return The found maximum.
     */
    public double getMaterialMaxPrice(ItemStack itemStack, boolean isBuy) {
        if (itemStack == null) return 0;
        ConfigurationSection maxSection = getPluginInstance().getConfig().getConfigurationSection("max-material-prices");
        if (maxSection != null) for (String keyName : maxSection.getKeys(false)) {
            if (keyName.toUpperCase().replace(" ", "_").replace("-", "_").equals(itemStack.getType().name())
                    || (Objects.requireNonNull(itemStack.getItemMeta()).hasDisplayName() && color(keyName).equals(itemStack.getItemMeta().getDisplayName())))
                return (isBuy ? new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("max-material-prices." + keyName + ".buy"))).doubleValue()
                        : new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("max-material-prices." + keyName + ".sell"))).doubleValue());
        }
        return (isBuy ? new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("buy-price-limit"))).doubleValue()
                : new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("sell-price-limit"))).doubleValue());
    }

    /**
     * Gets the min buy or sell price of a selected material.
     *
     * @param itemStack The item stack to check for.
     * @param isBuy     Whether it is buy or sell.
     * @return The found minimum.
     */
    public double getMaterialMinPrice(ItemStack itemStack, boolean isBuy) {
        if (itemStack == null) return 0;
        ConfigurationSection maxSection = getPluginInstance().getConfig().getConfigurationSection("min-material-prices");
        if (maxSection != null) for (String keyName : maxSection.getKeys(false))
            if (keyName.toUpperCase().replace(" ", "_").replace("-", "_").equals(itemStack.getType().name())
                    || (Objects.requireNonNull(itemStack.getItemMeta()).hasDisplayName() && color(keyName).equals(itemStack.getItemMeta().getDisplayName())))
                return (isBuy ? new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("min-material-prices." + keyName + ".buy"))).doubleValue()
                        : new BigDecimal(Objects.requireNonNull(getPluginInstance().getConfig().getString("min-material-prices." + keyName + ".sell"))).doubleValue());
        return -1;
    }

    /**
     * Checks if a shop already exists with the passed id.
     *
     * @param shopId The id to check for.
     * @return Whether the id exists in true or false format.
     */
    public boolean doesShopIdExist(UUID shopId) {
        return getShopMap().containsKey(shopId);
    }

    /**
     * Generates a new ID suited for a new display shop.
     *
     * @return a long value for the ID.
     */
    public UUID generateNewId() {
        UUID generatedId = UUID.randomUUID();
        return getShopMap().containsKey(generatedId) ? generateNewId() : generatedId;
    }

    /**
     * Gets the proper offsets based on configuration or defaults.
     *
     * @param shop The shop to obtain the offsets for.
     * @return The array of X, Y, and Z offsets.
     */
    public Double[] getBaseBlockOffsets(Shop shop) {
        if (shop.getStoredBaseBlockMaterial() != null) {
            List<String> offsets = getPluginInstance().getConfig().getStringList("material-based-offsets");
            for (int i = -1; ++i < offsets.size(); ) {
                String offsetString = offsets.get(i);
                if (!offsetString.contains(":") || !offsetString.contains(",")) continue;

                String[] offsetMainSplit = offsetString.split(":");
                if (!shop.getStoredBaseBlockMaterial().toUpperCase().contains(offsetMainSplit[0].toUpperCase()
                        .replace(" ", "_").replace("-", "_"))) continue;

                String[] offsetValueSplit = offsetString.replace(offsetMainSplit[0] + ":", "").split(",");
                if (offsetValueSplit.length < 3) continue;

                return new Double[]{Double.parseDouble(offsetValueSplit[0]), Double.parseDouble(offsetValueSplit[1]), Double.parseDouble(offsetValueSplit[2])};
            }
        }

        return new Double[]{0.5, -0.25, 0.5};
    }

    /**
     * Attempts to get a shop object from the shop map by its ID.
     *
     * @param shopId The ID to get the shop from
     * @return the shop object. (Can return NULL if the object does not exist)
     */
    public Shop getShopById(UUID shopId) {
        if (getShopMap().containsKey(shopId)) return getShopMap().get(shopId);
        return null;
    }

    /**
     * Get a shop from the passed chest if possible.
     *
     * @param location Chest location.
     * @return The shop if found.
     */
    public Shop getShop(Location location) {

        final Shop[] list = getShopMap().values().toArray(new Shop[0]);
        for (int i = -1; ++i < list.length; ) {
            final Shop shop = list[i];
            if (shop == null || shop.getBaseLocation() == null
                    || !shop.getBaseLocation().isSame(location)) continue;
            return shop;
        }

        return null;
    }

    /**
     * Creates a shop at the passed block owned by the passed player.
     *
     * @param player              The player to use as the owner.
     * @param block               The block to create the shop at.
     * @param shopItemAmount      The amount used for the shop item.
     * @param doCreationEffects   Whether to play the effects and sounds.
     * @param sendCreationMessage Whether to send the creation message.
     * @return The shop object.
     */
    public Shop createShop(Player player, Block block, int shopItemAmount, boolean doCreationEffects, boolean sendCreationMessage) {
        DShop shop = new DShop(getPluginInstance().getManager().generateNewId(), player.getUniqueId(),
                block.getLocation(), shopItemAmount, block.getType().name());
        shop.register();

        if (doCreationEffects) {
            String soundString = getPluginInstance().getConfig().getString("immersion-section.shop-creation-sound");
            if (soundString != null && !soundString.equalsIgnoreCase(""))
                block.getWorld().playSound(block.getLocation(),
                        Sound.valueOf(soundString), 1, 1);

            String particleString = getPluginInstance().getConfig().getString("immersion-section.shop-creation-particle");
            if (particleString != null && !particleString.equalsIgnoreCase(""))
                getPluginInstance().getPacketManager().getParticleHandler().displayParticle(player, particleString.toUpperCase()
                                .replace(" ", "_").replace("-", "_"),
                        block.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, 0, 12);
        }

        if (sendCreationMessage) {
            String message = getPluginInstance().getLangConfig().getString("shop-created");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
        }

        return shop;
    }

    /**
     * Attempts to load all available shops from the database.
     *
     * @param isAsync Whether the task is ran on the main thread.
     * @param cleanUp Deletes all shops that have invalid data (Example: No longer existing worlds).
     */
    public void loadShops(boolean isAsync, boolean cleanUp) {
        long loadedShops = 0;

        List<String> shopsToDelete = new ArrayList<>();

        // begins loading from SQLite.
        try {
            PreparedStatement statement = getPluginInstance().getDatabaseConnection().prepareStatement("SELECT Count(*) FROM shops;");

            long current = 0;
            ResultSet resultSet = statement.executeQuery();
            final long shopCount = (resultSet.next() ? resultSet.getInt(1) : 0),
                    shopCountPercentage = ((long) (shopCount * 0.15));
            resultSet.close();

            statement = getPluginInstance().getDatabaseConnection().prepareStatement("SELECT * FROM shops;");
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                try {
                    final String shopIdString = resultSet.getString("id");
                    if (shopIdString == null || shopIdString.equalsIgnoreCase("")) {

                        if (cleanUp) {
                            PreparedStatement deleteStatement = getPluginInstance().getDatabaseConnection()
                                    .prepareStatement("DELETE FROM shops where id = '" + shopIdString + "';");
                            deleteStatement.executeUpdate();
                            getPluginInstance().log(Level.WARNING, "[CLEANING] A shop was removed due to invalid id.");
                            deleteStatement.close();
                        }
                        continue;
                    }

                    final UUID shopId = UUID.fromString(shopIdString);
                    UUID ownerId = null;
                    final String ownerIdString = resultSet.getString("owner");
                    if (ownerIdString != null && !ownerIdString.equalsIgnoreCase(""))
                        ownerId = UUID.fromString(ownerIdString);

                    final int cleanInactiveTime = getPluginInstance().getConfig().getInt("clean-inactive-duration");
                    if (cleanInactiveTime >= 0 && ownerId != null) {
                        OfflinePlayer offlinePlayer = getPluginInstance().getServer().getOfflinePlayer(ownerId);
                        if (offlinePlayer != null) {
                            final long lastOnline = (((System.currentTimeMillis() - offlinePlayer.getLastPlayed()) / 1000) % 60);

                            if (lastOnline >= cleanInactiveTime) {
                                shopsToDelete.add(shopIdString);
                                continue;
                            }
                        }
                    }

                    final String baseLocationString = resultSet.getString((!getPluginInstance().hasColumn(resultSet,
                            "location") ? "base_" : "") + "location"), storedBaseBlockMaterialLine;
                    LClone baseLocation;
                    if (baseLocationString.contains(":")) {
                        final String[] locationStringArgsOuter = baseLocationString.split(":"),
                                locationStringArgs = locationStringArgsOuter[locationStringArgsOuter.length - 1].replace("/0", "").split(",");

                        baseLocation = new LClone(locationStringArgs[0], Double.parseDouble(locationStringArgs[1]),
                                Double.parseDouble(locationStringArgs[2]), Double.parseDouble(locationStringArgs[3]),
                                Float.parseFloat(locationStringArgs[4]), Float.parseFloat(locationStringArgs[5]));
                        storedBaseBlockMaterialLine = locationStringArgsOuter[0].replace(",", ":");
                    } else {
                        final String[] locationStringArgs = baseLocationString.split(",");
                        baseLocation = new LClone(locationStringArgs[0], Double.parseDouble(locationStringArgs[1]),
                                Double.parseDouble(locationStringArgs[2]), Double.parseDouble(locationStringArgs[3]),
                                Float.parseFloat(locationStringArgs[4]), Float.parseFloat(locationStringArgs[5]));

                        storedBaseBlockMaterialLine = (getPluginInstance().hasColumn(resultSet, "base_material")
                                ? resultSet.getString("base_material") : "STONE:0");
                    }

                    World world = getPluginInstance().getServer().getWorld(baseLocation.getWorldName());
                    if (world == null && cleanUp) {
                        Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                        deleteStatement.executeUpdate("DELETE FROM shops where id = '" + shopIdString + "';");
                        getPluginInstance().log(Level.WARNING, "[CLEANING] The shop \"" + shopIdString
                                + "\" was removed due to invalid world \"" + baseLocation.getWorldName() + "\".");
                        continue;
                    }

                    final String shopItemString = resultSet.getString("shop_item"),
                            tradeItemString = resultSet.getString("trade_item");

                    ItemStack shopItem, tradeItem;
                    shopItem = tradeItem = null;

                    if (shopItemString != null && !shopItemString.equalsIgnoreCase(""))
                        shopItem = getPluginInstance().getPacketManager().toItem(shopItemString);

                    if (tradeItemString != null && !tradeItemString.equalsIgnoreCase(""))
                        tradeItem = getPluginInstance().getPacketManager().toItem(tradeItemString);

                    DShop shop = new DShop(shopId, ownerId, shopItem, baseLocation,
                            resultSet.getInt("shop_item_amount"), storedBaseBlockMaterialLine);
                    shop.setTradeItem(tradeItem);
                    shop.setDescription(resultSet.getString("description"));
                    shop.setBuyCounter(resultSet.getInt("buy_counter"));
                    shop.setSellCounter(resultSet.getInt("sell_counter"));
                    shop.setBuyLimit(resultSet.getInt("buy_limit"));
                    shop.setSellLimit(resultSet.getInt("sell_limit"));
                    shop.setBuyPrice(resultSet.getDouble("buy_price"));
                    shop.setSellPrice(resultSet.getDouble("sell_price"));
                    shop.setStoredBalance(resultSet.getDouble("balance"));

                    String changeStamp = resultSet.getString("change_time_stamp");
                    if (changeStamp != null && !changeStamp.contains("."))
                        shop.setChangeTimeStamp(Long.parseLong(changeStamp));

                    shop.setCommandOnlyMode(resultSet.getBoolean("command_only_mode"));
                    shop.setStock(resultSet.getInt("stock"));

                    final String assistantsLine = resultSet.getString("assistants");
                    if (assistantsLine != null && !assistantsLine.isEmpty())
                        if (assistantsLine.contains(";")) {
                            String[] assistantArgs = assistantsLine.split(";");
                            for (String playerUniqueId : assistantArgs)
                                shop.getAssistants().add(UUID.fromString(playerUniqueId));
                        }

                    final String commandsLine = resultSet.getString("commands");
                    if (commandsLine != null && !commandsLine.equalsIgnoreCase(""))
                        if (commandsLine.contains(";")) {
                            final String[] commandsSplit = commandsLine.split(";");
                            for (String command : commandsSplit) shop.getCommands().add(command);
                        } else shop.getCommands().add(commandsLine);

                    String extraDataLine = resultSet.getString("extra_data");
                    if (extraDataLine != null && !extraDataLine.isEmpty())
                        if (extraDataLine.contains(";")) {
                            final String[] extraDataArgs = extraDataLine.split(";");

                            shop.setDynamicPriceChange(Boolean.parseBoolean(extraDataArgs[0]));
                            if (extraDataArgs.length > 2 && extraDataLine.contains(":")) {
                                String[] buySplit = extraDataArgs[1].split(":"), sellSplit = extraDataArgs[2].split(":");

                                String lastBuyStamp = buySplit[0];
                                if (lastBuyStamp != null && changeStamp != null && !changeStamp.contains("."))
                                    shop.setLastBuyTimeStamp(Long.parseLong(lastBuyStamp));
                                shop.setDynamicBuyCounter(Integer.parseInt(buySplit[1]));

                                String lastSellStamp = buySplit[0];
                                if (lastSellStamp != null && changeStamp != null && !changeStamp.contains("."))
                                    shop.setLastSellTimeStamp(Long.parseLong(lastSellStamp));
                                shop.setDynamicSellCounter(Integer.parseInt(sellSplit[1]));
                            }

                        } else shop.setDynamicPriceChange(Boolean.parseBoolean(extraDataLine));

                    shop.register();
                    loadedShops++;

                    if (world != null) if (isAsync) {
                        getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () -> fixAboveBlock(shop));
                    } else fixAboveBlock(shop);

                    current++;
                    if ((shopCountPercentage <= 0 || current % shopCountPercentage == 0 || current == shopCount))
                        getPluginInstance().log(Level.INFO, "Loading shops " + current + "/" + shopCount
                                + " (" + Math.min(100, (int) (((double) current / (double) shopCount) * 100)) + "%)...");
                } catch (Exception e) {
                    e.printStackTrace();
                    getPluginInstance().log(Level.WARNING, "The shop '"
                            + resultSet.getString("id") + "' failed to load(" + e.getMessage() + ").");
                }
            }

            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING, "An error occurred during shop load time (" + e.getMessage() + ").");
        }

        if (!shopsToDelete.isEmpty())
            for (String id : shopsToDelete) {
                try {
                    PreparedStatement statement = getPluginInstance().getDatabaseConnection()
                            .prepareStatement("DELETE FROM shops WHERE id = '" + id + "';");
                    statement.executeUpdate();
                    statement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    getPluginInstance().log(Level.WARNING, "An error occurred during shop load time (" + e.getMessage() + ").");
                }
            }

        if (loadedShops <= 0) getPluginInstance().log(Level.INFO, "No shops were found.");
    }

    private void fixAboveBlock(DShop shop) {
        final Location location = shop.getBaseLocation().asBukkitLocation();
        if (location == null) return;

        final BlockState blockState = location.getBlock().getRelative(BlockFace.UP).getState();
        blockState.setType(Material.AIR);
        blockState.update(true, false);
    }

    /**
     * Checks to see if the material is blocked or not.
     *
     * @param material The material to check for.
     * @return The result.
     */
    public boolean isBlockedMaterial(Material material) {
        List<String> materialList = getPluginInstance().getConfig().getStringList("blocked-material-list");
        for (int i = -1; ++i < materialList.size(); ) {
            if (material.name().equalsIgnoreCase(materialList.get(i)
                    .replace(" ", "_").replace("-", "_"))) return true;
        }

        return false;
    }

    /**
     * Saves all market regions to the database.
     */
    public synchronized void saveMarketRegions() {
        for (MarketRegion marketRegion : getPluginInstance().getManager().getMarketRegions()) {
            try {
                final String pointOneString = (marketRegion.getRegion().getPointOne().getWorldName() + ","
                        + marketRegion.getRegion().getPointOne().getX() + "," + marketRegion.getRegion().getPointOne().getY()
                        + "," + marketRegion.getRegion().getPointOne().getZ() + "," + marketRegion.getRegion().getPointOne().getYaw()
                        + "," + marketRegion.getRegion().getPointOne().getPitch()),
                        pointTwoString = (marketRegion.getRegion().getPointTwo().getWorldName() + ","
                                + marketRegion.getRegion().getPointTwo().getX() + "," + marketRegion.getRegion().getPointTwo().getY()
                                + "," + marketRegion.getRegion().getPointTwo().getZ() + "," + marketRegion.getRegion().getPointTwo().getYaw()
                                + "," + marketRegion.getRegion().getPointTwo().getPitch());

                final String extraDataLine = (marketRegion.getCost() + "," + marketRegion.getRenewCost()),
                        host = getPluginInstance().getConfig().getString("mysql.host"), syntax,
                        renterId = (marketRegion.getRenter() != null ? marketRegion.getRenter().toString() : "");
                if (host == null || host.isEmpty())
                    syntax = "INSERT OR REPLACE INTO market_regions(id, point_one, point_two, renter, extended_duration, rent_time_stamp, extra_data) VALUES('"
                            + marketRegion.getMarketId() + "', '" + pointOneString.replace("'", "\\'").replace("\"", "\\\"")
                            + "', '" + pointTwoString.replace("'", "\\'").replace("\"", "\\\"") + "', '"
                            + renterId + "', '" + marketRegion.getExtendedDuration() + "', '" + marketRegion.getRentedTimeStamp()
                            + "', '" + extraDataLine + "');";
                else
                    syntax = "INSERT INTO market_regions(id, point_one, point_two, renter, extended_duration, rent_time_stamp, extra_data) VALUES( '"
                            + marketRegion.getMarketId() + "', '" + pointOneString.replace("'", "\\'")
                            .replace("\"", "\\\"") + "', '" + pointTwoString.replace("'", "\\'")
                            .replace("\"", "\\\"") + "', '" + renterId + "', '" + marketRegion.getExtendedDuration()
                            + "', '" + marketRegion.getRentedTimeStamp() + "') ON DUPLICATE KEY UPDATE id = '" + marketRegion.getMarketId()
                            + "'," + " point_one = '" + pointOneString.replace("'", "\\'").replace("\"", "\\\"")
                            + "', point_two = '" + pointTwoString.replace("'", "\\'").replace("\"", "\\\"")
                            + "', renter = '" + renterId + "', extended_duration = '" + marketRegion.getExtendedDuration()
                            + "', rent_time_stamp = '" + marketRegion.getRentedTimeStamp() + "', '" + extraDataLine + "';";

                PreparedStatement preparedStatement = getPluginInstance().getDatabaseConnection().prepareStatement(syntax);
                preparedStatement.execute();
                preparedStatement.close();
            } catch (Exception e) {
                e.printStackTrace();
                getPluginInstance().log(Level.WARNING, "There was an issue saving the market region '"
                        + marketRegion.getMarketId() + "' (" + e.getMessage() + ").");
            }
        }
    }

    /**
     * Loads all market regions into the memory.
     *
     * @param cleanUp Deletes all market regions that have invalid data (Example: No longer existing worlds).
     */
    public void loadMarketRegions(boolean cleanUp) {
        long startTime = System.currentTimeMillis();
        int loadedRegions = 0, failedToLoadRegions = 0;

        File file = new File(getPluginInstance().getDataFolder(), "/market-regions.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection marketIdsSection = yaml.getConfigurationSection("");
        if (marketIdsSection != null)
            for (String marketId : marketIdsSection.getKeys(false)) {
                DRegion region = new DRegion();

                String worldOne = yaml.getString(marketId + ".point-one.world"), worldTwo = yaml.getString(marketId + ".point-two.world");
                if (worldOne == null || worldOne.equalsIgnoreCase("") || worldTwo == null || worldTwo.equalsIgnoreCase(""))
                    continue;

                region.setPointOne(new LClone(worldOne, yaml.getDouble(marketId + ".point-one.x"),
                        yaml.getDouble(marketId + ".point-one.y"), yaml.getDouble(marketId + ".point-one.z"), 0, 0));
                region.setPointTwo(new LClone(worldTwo, yaml.getDouble(marketId + ".point-two.x"),
                        yaml.getDouble(marketId + ".point-two.y"), yaml.getDouble(marketId + ".point-two.z"), 0, 0));


                getMarketRegions().add(new MRegion(getPluginInstance(), marketId, region));
                loadedRegions++;
            }

        file.renameTo(new File(getPluginInstance().getDataFolder(), "/old-mr.yml"));

        try (PreparedStatement statement = getPluginInstance().getDatabaseConnection().prepareStatement("SELECT * FROM market_regions;");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final String regionId = resultSet.getString("id");
                if (regionId == null || regionId.equalsIgnoreCase("")) {

                    if (cleanUp) {
                        Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                        deleteStatement.executeUpdate("DELETE FROM market_regions where id = '" + regionId + "';");
                        getPluginInstance().log(Level.WARNING, "[CLEANING] A market region was removed due an invalid id.");
                    }

                    continue;
                }

                if (doesMarketRegionExist(regionId)) continue;
                final String pointOneString = resultSet.getString("point_one"),
                        pointTwoString = resultSet.getString("point_two");
                if (pointOneString.contains(",") && pointTwoString.contains(",")) {
                    final String[] pointOneArgs = pointOneString.split(","), pointTwoArgs = pointTwoString.split(",");
                    final LClone pointOne = new LClone(pointOneArgs[0], Double.parseDouble(pointOneArgs[1]), Double.parseDouble(pointOneArgs[2]),
                            Double.parseDouble(pointOneArgs[3]), Float.parseFloat(pointOneArgs[4]), Float.parseFloat(pointOneArgs[5])),
                            pointTwo = new LClone(pointTwoArgs[0], Double.parseDouble(pointTwoArgs[1]), Double.parseDouble(pointTwoArgs[2]),
                                    Double.parseDouble(pointTwoArgs[3]), Float.parseFloat(pointTwoArgs[4]), Float.parseFloat(pointTwoArgs[5]));

                    if (getPluginInstance().getServer().getWorld(pointOne.getWorldName()) == null
                            || getPluginInstance().getServer().getWorld(pointTwo.getWorldName()) == null) {
                        if (cleanUp) {
                            Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                            deleteStatement.executeUpdate("DELETE FROM market_regions where id = '" + regionId + "';");
                            getPluginInstance().log(Level.WARNING, "[CLEANING] The market region \"" + regionId
                                    + "\" was removed due an invalid world that possibly no longer exists.");
                        }

                        continue;
                    }

                    if (pointOne.getWorldName() == null || pointOne.getWorldName().equalsIgnoreCase("")
                            || pointTwo.getWorldName() == null || pointTwo.getWorldName().equalsIgnoreCase("")
                            || !pointOne.getWorldName().equalsIgnoreCase(pointTwo.getWorldName())) {

                        if (cleanUp) {
                            Statement deleteStatement = getPluginInstance().getDatabaseConnection().createStatement();
                            deleteStatement.executeUpdate("DELETE FROM market_regions where id = '" + regionId + "';");
                            getPluginInstance().log(Level.WARNING, "[CLEANING] The market region \"" + regionId
                                    + "\" was removed due to mismatching region point worlds.");
                        }

                        continue;
                    }

                    DRegion region = new DRegion();
                    region.setPointOne(pointOne);
                    region.setPointTwo(pointTwo);

                    MRegion marketRegion = new MRegion(getPluginInstance(), regionId, region);

                    String renterUniqueId = resultSet.getString("renter");
                    if (renterUniqueId != null && !renterUniqueId.isEmpty()) {
                        final UUID renter = UUID.fromString(renterUniqueId);
                        marketRegion.setRenter(renter);
                    }

                    marketRegion.setExtendedDuration(resultSet.getInt("extended_duration"));
                    marketRegion.setRentedTimeStamp(resultSet.getString("rent_time_stamp"));

                    final String extraDataLine = resultSet.getString("extra_data");
                    if (extraDataLine != null && !extraDataLine.isEmpty()) {
                        final String[] args = extraDataLine.split(",");
                        marketRegion.setCost(Double.parseDouble(args[0]));
                        marketRegion.setRenewCost(Double.parseDouble(args[1]));
                    }

                    getPluginInstance().getManager().getMarketRegions().add(marketRegion);
                    loadedRegions++;
                }
            }
        } catch (SQLException e) {
            failedToLoadRegions += 1;
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING,
                    "This is just a warning stating that a market region has failed to load.");
        }

        if (loadedRegions > 0 || failedToLoadRegions > 0)
            getPluginInstance().log(Level.INFO, loadedRegions + " " + ((loadedRegions == 1)
                    ? "market region was" : "market regions were") + " loaded and " + failedToLoadRegions
                    + " " + ((failedToLoadRegions == 1) ? "market region" : "market regions")
                    + " failed to load. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    /**
     * Obtains the default base material without checking attached durability.
     *
     * @return The material type.
     */
    public Material getBaseBlockType() {
        final String material = getPluginInstance().getConfig().getString("shop-block-material");
        if (material != null && !material.isEmpty()) {
            if (material.contains(":"))
                return Material.getMaterial(material.split(":")[0].toUpperCase()
                        .replace(" ", "_").replace("-", "_"));
            else return Material.getMaterial(material.toUpperCase().replace(" ", "_").replace("-", "_"));
        }
        return null;
    }

    /**
     * Returns a list of all shops owned by the player.
     *
     * @param player The player to check for.
     * @return The list of shops the player owns.
     */
    public List<Shop> getPlayerShops(Player player) {
        List<Shop> shopList = new ArrayList<>();
        for (Shop shop : getShopMap().values())
            if (shop.getOwnerUniqueId() != null && shop.getOwnerUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString()))
                shopList.add(shop);

        return shopList;
    }

    /**
     * Gets if the player has reached the maximum amount of shops they can have.
     *
     * @param player The player to check.
     * @return Whether the player has exceeded their limit or not.
     */
    public boolean exceededShopLimit(Player player) {
        if (player.hasPermission("displayshops.limit.*")) return false;
        final List<Shop> shopList = getPlayerShops(player);
        final int shopLimit = getShopLimit(player);
        return (shopList.size() >= shopLimit);
    }

    /**
     * Gets the shop limit of the passed player.
     *
     * @param player The player to check for.
     * @return The limit they have on shops.
     */
    public int getShopLimit(Player player) {
        if (player.hasPermission("displayshops.limit.*")) return -1;
        int shopLimit = getPluginInstance().getConfig().getInt("default-shop-limit");
        for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
            if (permissionAttachmentInfo.getPermission().toLowerCase().startsWith("displayshops.limit.")) {
                String intValue = permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.limit.", "");
                if (isNotNumeric(intValue)) continue;

                int tempValue = Integer.parseInt(permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.limit.", ""));
                if (tempValue > shopLimit) shopLimit = tempValue;
            }
        }

        return shopLimit;
    }

    /**
     * Returns a list of all regions rented by the player.
     *
     * @param player The player to check for.
     * @return The list of rented regions.
     */
    public List<MarketRegion> getMarketRegions(Player player) {
        List<MarketRegion> regionList = new ArrayList<>();
        for (MarketRegion marketRegion : getMarketRegions())
            if (marketRegion.getRenter() != null && marketRegion.getRenter().toString()
                    .equalsIgnoreCase(player.getUniqueId().toString())) regionList.add(marketRegion);

        return regionList;
    }

    /**
     * Gets if the player has reached the maximum amount of rented regions they can have.
     *
     * @param player The player to check.
     * @return Whether the player has exceeded their limit or not.
     */
    public boolean exceededMarketRegionLimit(Player player) {
        if (player.hasPermission("displayshops.rlimit.*")) return false;
        final List<MarketRegion> regionList = getMarketRegions(player);
        final int regionLimit = getMarketRegionLimit(player);
        return (regionList.size() >= regionLimit);
    }

    /**
     * Gets the rented region limit of the passed player.
     *
     * @param player The player to check for.
     * @return The limit they have on rented regions.
     */
    public int getMarketRegionLimit(Player player) {
        if (player.hasPermission("displayshops.rlimit.*")) return -1;
        int regionLimit = getPluginInstance().getConfig().getInt("default-region-limit");
        for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
            if (permissionAttachmentInfo.getPermission().toLowerCase().startsWith("displayshops.rlimit.")) {
                String intValue = permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.rlimit.", "");
                if (isNotNumeric(intValue)) continue;

                int tempValue = Integer.parseInt(permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.rlimit.", ""));
                if (tempValue > regionLimit) regionLimit = tempValue;
            }
        }

        return regionLimit;
    }

    /**
     * Obtains the passed shop's max stock based on owner permissions or administrator bypasses.
     *
     * @param shop The shop to get the max stock for (obtains owner for permissions, returns max possible integer if the shop is admin or the owner has the "displayshops.stock.max").
     * @return The obtained max stock (defaults to configuration value or max possible integer, if the shop is admin).
     */
    public int getMaxStock(Shop shop) {
        int maxStock = ((shop.isAdminShop() && shop.getStock() <= -1)
                ? Integer.MAX_VALUE : getPluginInstance().getConfig().getInt("max-shop-stock"));
        if (!shop.isAdminShop() && shop.getOwnerUniqueId() != null) {
            OfflinePlayer owner = getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId());
            if (owner.getPlayer() != null) {
                final Player player = owner.getPlayer();
                for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
                    if (permissionAttachmentInfo.getPermission().toLowerCase().startsWith("displayshops.stock.")) {
                        String intValue = permissionAttachmentInfo.getPermission().toLowerCase()
                                .replace("displayshops.stock.", "");

                        if (intValue.equalsIgnoreCase("max")) return Integer.MAX_VALUE;
                        else if (isNotNumeric(intValue)) continue;

                        int tempValue = (int) Double.parseDouble(permissionAttachmentInfo.getPermission()
                                .toLowerCase().replace("displayshops.stock.", ""));
                        if (tempValue > maxStock) maxStock = tempValue;
                    }
                }
            }
        }

        return maxStock;
    }

    /**
     * Gets the player specific shop promotion item modifier.
     *
     * @param player The player to check.
     * @return The modifier for multiplication.
     */
    public double getPromotionPriceModifier(Player player) {
        double modifier = 1;
        for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
            if (permissionAttachmentInfo.getPermission().toLowerCase().startsWith("displayshops.pm.")) {
                String intValue = permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.pm.", "");
                if (isNotNumeric(intValue)) continue;

                double tempValue = Double.parseDouble(permissionAttachmentInfo.getPermission().toLowerCase().replace("displayshops.pm.", ""));
                if (tempValue > modifier) modifier = tempValue;
            }
        }

        return modifier;
    }

    /**
     * Obtains available space for a defined item.
     *
     * @param player    The player whose inventory needs to be checked.
     * @param itemStack The itemstack to check for.
     * @return The total available item amount space.
     */
    public int getInventorySpaceForItem(Player player, ItemStack itemStack) {
        int availableSpace = 0;

        if (getPluginInstance().getServerVersion() >= 1_9) {
            for (ItemStack item : player.getInventory().getStorageContents())
                if (item == null || item.getType().name().contains("AIR"))
                    availableSpace += itemStack.getType().getMaxStackSize();
                else if (itemStack.isSimilar(item))
                    availableSpace += Math.max(0, (itemStack.getType().getMaxStackSize() - item.getAmount()));

            return availableSpace;
        }

        for (int i = 8; ++i < Math.min(player.getInventory().getSize(), 44); ) {
            final ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().name().contains("AIR"))
                availableSpace += itemStack.getType().getMaxStackSize();
            else if (itemStack.isSimilar(item))
                availableSpace += Math.max(0, (itemStack.getType().getMaxStackSize() - item.getAmount()));
        }

        return availableSpace;
    }

    /**
     * Gives the passed item stack to the player the passed amount of times in the form of stacks.
     *
     * @param player           The player to give the item to.
     * @param itemStackToClone The item stack to make the stacks from.
     * @param amount           The amount of the items to give to the player (NOT stacks).
     */
    public void giveItemStacks(@NotNull Player player, @NotNull ItemStack itemStackToClone, int amount) {
        if (amount <= 0) return;

        ItemStack clone = itemStackToClone.clone();
        clone.setAmount(itemStackToClone.getAmount() * amount);
        FixedMetadataValue fixedMetadataValue = new FixedMetadataValue(getPluginInstance(), player.getUniqueId().toString());

        if (itemStackToClone.getType().getMaxStackSize() == 1) {
            ItemStack itemStack = itemStackToClone.clone();
            itemStack.setAmount(1);
            for (int i = -1; ++i < amount; ) {
                if (player.getInventory().firstEmpty() == -1) {
                    Item item = player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                    item.setMetadata("shop-owners", fixedMetadataValue);
                } else player.getInventory().addItem(itemStack);
            }
            return;
        }

        int stacks = (amount / itemStackToClone.getType().getMaxStackSize()),
                remainder = (amount % itemStackToClone.getType().getMaxStackSize());
        if (stacks <= 0 && remainder <= 0) return;

        if (stacks > 0) {
            ItemStack itemStack = itemStackToClone.clone();
            for (int i = -1; ++i < stacks; ) {
                itemStack.setAmount(itemStack.getType().getMaxStackSize());
                if (player.getInventory().firstEmpty() == -1) {
                    Item item = player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                    item.setMetadata("shop-owners", fixedMetadataValue);
                } else player.getInventory().addItem(itemStack);
            }
        }

        if (remainder > 0) {
            ItemStack itemStack = itemStackToClone.clone();
            itemStack.setAmount(remainder);
            if (player.getInventory().firstEmpty() == -1) {
                Item item = player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                item.setMetadata("shop-owners", fixedMetadataValue);
            } else player.getInventory().addItem(itemStack);
        }
    }

    /**
     * Remove a certain amount of a certain similar item.
     *
     * @param inventory The inventory to remove from.
     * @param itemStack The item to check for.
     * @param amount    The amount to remove.
     * @return Whether an item was removed or not.
     */
    public boolean removeItem(Inventory inventory, ItemStack itemStack, int amount) {
        int left = amount;
        boolean removedItems = false;

        if (itemStack != null) {
            for (int i = -1; ++i < inventory.getSize(); ) {
                ItemStack is = inventory.getItem(i);
                if (is == null) continue;

                if (isSimilar(is, itemStack)) {
                    if (left >= is.getAmount()) {
                        inventory.clear(i);
                        left -= is.getAmount();
                    } else {
                        if (left <= 0) break;
                        is.setAmount(is.getAmount() - left);
                        left = 0;
                    }

                    removedItems = true;
                }
            }

            if (inventory.getHolder() instanceof Player) ((Player) inventory.getHolder()).updateInventory();
        }

        return removedItems;
    }

    /**
     * Get amount of similar items.
     *
     * @param inventory The inventory to check.
     * @param itemStack The item to check for.
     * @return The total amount.
     */
    public int getItemAmount(Inventory inventory, ItemStack itemStack) {
        int amount = 0;

        if (itemStack != null)
            for (int i = -1; ++i < inventory.getSize(); ) {
                final ItemStack is = inventory.getItem(i);
                if (is == null) continue;
                if (isSimilar(is, itemStack)) amount += is.getAmount();
            }

        return amount;
    }

    /**
     * Checks if two items are identical using string format.
     *
     * @param itemOne The first item.
     * @param itemTwo The second item.
     * @return Whether the items are identical.
     */
    public boolean isSimilar(ItemStack itemOne, ItemStack itemTwo) {
        return itemOne.isSimilar(itemTwo);
    }

    /**
     * See if a string is NOT a numerical value.
     *
     * @param string The string to check.
     * @return Whether it is numerical or not.
     */
    public boolean isNotNumeric(String string) {
        if (string == null || string.isEmpty()) return true;

        final char[] chars = string.toCharArray();
        if (chars.length == 1 && !Character.isDigit(chars[0])) return true;

        final boolean onlyWholeNumbers = getPluginInstance().getConfig().getBoolean("whole-number-entries");
        for (int i = -1; ++i < string.length(); ) {
            final char c = chars[i];
            if (onlyWholeNumbers && (c == '.' || c == ',')) return true;
            if (!Character.isDigit(c) && (c != '.' && c != ',') && !((i == 0 && c == '-'))) return true;
        }

        return false;
    }

    /**
     * Wraps a string into multiple lines based on a word count.
     *
     * @param text          The long string to wrap.
     * @param wordLineLimit The line size in terms of word count.
     * @return wraps the string to multiple lines
     */
    public List<String> wrapString(String text, int wordLineLimit) {
        List<String> result = new ArrayList<>();

        final int longWordCount = getPluginInstance().getConfig().getInt("description-long-word-wrap");
        final String[] words = text.trim().split(" ");
        if (words.length > 0) {
            int wordCount = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = -1; ++i < words.length; ) {
                String word = words[i];
                if (wordCount < wordLineLimit) {

                    if (word.length() >= longWordCount && longWordCount > 0) word.substring(0, longWordCount);

                    sb.append(word).append(" ");
                    wordCount++;
                    continue;
                }

                result.add(sb.toString().trim());
                sb = new StringBuilder();
                sb.append(word).append(" ");
                wordCount = 1;
            }

            result.add(sb.toString().trim());
        }
        return result;
    }

    /**
     * Builds and returns the shop creation item.
     *
     * @param player The player to check for what base-block to use.
     * @param amount The amount of the item stack.
     * @return The shop creation physical item stack.
     */
    public ItemStack buildShopCreationItem(Player player, int amount) {
        CustomItem item = null;
        String materialName = getPluginInstance().getConfig().getString("shop-block-material");
        if (materialName != null)
            if (materialName.contains(":")) {
                String[] args = materialName.split(":");
                item = new CustomItem(getPluginInstance(), args[0], Integer.parseInt(args[1]), amount)
                        .setDisplayName(player, getPluginInstance().getConfig().getString("shop-creation-item.display-name"))
                        .setLore(player, getPluginInstance().getConfig().getStringList("shop-creation-item.lore"))
                        .setEnchantments(getPluginInstance().getConfig().getStringList("shop-creation-item.enchantments"))
                        .setItemFlags(getPluginInstance().getConfig().getStringList("shop-creation-item.flags"))
                        .setModelData(getPluginInstance().getConfig().getInt("shop-creation-item.model-data"));
            } else
                item = new CustomItem(getPluginInstance(), materialName, 0, amount)
                        .setDisplayName(player, getPluginInstance().getConfig().getString("shop-creation-item.display-name"))
                        .setLore(player, getPluginInstance().getConfig().getStringList("shop-creation-item.lore"))
                        .setEnchantments(getPluginInstance().getConfig().getStringList("shop-creation-item.enchantments"))
                        .setItemFlags(getPluginInstance().getConfig().getStringList("shop-creation-item.flags"))
                        .setModelData(getPluginInstance().getConfig().getInt("shop-creation-item.model-data"));

        if (getPluginInstance().getConfig().getBoolean("shop-creation-item.enchanted"))
            item.setEnchanted(true);

        return item.get();
    }

    /**
     * Builds and returns the shop currency item.
     *
     * @param amount The amount of the item stack.
     * @return The shop currency physical item stack.
     */
    public ItemStack buildShopCurrencyItem(int amount) {
        final String materialName = getPluginInstance().getConfig().getString("shop-currency-item.material");
        if (materialName != null && !materialName.isEmpty()) {
            if (materialName.contains(":")) {
                String[] args = materialName.split(":");
                return new CustomItem(getPluginInstance(), args[0], Integer.parseInt(args[1]), amount)
                        .setDisplayName(null, getPluginInstance().getConfig().getString("shop-currency-item.display-name"))
                        .setLore(null, getPluginInstance().getConfig().getStringList("shop-currency-item.lore"))
                        .setEnchantments(getPluginInstance().getConfig().getStringList("shop-currency-item.enchantments"))
                        .setItemFlags(getPluginInstance().getConfig().getStringList("shop-currency-item.flags"))
                        .setModelData(getPluginInstance().getConfig().getInt("shop-currency-item.model-data")).get();
            } else
                return new CustomItem(getPluginInstance(), materialName, 0, amount)
                        .setDisplayName(null, getPluginInstance().getConfig().getString("shop-currency-item.display-name"))
                        .setLore(null, getPluginInstance().getConfig().getStringList("shop-currency-item.lore"))
                        .setEnchantments(getPluginInstance().getConfig().getStringList("shop-currency-item.enchantments"))
                        .setItemFlags(getPluginInstance().getConfig().getStringList("shop-currency-item.flags"))
                        .setModelData(getPluginInstance().getConfig().getInt("shop-currency-item.model-data")).get();
        }
        return null;
    }

    private HashMap<Integer, List<ItemStack>> getBaseBlockSelectionItems(Shop shop, Player player, int inventorySize, DataPack dataPack) {
        HashMap<Integer, List<ItemStack>> pageMap = new HashMap<>();

        int currentPage = 1;
        List<ItemStack> pageContents = new ArrayList<>();

        List<String> allowedMaterialList = getPluginInstance().getMenusConfig().getStringList("base-block-menu.available-materials");
        if (getPluginInstance().getMenusConfig().getBoolean("base-block-menu.sort-alphabetically"))
            Collections.sort(allowedMaterialList);

        String storedMaterialLine = shop.getStoredBaseBlockMaterial();
        if (storedMaterialLine == null || storedMaterialLine.isEmpty())
            storedMaterialLine = getPluginInstance().getConfig().getString("shop-block-material");

        String currentMaterial = "CHEST";
        int currentDurability = -1;

        if (storedMaterialLine != null) {
            if (storedMaterialLine.contains(":")) {
                String[] args = storedMaterialLine.split(":");
                currentMaterial = args[0];
                currentDurability = Integer.parseInt(args[1]);
            } else currentMaterial = storedMaterialLine;
        }

        try {
            for (String allowedMaterialLine : allowedMaterialList) {

                ItemStack itemStack = null;
                double foundPrice = 0;

                String unlockId = allowedMaterialLine, typeId = "";
                if (allowedMaterialLine.contains(":")) {
                    String[] args = allowedMaterialLine.split(":");
                    unlockId = (args[0] + ":" + args[1]);

                    Material material = Material.getMaterial(args[0]);
                    if (material == null) {
                        if (getPluginInstance().isItemAdderInstalled()) {
                            dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                            if (customBlock != null) {
                                typeId = customBlock.getId();
                                itemStack = getPluginInstance().getPacketManager().updateNBT(customBlock.getItemStack(), "ds-type", typeId);
                            }
                        }
                    } else {
                        int durability = Integer.parseInt(args[1]);
                        itemStack = new ItemStack(material, 1, (byte) (Math.max(durability, 0)));
                        itemStack = getPluginInstance().getPacketManager().updateNBT(itemStack, "ds-type", material.name());
                    }

                    if (args.length >= 3 && !isNotNumeric(args[2])) foundPrice = Double.parseDouble(args[2]);
                } else {
                    Material material = Material.getMaterial(allowedMaterialLine);
                    if (material == null) {
                        if (getPluginInstance().isItemAdderInstalled()) {
                            dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(allowedMaterialLine);
                            if (customBlock != null) {
                                typeId = customBlock.getId();
                                itemStack = getPluginInstance().getPacketManager().updateNBT(customBlock.getItemStack(), "ds-type", typeId);
                            }
                        }
                    } else itemStack = getPluginInstance().getPacketManager().updateNBT(new ItemStack(material), "ds-type", material.name());
                }

                if (itemStack == null) continue;

                final ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    if (typeId.equalsIgnoreCase(currentMaterial) || (currentMaterial.equalsIgnoreCase(itemStack.getType().name())
                            && (currentDurability == itemStack.getDurability() || currentDurability <= -1))) {
                        itemMeta.setDisplayName(color(getPluginInstance().papiText(player, getPluginInstance()
                                .getMenusConfig().getString("base-block-menu.current-selection-item.display-name"))));
                        itemMeta.setLore(new ArrayList<String>() {{
                            for (String line : getPluginInstance().getMenusConfig().getStringList("base-block-menu.current-selection-item.lore"))
                                add(color(getPluginInstance().papiText(player, line)));
                        }});

                        if (getPluginInstance().getMenusConfig().getBoolean("base-block-menu.current-selection-item.enchanted")) {
                            try {
                                itemMeta.addEnchant(Enchantment.DURABILITY, 0, true);
                                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                            } catch (Exception e) {
                                e.printStackTrace();
                                getPluginInstance().log(Level.WARNING, "Failed to hide the enchantments on the current base-block item"
                                        + " for the selection GUI. Please disable this option for your version.");
                            }
                        }

                        itemStack.setItemMeta(itemMeta);
                    } else {

                        final boolean isUnlocked = dataPack.hasUnlockedBBM(unlockId), papiHere = (getPluginInstance().getPapiHelper() != null);
                        String name = Objects.requireNonNull(Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("base-block-menu."
                                + (isUnlocked ? "unlocked" : "locked") + "-item-format.display-name")));

                        if (getPluginInstance().isItemAdderInstalled()) {
                            dev.lone.itemsadder.api.CustomStack customStack = dev.lone.itemsadder.api.CustomStack.getInstance(typeId);
                            if (customStack != null) name = name.replace("{material}", customStack.getDisplayName());
                            else name = name.replace("{material}", getTranslatedName(itemStack.getType()));
                        } else name = name.replace("{material}", getTranslatedName(itemStack.getType()));

                        itemMeta.setDisplayName(color(papiHere ? getPluginInstance().getPapiHelper().replace(player, name) : name));

                        final double finalFoundPrice = foundPrice;
                        itemMeta.setLore(new ArrayList<String>() {{
                            if (allowedMaterialLine.contains(":")) {
                                String[] args = allowedMaterialLine.split(":");
                                for (String line : getPluginInstance().getMenusConfig().getStringList("base-block-menu." + (isUnlocked ? "unlocked" : "locked") + "-item-format.lore"))
                                    if (!line.equalsIgnoreCase("{requirement}"))
                                        add(color(getPluginInstance().papiText(player, line.replace("{price}",
                                                getPluginInstance().getManager().formatNumber(finalFoundPrice, true)))));
                                    else if (args.length >= 4) add(color(getPluginInstance().papiText(player, args[3])));
                            } else
                                for (String line : getPluginInstance().getMenusConfig().getStringList("base-block-menu." + (isUnlocked ? "unlocked" : "locked") + "-item-format.lore"))
                                    add(color(getPluginInstance().papiText(player, line.replace("{price}",
                                            getPluginInstance().getManager().formatNumber(finalFoundPrice, true)))));
                        }});

                        itemStack.setItemMeta(itemMeta);
                    }
                }

                if (pageContents.size() >= (inventorySize - 9)) {
                    pageMap.put(currentPage, new ArrayList<>(pageContents));
                    pageContents.clear();
                    currentPage += 1;
                }

                pageContents.add(itemStack);
            }

            if (!pageContents.isEmpty()) pageMap.put(currentPage, new ArrayList<>(pageContents));
        } catch (Exception e) {
            e.printStackTrace();
            getPluginInstance().log(Level.WARNING, "One of the materials found in the "
                    + "'available-materials' list in the 'menus.yml' is incorrect.");
        }

        return pageMap;
    }

    /**
     * Gets the base-block selection GUI and calculates the player's access, current shop base-block, etc.
     *
     * @param player The player to use for permission basing.
     * @param shop   The shop to use to get information from.
     * @return The complete GUI.
     */
    public Inventory getBaseBlockSelectionMenu(Player player, Shop shop) {
        final String title = getPluginInstance().getMenusConfig().getString("base-block-menu.title");
        int size = getPluginInstance().getMenusConfig().getInt("base-block-menu.size");
        if (size < 18) size = 18;

        Inventory inventory = getPluginInstance().getServer().createInventory(null, size, color(title));

        String material = getPluginInstance().getMenusConfig().getString("base-block-menu.background-item.material");
        if (material != null && !material.isEmpty()) {
            ItemStack backgroundItem = new CustomItem(getPluginInstance(), material, getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu" +
                            ".background-item" +
                            ".display-name")).setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.model-data")).get();
            if (backgroundItem != null) for (int i = ((size - 1) - 9); ++i < inventory.getSize(); )
                inventory.setItem(i, backgroundItem);
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        dataPack.setBaseBlockPageMap(getBaseBlockSelectionItems(shop, player, inventory.getSize(), dataPack));
        dataPack.setCurrentBaseBlockPage(1);

        if (!dataPack.getBaseBlockPageMap().containsKey(1)) {
            dataPack.setBaseBlockPageMap(null);
            return inventory;
        }

        int nSlot = getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.slot");
        if (nSlot > -1 && nSlot < inventory.getSize() && dataPack.getBaseBlockPageMap().containsKey(dataPack.getCurrentBaseBlockPage() + 1)) {
            ItemStack nextPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("base-block-menu.next-page-item.material"),
                    getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu.next-page-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.model-data")).get();
            inventory.setItem(nSlot, nextPage);
        }

        int pSlot = getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.slot");
        if (pSlot > -1 && pSlot < inventory.getSize() && dataPack.getBaseBlockPageMap().containsKey(dataPack.getCurrentBaseBlockPage() - 1)) {
            ItemStack previousPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("base-block-menu.previous-page-item.material"),
                    getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu.previous-page-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.model-data")).get();
            inventory.setItem(pSlot, previousPage);
        }

        for (ItemStack itemStack : dataPack.getBaseBlockPageMap().get(1)) {
            itemStack = getPluginInstance().getPacketManager().updateNBT(itemStack, "ds-bbm", shop.getShopId().toString());
            inventory.addItem(itemStack);
        }

        applyDecorativeItems(player, inventory, "BASE_BLOCK");
        return inventory;
    }

    private boolean checkShopAgainstFilters(@NotNull Shop shop, @Nullable OfflinePlayer offlinePlayer, @Nullable String currentFilterType, @Nullable String filter) {

        if (shop.getBaseLocation() == null || !getPluginInstance().getMenusConfig().getBoolean("shop-visit-menu.show-admin-shops")
                && shop.isAdminShop() || shop.getShopItem() == null || shop.getStock() == 0 || shop.getStock() < shop.getShopItemAmount())
            return false;

        if (currentFilterType != null && !currentFilterType.isEmpty()) {

            final String buyType = getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.buy-type"),
                    sellType = getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.sell-type");

            if ((currentFilterType.equals(buyType) && shop.getBuyPrice(false) < 0)
                    || (currentFilterType.equals(sellType) && shop.getSellPrice(false) < 0)) return false;
        }

        if (filter != null) {
            if (offlinePlayer != null) {
                if (shop.getOwnerUniqueId() == null || !shop.getOwnerUniqueId().toString().equals(offlinePlayer.getUniqueId().toString()))
                    return false;

                OfflinePlayer op = getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId());
                return op == null || !op.hasPlayedBefore() || op.getName() == null || op.getName().equalsIgnoreCase(offlinePlayer.getName());
            } else return shop.getShopItem().getItemMeta() == null
                    || ChatColor.stripColor(shop.getShopItem().getItemMeta().getDisplayName().toLowerCase()).contains(filter.toLowerCase())
                    || shop.getShopItem().getType().name().toLowerCase().replace("_", " ").contains(filter.toLowerCase())
                    || shop.getDescription().toLowerCase().contains(filter.toLowerCase())
                    || (shop.getBuyPrice(false) + " " + shop.getSellPrice(false)).contains(filter);
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    public void loadVisitPage(@Nullable Player player, @NotNull DataPack dataPack, @NotNull Inventory inventory, @Nullable String currentFilterType, @Nullable String filter) {
        final boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null),
                forceUse = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");

        OfflinePlayer offlinePlayer;
        if (filter != null && !filter.isEmpty()) {
            OfflinePlayer op = getPluginInstance().getServer().getOfflinePlayer(filter);
            if (op != null && op.hasPlayedBefore()) offlinePlayer = op;
            else offlinePlayer = null;
        } else offlinePlayer = null;

        final int filterSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.slot"),
                typeSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.slot");

        ItemStack filterItem = new CustomItem(getPluginInstance(),
                getPluginInstance().getMenusConfig().getString("shop-visit-menu.filter-item.material"),
                getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.durability"),
                getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.amount"))
                .setDisplayName(player, Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-visit-menu.filter-item.display-name"))
                        .replace("{filter}", ((filter != null && !filter.isEmpty()) ? filter
                                : Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-visit-menu.filter-item.no-previous")))))
                .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.filter-item.lore"))
                .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.filter-item.enchantments"))
                .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.filter-item.flags"))
                .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.model-data")).get();
        inventory.setItem(filterSlot, filterItem);

        ItemStack typeItem = new CustomItem(getPluginInstance(),
                getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.material"),
                getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.durability"),
                getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.amount"))
                .setDisplayName(player, Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.display-name"))
                        .replace("{type}", ((currentFilterType != null && !currentFilterType.isEmpty()) ? currentFilterType
                                : Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.both-type")))))
                .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.type-item.lore"))
                .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.type-item.enchantments"))
                .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.type-item.flags"))
                .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.model-data")).get();
        inventory.setItem(typeSlot, typeItem);

        final int pageSize = (getPluginInstance().getMenusConfig().getInt("shop-visit-menu.size") - 9),
                currentPage = dataPack.getCurrentVisitPage(),
                startingIndex = (Math.max(0, (currentPage - 1)) * (pageSize - 1));

        List<Pair<Shop, ItemStack>> visitShopContents = getPluginInstance().getManager().getShopVisitItemList();
        int counter = 0;

        for (int i = (startingIndex - 1); ++i < visitShopContents.size(); ) {

            final Pair<Shop, ItemStack> pair = visitShopContents.get(i);
            final Shop shop = pair.getKey();

            if (!checkShopAgainstFilters(shop, offlinePlayer, currentFilterType, filter)) continue;

            inventory.setItem(counter, pair.getValue());

            counter++;
            if (counter >= pageSize) break;
        }

        if (player != null && filter != null && !filter.isEmpty()) {
            final String message = getPluginInstance().getLangConfig().getString((counter > 0) ? "visit-filter-count" : "visit-filter-none");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message
                        .replace("{count}", getPluginInstance().getManager().formatNumber(counter, false))
                        .replace("{filter}", filter));
        }

        final boolean hasNextPage = ((((currentPage + 1) - 1) * pageSize) <= visitShopContents.size()),
                hasPrevPage = ((((currentPage - 1) - 1) * pageSize) >= 0);

        int rSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.refresh-page-item.slot");
        if (rSlot > -1 && rSlot < inventory.getSize()) {
            ItemStack nextPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-visit-menu.refresh-page-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.refresh-page-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.refresh-page-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-visit-menu.refresh-page-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.refresh-page-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.refresh-page-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.refresh-page-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.refresh-page-item.model-data")).get();
            inventory.setItem(rSlot, nextPage);
        }

        int nSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.next-page-item.slot");
        if (nSlot > -1 && nSlot < inventory.getSize() && hasNextPage) {
            ItemStack nextPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-visit-menu.next-page-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.next-page-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.next-page-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-visit-menu.next-page-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.next-page-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.next-page-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.next-page-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.next-page-item.model-data")).get();
            inventory.setItem(nSlot, nextPage);
        }

        int pSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.previous-page-item.slot");
        if (pSlot > -1 && pSlot < inventory.getSize() && hasPrevPage) {
            ItemStack previousPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig()
                    .getString("shop-visit-menu.previous-page-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.previous-page-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.previous-page-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-visit-menu.previous-page-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.previous-page-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.previous-page-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.previous-page-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.previous-page-item.model-data")).get();
            inventory.setItem(pSlot, previousPage);
        }

        applyDecorativeItems(player, inventory, "VISIT");
    }

    /**
     * Builds the visit menu where players can select shops to visit.
     *
     * @param player Player to build the menu for.
     * @return The inventory built.
     */
    public Inventory buildVisitMenu(Player player, @Nullable String filter) {
        final String title = getPluginInstance().getMenusConfig().getString("shop-visit-menu.title");
        int size = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.size");
        if (size < 18) size = 18;

        Inventory inventory = getPluginInstance().getServer().createInventory(null, size, color(title));

        String material = getPluginInstance().getMenusConfig().getString("shop-visit-menu.background-item.material");
        if (material != null && !material.isEmpty()) {
            ItemStack backgroundItem = new CustomItem(getPluginInstance(), material,
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-visit-menu.background-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.model-data")).get();
            if (backgroundItem != null) for (int i = ((size - 1) - 9); ++i < inventory.getSize(); )
                inventory.setItem(i, backgroundItem);
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);

        dataPack.setCurrentVisitPage(1);
        loadVisitPage(player, dataPack, inventory, getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.buy-type"), filter);

        return inventory;
    }

    /**
     * Builds and sets the shop edit menu from the configuration to the variable.
     *
     * @param player The player the edit menu needs to be built for.
     * @return The built inventory.
     */
    public Inventory buildShopEditMenu(Player player) {
        final DataPack dataPack = getDataPack(player);

        Shop shop = dataPack.getSelectedShop();
        if (shop == null) {
            sendMessage(player, getPluginInstance().getLangConfig().getString("shop-edit-invalid"));
            return null;
        }

        Inventory inventory;
        final int inventorySize = getPluginInstance().getMenusConfig().getInt("shop-edit-menu.size");
        if ((inventorySize > 5 && (inventorySize % 9 == 0) && inventorySize <= 54))
            inventory = getPluginInstance().getServer().createInventory(null, inventorySize,
                    color(getPluginInstance().getMenusConfig().getString("shop-edit-menu.title")));
        else inventory = getPluginInstance().getServer().createInventory(null, InventoryType.HOPPER,
                color(getPluginInstance().getMenusConfig().getString("shop-edit-menu.title")));

        final boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null);
        String material = getPluginInstance().getMenusConfig().getString("shop-edit-menu.background-item.material");
        if (material != null && !material.isEmpty()) {
            ItemStack backgroundItem = new CustomItem(getPluginInstance(), material,
                    getPluginInstance().getMenusConfig().getInt("shop-edit-menu.background-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-edit-menu.background-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-edit-menu.background-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-edit-menu.background-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-edit-menu.background-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-edit-menu.background-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-edit-menu.background-item.model-data")).get();
            if (backgroundItem != null)
                for (int i = -1; ++i < inventory.getSize(); ) inventory.setItem(i, backgroundItem);
        }

        final ConfigurationSection cs = getPluginInstance().getMenusConfig().getConfigurationSection("shop-edit-menu");
        if (cs == null) return null;

        final Collection<String> keys = cs.getKeys(false);
        if (keys.isEmpty()) return null;

        for (String key : keys) {
            if (key.equalsIgnoreCase("title") || key.equalsIgnoreCase("size") || key.equalsIgnoreCase("background-item"))
                continue;

            final int slot = getPluginInstance().getMenusConfig().getInt("shop-edit-menu." + key + ".slot");
            if (slot < 0 || slot >= inventorySize) continue;

            List<String> newLore = new ArrayList<>(), stockList = getPluginInstance().getMenusConfig().getStringList("shop-edit-menu." + key + ".lore");
            for (int i = -1; ++i < stockList.size(); ) {
                String line = stockList.get(i);
                if (useVault && (line.toLowerCase().contains("{no-vault}") || line.toLowerCase().startsWith("{no-vault}")
                        || line.toLowerCase().endsWith("{no-vault}") || line.equalsIgnoreCase("{no-vault}")))
                    continue;

                String newLine = line.replace("{price}",
                        formatNumber(getPluginInstance().getMenusConfig().getDouble("shop-edit-menu." + key + ".price"), true));
                newLore.add(getPluginInstance().papiText(player, newLine));
            }

            final ItemStack itemStack = new CustomItem(getPluginInstance(),
                    getPluginInstance().getMenusConfig().getString("shop-edit-menu." + key + ".material"),
                    getPluginInstance().getMenusConfig().getInt("shop-edit-menu." + key + ".durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-edit-menu." + key + ".amount"), shop,
                    shop.getShopItem().getType().getMaxStackSize(), 1).setDisplayName(player,
                            getPluginInstance().getMenusConfig().getString("shop-edit-menu." + key + ".display-name"))
                    .setLore(player, newLore).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-edit-menu."
                            + key + ".enchantments")).setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-edit-menu."
                            + key + ".flags")).setModelData(getPluginInstance().getMenusConfig().getInt("shop-edit-menu." + key + ".model-data")).get();
            if (itemStack == null) continue;

            inventory.setItem(slot, itemStack);
        }

        applyDecorativeItems(player, inventory, "EDIT");
        return inventory;
    }

    /**
     * Builds and sets the shop transaction menu from the configuration to the variable.
     *
     * @param player The player to personalize the menu for.
     * @param shop   The shop the interactions should be made for.
     */
    public Inventory buildTransactionMenu(Player player, Shop shop) {
        Inventory inventory;
        int inventorySize = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.size");
        if ((inventorySize > 5 && (inventorySize % 9 == 0) && inventorySize <= 54))
            inventory = getPluginInstance().getServer().createInventory(null, inventorySize,
                    color(getPluginInstance().getMenusConfig().getString("shop-transaction-menu.title")));
        else
            inventory = getPluginInstance().getServer().createInventory(null, InventoryType.HOPPER,
                    color(getPluginInstance().getMenusConfig().getString("shop-transaction-menu.title")));

        String material = getPluginInstance().getMenusConfig().getString("shop-transaction-menu.background-item.material");
        if (material != null && !material.isEmpty()) {
            ItemStack backgroundItem = new CustomItem(getPluginInstance(), material,
                    getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.background-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.background-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-transaction-menu.background-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.background-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.background-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.background-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.background-item.model-data")).get();
            if (backgroundItem != null) for (int i = -1; ++i < inventory.getSize(); )
                inventory.setItem(i, backgroundItem);
        }

        updateTransactionMenu(inventory, player, shop, 1);
        return inventory;
    }

    /**
     * Updates the transaction gui with live information.
     *
     * @param inventory The inventory to update.
     * @param shop      The shop to use the information from.
     * @param player    The player to personalize the menu for.
     */
    public void updateTransactionMenu(Inventory inventory, Player player, Shop shop, int unitCount) {
        final String materialName = getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-item.material");
        if (materialName == null || materialName.equalsIgnoreCase("")) return;

        Material material = Material.getMaterial(materialName);
        if (material == null) return;

        if (unitCount <= 0) unitCount = 1;
        else if (unitCount >= material.getMaxStackSize()) unitCount = material.getMaxStackSize();

        final boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null),
                syncBalance = getPluginInstance().getConfig().getBoolean("sync-owner-balance");
        final int buyItemSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.buy-item.slot"),
                sellItemSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.sell-item.slot"),
                unitSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.slot"),
                unitIncreaseSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-increase-item.slot"),
                unitDecreaseSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-decrease-item.slot");
        DataPack dataPack = getDataPack(player);

        if (buyItemSlot >= 0 && buyItemSlot < inventory.getSize()) {
            ItemStack buyItem;
            if (shop.getBuyPrice(true) < 0) {
                buyItem = getActionBlockedItem(player, shop, material, unitCount,
                        getPluginInstance().getLangConfig().getString("transaction-reasons.no-buy-price"));
                if (buyItem != null) inventory.setItem(buyItemSlot, buyItem);
            } else if (shop.getStock() < (shop.getShopItemAmount() * unitCount) && !(shop.isAdminShop() && shop.getStock() < 0)) {
                buyItem = getActionBlockedItem(player, shop, material, unitCount,
                        getPluginInstance().getLangConfig().getString("transaction-reasons.insufficient-stock"));
                if (buyItem != null) inventory.setItem(buyItemSlot, buyItem);
            } else if (dataPack.hasMetTransactionLimit(shop, true)) {
                buyItem = getActionBlockedItem(player, shop, material, unitCount,
                        getPluginInstance().getLangConfig().getString("transaction-reasons.buy-count-exceeded"));
                if (buyItem != null) inventory.setItem(buyItemSlot, buyItem);
            } else if (getInventorySpaceForItem(player, shop.getShopItem()) < (shop.getShopItemAmount() * unitCount)) {
                buyItem = getActionBlockedItem(player, shop, material, unitCount,
                        getPluginInstance().getLangConfig().getString("transaction-reasons.inventory-full"));
                if (buyItem != null) inventory.setItem(buyItemSlot, buyItem);
            } else {
                buyItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.buy-item.material"),
                        getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.buy-item.durability"),
                        getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.buy-item.amount"), shop, material.getMaxStackSize(), unitCount)
                        .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-transaction-menu.buy-item.display-name"))
                        .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.buy-item.lore"))
                        .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.buy-item.enchantments"))
                        .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.buy-item.flags"))
                        .setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.buy-item.model-data")).get();
                if (buyItem != null) inventory.setItem(buyItemSlot, buyItem);
            }
        }

        if (shop.getShopItem() != null) {
            final ItemStack previewItem = shop.getShopItem().clone();
            previewItem.setAmount(Math.min(shop.getShopItemAmount(), previewItem.getType().getMaxStackSize()));

            if (!useVault) {
                ItemMeta itemMeta = previewItem.getItemMeta();
                if (itemMeta != null) {
                    List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore()), previewLore = getPluginInstance().getMenusConfig().getStringList("shop" +
                            "-transaction-menu.preview-trade-item-lore");
                    for (int i = -1; ++i < previewLore.size(); )
                        lore.add(color(getPluginInstance().papiText(player, previewLore.get(i))));
                    itemMeta.setLore(lore);
                    previewItem.setItemMeta(itemMeta);
                }
            }

            final int previewSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.preview-slot");
            if (previewSlot >= 0 && previewSlot < inventory.getSize()) inventory.setItem(previewSlot, previewItem);
        }

        if (sellItemSlot >= 0 && sellItemSlot < inventory.getSize()) {
            final double currentBalance = (shop.isAdminShop() ? -1 : getCurrencyBalance(getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId()), shop, useVault));
            ItemStack sellItem;
            if ((shop.getSellPrice(true) < 0)) {
                sellItem = getActionBlockedItem(player, shop, material, unitCount, getPluginInstance().getLangConfig().getString("transaction-reasons.no-sell-price"));
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            } else if ((!shop.isAdminShop() && ((syncBalance ? currentBalance : shop.getStoredBalance()) < shop.getSellPrice(true)))) {
                sellItem = getActionBlockedItem(player, shop, material, unitCount, getPluginInstance().getLangConfig().getString("transaction-reasons.insufficient-shop-balance"));
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            } else if (!(shop.isAdminShop() && shop.getStock() < 0) && (shop.getStock() + (shop.getShopItemAmount() * unitCount)) > getMaxStock(shop)) {
                sellItem = getActionBlockedItem(player, shop, material, unitCount, getPluginInstance().getLangConfig().getString("transaction-reasons.stock-exceeded"));
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            } else if (dataPack.hasMetTransactionLimit(shop, false)) {
                sellItem = getActionBlockedItem(player, shop, material, unitCount, getPluginInstance().getLangConfig().getString("transaction-reasons.sell-count-exceeded"));
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            } else if (shop.isAdminShop() && shop.getCommands().size() > 0) {
                sellItem = getActionBlockedItem(player, shop, material, unitCount, getPluginInstance().getLangConfig().getString("transaction-reasons.commands-present"));
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            } else {
                sellItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.sell-item.material"), getPluginInstance().getMenusConfig().getInt(
                        "shop-transaction-menu.sell-item.durability"), getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.sell-item.amount"), shop, material.getMaxStackSize(),
                        unitCount).setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-transaction-menu.sell-item.display-name")).setLore(player,
                        getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.sell-item.lore")).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop" +
                        "-transaction-menu.sell-item.enchantments")).setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.sell-item.flags")).setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.sell-item.model-data")).get();
                if (sellItem != null) inventory.setItem(sellItemSlot, sellItem);
            }
        }

        if (unitSlot >= 0 && unitSlot < inventory.getSize()) {
            final ItemStack unitItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.durability"), unitCount, shop, material.getMaxStackSize(), unitCount).setDisplayName(player,
                    getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-item.display-name")).setLore(player, getPluginInstance().getMenusConfig().getStringList("shop" +
                    "-transaction" +
                    "-menu" +
                    ".unit-item.lore")).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-item.enchantments")).setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-item.flags")).setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.model-data")).get();
            if (unitItem != null) inventory.setItem(unitSlot, unitItem);
        }

        if (unitIncreaseSlot >= 0 && unitIncreaseSlot < inventory.getSize()) {
            final ItemStack unitIncreaseItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-increase-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-increase-item.durability"), getPluginInstance().getMenusConfig().getInt("shop-transaction-menu" +
                    ".unit-increase-item" +
                    ".amount"), shop, material.getMaxStackSize(), unitCount).setDisplayName(player,
                    getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-increase-item.display-name")).setLore(player, getPluginInstance().getMenusConfig().getStringList("shop" +
                    "-transaction-menu.unit-increase-item.lore")).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-increase-item.enchantments")).setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-increase-item.flags")).setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-increase-item.model-data")).get();
            if (unitIncreaseItem != null) inventory.setItem(unitIncreaseSlot, unitIncreaseItem);
        }

        if (unitDecreaseSlot >= 0 && unitDecreaseSlot < inventory.getSize()) {
            final ItemStack unitDecreaseItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-decrease-item.material"),
                    getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-decrease-item.durability"), getPluginInstance().getMenusConfig().getInt("shop-transaction-menu" +
                    ".unit-decrease-item" +
                    ".amount"), shop, material.getMaxStackSize(), unitCount).setDisplayName(player,
                    getPluginInstance().getMenusConfig().getString("shop-transaction-menu.unit-decrease-item.display-name")).setLore(player, getPluginInstance().getMenusConfig().getStringList("shop" +
                    "-transaction-menu.unit-decrease-item.lore")).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-decrease-item.enchantments")).setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.unit-decrease-item.flags")).setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-decrease-item.model-data")).get();
            if (unitDecreaseItem != null) inventory.setItem(unitDecreaseSlot, unitDecreaseItem);
        }

        applyDecorativeItems(player, inventory, "TRANSACTION");
    }

    private void applyDecorativeItems(Player player, Inventory inventory, String menuType) {
        ConfigurationSection decorativeSection = getPluginInstance().getMenusConfig().getConfigurationSection("decorative-items");
        if (decorativeSection == null) return;

        for (String itemId : decorativeSection.getKeys(false)) {
            String menuId = decorativeSection.getString(itemId + ".menu");

            if (menuId == null || menuId.isEmpty() || !menuId.equalsIgnoreCase(menuType))
                continue;

            final int slot = decorativeSection.getInt(itemId + ".slot");
            if (slot < 0 || slot >= inventory.getSize()) continue;

            CustomItem customItem = new CustomItem(getPluginInstance(), decorativeSection.getString(itemId + ".material"),
                    decorativeSection.getInt(itemId + ".durability"), decorativeSection.getInt(itemId + ".amount"))
                    .setDisplayName(player, decorativeSection.getString(itemId + ".display-name"))
                    .setLore(player, new ArrayList<String>() {{
                        for (String line : decorativeSection.getStringList(itemId + ".lore"))
                            add(color(line));
                    }}).setEnchantments(decorativeSection.getStringList(itemId + ".enchantments"))
                    .setItemFlags(decorativeSection.getStringList(itemId + ".flags"))
                    .setModelData(decorativeSection.getInt(itemId + ".model-data"));

            inventory.setItem(slot, customItem.get());
        }
    }

    private ItemStack getActionBlockedItem(Player player, Shop shop, Material material, int unitCount, String reason) {
        return new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("shop-transaction-menu.blocked-item.material"),
                getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.blocked-item.durability"),
                getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.blocked-item.amount"),
                shop, material.getMaxStackSize(), unitCount)
                .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-transaction-menu.blocked-item.display-name"))
                .setLore(player, new ArrayList<String>() {{
                    for (String line : getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.blocked-item.lore")) {
                        if (line.equalsIgnoreCase("{reason}")) {
                            if (reason != null && !reason.isEmpty()) add(color(reason));
                            continue;
                        }
                        add(color(line));
                    }
                }}).setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.blocked-item.enchantments"))
                .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.blocked-item.flags"))
                .setModelData(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.blocked-item.model-data")).get();
    }

    /**
     * Checks if the passed world is in the world blacklist.
     *
     * @param world The world to check for.
     * @return Whether the world is blocked or not.
     */
    public boolean isBlockedWorld(World world) {
        List<String> worldList = getPluginInstance().getConfig().getStringList("blocked-worlds");
        for (int i = -1; ++i < worldList.size(); )
            if (worldList.get(i).equalsIgnoreCase(world.getName())) return true;
        return false;
    }

    /**
     * Obtains the currency balance of the passed player.
     *
     * @param player   The player to get the balance of.
     * @param shop     The shop to get the trade-item from.
     * @param useVault Whether to use Vault methods.
     * @return The found player balance amount.
     */
    public double getCurrencyBalance(OfflinePlayer player, Shop shop, boolean useVault) {
        if (player == null || shop == null) return 0;
        if (useVault) return getPluginInstance().getVaultEconomy().getBalance(player);
        else if (player.getPlayer() != null) {
            final ItemStack currencyItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use") || shop.getTradeItem() == null) ?
                    getPluginInstance().getManager().buildShopCurrencyItem(1) : shop.getTradeItem();
            return getPluginInstance().getManager().getItemAmount(player.getPlayer().getInventory(), currencyItem);
        }
        return 0;
    }

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public HashMap<UUID, Shop> getShopMap() {
        return shopMap;
    }

    private void setShopMap(HashMap<UUID, Shop> shopMap) {
        this.shopMap = shopMap;
    }

    public List<MarketRegion> getMarketRegions() {
        return marketRegions;
    }

    private void setMarketRegions(List<MarketRegion> marketRegions) {
        this.marketRegions = marketRegions;
    }

    public HashMap<UUID, DataPack> getDataPackMap() {
        return dataPackMap;
    }

    private void setDataPackMap(HashMap<UUID, DataPack> dataPackMap) {
        this.dataPackMap = dataPackMap;
    }

    public List<Pair<Shop, ItemStack>> getShopVisitItemList() {
        return shopVisitItemList;
    }

}