/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import xzot1k.plugins.ds.DisplayShops;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CustomItem {

    private final DisplayShops pluginInstance;
    boolean isNew, papiHere;
    private ItemStack itemStack;
    private Shop shop;
    private int unitCount, unitItemMaxStack;

    public CustomItem(DisplayShops pluginInstance, String materialName, int durability, int amount) {
        this.pluginInstance = pluginInstance;
        constructorHelper(materialName, durability, amount);
    }

    public CustomItem(DisplayShops pluginInstance, String materialName, int durability, int amount, Shop shop, int unitItemMaxStack, int unitCount) {
        this.pluginInstance = pluginInstance;
        this.shop = shop;
        this.unitCount = unitCount;
        this.unitItemMaxStack = unitItemMaxStack;
        constructorHelper(materialName, durability, amount);
    }

    private void constructorHelper(String materialName, int durability, int amount) {
        this.isNew = (Math.floor(getPluginInstance().getServerVersion()) > 1_12);
        this.papiHere = (getPluginInstance().getPapiHelper() != null);

        if (materialName == null || materialName.isEmpty()) {
            itemStack = new ItemStack(Material.STONE);
            return;
        }

        String replacedMaterial = materialName.toUpperCase().replace(" ", "_").replace("-", "_");
        if (materialName.toUpperCase().startsWith("HEAD") && materialName.contains(":")) {
            final String[] materialArgs = replacedMaterial.split(":");
            if (getPluginInstance().getHeadDatabaseAPI() != null && !getPluginInstance().getManager().isNotNumeric(materialArgs[1])) {
                itemStack = getPluginInstance().getHeadDatabaseAPI().getItemHead(materialArgs[1]);
                itemStack.setAmount(Math.max(1, Math.min(amount, itemStack.getType().getMaxStackSize())));
            } else {
                itemStack = isNew ? new ItemStack(Objects.requireNonNull(Material.getMaterial("PLAYER_HEAD")), 1)
                        : new ItemStack(Objects.requireNonNull(Material.getMaterial("SKULL_ITEM")),
                        Math.max(1, Math.min(amount, itemStack.getType().getMaxStackSize())), (short) 3);
                SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
                if (skullMeta != null && materialArgs[1] != null && !materialArgs[1].equalsIgnoreCase("")) {
                    if (isNew()) {
                        OfflinePlayer player = getPluginInstance().getServer().getOfflinePlayer(materialArgs[1]);
                        skullMeta.setOwningPlayer(player);
                    } else skullMeta.setOwner(materialArgs[1]);
                    itemStack.setItemMeta(skullMeta);
                }
            }
        } else {
            Material material = Material.getMaterial(replacedMaterial);
            if (material == null) {

                if (getPluginInstance().isItemAdderInstalled()) {
                    dev.lone.itemsadder.api.CustomStack customStack = dev.lone.itemsadder.api.CustomStack.getInstance(materialName);
                    if (customStack != null) itemStack = customStack.getItemStack();
                }

                if(itemStack == null) itemStack = new ItemStack(Material.STONE);
            } else itemStack = new ItemStack(material, Math.min(amount, material.getMaxStackSize()), (short) durability);
        }
    }

    public CustomItem setDisplayName(Player player, String displayName) {
        ItemMeta itemMeta = get().getItemMeta();
        if (displayName != null && !displayName.isEmpty()) {
            displayName = (papiHere && player != null) ? getPluginInstance().getPapiHelper().replace(player, displayName) : displayName;
            itemMeta.setDisplayName(getPluginInstance().getManager().color(displayName));
            get().setItemMeta(itemMeta);
        }
        return this;
    }

    public CustomItem setLore(Player player, String... lines) {
        ItemMeta itemMeta = get().getItemMeta();
        itemMeta.setLore(new ArrayList<String>() {{
            final boolean useVault = getPluginInstance().getConfig().getBoolean("use-vault");
            final double tax = getPluginInstance().getConfig().getDouble("transaction-tax");
            final int unitIncrement = ((int) (unitItemMaxStack * 0.25));

            if (getShop() != null) {
                String tradeItemName = "";
                if (!useVault) {
                    final boolean forceUseCurrency = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");
                    final ItemStack forceCurrencyItem = getPluginInstance().getManager().buildShopCurrencyItem(1);
                    final String defaultName = getPluginInstance().getManager().getItemName(forceCurrencyItem);
                    tradeItemName = !useVault ? (forceUseCurrency ? (forceCurrencyItem != null ? defaultName : "")
                            : (shop.getTradeItem() != null ? getPluginInstance().getManager().getItemName(shop.getTradeItem()) : defaultName)) : "";
                }

                final double beforeBuyPrice = (shop.getBuyPrice(true) * unitCount),
                        calculatedBuyPrice = (beforeBuyPrice + (beforeBuyPrice * tax)),
                        calculatedSellPrice = (shop.getSellPrice(true) * unitCount);

                for (String line : lines) {
                    line = papiHere ? getPluginInstance().getPapiHelper().replace(player, line) : line;
                    if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}")))
                        add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")
                                .replace("{buy-price}", getPluginInstance().getManager().formatNumber(calculatedBuyPrice, true))
                                .replace("{sell-price}", getPluginInstance().getManager().formatNumber(calculatedSellPrice, true))
                                .replace("{stock}", getPluginInstance().getManager().formatNumber(shop.getStock(), false))
                                .replace("{buy-limit}", getPluginInstance().getManager().formatNumber(shop.getBuyLimit(), false))
                                .replace("{buy-counter}", getPluginInstance().getManager().formatNumber(shop.getBuyCounter(), false))
                                .replace("{sell-limit}", getPluginInstance().getManager().formatNumber(shop.getSellLimit(), false))
                                .replace("{sell-counter}", getPluginInstance().getManager().formatNumber(shop.getSellCounter(), false))
                                .replace("{unit-increment}", String.valueOf(Math.max(unitIncrement, 1)))
                                .replace("{trade-item}", tradeItemName).replace("{balance}", getPluginInstance().getManager().formatNumber(shop.getStoredBalance(), true))
                                .replace("{unit-count}", shop.getShopItem() != null ? getPluginInstance().getManager().formatNumber(unitCount, false) : String.valueOf(0))));
                }
            } else for (String line : lines)
                if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}")))
                    add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")));
        }});
        get().setItemMeta(itemMeta);
        return this;
    }

    public CustomItem setLore(Player player, List<String> lines) {
        ItemMeta itemMeta = get().getItemMeta();
        itemMeta.setLore(new ArrayList<String>() {{
            final boolean useVault = getPluginInstance().getConfig().getBoolean("use-vault");
            final double tax = getPluginInstance().getConfig().getDouble("transaction-tax");
            final int unitIncrement = ((int) (unitItemMaxStack * 0.25));

            if (getShop() != null) {
                String tradeItemName = "";
                if (!useVault) {
                    final boolean forceUseCurrency = getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");
                    final ItemStack forceCurrencyItem = getPluginInstance().getManager().buildShopCurrencyItem(1);
                    final String defaultName = getPluginInstance().getManager().getItemName(forceCurrencyItem);
                    tradeItemName = !useVault ? (forceUseCurrency ? (forceCurrencyItem != null ? defaultName : "")
                            : (shop.getTradeItem() != null ? getPluginInstance().getManager().getItemName(shop.getTradeItem()) : defaultName)) : "";
                }

                final double beforeBuyPrice = (shop.getBuyPrice(true) * unitCount),
                        calculatedBuyPrice = (beforeBuyPrice + (beforeBuyPrice * tax)),
                        calculatedSellPrice = (shop.getSellPrice(true) * unitCount);

                for (String line : lines) {
                    line = (papiHere && player != null) ? getPluginInstance().getPapiHelper().replace(player, line) : line;
                    if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}")))
                        add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")
                                .replace("{buy-price}", getPluginInstance().getManager().formatNumber(calculatedBuyPrice, true))
                                .replace("{sell-price}", getPluginInstance().getManager().formatNumber(calculatedSellPrice, true))
                                .replace("{stock}", getPluginInstance().getManager().formatNumber(shop.getStock(), false))
                                .replace("{buy-limit}", getPluginInstance().getManager().formatNumber(shop.getBuyLimit(), false))
                                .replace("{buy-counter}", getPluginInstance().getManager().formatNumber(shop.getBuyCounter(), false))
                                .replace("{sell-limit}", getPluginInstance().getManager().formatNumber(shop.getSellLimit(), false))
                                .replace("{sell-counter}", getPluginInstance().getManager().formatNumber(shop.getSellCounter(), false))
                                .replace("{unit-increment}", String.valueOf(Math.max(unitIncrement, 1)))
                                .replace("{trade-item}", tradeItemName).replace("{balance}", getPluginInstance().getManager().formatNumber(shop.getStoredBalance(), true))
                                .replace("{unit-count}", shop.getShopItem() != null ? getPluginInstance().getManager().formatNumber(unitCount, false) : String.valueOf(0))));
                }
            } else for (String line : lines)
                if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}")))
                    add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")));
        }});
        get().setItemMeta(itemMeta);
        return this;
    }

    public CustomItem setEnchanted(boolean enchanted) {
        ItemMeta itemMeta = get().getItemMeta();
        if (itemMeta != null) {
            if (!enchanted) {
                itemMeta.getItemFlags().add(ItemFlag.HIDE_ENCHANTS);
                itemMeta.addEnchant(Enchantment.DURABILITY, 10, true);
            } else {
                itemMeta.getItemFlags().remove(ItemFlag.HIDE_ENCHANTS);
                itemMeta.removeEnchant(Enchantment.DURABILITY);
            }
        }

        return this;
    }

    public CustomItem setEnchantments(List<String> enchants) {
        ItemMeta itemMeta = get().getItemMeta();
        if (itemMeta != null && !enchants.isEmpty()) {
            for (int i = -1; ++i < enchants.size(); ) {
                final String line = enchants.get(i);
                if (line.contains(":")) {
                    final String[] enchantLine = line.split(":");
                    Enchantment enchantment = Enchantment.getByName(enchantLine[0]);
                    if (enchantment == null || getPluginInstance().getManager().isNotNumeric(enchantLine[1])) continue;
                    itemMeta.addEnchant(enchantment, Integer.parseInt(enchantLine[1]), true);
                } else {
                    Enchantment enchantment = Enchantment.getByName(line);
                    if (enchantment == null) continue;
                    itemMeta.addEnchant(enchantment, 0, true);
                }
                get().setItemMeta(itemMeta);
            }
        }
        return this;
    }

    public CustomItem setItemFlags(List<String> itemFlags) {
        ItemMeta itemMeta = get().getItemMeta();
        if (itemMeta != null && itemFlags != null && !itemFlags.isEmpty()) {
            for (int i = -1; ++i < itemFlags.size(); ) {
                final String line = itemFlags.get(i);
                try {
                    ItemFlag itemFlag = ItemFlag.valueOf(line);
                    itemMeta.addItemFlags(itemFlag);
                } catch (Exception e) {e.printStackTrace();}
            }
            get().setItemMeta(itemMeta);
        }
        return this;
    }

    public CustomItem setModelData(int modelData) {
        ItemMeta itemMeta = get().getItemMeta();
        if (modelData > 0 && isNew()) {
            itemMeta.setCustomModelData(modelData);
            get().setItemMeta(itemMeta);
        }
        return this;
    }

    private boolean isNew() {
        return isNew;
    }

    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    public ItemStack get() {
        return itemStack;
    }

    private Shop getShop() {
        return shop;
    }
}
