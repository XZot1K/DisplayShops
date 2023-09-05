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
import org.bukkit.configuration.ConfigurationSection;
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
import xzot1k.plugins.ds.api.eco.EcoHook;
import xzot1k.plugins.ds.api.enums.*;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.events.ShopDeletionEvent;
import xzot1k.plugins.ds.api.events.ShopEditEvent;
import xzot1k.plugins.ds.api.objects.*;

import java.lang.reflect.Method;
import java.util.*;

public class MenuListener implements Listener {

    private final DisplayShops INSTANCE;
    public ItemStack saleChangeItem, tradeChangeItem;

    public MenuListener(@NotNull DisplayShops instance) {
        this.INSTANCE = instance;
        updateChangeItem();
    }

    public void updateChangeItem() {
        final Menu edit = INSTANCE.getMenu("edit");
        if (edit != null) {
            final String saleChangeItemMaterial = edit.getConfiguration().getString("sale-item-change.material"),
                    saleChangeItemName = edit.getConfiguration().getString("sale-item-change.name");
            if (saleChangeItemName != null) {
                saleChangeItem = new CustomItem(saleChangeItemMaterial, 1)
                        .setDisplayName(null, null, saleChangeItemName)
                        .setLore(null, edit.getConfiguration().getStringList("sale-item-change.lore"))
                        .setModelData(edit.getConfiguration().getInt("sale-item-change.custom-model-data")).get();
            }

            final String tradeChangeItemMaterial = edit.getConfiguration().getString("trade-item-change.material"),
                    tradeChangeItemName = edit.getConfiguration().getString("trade-item-change.name");
            if (tradeChangeItemName != null) {
                tradeChangeItem = new CustomItem(tradeChangeItemMaterial, 1)
                        .setDisplayName(null, null, tradeChangeItemName)
                        .setLore(null, edit.getConfiguration().getStringList("trade-item-change.lore"))
                        .setModelData(edit.getConfiguration().getInt("trade-item-change.custom-model-data")).get();
            }
        }
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
        final String creationItemName = INSTANCE.getManager().color(Objects.requireNonNull(INSTANCE.getConfig().getString("shop-creation-item.display-name")));
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
        } else if (menu.getMenuName().startsWith("deposit")) {
            if (e.getSlot() >= (e.getInventory().getSize() - 9)) {if (!checkInteractiveTouches(e)) return;}
            operateDepositMenu(e, e.getClickedInventory(), menu, player, (menu.getMenuName().contains("stock") ? DepositType.STOCK : DepositType.BALANCE));
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

        if (isSaleItemSlot && inventory.getType() != InventoryType.PLAYER) {
            if ((e.getCurrentItem() != null && menu.isFillerItem(e.getCurrentItem()))) return;

            if (e.getCurrentItem() != null && saleChangeItem != null && INSTANCE.getManager().isSimilar(e.getCurrentItem(), saleChangeItem)
                    && dataPack.getInteractionType() == InteractionType.SELECT_SALE_ITEM) {
                inventory.setItem(saleItemSlot, (shop.getShopItem() != null ? shop.getShopItem().clone() : null));
                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);
            } else {
                dataPack.setInteractionType(InteractionType.SELECT_SALE_ITEM);
                dataPack.setInteractionValue(null);
                inventory.setItem(e.getSlot(), saleChangeItem);
            }

            playClickSound(player);
            return;

        } else if (isTradeItemSlot && inventory.getType() != InventoryType.PLAYER) {
            if ((e.getCurrentItem() != null && menu.isFillerItem(e.getCurrentItem()))) return;

            if (e.getCurrentItem() != null && tradeChangeItem != null && INSTANCE.getManager().isSimilar(e.getCurrentItem(), tradeChangeItem)
                    && dataPack.getInteractionType() == InteractionType.SELECT_TRADE_ITEM) {
                inventory.setItem(tradeItemSlot, shop.getCurrencyItem());
                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);
                if (menu.getConfiguration().contains("buttons.currency-type")) {
                    final EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(shop.getCurrencyType());
                    if (ecoHook != null) menu.updateButton(player, inventory, menu.getConfiguration().getInt("buttons.currency-type.slot"),
                            shop, null, ("{type}:" + (shop.getCurrencyType().equals("item-for-item") ? shop.getTradeItemName() : ecoHook.getName())));
                }
            } else {
                dataPack.setInteractionType(InteractionType.SELECT_TRADE_ITEM);
                dataPack.setInteractionValue(null);
                inventory.setItem(e.getSlot(), tradeChangeItem);
            }

            playClickSound(player);
            return;
        } else if (inventory.getType() == InventoryType.PLAYER) {
            if (e.getCurrentItem() == null || dataPack.getInteractionType() == null) return;

            ItemStack selectedItem = e.getCurrentItem();
            if (INSTANCE.getListeners().isBlockedItem(selectedItem) || INSTANCE.getBlockedItemId(selectedItem) >= 0) {
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("set-item-blocked"));
                return;
            }

            boolean forceSingleStack = INSTANCE.getConfig().getBoolean("force-single-stack");
            ItemStack selectedItemClone = e.getCurrentItem().clone();
            if (forceSingleStack) selectedItemClone.setAmount(1);

            if (dataPack.getInteractionType() == InteractionType.SELECT_SALE_ITEM) {
                if (INSTANCE.getManager().isSimilar(e.getCurrentItem(), shop.getShopItem())) {
                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    e.getView().getTopInventory().setItem(saleItemSlot, shop.getShopItem());
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sale-item-similar"));
                    return;
                }

                if (INSTANCE.getConfig().getBoolean("require-empty-stock") && shop.getStock() > 0) {
                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    e.getView().getTopInventory().setItem(saleItemSlot, shop.getShopItem());
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-empty-required"));
                    return;
                }

                final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                        INSTANCE.getConfig().getDouble("prices.sale-item-change"));
                if (economyCallEvent.failed()) return;

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

                String description = "";
                if (INSTANCE.getConfig().getBoolean("enchantment-description-set") && shop.getShopItem() != null
                        && (!shop.getShopItem().getEnchantments().isEmpty() || shop.getShopItem().getItemMeta() instanceof EnchantmentStorageMeta))
                    description = description + INSTANCE.getManager().getEnchantmentLine(shop.getShopItem());
                else if (INSTANCE.getConfig().getBoolean("potion-description-set") && shop.getShopItem() != null
                        && shop.getShopItem().getItemMeta() instanceof PotionMeta)
                    description = (description + INSTANCE.getManager().getPotionLine(shop.getShopItem()));

                shop.setDescription(description);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-item-set"));

                INSTANCE.getInSightTask().refreshShop(shop);
                INSTANCE.runEventCommands("shop-item-set", player);
            } else {
                if (INSTANCE.getManager().isSimilar(e.getCurrentItem(), shop.getTradeItem())) {
                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    e.getView().getTopInventory().setItem(tradeItemSlot, shop.getCurrencyItem());
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("trade-item-similar"));
                    return;
                }

                if (INSTANCE.getConfig().getBoolean("require-empty-balance") && shop.getStoredBalance() > 0) {
                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);
                    e.getView().getTopInventory().setItem(tradeItemSlot, shop.getCurrencyItem());
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-balance-exists"));
                    return;
                }

                final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                        INSTANCE.getConfig().getDouble("prices.trade-item-change"));
                if (economyCallEvent.failed()) return;

                dataPack.setInteractionType(null);
                dataPack.setInteractionValue(null);

                shop.setTradeItem(selectedItemClone);
                e.getView().getTopInventory().setItem(tradeItemSlot, shop.getTradeItem().clone());

                if (menu.getConfiguration().contains("buttons.currency-type")) {
                    final EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(shop.getCurrencyType());
                    if (ecoHook != null) menu.updateButton(player, e.getView().getTopInventory(), menu.getConfiguration().getInt("buttons.currency-type.slot"),
                            shop, null, ("{type}:" + (shop.getCurrencyType().equals("item-for-item") ? shop.getTradeItemName() : ecoHook.getName())));
                }

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("trade-item-set"),
                        ("{name}:" + shop.getTradeItemName()));
                INSTANCE.getInSightTask().refreshShop(shop);
                INSTANCE.runEventCommands("shop-trade-set", player);
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
                if (title != null) new AnvilGUI.Builder()
                        .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                        .onClick((slot, stateSnapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String filteredEntry = stateSnapshot.getText().substring(0, Math.min(stateSnapshot.getText().length(),
                                    INSTANCE.getConfig().getInt("description-character-limit")));
                            List<String> filterList = INSTANCE.getConfig().getStringList("description-filter");
                            for (int i = -1; ++i < filterList.size(); )
                                filteredEntry = filteredEntry.replaceAll("(?i)" + filterList.get(i), "");
                            filteredEntry = INSTANCE.getManager().color(filteredEntry.replace("'", "[q1]").replace("\"", "[q2]"));

                            if (ChatColor.stripColor(filteredEntry).equalsIgnoreCase(ChatColor.stripColor(shop.getDescription()))) {
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                                final String tooSimilar = menu.getConfiguration().getString("description-entry.too-similar");
                                return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(tooSimilar != null ? tooSimilar : ""));
                            }

                            // call edit event
                            ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.DESCRIPTION_CHANGE, filteredEntry);
                            INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                            if (shopEditEvent.isCancelled()) return Collections.singletonList(AnvilGUI.ResponseAction.close());

                            final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                                    INSTANCE.getConfig().getDouble("prices.description"));
                            if (economyCallEvent.failed()) return AnvilGUI.Response.close();

                            shop.setDescription(filteredEntry);
                            shop.updateTimeStamp();
                            INSTANCE.getInSightTask().refreshShop(shop);

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("description-set"),
                                    ("{description}:" + shop.getDescription()));

                            INSTANCE.runEventCommands("shop-description", player);
                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                        })
                        .text((shop.getDescription() != null && !shop.getDescription().isEmpty())
                                ? INSTANCE.getManager().color(shop.getDescription()) : " ")
                        .title(INSTANCE.getManager().color(title))
                        .plugin(INSTANCE).open(player);

                playClickSound(player);
                return;
            }
            case "clear-limits": {
                // call edit event
                ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.CLEAR_LIMITS);
                INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                if (shopEditEvent.isCancelled()) return;

                shop.setPlayerSellLimit(-1);
                shop.setPlayerBuyLimit(-1);
                shop.setGlobalSellCounter(0);
                shop.setGlobalBuyCounter(0);
                dataPack.updateCurrentTransactionLimitCounter(shop, true, 0);
                dataPack.updateCurrentTransactionLimitCounter(shop, false, 0);

                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("limits-cleared"),
                        ("{amount}:" + INSTANCE.getLangConfig().getString("disabled")));
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
                if (INSTANCE.getConfig().getBoolean("require-empty-stock") && shop.getStock() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-empty-required"));
                    return;
                }

                tempMenu = INSTANCE.getMenu("confirm");
                dataPack.setInteractionType(InteractionType.CLEAR_SALE_ITEM);
                dataPack.setInteractionValue(null);
                break;
            }
            case "trade-item-arrow": {
                if (menu.isFillerItem(e.getCurrentItem())) return;

                if (INSTANCE.getConfig().getBoolean("require-empty-balance") && shop.getStoredBalance() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-balance-exists"));
                    return;
                }

                tempMenu = INSTANCE.getMenu("confirm");
                dataPack.setInteractionType(InteractionType.CLEAR_TRADE_ITEM);
                dataPack.setInteractionValue(null);
                break;
            }
           /* case "balance":
            case "stock": {
                final boolean useSelector = INSTANCE.getConfig().getBoolean("use-balance-amount-selectors");
                 if (!useSelector && shop.getTradeItem() != null)
                tempMenu = INSTANCE.getMenu("deposit-balance");
                else {
                tempMenu = INSTANCE.getMenu("amount-selector");
                final InteractionType approxType = InteractionType.getApproxType(buttonName);

                dataPack.setInteractionType(approxType);
                dataPack.setInteractionValue(null);
                // }
                break;
            }final boolean useSelector = INSTANCE.getConfig().getBoolean("use-stock-amount-selectors");
                if (!useSelector)
                    tempMenu = INSTANCE.getMenu("deposit-stock");
                else {*/
            case "balance":
            case "stock":
            case "buy-price":
            case "sell-price":
            case "shop-item-amount":
            case "player-buy-limit":
            case "player-sell-limit":
            case "global-buy-limit":
            case "global-sell-limit": {
                tempMenu = INSTANCE.getMenu("amount-selector");
                final InteractionType approxType = InteractionType.getApproxType(buttonName);

                dataPack.setInteractionType(approxType);
                dataPack.setInteractionValue(null);
                break;
            }
            case "currency-type": {
                if (shop.getStoredBalance() > 0) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-balance-exists"));
                    return;
                }

                final String nextCurrency = INSTANCE.getEconomyHandler().determineNextCurrencyCycle(player, shop);

                // call edit event
                ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.CURRENCY_CHANGE, nextCurrency);
                INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                if (shopEditEvent.isCancelled()) return;

                final ItemStack itemStack = e.getCurrentItem();
                final ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    final EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(nextCurrency);
                    if (ecoHook != null) {
                        shop.setCurrencyType(nextCurrency);
                        shop.save(true);

                        final String nameFormat = menu.getConfiguration().getString("buttons.currency-type.name");
                        if (nameFormat != null) {
                            itemMeta.setDisplayName(INSTANCE.getManager().color(nameFormat
                                    .replace("{type}", (shop.getCurrencyType().equals("item-for-item") ? shop.getTradeItemName() : ecoHook.getName()))));
                            itemStack.setItemMeta(itemMeta);
                            inventory.setItem(e.getSlot(), itemStack);
                        }

                        if (!shop.getCurrencyType().equalsIgnoreCase("item-for-item")) {
                            final ItemStack fillItem = new CustomItem(menu.getConfiguration().getString("filler-material"), 0, 1)
                                    .setDisplayName(null, null, "&6")
                                    .setModelData(menu.getConfiguration().getInt("filler-model-data")).get();
                            inventory.setItem(menu.getConfiguration().getInt("trade-item-slot"), fillItem);
                            if (menu.getConfiguration().contains("buttons.trade-item-arrow"))
                                inventory.setItem(menu.getConfiguration().getInt("buttons.trade-item-arrow.slot"), fillItem);
                        } else {
                            final int tradeSlot = menu.getConfiguration().getInt("trade-item-slot");
                            if (tradeSlot >= 0 && tradeSlot < inventory.getSize())
                                inventory.setItem(tradeSlot, dataPack.getSelectedShop().getCurrencyItem().clone());

                            if (menu.getConfiguration().contains("buttons.trade-item-arrow")) {
                                ConfigurationSection buttonsSection = menu.getConfiguration().getConfigurationSection("buttons");
                                if (buttonsSection != null) menu.buildButton(buttonsSection, "trade-item-arrow", player, inventory, shop, null);
                            }
                        }

                        final int buyPriceSlot = menu.getConfiguration().getInt("buttons.buy-price.slot");
                        if (buyPriceSlot >= 0 && buyPriceSlot < inventory.getSize()) menu.updateButton(player, inventory, buyPriceSlot, shop, null);

                        final int sellPriceSlot = menu.getConfiguration().getInt("buttons.sell-price.slot");
                        if (sellPriceSlot >= 0 && sellPriceSlot < inventory.getSize()) menu.updateButton(player, inventory, sellPriceSlot, shop, null);

                        final int balanceSlot = menu.getConfiguration().getInt("buttons.balance.slot");
                        if (balanceSlot >= 0 && balanceSlot < inventory.getSize()) menu.updateButton(player, inventory, balanceSlot, shop, null);

                        INSTANCE.getInSightTask().refreshShop(shop);
                    }
                }
            }
            default: {return;}
        }

        if (tempMenu != null) player.openInventory(tempMenu.build(player));
        playClickSound(player);
    }

    private void operateTransactionMenu(InventoryClickEvent e, Inventory inventory, Menu menu, Player player) {
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack) || e.getClickedInventory() == null || e.getClickedInventory().getType() == InventoryType.PLAYER) return;

        final Shop shop = dataPack.getSelectedShop();
        if (shop != null) shop.checkCurrentEditor(player);

        final boolean editPrevention = DisplayShops.getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString()))) {
            dataPack.resetEditData();
            player.closeInventory();
            return;
        }

        boolean forceUse = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");
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
                                .replace("{sell}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), shop.getSellPrice(true)))
                                .replace("{buy}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), shop.getBuyPrice(true)))));
                    itemMeta.setLore(lore);
                    currencyItem.setItemMeta(itemMeta);
                }

                inventory.setItem(previewSlot, currencyItem);
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-trade-item"));
            } else {
                ItemStack previewItem = shop.getShopItem().clone();
                if (shop.getCurrencyType().equals("item-for-item")) {
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
        if (buttonName.startsWith("unit-increase-")) {
            final String amountString = buttonName.replace("unit-increase-", "");
            if (INSTANCE.getManager().isNotNumeric(amountString)) return;
            playClickSound(player);

            ItemStack unitCountItem = inventory.getItem(unitItemSlot);
            if (unitCountItem == null) {
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                return;
            }

            final int amount = Integer.parseInt(amountString);
            unitCountItem.setAmount(Math.min((unitCountItem.getAmount() + amount), unitCountItem.getType().getMaxStackSize()));
            updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());
            return;
        } else if (buttonName.startsWith("unit-decrease-")) {
            final String amountString = buttonName.replace("unit-decrease-", "");
            if (INSTANCE.getManager().isNotNumeric(amountString)) return;
            playClickSound(player);

            ItemStack unitCountItem = inventory.getItem(unitItemSlot);
            if (unitCountItem == null) {
                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("unit-item-invalid"));
                return;
            }

            final int amount = Integer.parseInt(amountString);
            unitCountItem.setAmount(Math.max(1, (unitCountItem.getAmount() - amount)));
            updateTransactionButtons(player, e.getClickedInventory(), shop, menu, unitCountItem.getAmount());
            return;
        }

        switch (buttonName) {
            case "buy": {
                playClickSound(player);

                if (shop.getBuyPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-buy-invalid"));
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
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, true, true) || dataPack.hasMetTransactionLimit(shop, true, false)) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
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
                    return;
                }

                int availableUnits = (shop.getStock() < 0 ? -1 : Math.max(0, (shop.getStock() / shop.getShopItemAmount())));
                if (shop.getGlobalBuyLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, true, true);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
                        return;
                    }

                    availableUnits = (int) Math.min(availableUnits, remainingLimit);

                } else if (shop.getPlayerBuyLimit() > 0) {
                    long remainingLimit = (shop.getPlayerBuyLimit() - dataPack.getCurrentTransactionCounter(shop, true, false));
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("buy-limit-exceeded"));
                        return;
                    }

                    availableUnits = (int) Math.min(availableUnits, remainingLimit);
                }

                if ((!shop.isAdminShop() && shop.getStock() <= 0) || (shop.isAdminShop() && (shop.getStock() >= 0 && shop.getStock() < shop.getShopItemAmount()))) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-low-stock"));
                    return;
                }

                double investorBalance = INSTANCE.getEconomyHandler().getBalance(player, shop),
                        tax = INSTANCE.getConfig().getDouble("transaction-tax");
                final double buyPrice = shop.getBuyPrice(true);
                final int maxBuyAll = INSTANCE.getConfig().getInt("maximum-buy-all");

                int affordableUnits = (player.hasPermission("displayshops.bypass") ? maxBuyAll
                        : Math.min(maxBuyAll, (int) (investorBalance / (buyPrice + (buyPrice * tax)))));
                availableUnits = ((availableUnits < 0 && shop.isAdminShop()) ? affordableUnits : Math.min(availableUnits, Math.min(maxBuyAll, affordableUnits)));
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

                runEconomyCall(player, shop, EconomyCallType.BUY, availableUnits);
                break;
            }
            case "sell": {
                playClickSound(player);

                if (shop.getSellPrice(true) < 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-sell-invalid"));
                    return;
                }

                if (!shop.getCommands().isEmpty()) {
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("commands-sell-fail"));
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
                    return;
                }

                final int maxStock = shop.getMaxStock();
                if ((!shop.isAdminShop() || (shop.isAdminShop() && shop.getStock() >= 0))
                        && (shop.getStock() + (shop.getShopItemAmount() * unitCount)) > maxStock) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-max-stock"))
                            .replace("{max}", INSTANCE.getManager().formatNumber(maxStock, false)));
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, false, true) || dataPack.hasMetTransactionLimit(shop, false, false)) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
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
                    return;
                }

                int sellableUnits = (INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem()) / shop.getShopItemAmount());
                if (shop.getGlobalSellLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, false, true);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
                        return;
                    }

                    sellableUnits = Math.min(sellableUnits, ((int) remainingLimit));
                } else if (shop.getGlobalSellLimit() > 0) {
                    long remainingLimit = dataPack.getCurrentTransactionCounter(shop, false, false);
                    if (remainingLimit <= 0) {
                        player.closeInventory();
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sell-limit-exceeded"));
                        return;
                    }

                    sellableUnits = Math.min(sellableUnits, ((int) remainingLimit));
                }

                if (sellableUnits < 1) {
                    INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-invalid-amount"))
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount(), false)));
                    return;
                }

                if (!shop.isAdminShop() || shop.getStock() >= 0) {
                    final long maxStock = shop.getMaxStock(), availableSpace = (maxStock - shop.getStock());
                    sellableUnits = (int) Math.min((availableSpace / shop.getShopItemAmount()), sellableUnits);
                    if (sellableUnits <= 0) {
                        INSTANCE.getManager().sendMessage(player, Objects.requireNonNull(INSTANCE.getLangConfig().getString("shop-max-stock"))
                                .replace("{max}", INSTANCE.getManager().formatNumber(maxStock, false)));
                        return;
                    }
                }

                if (shop.getOwnerUniqueId() != null) {
                    final OfflinePlayer shopOwner = INSTANCE.getServer().getOfflinePlayer(shop.getOwnerUniqueId());
                    final boolean useOwnerSyncing = INSTANCE.getConfig().getBoolean("sync-owner-balance");
                    double balance = (useOwnerSyncing ? INSTANCE.getEconomyHandler().getBalance(shopOwner, shop) : shop.getStoredBalance()),
                            costPerUnit = shop.getSellPrice(true);
                    sellableUnits = (int) Math.min(sellableUnits, (balance / costPerUnit));
                }

                if (sellableUnits <= 0) {
                    player.closeInventory();
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("transaction-all-fail"));
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.SELL, Math.min(sellableUnits, INSTANCE.getConfig().getInt("maximum-sell-all")));
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
            default: {break;}
        }
    }

    private void operateAppearanceMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        if (inventory.getType() == InventoryType.PLAYER || e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.CREATIVE || e.getCurrentItem() == null
                || e.getCurrentItem().getType().name().contains("AIR")) return;

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        if (!inventoryCheck(player, dataPack) || (e.getClickedInventory() != null
                && e.getClickedInventory().getType() == InventoryType.PLAYER)) return;

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
                    if (dataPack.hasNextPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {
                    playClickSound(player);
                    if (dataPack.hasPreviousPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "search": {
                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("search-entry.title");
                    if (title != null) new AnvilGUI.Builder().onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player, stateSnapshot.getText().trim())), 1))
                            .onClick((slot, stateSnapshot) -> {
                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim(), null);
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

        String shopId = INSTANCE.getNBT(e.getCurrentItem(), "ds-bbm"),
                typeId = INSTANCE.getNBT(e.getCurrentItem(), "ds-type");
        if (shopId == null || typeId == null || shopId.isEmpty() || typeId.isEmpty()) return;

        playClickSound(player);

        final Location baseBlockLocation = shop.getBaseLocation().asBukkitLocation();
        final Block block = baseBlockLocation.getBlock();

        final boolean hasStarPerm = player.hasPermission("displayshops.bbm.*");
        if (!hasStarPerm && !player.hasPermission("displayshops.bbm." + typeId)) {
            String message = INSTANCE.getLangConfig().getString("missing-bbm-requirement");
            if (message != null && !message.equalsIgnoreCase("")) INSTANCE.getManager().sendMessage(player, message);
            return;
        }

        final String unlockId = (typeId + ":" + e.getCurrentItem().getDurability());

        // call edit event
        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.APPEARANCE_CHANGE, unlockId);
        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
        if (shopEditEvent.isCancelled()) return;

        if (!dataPack.hasUnlockedBBM(unlockId)) {
            final double foundPrice = INSTANCE.getManager().getBBMPrice(typeId, e.getCurrentItem().getDurability());

            final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.APPEARANCE, foundPrice);
            if (economyCallEvent.failed()) {
                player.closeInventory();
                dataPack.resetEditData();
                return;
            }

            dataPack.unlockBaseBlock(unlockId);
        }

        if (shop.getStoredBaseBlockMaterial().equalsIgnoreCase(unlockId)) return;
        else shop.setStoredBaseBlockMaterial(unlockId);

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
            } catch (Exception ignored) {}
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
            INSTANCE.displayParticle((Player) e.getWhoClicked(), changeEffect.toUpperCase()
                            .replace(" ", "_").replace("-", "_"),
                    baseBlockLocation.add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, 0, 12);

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
        if (!inventoryCheck(player, dataPack) || (e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER)) return;

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
                    if (dataPack.hasNextPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {
                    playClickSound(player);
                    if (dataPack.hasPreviousPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "add": {
                    playClickSound(player);

                    final int assistantsCap = INSTANCE.getConfig().getInt("assistants-cap");
                    if (shop.getAssistants().size() >= assistantsCap) {
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-cap-exceeded"),
                                ("{cap}:" + INSTANCE.getManager().formatNumber(assistantsCap, false)));
                        return;
                    }

                    final String title = menu.getConfiguration().getString("assistant-entry.title");
                    if (title != null) new AnvilGUI.Builder().onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                            .onClick((slot, stateSnapshot) -> {
                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                final String entry = stateSnapshot.getText().trim();
                                if (INSTANCE.getManager().getUUIDPattern().matcher(entry).matches()) {
                                    final UUID uuid = UUID.fromString(entry);

                                    if (shop.getAssistants().contains(uuid)) {
                                        OfflinePlayer offlinePlayer = INSTANCE.getServer().getOfflinePlayer(uuid);
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-access"),
                                                ("{player}:" + offlinePlayer.getName()));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    }

                                    // call edit event
                                    ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.ASSISTANT_ADD, uuid.toString());
                                    INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                                    if (shopEditEvent.isCancelled()) return Collections.singletonList(AnvilGUI.ResponseAction.close());

                                    shop.getAssistants().add(uuid);
                                } else {
                                    if (entry.isEmpty()) {
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-entry"));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    }

                                    OfflinePlayer offlinePlayer = INSTANCE.getServer().getOfflinePlayer(entry);
                                    if (!offlinePlayer.hasPlayedBefore()) {
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-invalid"));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    } else {
                                        if (shop.getAssistants().contains(offlinePlayer.getUniqueId())) {
                                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-access"),
                                                    ("{player}:" + offlinePlayer.getName()));
                                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                        }

                                        // call edit event
                                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.ASSISTANT_ADD, offlinePlayer.getUniqueId().toString());
                                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                                        if (shopEditEvent.isCancelled()) return Collections.singletonList(AnvilGUI.ResponseAction.close());

                                        shop.getAssistants().add(offlinePlayer.getUniqueId());
                                    }
                                }

                                shop.save(true);
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-added"),
                                        ("{player}:" + stateSnapshot.getText().trim()));

                                menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim(), null);
                                menu.switchPage(inventory, player, dataPack.getCurrentPage());
                                return Collections.singletonList(AnvilGUI.ResponseAction.close());
                            })
                            .text(" ")
                            .title(INSTANCE.getManager().color(title))
                            .plugin(INSTANCE).open(player);
                    return;
                }

                case "remove": {
                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("assistant-entry.title");
                    if (title != null) new AnvilGUI.Builder().onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE,
                                    () -> stateSnapshot.getPlayer().openInventory(menu.build(player)), 1))
                            .onClick((slot, stateSnapshot) -> {
                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                final String entry = stateSnapshot.getText().trim();
                                if (INSTANCE.getManager().getUUIDPattern().matcher(entry).matches()) {
                                    final UUID uuid = UUID.fromString(entry);
                                    if (!shop.getAssistants().contains(uuid)) {
                                        OfflinePlayer offlinePlayer = INSTANCE.getServer().getOfflinePlayer(uuid);
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-no-access"),
                                                ("{player}:" + offlinePlayer.getName()));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    } else {
                                        // call edit event
                                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.ASSISTANT_REMOVE, uuid.toString());
                                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                                        if (shopEditEvent.isCancelled()) return Collections.singletonList(AnvilGUI.ResponseAction.close());

                                        shop.getAssistants().remove(uuid);
                                    }
                                } else {
                                    if (entry.isEmpty()) {
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-entry"));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    }

                                    OfflinePlayer offlinePlayer = INSTANCE.getServer().getOfflinePlayer(entry);
                                    if (!offlinePlayer.hasPlayedBefore()) {
                                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-invalid"));
                                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                    } else {
                                        if (!shop.getAssistants().contains(offlinePlayer.getUniqueId())) {
                                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-no-access"),
                                                    ("{player}:" + stateSnapshot.getText().trim()));
                                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                        } else {
                                            // call edit event
                                            ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.ASSISTANT_REMOVE, offlinePlayer.getUniqueId().toString());
                                            INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                                            if (shopEditEvent.isCancelled()) return Collections.singletonList(AnvilGUI.ResponseAction.close());

                                            shop.getAssistants().remove(offlinePlayer.getUniqueId());
                                        }
                                    }
                                }

                                shop.save(true);
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-removed"),
                                        ("{player}:" + stateSnapshot.getText().trim()));

                                menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim(), null);
                                menu.switchPage(inventory, player, dataPack.getCurrentPage());
                                return Collections.singletonList(AnvilGUI.ResponseAction.close());
                            })
                            .text(" ")
                            .title(INSTANCE.getManager().color(title))
                            .plugin(INSTANCE).open(player);

                    return;
                }

                case "search": {
                    playClickSound(player);
                    final String title = menu.getConfiguration().getString("search-entry.title");
                    if (title != null) new AnvilGUI.Builder().onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                    stateSnapshot.getPlayer().openInventory(menu.build(player, stateSnapshot.getText().trim())), 1))
                            .onClick((slot, stateSnapshot) -> {
                                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim(), null);
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

        final String uuidString = INSTANCE.getNBT(e.getCurrentItem(), "uuid");
        if (uuidString == null || uuidString.isEmpty()) return;

        final UUID uuid = UUID.fromString(uuidString);
        playClickSound(player);

        final int assistantsCap = INSTANCE.getConfig().getInt("assistants-cap");
        if (shop.getAssistants().size() >= assistantsCap) {
            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("assistants-cap-exceeded"),
                    ("{cap}:" + INSTANCE.getManager().formatNumber(assistantsCap, false)));
            return;
        }


        final boolean isActive = shop.getAssistants().contains(uuid);
        // call edit event
        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, (!isActive ? EditType.ASSISTANT_ADD : EditType.ASSISTANT_REMOVE), uuid.toString());
        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
        if (shopEditEvent.isCancelled()) return;

        final String activeColor = menu.getConfiguration().getString("active-color"),
                inactiveColor = menu.getConfiguration().getString("inactive-color");
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

        if (e.getCurrentItem() == null || (e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER)) return;
        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);

        final String buttonName = menu.getButtonName(e.getSlot());
        if (buttonName != null && !buttonName.isEmpty()) {
            switch (buttonName) {

                case "next": {
                    playClickSound(player);
                    if (dataPack.hasNextPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() + 1));
                    return;
                }

                case "previous": {
                    playClickSound(player);
                    if (dataPack.hasPreviousPage()) menu.switchPage(inventory, player, (dataPack.getCurrentPage() - 1));
                    return;
                }

                case "type": {
                    playClickSound(player);

                    final String typeButtonName = menu.getConfiguration().getString("buttons.type.name");
                    if (typeButtonName == null) return;

                    final ItemStack itemStack = e.getCurrentItem().clone();
                    final ItemMeta itemMeta = e.getCurrentItem().getItemMeta();

                    final ShopActionType currentType = ShopActionType.getTypeFromItem(e.getCurrentItem(), menu);
                    if (currentType != null && itemMeta != null) {
                        final ShopActionType nextType = currentType.getNext();

                        itemMeta.setDisplayName(INSTANCE.getManager().color(typeButtonName.replace("{type}", nextType.getName(menu))));
                        itemStack.setItemMeta(itemMeta);
                        e.getClickedInventory().setItem(e.getSlot(), itemStack);

                        String currentSearchText = null;
                        final ItemStack searchItem = inventory.getItem(menu.getConfiguration().getInt("buttons.search.slot"));
                        if (searchItem != null) currentSearchText = INSTANCE.getNBT(searchItem, "ds-search");

                        menu.loadPages(player, dataPack, null, currentSearchText, e.getCurrentItem());
                        menu.switchPage(inventory, player, 1);
                    }
                    return;
                }

                case "search": {
                    playClickSound(player);

                    final String title = menu.getConfiguration().getString("search-entry.title");
                    if (title != null && !title.isEmpty()) {
                        new AnvilGUI.Builder()
                                .onClose(stateSnapshot -> INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () ->
                                        stateSnapshot.getPlayer().openInventory(inventory), 1))
                                .onClick((slot, stateSnapshot) -> {
                                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                                    final ItemStack typeItem = inventory.getItem(menu.getConfiguration().getInt("buttons.type.slot"));
                                    menu.loadPages(player, dataPack, null, stateSnapshot.getText().trim(), typeItem);
                                    menu.switchPage(inventory, player, dataPack.getCurrentPage());

                                    final int searchSlot = menu.getConfiguration().getInt("buttons.search.slot");
                                    menu.updateButton(player, inventory, searchSlot, null, null, ("{search-text}:" + stateSnapshot.getText().trim()));

                                    ItemStack searchItem = inventory.getItem(searchSlot);
                                    if (searchItem != null) INSTANCE.updateNBT(searchItem, "ds-search", stateSnapshot.getText().trim());

                                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                })
                                .text("")
                                .title(INSTANCE.getManager().color(title))
                                .plugin(INSTANCE).open(player);
                    }
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

        String shopId = INSTANCE.getNBT(e.getCurrentItem(), "shop-id");
        if (shopId == null || shopId.isEmpty()) return;

        final Shop selectedShop = INSTANCE.getManager().getShopMap().get(UUID.fromString(shopId));
        if (selectedShop == null) return;

        playClickSound(player);

        player.closeInventory();
        selectedShop.visit(player, true);
    }

    private void operateAmountSelectorMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player) {
        if (e.getCurrentItem() == null || (e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER)) {
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
                        maxAmount = (shop.getStock() + INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem()));
                        break;
                    }

                    case AMOUNT_BALANCE: {
                        maxAmount = (shop.getStoredBalance() + INSTANCE.getEconomyHandler().getBalance(player, shop));
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

                    default: {break;}
                }

                amountItem.setAmount((int) Math.max(1, Math.min(maxAmount, amountItem.getMaxStackSize())));
                finalAmount = maxAmount;
            } else {
                if (INSTANCE.getManager().isNotNumeric(amountString)) return;

                double amount = Double.parseDouble(amountString),
                        foundAmount = Double.parseDouble(INSTANCE.getNBT(amountItem, "ds-amount"));

                switch (dataPack.getInteractionType()) {
                    case AMOUNT_STOCK:
                    case AMOUNT_BALANCE:
                    case AMOUNT_GLOBAL_BUY_LIMIT:
                    case AMOUNT_GLOBAL_SELL_LIMIT:
                    case AMOUNT_PLAYER_BUY_LIMIT:
                    case AMOUNT_PLAYER_SELL_LIMIT:
                    case AMOUNT_BUY_PRICE:
                    case AMOUNT_SELL_PRICE: {
                        if (foundAmount <= -1) foundAmount = 0;
                        break;
                    }

                    case SHOP_ITEM_AMOUNT: {
                        if (foundAmount <= 0) foundAmount = 1;
                        break;
                    }

                    default: {break;}
                }

                finalAmount = (foundAmount + amount);
                amountItem.setAmount((int) Math.max(Math.min(amountItem.getType().getMaxStackSize(), finalAmount), 1));
            }

            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, finalAmount);
            return;
        } else if (buttonName.startsWith("remove-")) {
            final String amountString = buttonName.replace("remove-", "");
            double finalAmount;

            if (amountString.equals("all")) {
                amountItem.setAmount(1);
                switch (dataPack.getInteractionType()) {
                    case AMOUNT_GLOBAL_BUY_LIMIT:
                    case AMOUNT_GLOBAL_SELL_LIMIT:
                    case AMOUNT_PLAYER_BUY_LIMIT:
                    case AMOUNT_PLAYER_SELL_LIMIT:
                    case AMOUNT_BUY_PRICE:
                    case AMOUNT_SELL_PRICE: {
                        finalAmount = -1;
                        break;
                    }

                    case SHOP_ITEM_AMOUNT: {
                        finalAmount = 1;
                        break;
                    }

                    default: {
                        finalAmount = 0;
                        break;
                    }
                }

                amountItem.setAmount((int) Math.max(Math.min(amountItem.getType().getMaxStackSize(), finalAmount), 1));
                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, finalAmount);
                return;
            }

            if (INSTANCE.getManager().isNotNumeric(amountString)) return;

            double amount = Double.parseDouble(amountString),
                    foundAmount = Double.parseDouble(INSTANCE.getNBT(amountItem, "ds-amount")),
                    newAmount = (foundAmount - amount);
            switch (dataPack.getInteractionType()) {
                case AMOUNT_GLOBAL_BUY_LIMIT:
                case AMOUNT_GLOBAL_SELL_LIMIT:
                case AMOUNT_PLAYER_BUY_LIMIT:
                case AMOUNT_PLAYER_SELL_LIMIT:
                case AMOUNT_BUY_PRICE:
                case AMOUNT_SELL_PRICE: {
                    finalAmount = Math.max(-1, newAmount);
                    break;
                }

                case SHOP_ITEM_AMOUNT: {
                    finalAmount = Math.max(1, newAmount);
                    break;
                }

                default: {
                    finalAmount = Math.max(0, newAmount);
                    break;
                }
            }

            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, finalAmount);
            return;
        }

        switch (buttonName) {
            case "custom-amount": {
                final String title = menu.getConfiguration().getString("custom-amount-entry.title");
                if (title != null) new AnvilGUI.Builder().onClose(stateSnapshot ->
                                INSTANCE.getServer().getScheduler().runTaskLater(INSTANCE, () -> stateSnapshot.getPlayer().openInventory(inventory), 1))
                        .onClick((slot, stateSnapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                            String text = stateSnapshot.getText().replace(" ", "").replace(",", ".");

                            if (text.startsWith("-") && !dataPack.getInteractionType().name().contains("PRICE") && !dataPack.getInteractionType().name().contains("LIMIT")) {
                                final String negativeText = menu.getConfiguration().getString("custom-amount-entry.negative");
                                return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(negativeText != null ? negativeText : ""));
                            }

                            if (INSTANCE.getManager().isNotNumeric(text)) {
                                final String invalidText = menu.getConfiguration().getString("custom-amount-entry.invalid");
                                return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(invalidText != null ? invalidText : ""));
                            }

                            dataPack.setInteractionValue(Math.max((dataPack.getInteractionType() == InteractionType.AMOUNT_STOCK
                                    && !player.hasPermission("displayshops.admin") ? 0 : -1), Double.parseDouble(text)));
                            amountItem.setAmount(Math.max(1, Math.min(((Double) dataPack.getInteractionValue()).intValue(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, ((Double) dataPack.getInteractionValue()).intValue());
                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                        }).text(" ")
                        .title(INSTANCE.getManager().color(title))
                        .plugin(INSTANCE).open(player);
                playClickSound(player);
                break;
            }

            case "confirm": {
                final double amount = Double.parseDouble(INSTANCE.getNBT(amountItem, "ds-amount"));
                Menu menuToOpen = null;
                switch (dataPack.getInteractionType()) {
                    case AMOUNT_BUY_PRICE: {
                        if (amount == shop.getBuyPrice(false)) {
                            amountItem.setAmount((int) Math.max(1, Math.min(shop.getBuyPrice(false), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getBuyPrice(false));
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.BUY_PRICE, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        shop.setBuyPrice(amount);

                        final String message = INSTANCE.getLangConfig().getString((amount <= -1) ? "buying-disabled" : "buy-price-set");
                        INSTANCE.getManager().sendMessage(player, message,
                                ("{price}:" + INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), amount)));

                        INSTANCE.runEventCommands("shop-buy-price", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_SELL_PRICE: {
                        if (amount == shop.getSellPrice(false)) {
                            amountItem.setAmount((int) Math.max(1, Math.min(shop.getSellPrice(false), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getSellPrice(false));
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.SELL_PRICE, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        shop.setSellPrice(amount);

                        final String message = INSTANCE.getLangConfig().getString((amount <= -1) ? "selling-disabled" : "sell-price-set");
                        INSTANCE.getManager().sendMessage(player, message, ("{price}:" + INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), amount)));

                        INSTANCE.runEventCommands("shop-sell-price", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case SHOP_ITEM_AMOUNT: {
                        if (amount > INSTANCE.getConfig().getInt("max-item-stack-size") || amount < 1) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getShopItemAmount(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getShopItemAmount());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-stack-size"));
                            break;
                        }

                        if (amount == shop.getShopItemAmount()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getShopItemAmount(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getShopItemAmount());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.SHOP_ITEM_AMOUNT, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        shop.setShopItemAmount((int) amount);

                        final String message = INSTANCE.getLangConfig().getString("stack-size-set");
                        INSTANCE.getManager().sendMessage(player, message, ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("shop-amount", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_BALANCE: {
                        if (amount == shop.getStoredBalance()) {
                            amountItem.setAmount((int) Math.max(1, Math.min(shop.getStoredBalance(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStoredBalance());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        final boolean isRemoval = (amount < shop.getStoredBalance());
                        final double difference = (Math.max(amount, shop.getStoredBalance()) - Math.min(amount, shop.getStoredBalance()));

                        EconomyCallEvent economyCallEvent;
                        if (isRemoval) {
                            if (difference <= 0 || (shop.getStoredBalance() - difference) > shop.getStoredBalance()) {
                                amountItem.setAmount((int) Math.max(1, Math.min(shop.getStoredBalance(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStoredBalance());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("balance-withdraw-fail"),
                                        ("{balance}:" + INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), shop.getStoredBalance())));
                                return;
                            }
                        } else {
                            if ((shop.getStoredBalance() + difference) >= INSTANCE.getConfig().getLong("max-stored-currency")) {
                                amountItem.setAmount((int) Math.max(1, Math.min(shop.getStoredBalance(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStoredBalance());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("max-stored-currency"),
                                        ("{amount}:" + INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), difference)));
                                return;
                            }
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, (isRemoval ? EditType.WITHDRAW_BALANCE
                                : EditType.DEPOSIT_BALANCE), difference);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        economyCallEvent = EconomyCallEvent.call(player, shop, (isRemoval ? EconomyCallType.WITHDRAW_BALANCE
                                : EconomyCallType.DEPOSIT_BALANCE), difference);
                        if (economyCallEvent.failed()) {
                            amountItem.setAmount((int) Math.max(1, Math.min(shop.getStoredBalance(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStoredBalance());
                            return;
                        }

                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("balance-" + (isRemoval ? "withdrawn" : "deposited")),
                                ("{amount}:" + INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), economyCallEvent.getAmount())));
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_STOCK: {
                        if (shop.isAdminShop() && shop.getStock() == -1) {
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-infinite-stock"));
                            return;
                        }

                        if (amount == shop.getStock()) {
                            amountItem.setAmount((int) Math.max(1, Math.min(shop.getStoredBalance(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStoredBalance());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        final boolean isRemoval = (amount < shop.getStock());
                        final double difference = (Math.max(amount, shop.getStock()) - Math.min(amount, shop.getStock()));
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, (isRemoval ? EditType.WITHDRAW_STOCK : EditType.DEPOSIT_STOCK));

                        if (isRemoval) {
                            final int availableSpace = Math.min(INSTANCE.getManager().getInventorySpaceForItem(player, shop.getShopItem()),
                                    (36 * shop.getShopItem().getMaxStackSize()));
                            final int newAmount = (int) Math.min(difference, availableSpace);

                            if (difference <= 0) {
                                amountItem.setAmount(Math.max(1, Math.min(shop.getStock(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStock());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("stock-withdraw-fail"),
                                        ("{amount}:" + INSTANCE.getManager().formatNumber(newAmount, false)));
                                return;
                            }

                            if (availableSpace <= 0) {
                                amountItem.setAmount(Math.max(1, Math.min(shop.getStock(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStock());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("insufficient-space"),
                                        ("{space}:" + INSTANCE.getManager().formatNumber(availableSpace, false)));
                                return;
                            }

                            // call edit event
                            shopEditEvent.setAmount(newAmount);
                            INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                            if (shopEditEvent.isCancelled()) return;

                            shop.setStock(shop.getStock() - newAmount);
                            final ItemStack itemStack = shop.getShopItem().clone();
                            INSTANCE.getServer().getScheduler().runTask(INSTANCE, () ->
                                    INSTANCE.getManager().giveItemStacks(player, itemStack, (int) (double) newAmount));

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("withdrawn-stock"),
                                    ("{amount}:" + INSTANCE.getManager().formatNumber(newAmount, false)));
                            INSTANCE.runEventCommands("shop-withdraw", player);
                        } else {
                            final int maxStock = shop.getMaxStock();
                            int totalItemCount = INSTANCE.getManager().getItemAmount(player.getInventory(), shop.getShopItem());
                            if (totalItemCount <= 0 || totalItemCount < difference) {
                                amountItem.setAmount(Math.max(1, Math.min(shop.getStock(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStock());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("insufficient-items"));
                                return;
                            }

                            if (difference <= 0 || (shop.getStock() + difference) >= maxStock) {
                                amountItem.setAmount(Math.max(1, Math.min(shop.getStock(), amountItem.getMaxStackSize())));
                                updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getStock());
                                INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("stock-deposit-fail"),
                                        ("{amount}:" + INSTANCE.getManager().formatNumber(difference, false)));
                                return;
                            }

                            // call edit event
                            shopEditEvent.setAmount(difference);
                            INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                            if (shopEditEvent.isCancelled()) return;

                            INSTANCE.getManager().removeItem(player.getInventory(), shop.getShopItem(), (int) difference);
                            shop.setStock((int) (shop.getStock() + difference));

                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("deposited-stock"),
                                    ("{amount}:" + INSTANCE.getManager().formatNumber(difference, false)));
                            INSTANCE.runEventCommands("shop-deposit", player);
                        }

                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_PLAYER_BUY_LIMIT: {
                        if (amount > ((float) (shop.getMaxStock()) / Math.max(1, shop.getShopItemAmount()))) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerBuyLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getPlayerBuyLimit()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerBuyLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.PLAYER_BUY_LIMIT, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                                INSTANCE.getConfig().getDouble("prices.player-buy-limit"));
                        if (economyCallEvent.failed()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerBuyLimit());
                            return;
                        }

                        shop.setPlayerBuyLimit((int) amount);
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-buy-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("player-buy-limit", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_PLAYER_SELL_LIMIT: {
                        if (amount > ((float) shop.getMaxStock() / Math.max(1, shop.getShopItemAmount()))) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerSellLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getPlayerSellLimit()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerSellLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.PLAYER_SELL_LIMIT, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                                INSTANCE.getConfig().getDouble("prices.player-sell-limit"));
                        if (economyCallEvent.failed()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getPlayerSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getPlayerSellLimit());
                            return;
                        }

                        shop.setPlayerSellLimit((int) amount);
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("player-sell-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("player-sell-limit", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_GLOBAL_BUY_LIMIT: {
                        if (amount > ((float) shop.getMaxStock() / Math.max(1, shop.getShopItemAmount()))) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalBuyLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getGlobalBuyLimit()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalBuyLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.GLOBAL_BUY_LIMIT, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                                INSTANCE.getConfig().getDouble("prices.global-buy-limit"));
                        if (economyCallEvent.failed()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalBuyLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalBuyLimit());
                            return;
                        }

                        shop.setGlobalBuyLimit((int) amount);
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("global-buy-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("global-buy-limit", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    case AMOUNT_GLOBAL_SELL_LIMIT: {
                        if (amount > ((float) shop.getMaxStock()) / Math.max(1, shop.getShopItemAmount())) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalSellLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("invalid-limit"));
                            return;
                        }

                        if (amount == shop.getGlobalSellLimit()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalSellLimit());
                            INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("shop-edit-too-similar"));
                            return;
                        }

                        // call edit event
                        ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.GLOBAL_SELL_LIMIT, amount);
                        INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                        if (shopEditEvent.isCancelled()) return;

                        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                                INSTANCE.getConfig().getDouble("prices.global-sell-limit"));
                        if (economyCallEvent.failed()) {
                            amountItem.setAmount(Math.max(1, Math.min(shop.getGlobalSellLimit(), amountItem.getMaxStackSize())));
                            updateItemAmount(inventory, menu, player, dataPack, amountSlot, amountItem, shop.getGlobalSellLimit());
                            return;
                        }

                        shop.setGlobalSellLimit((int) amount);
                        INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("global-sell-limit-set"),
                                ("{amount}:" + INSTANCE.getManager().formatNumber(amount, false)));

                        INSTANCE.runEventCommands("global-sell-limit", player);
                        menuToOpen = INSTANCE.getMenu("edit");
                        break;
                    }
                    default: {break;}
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

    private void operateDepositMenu(@NotNull InventoryClickEvent e, @NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player, @NotNull DepositType depositType) {
        if (e.getSlot() >= (inventory.getSize() - 9)) {
            e.setCancelled(true);

            final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
            if (!inventoryCheck(player, dataPack)) return;

            final Shop shop = dataPack.getSelectedShop();
            if (shop == null || shop.getShopItem() == null) {
                dataPack.resetEditData();
                player.closeInventory();
                return;
            }

            final String buttonName = menu.getButtonName(e.getSlot());
            if (buttonName == null || buttonName.isEmpty()) return;

            if (buttonName.equals("close")) {
                playClickSound(player);
                handleVanillaLikeDeposit(player, inventory, shop, depositType);
            }
        }
    }

    private void handleVanillaLikeDeposit(@NotNull Player player, @NotNull Inventory inventory, @NotNull Shop shop, @NotNull DepositType depositType) {
        if (depositType == DepositType.STOCK) {
            for (int i = -1; ++i < (inventory.getContents().length - 9); ) {
                final ItemStack itemStack = inventory.getContents()[i];
                if (itemStack == null || itemStack.getType().name().contains("AIR")) continue;

                if (!INSTANCE.getManager().isSimilar(itemStack, shop.getShopItem())) {
                    INSTANCE.getManager().giveItemStacks(player, itemStack, itemStack.getAmount());
                    inventory.setItem(i, null);
                    continue;
                }

                final int newStockTotal = (shop.getStock() + itemStack.getAmount());
                if (newStockTotal > shop.getMaxStock()) {
                    final int difference = (shop.getMaxStock() - newStockTotal);
                    INSTANCE.getManager().giveItemStacks(player, itemStack, difference);
                    inventory.setItem(i, null);
                    continue;
                }

                shop.setStock(shop.getStock() + itemStack.getAmount());
                inventory.setItem(i, null);
            }

            shop.save(true);
            player.closeInventory();

        } else if (depositType == DepositType.BALANCE) {

            final long maxStoredBalance = INSTANCE.getConfig().getLong("max-stored-currency");

            for (int i = -1; ++i < (inventory.getContents().length - 9); ) {
                final ItemStack itemStack = inventory.getContents()[i];
                if (itemStack == null || itemStack.getType().name().contains("AIR") || shop.getTradeItem() == null) continue;

                if (!INSTANCE.getManager().isSimilar(itemStack, shop.getTradeItem())) {
                    INSTANCE.getManager().giveItemStacks(player, itemStack, itemStack.getAmount());
                    inventory.setItem(i, null);
                    continue;
                }

                final long newBalanceTotal = (long) (shop.getStoredBalance() + itemStack.getAmount());
                if (newBalanceTotal > maxStoredBalance) {
                    final long difference = (long) (shop.getStoredBalance() - newBalanceTotal);
                    INSTANCE.getManager().giveItemStacks(player, itemStack, (int) difference);
                    inventory.setItem(i, null);
                    continue;
                }

                shop.setStoredBalance(shop.getStoredBalance() + itemStack.getAmount());
                inventory.setItem(i, null);
            }

            shop.save(true);
            player.closeInventory();
        }
    }

    private void updateItemAmount(@NotNull Inventory inventory, @NotNull Menu menu, @NotNull Player player, DataPack dataPack, int amountSlot,
                                  ItemStack amountItem, double finalAmount) {
        final ItemMeta itemMeta = amountItem.getItemMeta();
        if (itemMeta != null) {
            String name = menu.getConfiguration().getString("buttons.amount.name");
            if (name != null) {
                String disabled = INSTANCE.getLangConfig().getString("disabled");
                if (disabled == null) disabled = "";

                final boolean isLimit = dataPack.getInteractionType().name().contains("LIMIT");
                final boolean isDecimal = (dataPack.getInteractionType().name().contains("PRICE") || dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE);
                final String newName = name.replace("{amount}", (!isLimit ? (isDecimal ? INSTANCE.getEconomyHandler().format(dataPack.getSelectedShop(),
                        dataPack.getSelectedShop().getCurrencyType(), finalAmount) : INSTANCE.getManager().formatNumber(finalAmount, false))
                        : ((finalAmount < 0) ? disabled : INSTANCE.getManager().formatNumber(finalAmount, false))));
                itemMeta.setDisplayName(INSTANCE.getManager().color(newName));
                amountItem.setItemMeta(itemMeta);
            }
        }

        amountItem.setAmount((int) Math.min(amountItem.getMaxStackSize(), Math.max(1, finalAmount)));
        inventory.setItem(amountSlot, INSTANCE.updateNBT(amountItem, "ds-amount", String.valueOf(finalAmount)));
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
        if (shop == null || (editPrevention && shop.getCurrentEditor() != null
                && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) || !shop.canEdit(player)) {
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

                    final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, EconomyCallType.EDIT_ACTION,
                            INSTANCE.getConfig().getDouble("prices.delete-shop"));
                    if (economyCallEvent.failed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    ShopDeletionEvent shopDeletionEvent = new ShopDeletionEvent(player, shop.getBaseLocation().asBukkitLocation());
                    if (shopDeletionEvent.isCancelled()) return;

                    // call edit event
                    ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.DELETE);
                    INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    shop.purge(player, true);

                    dataPack.resetEditData();
                    player.closeInventory();
                } else if (dataPack.getInteractionType() == InteractionType.CLEAR_SALE_ITEM) {
                    // call edit event
                    ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.CLEAR_SALE_ITEM, INSTANCE.toString(shop.getShopItem()));
                    INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);

                    shop.dropStock();
                    shop.setShopItem(null);
                    shop.setShopItemAmount(1);
                    shop.save(true);

                    INSTANCE.getInSightTask().refreshShop(shop);
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("sale-item-cleared"));
                    player.openInventory(INSTANCE.getMenu("edit").build(player));
                } else if (dataPack.getInteractionType() == InteractionType.CLEAR_TRADE_ITEM) {
                    // call edit event
                    ShopEditEvent shopEditEvent = new ShopEditEvent(player, shop, EditType.CLEAR_TRADE_ITEM, INSTANCE.toString(shop.getTradeItem()));
                    INSTANCE.getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    dataPack.setInteractionType(null);
                    dataPack.setInteractionValue(null);

                    shop.returnBalance();
                    shop.setTradeItem(null);
                    shop.save(true);

                    INSTANCE.getInSightTask().refreshShop(shop);
                    INSTANCE.getManager().sendMessage(player, INSTANCE.getLangConfig().getString("trade-item-cleared"));
                    player.openInventory(INSTANCE.getMenu("edit").build(player));
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

            default: {return;}
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
        if (dataPack.getSelectedShop() != null) return true;
        player.closeInventory();

        final String message = INSTANCE.getLangConfig().getString("shop-edit-invalid");
        if (message != null && !message.equalsIgnoreCase("")) INSTANCE.getManager().sendMessage(player, message);
        return false;
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

    private void runEconomyCall(@NotNull Player player, @NotNull Shop shop, @NotNull EconomyCallType economyCallType, int unitCount) {
        OfflinePlayer owner = null;
        if (shop.getOwnerUniqueId() != null)
            owner = INSTANCE.getServer().getOfflinePlayer(shop.getOwnerUniqueId());

        final boolean syncOwner = INSTANCE.getConfig().getBoolean("sync-owner-balance");
        final double totalShopBalance = (shop.getStoredBalance() + (unitCount * shop.getSellPrice(true)));

        if (economyCallType == EconomyCallType.SELL && !syncOwner && totalShopBalance >= INSTANCE.getConfig().getLong("max-stored-currency")) {
            final String message = INSTANCE.getLangConfig().getString("max-stored-currency");
            if (message != null && !message.equalsIgnoreCase(""))
                INSTANCE.getManager().sendMessage(player, message);
            return;
        }

        // boolean canBypassEconomy = player.hasPermission("displayshops.bypass");
        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, shop, economyCallType, (economyCallType == EconomyCallType.BUY
                ? (shop.getBuyPrice(true) * unitCount) : (shop.getSellPrice(true) * unitCount)));
        if (economyCallEvent.failed()) {
            player.closeInventory();
            INSTANCE.getManager().getDataPack(player).resetEditData();
            return;
        }

        final boolean isInfinite = (shop.isAdminShop() && shop.getStock() <= -1);
        if (economyCallEvent.getEconomyCallType() == EconomyCallType.SELL) {
            final int maxStock = shop.getMaxStock();
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
        if (INSTANCE.getConfig().getBoolean("close-transaction-gui")) player.closeInventory();
        shop.runCommands(player, (shop.getShopItemAmount() * unitCount));

        final String ecoType = economyCallType.name().toLowerCase(),
                message = INSTANCE.getLangConfig().getString("shop-" + ecoType);

        if (message != null && !message.equalsIgnoreCase("")) {
            String ownerName = shop.getOwnerUniqueId() == null ? "" : (owner == null ? "" : owner.getName());
            INSTANCE.getManager().sendMessage(player, message.replace("{item}", (shop.getShopItem().hasItemMeta() && shop.getShopItem().getItemMeta() != null
                            && shop.getShopItem().getItemMeta().hasDisplayName()) ? shop.getShopItem().getItemMeta().getDisplayName()
                            : WordUtils.capitalize(shop.getShopItem().getType().name().toLowerCase().replace("_", " ")))
                    .replace("{trade-item}", shop.getTradeItemName())
                    .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                    .replace("{owner}", ownerName == null ? "" : ownerName)
                    .replace("{price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), economyCallEvent.getAmount())));
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
                                            .replace("_", " "))).replace("{trade-item}", shop.getTradeItemName())
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                            .replace("{buyer}", player.getName())
                            .replace("{price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), economyCallEvent.getAmount())));
                }
            }
        }

        INSTANCE.runEventCommands("shop-" + ecoType, player);
    }

    private enum DepositType {STOCK, BALANCE}

}
