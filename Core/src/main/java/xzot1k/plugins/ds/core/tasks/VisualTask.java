/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.tasks;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.objects.Shop;

import java.util.ArrayList;
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
        if (getPluginInstance().getManager().getShopMap() != null && !getPluginInstance().getManager().getShopMap().isEmpty()) {
            for (Player player : getPluginInstance().getServer().getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;

                for (Shop shop : new ArrayList<>(getPluginInstance().getManager().getShopMap().values())) {
                    if (shop == null || shop.getBaseLocation() == null) continue;

                    if (getPlayersToRefresh().contains(player.getUniqueId())) {
                        shop.kill(player);
                        getPluginInstance().killCurrentShopPacket(player);
                        getShopsToRefresh().remove(shop.getShopId());
                    } else if (getShopsToRefresh().contains(shop.getShopId())) {
                        shop.kill(player);
                        getPluginInstance().killCurrentShopPacket(player);
                        getShopsToRefresh().remove(shop.getShopId());
                    }

                    final boolean packetExists = (getPluginInstance().getDisplayPacketMap().containsKey(player.getUniqueId())
                            && getPluginInstance().getDisplayPacketMap().get(player.getUniqueId()).containsKey(shop.getShopId())),
                            tooFarAway = (!shop.getBaseLocation().getWorldName().equalsIgnoreCase(player.getWorld().getName())
                                    || shop.getBaseLocation().distance(player.getLocation(), true) > 16);
                    if (packetExists && tooFarAway) {
                        shop.kill(player);
                        continue;
                    } else if (!tooFarAway && packetExists) continue;

                    getPluginInstance().sendDisplayPacket(shop, player, isAlwaysDisplay());
                }

                getPlayersToRefresh().remove(player.getUniqueId());
                if (isAlwaysDisplay()) continue;

                Shop tempCurrentShop = null;
                if (!getPluginInstance().getShopMemory().isEmpty() && getPluginInstance().getShopMemory().containsKey(player.getUniqueId())) {
                    final UUID shopId = getPluginInstance().getShopMemory().get(player.getUniqueId());
                    if (shopId != null) tempCurrentShop = getPluginInstance().getManager().getShopMap().get(shopId);
                }

                final Shop currentShop = tempCurrentShop, foundShopAtLocation = getPluginInstance().getManager().getShopRayTraced(player.getWorld().getName(),
                        player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), getViewDistance());

                if (foundShopAtLocation == null) {
                    if (currentShop != null) getPluginInstance().sendDisplayPacket(currentShop, player, false);
                    getPluginInstance().getShopMemory().remove(player.getUniqueId());
                    continue;
                }

                if (currentShop != null && currentShop.getShopId().toString().equals(foundShopAtLocation.getShopId().toString()))
                    continue;

                if (currentShop != null) getPluginInstance().sendDisplayPacket(currentShop, player, false);

                if (!getPluginInstance().getManager().getShopMap().containsKey(foundShopAtLocation.getShopId()))
                    return;

                getPluginInstance().sendDisplayPacket(foundShopAtLocation, player, true);
                getPluginInstance().getShopMemory().put(player.getUniqueId(), foundShopAtLocation.getShopId());
            }

            if (getPluginInstance().getConfig().getBoolean("run-gc-immediately") && getGcCounter() >= 15) {
                setGcCounter(0);
                System.gc();
            }
        }
    }

    public void refreshShop(Shop shop) {
        if (shop == null || shop.getShopId() == null) return;
        if (getShopsToRefresh() != null && (getShopsToRefresh().isEmpty() || !getShopsToRefresh().contains(shop.getShopId())))
            getShopsToRefresh().add(shop.getShopId());
    }

    public void refreshShops(Player player) {
        if (player == null) return;
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