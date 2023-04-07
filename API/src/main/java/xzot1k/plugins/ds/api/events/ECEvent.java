/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.objects.Shop;

public interface ECEvent {
    /**
     * Takes and gives currency from the players accordingly.
     *
     * @param chargeInvestor determines whether the investor will be charged for the transaction or not (If the type is NOT sell).
     */
    void performCurrencyTransfer(boolean chargeInvestor);

    /**
     * Reverses all transactions and returns all modified currency balanced to their original state before it was taken/given.
     */
    void reverseCurrencyTransfer();

    /**
     * Gets the price with the tax added to it.
     *
     * @return The result price value.
     */
    double getTaxedPrice();

    // getters & setters
    boolean isCancelled();

    void setCancelled(boolean cancelled);

    double getPrice();

    void setPrice(double price);

    EconomyCallType getEconomyCallType();

    HandlerList getHandlers();

    Shop getShop();

    double getTax();

    void setTax(double tax);

    /**
     * The person investing into a shop.
     *
     * @return The player buying/selling to/from a shop.
     */
    Player getInvestor();

    /**
     * This is the shop owner/seller. This value can return null for admin shops.
     *
     * @return The shop owner/seller.
     */
    OfflinePlayer getProducer();

    /**
     * Tells if the transaction will succeed based on if the investor and producer can both complete it.
     *
     * @return If the transaction succeeded.
     */
    boolean willSucceed();

    void setWillSucceed(boolean willSucceed);

    boolean canInvestorAfford();

    void setCanInvestorAfford(boolean canInvestorAfford);

    boolean canProducerAfford();

    void setCanProducerAfford(boolean canProducerAfford);

    boolean performedCurrencyTransfer();

    void setPerformedCurrencyTransfer(boolean performedCurrencyTransfer);

    boolean chargedInvestor();

    void setChargedInvestor(boolean chargedInvestor);

}