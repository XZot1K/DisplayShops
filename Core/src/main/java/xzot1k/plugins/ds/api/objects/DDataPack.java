/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.ChatInteractionType;
import xzot1k.plugins.ds.api.enums.StageType;
import xzot1k.plugins.ds.api.events.ChatInteractionStageEvent;

import java.util.*;

public class DDataPack implements DataPack {
    private DisplayShops pluginInstance;
    private HashMap<Integer, List<ItemStack>> baseBlockPageMap;
    private HashMap<Integer, List<Pair<Shop, ItemStack>>> visitPageMap;
    private LinkedHashMap<String, Boolean> baseBlockUnlocks;
    private int currentBaseBlockPage;
    private final HashMap<String, Long> cooldownMap;
    private final HashMap<UUID, Long[]> transactionLimitMap;
    private boolean inSelectionMode, transactionNotify;
    private ChatInteractionType chatInteractionType;
    private Shop selectedShop;
    private Region selectedRegion;
    private BukkitTask currentChatTask;

    public DDataPack(DisplayShops pluginInstance, @Nullable String baseBlockUnlocks) {
        this.setPluginInstance(pluginInstance);
        this.transactionLimitMap = new HashMap<>();
        this.cooldownMap = new HashMap<>();
        setTransactionNotify(true);
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
    public void updateCurrentTransactionLimitCounter(Shop shop, boolean isBuy, long amount) {
        final Long[] counters = getTransactionLimitMap().getOrDefault(shop.getShopId(), new Long[]{0L, 0L});
        if (isBuy) {counters[0] = amount;} else {counters[1] = amount;}
        getTransactionLimitMap().put(shop.getShopId(), counters);
    }

    /**
     * Gets the current transaction limit counter under the shop.
     *
     * @param shop  The shop to check for.
     * @param isBuy Whether the transaction was buy or sell.
     * @return The current counter for the shop.
     */
    public long getCurrentTransactionCounter(Shop shop, boolean isBuy) {
        final Long[] counters = getTransactionLimitMap().getOrDefault(shop.getShopId(), new Long[]{0L, 0L});
        return (isBuy ? ((shop.getBuyLimit() > 0 && counters != null) ? counters[0] : 0)
                : ((shop.getSellLimit() > 0 && counters != null) ? counters[1] : 0));
    }

    /**
     * Checks if the transaction limit was reached.
     *
     * @param shop  The shop to check against.
     * @param isBuy Whether the transaction was buy or sell.
     * @return Whether the limit was met.
     */
    public boolean hasMetTransactionLimit(Shop shop, boolean isBuy) {
        final long counter = getCurrentTransactionCounter(shop, isBuy);
        return (isBuy ? (shop.getBuyLimit() > 0 && (shop.getBuyLimit() - counter) <= 0)
                : (shop.getSellLimit() > 0 && (shop.getSellLimit() - counter) <= 0));
    }

    @Override
    public void updateChatTimeoutTask(Player player) {
        int ciTimeout;
        if (this.getCurrentChatTask() != null) {
            this.getCurrentChatTask().cancel();
        }
        if ((ciTimeout = this.getPluginInstance().getConfig().getInt("chat-interaction-timeout")) >= 0) {
            this.setCurrentChatTask(this.getPluginInstance().getServer().getScheduler().runTaskLater(this.getPluginInstance(), () -> {
                ChatInteractionStageEvent chatInteractionStageEvent = new ChatInteractionStageEvent(player, StageType.TIMEOUT, "");
                this.getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionStageEvent);
                if (this.getChatInteractionType() != null) {
                    this.resetEditData();
                    String timeoutMessage = this.getPluginInstance().getLangConfig().getString("chat-interaction-timeout");
                    if (timeoutMessage != null && !timeoutMessage.equalsIgnoreCase("")) {
                        this.getPluginInstance().getManager().sendMessage(player, timeoutMessage);
                    }
                }
            }, 20L * (long) ciTimeout));
        }
    }

    @Override
    public void updateCooldown(String cooldownId) {
        this.getCooldownMap().put(cooldownId, System.currentTimeMillis());
    }

    @Override
    public boolean isOnCooldown(Player player, String cooldownId, int cooldown) {
        return this.getCooldown(player, cooldownId, cooldown) > 0;
    }

    @Override
    public int getCooldown(Player player, String cooldownId, int cooldown) {
        if (!player.hasPermission("displayshops.cdbypass")) {
            int cd = Math.max(cooldown, 0);
            return (int) ((getCooldownMap().containsKey(cooldownId) ? getCooldownMap().get(cooldownId)
                    : (long) cd) / 1000L + (long) cooldown - System.currentTimeMillis() / 1000L);
        }
        return 0;
    }

    @Override
    public boolean hasUnlockedBBM(String unlockId) {
        return getBaseBlockUnlocks().getOrDefault(fixUnlockId(unlockId).toUpperCase(), false);
    }

    @Override
    public void unlockBaseBlock(String unlockId) {
        getBaseBlockUnlocks().put(fixUnlockId(unlockId).toUpperCase(), true);
    }

    @Override
    public void lockBaseBlock(String unlockId) {
        getBaseBlockUnlocks().put(fixUnlockId(unlockId).toUpperCase(), false);
    }

    @Override
    public void updateAllBaseBlockAccess(boolean unlockAll) {
        String defaultMaterial = getPluginInstance().getConfig().getString("shop-block-material");
        if (defaultMaterial == null) defaultMaterial = "STONE";

        for (String line : getPluginInstance().getMenusConfig().getStringList("base-block-menu.available-materials")) {
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
        for (String line : getPluginInstance().getMenusConfig().getStringList("base-block-menu.available-materials"))
            if (line.toUpperCase().startsWith(split[0].toUpperCase()) && line.split(":")[1].equalsIgnoreCase("-1")) {
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
        if (getCurrentChatTask() != null) getCurrentChatTask().cancel();

        setChatInteractionType(null);
        if (getSelectedShop() != null) getSelectedShop().setCurrentEditor(null);

        setSelectedShop(null);

        setVisitPageMap(null);
        setBaseBlockPageMap(null);
        setCurrentVisitPage(1);
        setCurrentBaseBlockPage(1);
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
    public ChatInteractionType getChatInteractionType() {
        return this.chatInteractionType;
    }

    @Override
    public void setChatInteractionType(ChatInteractionType chatInteractionType) {
        this.chatInteractionType = chatInteractionType;
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
    public BukkitTask getCurrentChatTask() {
        return this.currentChatTask;
    }

    @Override
    public void setCurrentChatTask(BukkitTask currentChatTask) {
        this.currentChatTask = currentChatTask;
    }

    @Override
    public HashMap<String, Long> getCooldownMap() {
        return this.cooldownMap;
    }

    private HashMap<UUID, Long[]> getTransactionLimitMap() {
        return transactionLimitMap;
    }

    @Override
    public HashMap<Integer, List<ItemStack>> getBaseBlockPageMap() {
        return this.baseBlockPageMap;
    }

    @Override
    public void setBaseBlockPageMap(HashMap<Integer, List<ItemStack>> baseBlockPageMap) {
        this.baseBlockPageMap = baseBlockPageMap;
    }

    @Override
    public int getCurrentBaseBlockPage() {
        return this.currentBaseBlockPage;
    }

    @Override
    public void setCurrentBaseBlockPage(int currentBaseBlockPage) {
        this.currentBaseBlockPage = currentBaseBlockPage;
    }

    @Override
    public HashMap<Integer, List<Pair<Shop, ItemStack>>> getVisitPageMap() {
        return this.visitPageMap;
    }

    @Override
    public void setVisitPageMap(HashMap<Integer, List<Pair<Shop, ItemStack>>> visitPageMap) {
        this.visitPageMap = visitPageMap;
    }

    @Override
    public int getCurrentVisitPage() {
        return this.currentBaseBlockPage;
    }

    @Override
    public void setCurrentVisitPage(int currentBaseBlockPage) {
        this.currentBaseBlockPage = currentBaseBlockPage;
    }

    public boolean isTransactionNotify() {
        return transactionNotify;
    }

    public void setTransactionNotify(boolean transactionNotify) {
        this.transactionNotify = transactionNotify;
    }

}

