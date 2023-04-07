/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.enums.EditType;
import xzot1k.plugins.ds.api.objects.Shop;

public class ShopEditEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Shop shop;
    private final EditType editType;
    private boolean cancelled;

    public ShopEditEvent(@NotNull Player player, @NotNull Shop shop, @NotNull EditType editType) {
        this.player = player;
        this.shop = shop;
        this.editType = editType;
        setCancelled(false);
    }

    // getters & setters
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
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

    public Player getPlayer() {
        return player;
    }

    public Shop getShop() {
        return shop;
    }

    public EditType getEditType() {
        return editType;
    }
}