/*
 * Copyright (c) 2022 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.api.enums.ChatInteractionType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public interface DataPack {

    /**
     * Updates the transaction limit counter for the shop.
     *
     * @param shop   The shop to update.
     * @param isBuy  Whether the transaction was buy or sell.
     * @param amount The amount to set.
     */
    void updateCurrentTransactionLimitCounter(@NotNull Shop shop, boolean isBuy, long amount);

    /**
     * Gets the current transaction limit counter under the shop.
     *
     * @param shop  The shop to check for.
     * @param isBuy Whether the transaction was buy or sell.
     * @return The current counter for the shop.
     */
    long getCurrentTransactionCounter(@NotNull Shop shop, boolean isBuy);

    /**
     * Checks if the transaction limit was reached.
     *
     * @param shop  The shop to check against.
     * @param isBuy Whether the transaction was buy or sell.
     * @return Whether the limit was met.
     */
    boolean hasMetTransactionLimit(@NotNull Shop shop, boolean isBuy);

    /**
     * Updates player's personal chat task values by cancelling the previous and cancelling the new after a configurable duration.
     *
     * @param player The player to update the chat task for.
     */
    void updateChatTimeoutTask(@NotNull Player player);

    /**
     * Updates the cooldown id for the player.
     *
     * @param cooldownId The cooldown id to update.
     */
    void updateCooldown(@NotNull String cooldownId);

    /**
     * Checks if the passed player has a cooldown at the specified id based on the base cooldown value.
     *
     * @param player     The player to check.
     * @param cooldownId The cooldown id.
     * @param cooldown   The cooldown's amount (normally the configuration value).
     * @return Whether the cooldown is active (above zero).
     */
    boolean isOnCooldown(@NotNull Player player, @NotNull String cooldownId, int cooldown);

    /**
     * Gets the cooldown left for the player at a specified id.
     *
     * @param player     The player to get the cooldown from.
     * @param cooldownId The cooldown id.
     * @param cooldown   The cooldown value (Usually from configuration).
     * @return The cooldown time remaining.
     */
    int getCooldown(@NotNull Player player, @NotNull String cooldownId, int cooldown);

    /**
     * Checks to see if the player has purchased and unlocked the base block material.
     *
     * @param unlockId The material followed by a colon and its durability which is normally set to -1 or 0.
     * @return Whether the appearance is unlocked.
     */
    boolean hasUnlockedBBM(@NotNull String unlockId);

    /**
     * Unlocks the base-block appearance.
     *
     * @param unlockId The material followed by a colon and the durability.
     */
    void unlockBaseBlock(@NotNull String unlockId);

    /**
     * Locks the base-block appearance.
     *
     * @param unlockId The material followed by a colon and the durability.
     */
    void lockBaseBlock(@NotNull String unlockId);

    String getBBMString();

    void loadBBM(String loadString);

    /**
     * Updates all base-block data to either locked aside the default or unlock all.
     *
     * @param unlockAll Whether to unlock all materials.
     */
    void updateAllBaseBlockAccess(boolean unlockAll);

    /**
     * Obtains a full string of cooldowns applied to the player.
     *
     * @return The string full of cooldowns.
     */
    String cooldownsToString();

    /**
     * Obtains a full string of transaction limits applied to the player.
     *
     * @return The string full of transaction limits.
     */
    String transactionLimitsToString();

    /**
     * Resets all type of editing a player is doing to a selected shop.
     */
    void resetEditData();

    // getters & setters
    LinkedHashMap<String, Boolean> getBaseBlockUnlocks();

    void setBaseBlockUnlocks(@Nullable LinkedHashMap<String, Boolean> baseBlockUnlocks);

    /**
     * Gets the shop object the player is currently tethered to from editing or any similar actions.
     *
     * @return The shop object. (Returns NULL if there is no shop selected for editing or similar actions)
     */
    Shop getSelectedShop();

    /**
     * Sets the shop object the player is currently editing or any similar actions.
     *
     * @param selectedShop The shop object to set as.
     */
    void setSelectedShop(@Nullable Shop selectedShop);

    /**
     * Gets the current chat interaction type in-progress. This is what is used to define what
     * the player's entry is being used for.
     *
     * @return The chat interaction type. (Returns NULL if no chat interaction is active)
     */
    ChatInteractionType getChatInteractionType();

    /**
     * Sets the chat interaction identification type.
     *
     * @param chatInteractionType The type to set as.
     */
    void setChatInteractionType(@Nullable ChatInteractionType chatInteractionType);

    /**
     * Obtains the selected region of a player, if there is one.
     *
     * @return The region object. (Returns NULL if no region is selected)
     */
    Region getSelectedRegion();

    /**
     * Sets the selected region stored for the player.
     *
     * @param selectedRegion The new region object.
     */
    void setSelectedRegion(@Nullable Region selectedRegion);

    /**
     * Checks if the player is currently in region selection mode.
     *
     * @return Whether the player is in selection mode.
     */
    boolean isInSelectionMode();

    /**
     * Sets if the player is in selection mode or not.
     *
     * @param inSelectionMode Whether the player should be in selection mode or not.
     */
    void setInSelectionMode(boolean inSelectionMode);

    BukkitTask getCurrentChatTask();

    void setCurrentChatTask(@Nullable BukkitTask currentChatTask);

    /**
     * Gets the player cooldown map for the player.
     *
     * @return The map itself.
     */
    HashMap<String, Long> getCooldownMap();

    /**
     * Gets the base-block page map for the player if it exists.
     *
     * @return The map itself.
     */
    HashMap<Integer, List<ItemStack>> getBaseBlockPageMap();

    /**
     * Sets the base-block page map for the player.
     *
     * @param baseBlockPageMap The map to set it as.
     */
    void setBaseBlockPageMap(@Nullable HashMap<Integer, List<ItemStack>> baseBlockPageMap);

    /**
     * Gets the current page the player is on from the base block page map.
     *
     * @return The current page.
     */
    int getCurrentBaseBlockPage();

    /**
     * Sets the current page the player is on from the base block page map.
     */
    void setCurrentBaseBlockPage(int currentBaseBlockPage);

    /**
     * Gets the visit page map for the player if it exists.
     *
     * @return The map itself.
     */
    HashMap<Integer, List<ItemStack>> getVisitPageMap();

    /**
     * Sets the visit page map for the player.
     *
     * @param visitPageMap The map to set it as.
     */
    void setVisitPageMap(@Nullable HashMap<Integer, List<ItemStack>> visitPageMap);

    /**
     * Gets the current page the player is on from the visit page map.
     *
     * @return The current page.
     */
    int getCurrentVisitPage();

    /**
     * Sets the current page the player is on from the visit page map.
     */
    void setCurrentVisitPage(int currentBaseBlockPage);

    /**
     * Determines if player will be notified .
     */
    boolean isTransactionNotify();

    void setTransactionNotify(boolean transactionNotify);

}