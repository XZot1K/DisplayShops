/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.objects.Shop;

public class EconomyCallEvent extends Event implements Cancellable, ECEvent {

    private static final HandlerList handlers = new HandlerList();
    private final DisplayShops INSTANCE;
    private Player investor;
    private OfflinePlayer producer;
    private double price, tax;
    private boolean cancelled, canInvestorAfford, canProducerAfford, willSucceed, performedCurrencyTransfer, chargedInvestor;
    private Shop shop;
    private EconomyCallType economyCallType;

    public EconomyCallEvent(Player investor, @Nullable OfflinePlayer producer, EconomyCallType economyCallType, Shop shop, double price) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        setShop(shop);
        setInvestor(investor);
        setProducer(producer);
        setPrice(price);
        setEconomyCallType(economyCallType);
        setCanInvestorAfford(true);
        setCanProducerAfford(true);
        setPerformedCurrencyTransfer(false);
        setChargedInvestor(false);
        setTax(INSTANCE.getConfig().getDouble("transaction-tax"));

        AffordCheckEvent affordCheckEvent = new AffordCheckEvent(INSTANCE, investor, producer, true, true, getPrice(), getTaxedPrice(), this, shop);
        INSTANCE.getServer().getPluginManager().callEvent(affordCheckEvent);
        if (!affordCheckEvent.isCancelled()) {
            final boolean useOwnerSyncing = INSTANCE.getConfig().getBoolean("sync-owner-balance");

            if (getEconomyCallType() == EconomyCallType.SELL) {
                final boolean canSync = (useOwnerSyncing && getProducer() != null);
                setCanProducerAfford(getShop().isAdminShop() || (canSync ? (getProducer() != null
                        ? INSTANCE.getEconomyHandler().has(getProducer(), getShop(), getPrice()) : getShop().getStoredBalance() >= getPrice())
                        : (getShop().getStoredBalance() >= getPrice())));
                setWillSucceed(canInvestorAfford() && canProducerAfford());
                return;
            }

            setCanInvestorAfford(getInvestor().hasPermission("displayshops.bypass")
                    || INSTANCE.getEconomyHandler().has(getInvestor(), getShop(), getTaxedPrice()));
        }

        setWillSucceed(canInvestorAfford() && canProducerAfford());
    }

    public static HandlerList getHandlerList() {return handlers;}

    /**
     * Takes and gives currency from the players accordingly.
     *
     * @parm chargeInvestor determines whether the investor will be charged for the transaction or not (If the type is NOT sell).
     */
    public void performCurrencyTransfer(boolean chargeInvestor) {
        if (performedCurrencyTransfer()) return;
        setPerformedCurrencyTransfer(true);

        final CurrencyTransferEvent currencyTransferEvent = new CurrencyTransferEvent(INSTANCE, getShop(), getEconomyCallType(), getInvestor(),
                getProducer(), getPrice(), getTaxedPrice(), false, chargeInvestor, chargedInvestor(), this);
        INSTANCE.getServer().getPluginManager().callEvent(currencyTransferEvent);

        if (!currencyTransferEvent.isCancelled()) {
            final boolean useOwnerSyncing = INSTANCE.getConfig().getBoolean("sync-owner-balance");

            if (getEconomyCallType() == EconomyCallType.SELL) {
                if (getInvestor() != null) INSTANCE.getEconomyHandler().deposit(getInvestor(), getShop(), getPrice());

                if (!getShop().isAdminShop()) {
                    if (useOwnerSyncing && getProducer() != null) {
                        INSTANCE.getEconomyHandler().withdraw(getProducer(), getShop(), getPrice());
                        return;
                    }

                    final double storedPriceCalculation = (getShop().getStoredBalance() - getPrice());
                    getShop().setStoredBalance(Math.max(storedPriceCalculation, 0));
                }

                return;
            }

            if (getInvestor() != null && chargeInvestor) {
                INSTANCE.getEconomyHandler().withdraw(getInvestor(), getShop(), getTaxedPrice());
                setChargedInvestor(true);
            }

            if ((getEconomyCallType() != EconomyCallType.EDIT_ACTION && getEconomyCallType() != EconomyCallType.RENT
                    && getEconomyCallType() != EconomyCallType.RENT_RENEW) && !getShop().isAdminShop()) {
                if (useOwnerSyncing && getProducer() != null) {
                    INSTANCE.getEconomyHandler().deposit(getProducer(), getShop(), getPrice());
                    return;
                }

                getShop().setStoredBalance(getShop().getStoredBalance() + getPrice());
            }
        }
    }

    /**
     * Reverses all transactions and returns all modified currency balanced to their original state before it was taken/given.
     */
    public void reverseCurrencyTransfer() {
        if (!performedCurrencyTransfer()) return;

        CurrencyTransferEvent currencyTransferEvent = new CurrencyTransferEvent(INSTANCE, getShop(), getEconomyCallType(), getInvestor(),
                getProducer(), getPrice(), getTaxedPrice(), true, false, false, this);
        INSTANCE.getServer().getPluginManager().callEvent(currencyTransferEvent);

        if (!currencyTransferEvent.isCancelled()) {
            final boolean useOwnerSyncing = INSTANCE.getConfig().getBoolean("sync-owner-balance");
            if (getEconomyCallType() == EconomyCallType.SELL) {
                if (getInvestor() != null)
                    INSTANCE.getEconomyHandler().withdraw(getInvestor(), getShop(), getPrice());

                if (!getShop().isAdminShop()) {
                    if (useOwnerSyncing && getProducer() != null) {
                        INSTANCE.getEconomyHandler().deposit(getProducer(), getShop(), getPrice());
                        return;
                    }

                    getShop().setStoredBalance(Math.min(INSTANCE.getConfig().getDouble("max-stored-currency"),
                            (getShop().getStoredBalance() + getPrice())));
                }
                return;
            }

            if (getInvestor() != null && chargedInvestor())
                INSTANCE.getEconomyHandler().deposit(getInvestor(), getShop(), getTaxedPrice());

            if (!getShop().isAdminShop()) {
                if (useOwnerSyncing && getProducer() != null) {
                    INSTANCE.getEconomyHandler().withdraw(getProducer(), getShop(), getPrice());
                    return;
                }

                getShop().setStoredBalance(Math.max(0, (getShop().getStoredBalance() - getPrice())));
            }
        }
    }

    /**
     * Gets the price with the tax added to it.
     *
     * @return The result price value.
     */
    public double getTaxedPrice() {return (getPrice() + (getPrice() * getTax()));}

    // getters & setters
    @Override
    public boolean isCancelled() {return cancelled;}

    @Override
    public void setCancelled(boolean cancelled) {this.cancelled = cancelled;}

    public double getPrice() {return price;}

    public void setPrice(double price) {this.price = price;}

    public EconomyCallType getEconomyCallType() {return economyCallType;}

    private void setEconomyCallType(EconomyCallType economyCallType) {this.economyCallType = economyCallType;}

    @Override
    public @NotNull HandlerList getHandlers() {return handlers;}

    public Shop getShop() {return shop;}

    private void setShop(Shop shop) {this.shop = shop;}

    public double getTax() {return tax;}

    public void setTax(double tax) {this.tax = tax;}

    /**
     * The person investing into a shop.
     *
     * @return The player buying/selling to/from a shop.
     */
    public Player getInvestor() {return investor;}

    private void setInvestor(Player investor) {this.investor = investor;}

    /**
     * This is the shop owner/seller. This value can return null for admin shops.
     *
     * @return The shop owner/seller.
     */
    public OfflinePlayer getProducer() {return producer;}

    private void setProducer(OfflinePlayer producer) {this.producer = producer;}

    /**
     * Tells if the transaction will succeed based on if the investor and producer can both complete it.
     *
     * @return If the transaction succeeded.
     */
    public boolean willSucceed() {return willSucceed;}

    public void setWillSucceed(boolean willSucceed) {this.willSucceed = willSucceed;}

    public boolean canInvestorAfford() {return canInvestorAfford;}

    public void setCanInvestorAfford(boolean canInvestorAfford) {this.canInvestorAfford = canInvestorAfford;}

    public boolean canProducerAfford() {return canProducerAfford;}

    public void setCanProducerAfford(boolean canProducerAfford) {this.canProducerAfford = canProducerAfford;}

    public boolean performedCurrencyTransfer() {return performedCurrencyTransfer;}

    public void setPerformedCurrencyTransfer(boolean performedCurrencyTransfer) {this.performedCurrencyTransfer = performedCurrencyTransfer;}

    public boolean chargedInvestor() {return chargedInvestor;}

    public void setChargedInvestor(boolean chargedInvestor) {this.chargedInvestor = chargedInvestor;}

}