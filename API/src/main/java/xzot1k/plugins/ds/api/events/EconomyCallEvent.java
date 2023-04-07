/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.objects.Shop;

public class EconomyCallEvent extends Event implements Cancellable, ECEvent {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public void performCurrencyTransfer(boolean chargeInvestor) {

    }

    @Override
    public void reverseCurrencyTransfer() {

    }

    @Override
    public double getTaxedPrice() {
        return 0;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public double getPrice() {
        return 0;
    }

    @Override
    public void setPrice(double price) {

    }

    @Override
    public EconomyCallType getEconomyCallType() {
        return null;
    }

    @Override
    public HandlerList getHandlers() {
        return null;
    }

    @Override
    public Shop getShop() {
        return null;
    }

    @Override
    public double getTax() {
        return 0;
    }

    @Override
    public void setTax(double tax) {

    }

    @Override
    public Player getInvestor() {
        return null;
    }

    @Override
    public OfflinePlayer getProducer() {
        return null;
    }

    @Override
    public boolean willSucceed() {
        return false;
    }

    @Override
    public void setWillSucceed(boolean willSucceed) {

    }

    @Override
    public boolean canInvestorAfford() {
        return false;
    }

    @Override
    public void setCanInvestorAfford(boolean canInvestorAfford) {

    }

    @Override
    public boolean canProducerAfford() {
        return false;
    }

    @Override
    public void setCanProducerAfford(boolean canProducerAfford) {

    }

    @Override
    public boolean performedCurrencyTransfer() {
        return false;
    }

    @Override
    public void setPerformedCurrencyTransfer(boolean performedCurrencyTransfer) {

    }

    @Override
    public boolean chargedInvestor() {
        return false;
    }

    @Override
    public void setChargedInvestor(boolean chargedInvestor) {

    }
}