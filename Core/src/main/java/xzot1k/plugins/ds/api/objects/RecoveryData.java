/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class RecoveryData {


    private double currency;
    private int itemAmount;
    private ItemStack item;

    public static RecoveryData getRecoveryData(@NotNull UUID playerUniqueId) {
        RecoveryData recoveryData = new RecoveryData(playerUniqueId);
        if (recoveryData.getItem() == null && recoveryData.getItemAmount() <= 0 && recoveryData.getCurrency() <= 0) return null;
        return new RecoveryData(playerUniqueId);
    }

    public static void updateRecovery(@NotNull UUID playerUniqueId, @NotNull RecoveryData recoveryData) {
        final String itemToString = (recoveryData.getItem() != null ? DisplayShops.getPluginInstance().getPacketManager().toString(recoveryData.getItem()) : ""),
                host = DisplayShops.getPluginInstance().getConfig().getString("mysql.host"), syntax,
                recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency REAL, item_amount INTEGER, item TEXT)";
        if (host == null || host.isEmpty())
            syntax = ("INSERT OR REPLACE INTO recovery(uuid, currency, item_amount, item) VALUES('" + playerUniqueId + "', '" + recoveryData.getCurrency()
                    + "', '" + recoveryData.getItemAmount() + "', '" + itemToString + "');");
        else
            syntax = ("INSERT INTO recovery(uuid, currency, item_amount, item) VALUES( '" + playerUniqueId + "', '" + recoveryData.getCurrency() + "', '"
                    + recoveryData.getItemAmount() + "', '" + itemToString + "') ON DUPLICATE KEY UPDATE uuid = '" + playerUniqueId + "', currency = '"
                    + recoveryData.getCurrency() + "', item_amount = '" + recoveryData.getItemAmount() + "', item = '" + itemToString + "';");

        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection().prepareStatement("CREATE TABLE IF NOT EXISTS recovery "
                + recoveryParameters + ";")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue creating the recovery table.");
        }

        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection().prepareStatement(syntax)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue retrieving recovery data for \"" + playerUniqueId + "\".");
        }
    }

    public static void clearRecovery(@NotNull UUID playerUniqueId) {
        final String recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency REAL, item_amount INTEGER, item TEXT)";
        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection().prepareStatement("CREATE TABLE IF NOT EXISTS recovery "
                + recoveryParameters + ";")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue creating the recovery table.");
        }

        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection()
                .prepareStatement("DELETE FROM recovery WHERE id = '" + playerUniqueId + "';")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue retrieving recovery data for \"" + playerUniqueId + "\".");
        }
    }

    public RecoveryData(@NotNull UUID playerUniqueId) {
        setCurrency(0);
        setItemAmount(0);
        setItem(null);

        final String recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency REAL, item_amount INTEGER, item TEXT)";
        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection().prepareStatement("CREATE TABLE IF NOT EXISTS recovery "
                + recoveryParameters + ";")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue creating the recovery table.");
        }

        try (PreparedStatement statement = DisplayShops.getPluginInstance().getDatabaseConnection()
                .prepareStatement("SELECT * FROM recovery WHERE uuid = '" + playerUniqueId + "';");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                setCurrency(resultSet.getDouble("currency"));
                setItemAmount(resultSet.getInt("item_amount"));

                String itemString = resultSet.getString("item");
                if (itemString != null && !itemString.isEmpty())
                    setItem(DisplayShops.getPluginInstance().getPacketManager().toItem(itemString));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue retrieving recovery data for \"" + playerUniqueId + "\".");
        }
    }

    public double getCurrency() {
        return currency;
    }

    public void setCurrency(double currency) {
        this.currency = currency;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

}
