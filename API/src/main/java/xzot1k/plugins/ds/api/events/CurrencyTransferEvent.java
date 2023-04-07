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
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.objects.Shop;

public class CurrencyTransferEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final DisplayShopsAPI pluginInstance;
    private final boolean reversedTransfer, shouldChargeInvestor;
    private final Player investor;
    private final OfflinePlayer producer;
    private final Shop shop;
    private final EconomyCallType economyCallType;
    private final double price, taxedPrice;
    private final EconomyCallEvent economyCallEvent;
    private boolean cancelled;
    private boolean chargedInvestor;

    public CurrencyTransferEvent(@NotNull DisplayShopsAPI pluginInstance, @NotNull Shop shop, @NotNull EconomyCallType economyCallType, @NotNull Player investor,
                                 @NotNull OfflinePlayer producer, double price, double taxedPrice, boolean reversedTransfer, boolean chargedInvestor,
                                 boolean shouldChargeInvestor, @NotNull EconomyCallEvent economyCallEvent) {
        this.pluginInstance = pluginInstance;
        this.investor = investor;
        this.producer = producer;
        this.shop = shop;
        this.economyCallType = economyCallType;
        this.price = price;
        this.taxedPrice = taxedPrice;
        this.reversedTransfer = reversedTransfer;
        this.economyCallEvent = economyCallEvent;
        this.chargedInvestor = chargedInvestor;
        this.shouldChargeInvestor = shouldChargeInvestor;
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

    public Shop getShop() {
        return shop;
    }

    public EconomyCallType getEconomyCallType() {
        return economyCallType;
    }

    public double getPrice() {
        return price;
    }

    public double getTaxedPrice() {
        return taxedPrice;
    }

    /**
     * Checks if the transaction is supposed to be reversed or not.
     *
     * @return Whether its supposed to be reversed.
     */
    public boolean isReversedTransfer() {
        return reversedTransfer;
    }

    public EconomyCallEvent getEconomyCallEvent() {
        return economyCallEvent;
    }

    /**
     * Sets whether the investor was charged or not.
     *
     * @param chargedInvestor The new value.
     */
    public void setInvestorCharged(boolean chargedInvestor) {
        this.chargedInvestor = chargedInvestor;
    }

    /**
     * Checks if the investor was charged.
     *
     * @return Whether the investor was charged.
     */
    public boolean wasInvestorCharged() {
        return chargedInvestor;
    }

    /**
     * Gets whether this currency transfer originally was going to charge the investor.
     *
     * @return whether this currency transfer originally was going to charge the investor.
     */
    public boolean shouldChargeInvestor() {
        return shouldChargeInvestor;
    }
}