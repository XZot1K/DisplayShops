/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.enums.ChatInteractionType;

public class ChatInteractionEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private Player player;
    private ChatInteractionType chatInteractionType;
    private String playerEntryValue;
    private boolean cancelled;

    public ChatInteractionEvent(@NotNull Player player, @NotNull ChatInteractionType chatInteractionType, @NotNull String playerEntryValue) {
        setPlayer(player);
        setChatInteractionType(chatInteractionType);
        setPlayerEntryValue(playerEntryValue);
        setCancelled(false);
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    // getters & setters
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

    private void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public String getPlayerEntryValue() {
        return playerEntryValue;
    }

    public void setPlayerEntryValue(String playerEntryValue) {
        this.playerEntryValue = playerEntryValue;
    }

    public ChatInteractionType getChatInteractionType() {
        return chatInteractionType;
    }

    private void setChatInteractionType(ChatInteractionType chatInteractionType) {
        this.chatInteractionType = chatInteractionType;
    }
}