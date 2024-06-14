/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.tasks;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.objects.Display;
import xzot1k.plugins.ds.api.objects.Shop;

import java.util.LinkedList;
import java.util.UUID;

public class VisualTask extends BukkitRunnable {

    private DisplayShops pluginInstance;
    private int viewDistance, gcCounter;
    private boolean alwaysDisplay;
    private LinkedList<UUID> shopsToRefresh, playersToRefresh;

    public VisualTask(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
        setShopsToRefresh(new LinkedList<>());
        setPlayersToRefresh(new LinkedList<>());
        setGcCounter(0);
        setAlwaysDisplay(getPluginInstance().getConfig().getBoolean("always-display"));
        setViewDistance(getPluginInstance().getConfig().getInt("view-distance"));
    }

    @Override
    public void run() {
        if (getPluginInstance().getManager().getShopMap() == null || getPluginInstance().getManager().getShopMap().isEmpty()) return;

        for (Shop shop : getPluginInstance().getManager().getShopMap().values()) {
            if (shop == null || shop.getBaseLocation() == null) continue;

            // if display manager exists, use new displays
            if (getPluginInstance().getDisplayManager() != null) {

                Display display = getPluginInstance().getDisplayManager().getDisplay(shop.getShopId());
                if (display == null) {continue;}

                display.update();
                display.rotate();

                for (Player player : getPluginInstance().getServer().getOnlinePlayers()) {
                    if (player == null || !player.isOnline()) {continue;}

                    final Shop foundShopAtLocation = getPluginInstance().getManager().getShopRayTraced(player.getWorld().getName(),
                            player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), getViewDistance());

                    boolean isFocused = foundShopAtLocation != null && foundShopAtLocation.getShopId().toString().equals(shop.getShopId().toString());
                    display.show(player, isFocused);
                }

                continue;
            }

            for (Player player : getPluginInstance().getServer().getOnlinePlayers()) {
                if (getPlayersToRefresh().contains(player.getUniqueId()) || getShopsToRefresh().contains(shop.getShopId())) {
                    shop.kill(player);
                    getPluginInstance().killCurrentShopPacket(player);
                    getShopsToRefresh().remove(shop.getShopId());
                    getPlayersToRefresh().remove(player.getUniqueId());
                }

                final boolean packetExists = (getPluginInstance().getDisplayPacketMap().containsKey(player.getUniqueId())
                        && getPluginInstance().getDisplayPacketMap().get(player.getUniqueId()).containsKey(shop.getShopId())),
                        tooFarAway = (!shop.getBaseLocation().getWorldName().equalsIgnoreCase(player.getWorld().getName())
                                || shop.getBaseLocation().distance(player.getLocation(), true) > 16);

                if (tooFarAway) {
                    shop.kill(player);
                    continue;
                }

                if (isAlwaysDisplay()) {
                    if (packetExists) continue;

                    getPluginInstance().sendDisplayPacket(shop, player, true);
                    continue;
                }

                if (!packetExists) getPluginInstance().sendDisplayPacket(shop, player, false);

                Shop currentShop = null;
                if (!getPluginInstance().getShopMemory().isEmpty() && getPluginInstance().getShopMemory().containsKey(player.getUniqueId())) {
                    final UUID shopId = getPluginInstance().getShopMemory().getOrDefault(player.getUniqueId(), null);
                    if (shopId != null) currentShop = getPluginInstance().getManager().getShopById(shopId);
                }

                final Shop foundShopAtLocation = getPluginInstance().getManager().getShopRayTraced(player.getWorld().getName(),
                        player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), getViewDistance());

                if (foundShopAtLocation == null) {
                    if (currentShop != null) getPluginInstance().sendDisplayPacket(currentShop, player, false);
                    getPluginInstance().getShopMemory().remove(player.getUniqueId());
                    continue;
                }

                if (currentShop != null) {
                    if (!currentShop.getShopId().toString().equals(foundShopAtLocation.getShopId().toString())) {
                        getPluginInstance().sendDisplayPacket(currentShop, player, false);
                        getPluginInstance().getShopMemory().remove(player.getUniqueId());
                    } else continue;
                }

                getPluginInstance().sendDisplayPacket(foundShopAtLocation, player, true);
                getPluginInstance().getShopMemory().put(player.getUniqueId(), foundShopAtLocation.getShopId());
            }
        }

        if (getPluginInstance().getConfig().getBoolean("run-gc-immediately") && getGcCounter() >= 15) {
            setGcCounter(0);
            System.gc();
        }
    }

    public void refreshShop(Shop shop) {
        if (getPluginInstance().getDisplayManager() != null) {return;}

        if (shop == null || shop.getShopId() == null) return;
        if (getShopsToRefresh() != null && (getShopsToRefresh().isEmpty() || !getShopsToRefresh().contains(shop.getShopId())))
            getShopsToRefresh().add(shop.getShopId());
    }

    public void refreshShops(Player player) {
        if (player == null || getPluginInstance().getDisplayManager() != null) return;

        if (getPlayersToRefresh() != null && (getPlayersToRefresh().isEmpty() || !getPlayersToRefresh().contains(player.getUniqueId())))
            getPlayersToRefresh().add(player.getUniqueId());
    }

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    private void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    private boolean isAlwaysDisplay() {
        return alwaysDisplay;
    }

    private void setAlwaysDisplay(boolean alwaysDisplay) {
        this.alwaysDisplay = alwaysDisplay;
    }

    private LinkedList<UUID> getShopsToRefresh() {
        return shopsToRefresh;
    }

    private void setShopsToRefresh(LinkedList<UUID> shopsToRefresh) {
        this.shopsToRefresh = shopsToRefresh;
    }

    private LinkedList<UUID> getPlayersToRefresh() {
        return playersToRefresh;
    }

    private void setPlayersToRefresh(LinkedList<UUID> playersToRefresh) {
        this.playersToRefresh = playersToRefresh;
    }

    public int getGcCounter() {
        return gcCounter;
    }

    public void setGcCounter(int gcCounter) {
        this.gcCounter = gcCounter;
    }

}