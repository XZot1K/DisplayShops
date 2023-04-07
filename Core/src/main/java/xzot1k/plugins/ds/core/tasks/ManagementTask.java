/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.tasks;

import org.apache.commons.lang.WordUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.objects.DataPack;
import xzot1k.plugins.ds.api.objects.MarketRegion;
import xzot1k.plugins.ds.api.objects.RecoveryData;
import xzot1k.plugins.ds.api.objects.Shop;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Level;

public class ManagementTask extends BukkitRunnable {

    private final boolean dynamicPricesEnabled;
    private DisplayShops pluginInstance;
    private int autoSaveCounter, mysqlDumpCounter;
    private int autoSaveInterval, dynamicResetDuration, mysqlDumpInterval;

    public ManagementTask(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
        setAutoSaveCounter(0);
        setMysqlDumpCounter(0);
        setAutoSaveInterval(getPluginInstance().getConfig().getInt("auto-save-interval"));
        setMysqlDumpInterval(getPluginInstance().getConfig().getInt("mysql.dump-interval"));
        setDynamicResetDuration(getPluginInstance().getConfig().getInt("dynamic-price-change"));
        this.dynamicPricesEnabled = getPluginInstance().getConfig().getBoolean("dynamic-prices");
    }

    @Override
    public void run() {
        setAutoSaveCounter(getAutoSaveCounter() + 1);
        final boolean isSaveTime = (getAutoSaveCounter() >= getAutoSaveInterval());
        if (isSaveTime) setAutoSaveCounter(0);

        final List<Shop> shopList = new ArrayList<>(getPluginInstance().getManager().getShopMap().values());
        for (Shop shop : shopList) {
            if (shop == null) continue;

            if (isDynamicPricesEnabled() && shop.canDynamicPriceChange()) {
                boolean wasUpdated = false;
                if (shop.isReadyForDynamicReset(getDynamicResetDuration(), EconomyCallType.BUY)) {
                    shop.setDynamicBuyCounter(0);
                    wasUpdated = true;
                }
                if (shop.isReadyForDynamicReset(getDynamicResetDuration(), EconomyCallType.SELL)) {
                    shop.setDynamicSellCounter(0);
                    wasUpdated = true;
                }

                if (wasUpdated) getPluginInstance().refreshShop(shop);
            }

            if (isSaveTime) shop.save(false);
        }

        for (MarketRegion marketRegion : getPluginInstance().getManager().getMarketRegions()) {
            int timeUntilExpire;
            if (marketRegion != null && marketRegion.getRenter() != null && marketRegion.getRentedTimeStamp() != null && !marketRegion.getRentedTimeStamp().isEmpty())
                if ((timeUntilExpire = marketRegion.timeUntilExpire()) <= 0) marketRegion.reset();
                else if (getPluginInstance().getConfig().getIntegerList("rent-notify-intervals").contains(timeUntilExpire)) {
                    getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () -> {
                        Player renter = getPluginInstance().getServer().getPlayer(marketRegion.getRenter());
                        if (renter != null)
                            getPluginInstance().getManager().sendMessage(renter, getPluginInstance().getLangConfig().getString("rent-notification")
                                    .replace("{id}", WordUtils.capitalize(marketRegion.getMarketId())).replace("{time}", marketRegion.formattedTimeUntilExpire()));
                    });
                }
        }

        if (isSaveTime) {
            getPluginInstance().getManager().saveMarketRegions();
            final Set<Map.Entry<UUID, DataPack>> dataPacks = new HashSet<>(getPluginInstance().getManager().getDataPackMap().entrySet());
            try {
                Statement statement = getPluginInstance().getDatabaseConnection().createStatement();
                for (Map.Entry<UUID, DataPack> dataPackEntry : dataPacks) {
                    if (dataPackEntry == null) continue;
                    getPluginInstance().getManager().saveDataPack(statement, dataPackEntry.getKey(), dataPackEntry.getValue(), false, false);
                    OfflinePlayer player = getPluginInstance().getServer().getPlayer(dataPackEntry.getKey());
                    if (player == null || !player.isOnline())
                        getPluginInstance().getManager().getDataPackMap().remove(dataPackEntry.getKey());
                }

                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
                getPluginInstance().log(Level.WARNING, "There was an issue saving due to an SQL issue.");
            }
        }

        final String host = getPluginInstance().getConfig().getString("mysql.host");
        if (host != null && !host.isEmpty()) {
            setMysqlDumpCounter(getMysqlDumpCounter() + 1);
            final boolean isDumpTime = (getMysqlDumpCounter() >= getMysqlDumpInterval());
            if (isDumpTime) {
                try {
                    getPluginInstance().exportMySQLDatabase();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                setMysqlDumpCounter(0);
            }
        }

        for (Player player : getPluginInstance().getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) return;

            RecoveryData recoveryData = RecoveryData.getRecoveryData(player.getUniqueId());
            if (recoveryData == null) return;

            if (recoveryData.getCurrency() > 0 && getPluginInstance().getVaultEconomy() != null) {
                getPluginInstance().getVaultEconomy().depositPlayer(player, recoveryData.getCurrency());
                recoveryData.setCurrency(0);
            }

            int amount = 0;
            if (recoveryData.getItemAmount() > 0) {
                amount = recoveryData.getItemAmount();
                recoveryData.setItemAmount(0);
            }

            if (recoveryData.getItem() != null) {
                final int finalAmount = amount;
                getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () ->
                        getPluginInstance().getManager().giveItemStacks(player, recoveryData.getItem().clone(), finalAmount));
                recoveryData.setItem(null);
            }

            if (recoveryData.getCurrency() <= 0 && recoveryData.getItemAmount() <= 0 && recoveryData.getItem() == null) {
                RecoveryData.clearRecovery(player.getUniqueId());
                return;
            }

            RecoveryData.updateRecovery(player.getUniqueId(), recoveryData);
        }
    }

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    private int getAutoSaveInterval() {
        return autoSaveInterval;
    }

    private void setAutoSaveInterval(int autoSaveInterval) {
        this.autoSaveInterval = autoSaveInterval;
    }

    private int getDynamicResetDuration() {
        return dynamicResetDuration;
    }

    private void setDynamicResetDuration(int dynamicResetDuration) {
        this.dynamicResetDuration = dynamicResetDuration;
    }

    public boolean isDynamicPricesEnabled() {
        return dynamicPricesEnabled;
    }

    public int getAutoSaveCounter() {
        return autoSaveCounter;
    }

    public void setAutoSaveCounter(int autoSaveCounter) {
        this.autoSaveCounter = autoSaveCounter;
    }

    public int getMysqlDumpCounter() {
        return mysqlDumpCounter;
    }

    public void setMysqlDumpCounter(int mysqlDumpCounter) {
        this.mysqlDumpCounter = mysqlDumpCounter;
    }

    public int getMysqlDumpInterval() {
        return mysqlDumpInterval;
    }

    public void setMysqlDumpInterval(int mysqlDumpInterval) {
        this.mysqlDumpInterval = mysqlDumpInterval;
    }
}