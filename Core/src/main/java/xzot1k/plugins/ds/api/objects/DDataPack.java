/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.InteractionType;

import java.util.*;

public class DDataPack implements DataPack {
    private DisplayShops pluginInstance;
    private HashMap<Integer, List<ItemStack>> pageMap;
    private LinkedHashMap<String, Boolean> baseBlockUnlocks;
    private int currentPage;
    private final HashMap<String, Long> cooldownMap;
    private final HashMap<UUID, Long[]> transactionLimitMap;
    private boolean inSelectionMode, transactionNotify;
    private InteractionType interactionType;
    private Object interactionValue, longTermInteractionValue;
    private Shop selectedShop;
    private Region selectedRegion;

    public DDataPack(DisplayShops pluginInstance, @Nullable String baseBlockUnlocks) {
        this.setPluginInstance(pluginInstance);
        this.transactionLimitMap = new HashMap<>();
        this.cooldownMap = new HashMap<>();
        setTransactionNotify(true);
        setPageMap(new LinkedHashMap<>());
        setBaseBlockUnlocks(new LinkedHashMap<>());
        loadBBM(baseBlockUnlocks);
    }

    /**
     * Updates the transaction limit counter for the shop.
     *
     * @param shop   The shop to update.
     * @param isBuy  Whether the transaction was buy or sell.
     * @param amount The amount to set.
     */
    public void updateCurrentTransactionLimitCounter(@NotNull Shop shop, boolean isBuy, long amount) {
        final Long[] counters = getTransactionLimitMap().getOrDefault(shop.getShopId(), new Long[]{0L, 0L});
        if (isBuy) {
            counters[0] = amount;
        } else {
            counters[1] = amount;
        }
        getTransactionLimitMap().put(shop.getShopId(), counters);
    }

    /**
     * Gets the current transaction limit counter under the shop.
     *
     * @param shop     The shop to check for.
     * @param isBuy    Whether the transaction was buy or sell.
     * @param isGlobal Whether the limited needs to be global or player specific.
     * @return The current counter for the shop.
     */
    public long getCurrentTransactionCounter(Shop shop, boolean isBuy, boolean isGlobal) {
        final Long[] counters = getTransactionLimitMap().getOrDefault(shop.getShopId(), new Long[]{0L, 0L});
        return (isBuy ? (((isGlobal ? shop.getGlobalBuyLimit() : shop.getPlayerBuyLimit()) > 0 && counters != null) ? counters[0] : 0)
                : (((isGlobal ? shop.getGlobalSellLimit() : shop.getPlayerSellLimit()) > 0 && counters != null) ? counters[1] : 0));
    }

    /**
     * Checks if the transaction limit was reached.
     *
     * @param shop     The shop to check against.
     * @param isBuy    Whether the transaction was buy or sell.
     * @param isGlobal Whether the limited needs to be global or player specific.
     * @return Whether the limit was met.
     */
    public boolean hasMetTransactionLimit(Shop shop, boolean isBuy, boolean isGlobal) {
        final long counter = getCurrentTransactionCounter(shop, isBuy, isGlobal);

        final int buyLimit = (isGlobal ? shop.getGlobalBuyLimit() : shop.getPlayerBuyLimit()),
                sellLimit = (isGlobal ? shop.getGlobalSellLimit() : shop.getPlayerSellLimit());

        return (isBuy ? (buyLimit > 0 && (buyLimit - counter) <= 0)
                : (sellLimit > 0 && (sellLimit - counter) <= 0));
    }

    @Override
    public void updateCooldown(@NotNull String cooldownId) {
        this.getCooldownMap().put(cooldownId, System.currentTimeMillis());
    }

    @Override
    public boolean isOnCooldown(@NotNull Player player, @NotNull String cooldownId, int cooldown) {
        return this.getCooldown(player, cooldownId, cooldown) > 0;
    }

    @Override
    public int getCooldown(@NotNull Player player, @NotNull String cooldownId, int cooldown) {
        if (!player.hasPermission("displayshops.cdbypass")) {
            int cd = Math.max(cooldown, 0);
            return (int) ((getCooldownMap().containsKey(cooldownId) ? getCooldownMap().get(cooldownId)
                    : (long) cd) / 1000L + (long) cooldown - System.currentTimeMillis() / 1000L);
        }
        return 0;
    }

    @Override
    public boolean hasUnlockedBBM(@NotNull String unlockId) {
        return getBaseBlockUnlocks().getOrDefault(fixUnlockId(unlockId).toUpperCase(), false);
    }

    @Override
    public void unlockBaseBlock(@NotNull String unlockId) {
        getBaseBlockUnlocks().put(fixUnlockId(unlockId).toUpperCase(), true);
    }

    @Override
    public void lockBaseBlock(@NotNull String unlockId) {
        getBaseBlockUnlocks().put(fixUnlockId(unlockId).toUpperCase(), false);
    }

    @Override
    public void updateAllBaseBlockAccess(boolean unlockAll) {
        String defaultMaterial = getPluginInstance().getConfig().getString("shop-block-material");
        if (defaultMaterial == null) defaultMaterial = "STONE";

        Menu menu = getPluginInstance().getMenu("appearance");

        for (String line : menu.getConfiguration().getStringList("appearances")) {
            if (line == null || !line.contains(":")) continue;
            final String[] lineArgs = line.split(":");
            final String unlockId = (lineArgs[0] + ":" + lineArgs[1]).toUpperCase();
            getBaseBlockUnlocks().putIfAbsent(unlockId, (defaultMaterial.toUpperCase().startsWith(unlockId) ||
                    (defaultMaterial.toUpperCase().startsWith(lineArgs[0])
                            && lineArgs[1].equalsIgnoreCase("-1")) || unlockAll));
        }
    }

    private String fixUnlockId(@NotNull String unlockId) {
        String[] split = unlockId.split(":");

        Menu menu = getPluginInstance().getMenu("appearance");

        for (String line : menu.getConfiguration().getStringList("appearances"))
            if (line.toUpperCase().startsWith(split[0].toUpperCase())
                    && line.split(":")[1].equalsIgnoreCase("-1")) {
                unlockId = unlockId.replace(split[1], "-1");
                break;
            }
        return unlockId;
    }

    @Override
    public String getBBMString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : getBaseBlockUnlocks().entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append("/").append(entry.getValue() ? 1 : 0);
        }
        return sb.toString();
    }

    @Override
    public void loadBBM(@Nullable String loadString) {
        if (loadString == null || !loadString.contains(",")) {
            updateAllBaseBlockAccess(false);
            return;
        }
        for (String line : loadString.split(",")) {
            String[] data = line.split("/");
            if (data.length < 2) continue;
            getBaseBlockUnlocks().put(data[0].toUpperCase(), data[1].equalsIgnoreCase("1"));
        }
        updateAllBaseBlockAccess(false);
    }

    @Override
    public String cooldownsToString() {
        StringBuilder cdLine = new StringBuilder();
        int counter = this.getCooldownMap().size();
        for (Map.Entry<String, Long> cdEntry : this.getCooldownMap().entrySet()) {
            cdLine.append(cdEntry.getKey()).append(":").append(cdEntry.getValue());
            if (--counter <= 0) continue;
            cdLine.append(",");
        }
        return cdLine.toString();
    }

    @Override
    public String transactionLimitsToString() {
        StringBuilder cdLine = new StringBuilder();
        int counter = this.getTransactionLimitMap().size();
        for (Map.Entry<UUID, Long[]> cdEntry : this.getTransactionLimitMap().entrySet()) {
            cdLine.append(cdEntry.getKey()).append(":").append(cdEntry.getValue()[0])
                    .append(";").append(cdEntry.getValue()[1]);
            if (--counter <= 0) continue;
            cdLine.append(",");
        }
        return cdLine.toString();
    }

    @Override
    public void resetEditData() {
        if (getSelectedShop() != null) getSelectedShop().setCurrentEditor(null);
        setSelectedShop(null);
        setInteractionType(null);
        //setLongTermInteractionValue(null);
    }

    @Override
    public LinkedHashMap<String, Boolean> getBaseBlockUnlocks() {
        return this.baseBlockUnlocks;
    }

    @Override
    public void setBaseBlockUnlocks(LinkedHashMap<String, Boolean> baseBlockUnlocks) {
        this.baseBlockUnlocks = baseBlockUnlocks;
    }

    private DisplayShops getPluginInstance() {
        return this.pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    @Override
    public Shop getSelectedShop() {
        return this.selectedShop;
    }

    @Override
    public void setSelectedShop(Shop selectedShop) {
        this.selectedShop = selectedShop;
    }

    @Override
    public Region getSelectedRegion() {
        return this.selectedRegion;
    }

    @Override
    public void setSelectedRegion(Region selectedRegion) {
        this.selectedRegion = selectedRegion;
    }

    @Override
    public boolean isInSelectionMode() {
        return this.inSelectionMode;
    }

    @Override
    public void setInSelectionMode(boolean inSelectionMode) {
        this.inSelectionMode = inSelectionMode;
    }

    @Override
    public HashMap<String, Long> getCooldownMap() {
        return this.cooldownMap;
    }

    private HashMap<UUID, Long[]> getTransactionLimitMap() {
        return transactionLimitMap;
    }

    /**
     * Gets the current page the player is on from the page map.
     *
     * @return The current page.
     */
    @Override
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Sets the current page the player is on from the page map.
     */
    @Override
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    /**
     * Gets the page map for the player if it exists.
     *
     * @return The map itself.
     */
    @Override
    public HashMap<Integer, List<ItemStack>> getPageMap() {
        return pageMap;
    }

    /**
     * Sets the page map for the player.
     *
     * @param pageMap The map to set it as.
     */
    @Override
    public void setPageMap(@Nullable HashMap<Integer, List<ItemStack>> pageMap) {
        this.pageMap = pageMap;
    }

    /**
     * @return Whether the player has a next page in their current menu interaction.
     */
    @Override
    public boolean hasNextPage() {
        return getPageMap().containsKey(getCurrentPage() + 1);
    }

    /**
     * @return Whether the player has a previous page in their current menu interaction.
     */
    @Override
    public boolean hasPreviousPage() {
        return getPageMap().containsKey(getCurrentPage() - 1);
    }

    @Override
    public boolean isTransactionNotify() {
        return transactionNotify;
    }

    @Override
    public void setTransactionNotify(boolean transactionNotify) {
        this.transactionNotify = transactionNotify;
    }

    /**
     * @return The current interaction type the player is performing.
     */
    @Override
    public InteractionType getInteractionType() {
        return interactionType;
    }

    /**
     * Sets the interaction type the player is currently performing.
     *
     * @param interactionType The new interaction type to update with.
     */
    @Override
    public void setInteractionType(@Nullable InteractionType interactionType) {
        this.interactionType = interactionType;
    }

    /**
     * Gets the value attached to the current interaction performed by the player.
     *
     * @return The current value attached to the interaction.
     */
    @Override
    public Object getInteractionValue() {
        return interactionValue;
    }

    /**
     * Sets the value attached to the current interaction performed by the player.
     *
     * @param interactionValue The new value to set as the attached interaction value.
     */
    @Override
    public void setInteractionValue(@Nullable Object interactionValue) {
        this.interactionValue = interactionValue;
    }

    /**
     * Gets the value attached to the current interaction performed by the player, intended for retrieval way later in the process.
     *
     * @return The current value attached to the interaction, intended for retrieval way later in the process.
     */
    @Override
    public Object getLongTermInteractionValue() {
        return longTermInteractionValue;
    }

    /**
     * Sets the value attached to the current interaction performed by the player, intended for retrieval way later in the process.
     *
     * @param longTermInteractionValue The new value to set as the attached interaction value, intended for retrieval way later in the process.
     */
    @Override
    public void setLongTermInteractionValue(@Nullable Object longTermInteractionValue) {
        this.longTermInteractionValue = longTermInteractionValue;
    }

}

