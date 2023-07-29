/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;

import java.lang.reflect.Field;
import java.util.*;

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

    public CustomItem(String materialName, int amount) {
        this.pluginInstance = DisplayShops.getPluginInstance();
        constructorHelper(materialName, 0, amount);
    }

    public CustomItem(String materialName, int durability, int amount) {
        this.pluginInstance = DisplayShops.getPluginInstance();
        constructorHelper(materialName, durability, amount);
    }

    public CustomItem(String materialName, int durability, int amount, Shop shop, int unitItemMaxStack, int unitCount) {
        this.pluginInstance = DisplayShops.getPluginInstance();
        this.shop = shop;
        this.unitCount = unitCount;
        this.unitItemMaxStack = unitItemMaxStack;
        constructorHelper(materialName, durability, amount);
    }

    public CustomItem(ItemStack itemStack, Shop shop, int unitItemMaxStack, int unitCount) {
        this.pluginInstance = DisplayShops.getPluginInstance();
        this.isNew = (Math.floor(getPluginInstance().getServerVersion()) > 1_12);
        this.papiHere = (getPluginInstance().getPapiHelper() != null);
        this.itemStack = itemStack;
        this.shop = shop;
        this.unitCount = unitCount;
        this.unitItemMaxStack = unitItemMaxStack;
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
        } else if (materialName.toUpperCase().startsWith("TEXTURE") && materialName.contains(":")) {

            final String[] materialArgs = replacedMaterial.split(":");

            itemStack = isNew ? new ItemStack(Objects.requireNonNull(Material.getMaterial("PLAYER_HEAD")), 1)
                    : new ItemStack(Objects.requireNonNull(Material.getMaterial("SKULL_ITEM")),
                    Math.max(1, Math.min(amount, itemStack.getType().getMaxStackSize())), (short) 3);

            SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
            if (skullMeta != null && materialArgs[1] != null && !materialArgs[1].equalsIgnoreCase("")) {

                GameProfile profile = new GameProfile(UUID.randomUUID(), "");
                profile.getProperties().put("textures", new Property("textures", materialArgs[1]));

                Field profileField;
                try {
                    profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile);
                } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
                    e.printStackTrace();
                }

                itemStack.setItemMeta(skullMeta);
            }

        } else {

            String itemMat;
            if (materialName.contains(":")) {
                final String[] materialArgs = materialName.split(":");

                if (materialArgs.length > 2) itemMat = (materialArgs[0] + ":" + materialArgs[1]);
                else itemMat = materialArgs[0];

            } else itemMat = materialName;

            Material material = Material.getMaterial(itemMat);
            if (material == null) {

                if (getPluginInstance().isItemAdderInstalled()) {
                    if (dev.lone.itemsadder.api.CustomStack.isInRegistry(itemMat)) {
                        dev.lone.itemsadder.api.CustomStack customStack = dev.lone.itemsadder.api.CustomStack.getInstance(itemMat);
                        if (customStack != null) itemStack = customStack.getItemStack();
                    } else itemStack = new ItemStack(Material.STONE);
                }

            } else itemStack = new ItemStack(material, Math.min(amount, material.getMaxStackSize()), (short) durability);
        }
    }

    public CustomItem setDisplayName(Player player, String displayName) {
        ItemMeta itemMeta = get().getItemMeta();
        if (itemMeta != null && displayName != null && !displayName.isEmpty()) {
            displayName = (papiHere && player != null) ? getPluginInstance().getPapiHelper().replace(player, displayName) : displayName;
            itemMeta.setDisplayName(getPluginInstance().getManager().color(displayName));
            get().setItemMeta(itemMeta);
        }
        return this;
    }

    public CustomItem setDisplayName(@Nullable Player player, @Nullable Shop shop, @NotNull String displayName) {

        final String currencySymbol = getPluginInstance().getConfig().getString("currency-symbol");
        final double tax = getPluginInstance().getConfig().getDouble("transaction-tax"),
                beforeBuyPrice = (shop != null ? (shop.getBuyPrice(true) * unitCount) : 0),
                calculatedSellPrice = (shop != null ? (shop.getSellPrice(true) * unitCount) : 0),
                calculatedBuyPrice = (beforeBuyPrice + (beforeBuyPrice * tax));

        final String newName = displayName
                .replace("{assistant-count}", String.valueOf((shop != null ? shop.getAssistants().size() : 0)))
                .replace("{currency-symbol}", (currencySymbol != null ? currencySymbol : ""))
                .replace("{base-buy-price}", getPluginInstance().getManager()
                        .formatNumber((shop != null ? (shop.getBuyPrice(false) * unitCount) : 0), true))
                .replace("{base-sell-price}", getPluginInstance().getManager()
                        .formatNumber((shop != null ? (shop.getSellPrice(false) * unitCount) : 0), true))
                .replace("{buy-price}", getPluginInstance().getManager().formatNumber(calculatedBuyPrice, true))
                .replace("{sell-price}", getPluginInstance().getManager().formatNumber(calculatedSellPrice, true))
                .replace("{stock}", ((shop != null) ? getPluginInstance().getManager().formatNumber(shop.getStock(), false) : "0"))
                .replace("{balance}", (shop != null ? getPluginInstance().getManager().formatNumber(shop.getStoredBalance(), true) : "0"))
                .replace("{shop-item-amount}", ((shop != null && shop.getShopItem() != null) ? getPluginInstance().getManager()
                        .formatNumber(shop.getShopItemAmount(), false) : "0"))
                .replace("{unit-count}", ((shop != null && shop.getShopItem() != null)
                        ? getPluginInstance().getManager().formatNumber(unitCount, false) : "0"))
                .replace("{item-count}", ((shop != null && shop.getShopItem() != null) ?
                        getPluginInstance().getManager().formatNumber((shop.getShopItemAmount() * unitCount), false) : "0"));

        setDisplayName(player, newName);
        return this;
    }

    public CustomItem setLore(Player player, String... lines) {
        ItemMeta itemMeta = get().getItemMeta();

        if (itemMeta != null) {
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
                        tradeItemName = (forceUseCurrency ? defaultName : shop.getTradeItem() != null ?
                                getPluginInstance().getManager().getItemName(shop.getTradeItem()) : defaultName);
                    }

                    final double beforeBuyPrice = (shop.getBuyPrice(true) * unitCount),
                            calculatedBuyPrice = (beforeBuyPrice + (beforeBuyPrice * tax)),
                            calculatedSellPrice = (shop.getSellPrice(true) * unitCount);

                    for (String line : lines) {
                        line = papiHere ? getPluginInstance().getPapiHelper().replace(player, line) : line;
                        if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}"))) {
                            if (line.contains("{description}")) {
                                if (shop.getDescription() == null || shop.getDescription().isEmpty()) continue;

                                final int wordCount = getPluginInstance().getConfig().getInt("description-word-line-limit");
                                List<String> descriptionLines = getPluginInstance().getManager().wrapString(shop.getDescription(), wordCount);
                                Collections.reverse(descriptionLines);

                                for (int j = -1; ++j < descriptionLines.size(); ) {
                                    final String descLine = descriptionLines.get(j);
                                    add(getPluginInstance().getManager().color(descLine));
                                }

                                if (!descriptionLines.isEmpty()) add("");
                                continue;
                            }

                            add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")
                                    .replace("{assistant-count}", String.valueOf((shop != null ? shop.getAssistants().size() : 0)))
                                    .replace("{base-buy-price}", getPluginInstance().getManager()
                                            .formatNumber((shop != null ? (shop.getBuyPrice(false) * unitCount) : 0), true))
                                    .replace("{base-sell-price}", getPluginInstance().getManager()
                                            .formatNumber((shop != null ? (shop.getSellPrice(false) * unitCount) : 0), true))
                                    .replace("{buy-price}", getPluginInstance().getManager().formatNumber(calculatedBuyPrice, true))
                                    .replace("{sell-price}", getPluginInstance().getManager().formatNumber(calculatedSellPrice, true))
                                    .replace("{stock}", getPluginInstance().getManager().formatNumber(shop.getStock(), false))
                                    .replace("{global-buy-limit}", getPluginInstance().getManager().formatNumber(shop.getGlobalBuyLimit(), false))
                                    .replace("{global-buy-counter}", getPluginInstance().getManager().formatNumber(shop.getGlobalBuyCounter(), false))
                                    .replace("{global-sell-limit}", getPluginInstance().getManager().formatNumber(shop.getGlobalSellLimit(), false))
                                    .replace("{global-sell-counter}", getPluginInstance().getManager().formatNumber(shop.getGlobalSellCounter(), false))
                                    .replace("{player-buy-limit}", getPluginInstance().getManager().formatNumber(shop.getPlayerBuyLimit(), false))
                                    .replace("{player-sell-limit}", getPluginInstance().getManager().formatNumber(shop.getPlayerSellLimit(), false))
                                    .replace("{unit-increment}", String.valueOf(Math.max(unitIncrement, 1)))
                                    .replace("{trade-item}", tradeItemName).replace("{balance}",
                                            getPluginInstance().getManager().formatNumber(shop.getStoredBalance(), true))
                                    .replace("{shop-item-amount}", (shop.getShopItem() != null ? getPluginInstance().getManager()
                                            .formatNumber(shop.getShopItemAmount(), false) : "0"))
                                    .replace("{unit-count}", (shop.getShopItem() != null ? getPluginInstance().getManager().formatNumber(unitCount, false) : "0"))
                                    .replace("{item-count}", shop.getShopItem() != null ?
                                            getPluginInstance().getManager().formatNumber((shop.getShopItemAmount() * unitCount), false) : "0")));
                        }
                    }
                } else for (String line : lines)
                    if (!line.contains("{no-vault}") || (!useVault && line.contains("{no-vault}")))
                        add(getPluginInstance().getManager().color(line.replace("{no-vault}", "")));
            }});
            get().setItemMeta(itemMeta);
        }

        return this;
    }

    public CustomItem setLore(Player player, List<String> lines) {
        setLore(player, Arrays.copyOf(lines.toArray(), lines.size(), String[].class));
        return this;
    }

    public CustomItem refreshPlaceholders(@NotNull Player player, @NotNull String menuName, String buttonAction) {
        final Menu menu = getPluginInstance().getMenu(menuName);
        if (menu == null) return this;

        final String name = menu.getConfiguration().getString("buttons." + buttonAction + ".name");
        if (name != null && !name.isEmpty()) setDisplayName(player, shop, name);

        final List<String> lore = menu.getConfiguration().getStringList("buttons." + buttonAction + ".lore");
        if (!lore.isEmpty()) setLore(player, lore);

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
            String[] flagNames = {"HIDE_POTION_EFFECTS", "HIDE_DYE", "HIDE_ATTRIBUTES", "HIDE_UNBREAKABLE"};
            for (int i = -1; ++i < flagNames.length; ) {
                final String flagName = flagNames[i];
                try {
                    final ItemFlag flag = ItemFlag.valueOf(flagName);
                    itemMeta.addItemFlags(flag);
                } catch (Exception ignored) {}
            }
            get().setItemMeta(itemMeta);
        }
        return this;
    }

    public CustomItem setModelData(int modelData) {
        ItemMeta itemMeta = get().getItemMeta();
        if (itemMeta != null && modelData > 0 && isNew()) {
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
