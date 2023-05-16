/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.Direction;
import xzot1k.plugins.ds.api.enums.EditType;
import xzot1k.plugins.ds.api.enums.ItemType;
import xzot1k.plugins.ds.api.events.*;
import xzot1k.plugins.ds.api.objects.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Listeners implements Listener {

    private DisplayShops pluginInstance;
    public ItemStack creationItem;

    public boolean isPistonCheck() {
        return pistonCheck;
    }

    public void setPistonCheck(boolean pistonCheck) {
        this.pistonCheck = pistonCheck;
    }

    private boolean pistonCheck;

    public Listeners(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
        creationItem = getPluginInstance().getManager().buildShopCreationItem(null, 1);
        setPistonCheck(getPluginInstance().getConfig().getBoolean("piston-protection.check"));
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

            if (e.getClickedBlock() != null) {
                final String blockType = e.getClickedBlock().getType().name();

                if (getPluginInstance().getServerVersion() > 1_13) {
                    if (e.getClickedBlock() instanceof org.bukkit.block.Container) {
                        getPluginInstance().getServer().getScheduler().runTaskLater(getPluginInstance(), () -> {
                            e.getPlayer().closeInventory();
                            e.getPlayer().getOpenInventory().close();
                        }, 1);
                    }
                } else if (blockType.contains("CHEST") || blockType.contains("DISPENSER")
                        || blockType.contains("DROPPER") || blockType.contains("HOPPER")
                        || blockType.contains("FURNACE") || blockType.contains("BREWING_STAND")
                        || blockType.contains("JUKEBOX") || blockType.contains("BEACON")) {
                    getPluginInstance().getServer().getScheduler().runTaskLater(getPluginInstance(), () -> {
                        e.getPlayer().closeInventory();
                        e.getPlayer().getOpenInventory().close();
                    }, 1);
                }
            }

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

                    if (editPrevention) shop.setCurrentEditor(e.getPlayer().getUniqueId());
                    dataPack.setSelectedShop(shop);

                    final Menu editMenu = getPluginInstance().getMenu("edit");
                    if (editMenu != null) e.getPlayer().openInventory(editMenu.build(e.getPlayer()));

                    getPluginInstance().runEventCommands("shop-edit", e.getPlayer());
                    return;
                }

                ShopTransactionEvent shopTransactionEvent = new ShopTransactionEvent(e.getPlayer(), shop);
                getPluginInstance().getServer().getPluginManager().callEvent(shopTransactionEvent);
                if (shopTransactionEvent.isCancelled()) return;

                dataPack.setSelectedShop(shop);

                final Menu editMenu = getPluginInstance().getMenu("transaction");
                if (editMenu != null) e.getPlayer().openInventory(editMenu.build(e.getPlayer()));

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

                    if (!e.getPlayer().hasPermission("displayshops.admin") && shop.getOwnerUniqueId() != null
                            && !shop.getOwnerUniqueId().toString().equals(e.getPlayer().getUniqueId().toString())) {
                        dataPack.resetEditData();
                        String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
                        if (message != null && !message.equalsIgnoreCase(""))
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message);
                        return;
                    }

                    shop.purge(e.getPlayer(), true);
                    return;
                }

                ItemStack itemInHand;
                if (isOffhandVersion) itemInHand = e.getPlayer().getInventory().getItemInMainHand();
                else itemInHand = e.getPlayer().getItemInHand();

                if (getPluginInstance().getManager().isSimilar(itemInHand, shop.getShopItem())) {
                    ShopEditEvent shopEditEvent = new ShopEditEvent(e.getPlayer(), shop, EditType.QUICK_DEPOSIT);
                    getPluginInstance().getServer().getPluginManager().callEvent(shopEditEvent);
                    if (shopEditEvent.isCancelled()) return;

                    final int maxStock = getPluginInstance().getManager().getMaxStock(shop), newTotalStock = (shop.getStock() + itemInHand.getAmount()), remainderInHand = (newTotalStock - maxStock);

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
                            getPluginInstance().getManager().sendMessage(e.getPlayer(), message.replace("{amount}", getPluginInstance().getManager().formatNumber(itemInHand.getAmount(), false)));
                        shop.setStock(newTotalStock);
                        if (remainderInHand <= 0) {
                            if (isOffhandVersion) e.getPlayer().getInventory().setItemInMainHand(null);
                            else e.getPlayer().setItemInHand(null);
                        } else itemInHand.setAmount(remainderInHand);
                    }

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

                final Menu editMenu = getPluginInstance().getMenu("edit");
                if (editMenu != null) e.getPlayer().openInventory(editMenu.build(e.getPlayer()));

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

                final Menu editMenu = getPluginInstance().getMenu("transaction");
                if (editMenu != null) e.getPlayer().openInventory(editMenu.build(e.getPlayer()));

                getPluginInstance().runEventCommands("shop-open", e.getPlayer());
            }
        }
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
    public void onExplode(EntityExplodeEvent e) {
        for (int i = -1; ++i < e.blockList().size(); ) {
            Block block = e.blockList().get(i);
            Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
            if (shop != null) e.blockList().remove(i);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!isPistonCheck()) return;

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
                if (e.getBlocks().removeIf(block -> shop.getBaseLocation().isSameNormal(block.getLocation()))) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!isPistonCheck()) return;

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
                for (int i = -1; ++i < e.getBlocks().size(); ) {
                    final Block block = e.getBlocks().get(i);
                    if (shop.getBaseLocation().isSameNormal(block.getLocation())) {
                        e.setCancelled(true);
                        break;
                    }
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

    private void setShopItem(@NotNull Player player, @NotNull ItemStack handItem, @NotNull Shop shop) {
        ShopItemSetEvent shopItemSetEvent = new ShopItemSetEvent(player, shop, ItemType.SHOP, handItem);
        getPluginInstance().getServer().getPluginManager().callEvent(shopItemSetEvent);
        if (shopItemSetEvent.isCancelled()) return;

        if (handItem.getType() == Material.AIR) {
            String message = getPluginInstance().getLangConfig().getString("set-item-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        ItemStack handItemClone = handItem.clone();

        boolean forceSingleStack = getPluginInstance().getConfig().getBoolean("force-single-stack");
        if (forceSingleStack) handItemClone.setAmount(1);

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

        shop.setShopItem(handItemClone);
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

        if (shop.getStock() >= 0) shop.setStock(shop.getShopItem().getAmount());

        if (forceSingleStack && handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
            if (Math.floor(getPluginInstance().getServerVersion()) >= 1_9) player.getInventory().setItemInMainHand(handItem);
            else player.setItemInHand(handItem);
        } else {
            if (Math.floor(getPluginInstance().getServerVersion()) >= 1_9) player.getInventory().setItemInMainHand(null);
            else player.setItemInHand(null);
        }

        player.updateInventory();

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


        getPluginInstance().getInSightTask().refreshShop(shop);
        getPluginInstance().runEventCommands("shop-item-set", player);
    }

    public boolean isBlockedItem(ItemStack itemStack) {
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

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

}