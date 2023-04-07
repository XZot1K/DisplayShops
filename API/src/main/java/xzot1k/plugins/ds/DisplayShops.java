/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xzot1k.plugins.ds.api.Manager;
import xzot1k.plugins.ds.api.PacketManager;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.api.objects.Shop;

import java.io.File;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DisplayShops extends JavaPlugin implements DisplayShopsAPI {

    private static DisplayShops pluginInstance;

    public DisplayShops() {
        DisplayShops.pluginInstance = this;
    }

    public static DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void log(Level level, String message) {

    }

    @Override
    public void setupTasks() {

    }

    @Override
    public String getLatestVersion() {
        return null;
    }

    @Override
    public long getBlockedItemId(ItemStack itemStack) {
        return -1;
    }

    @Override
    public void reloadConfigs() {

    }

    @Override
    public FileConfiguration getLangConfig() {
        return null;
    }

    @Override
    public FileConfiguration getMenusConfig() {
        return null;
    }

    @Override
    public void saveDefaultConfigs() {

    }

    @Override
    public void refreshShop(Shop shop) {}

    @Override
    public DisplayPacket getDisplayPacket(Shop shop, Player player) {
        return null;
    }

    @Override
    public void killCurrentShopPacket(Player player) {

    }

    @Override
    public void clearDisplayPackets(Player player) {

    }

    @Override
    public void removeDisplayPacket(Shop shop, Player player) {

    }

    @Override
    public void updateDisplayPacket(Shop shop, Player player, DisplayPacket displayPacket) {

    }

    @Override
    public void sendDisplayPacket(Shop shop, Player player, boolean showHolograms) {

    }

    @Override
    public Manager getManager() {
        return null;
    }

    @Override
    public double getServerVersion() {
        return 0;
    }

    @Override
    public boolean isPaperSpigot() {
        return false;
    }

    @Override
    public boolean isPrismaInstalled() {
        return false;
    }

    @Override
    public Connection getDatabaseConnection() {
        return null;
    }

    @Override
    public List<UUID> getTeleportingPlayers() {
        return null;
    }

    @Override
    public HashMap<UUID, UUID> getShopMemory() {
        return null;
    }

    @Override
    public Economy getVaultEconomy() {
        return null;
    }

    @Override
    public PacketManager getPacketManager() {return null;}

    @Override
    public File getLoggingFile() {
        return null;
    }

    @Override
    public void writeToLog(String text) {

    }

}