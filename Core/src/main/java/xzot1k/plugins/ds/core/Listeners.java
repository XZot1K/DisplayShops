/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core;

import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.*;
import xzot1k.plugins.ds.api.events.*;
import xzot1k.plugins.ds.api.objects.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Listeners implements Listener {

    private DisplayShops pluginInstance;
    public ItemStack creationItem;

    public Listeners(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
        creationItem = getPluginInstance().getManager().buildShopCreationItem(null, 1);
    }

    private boolean isCreationItem(BlockPlaceEvent e) {
        return (e.getItemInHand().isSimilar(creationItem));
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        handlePickup(e, e.getItem(), e.getPlayer());
    }

    private void handlePickup(Cancellable e, Item item, Entity entity) {
        if (item.hasMetadata("shop-owner")) {
            for (MetadataValue value : item.getMetadata("shop-owner"))
                if (value.asString().equals(entity.getUniqueId().toString())) {
                    e.setCancelled(true);
                    break;
                }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlaceCheck(BlockPlaceEvent e) {
        if (!isCreationItem(e)) return;

        if (!e.getPlayer().hasPermission("displayshops.create")) {
            e.setCancelled(true);
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            return;
        }

        Block blockRelativeOne = e.getBlock().getRelative(BlockFace.UP), blockRelativeTwo = blockRelativeOne.getRelative(BlockFace.UP);
        if (!blockRelativeOne.getType().name().contains("AIR") || !blockRelativeTwo.getType().name().contains("AIR")) {
            e.setCancelled(true);
            String message = getPluginInstance().getLangConfig().getString("relative-unsafe-world");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            return;
        }

        if (getPluginInstance().getManager().isBlockedWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
            String message = getPluginInstance().getLangConfig().getString("blocked-world");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            return;
        }

        if (getPluginInstance().isTownyInstalled()) {
            final boolean shopPlotOnly = getPluginInstance().getConfig().getBoolean("towny-shop-plots-only");
            if (shopPlotOnly && (!com.palmergames.bukkit.towny.utils.ShopPlotUtil.isShopPlot(e.getBlock().getLocation())
                    || !com.palmergames.bukkit.towny.utils.ShopPlotUtil.doesPlayerHaveAbilityToEditShopPlot(e.getPlayer(), e.getBlock().getLocation()))) {
                e.setCancelled(true);
                String message = this.getPluginInstance().getLangConfig().getString("towny-no-access");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                return;
            }
        }

        MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(e.getBlock().getLocation());
        shopCreationWorks(e, (marketRegion != null));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLava(PlayerBucketEmptyEvent e) {
        if (e.getBucket().name().contains("LAVA")) {
            final Location loc = e.getBlockClicked().getLocation();
            for (Shop shop : getPluginInstance().getManager().getShopMap().values()) {
                if (shop.getBaseLocation().getY() <= e.getBlockClicked().getY() && shop.getBaseLocation().distance(loc, true) < 2) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null
                && e.getItem().getType().name().contains("SIGN"))) return;

        final boolean isOffhandVersion = (Math.floor(getPluginInstance().getServerVersion()) >= 1_9);
        if (isOffhandVersion && Math.floor(getPluginInstance().getServerVersion()) >= 1_12 && e.getHand() != EquipmentSlot.HAND)
            return;

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(e.getPlayer());
        if (dataPack == null) return;
        final ItemStack handItem = (isOffhandVersion ? e.getPlayer().getInventory().getItemInMainHand() : e.getPlayer().getItemInHand());

        if (dataPack.isInSelectionMode()) {
            if (isOffhandVersion && getPluginInstance().getServerVersion() > 1_12 && e.getHand() == EquipmentSlot.OFF_HAND) {
                e.setCancelled(true);
                return;
            }

            if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                if (dataPack.getSelectedRegion() != null)
                    dataPack.getSelectedRegion().setPointOne(new LClone(e.getClickedBlock().getLocation()));
                else {
                    DRegion regionSelection = new DRegion();
                    regionSelection.setPointOne(new LClone(e.getClickedBlock().getLocation()));
                    dataPack.setSelectedRegion(regionSelection);
                }

                String message = getPluginInstance().getLangConfig().getString("selection-one-set");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                return;
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);

                if (dataPack.getSelectedRegion() != null)
                    dataPack.getSelectedRegion().setPointTwo(new LClone(e.getClickedBlock().getLocation()));
                else {
                    Region regionSelection = new DRegion();
                    regionSelection.setPointTwo(new LClone(e.getClickedBlock().getLocation()));
                    dataPack.setSelectedRegion(regionSelection);
                }

                String message = getPluginInstance().getLangConfig().getString("selection-two-set");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                return;
            }
        }

        final Shop shop = getPluginInstance().getManager().getShop(e.getClickedBlock().getLocation());
        if (shop == null) return;

        e.setCancelled(true);

        final MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(e.getClickedBlock().getLocation());
        if (marketRegion != null && marketRegion.getRenter() == null) {
            getPluginInstance().getManager().sendMessage(e.getPlayer(), Objects.requireNonNull(getPluginInstance().getLangConfig().getString("rentable-interact"))
                    .replace("{id}", marketRegion.getMarketId()));
            return;
        }

        final boolean editPrevention = getPluginInstance().getConfig().getBoolean("editor-prevention");
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (handItem.getType() == Material.LAVA_BUCKET) getPluginInstance().getInSightTask().refreshShop(shop);

            final boolean canEdit = shop.canEdit(e.getPlayer());
            if (e.getPlayer().isSneaking() && canEdit) {
                if (getPluginInstance().getConfig().getBoolean("block-creative") && e.getPlayer().getGameMode() == GameMode.CREATIVE) {
                    String message = getPluginInstance().getLangConfig().getString("creative-blocked");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (shop.getShopItem() == null) {
                    setShopItem(e.getPlayer(), handItem, shop);
                    return;
                }

                if (shop.isAdminShop() && shop.getStock() < 0) {
                    String message = getPluginInstance().getLangConfig().getString("shop-infinite-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (shop.getStock() <= 0) {
                    String message = getPluginInstance().getLangConfig().getString("shop-low-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (e.getPlayer().getInventory().firstEmpty() == -1) {
                    String message = getPluginInstance().getLangConfig().getString("insufficient-space");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message.replace("{space}", "0"));
                    return;
                }

                if (editPrevention) {
                    if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        if (getPluginInstance().getServer().getOfflinePlayer(shop.getCurrentEditor()).isOnline()) {
                            e.setCancelled(true);
                            String message = getPluginInstance().getLangConfig().getString("shop-under-edit");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                            return;
                        }
                        shop.setCurrentEditor(null);
                    }
                }

                ShopEditEvent shopEditEvent = new ShopEditEvent(e.getPlayer(), shop, EditType.QUICK_WITHDRAW);
                getPluginInstance().getServer().getPluginManager().callEvent(shopEditEvent);
                if (shopEditEvent.isCancelled()) return;

                if (editPrevention) shop.setCurrentEditor(e.getPlayer().getUniqueId());
                dataPack.setSelectedShop(shop);

                String message = getPluginInstance().getLangConfig().getString("quick-withdraw");

                final int itemAmount = Math.min(shop.getShopItem().getMaxStackSize(), shop.getShopItemAmount());
                if (shop.getStock() >= itemAmount) {
                    shop.setStock(shop.getStock() - itemAmount);

                    ItemStack cloneItem = shop.getShopItem().clone();
                    cloneItem.setAmount(itemAmount);

                    if (e.getPlayer().getInventory().firstEmpty() == -1)
                        e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), cloneItem);
                    else e.getPlayer().getInventory().addItem(cloneItem);

                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message
                                .replace("{amount}", getPluginInstance().getManager().formatNumber(shop.getShopItemAmount(), false)));
                } else {
                    ItemStack shopItemClone = shop.getShopItem().clone();
                    shopItemClone.setAmount(shop.getStock());
                    shop.setStock(0);
                    if (e.getPlayer().getInventory().firstEmpty() == -1)
                        e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), shopItemClone);
                    else e.getPlayer().getInventory().addItem(shopItemClone);
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message
                                .replace("{amount}", getPluginInstance().getManager().formatNumber(shopItemClone.getAmount(), false)));
                }

                shop.updateTimeStamp();

                getPluginInstance().runEventCommands("shop-withdraw", e.getPlayer());
                dataPack.resetEditData();
                getPluginInstance().getInSightTask().refreshShop(shop);
            } else {
                if (shop.getShopItem() == null) {
                    String message = getPluginInstance().getLangConfig().getString("shop-invalid-item");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (!shop.isAdminShop() && !getPluginInstance().getConfig().getBoolean("offline-mode")) {
                    Player player = getPluginInstance().getServer().getPlayer(shop.getOwnerUniqueId());
                    if (player == null || !player.isOnline()) {
                        String message = getPluginInstance().getLangConfig().getString("owner-offline");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }
                }

                if (editPrevention) {
                    if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        if (getPluginInstance().getServer().getOfflinePlayer(shop.getCurrentEditor()).isOnline()) {
                            e.setCancelled(true);
                            String message = getPluginInstance().getLangConfig().getString("shop-under-edit");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                            return;
                        }
                        shop.setCurrentEditor(null);
                    }
                }

                if (!shop.isAdminShop() && (shop.getOwnerUniqueId().toString().equals(e.getPlayer().getUniqueId().toString()) || shop.getAssistants().contains(e.getPlayer().getUniqueId()))) {
                    ShopEditEvent shopEditEvent = new ShopEditEvent(e.getPlayer(), shop, EditType.OPEN_EDIT_MENU);
                    getPluginInstance().getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    shop.setCurrentEditor(e.getPlayer().getUniqueId());
                    dataPack.setSelectedShop(shop);
                    Inventory inventory = getPluginInstance().getManager().buildShopEditMenu(e.getPlayer());
                    if (inventory != null) e.getPlayer().openInventory(inventory);
                    getPluginInstance().runEventCommands("shop-edit", e.getPlayer());
                    return;
                }

                ShopTransactionEvent shopTransactionEvent = new ShopTransactionEvent(e.getPlayer(), shop);
                getPluginInstance().getServer().getPluginManager().callEvent(shopTransactionEvent);
                if (shopTransactionEvent.isCancelled()) return;

                dataPack.setSelectedShop(shop);
                e.getPlayer().openInventory(getPluginInstance().getManager().buildTransactionMenu(e.getPlayer(), shop));
                getPluginInstance().runEventCommands("shop-open", e.getPlayer());
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (e.getPlayer().isSneaking() && shop.canEdit(e.getPlayer())) {
                if (getPluginInstance().getConfig().getBoolean("block-creative") && e.getPlayer().getGameMode() == GameMode.CREATIVE) {
                    String message = getPluginInstance().getLangConfig().getString("creative-blocked");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (shop.getShopItem() == null && marketRegion == null) {
                    if (!e.getPlayer().hasPermission("displayshops.delete")) {
                        dataPack.resetEditData();
                        String message = getPluginInstance().getLangConfig().getString("no-permission");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }

                    if (!e.getPlayer().hasPermission("displayshops.admin") && shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        dataPack.resetEditData();
                        String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }

                    deleteShop(e.getPlayer(), shop);
                    return;
                }

                ItemStack itemInHand;
                if (isOffhandVersion) itemInHand = e.getPlayer().getInventory().getItemInMainHand();
                else itemInHand = e.getPlayer().getItemInHand();

                if (getPluginInstance().getManager().isSimilar(itemInHand, shop.getShopItem())) {
                    ShopEditEvent shopEditEvent = new ShopEditEvent(e.getPlayer(), shop, EditType.QUICK_DEPOSIT);
                    getPluginInstance().getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    final int maxStock = getPluginInstance().getManager().getMaxStock(shop), newTotalStock = (shop.getStock() + itemInHand.getAmount()), remainderInHand =
                            (newTotalStock - maxStock);

                    if (shop.isAdminShop() && shop.getStock() < 0) {
                        String message = getPluginInstance().getLangConfig().getString("shop-infinite-stock");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }

                    if (shop.getStock() >= maxStock) {
                        String message = getPluginInstance().getLangConfig().getString("shop-max-stock");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message.replace("{max}", getPluginInstance().getManager().formatNumber(maxStock, false)));
                        return;
                    }

                    String message = getPluginInstance().getLangConfig().getString("quick-deposit");
                    if (newTotalStock > maxStock && remainderInHand > 0) {
                        itemInHand.setAmount(remainderInHand);
                        shop.setStock(maxStock);
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message.replace("{amount}",
                                    getPluginInstance().getManager().formatNumber(itemInHand.getAmount() - remainderInHand
                                            , false)));
                    } else {
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message.replace("{amount}",
                                    getPluginInstance().getManager().formatNumber(itemInHand.getAmount(), false)));
                        shop.setStock(newTotalStock);
                        if (remainderInHand <= 0) {
                            if (isOffhandVersion) e.getPlayer().getInventory().setItemInMainHand(null);
                            else e.getPlayer().setItemInHand(null);
                        } else itemInHand.setAmount(remainderInHand);
                    }

                    shop.updateTimeStamp();

                    getPluginInstance().runEventCommands("shop-deposit", e.getPlayer());
                    dataPack.resetEditData();
                    getPluginInstance().getInSightTask().refreshShop(shop);
                    return;
                }
            }

            if (shop.getShopItem() == null) {
                String message = getPluginInstance().getLangConfig().getString("shop-invalid-item");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                return;
            }

            final boolean canEdit = shop.canEdit(e.getPlayer());
            if (canEdit) {
                if (getPluginInstance().getConfig().getBoolean("block-creative") && e.getPlayer().getGameMode() == GameMode.CREATIVE) {
                    String message = getPluginInstance().getLangConfig().getString("creative-blocked");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                    return;
                }

                if (editPrevention) {
                    if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        if (getPluginInstance().getServer().getOfflinePlayer(shop.getCurrentEditor()).isOnline()) {
                            e.setCancelled(true);
                            String message = getPluginInstance().getLangConfig().getString("shop-under-edit");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                            return;
                        }
                        shop.setCurrentEditor(null);
                    }
                }

                ShopEditEvent shopEditEvent = new ShopEditEvent(e.getPlayer(), shop, EditType.OPEN_EDIT_MENU);
                getPluginInstance().getServer().getPluginManager().callEvent(shopEditEvent);
                if (shopEditEvent.isCancelled()) return;

                if (editPrevention) shop.setCurrentEditor(e.getPlayer().getUniqueId());

                dataPack.setSelectedShop(shop);
                Inventory inventory = getPluginInstance().getManager().buildShopEditMenu(e.getPlayer());
                if (inventory != null) e.getPlayer().openInventory(inventory);
                getPluginInstance().runEventCommands("shop-edit", e.getPlayer());
            } else {
                if (!shop.isAdminShop() && !getPluginInstance().getConfig().getBoolean("offline-mode")) {
                    Player player = getPluginInstance().getServer().getPlayer(shop.getOwnerUniqueId());
                    if (player == null || !player.isOnline()) {
                        String message = getPluginInstance().getLangConfig().getString("owner-offline");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }
                }

                if (editPrevention) {
                    if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        if (getPluginInstance().getServer().getOfflinePlayer(shop.getCurrentEditor()).isOnline()) {
                            e.setCancelled(true);
                            String message = getPluginInstance().getLangConfig().getString("shop-under-edit");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                            return;
                        }
                        shop.setCurrentEditor(null);
                    }
                }

                ShopTransactionEvent shopTransactionEvent = new ShopTransactionEvent(e.getPlayer(), shop);
                getPluginInstance().getServer().getPluginManager().callEvent(shopTransactionEvent);
                if (shopTransactionEvent.isCancelled()) return;

                dataPack.setSelectedShop(shop);
                e.getPlayer().openInventory(getPluginInstance().getManager().buildTransactionMenu(e.getPlayer(), shop));
                getPluginInstance().runEventCommands("shop-open", e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        final Player player = (Player) e.getPlayer();

        final String inventoryTitle = getInventoryName(e.getInventory(), e.getView());
        if (!ChatColor.stripColor(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig()
                .getString("shop-edit-menu.title"))).equals(ChatColor.stripColor(inventoryTitle)))
            return;

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        if (dataPack.getChatInteractionType() != null) return;

        dataPack.resetEditData();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;

        final Player player = (Player) e.getWhoClicked();
        final String creationItemName = getPluginInstance().getManager().color(getPluginInstance().getConfig().getString("shop-creation-item.display-name"));
        String[] blockedInventories = {"ANVIL", "DISPENSER", "DROPPER", "FURNACE", "GRINDSTONE", "STONECUTTER"};
        for (int i = -1; ++i < blockedInventories.length; )
            if (player.getOpenInventory().getType().name().startsWith(blockedInventories[i])) {
                boolean isCreationItem =
                        (e.getCurrentItem() != null && (e.getCurrentItem().hasItemMeta() && e.getCurrentItem().getItemMeta() != null) && e.getCurrentItem().getItemMeta().hasDisplayName() && e.getCurrentItem().getItemMeta().getDisplayName().equals(creationItemName)) || (e.getCursor() != null && (e.getCursor().hasItemMeta() && e.getCursor().getItemMeta() != null) && e.getCursor().getItemMeta().hasDisplayName() && e.getCursor().getItemMeta().getDisplayName().equals(creationItemName));
                if (isCreationItem) {
                    e.setCancelled(true);
                    e.setResult(Event.Result.DENY);
                    player.updateInventory();
                    return;
                }
            }

        final String inventoryName = getInventoryName(e.getClickedInventory(), e.getView());
        if (inventoryName == null || player.isSleeping()) return;

        if (inventoryName.equals(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("shop-visit-menu.title")))) {
            checkInteractiveTouches(e, player);
            operateVisitMenu(e, player);
        } else if (inventoryName.equals(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("base-block-menu.title")))) {
            checkInteractiveTouches(e, player);
            operateBBM(e, player);
        } else if (inventoryName.equals(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("shop-edit-menu.title")))) {
            checkInteractiveTouches(e, player);
            operateEditMenu(e, player);
        } else if (inventoryName.equals(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("shop-transaction-menu.title")))) {
            checkInteractiveTouches(e, player);
            operateTransactionMenu(e, player);
        }
    }

    private void checkInteractiveTouches(InventoryClickEvent e, Player player) {
        if ((e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD || e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.CLONE_STACK || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) && player.getGameMode() == GameMode.CREATIVE) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            player.updateInventory();
        }
    }

    private void operateTransactionMenu(InventoryClickEvent e, Player player) {
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.CREATIVE) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        } else {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
        }

        if (e.getClickedInventory() == null || e.getClickedInventory().getType() == InventoryType.PLAYER) return;
        final DDataPack dataPack = (DDataPack) getPluginInstance().getManager().getDataPack(player);

        if (inventoryCheck(player, dataPack)) {
            Shop shop = dataPack.getSelectedShop();
            if (shop == null) {
                player.closeInventory();
                String message = getPluginInstance().getLangConfig().getString("shop-edit-invalid");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                player.updateInventory();
                return;
            }


            if (getPluginInstance().getConfig().getBoolean("editor-prevention")) {
                if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) {
                    if (getPluginInstance().getServer().getOfflinePlayer(shop.getCurrentEditor()).isOnline()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        String message = getPluginInstance().getLangConfig().getString("shop-under-edit");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }
                    shop.setCurrentEditor(null);
                }
            }

            String message;
            boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null),
                    forceUse = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");
            final int previewSlot = getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.preview-slot");

            if (e.getSlot() == previewSlot && !useVault) {
                ItemStack previewSlotItem = e.getClickedInventory().getItem(previewSlot);
                if (previewSlotItem == null) return;
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (shop.getShopItem().getType() == previewSlotItem.getType()) {
                    ItemStack currencyItem = (!forceUse && shop.getTradeItem() != null) ? shop.getTradeItem().clone() : getPluginInstance().getManager().buildShopCurrencyItem(1);
                    ItemMeta itemMeta = currencyItem.getItemMeta();
                    if (itemMeta != null) {
                        List<String> tradeLore = getPluginInstance().getMenusConfig().getStringList("shop-transaction-menu.trade-item-lore"), lore =
                                (currencyItem.getItemMeta().getLore() != null ?
                                        currencyItem.getItemMeta().getLore() : new ArrayList<>());
                        for (int i = -1; ++i < tradeLore.size(); )
                            lore.add(getPluginInstance().getManager().color(tradeLore.get(i).replace("{sell}",
                                    getPluginInstance().getManager().formatNumber(shop.getSellPrice(true), false)).replace(
                                    "{buy}", getPluginInstance().getManager().formatNumber(shop.getBuyPrice(true), false))));
                        itemMeta.setLore(lore);
                        currencyItem.setItemMeta(itemMeta);
                    }

                    e.getClickedInventory().setItem(previewSlot, currencyItem);
                    message = getPluginInstance().getLangConfig().getString("shop-trade-item");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                } else {
                    ItemStack previewItem = shop.getShopItem().clone();
                    if (!(getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null)) {
                        ItemMeta itemMeta = previewItem.getItemMeta();
                        if (itemMeta != null) {
                            List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore()), previewLore =
                                    getPluginInstance().getMenusConfig().getStringList(
                                            "shop-transaction-menu.preview-trade-item-lore");
                            for (int i = -1; ++i < previewLore.size(); )
                                lore.add(getPluginInstance().getManager().color(previewLore.get(i)));
                            itemMeta.setLore(lore);
                            previewItem.setItemMeta(itemMeta);
                        }
                    }

                    e.getClickedInventory().setItem(previewSlot, previewItem);
                    message = getPluginInstance().getLangConfig().getString("shop-preview-item");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                }

            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.buy-item.slot")
                    && !Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-transaction-menu.blocked-item.material"))
                    .toUpperCase().contains(Objects.requireNonNull(e.getCurrentItem()).getType().name())) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (shop.getBuyPrice(true) < 0) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-buy-invalid");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                final int currentCd = dataPack.getCooldown(player, "transaction-cd", getPluginInstance().getConfig().getInt("transaction-cooldown"));
                if (currentCd > 0) return;
                else dataPack.updateCooldown("transaction-cd");

                if (e.getClick().isShiftClick()) {
                    int availableUnits = (shop.getStock() < 0 ? -1 : Math.max(0, (shop.getStock() / shop.getShopItemAmount())));
                    if (shop.getBuyLimit() > 0) {
                        int remainingLimit = (shop.getBuyLimit() - shop.getBuyCounter());
                        if (remainingLimit <= 0) {
                            player.closeInventory();
                            message = getPluginInstance().getLangConfig().getString("buy-limit-exceeded");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(player, message);
                            player.updateInventory();
                            return;
                        }

                        availableUnits = Math.min(availableUnits, remainingLimit);
                    }

                    if ((!shop.isAdminShop() && shop.getStock() <= 0) || (shop.isAdminShop() && (shop.getStock() >= 0 && shop.getStock() < shop.getShopItemAmount()))) {
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("shop-low-stock");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        player.updateInventory();
                        return;
                    }

                    double investorBalance, tax = getPluginInstance().getConfig().getDouble("transaction-tax");
                    if (useVault) investorBalance = getPluginInstance().getVaultEconomy().getBalance(player);
                    else {
                        final ItemStack currencyItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use")
                                || shop.getTradeItem() == null) ? getPluginInstance().getManager().buildShopCurrencyItem(1) : shop.getTradeItem();
                        investorBalance = getPluginInstance().getManager().getItemAmount(player.getInventory(), currencyItem);
                    }

                    final double buyPrice = shop.getBuyPrice(true);
                    final int maxBuyAll = getPluginInstance().getConfig().getInt("maximum-buy-all");
                    int affordableUnits = (player.hasPermission("displayshops.bypass") ? maxBuyAll
                            : Math.min(maxBuyAll, (int) (investorBalance / (buyPrice + (buyPrice * tax)))));
                    availableUnits = ((availableUnits < 0 && shop.isAdminShop()) ? affordableUnits : availableUnits);

                    if (availableUnits <= 0) {
                        message = getPluginInstance().getLangConfig().getString("no-affordable-units");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    final int availableSpace = getPluginInstance().getManager().getInventorySpaceForItem(player, shop.getShopItem());
                    if (player.getInventory().firstEmpty() == -1 || availableSpace < shop.getShopItemAmount()) {
                        dataPack.resetEditData();
                        message = getPluginInstance().getLangConfig().getString("insufficient-space");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message
                                    .replace("{space}", getPluginInstance().getManager().formatNumber(availableSpace, false)));
                        return;
                    }

                    runEconomyCall(player, shop, EconomyCallType.BUY, Math.max(0, Math.min((availableSpace / shop.getShopItemAmount()), availableUnits)));
                    return;
                }

                int unitCount = 1;
                final ItemStack unitCountItem = e.getClickedInventory().getItem(getPluginInstance()
                        .getMenusConfig().getInt("shop-transaction-menu.unit-item.slot"));
                if (unitCountItem != null) unitCount = unitCountItem.getAmount();

                if ((shop.isAdminShop() && (shop.getStock() >= 0 && shop.getStock() < (shop.getShopItemAmount() * unitCount)))
                        || (!shop.isAdminShop() && shop.getStock() < (shop.getShopItemAmount() * unitCount))) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-low-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, true)) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("buy-limit-exceeded");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                final int availableSpace = getPluginInstance().getManager().getInventorySpaceForItem(player, shop.getShopItem());
                if (player.getInventory().firstEmpty() == -1 || availableSpace < (unitCount * shop.getShopItemAmount())) {
                    dataPack.resetEditData();
                    message = getPluginInstance().getLangConfig().getString("insufficient-space");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message
                                .replace("{space}", getPluginInstance().getManager().formatNumber(availableSpace, false)));
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.BUY, unitCount);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.sell-item.slot")
                    && !Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-transaction-menu.blocked-item.material"))
                    .toUpperCase().contains(Objects.requireNonNull(e.getCurrentItem()).getType().name())) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (shop.getSellPrice(true) < 0) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-sell-invalid");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                if (shop.getCommands().size() > 0) {
                    message = getPluginInstance().getLangConfig().getString("commands-sell-fail");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                final int itemAmount = getPluginInstance().getManager().getItemAmount(player.getInventory(), shop.getShopItem());
                if (e.getClick().isShiftClick()) {
                    final int maxSellAll = getPluginInstance().getConfig().getInt("maximum-sell-all");
                    int totalSellableUnits = (itemAmount / shop.getShopItemAmount()); // investor

                    if (!shop.isAdminShop() || shop.getStock() >= 0) {
                        final long maxStock = getPluginInstance().getManager().getMaxStock(shop),
                                availableSpace = (maxStock - shop.getStock());
                        totalSellableUnits = (int) Math.min((availableSpace / shop.getShopItemAmount()), totalSellableUnits);
                        if (totalSellableUnits <= 0) {
                            message = getPluginInstance().getLangConfig().getString("shop-max-stock");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(player, message
                                        .replace("{max}", getPluginInstance().getManager().formatNumber(maxStock, false)));
                            return;
                        }
                    }

                    if (shop.getSellLimit() > 0) {
                        int remainingLimit = (shop.getSellLimit() - shop.getSellCounter());
                        if (remainingLimit <= 0) {
                            player.closeInventory();
                            message = getPluginInstance().getLangConfig().getString("sell-limit-exceeded");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(player, message);
                            player.updateInventory();
                            return;
                        }

                        totalSellableUnits = Math.min(totalSellableUnits, remainingLimit);
                    }

                    if (totalSellableUnits <= 0) {
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("transaction-all-fail");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        player.updateInventory();
                        return;
                    }

                    runEconomyCall(player, shop, EconomyCallType.SELL, Math.min(totalSellableUnits, maxSellAll));
                    return;
                }

                int unitCount = 1;
                final ItemStack unitCountItem = e.getClickedInventory().getItem(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.slot"));
                if (unitCountItem != null) unitCount = unitCountItem.getAmount();

                if (itemAmount < (shop.getShopItemAmount() * unitCount)) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-invalid-amount");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{amount}",
                                getPluginInstance().getManager().formatNumber(shop.getShopItemAmount() * unitCount, false)));
                    player.updateInventory();
                    return;
                }

                final int maxStock = getPluginInstance().getManager().getMaxStock(shop);
                if ((!shop.isAdminShop() || (shop.isAdminShop() && shop.getStock() >= 0)) && (shop.getStock() + (shop.getShopItemAmount() * unitCount)) > maxStock) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-max-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message
                                .replace("{max}", getPluginInstance().getManager().formatNumber(maxStock, false)));
                    player.updateInventory();
                    return;
                }

                if (dataPack.hasMetTransactionLimit(shop, false)) {
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("sell-limit-exceeded");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    player.updateInventory();
                    return;
                }

                runEconomyCall(player, shop, EconomyCallType.SELL, unitCount);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-increase-item.slot")) {
                ItemStack unitCountItem = e.getClickedInventory().getItem(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.slot"));
                if (unitCountItem == null) {
                    message = getPluginInstance().getLangConfig().getString("unit-item-invalid");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (unitCountItem.getAmount() >= unitCountItem.getType().getMaxStackSize()) {
                    message = getPluginInstance().getLangConfig().getString("unit-count-max");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
                    final int newStack = (unitCountItem.getAmount() + ((int) (unitCountItem.getType().getMaxStackSize() * 0.25)));
                    getPluginInstance().getManager().updateTransactionMenu(e.getClickedInventory(), player, shop, e.getClick().isRightClick()
                            ? Math.min(newStack, unitCountItem.getType().getMaxStackSize()) : (unitCountItem.getAmount() + 1));
                });

                message = getPluginInstance().getLangConfig().getString("unit-count-increased");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-decrease-item.slot")) {
                ItemStack unitCountItem = e.getClickedInventory().getItem(getPluginInstance().getMenusConfig().getInt("shop-transaction-menu.unit-item.slot"));
                if (unitCountItem == null) {
                    message = getPluginInstance().getLangConfig().getString("unit-item-invalid");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (unitCountItem.getAmount() <= 1) {
                    message = getPluginInstance().getLangConfig().getString("unit-count-min");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
                    final int newStack = (unitCountItem.getAmount() - ((int) (unitCountItem.getType().getMaxStackSize() * 0.25)));
                    getPluginInstance().getManager().updateTransactionMenu(e.getClickedInventory(), player, shop, e.getClick().isRightClick() ? Math.max(newStack, 0) :
                            (unitCountItem.getAmount() - 1));
                });

                message = getPluginInstance().getLangConfig().getString("unit-count-decreased");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
            }
        }
    }

    private void operateEditMenu(InventoryClickEvent e, Player player) {
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.CREATIVE) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        } else {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
        }

        if (e.getClickedInventory() == null || (e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER))
            return;

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        if (inventoryCheck(player, dataPack)) {
            final Shop shop = dataPack.getSelectedShop();
            if (shop == null) {
                dataPack.resetEditData();
                player.closeInventory();
                String message = getPluginInstance().getLangConfig().getString("shop-edit-invalid");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                return;
            }

            if (getPluginInstance().getConfig().getBoolean("editor-prevention")) {
                if ((shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) || !shop.canEdit(player)) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    return;
                }
            }

            boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null);
            String message, cancel = getPluginInstance().getConfig().getString("chat-interaction-cancel"), cancelKey = cancel != null ? cancel : "";
            if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.balance-manage-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                final boolean isLeftClick = e.getClick().isLeftClick();
                final ChatInteractionType chatInteractionType = (isLeftClick ? ChatInteractionType.DEPOSIT_BALANCE : ChatInteractionType.WITHDRAW_BALANCE);
                ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, chatInteractionType, "");
                getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                if (!chatInteractionEvent.isCancelled()) {
                    dataPack.setChatInteractionType(chatInteractionType);
                    dataPack.updateChatTimeoutTask(player);
                }

                ItemStack tradeItem = null;
                String tradeItemName = "";
                if (!useVault) {
                    tradeItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use")
                            ? getPluginInstance().getManager().buildShopCurrencyItem(1) : (shop.getTradeItem() != null
                            ? shop.getTradeItem() : getPluginInstance().getManager().buildShopCurrencyItem(1)));
                    if (tradeItem != null) tradeItemName = getPluginInstance().getManager().getItemName(tradeItem);
                }

                player.closeInventory();
                message = getPluginInstance().getLangConfig().getString(isLeftClick ? "deposit-balance" : "withdraw-balance");
                if (message != null && !message.equalsIgnoreCase("")) {
                    double balance = (isLeftClick ? (useVault ? getPluginInstance().getVaultEconomy().getBalance(player)
                            : getPluginInstance().getManager().getItemAmount(player.getInventory(), tradeItem)) : shop.getStoredBalance());
                    getPluginInstance().getManager().sendMessage(player, message
                            .replace("{cancel}", cancelKey).replace("{trade-item}", tradeItemName)
                            .replace("{balance}", getPluginInstance().getManager().formatNumber(balance, true)));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.base-block-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
                player.openInventory(getPluginInstance().getManager().getBaseBlockSelectionMenu(player, shop));
                dataPack.setSelectedShop(shop);
                shop.setCurrentEditor(player.getUniqueId());
                message = getPluginInstance().getLangConfig().getString("base-block-change");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.stock-manage-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
                if (e.getClick().isLeftClick()) {
                    if (shop.isAdminShop() && shop.getStock() < 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("shop-infinite-stock");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.DEPOSIT_STOCK, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.DEPOSIT_STOCK);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("deposit-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey).replace("{contents}",
                                getPluginInstance().getManager().formatNumber(getPluginInstance().getManager().getItemAmount(player.getInventory(), shop.getShopItem()), false)));
                } else if (e.getClick().isRightClick()) {
                    if (shop.isAdminShop() && shop.getStock() < 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("shop-infinite-stock");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.WITHDRAW_STOCK, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.WITHDRAW_STOCK);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("withdraw-stock");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.limits.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
                if (e.getClick().isShiftClick()) {
                    EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                            EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.limits.price"));
                    if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    shop.setBuyCounter(0);
                    shop.setSellCounter(0);

                    for (DataPack dp : getPluginInstance().getManager().getDataPackMap().values()) {
                        dp.updateCurrentTransactionLimitCounter(shop, true, 0);
                        dp.updateCurrentTransactionLimitCounter(shop, false, 0);
                    }

                    shop.updateTimeStamp();

                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("limit-reset");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                } else if (e.getClick().isLeftClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.BUY_LIMIT, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.BUY_LIMIT);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("buy-limit");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                } else if (e.getClick().isRightClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.SELL_LIMIT, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.SELL_LIMIT);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("sell-limit");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.change-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
                if (e.getClick().isShiftClick() && !useVault) {
                    ItemStack handItem = Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                            ? player.getInventory().getItemInMainHand().clone() : player.getItemInHand().clone();

                    ShopItemSetEvent shopItemSetEvent = new ShopItemSetEvent(player, shop, ItemType.TRADE, handItem);
                    getPluginInstance().getServer().getPluginManager().callEvent(shopItemSetEvent);
                    if (shopItemSetEvent.isCancelled()) return;

                    if (handItem.getType() == Material.AIR) {
                        message = getPluginInstance().getLangConfig().getString("trade-item-invalid");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        if (dataPack.getCurrentChatTask() != null) dataPack.getCurrentChatTask().cancel();

                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    } else {

                        if (handItem.getAmount() > 1 && getPluginInstance().getConfig().getBoolean("force-single-stack")) {
                            dataPack.resetEditData();
                            player.closeInventory();
                            message = getPluginInstance().getLangConfig().getString("single-item-required");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(player, message);
                            return;
                        }

                        if (shop.getTradeItem() != null && shop.getStoredBalance() > 0) {
                            dataPack.resetEditData();
                            player.closeInventory();
                            message = getPluginInstance().getLangConfig().getString("empty-balance-required");
                            if (message != null && !message.equalsIgnoreCase(""))
                                getPluginInstance().getManager().sendMessage(player, message);
                            return;
                        }

                        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                                EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-item.price"));
                        if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                            dataPack.resetEditData();
                            player.closeInventory();
                            return;
                        }

                        if (shop.getTradeItem() != null && handItem.isSimilar(shop.getTradeItem())) {
                            message = getPluginInstance().getLangConfig().getString("trade-item-similar");
                        } else {
                            shop.setTradeItem(handItem);
                            message = getPluginInstance().getLangConfig().getString("trade-item-set");
                        }
                    }

                    dataPack.resetEditData();
                    player.closeInventory();
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{amount}", shop.getTradeItem() != null
                                ? getPluginInstance().getManager().formatNumber(shop.getTradeItem().getAmount(), false) : ""));
                    getPluginInstance().runEventCommands("trade-item-set", player);
                } else if (e.getClick().isLeftClick()) {
                    if (getPluginInstance().getConfig().getBoolean("require-empty-stock") && shop.getStock() > 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("shop-empty-required");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                            EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-item.price"));
                    if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    shop.dropStock();
                    shop.setShopItem(null);

                    shop.updateTimeStamp();

                    getPluginInstance().getInSightTask().refreshShop(shop);
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("item-change");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                } else if (e.getClick().isRightClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.SHOP_ITEM_AMOUNT, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.SHOP_ITEM_AMOUNT);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("item-change-amount");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.change-price-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
                if (e.getClick().isShiftClick() && getPluginInstance().getConfig().getBoolean("dynamic-prices")) {
                    EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player,
                            null, shop, EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.change-price-item.price"));
                    if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    shop.setDynamicBuyCounter(0);
                    shop.setDynamicSellCounter(0);
                    shop.setLastBuyTimeStamp(System.currentTimeMillis());
                    shop.setLastSellTimeStamp(System.currentTimeMillis());
                    shop.setDynamicPriceChange(!shop.canDynamicPriceChange());

                    shop.updateTimeStamp();

                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("dynamic-price-toggle");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{status}",
                                (Objects.requireNonNull(shop.canDynamicPriceChange() ? getPluginInstance().getLangConfig().getString("enabled")
                                        : getPluginInstance().getLangConfig().getString("disabled")))).replace("{cancel}", cancelKey));
                } else if (e.getClick().isLeftClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.BUY_PRICE, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.BUY_PRICE);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("buy-price-change");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                } else if (e.getClick().isRightClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.SELL_PRICE, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.SELL_PRICE);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("sell-price-change");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.description-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (!player.hasPermission("displayshops.description")) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("description-no-access");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                if (e.getClick().isLeftClick()) {
                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.BUY_PRICE, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.EDIT_DESCRIPTION);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("set-description-interaction");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                } else if (e.getClick().isRightClick()) {
                    if (shop.getDescription() == null || shop.getDescription().isEmpty()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("shop-description-empty");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                            EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.description-item.price"));

                    if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    shop.setDescription("");
                    shop.updateTimeStamp();

                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-description-empty");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.assistants-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                if (!player.hasPermission("displayshops.assistants") || (shop.getOwnerUniqueId() != null
                        && !player.getUniqueId().toString().equals(shop.getOwnerUniqueId().toString()))) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("no-assistants-access");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                if (e.getClick().isShiftClick()) {
                    if (shop.getAssistants().size() == 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("assistants-empty");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    List<String> playerNames = new ArrayList<>();
                    for (UUID playerId : shop.getAssistants()) {
                        if (playerId == null) continue;
                        final OfflinePlayer offlinePlayer = getPluginInstance().getServer().getOfflinePlayer(playerId);
                        playerNames.add(offlinePlayer.getName());
                    }

                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("assistants-list");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{list}", playerNames.toString()));
                } else if (e.getClick().isLeftClick()) {
                    final int assistantsCap = getPluginInstance().getConfig().getInt("assistants-cap");
                    if (shop.getAssistants().size() >= assistantsCap) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("assistants-cap-exceeded");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message.replace("{cap}", getPluginInstance().getManager().formatNumber(assistantsCap, false)));
                        return;
                    }

                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.ASSISTANTS_ADD, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.ASSISTANTS_ADD);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("assistants-add");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                } else if (e.getClick().isRightClick()) {
                    if (shop.getAssistants().size() == 0) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        message = getPluginInstance().getLangConfig().getString("assistants-empty");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(player, message);
                        return;
                    }

                    ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.ASSISTANTS_REMOVE, "");
                    getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
                    if (!chatInteractionEvent.isCancelled()) {
                        dataPack.setChatInteractionType(ChatInteractionType.ASSISTANTS_REMOVE);
                        dataPack.updateChatTimeoutTask(player);
                    }

                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("assistants-remove");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));
                }
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-edit-menu.destroy-item.slot")) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                        ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

                MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(shop.getBaseLocation().asBukkitLocation());
                if (marketRegion != null) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("rentable-shop-delete");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                if (!player.hasPermission("displayshops.delete")) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("no-permission");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                if (getPluginInstance().getConfig().getBoolean("require-empty-stock") && !shop.isAdminShop() && shop.getStock() > 0) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    message = getPluginInstance().getLangConfig().getString("shop-empty-required");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player,
                        null, shop, EconomyCallType.EDIT_ACTION, getPluginInstance().getMenusConfig().getDouble("shop-edit-menu.destroy-item.price"));

                if (economyCallEvent == null || (economyCallEvent.isCancelled() || !economyCallEvent.willSucceed())) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    return;
                }

                ShopDeletionEvent shopDeletionEvent = new ShopDeletionEvent(player, shop.getBaseLocation().asBukkitLocation());
                if (shopDeletionEvent.isCancelled()) return;

                deleteShop(player, shop);
                dataPack.resetEditData();
                player.closeInventory();
            }
        }
    }

    private void operateVisitMenu(InventoryClickEvent e, Player player) {
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.CREATIVE) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        } else e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        if (e.getClickedInventory() == null || e.getClickedInventory().getType() == InventoryType.PLAYER || e.getCurrentItem() == null)
            return;
        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        if (e.getSlot() <= ((e.getClickedInventory().getSize() - 1) - 9)) {
            player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                    ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);
            String shopId = getPluginInstance().getPacketManager().getNBT(e.getCurrentItem(), "shop-id");
            if (shopId == null || shopId.isEmpty()) return;
            final Shop shop = getPluginInstance().getManager().getShopMap().get(UUID.fromString(shopId));
            if (shop == null) return;

            player.closeInventory();
            double visitCost = getPluginInstance().getConfig().getDouble("visit-charge");
            EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player,
                    null, shop, EconomyCallType.VISIT, visitCost);
            if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                dataPack.resetEditData();
                player.closeInventory();
                return;
            }

            shop.visit(player, true);
        } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.slot")) {
            player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                    ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

            ChatInteractionEvent chatInteractionEvent = new ChatInteractionEvent(player, ChatInteractionType.VISIT_FILTER_ENTRY, "");
            getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionEvent);
            if (!chatInteractionEvent.isCancelled()) {
                dataPack.setChatInteractionType(chatInteractionEvent.getChatInteractionType());
                dataPack.updateChatTimeoutTask(player);
            }

            player.closeInventory();
            String cancel = getPluginInstance().getConfig().getString("chat-interaction-cancel"),
                    cancelKey = ((cancel != null) ? cancel : ""),
                    message = getPluginInstance().getLangConfig().getString("visit-filter-prompt");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message.replace("{cancel}", cancelKey));

        } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.slot")) {
            player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                    ? Sound.valueOf("UI_BUTTON_CLICK") : Sound.valueOf("CLICK"), 1, 1);

            final String buyType = getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.buy-type"),
                    sellType = getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.sell-type"),
                    bothType = getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.both-type"),
                    nameFormat = ChatColor.stripColor(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item" +
                            ".display-name"))),
                    currentType = getCurrentShopFilterType(e.getCurrentItem());

            final ItemStack itemStack = e.getCurrentItem().clone();
            final ItemMeta itemMeta = e.getCurrentItem().getItemMeta();

            if (currentType != null && nameFormat != null && buyType != null && sellType != null && bothType != null && itemMeta != null) {

                if (currentType.equals(buyType)) {
                    itemMeta.setDisplayName(getPluginInstance().getManager().color(nameFormat.replace("{type}", sellType)));
                } else if (currentType.equals(sellType)) itemMeta.setDisplayName(getPluginInstance().getManager().color(nameFormat.replace("{type}", bothType)));
                else itemMeta.setDisplayName(getPluginInstance().getManager().color(nameFormat.replace("{type}", buyType)));

                itemStack.setItemMeta(itemMeta);
                e.getClickedInventory().setItem(e.getSlot(), itemStack);

                clearNSetVisit(e.getClickedInventory(), player, dataPack, true, false);
            }

        } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-visit-menu.next-page-item.slot")) {
            String mat = getPluginInstance().getMenusConfig().getString("shop-visit-menu.next-page-item.material");
            if (mat != null) {
                mat = mat.toUpperCase().replace(" ", "_").replace("-", "_");

                if (((e.getCurrentItem().getType() == Material.PLAYER_HEAD || e.getCurrentItem().getType().name().equals("SKULL")) && mat.startsWith("HEAD"))
                        || mat.contains(e.getCurrentItem().getType().name()))
                    clearNSetVisit(e.getClickedInventory(), player, dataPack, false, true);
            }
        } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-visit-menu.previous-page-item.slot")) {
            String mat = getPluginInstance().getMenusConfig().getString("shop-visit-menu.previous-page-item.material");
            if (mat != null) {
                mat = mat.toUpperCase().replace(" ", "_").replace("-", "_");

                if (((e.getCurrentItem().getType() == Material.PLAYER_HEAD
                        || e.getCurrentItem().getType().name().equals("SKULL")) && mat.startsWith("HEAD"))
                        || mat.contains(e.getCurrentItem().getType().name()))
                    clearNSetVisit(e.getClickedInventory(), player, dataPack, false, false);
            }
        } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("shop-visit-menu.refresh-page-item.slot")) {
            clearNSetVisit(e.getClickedInventory(), player, dataPack, true, false);
        }
    }

    private String getCurrentShopFilterType(@NotNull ItemStack currentItem) {
        String nameFormat = ChatColor.stripColor(getPluginInstance().getManager().color(getPluginInstance().getMenusConfig().getString("shop-visit-menu.type-item.display-name")))
                .replace("{type}", "");

        final ItemMeta itemMeta = currentItem.getItemMeta();
        if (itemMeta != null) {
            final String currentType = ChatColor.stripColor(itemMeta.getDisplayName()).replace(nameFormat, "");
            if (!currentType.isEmpty()) return currentType;
        }
        return null;
    }

    public void clearNSetVisit(Inventory clickedInventory, Player player, DataPack dataPack, boolean keepPage, boolean isNext) {
        if (clickedInventory == null) return;

        final int filterSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.filter-item.slot"),
                typeSlot = getPluginInstance().getMenusConfig().getInt("shop-visit-menu.type-item.slot");

        for (int i = -1; ++i < (clickedInventory.getSize() - 9); )
            if (i != filterSlot) clickedInventory.setItem(i, null);

        String material = getPluginInstance().getMenusConfig().getString("shop-visit-menu.background-item.material");
        if (material != null && !material.isEmpty()) {
            ItemStack backgroundItem = new CustomItem(getPluginInstance(), material,
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.durability"),
                    getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.amount"))
                    .setDisplayName(player, getPluginInstance().getMenusConfig().getString("shop-visit-menu.background-item.display-name"))
                    .setLore(player, getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.lore"))
                    .setEnchantments(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.enchantments"))
                    .setItemFlags(getPluginInstance().getMenusConfig().getStringList("shop-visit-menu.background-item.flags"))
                    .setModelData(getPluginInstance().getMenusConfig().getInt("shop-visit-menu.background-item.model-data")).get();
            if (backgroundItem != null)
                for (int i = ((clickedInventory.getSize() - 1) - 9); ++i < clickedInventory.getSize(); )
                    if (i != filterSlot && i != typeSlot) clickedInventory.setItem(i, backgroundItem);
        }

        String filter = null, filterFormat = getPluginInstance().getMenusConfig().getString("shop-visit-menu.filter-item.display-name");
        if (filterFormat != null) {
            filterFormat = ChatColor.stripColor(getPluginInstance().getManager().color(filterFormat.replace("{filter}", "")));

            final ItemStack itemStack = clickedInventory.getItem(filterSlot);
            if (itemStack != null && itemStack.getItemMeta() != null)
                filter = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName()).replace(filterFormat, "");
        }

        if (filter != null && filter.equalsIgnoreCase(Objects.requireNonNull(getPluginInstance().getMenusConfig().getString("shop-visit-menu.filter-item.no-previous"))))
            filter = null;

        dataPack.setCurrentVisitPage(dataPack.getCurrentVisitPage() + (keepPage ? 0 : (isNext ? 1 : -1)));

        String currentType = null;
        final ItemStack typeItem = clickedInventory.getItem(typeSlot);
        if (typeItem != null) currentType = getCurrentShopFilterType(typeItem);

        getPluginInstance().getManager().loadVisitPage(player, dataPack, clickedInventory, currentType, filter);
    }

    private void operateBBM(InventoryClickEvent e, Player player) {
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.CREATIVE) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        } else {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
        }

        if (Objects.requireNonNull(e.getClickedInventory()).getType() == InventoryType.PLAYER) return;
        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        if (inventoryCheck(player, dataPack)) {
            Shop shop = dataPack.getSelectedShop();
            if (shop == null) {
                dataPack.resetEditData();
                player.closeInventory();
                String message = getPluginInstance().getLangConfig().getString("shop-edit-invalid");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                return;
            }

            if (shop.getBaseLocation() == null) {
                dataPack.resetEditData();
                player.closeInventory();
                String message = getPluginInstance().getLangConfig().getString("base-location-invalid");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                return;
            }

            if (getPluginInstance().getConfig().getBoolean("editor-prevention")) {
                if (shop.getCurrentEditor() != null && !shop.getCurrentEditor().toString().equals(player.getUniqueId().toString())) {
                    dataPack.resetEditData();
                    player.closeInventory();
                    return;
                }
            }

            if (e.getCurrentItem() == null || e.getCurrentItem().getType().name().contains("AIR")) return;
            if (e.getSlot() >= 0 && e.getSlot() < (e.getClickedInventory().getSize() - 9)) {
                player.playSound(player.getLocation(), Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9 ? Sound.valueOf("UI_BUTTON_CLICK")
                        : Sound.valueOf("CLICK"), 1, 1);

                if (!e.getCurrentItem().hasItemMeta() || !Objects.requireNonNull(e.getCurrentItem().getItemMeta()).hasDisplayName()) return;

                String shopId = getPluginInstance().getPacketManager().getNBT(e.getCurrentItem(), "ds-bbm"),
                        typeId = getPluginInstance().getPacketManager().getNBT(e.getCurrentItem(), "ds-type");
                if (shopId == null || typeId == null || shopId.isEmpty() || typeId.isEmpty()) return;

                final Location baseBlockLocation = shop.getBaseLocation().asBukkitLocation();
                final Block block = baseBlockLocation.getBlock();

                final boolean hasStarPerm = player.hasPermission("displayshops.bbm.*");
                if (!hasStarPerm && !player.hasPermission("displayshops.bbm." + typeId)) {
                    String message = getPluginInstance().getLangConfig().getString("missing-bbm-requirement");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(player, message);
                    return;
                }

                final String unlockId = (typeId + ":" + e.getCurrentItem().getDurability());
                if (!dataPack.hasUnlockedBBM(unlockId)) {
                    final double foundPrice = getPluginInstance().getManager().getBBMPrice(typeId, e.getCurrentItem().getDurability());
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
                    if (getPluginInstance().isItemAdderInstalled()) dev.lone.itemsadder.api.CustomBlock.remove(baseBlockLocation);
                    block.setType(Material.AIR);
                    block.setType(material);

                    final boolean isOld = ((Math.floor(this.getPluginInstance().getServerVersion()) <= 1_12));
                    if (isOld) try {
                        Method method = Block.class.getMethod("setData", byte.class);
                        method.invoke(baseBlockLocation.getBlock(), ((byte) (e.getCurrentItem().getDurability() < 0
                                ? oppositeDirectionByte(Direction.getYaw(player)) : e.getCurrentItem().getDurability())));
                    } catch (Exception ei) {
                        ei.printStackTrace();
                    }
                    else {
                        block.setBlockData(getPluginInstance().getServer().createBlockData(material));
                        setBlock(block, material, (material.name().contains("SHULKER") ? BlockFace.UP : BlockFace.valueOf(Direction.getYaw(player).name())));
                    }
                } else if (getPluginInstance().isItemAdderInstalled()) {
                    dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(typeId);
                    if (customBlock != null) {
                        block.setType(Material.AIR);
                        customBlock.place(baseBlockLocation);
                    }
                }

                String changeSound = getPluginInstance().getConfig().getString("immersion-section.shop-bbm-sound");
                if (changeSound != null && !changeSound.equalsIgnoreCase(""))
                    player.playSound(player.getLocation(), Sound.valueOf(changeSound.toUpperCase().replace(" ", "_")
                            .replace("-", "_")), 1, 1);

                String changeEffect = getPluginInstance().getConfig().getString("immersion-section.shop-bbm-particle");
                if (changeEffect != null && !changeEffect.equalsIgnoreCase(""))
                    getPluginInstance().getPacketManager().getParticleHandler().displayParticle((Player) e.getWhoClicked(), changeEffect.toUpperCase()
                                    .replace(" ", "_").replace("-", "_"), baseBlockLocation.add(0.5, 0.5, 0.5),
                            0.5, 0.5, 0.5, 0, 12);

                dataPack.resetEditData();
                player.closeInventory();

                String message = getPluginInstance().getLangConfig().getString("base-block-set");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.slot")
                    && (dataPack.getBaseBlockPageMap().containsKey(dataPack.getCurrentBaseBlockPage() + 1))) {
                dataPack.setCurrentBaseBlockPage(dataPack.getCurrentBaseBlockPage() + 1);
                clearNSetBBM(e.getClickedInventory(), player, dataPack);
            } else if (e.getSlot() == getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.slot")
                    && (dataPack.getCurrentBaseBlockPage() - 1) >= 1) {
                dataPack.setCurrentBaseBlockPage(dataPack.getCurrentBaseBlockPage() - 1);
                clearNSetBBM(e.getClickedInventory(), player, dataPack);
            }
        }
    }

    private void clearNSetBBM(Inventory clickedInventory, Player player, DataPack dataPack) {
        for (int i = -1; ++i < (clickedInventory.getSize() - 9); )
            clickedInventory.setItem(i, null);

        ItemStack backgroundItem = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("base-block-menu.background-item.material"),
                getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.durability"),
                getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.amount"))
                .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu.background-item.display-name"))
                .setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.lore"))
                .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.enchantments"))
                .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.background-item.flags"))
                .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.background-item.model-data")).get();

        int nSlot = getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.slot");
        if (nSlot > -1 && nSlot < clickedInventory.getSize() && dataPack.getBaseBlockPageMap().containsKey(dataPack.getCurrentBaseBlockPage() + 1)) {
            if (Objects.requireNonNull(clickedInventory.getItem(nSlot)).getType() == backgroundItem.getType()) {
                ItemStack nextPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("base-block-menu.next-page-item.material"),
                        getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.durability"),
                        getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.amount"))
                        .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu.next-page-item.display-name"))
                        .setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.lore"))
                        .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.enchantments"))
                        .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.next-page-item.flags"))
                        .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.next-page-item.model-data")).get();
                clickedInventory.setItem(nSlot, nextPage);
            }
        } else clickedInventory.setItem(nSlot, backgroundItem);

        int pSlot = getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.slot");
        if (pSlot > -1 && pSlot < clickedInventory.getSize() && dataPack.getBaseBlockPageMap().containsKey(dataPack.getCurrentBaseBlockPage() - 1)) {
            if (Objects.requireNonNull(clickedInventory.getItem(pSlot)).getType() == backgroundItem.getType()) {
                ItemStack previousPage = new CustomItem(getPluginInstance(), getPluginInstance().getMenusConfig().getString("base-block-menu.previous-page-item.material"),
                        getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.durability"),
                        getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.amount"))
                        .setDisplayName(player, getPluginInstance().getMenusConfig().getString("base-block-menu.previous-page-item.display-name"))
                        .setLore(player, getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.lore"))
                        .setEnchantments(getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.enchantments"))
                        .setItemFlags(getPluginInstance().getMenusConfig().getStringList("base-block-menu.previous-page-item.flags"))
                        .setModelData(getPluginInstance().getMenusConfig().getInt("base-block-menu.previous-page-item.model-data")).get();
                clickedInventory.setItem(pSlot, previousPage);
            }
        } else clickedInventory.setItem(pSlot, backgroundItem);

        for (ItemStack itemStack : dataPack.getBaseBlockPageMap().get(dataPack.getCurrentBaseBlockPage()))
            clickedInventory.addItem(itemStack);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEdit(ShopEditEvent e) {
        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () ->
                getPluginInstance().writeToLog("[" + getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis())) + "] "
                        + e.getPlayer().getName() + " performed the " + WordUtils.capitalize(e.getEditType().name().toLowerCase().replace("_", " "))
                        + " edit action on the shop '" + e.getShop().getShopId().toString() + "' at (World: " + e.getShop().getBaseLocation().getWorldName()
                        + " X: " + e.getShop().getBaseLocation().getX() + " Y: " + e.getShop().getBaseLocation().getY() + " Z: "
                        + e.getShop().getBaseLocation().getZ() + ")."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCreate(ShopCreationEvent e) {
        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () ->
                getPluginInstance().writeToLog("[" + getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis())) + "] "
                        + e.getPlayer().getName() + " created a shop at (World: " + Objects.requireNonNull(e.getLocation().getWorld()).getName()
                        + " X: " + e.getLocation().getBlockX() + " Y: " + e.getLocation().getBlockY() + " Z: " + e.getLocation().getBlockZ() + ")."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDelete(ShopDeletionEvent e) {
        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () ->
                getPluginInstance().writeToLog("[" + getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis())) + "] "
                        + e.getPlayer().getName() + " a shop at (World: " + Objects.requireNonNull(e.getLocation().getWorld()).getName()
                        + " X: " + e.getLocation().getBlockX() + " Y: " + e.getLocation().getBlockY() + " Z: " + e.getLocation().getBlockZ() + ")."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEconomyCall(EconomyCallEvent e) {
        if (e.willSucceed() && e.getInvestor() != null) {
            StringBuilder stringBuilder = new StringBuilder();

            int unitCount;
            switch (e.getEconomyCallType()) {
                case SELL:
                    unitCount = (int) (e.getPrice() / e.getShop().getSellPrice(true));
                    stringBuilder.append("[").append(getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis()))).append("] ")
                            .append(e.getInvestor().getName()).append(" sold to the" +
                                    " shop '").append(e.getShop().getShopId().toString()).append("' (World: ").append(e.getShop().getBaseLocation().getWorldName())
                            .append(" X: ").append(e.getShop().getBaseLocation().getX()).append(" Y: ").append(e.getShop().getBaseLocation().getY()).append(" Z: ").append(e.getShop().getBaseLocation().getZ()).append(") and received '").append(e.getPrice()).append("' currency. (Estimated Unit Count: ").append(getPluginInstance().getManager().formatNumber(unitCount, false)).append(")");
                    break;

                case BUY:
                    unitCount = (int) (e.getPrice() / e.getShop().getBuyPrice(true));
                    stringBuilder.append("[").append(getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis()))).append("] ")
                            .append(e.getInvestor().getName()).append(" purchased " +
                                    "from the shop '").append(e.getShop().getShopId().toString()).append("' (World: ").append(e.getShop().getBaseLocation().getWorldName())
                            .append(" X: ").append(e.getShop().getBaseLocation().getX()).append(" Y: ").append(e.getShop().getBaseLocation().getY())
                            .append(" Z: ").append(e.getShop().getBaseLocation().getZ()).append(") and used '").append(e.getPrice())
                            .append("' currency. (Estimated Unit Count: ").append(getPluginInstance().getManager().formatNumber(unitCount, false)).append(")");
                    break;

                case EDIT_ACTION:
                    unitCount = (int) (e.getPrice() / e.getShop().getBuyPrice(true));
                    stringBuilder.append("[").append(getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis()))).append("] ")
                            .append(e.getInvestor().getName()).append(" edited the " +
                                    "shop '").append(e.getShop().getShopId().toString()).append("' (World: ").append(e.getShop().getBaseLocation().getWorldName())
                            .append(" X: ").append(e.getShop().getBaseLocation().getX()).append(" Y: ").append(e.getShop().getBaseLocation().getY())
                            .append(" Z: ").append(e.getShop().getBaseLocation().getZ()).append(") and used '").append(e.getPrice())
                            .append("' currency. (Estimated Unit Count: ").append(getPluginInstance().getManager().formatNumber(unitCount, false)).append(")");
                    break;

                default:
                    return;
            }

            if (stringBuilder.length() > 0)
                getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(),
                        () -> getPluginInstance().writeToLog(stringBuilder.toString()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInteract(ChatInteractionEvent e) {
        final DataPack dataPack = getPluginInstance().getManager().getDataPack(e.getPlayer());
        if (dataPack != null && dataPack.getCurrentChatTask() != null) dataPack.getCurrentChatTask().cancel();

        ChatInteractionStageEvent chatInteractionStageEvent = new ChatInteractionStageEvent(e.getPlayer(), StageType.START, e.getPlayerEntryValue());
        getPluginInstance().getServer().getPluginManager().callEvent(chatInteractionStageEvent);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        final DataPack dataPack = getPluginInstance().getManager().getDataPack(e.getPlayer());
        if (dataPack.getChatInteractionType() != null) {
            e.setCancelled(true);
            final String message = net.md_5.bungee.api.ChatColor.stripColor(e.getMessage());
            getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () -> {
                ChatInteractionStageEvent stageEvent = new ChatInteractionStageEvent(e.getPlayer(), StageType.ENTRY, message);
                getPluginInstance().getServer().getPluginManager().callEvent(stageEvent);
                if (!getPluginInstance().getManager().initiateChatInteractionOperation(e.getPlayer(), dataPack.getChatInteractionType(), message)) {
                    ChatInteractionStageEvent incorrectStageEvent = new ChatInteractionStageEvent(e.getPlayer(), StageType.INCORRECT, message);
                    getPluginInstance().getServer().getPluginManager().callEvent(incorrectStageEvent);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent e) {

        if (getPluginInstance().getConfig().getBoolean("explosive-protection.check")) {
            e.blockList().stream().parallel().forEach(block -> {
                final Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
                if (shop != null) shop.delete(true);
            }); return;
        }

        if (getPluginInstance().getConfig().getBoolean("explosive-protection.alternative-method")) {
            Optional<Block> blockSearch = e.blockList().stream().parallel().filter(block ->
                    (getPluginInstance().getManager().getShop(block.getLocation()) != null)).findAny();
            blockSearch.ifPresent(block -> e.setCancelled(true));
        } else e.blockList().removeIf(block -> (getPluginInstance().getManager().getShop(block.getLocation()) != null));

        /*for (int i = -1; ++i < e.blockList().size(); ) {
            Block block = e.blockList().get(i);
            Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
            if (shop != null) {
                getPluginInstance().log(Level.WARNING, shop.getShopId() + " was protected from TNT.");
                e.blockList().remove(i);
            }
        }*/
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!getPluginInstance().getConfig().getBoolean("piston-protection.check")) return;
        handlePistons(e, e.getBlocks());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!getPluginInstance().getConfig().getBoolean("piston-protection.check")) return;
        handlePistons(e, e.getBlocks());
    }

    private void handlePistons(BlockPistonEvent e, List<Block> blocks) {
        final boolean altPistonCheck = getPluginInstance().getConfig().getBoolean("piston-protection.alternative-method");
        for (Map.Entry<UUID, Shop> entry : getPluginInstance().getManager().getShopMap().entrySet()) {
            final Shop shop = entry.getValue();
            if (shop == null || shop.getBaseLocation() == null) continue;

            if (altPistonCheck) {
                final boolean yDiff = (e.getBlock().getY() != shop.getBaseLocation().getY()),
                        xDiff = (e.getBlock().getX() != shop.getBaseLocation().getX()),
                        zDiff = (e.getBlock().getZ() != shop.getBaseLocation().getZ());

                final double changeInX = (Math.max(e.getBlock().getX(), shop.getBaseLocation().getX()) - Math.min(e.getBlock().getX(), shop.getBaseLocation().getX())),
                        changeInY = (Math.max(e.getBlock().getY(), shop.getBaseLocation().getY()) - Math.min(e.getBlock().getY(), shop.getBaseLocation().getY())),
                        changeInZ = (Math.max(e.getBlock().getZ(), shop.getBaseLocation().getZ()) - Math.min(e.getBlock().getZ(), shop.getBaseLocation().getZ()));

                if ((!xDiff && zDiff && changeInZ < 12) || (!zDiff && xDiff && changeInX < 12) || (yDiff && changeInY < 12 && !xDiff && !zDiff)) {
                    e.setCancelled(true);
                    break;
                }
            } else {
                if (blocks.stream().parallel().anyMatch(block -> shop.getBaseLocation().isSame(block.getLocation()))) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }


   /* @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() == null || !(e.getDestination().getHolder() instanceof Container)
                || !(e.getSource().getHolder() instanceof Container)) return;

        final Container container = (Container) e.getDestination().getHolder();
        final Container hopper = (Container) e.getDestination().getHolder();

        final Shop shop = getPluginInstance().getManager().getShop(container.getLocation());
        if (shop != null) {
            e.setCancelled(true);
            hopper.getBlock().breakNaturally();
        }
    }*/

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player) || !getPluginInstance().getConfig().getBoolean("shop-creation-item.craftable"))
            return;

        Player player = (Player) e.getWhoClicked();
        if (Objects.requireNonNull(e.getCurrentItem()).getItemMeta() != null && e.getCurrentItem().getItemMeta().hasDisplayName()
                && e.getCurrentItem().getItemMeta().getDisplayName().equals(getPluginInstance().getManager().color(getPluginInstance()
                .getConfig().getString("shop-creation-item.display-name")))) {

            if (!player.hasPermission("displayshops.craft")) {
                e.setCancelled(true);
                String message = getPluginInstance().getLangConfig().getString("no-permission");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                return;
            }

            e.getInventory().setItem(e.getSlot(), getPluginInstance().getManager().buildShopCreationItem(player, 1));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getPluginInstance().getManager().getDataPackMap().containsKey(e.getPlayer().getUniqueId()))
            getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
                DataPack dataPack = getPluginInstance().getManager().loadDataPack(e.getPlayer());
                dataPack.resetEditData();
            });
        else getPluginInstance().getManager().getDataPack(e.getPlayer()).resetEditData();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        final DataPack dataPack = getPluginInstance().getManager().getDataPack(e.getPlayer());
        getPluginInstance().getManager().saveDataPack(null, e.getPlayer().getUniqueId(), dataPack, true, true);
        dataPack.resetEditData();
        getPluginInstance().getDisplayPacketMap().remove(e.getPlayer().getUniqueId());
        getPluginInstance().getShopMemory().remove(e.getPlayer().getUniqueId());
        getPluginInstance().getTeleportingPlayers().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() != null && (e.getTo().getBlockX() != e.getFrom().getBlockX() || e.getTo().getBlockY() != e.getFrom().getBlockY()
                || e.getTo().getBlockZ() != e.getFrom().getBlockZ()))
            if (getPluginInstance().getTeleportingPlayers().contains(e.getPlayer().getUniqueId())) {
                getPluginInstance().getTeleportingPlayers().remove(e.getPlayer().getUniqueId());
                String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();
            if (getPluginInstance().getTeleportingPlayers().contains(victim.getUniqueId())) {
                getPluginInstance().getTeleportingPlayers().remove(victim.getUniqueId());
                String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(victim, message);
            }
        }

        if (e.getDamager() instanceof Player) {
            Player attacker = (Player) e.getDamager();
            if (getPluginInstance().getTeleportingPlayers().contains(attacker.getUniqueId())) {
                getPluginInstance().getTeleportingPlayers().remove(attacker.getUniqueId());
                String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(attacker, message);
            }
        } else if (e.getEntity() instanceof Projectile) {
            Projectile projectile = (Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                Player attacker = (Player) projectile.getShooter();
                if (getPluginInstance().getTeleportingPlayers().contains(attacker.getUniqueId())) {
                    getPluginInstance().getTeleportingPlayers().remove(attacker.getUniqueId());
                    String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
                    if (message != null && !message.equalsIgnoreCase(""))
                        getPluginInstance().getManager().sendMessage(attacker, message);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (getPluginInstance().getTeleportingPlayers().contains(e.getPlayer().getUniqueId())) {
            getPluginInstance().getTeleportingPlayers().remove(e.getPlayer().getUniqueId());
            String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
        }

        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
            getPluginInstance().clearDisplayPackets(e.getPlayer());
            getPluginInstance().killCurrentShopPacket(e.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (getPluginInstance().getTeleportingPlayers().contains(e.getPlayer().getUniqueId())) {
            getPluginInstance().getTeleportingPlayers().remove(e.getPlayer().getUniqueId());
            String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
        }

        getPluginInstance().getInSightTask().refreshShops(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (getPluginInstance().getTeleportingPlayers().contains(e.getPlayer().getUniqueId())) {
            getPluginInstance().getTeleportingPlayers().remove(e.getPlayer().getUniqueId());
            String message = getPluginInstance().getLangConfig().getString("shop-visit-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
        }

        getPluginInstance().getInSightTask().refreshShops(e.getPlayer());
    }

    // helper methods
    private boolean inventoryCheck(Player player, DataPack dataPack) {
        if (dataPack.getSelectedShop() == null) {
            player.closeInventory();
            String message = getPluginInstance().getLangConfig().getString("shop-edit-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return false;
        }

        return true;
    }

    private String getInventoryName(@NotNull Inventory inventory, @NotNull InventoryView inventoryView) {
        if ((Math.floor(this.getPluginInstance().getServerVersion()) >= 1_14)) return inventoryView.getTitle();
        else {
            try {
                Method method = inventory.getClass().getMethod("getTitle");
                return (String) method.invoke(inventory);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private boolean isTooClose(BlockPlaceEvent e, boolean removeItem) {
        if (getPluginInstance().getManager().isTooClose(e.getBlock().getLocation())) {
            String message = getPluginInstance().getLangConfig().getString("too-close");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            return true;
        }

        if (getPluginInstance().getManager().exceededShopLimit(e.getPlayer())) {
            String message = getPluginInstance().getLangConfig().getString("max-shops");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
            return true;
        }

        if (removeItem) {
            if (e.getItemInHand().getAmount() > 1) e.getItemInHand().setAmount(e.getItemInHand().getAmount() - 1);
            else getPluginInstance().getManager().removeItem(e.getPlayer().getInventory(), e.getItemInHand(), 1);
        }

        return false;
    }

    private void setShopItem(Player player, ItemStack handItem, Shop shop) {
        ShopItemSetEvent shopItemSetEvent = new ShopItemSetEvent(player, shop, ItemType.SHOP, handItem);
        getPluginInstance().getServer().getPluginManager().callEvent(shopItemSetEvent);
        if (shopItemSetEvent.isCancelled()) return;

        if (handItem == null || handItem.getType() == Material.AIR) {
            String message = getPluginInstance().getLangConfig().getString("set-item-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (handItem.getAmount() > 1 && getPluginInstance().getConfig().getBoolean("force-single-stack")) {
            final String message = getPluginInstance().getLangConfig().getString("single-item-required");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (isBlockedItem(handItem)) {
            String message = getPluginInstance().getLangConfig().getString("set-item-blocked");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getBlockedItemId(handItem) >= 0) {
            String message = getPluginInstance().getLangConfig().getString("set-item-blocked");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.setShopItem(handItem.clone());
        shop.setShopItemAmount(shop.getShopItem().getAmount());

        final double minBuyPrice = getPluginInstance().getManager().getMaterialMinPrice(handItem, true),
                maxBuyPrice = getPluginInstance().getManager().getMaterialMaxPrice(handItem, true),
                minSellPrice = getPluginInstance().getManager().getMaterialMinPrice(handItem, false),
                maxSellPrice = getPluginInstance().getManager().getMaterialMaxPrice(handItem, false),
                buyPrice = shop.getBuyPrice(false), sellPrice = shop.getSellPrice(false);

        if (buyPrice < minBuyPrice) shop.setBuyPrice(minBuyPrice);
        else if (buyPrice > maxBuyPrice) shop.setBuyPrice(maxBuyPrice);

        if (sellPrice < minSellPrice) shop.setSellPrice(minSellPrice);
        else if (sellPrice > maxSellPrice) shop.setSellPrice(maxSellPrice);

        if (shop.getStock() >= 0) shop.setStock(handItem.getAmount());
        if (Math.floor(getPluginInstance().getServerVersion()) >= 1_9) player.getInventory().setItemInMainHand(null);
        else player.setItemInHand(null);

        String description = "";

        if (getPluginInstance().getConfig().getBoolean("enchantment-description-set") && shop.getShopItem() != null
                && (shop.getShopItem().getEnchantments().size() > 0 || shop.getShopItem().getItemMeta() instanceof EnchantmentStorageMeta))
            description = description + getPluginInstance().getManager().getEnchantmentLine(shop.getShopItem());
        else if (getPluginInstance().getConfig().getBoolean("potion-description-set") && shop.getShopItem() != null
                && shop.getShopItem().getItemMeta() instanceof PotionMeta)
            description = description + getPluginInstance().getManager().getPotionLine(shop.getShopItem());

        shop.setDescription(description);

        final String message = getPluginInstance().getLangConfig().getString("shop-item-set");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message);

        shop.updateTimeStamp();

        getPluginInstance().getInSightTask().refreshShop(shop);
        getPluginInstance().runEventCommands("shop-item-set", player);
    }

    private boolean isBlockedItem(ItemStack itemStack) {
        List<String> materialNames = getPluginInstance().getConfig().getStringList("blocked-item-list");
        for (int i = -1; ++i < materialNames.size(); ) {
            String materialLine = materialNames.get(i);
            if (materialLine.contains(":")) {
                String[] splitMaterialLine = materialLine.split(":");
                if (splitMaterialLine[0].replace(" ", "_").replace("-", "_").equalsIgnoreCase(itemStack.getType().name()) && splitMaterialLine[1].equalsIgnoreCase(String.valueOf(itemStack.getDurability())))
                    return true;
            } else if (materialLine.replace(" ", "_").replace("-", "_").equalsIgnoreCase(itemStack.getType().name()))
                return true;
        }

        return false;
    }

    private void deleteShop(Player player, Shop shop) {
        shop.purge(true);
        if (getPluginInstance().getConfig().getBoolean("creation-item-drop")) {
            if (player.getInventory().firstEmpty() == -1)
                player.getWorld().dropItemNaturally(player.getLocation(), getPluginInstance().getManager().buildShopCreationItem(player, 1));
            else player.getInventory().addItem(getPluginInstance().getManager().buildShopCreationItem(player, 1));
        }

        Location location = shop.getBaseLocation().asBukkitLocation();
        String soundString = getPluginInstance().getConfig().getString("immersion-section.shop-delete-sound");
        if (soundString != null && !soundString.equalsIgnoreCase(""))
            player.playSound(location, Sound.valueOf(soundString), 1, 1);

        String particleString = getPluginInstance().getConfig().getString("immersion-section.shop-delete-particle");
        if (particleString != null && !particleString.equalsIgnoreCase(""))
            getPluginInstance().getPacketManager().getParticleHandler().displayParticle(player, particleString.toUpperCase()
                    .replace(" ", "_").replace("-", "_"), location.add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, 0, 8);

        String message = getPluginInstance().getLangConfig().getString("shop-deleted");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message);

        getPluginInstance().runEventCommands("shop-delete", player);
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

    private void shopCreationWorks(BlockPlaceEvent e, boolean needsHelp) {
        Shop shopCheck = getPluginInstance().getManager().getShop(e.getBlock().getLocation());
        if (shopCheck != null || isTooClose(e, false)) {
            e.setCancelled(true);
            return;
        }

        if (getPluginInstance().getManager().isBlockedMaterial(e.getBlock().getRelative(BlockFace.DOWN).getType())) {
            e.setCancelled(true);
            if (e.getPlayer().getInventory().firstEmpty() == -1)
                e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(),
                        getPluginInstance().getManager().buildShopCreationItem(e.getPlayer(), 1));
            else
                e.getPlayer().getInventory().addItem(getPluginInstance().getManager().buildShopCreationItem(e.getPlayer(), 1));
            return;
        }

        ShopCreationEvent shopCreationEvent = new ShopCreationEvent(e.getPlayer(), e.getBlockPlaced().getLocation());
        getPluginInstance().getServer().getPluginManager().callEvent(shopCreationEvent);
        if (shopCreationEvent.isCancelBlockPlaceEvent()) e.setCancelled(true);
        if (shopCreationEvent.isCancelled()) return;

        if (needsHelp) getPluginInstance().getServer().getScheduler().runTaskLater(getPluginInstance(), () -> {
            final Block block = e.getBlock();
            final ItemStack creationItem = getPluginInstance().getManager().buildShopCreationItem(e.getPlayer(), 1);
            if ((Math.floor(this.getPluginInstance().getServerVersion()) <= 1_10)) {
                block.setType(Material.AIR);
                block.setType(creationItem.getType());
                block.setBlockData(getPluginInstance().getServer().createBlockData(creationItem.getType()));
                setBlock(block, creationItem.getType(), (creationItem.getType().name().contains("SHULKER")
                        ? BlockFace.UP : BlockFace.valueOf(Direction.getYaw(e.getPlayer()).name())));

                final BlockState blockState = block.getRelative(BlockFace.UP).getState();
                blockState.setType(Material.AIR);
                blockState.update(true, false);
            } else {
                block.setType(Material.AIR);
                block.setType(creationItem.getType());

                if (block instanceof Directional) try {
                    Method method = Block.class.getMethod("setData", Byte.class, Boolean.class);
                    method.invoke(block, oppositeDirectionByte(Direction.getYaw(e.getPlayer())), true);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                }
                else try {
                    Method method = Block.class.getMethod("setData", Byte.class, Boolean.class);
                    method.invoke(block, (byte) creationItem.getDurability(), true);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                }

                final BlockState blockState = block.getRelative(BlockFace.UP).getState();
                blockState.setType(Material.AIR);
                blockState.update(true, false);
            }
        }, 10);
        else e.getBlock().getRelative(BlockFace.UP).breakNaturally();

        getPluginInstance().getManager().createShop(e.getPlayer(), e.getBlock(), 1, true, true);
    }

    private void runEconomyCall(Player player, Shop shop, EconomyCallType economyCallType, int unitCount) {
        OfflinePlayer owner = null;
        if (shop.getOwnerUniqueId() != null)
            owner = getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId());

        final boolean useVault = (getPluginInstance().getConfig().getBoolean("use-vault") && getPluginInstance().getVaultEconomy() != null),
                syncOwner = getPluginInstance().getConfig().getBoolean("sync-owner-balance");
        final double totalShopBalance = (shop.getStoredBalance() + (unitCount * shop.getSellPrice(true)));

        if (economyCallType == EconomyCallType.SELL && !syncOwner && totalShopBalance >= getPluginInstance().getConfig().getLong("max-stored-currency")) {
            final String message = getPluginInstance().getLangConfig().getString("max-stored-currency");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        boolean canBypassEconomy = player.hasPermission("displayshops.bypass");
        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, owner, shop, economyCallType,
                (economyCallType == EconomyCallType.BUY ? (shop.getBuyPrice(true) * unitCount) : (shop.getSellPrice(true) * unitCount)));
        if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
            player.closeInventory();
            return;
        }

        final boolean isInfinite = (shop.isAdminShop() && shop.getStock() <= -1);
        if (economyCallEvent.getEconomyCallType() == EconomyCallType.SELL) {
            final int maxStock = getPluginInstance().getManager().getMaxStock(shop);
            int amountToRemove = (shop.getShopItemAmount() * unitCount);
            if (!isInfinite && ((shop.getStock() + amountToRemove) > maxStock))
                amountToRemove = (amountToRemove - ((shop.getStock() + amountToRemove) - maxStock));

            getPluginInstance().getManager().removeItem(player.getInventory(), shop.getShopItem(), amountToRemove);
            DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
            dataPack.updateCurrentTransactionLimitCounter(shop, false, dataPack.getCurrentTransactionCounter(shop, false) + 1);

            if (!isInfinite) shop.setStock(shop.getStock() + amountToRemove);
            if (shop.getSellLimit() > 0) shop.setSellCounter(shop.getSellCounter() + unitCount);

        } else {
            if (!isInfinite) shop.setStock(shop.getStock() - (shop.getShopItemAmount() * unitCount));
            if (shop.getBuyLimit() > 0) shop.setBuyCounter(shop.getBuyCounter() + unitCount);

            DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
            dataPack.updateCurrentTransactionLimitCounter(shop, true, dataPack.getCurrentTransactionCounter(shop, true) + 1);

            if (!shop.isCommandOnlyMode() && shop.getShopItem() != null)
                getPluginInstance().getManager().giveItemStacks(player, shop.getShopItem(), (shop.getShopItemAmount() * unitCount));
        }

        if (shop.canDynamicPriceChange() && getPluginInstance().getConfig().getBoolean("dynamic-prices")) {
            shop.updateTransactionTimeStamp(economyCallType);
            if (economyCallType == EconomyCallType.SELL) shop.setDynamicSellCounter(shop.getDynamicSellCounter() + 1);
            else shop.setDynamicBuyCounter(shop.getDynamicBuyCounter() + 1);
        }

        getPluginInstance().getInSightTask().refreshShop(shop);
        player.updateInventory();
        if (getPluginInstance().getConfig().getBoolean("close-transaction-gui")) player.closeInventory();
        shop.runCommands(player, (shop.getShopItemAmount() * unitCount));

        String tradeItemName = "";
        if (!useVault) {
            final boolean forceUseCurrency = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");
            final ItemStack forceCurrencyItem = getPluginInstance().getManager().buildShopCurrencyItem(1);
            final String defaultName = getPluginInstance().getManager().getItemName(forceCurrencyItem);
            tradeItemName = forceUseCurrency ? forceCurrencyItem != null ? defaultName : ""
                    : shop.getTradeItem() != null ? getPluginInstance().getManager().getItemName(shop.getTradeItem()) : defaultName;
        }


        if (economyCallType == EconomyCallType.BUY) {
            String message = !canBypassEconomy ? getPluginInstance().getLangConfig().getString("shop-buy")
                    : getPluginInstance().getLangConfig().getString("shop-buy-bypass");
            if (message != null && !message.equalsIgnoreCase("")) {
                String ownerName = shop.getOwnerUniqueId() == null ? "" : (owner == null ? "" : owner.getName());
                getPluginInstance().getManager().sendMessage(player,
                        message.replace("{item}", getPluginInstance().getManager().getItemName(shop.getShopItem()))
                                .replace("{amount}", getPluginInstance().getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                                .replace("{trade-item}", tradeItemName).replace("{owner}", ownerName == null ? "" : ownerName)
                                .replace("{price}", getPluginInstance().getManager().formatNumber(economyCallEvent.getTaxedPrice(), true)));
            }

            if (owner != null && owner.isOnline()) {
                final DataPack ownerDP = getPluginInstance().getManager().getDataPack(owner.getPlayer());
                if (ownerDP.isTransactionNotify()) {
                    message = getPluginInstance().getLangConfig().getString("shop-buy-owner");
                    if (message != null && !message.equalsIgnoreCase("")) {
                        getPluginInstance().getManager().sendMessage(owner.getPlayer(),
                                message.replace("{item}", getPluginInstance().getManager().getItemName(shop.getShopItem()))
                                        .replace("{amount}", getPluginInstance().getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                                        .replace("{trade-item}", tradeItemName).replace("{buyer}", player.getName())
                                        .replace("{price}", getPluginInstance().getManager().formatNumber(economyCallEvent.getPrice(), true)));
                    }
                }
            }
            getPluginInstance().runEventCommands("shop-buy", player);
        } else {
            String message = getPluginInstance().getLangConfig().getString("shop-sell");
            if (message != null && !message.equalsIgnoreCase("")) {
                String ownerName = shop.getOwnerUniqueId() == null ? "" : (owner == null ? "" : owner.getName());
                getPluginInstance().getManager().sendMessage(player, message.replace("{item}", (shop.getShopItem().hasItemMeta()
                                && shop.getShopItem().getItemMeta() != null && shop.getShopItem().getItemMeta().hasDisplayName())
                                ? shop.getShopItem().getItemMeta().getDisplayName() : WordUtils.capitalize(shop.getShopItem().getType().name().toLowerCase()
                                .replace("_", " "))).replace("{trade-item}", tradeItemName)
                        .replace("{amount}", getPluginInstance().getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                        .replace("{owner}", ownerName == null ? "" : ownerName)
                        .replace("{price}", getPluginInstance().getManager().formatNumber(economyCallEvent.getPrice(), true)));
            }

            if (owner != null && owner.isOnline()) {
                final DataPack ownerDP = getPluginInstance().getManager().getDataPack(owner.getPlayer());
                if (ownerDP.isTransactionNotify()) {
                    message = getPluginInstance().getLangConfig().getString("shop-sell-owner");
                    if (message != null && !message.equalsIgnoreCase("")) {
                        getPluginInstance().getManager().sendMessage(owner.getPlayer(),
                                message.replace("{item}", (shop.getShopItem().hasItemMeta()
                                                && shop.getShopItem().getItemMeta() != null && shop.getShopItem().getItemMeta().hasDisplayName())
                                                ? shop.getShopItem().getItemMeta().getDisplayName() : WordUtils.capitalize(shop.getShopItem().getType().name().toLowerCase()
                                                .replace("_", " "))).replace("{trade-item}", tradeItemName)
                                        .replace("{amount}", getPluginInstance().getManager().formatNumber(shop.getShopItemAmount() * unitCount, false))
                                        .replace("{buyer}", player.getName())
                                        .replace("{price}", getPluginInstance().getManager().formatNumber(economyCallEvent.getPrice(), true)));
                    }
                }
            }
            getPluginInstance().runEventCommands("shop-sell", player);
        }
    }

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

}