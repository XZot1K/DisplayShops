/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShopsAPI;
import xzot1k.plugins.ds.api.objects.Shop;

public class AffordCheckEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final DisplayShopsAPI pluginInstance;
    private final Player investor;
    private final OfflinePlayer producer;
    private final double price, taxedPrice;
    private final EconomyCallEvent economyCallEvent;
    private final Shop shop;
    private boolean cancelled, canInvestorAfford, canProducerAfford;

    public AffordCheckEvent(@NotNull DisplayShopsAPI pluginInstance, @NotNull Player investor, @NotNull OfflinePlayer producer, boolean canInvestorAfford,
                            boolean canProducerAfford, double price, double taxedPrice, @NotNull EconomyCallEvent economyCallEvent, @NotNull Shop shop) {
        this.pluginInstance = pluginInstance;
        this.investor = investor;
        this.producer = producer;
        setCanInvestorAfford(canInvestorAfford);
        setCanProducerAfford(canProducerAfford);
        this.price = price;
        this.taxedPrice = taxedPrice;
        this.economyCallEvent = economyCallEvent;
        this.shop = shop;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    private DisplayShopsAPI getPluginInstance() {
        return pluginInstance;
    }

    public Player getInvestor() {
        return investor;
    }

    public OfflinePlayer getProducer() {
        return producer;
    }

    public boolean canInvestorAfford() {
        return canInvestorAfford;
    }

    public void setCanInvestorAfford(boolean canInvestorAfford) {
        this.canInvestorAfford = canInvestorAfford;
    }

    public boolean canProducerAfford() {
        return canProducerAfford;
    }

    public void setCanProducerAfford(boolean canProducerAfford) {
        this.canProducerAfford = canProducerAfford;
    }

    public double getPrice() {
        return price;
    }

    public double getTaxedPrice() {
        return taxedPrice;
    }

    public EconomyCallEvent getEconomyCallEvent() {
        return economyCallEvent;
    }

    public Shop getShop() {
        return shop;
    }
}