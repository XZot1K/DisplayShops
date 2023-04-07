/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.enums;

public enum ChatInteractionType {
    SHOP_ITEM_AMOUNT(true), BUY_LIMIT(true), SELL_LIMIT(true), BUY_PRICE(true),
    SELL_PRICE(true), WITHDRAW_STOCK(true), DEPOSIT_STOCK(true), EDIT_DESCRIPTION(false),
    DEPOSIT_BALANCE(true), WITHDRAW_BALANCE(true), ASSISTANTS_ADD(false), ASSISTANTS_REMOVE(false),
    VISIT_FILTER_ENTRY(false);

    final boolean numericalEntry;

    ChatInteractionType(boolean numericalEntry) {
        this.numericalEntry = numericalEntry;
    }

    /**
     * Checks if the chat interaction type requires a numerical entry or not.
     *
     * @return whether a numerical entry is required.
     */
    public boolean isNumericalEntry() {return numericalEntry;}
}