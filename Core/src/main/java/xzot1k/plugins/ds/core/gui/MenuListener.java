package xzot1k.plugins.ds.core.gui;

import net.md_5.bungee.api.ChatColor;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.Direction;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.enums.InteractionType;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.events.ShopDeletionEvent;
import xzot1k.plugins.ds.api.objects.*;

import java.lang.reflect.Method;
import java.util.*;

public class MenuListener implements Listener {

    private final DisplayShops INSTANCE;

    public MenuListener(@NotNull DisplayShops instance) {
        this.INSTANCE = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        final Player player = (Player) e.getPlayer();

        final String invName = getInventoryName(e.getInventory(), e.getView());
        if (invName == null || invName.isEmpty() || e.getInventory().getType() == InventoryType.ANVIL
                || INSTANCE.matchesMenu("amount-selector", invName)) return;

        // update the long term value to allow the reset button in the amount selector to work right
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        dataPack.setLongTermInteractionValue(dataPack.getInteractionValue());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        final Player player = (Player) e.getPlayer();

        final String invName = getInventoryName(e.getInventory(), e.getView());
        if (invName == null || invName.isEmpty() || e.getInventory().getType() == InventoryType.ANVIL
                || INSTANCE.matchesAnyMenu(invName)) return;

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (dataPack.getInteractionType() != null) return;

        Menu menu = INSTANCE.getMenu(invName);
        if (menu != null && menu.getMenuName().contains("assistants")
                && dataPack.getSelectedShop() != null)
            dataPack.getSelectedShop().save(true);

        dataPack.resetEditData();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;

        final Player player = (Player) e.getWhoClicked();
        final String creationItemName = INSTANCE.getManager().color(INSTANCE.getConfig().getString("shop-creation-item.display-name"));
        String[] blockedInventories = {"ANVIL", "DISPENSER", "DROPPER", "FURNACE", "GRINDSTONE", "STONECUTTER"};
        for (int i = -1; ++i < blockedInventories.length; ) {
            if (player.getOpenInventory().getType().name().startsWith(blockedInventories[i])) {

                boolean isCreationItem = (e.getCurrentItem() != null && (e.getCurrentItem().hasItemMeta() && e.getCurrentItem().getItemMeta() != null)
                        && e.getCurrentItem().getItemMeta().hasDisplayName() && e.getCurrentItem().getItemMeta().getDisplayName().equals(creationItemName))
                        || (e.getCursor() != null && (e.getCursor().hasItemMeta() && e.getCursor().getItemMeta() != null) && e.getCursor().getItemMeta().hasDisplayName()
                        && e.getCursor().getItemMeta().getDisplayName().equals(creationItemName));

                if (isCreationItem) {
                    e.setCancelled(true);
                    e.setResult(Event.Result.DENY);
                    player.updateInventory();
                    return;
                }
            }
        }

        final String inventoryName = getInventoryName(e.getClickedInventory(), e.getView());
        if (inventoryName == null || player.isSleeping()) return;

        final Menu menu = INSTANCE.getMenu(inventoryName);
        if (menu == null) return;

        if (menu.getMenuName().equals("edit")) {
            if (!checkInteractiveTouches(e)) return;
            operateEditMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("transaction")) {
            if (!checkInteractiveTouches(e)) return;
            operateTransactionMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("amount-selector")) {
            if (!checkInteractiveTouches(e)) return;
            operateAmountSelectorMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("confirm")) {
            if (!checkInteractiveTouches(e)) return;
            operateConfirmMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("appearance")) {
            if (!checkInteractiveTouches(e)) return;
            operateAppearanceMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("assistants")) {
            if (!checkInteractiveTouches(e)) return;
            operateAssistantsMenu(e, e.getClickedInventory(), menu, player);
        } else if (menu.getMenuName().equals("visit")) {
            if (!checkInteractiveTouches(e)) return;
            operateVisitMenu(e, e.getClickedInventory(), menu, player);
        }
    }

    private void operateEditMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack)) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop != null) shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null
                && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) || !shop.canEdit(player)) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        final int saleItemSlot = (menu.getConfiguration().getInt("sale-item-slot")),
                tradeItemSlot = menu.getConfiguration().getInt("trade-item-slot");
        final boolean isSaleItemSlot = (e.getSlot() == saleItemSlot && saleItemSlot >= 0 && saleItemSlot < inventory.getSize()),
                isTradeItemSlot = (e.getSlot() == tradeItemSlot && tradeItemSlot >= 0 && tradeItemSlot < inventory.getSize());

        if (isSaleItemSlot || isTradeItemSlot) {

            if (inventory.getType() == InventoryType.PLAYER) return;

            final String changeItemMaterial = menu.getConfiguration().getString("item-change.material"),
                    changeItemName = menu.getConfiguration().getString("item-change.name");

            if (e.getCurrentItem() != null && (changeItemMaterial != null && changeItemMaterial.contains(e.getCurrentItem().getType().name()))
                    && (changeItemName != null && e.getCurrentItem().getItemMeta() != null
                    && changeItemName.equals(e.getCurrentItem().getItemMeta().getDisplayName().replace(("" + ChatColor.COLOR_CHAR), "&")))) {

                if (dataPack.getInteractionType() == InteractionType.SELECT_SALE_ITEM)
                    inventory.setItem(saleItemSlot, (shop.getShopItem() != null ? shop.getShopItem().clone() : null));
                else if (dataPack.getInteractionType() == InteractionType.SELECT_TRADE_ITEM)
                    inventory.setItem(tradeItemSlot, (shop.getTradeItem() != null ? shop.getTradeItem().clone() : null));

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);
            } else {
                final CustomItem changeItem = new CustomItem(changeItemMaterial, 1)
                        .setDisplayName(player, shop, Objects.requireNonNull(changeItemName))
                        .setLore(player, menu.getConfiguration().getStringList("item-change.lore"));
                inventory.setItem(e.getSlot(), changeItem.get());

                if (isSaleItemSlot) {
                    dataPack.setInteractionType(InteractionType.SELECT_SALE_ITEM);
                    dataPack.setInteractionValue(null);

                    if (tradeItemSlot >= 0 && tradeItemSlot < inventory.getSize())
                        inventory.setItem(tradeItemSlot, (shop.getTradeItem() != null ? shop.getTradeItem().clone() : null));
                } else {
                    dataPack.setInteractionType(InteractionType.SELECT_TRADE_ITEM);
                    dataPack.setInteractionValue(null);

                    if (saleItemSlot >= 0 && saleItemSlot < inventory.getSize()) inventory.setItem(saleItemSlot, (shop.getShopItem() != null ? shop.getShopItem().clone() : null));
                }
            }

            playClickSound(player);
            return;
        } else if (inventory.getType() == InventoryType.PLAYER) {

            ItemStack selectedItem = e.getCurrentItem();
            if (selectedItem == null) return;

            if (dataPack.getInteractionType() == null) return;

            boolean forceSingleStack = INSTANCE.getConfig().getBoolean("force-single-stack");
            ItemStack selectedItemClone = selectedItem.clone();
            if (forceSingleStack) selectedItemClone.setAmount(1);

            if (INSTANCE.getListeners().isBlockedItem(selectedItem) || INSTANCE.getBlockedItemId(selectedItem) >= 0) {
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("set-item-blocked"));
                return;
            }

            if (dataPack.getInteractionType() == InteractionType.SELECT_SALE_ITEM) {

                if (shop.getStock() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-empty-required"));
                    return;
                }

                EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.sale-item-change"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);

                shop.setShopItem(selectedItemClone);
                shop.setShopItemAmount(shop.getShopItem().getAmount());
                e.getView().getTopInventory().setItem(saleItemSlot, shop.getShopItem().clone());

                final double minBuyPrice = INSTANCE.getManager().getMaterialMinPrice(selectedItem, true),
                        maxBuyPrice = INSTANCE.getManager().getMaterialMaxPrice(selectedItem, true),
                        minSellPrice = INSTANCE.getManager().getMaterialMinPrice(selectedItem, false),
                        maxSellPrice = INSTANCE.getManager().getMaterialMaxPrice(selectedItem, false),
                        buyPrice = shop.getBuyPrice(false), sellPrice = shop.getSellPrice(false);

                if (buyPrice < minBuyPrice) shop.setBuyPrice(minBuyPrice);
                else if (buyPrice > maxBuyPrice) shop.setBuyPrice(maxBuyPrice);

                if (sellPrice < minSellPrice) shop.setSellPrice(minSellPrice);
                else if (sellPrice > maxSellPrice) shop.setSellPrice(maxSellPrice);

                if (shop.getStock() >= 0) shop.setStock(shop.getShopItem().getAmount());

                // remove one from stack, if forceSingleStack is true
                if (forceSingleStack && selectedItem.getAmount() > 1) {
                    selectedItem.setAmount(selectedItem.getAmount() - 1);
                    inventory.setItem(e.getSlot(), selectedItem);
                } else inventory.setItem(e.getSlot(), null);

                player.updateInventory();

                String description = "";
                if (INSTANCE.getConfig().getBoolean("enchantment-description-set") && shop.getShopItem() != null
                        && (shop.getShopItem().getEnchantments().size() > 0 || shop.getShopItem().getItemMeta() instanceof EnchantmentStorageMeta))
                    description = description + INSTANCE.getManager().getEnchantmentLine(shop.getShopItem());
                else if (INSTANCE.getConfig().getBoolean("potion-description-set") && shop.getShopItem() != null
                        && shop.getShopItem().getItemMeta() instanceof PotionMeta)
                    description = (description + INSTANCE.getManager().getPotionLine(shop.getShopItem()));

                shop.setDescription(description);

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-item-set"));

                INSTANCE.getInSightTask().refreshShop(shop);
                INSTANCE.runEventCommands("shop-item-set", player);

            } else {

                EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                        EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.trade-item-change"));
                if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);

                shop.setTradeItem(selectedItemClone);
                e.getView().getTopInventory().setItem(tradeItemSlot, shop.getTradeItem().clone());

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("trade-item-set"));
            }

            return;
        }

        if (inventory.getType() == InventoryType.PLAYER || e.getCurrentItem() == null) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName == null || buttonName.isEmpty()) return;

        Menu tempMenu = null;
        switch (buttonName) {
            case "appearance": {
                tempMenu = INSTANCE.getMenu("appearance");
                break;
            }

            case "assistants": {
                tempMenu = INSTANCE.getMenu("assistants");
                break;
            }

            case "description": {
                final String title = menu.getConfiguration().getString("description-entry.title");

                new AnvilGUI.Builder()
                        .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                        .onClick((slot, stateSnapshot) -> {

                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String filteredEntry = stateSnapshot.getText().substring(0, Math.min(stateSnapshot.getText().length(),
                                    INSTANCE.getConfig().getInt("description-character-limit")));
                            List<String> filterList = INSTANCE.getConfig().getStringList("description-filter");
                            for (int i = -1; ++i < filterList.size(); )
                                filteredEntry = filteredEntry.replaceAll("(?i)" + filterList.get(i), "");
                            filteredEntry = ChatColor.stripColor(INSTANCE.getManager().color(filteredEntry
                                    .replace("'", "").replace("\"", "")));

                            if (filteredEntry.equalsIgnoreCase(shop.getDescription())) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                                return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(menu.getConfiguration().getString("description-entry.too-similar")));
                            }

                            EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                                    EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.global-buy-limit"));
                            if (economyCallEvent == null || !economyCallEvent.willSucceed()) return AnvilGUI.Response.close();

                            shop.setDescription(filteredEntry);
                            shop.updateTimeStamp();

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("description-set"),
                                    ("{description}:" + shop.getDescription()));

                            INSTANCE.runEventCommands("shop-description", player);
                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                        })
                        .text((shop.getDescription() != null && !shop.getDescription().isEmpty()) ?
                                INSTANCE.getManager().color(shop.getDescription()) : " ")
                        .title(INSTANCE.getManager().color(title))
                        .plugin(INSTANCE).open(player);

                playClickSound(player);
                return;
            }

            case "clear-limits": {

                shop.setGlobalSellCounter(0);
                shop.setGlobalBuyCounter(0);
                dataPack.updateCurrentTransactionLimitCounter(shop, true, 0);
                dataPack.updateCurrentTransactionLimitCounter(shop, false, 0);

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("limits-cleared"));
                break;
            }

            case "delete":
            case "destroy": {
                tempMenu = INSTANCE.getMenu("confirm");
                final InteractionType approxType = InteractionType.getApproxType(buttonName);

                dataPack.setInteractionType(approxType);
                dataPack.setInteractionValue(null);
                break;
            }

            case "close": {
                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);
                dataPack.resetEditData();

                player.closeInventory();
                break;
            }

            case "sale-item-arrow": {

                if (shop.getStock() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-empty-required"));
                    return;
                }

                shop.setShopItem(null);
                shop.setShopItemAmount(1);

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);

                inventory.setItem(saleItemSlot, null);
                inventory.setItem(tradeItemSlot, (shop.getTradeItem() != null ? shop.getTradeItem().clone() : null));

                INSTANCE.getInSightTask().refreshShop(shop);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sale-item-cleared"));
                break;
            }

            case "trade-item-arrow": {

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);

                inventory.setItem(saleItemSlot, (shop.getShopItem() != null ? shop.getShopItem().clone() : null));
                inventory.setItem(tradeItemSlot, null);

                shop.setTradeItem(null);
                INSTANCE.getInSightTask().refreshShop(shop);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("trade-item-cleared"));
                break;
            }

            case "buy-price":
            case "sell-price":
            case "balance":
            case "shop-item-amount":
            case "player-buy-limit":
            case "player-sell-limit":
            case "global-buy-limit":
            case "global-sell-limit":
            case "stock": {
                tempMenu = INSTANCE.getMenu("amount-selector");
                final InteractionType approxType = InteractionType.getApproxType(buttonName);

                dataPack.setInteractionType(approxType);
                dataPack.setInteractionValue(null);
                break;
            }

            default: {
                return;
            }
        }

        if (tempMenu != null) player.openInventory(tempMenu.build(player));
        playClickSound(player);
    }

    private void operateTransactionMenu(InventoryClickEvent e, Inventory inventory, Menu menu, Player player) {
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack) || e.getClickedInventory() == null) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop != null) shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString()))) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        boolean useVault = (INSTANCE.getConfig().getBoolean("use-vault") && INSTANCE.getVaultEconomy() != null),
                forceUse = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");
        final int previewSlot = menu.getConfiguration().getInt("preview-slot");

        if (e.getSlot() == previewSlot) {

            ItemStack previewSlotItem = inventory.getItem(previewSlot);
            if (previewSlotItem == null) return;

            playClickSound(player);

            if (shop.getShopItem().getType() == previewSlotItem.getType()) {
                ItemStack currencyItem = (!forceUse && shop.getTradeItem() != null) ? shop.getTradeItem().clone() :
                        INSTANCE.getManager().buildShopCurrencyItem(1);
                ItemMeta itemMeta = currencyItem.getItemMeta();
                if (itemMeta != null) {
                    List<String> tradeLore = menu.getConfiguration().getStringList("sale-item-lore"),
                            lore = (currencyItem.getItemMeta().getLore() != null ? currencyItem.getItemMeta().getLore() : new ArrayList<>());
                    for (int i = -1; ++i < tradeLore.size(); )
                        lore.add(INSTANCE.getManager().color(tradeLore.get(i)
                                .replace("{sell}", INSTANCE.getManager().formatNumber(shop.getSellPrice(true), false))
                                .replace("{buy}", INSTANCE.getManager().formatNumber(shop.getBuyPrice(true), false))));
                    itemMeta.setLore(lore);
                    currencyItem.setItemMeta(itemMeta);
                }

                inventory.setItem(previewSlot, currencyItem);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-trade-item"));
            } else {
                ItemStack previewItem = shop.getShopItem().clone();
                if (!(INSTANCE.getConfig().getBoolean("use-vault") && INSTANCE.getVaultEconomy() != null)) {
                    ItemMeta itemMeta = previewItem.getItemMeta();
                    if (itemMeta != null) {
                        List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore()),
                                previewLore = menu.getConfiguration().getStringList("trade-item-lore");
                        for (int i = -1; ++i < previewLore.size(); )
                            lore.add(INSTANCE.getManager().color(previewLore.get(i)));
                        itemMeta.setLore(lore);
                        previewItem.setItemMeta(itemMeta);
                    }
                }

                previewItem.setAmount(Math.min(dataPack.getSelectedShop().getShopItemAmount(), previewItem.getMaxStackSize()));
                inventory.setItem(previewSlot, previewItem);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-preview-item"));
            }

            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName == null || buttonName.isEmpty()) return;

        final int unitItemSlot = menu.getConfiguration().getInt("buttons.unit.slot");

        switch (buttonName) {

            case "buy": {
                playClickSound(player);

                if (shop.getBuyPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-buy-invalid"));
                    player.updateInventory();
                    return;
                }

                final int currentCd = dataPack.getCooldown(player, "transaction-cd",
                        INSTANCE.getConfig().getInt("transaction-cooldown"));
                if (currentCd > 0) return;
                else dataPack.updateCooldown("transaction-cd");

                int unitCount = 1;
                final ItemStack unitCountItem = inventory.getItem(unitItemSlot);
                if (unitCountItem != null) unitCount = unitCountItem.getAmount();

                if ((shop.isAdminShop() && (shop.getStock() >= 0 && shop.getStock() < (shop.getShopItemAmount() * unitCount)))
                        || (!shop.isAdminShop() && shop.getStock() < (shop.getShopItemAmount() * unitCount))) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-low-stock"));
                    player.updateInventory();
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, true, false)) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
                    player.updateInventory();
                    return;
                }

                final int availableSpace = INSTANCE.getManager().getInventorySpaceForItem(player, shop.getShopItem());
                if (player.getInventory().firstEmpty() == -1 || availableSpace < (unitCount * shop.getShopItemAmount())) {
                    dataPack.resetEditData();
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("insufficient-space"))
                            .replace("{space}", INSTANCE.getManager().formatNumber(availableSpace, false)));
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.BUY, unitCount);
                break;
            }

            case "buy-all": {
                playClickSound(player);

                if (shop.getBuyPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-buy-invalid"));
                    player.updateInventory();
                    return;
                }

                int availableUnits = (shop.getStock() < 0 ? -1 : Math.max(0, (shop.getStock() / shop.getShopItemAmount())));
                if (shop.getGlobalBuyLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, true, true);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
                        player.updateInventory();
                        return;
                    }

                    availableUnits = (int) Math.min(availableUnits, remainingLimit);

                } else if (shop.getPlayerBuyLimit() > 0) {
                    long remainingLimit = (shop.getPlayerBuyLimit() - dataPack.getCurrentTransactionCounter(shop, true, false));
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
                        player.updateInventory();
                        return;
                    }

                    availableUnits = (int) Math.min(availableUnits, remainingLimit);
                }

                if ((!shop.isAdminShop() && shop.getStock() <= 0) || (shop.isAdminShop() && (shop.getStock() >= 0 && shop.getStock() < shop.getShopItemAmount()))) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-low-stock"));
                    player.updateInventory();
                    return;
                }

                double investorBalance, tax = INSTANCE.getConfig().getDouble("transaction-tax");
                if (useVault) investorBalance = INSTANCE.getVaultEconomy().getBalance(player);
                else {
                    final ItemStack currencyItem = (INSTANCE.getConfig().getBoolean("shop-currency-item.force-use")
                            || shop.getTradeItem() == null) ? INSTANCE.getManager().buildShopCurrencyItem(1) : shop.getTradeItem();
                    investorBalance = INSTANCE.getManager().getItemAmount(player.getInventory(), currencyItem);
                }

                final double buyPrice = shop.getBuyPrice(true);
                final int maxBuyAll = INSTANCE.getConfig().getInt("maximum-buy-all");

                int affordableUnits = (player.hasPermission("displayshops.bypass") ? maxBuyAll
                        : Math.min(maxBuyAll, (int) (investorBalance / (buyPrice + (buyPrice * tax)))));
                availableUnits = ((availableUnits < 0 && shop.isAdminShop()) ? affordableUnits : Math.min(maxBuyAll, availableUnits));

                if (availableUnits <= 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("no-affordable-units"));
                    return;
                }

                final int availableSpace = INSTANCE.getManager().getInventorySpaceForItem(player, shop.getShopItem());
                if (player.getInventory().firstEmpty() == -1 || availableSpace < shop.getShopItemAmount()) {
                    dataPack.resetEditData();
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("insufficient-space"))
                            .replace("{space}", INSTANCE.getManager().formatNumber(availableSpace, false)));
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.BUY, Math.max(0, Math.min((availableSpace / shop.getShopItemAmount()), availableUnits)));
                break;
            }

            case "sell": {
                playClickSound(player);

                if (shop.getSellPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-sell-invalid"));
                    player.updateInventory();
                    return;
                }

                if (shop.getCommands().size() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("commands-sell-fail"));
                    player.updateInventory();
                    return;
                }

                final int itemAmount = INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem());

                int unitCount = 1;
                final ItemStack unitCountItem = inventory.getItem(menu.getConfiguration().getInt("buttons.unit.slot"));
                if (unitCountItem != null) unitCount = unitCountItem.getAmount();

                if (itemAmount < (shop.getShopItemAmount() * unitCount)) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-invalid-amount"))
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount() * unitCount, false)));
                    player.updateInventory();
                    return;
                }

                final int maxStock = INSTANCE.getManager().getMaxStock(shop);
                if ((!shop.isAdminShop() || (shop.isAdminShop() && shop.getStock() >= 0))
                        && (shop.getStock() + (shop.getShopItemAmount() * unitCount)) > maxStock) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-max-stock"))
                            .replace("{max}", INSTANCE.getManager().formatNumber(maxStock, false)));
                    player.updateInventory();
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, false, false)) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
                    player.updateInventory();
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.SELL, unitCount);
                break;
            }

            case "sell-all": {
                playClickSound(player);

                if (shop.getSellPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-sell-invalid"));
                    player.updateInventory();
                    return;
                }

                final int itemAmount = INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem()),
                        maxSellAll = INSTANCE.getConfig().getInt("maximum-sell-all");
                int totalSellableUnits = (itemAmount / shop.getShopItemAmount()); // investor

                if (itemAmount < shop.getShopItemAmount()) {
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-invalid-amount"))
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount(), false)));
                    return;
                }

                if (!shop.isAdminShop() || shop.getStock() >= 0) {
                    final long maxStock = INSTANCE.getManager().getMaxStock(shop),
                            availableSpace = (maxStock - shop.getStock());
                    totalSellableUnits = (int) Math.min((availableSpace / shop.getShopItemAmount()), totalSellableUnits);
                    if (totalSellableUnits <= 0) {
                        INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-max-stock"))
                                .replace("{max}", INSTANCE.getManager().formatNumber(maxStock, false)));
                        return;
                    }
                }

                if (shop.getGlobalSellLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, false, true);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
                        player.updateInventory();
                        return;
                    }

                    totalSellableUnits = (int) Math.min(totalSellableUnits, remainingLimit);

                } else if (shop.getGlobalSellLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, false, false);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
                        player.updateInventory();
                        return;
                    }

                    totalSellableUnits = (int) Math.min(totalSellableUnits, remainingLimit);
                }

                if (totalSellableUnits <= 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("transaction-all-fail"));
                    player.updateInventory();
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.SELL, Math.min(totalSellableUnits, maxSellAll));
                break;
            }

            case "unit-increase": {
                playClickSound(player);

                ItemStack unitCountItem = inventory.getItem(unitItemSlot);
                if (unitCountItem == null) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                    return;
                }

                if (unitCountItem.getAmount() >= unitCountItem.getType().getMaxStackSize()) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-max"));
                    return;
                }

                unitCountItem.setAmount(Math.min(unitCountItem.getMaxStackSize(), (unitCountItem.getAmount() + 1)));

                updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-increased"));
                break;
            }

            case "unit-increase-more": {
                playClickSound(player);

                ItemStack unitCountItem = inventory.getItem(unitItemSlot);
                if (unitCountItem == null) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                    return;
                }

                if (unitCountItem.getAmount() >= unitCountItem.getType().getMaxStackSize()) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-max"));
                    return;
                }

                final int newStack = (unitCountItem.getAmount() + ((int) (unitCountItem.getType().getMaxStackSize() * 0.25)));
                unitCountItem.setAmount(Math.min(newStack, unitCountItem.getType().getMaxStackSize()));

                updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-increased"));
                break;
            }

            case "unit-decrease": {
                playClickSound(player);

                ItemStack unitCountItem = inventory.getItem(unitItemSlot);
                if (unitCountItem == null) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                    return;
                }

                if (unitCountItem.getAmount() <= 1) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-min"));
                    return;
                }

                unitCountItem.setAmount(Math.max(1, (unitCountItem.getAmount() - 1)));

                updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-decreased"));
                break;
            }

            case "unit-decrease-more": {
                playClickSound(player);

                ItemStack unitCountItem = inventory.getItem(menu.getConfiguration().getInt("buttons.unit.slot"));
                if (unitCountItem == null) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                    return;
                }

                if (unitCountItem.getAmount() <= 1) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-min"));
                    return;
                }

                final int newStack = (unitCountItem.getAmount() - ((int) (unitCountItem.getType().getMaxStackSize() * 0.25)));
                unitCountItem.setAmount(Math.max(1, newStack));

                updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-count-decreased"));
                break;
            }

            default: {
                break;
            }
        }
    }

    private void operateAppearanceMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {

        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        if (inventory.getType() == InventoryType.PLAYER || e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.CREATIVE || e.getCurrentItem() == null
                || e.getCurrentItem().getType().name().contains("AIR")) return;

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack)) return;

        final Shop shop = dataPack.getSelectedShop();

        if (shop.getBaseLocation() == null) {
            dataPack.resetEditData();
            player.closeInventory();
            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("base-location-invalid"));
            return;
        }

        shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (editPrevention && shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName != null && !buttonName.isEmpty()) {
            switch (buttonName) {

                case "next": {

                    playClickSound(player);

                    if (dataPack.hasNextPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {

                    playClickSound(player);

                    if (dataPack.hasPreviousPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "search": {

                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("search-entry.title");
                    new AnvilGUI.Builder()
                            .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                            .onClick((slot, stateSnapshot) -> {

                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                menu.loadPages(player, dataPack, shop, stateSnapshot.getText().trim());
                                menu.switchPage(inventory, player, dataPack.getCurrentPage());

                                return Collections.singletonList(AnvilGUI.ResponseAction.close());
                            })
                            .text(" ")
                            .title(INSTANCE.getManager().color(title))
                            .plugin(INSTANCE).open(player);
                    return;
                }

                case "return": {
                    playClickSound(player);

                    final Menu managementMenu = INSTANCE.getMenu("edit");
                    player.openInventory(managementMenu.build(player));

                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    return;
                }

                default: {
                    break;
                }
            }
        }

        if (e.getCurrentItem().getItemMeta() == null || !e.getCurrentItem().getItemMeta().hasDisplayName()) return;

        String shopId = INSTANCE.getPacketManager().getNBT(e.getCurrentItem(), "ds-bbm"),
                typeId = INSTANCE.getPacketManager().getNBT(e.getCurrentItem(), "ds-type");
        if (shopId == null || typeId == null || shopId.isEmpty() || typeId.isEmpty()) return;

        playClickSound(player);

        final Location baseBlockLocation = shop.getBaseLocation().asBukkitLocation();
        final Block block = baseBlockLocation.getBlock();

        final boolean hasStarPerm = player.hasPermission("displayshops.bbm.*");
        if (!hasStarPerm && !player.hasPermission("displayshops.bbm." + typeId)) {
            String message = INSTANCE.getLangConfig().getString("missing-bbm-requirement");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message);
            return;
        }

        final String unlockId = (typeId + ":" + e.getCurrentItem().getDurability());
        if (!dataPack.hasUnlockedBBM(unlockId)) {
            final double foundPrice = INSTANCE.getManager().getBBMPrice(typeId, e.getCurrentItem().getDurability());
            EconomyCallEvent economyCallEvent = new EconomyCallEvent(player, null, EconomyCallType.EDIT_ACTION, shop, foundPrice);
            if (economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                player.closeInventory();
                dataPack.setSelectedShop(null);
                return;
            } else economyCallEvent.performCurrencyTransfer(true);
            dataPack.unlockBaseBlock(unlockId);
        }

        shop.setStoredBaseBlockMaterial(typeId + ":" + e.getCurrentItem().getDurability());

        Material material = Material.getMaterial(typeId.toUpperCase().replace(" ", "_").replace("-", "_"));
        if (material != null) {
            if (INSTANCE.isItemAdderInstalled()) dev.lone.itemsadder.api.CustomBlock.remove(baseBlockLocation);
            block.setType(Material.AIR);
            block.setType(material);

            final boolean isOld = ((Math.floor(this.INSTANCE.getServerVersion()) <= 1_12));
            if (isOld) try {
                @SuppressWarnings("JavaReflectionMemberAccess") Method method = Block.class.getMethod("setData", byte.class);
                method.invoke(baseBlockLocation.getBlock(), ((byte) (e.getCurrentItem().getDurability() < 0
                        ? oppositeDirectionByte(Direction.getYaw(player)) : e.getCurrentItem().getDurability())));
            } catch (Exception ei) {
                ei.printStackTrace();
            }
            else {
                block.setBlockData(INSTANCE.getServer().createBlockData(material));
                setBlock(block, material, (material.name().contains("SHULKER") ? BlockFace.UP : BlockFace.valueOf(Direction.getYaw(player).name())));
            }
        } else if (INSTANCE.isItemAdderInstalled()) {
            dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(typeId);
            if (customBlock != null) {
                block.setType(Material.AIR);
                customBlock.place(baseBlockLocation);
            }
        }

        String changeSound = INSTANCE.getConfig().getString("immersion-section.shop-bbm-sound");
        if (changeSound != null && !changeSound.equalsIgnoreCase(""))
            player.playSound(player.getLocation(), Sound.valueOf(changeSound.toUpperCase().replace(" ", "_")
                    .replace("-", "_")), 1, 1);

        String changeEffect = INSTANCE.getConfig().getString("immersion-section.shop-bbm-particle");
        if (changeEffect != null && !changeEffect.equalsIgnoreCase(""))
            INSTANCE.getPacketManager().getParticleHandler().displayParticle((Player) e.getWhoClicked(), changeEffect.toUpperCase()
                            .replace(" ", "_").replace("-", "_"), baseBlockLocation.add(0.5, 0.5, 0.5),
                    0.5, 0.5, 0.5, 0, 12);

        dataPack.resetEditData();
        player.closeInventory();

        INSTANCE.getInSightTask().refreshShop(shop);

        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("base-block-set"));
    }

    private void operateAssistantsMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {

        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        if (inventory.getType() == InventoryType.PLAYER || e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.CREATIVE || e.getCurrentItem() == null
                || e.getCurrentItem().getType().name().contains("AIR")) return;

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack)) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop != null) shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString()))) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName != null && !buttonName.isEmpty()) {
            switch (buttonName) {

                case "next": {

                    playClickSound(player);

                    if (dataPack.hasNextPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {

                    playClickSound(player);

                    if (dataPack.hasPreviousPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "search": {

                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("search-entry.title");
                    new AnvilGUI.Builder()
                            .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                            .onClick((slot, stateSnapshot) -> {

                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                menu.loadPages(player, dataPack, shop, stateSnapshot.getText().trim());
                                menu.switchPage(inventory, player, dataPack.getCurrentPage());

                                return Collections.singletonList(AnvilGUI.ResponseAction.close());
                            })
                            .text(" ")
                            .title(INSTANCE.getManager().color(title))
                            .plugin(INSTANCE).open(player);
                    return;
                }

                case "return": {
                    playClickSound(player);

                    final Menu managementMenu = INSTANCE.getMenu("edit");
                    player.openInventory(managementMenu.build(player));

                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);

                    shop.save(true);
                    return;
                }

                default: {
                    break;
                }
            }
        }

        if (e.getCurrentItem().getItemMeta() == null || !e.getCurrentItem().getItemMeta().hasDisplayName()) return;

        final String uuidString = INSTANCE.getPacketManager().getNBT(e.getCurrentItem(), "uuid");
        if (uuidString == null || uuidString.isEmpty()) return;

        final UUID uuid = UUID.fromString(uuidString);

        playClickSound(player);

        final int assistantsCap = INSTANCE.getConfig().getInt("assistants-cap");
        if (shop.getAssistants().size() >= assistantsCap) {
            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-cap-exceeded"),
                    ("{cap}:" + INSTANCE.getManager().formatNumber(assistantsCap, false)));
            return;
        }

        final String activeColor = menu.getConfiguration().getString("active-color"),
                inactiveColor = menu.getConfiguration().getString("inactive-color");
        final boolean isActive = shop.getAssistants().contains(uuid);

        String currentName = "";
        final ItemStack headItem = e.getCurrentItem();
        if (headItem.getItemMeta() != null) {
            final ItemMeta itemMeta = headItem.getItemMeta();

            currentName = ChatColor.stripColor(itemMeta.getDisplayName());
            itemMeta.setDisplayName(INSTANCE.getManager().color((isActive ? inactiveColor : activeColor) + currentName));

            headItem.setItemMeta(itemMeta);
        }

        if (isActive) shop.getAssistants().remove(uuid);
        else shop.getAssistants().add(uuid);

        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-" + (isActive ? "removed" : "added")),
                ("{player}:" + currentName));
    }

    private void operateVisitMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.CREATIVE) return;

        if (e.getClickedInventory() == null || e.getClickedInventory().getType() == InventoryType.PLAYER || e.getCurrentItem() == null) return;
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName != null && !buttonName.isEmpty()) {
            switch (buttonName) {

                case "next": {

                    playClickSound(player);

                    if (dataPack.hasNextPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {

                    playClickSound(player);

                    if (dataPack.hasPreviousPage())
                        menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "search": {

                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("search-entry.title");
                    new AnvilGUI.Builder()
                            .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                            .onClick((slot, stateSnapshot) -> {

                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim());
                                menu.switchPage(inventory, player, dataPack.getCurrentPage());

                                return Collections.singletonList(AnvilGUI.ResponseAction.close());
                            })
                            .text(" ")
                            .title(INSTANCE.getManager().color(title))
                            .plugin(INSTANCE).open(player);
                    return;
                }

                case "return": {
                    playClickSound(player);

                    final Menu managementMenu = INSTANCE.getMenu("edit");
                    player.openInventory(managementMenu.build(player));

                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    return;
                }

                default: {
                    break;
                }
            }
        }

        String shopId = INSTANCE.getPacketManager().getNBT(e.getCurrentItem(), "shop-id");
        if (shopId == null || shopId.isEmpty()) return;

        final Shop selectedShop = INSTANCE.getManager().getShopMap().get(UUID.fromString(shopId));
        if (selectedShop == null) return;

        playClickSound(player);

        player.closeInventory();
        double visitCost = menu.getConfiguration().getDouble("visit-charge");
        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player,
                null, selectedShop, EconomyCallType.VISIT, visitCost);
        if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        selectedShop.visit(player, true);
    }

    private void operateAmountSelectorMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        if (inventory.getType() == InventoryType.PLAYER || e.getCurrentItem() == null) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack)) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop == null) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName == null || buttonName.isEmpty()) return;

        final int amountSlot = menu.getConfiguration().getInt("buttons.amount.slot");
        final ItemStack amountItem = inventory.getItem(amountSlot);
        if (amountItem == null) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        if (buttonName.startsWith("add-")) {
            final String amountString = buttonName.replace("add-", "");

            double finalAmount;
            if (amountString.equals("all")) {
                double maxAmount = 0;

                switch (dataPack.getInteractionType()) {
                    case AMOUNT_BUY_PRICE: {
                        maxAmount = INSTANCE.getConfig().getDouble("buy-price-limit");
                        break;
                    }

                    case AMOUNT_SELL_PRICE: {
                        maxAmount = INSTANCE.getConfig().getDouble("sell-price-limit");
                        break;
                    }

                    case SHOP_ITEM_AMOUNT: {
                        maxAmount = INSTANCE.getConfig().getDouble("max-item-stack-size");
                        break;
                    }

                    case AMOUNT_STOCK: {
                        maxAmount = INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem());
                        break;
                    }

                    case AMOUNT_BALANCE: {
                        maxAmount = INSTANCE.getManager().getCurrencyBalance(player, shop,
                                (INSTANCE.getVaultEconomy() != null && INSTANCE.getConfig().getBoolean("use-vault")));
                        break;
                    }

                    case AMOUNT_PLAYER_BUY_LIMIT:
                    case AMOUNT_GLOBAL_BUY_LIMIT: {
                        maxAmount = INSTANCE.getConfig().getLong("buy-limit-cap");
                        break;
                    }

                    case AMOUNT_GLOBAL_SELL_LIMIT:
                    case AMOUNT_PLAYER_SELL_LIMIT: {
                        maxAmount = INSTANCE.getConfig().getLong("sell-limit-cap");
                        break;
                    }

                    default: {
                        break;
                    }
                }

                amountItem.setAmount((int) Math.max(1, Math.min(maxAmount, amountItem.getMaxStackSize())));
                finalAmount = maxAmount;
            } else {
                if (INSTANCE.getManager().isNotNumeric(amountString)) return;

                final double amount = Double.parseDouble(amountString);
                amountItem.setAmount((int) Math.max(amount, 1));

                final double foundAmount = Double.parseDouble(INSTANCE.getPacketManager().getNBT(amountItem, "ds-amount"));
                finalAmount = (foundAmount + amount);
            }

            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, finalAmount);
            return;
        } else if (buttonName.startsWith("remove-")) {
            final String amountString = buttonName.replace("remove-", "");
            double finalAmount;
            if (amountString.equals("all")) {
                amountItem.setAmount(1);
                finalAmount = ((dataPack.getLongTermInteractionValue() != null
                        && dataPack.getLongTermInteractionValue() instanceof Integer) ? (double) dataPack.getLongTermInteractionValue() : 0);
            } else {
                if (INSTANCE.getManager().isNotNumeric(amountString)) return;

                final double amount = Double.parseDouble(amountString);
                amountItem.setAmount((int) Math.max(amount, 1));

                final double foundAmount = Double.parseDouble(INSTANCE.getPacketManager().getNBT(amountItem, "ds-amount"));
                finalAmount = (foundAmount - amount);
            }

            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, finalAmount);
            return;
        }

        switch (buttonName) {

            case "custom-amount": {

                final String title = Objects.requireNonNull(menu.getConfiguration().getString("custom-amount-entry.title"));

                new AnvilGUI.Builder()
                        .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                        .onClick((slot, stateSnapshot) -> {

                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String text = stateSnapshot.getText().replace(" ", "").replace(",", ".");

                            if (dataPack.getInteractionType() != InteractionType.AMOUNT_STOCK
                                    && dataPack.getInteractionType() != InteractionType.AMOUNT_BALANCE) {
                                if (text.startsWith("-"))
                                    return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(menu.getConfiguration().getString("custom-amount-entry.negative")));
                            }

                            if (INSTANCE.getManager().isNotNumeric(text))
                                return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(menu.getConfiguration().getString("custom-amount-entry.invalid")));

                            dataPack.setInteractionValue(Double.parseDouble(text));
                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                        })
                        .text(" ")
                        .title(INSTANCE.getManager().color(title))
                        .plugin(INSTANCE).open(player);

                playClickSound(player);
                break;
            }

            case "confirm": {

                final double amount = Double.parseDouble(INSTANCE.getPacketManager().getNBT(amountItem, "ds-amount"));

                Menu menuToOpen = null;
                switch (dataPack.getInteractionType()) {

                    case AMOUNT_BUY_PRICE: {
                        shop.setBuyPrice(amount);

                        final String message = INSTANCE.getLangConfig().getString((amount <= -1) ? "buying-disabled" : "buy-price-set");
                        INSTANCE.getManager().sendMessage(player, message,
                                ("{price}:" + INSTANCE.getManager().formatNumber(amount, true)));

                        INSTANCE.runEventCommands("shop-buy-price", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_SELL_PRICE: {
                        shop.setSellPrice(amount);

                        final String message = INSTANCE.getLangConfig().getString((amount <= -1) ? "selling-disabled" : "sell-price-set");
                        INSTANCE.getManager().sendMessage(player, message,
                                ("{price}:" + INSTANCE.getManager().formatNumber(amount, true)));

                        INSTANCE.runEventCommands("shop-sell-price", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case SHOP_ITEM_AMOUNT: {
                        if (amount > INSTANCE.getConfig().getInt("max-item-stack-size") || amount < 1) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-stack-size"));
                            break;
                        }

                        if (amount == shop.getShopItemAmount()) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            break;
                        }

                        shop.setShopItemAmount((int) amount);

                        final String message = INSTANCE.getLangConfig().getString("stack-size-set");
                        INSTANCE.getManager().sendMessage(player, message,
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-amount", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_BALANCE: {
                        final boolean isRemoval = (amount < 0),
                                useVault = (INSTANCE.getVaultEconomy() != null && INSTANCE.getConfig().getBoolean("use-vault"));

                        String tradeItemName = null;
                        ItemStack tradeItem = null;
                        if (!useVault) {
                            tradeItem = (INSTANCE.getConfig().getBoolean("shop-currency-item.force-use")
                                    ? INSTANCE.getManager().buildShopCurrencyItem(1) : (shop.getTradeItem() != null
                                    ? shop.getTradeItem() : INSTANCE.getManager().buildShopCurrencyItem(1)));
                            if (tradeItem != null) tradeItemName = INSTANCE.getManager().getItemName(tradeItem);
                        }

                        if (isRemoval) {

                            if (-amount > shop.getStoredBalance()) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("balance-withdraw-fail"),
                                        ("{balance}:" + INSTANCE.getManager().formatNumber(shop.getStoredBalance(), true)));
                                return;
                            }

                            if (useVault) INSTANCE.getVaultEconomy().depositPlayer(player, -amount);
                            else if (tradeItem != null) {
                                int stackCount = (int) (-amount / tradeItem.getType().getMaxStackSize()),
                                        remainder = (int) (-amount % tradeItem.getType().getMaxStackSize());

                                tradeItem.setAmount(tradeItem.getType().getMaxStackSize());
                                for (int i = -1; ++i < stackCount; )
                                    if (player.getInventory().firstEmpty() == -1)
                                        player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                                    else player.getInventory().addItem(tradeItem);

                                if (remainder > 0) {
                                    tradeItem.setAmount(remainder);
                                    if (player.getInventory().firstEmpty() == -1)
                                        player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                                    else player.getInventory().addItem(tradeItem);
                                }
                            }
                        } else {

                            if (useVault ? !INSTANCE.getVaultEconomy().has(player, amount)
                                    : INSTANCE.getManager().getItemAmount(player.getInventory(), Objects.requireNonNull(tradeItem)) < amount) {
                                dataPack.setInteractionValue(null);
                                player.openInventory(menu.build(player));

                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("insufficient-funds"),
                                        ("{trade-item}:" + (tradeItemName != null ? tradeItemName : " ")),
                                        ("{price}:" + INSTANCE.getManager().formatNumber(amount, true)));
                                return;
                            }

                            if ((shop.getStoredBalance() + amount) >= INSTANCE.getConfig().getLong("max-stored-currency")) {
                                dataPack.setInteractionValue(null);
                                player.openInventory(menu.build(player));

                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("max-stored-currency"),
                                        ("{trade-item}:" + (tradeItemName != null ? tradeItemName : " ")),
                                        ("{amount}:" + INSTANCE.getManager().formatNumber(amount, true)));
                                return;
                            }

                            if (useVault) INSTANCE.getVaultEconomy().withdrawPlayer(player, amount);
                            else INSTANCE.getManager().removeItem(player.getInventory(), tradeItem, (int) amount);
                        }

                        shop.setStoredBalance(Math.max((shop.getStoredBalance() + amount), 0));
                        shop.updateTimeStamp();

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("balance-" + (isRemoval ? "withdrawn" : "deposited")),
                                ("{trade-item}:" + (tradeItemName != null ? tradeItemName : " ")),
                                ("{amount}:" + INSTANCE.getManager().formatNumber((amount >= 0) ? amount : -amount, true)));

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_STOCK: {

                        if (shop.isAdminShop() && shop.getStock() == -1) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-infinite-stock"));
                            return;
                        }

                        final boolean isRemoval = (amount < 0);

                        if (isRemoval) {

                            final int availableSpace = Math.min(INSTANCE.getManager().getInventorySpaceForItem(player, shop.getShopItem()),
                                    (36 * shop.getShopItem().getMaxStackSize()));
                            final int newAmount = (int) Math.min(-amount, availableSpace);

                            if (-amount > shop.getStock()) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("stock-withdraw-fail"),
                                        ("{amount}:" + INSTANCE.getManager().formatNumber(-amount, false)));
                                return;
                            }

                            if (availableSpace <= 0) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("insufficient-space"),
                                        ("{space}:" + INSTANCE.getManager().formatNumber(availableSpace, false)));
                                return;
                            }

                            shop.setStock(shop.getStock() - newAmount);

                            final ItemStack itemStack = shop.getShopItem().clone();
                            INSTANCE.getServer().getScheduler().runTask(INSTANCE, () ->
                                    INSTANCE.getManager().giveItemStacks(player, itemStack, (int) (double) newAmount));

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("withdrawn-stock"),
                                    ("{amount}:" + INSTANCE.getManager().formatNumber(newAmount, false)));

                            INSTANCE.runEventCommands("shop-withdraw", player);

                        } else {

                            final int difference = (int) amount, maxStock = INSTANCE.getManager().getMaxStock(shop);
                            int totalItemCount = INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem());
                            if (totalItemCount <= 0 || totalItemCount < difference) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("insufficient-items"));
                                return;
                            }

                            final int amountToRemove = (difference > 0 && difference >= amount ? (int) amount : difference);
                            if (amountToRemove == 0 || shop.getStock() >= maxStock) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("stock-deposit-fail"),
                                        ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));
                                return;
                            }

                            INSTANCE.getManager().removeItem(player.getInventory(), shop.getShopItem(), amountToRemove);
                            shop.setStock(shop.getStock() + amountToRemove);

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("deposited-stock"),
                                    ("{amount}:" + INSTANCE.getManager().formatNumber(amountToRemove, false)));

                            INSTANCE.runEventCommands("shop-deposit", player);
                        }

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_PLAYER_BUY_LIMIT: {
                        if (amount > ((float) (INSTANCE.getManager().getMaxStock(shop)) / Math.max(1, shop.getShopItemAmount()))) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getPlayerBuyLimit()) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                                EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.global-buy-limit"));
                        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                        shop.setPlayerBuyLimit((int) amount);

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-buy-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-buy-limit", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_PLAYER_SELL_LIMIT: {
                        if (amount > ((float) (INSTANCE.getManager().getMaxStock(shop)) / Math.max(1, shop.getShopItemAmount()))) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getPlayerSellLimit()) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                                EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.player-sell-limit"));
                        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                        shop.setPlayerBuyLimit((int) amount);

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-buy-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-buy-limit", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_GLOBAL_BUY_LIMIT: {
                        if (amount > ((float) (INSTANCE.getManager().getMaxStock(shop)) / Math.max(1, shop.getShopItemAmount()))) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getGlobalBuyLimit()) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                                EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.global-buy-limit"));
                        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                        shop.setGlobalBuyLimit((int) amount);

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("global-buy-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-buy-limit", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    case AMOUNT_GLOBAL_SELL_LIMIT: {
                        if (amount > ((float) (INSTANCE.getManager().getMaxStock(shop)) / Math.max(1, shop.getShopItemAmount()))) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getGlobalSellLimit()) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, null, shop,
                                EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.global-sell-limit"));
                        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

                        shop.setGlobalSellLimit((int) amount);

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("global-sell-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-sell-limit", player);

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }

                    default: {
                        break;
                    }
                }

                playClickSound(player);
                INSTANCE.getInSightTask().refreshShop(shop);

                // refresh the edit status to prevent any issues
                player.closeInventory();
                dataPack.setSelectedShop(shop);

                if (menuToOpen != null) player.openInventory(menuToOpen.build(player));
                break;
            }

            case "return": {
                final Menu editMenu = INSTANCE.getMenu("edit");
                player.openInventory(editMenu.build(player));
                playClickSound(player);
                break;
            }

            default: {
                break;
            }
        }
    }

    private void updateItemAmount(@NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player, DataPack dataPack, int amountSlot,
                                  ItemStack amountItem, double finalAmount) {
        final ItemMeta itemMeta = amountItem.getItemMeta();
        if (itemMeta != null) {
            String name = menu.getConfiguration().getString("buttons.amount.name");
            if (name != null) {
                final String currencySymbol = INSTANCE.getConfig().getString("currency-symbol");
                final boolean isDecimal = (dataPack.getInteractionType().name().contains("PRICE") || dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE);

                itemMeta.setDisplayName(INSTANCE.getManager().color(name
                        .replace("{currency-symbol}", (isDecimal ? (currencySymbol != null ? currencySymbol : "") : ""))
                        .replace("{amount}", INSTANCE.getManager().formatNumber(finalAmount, isDecimal))));

                amountItem.setItemMeta(itemMeta);
            }
        }

        amountItem.setAmount((int) Math.min(amountItem.getMaxStackSize(), Math.max(1, finalAmount)));

        inventory.setItem(amountSlot, INSTANCE.getPacketManager().getSerializeUtil().updateNBT(amountItem, "ds-amount", String.valueOf(finalAmount)));
        playClickSound(player);
    }

    private void operateConfirmMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        if (inventory.getType() == InventoryType.PLAYER || e.getCurrentItem() == null) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack)) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop != null) shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) || !shop.canEdit(player)) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName == null || buttonName.isEmpty()) return;

        switch (buttonName) {

            case "confirm": {

                if (dataPack.getInteractionType() == InteractionType.DELETE) {

                    MarketRegion marketRegion = INSTANCE.getManager().getMarketRegion(shop.getBaseLocation().asBukkitLocation());
                    if (marketRegion != null) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("rentable-shop-delete"));
                        return;
                    }

                    if (!player.hasPermission("displayshops.delete")) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("no-permission"));
                        return;
                    }

                    if (INSTANCE.getConfig().getBoolean("require-empty-stock") && !shop.isAdminShop() && shop.getStock() > 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-empty-required"));
                        return;
                    }

                    EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player,
                            null, shop, EconomyCallType.EDIT_ACTION, INSTANCE.getConfig().getDouble("prices.delete-shop"));
                    if (economyCallEvent == null || (economyCallEvent.isCancelled() || !economyCallEvent.willSucceed())) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    ShopDeletionEvent shopDeletionEvent = new ShopDeletionEvent(player, shop.getBaseLocation().asBukkitLocation());
                    if (shopDeletionEvent.isCancelled()) return;

                    shop.purge(player, true);

                    dataPack.resetEditData();
                    player.closeInventory();
                }

                break;
            }

            case "deny": {

                if (dataPack.getInteractionType() == InteractionType.DELETE) {
                    final Menu editMenu = INSTANCE.getMenu("edit");
                    player.openInventory(editMenu.build(player));
                }

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);
                break;
            }

            default: {
                return;
            }
        }

        playClickSound(player);
    }


    // helper methods
    private void playClickSound(@NotNull Player player) {
        player.playSound(player.getLocation(), Math.floor(INSTANCE.getServerVersion()) >= 1_9
                ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
    }

    public String getInventoryName(@NotNull Inventory inventory, @NotNull InventoryView inventoryView) {
        if ((Math.floor(INSTANCE.getServerVersion()) >= 1_14)) return inventoryView.getTitle();
        else {
            try {
                Method method = inventory.getClass().getMethod("getTitle");
                return (String) method.invoke(inventory);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private boolean checkInteractiveTouches(@NotNull InventoryClickEvent e) {
        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        return (e.getClick() != ClickType.DOUBLE_CLICK && e.getClick() != ClickType.CREATIVE
                && e.getAction() != InventoryAction.HOTBAR_MOVE_AND_READD
                && e.getAction() != InventoryAction.HOTBAR_SWAP
                && e.getAction() != InventoryAction.CLONE_STACK);
    }

    private boolean inventoryCheck(Player player, DataPack dataPack) {
        if (dataPack.getSelectedShop() == null) {

            player.closeInventory();

            final String message = INSTANCE.getLangConfig().getString("shop-edit-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message);
            return false;
        }

        return true;
    }

    private void setBlock(Block block, Material material, BlockFace blockFace) {
        block.setType(material);

        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional) {
            ((Directional) blockData).setFacing(blockFace);
            block.setBlockData(blockData);
        }

        if (blockData instanceof Orientable) {
            ((Orientable) blockData).setAxis(Axis.valueOf(convertBlockFaceToAxis(blockFace)));
            block.setBlockData(blockData);
        }

        if (blockData instanceof Rotatable) {
            ((Rotatable) blockData).setRotation(blockFace);
            block.setBlockData(blockData);
        }
    }

    private String convertBlockFaceToAxis(BlockFace face) {
        switch (face) {
            case NORTH:
            case SOUTH:
                return "Z";
            case UP:
            case DOWN:
                return "Y";
            case EAST:
            case WEST:
            default:
                return "X";
        }
    }

    private byte oppositeDirectionByte(Direction direction) {
        for (int i = -1; ++i < Direction.values().length; )
            if (direction == Direction.values()[i]) return (byte) i;
        return 4;
    }

    private void updateTransactionButtons(@NotNull Player player, @NotNull Inventory clickedInventory, @NotNull Shop shop, @NotNull Menu menu, int unitCount) {

        final int buySlot = menu.getConfiguration().getInt("buttons.buy.slot"),
                sellSlot = menu.getConfiguration().getInt("buttons.sell.slot");
        // buyAllSlot = menu.getConfiguration().getInt("buttons.buy-all.slot"),
        // sellAllSlot = menu.getConfiguration().getInt("buttons.sell-all.slot");

        ItemStack buyItemStack = clickedInventory.getItem(buySlot);
        if (buyItemStack != null) {
            final CustomItem buyItem = new CustomItem(buyItemStack, shop, (shop.getShopItem() != null ? shop.getShopItem().getMaxStackSize() : 1), unitCount)
                    .refreshPlaceholders(player, "transaction", "buy");
            clickedInventory.setItem(buySlot, buyItem.get());
        }

        ItemStack sellItemStack = clickedInventory.getItem(sellSlot);
        if (sellItemStack != null) {
            final CustomItem sellItem = new CustomItem(sellItemStack, shop, (shop.getShopItem() != null ? shop.getShopItem().getMaxStackSize() : 1), unitCount)
                    .refreshPlaceholders(player, "transaction", "sell");
            clickedInventory.setItem(sellSlot, sellItem.get());
        }
    }

    private void runEconomyCall(Player player, Shop shop, EconomyCallType economyCallType, int unitCount) {

        OfflinePlayer owner = null;
        if (shop.getOwnerUniqueId() != null)
            owner = INSTANCE.getServer().getOfflinePlayer(shop.getOwnerUniqueId());

        final boolean useVault = (INSTANCE.getConfig().getBoolean("use-vault") && INSTANCE.getVaultEconomy() != null),
                syncOwner = INSTANCE.getConfig().getBoolean("sync-owner-balance");
        final double totalShopBalance = (shop.getStoredBalance() + (unitCount * shop.getSellPrice(true)));

        if (economyCallType == EconomyCallType.SELL && !syncOwner && totalShopBalance >= INSTANCE.getConfig().getLong("max-stored-currency")) {
            final String message = INSTANCE.getLangConfig().getString("max-stored-currency");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message);
            return;
        }

        // boolean canBypassEconomy = player.hasPermission("displayshops.bypass");
        EconomyCallEvent economyCallEvent = INSTANCE.getManager().initiateShopEconomyTransaction(player, owner, shop, economyCallType,
                (economyCallType == EconomyCallType.BUY ? (shop.getBuyPrice(true) * unitCount) : (shop.getSellPrice(true) * unitCount)));
        if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
            player.closeInventory();
            return;
        }

        final boolean isInfinite = (shop.isAdminShop() && shop.getStock() <= -1);
        if (economyCallEvent.getEconomyCallType() == EconomyCallType.SELL) {
            final int maxStock = INSTANCE.getManager().getMaxStock(shop);
            int amountToRemove = (shop.getShopItemAmount() * unitCount);
            if (!isInfinite && ((shop.getStock() + amountToRemove) > maxStock))
                amountToRemove = (amountToRemove - ((shop.getStock() + amountToRemove) - maxStock));

            INSTANCE.getManager().removeItem(player.getInventory(), shop.getShopItem(), amountToRemove);
            DataPack dataPack = INSTANCE.getManager().getDataPack(player);
            dataPack.updateCurrentTransactionLimitCounter(shop, false, dataPack.getCurrentTransactionCounter(shop, false, false) + 1);

            if (!isInfinite) shop.setStock(shop.getStock() + amountToRemove);
            //  if (shop.getSellLimit() > 0) shop.setSellCounter(shop.getSellCounter() + unitCount);

        } else {
            if (!isInfinite) shop.setStock(shop.getStock() - (shop.getShopItemAmount() * unitCount));
            // if (shop.getBuyLimit() > 0) shop.setBuyCounter(shop.getBuyCounter() + unitCount);

            DataPack dataPack = INSTANCE.getManager().getDataPack(player);
            dataPack.updateCurrentTransactionLimitCounter(shop, true, dataPack.getCurrentTransactionCounter(shop, true, false) + 1);

            if (!shop.isCommandOnlyMode() && shop.getShopItem() != null)
                INSTANCE.getManager().giveItemStacks(player, shop.getShopItem(), (shop.getShopItemAmount() * unitCount));
        }

        if (shop.canDynamicPriceChange() && INSTANCE.getConfig().getBoolean("dynamic-prices")) {
            shop.updateTransactionTimeStamp(economyCallType);
            if (economyCallType == EconomyCallType.SELL) shop.setDynamicSellCounter(shop.getDynamicSellCounter() + 1);
            else shop.setDynamicBuyCounter(shop.getDynamicBuyCounter() + 1);
        }

        INSTANCE.getInSightTask().refreshShop(shop);
        player.updateInventory();
        if (INSTANCE.getConfig().getBoolean("close-transaction-gui")) player.closeInventory();
        shop.runCommands(player, (shop.getShopItemAmount() * unitCount));

        String tradeItemName = "";
        if (!useVault) {
            final boolean forceUseCurrency = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");
            final ItemStack forceCurrencyItem = INSTANCE.getManager().buildShopCurrencyItem(1);
            final String defaultName = INSTANCE.getManager().getItemName(forceCurrencyItem);
            tradeItemName = forceUseCurrency ? defaultName : shop.getTradeItem() != null
                    ? INSTANCE.getManager().getItemName(shop.getTradeItem()) : defaultName;
        }

        final String ecoType = economyCallType.name().toLowerCase(),
                message = INSTANCE.getLangConfig().getString("shop-" + ecoType);

        if (message != null && !message.equalsIgnoreCase("")) {
            String ownerName = shop.getOwnerUniqueId() == null ? "" : (owner == null ? "" : owner.getName());
            INSTANCE.getManager().sendMessage(player, message.replace("{item}", (shop.getShopItem().hasItemMeta() && shop.getShopItem().getItemMeta() != null
                            && shop.getShopItem().getItemMeta().hasDisplayName()) ? shop.getShopItem().getItemMeta().getDisplayName()
                            : WordUtils.capitalize(shop.getShopItem().getType().name().toLowerCase().replace("_", " ")))
                    .replace("{trade-item}", tradeItemName)
                    .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                    .replace("{owner}", ownerName == null ? "" : ownerName)
                    .replace("{price}", INSTANCE.getManager().formatNumber(economyCallEvent.getPrice(), true)));
        }

        if (owner != null && owner.isOnline()) {
            final DataPack ownerDP = INSTANCE.getManager().getDataPack(owner.getPlayer());
            if (ownerDP.isTransactionNotify()) {
                final Player ownerPlayer = owner.getPlayer();
                if (ownerPlayer != null) {
                    INSTANCE.getManager().sendMessage(ownerPlayer, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-" + ecoType + "-owner"))
                            .replace("{item}", (shop.getShopItem().hasItemMeta() && shop.getShopItem().getItemMeta() != null
                                    && shop.getShopItem().getItemMeta().hasDisplayName()) ? shop.getShopItem().getItemMeta().getDisplayName() :
                                    WordUtils.capitalize(shop.getShopItem().getType().name().toLowerCase()
                                            .replace("_", " "))).replace("{trade-item}", tradeItemName)
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                            .replace("{buyer}", player.getName())
                            .replace("{price}", INSTANCE.getManager().formatNumber(economyCallEvent.getPrice(), true)));
                }
            }
        }

        INSTANCE.runEventCommands("shop-" + ecoType, player);
    }


}
