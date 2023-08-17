package xzot1k.plugins.ds.core.gui;

import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.eco.EcoHook;
import xzot1k.plugins.ds.api.enums.InteractionType;
import xzot1k.plugins.ds.api.enums.ShopActionType;
import xzot1k.plugins.ds.api.objects.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class BackendMenu extends YamlConfiguration implements Menu {

    private final DisplayShops INSTANCE;
    private final String menuName, fileName;
    private final File file;
    private final HashMap<Integer, String> buttonLocationMap;
    private String title;
    private int size;

    /**
     * Creates a configuration file in the YAML format.
     *
     * @param file the configuration file.
     */
    public BackendMenu(@NotNull File file) {
        this.INSTANCE = DisplayShops.getPluginInstance();
        this.file = file;
        this.fileName = file.getName();
        this.menuName = fileName.toLowerCase().replace(".yml", "");

        this.buttonLocationMap = new HashMap<>();

        try {
            load(file);
            fixup();
            final String title = getString("title");
            setTitle(INSTANCE.getManager().color((title != null && !title.isEmpty()) ? title : ""));
            setSize(getInt("size"));
        } catch (IOException | InvalidConfigurationException e) {INSTANCE.getServer().getLogger().warning(e.getMessage());}
    }

    private void fixup() {
        // This fixes the edit.yml for the new values added
        if (getMenuName().equals("edit")) {
            ConfigurationSection buttonSection = getConfiguration().getConfigurationSection("buttons");
            if (buttonSection != null) {

                // This will rename the unit-amount item to shop-item-amount
                if (buttonSection.contains("unit-amount")) {
                    ConfigurationSection oldSection = buttonSection.getConfigurationSection("unit-amount");
                    if (oldSection != null) {
                        ConfigurationSection newSection = buttonSection.createSection("shop-item-amount");
                        for (String key : oldSection.getKeys(false)) {
                            final Object value = oldSection.get(key);
                            newSection.set(key, value);
                        }

                        buttonSection.set("unit-amount", null);
                        save();
                    }
                }

                // This will add the trade-item-amount, if missing
               /* if (!buttonSection.contains("trade-item-amount")) {
                    ConfigurationSection newSection = buttonSection.createSection("trade-item-amount");
                    final FileConfiguration jarConfigCopy = INSTANCE.getConfigFromJar("menus/edit.yml");
                    if (jarConfigCopy != null) {
                        ConfigurationSection tradeAmountSection = jarConfigCopy.getConfigurationSection("buttons.trade-item-amount");
                        if (tradeAmountSection != null) {
                            for (String key : tradeAmountSection.getKeys(false)) {
                                final Object value = tradeAmountSection.get(key);
                                newSection.set(key, value);
                            }
                        }

                        save();
                    }
                }*/

            }
        }/* else if (getMenuName().equals("amount-selector")) { // ensure {currency-symbol} is used in amount-selector
            ConfigurationSection amountButtonSection = getConfiguration().getConfigurationSection("buttons.amount");
            if (amountButtonSection != null) {
                final String name = amountButtonSection.getString("name");
                if (name != null && !name.contains("{currency-symbol}"))
                    amountButtonSection.set("name", ("&a{currency-symbol}" + name));
                save();
            }
        }*/


        for (Map.Entry<String, Object> entry : getValues(true).entrySet()) {

            final String key = entry.getKey().toLowerCase();

            if (key.endsWith("material")) {

                final String mat = String.valueOf(entry.getValue());
                if (!mat.isEmpty() && !isValidMaterial(mat)) {

                    if (mat.toUpperCase().startsWith("HEAD")) continue; // skip custom heads

                    if (INSTANCE.getServerVersion() < 1_13) {
                        // wool
                        if (mat.equalsIgnoreCase("LIME_WOOL")) getConfiguration().set(entry.getKey(), "WOOL:5");
                        else if (mat.equalsIgnoreCase("RED_WOOL")) getConfiguration().set(entry.getKey(), "WOOL:14");
                        else if (mat.equalsIgnoreCase("ORANGE_WOOL")) getConfiguration().set(entry.getKey(), "WOOL:1");
                        else if (mat.toUpperCase().contains("_WOOL")) getConfiguration().set(entry.getKey(), "WOOL");
                            // dye
                        else if (mat.equalsIgnoreCase("LIME_DYE")) getConfiguration().set(entry.getKey(), "INK_SACK:10");
                        else if (mat.equalsIgnoreCase("RED_DYE")) getConfiguration().set(entry.getKey(), "INK_SACK:1");
                        else if (mat.equalsIgnoreCase("ORANGE_DYE")) getConfiguration().set(entry.getKey(), "INK_SACK:14");
                        else if (mat.toUpperCase().contains("_DYE")) getConfiguration().set(entry.getKey(), "INK_SACK");
                            // minecart
                        else if (mat.equalsIgnoreCase("CHEST_MINECART")) getConfiguration().set(entry.getKey(), "STORAGE_MINECART");
                            // sign
                        else if (mat.toUpperCase().contains("_SIGN")) getConfiguration().set(entry.getKey(), "SIGN");
                            // end portal frame
                        else if (mat.equalsIgnoreCase("END_PORTAL_FRAME")) getConfiguration().set(entry.getKey(), "ENDER_PORTAL_FRAME");
                            // player head
                        else if (mat.equalsIgnoreCase("PLAYER_HEAD")) getConfiguration().set(entry.getKey(), "SKULL_ITEM");
                            // stained-glass
                        else if (mat.equalsIgnoreCase("BLACK_STAINED_GLASS_PANE")) getConfiguration().set(entry.getKey(), "STAINED_GLASS_PANE:15");
                        else if (mat.toUpperCase().contains("STAINED_GLASS_PANE")) getConfiguration().set(entry.getKey(), "STAINED_GLASS_PANE:15");

                    } else if (INSTANCE.getServerVersion() >= 1_13) {

                        // wool
                        if (mat.equalsIgnoreCase("WOOL:5")) getConfiguration().set(entry.getKey(), "LIME_WOOL");
                        else if (mat.equalsIgnoreCase("WOOL:14")) getConfiguration().set(entry.getKey(), "RED_WOOL");
                        else if (mat.equalsIgnoreCase("WOOL:1")) getConfiguration().set(entry.getKey(), "ORANGE_WOOL");
                        else if (mat.toUpperCase().contains("WOOL")) getConfiguration().set(entry.getKey(), "WHITE_WOOL");
                            // dye
                        else if (mat.equalsIgnoreCase("INK_SACK:10")) getConfiguration().set(entry.getKey(), "LIME_DYE");
                        else if (mat.equalsIgnoreCase("INK_SACK:1")) getConfiguration().set(entry.getKey(), "RED_DYE");
                        else if (mat.equalsIgnoreCase("INK_SACK:14")) getConfiguration().set(entry.getKey(), "ORANGE_DYE");
                        else if (mat.toUpperCase().contains("INK_SACK")) getConfiguration().set(entry.getKey(), "_DYE");
                            // minecart
                        else if (mat.equalsIgnoreCase("STORAGE_MINECART")) getConfiguration().set(entry.getKey(), "CHEST_MINECART");
                            // sign
                        else if (INSTANCE.getServerVersion() >= 1_14 && mat.equalsIgnoreCase("SIGN")) getConfiguration().set(entry.getKey(), "OAK_SIGN");
                            // end portal frame
                        else if (mat.equalsIgnoreCase("ENDER_PORTAL_FRAME")) getConfiguration().set(entry.getKey(), "END_PORTAL_FRAME");
                            // player head
                        else if (mat.equalsIgnoreCase("SKULL_ITEM")) getConfiguration().set(entry.getKey(), "PLAYER_HEAD");
                            // stained-glass
                        else if (mat.equalsIgnoreCase("STAINED_GLASS_PANE:15")) getConfiguration().set(entry.getKey(), "BLACK_STAINED_GLASS_PANE");
                        else if (mat.toUpperCase().contains("STAINED_GLASS_PANE")) getConfiguration().set(entry.getKey(), "BLACK_STAINED_GLASS_PANE");
                    }
                }
            }
        }

        try {
            getConfiguration().save(getFile());
            reload();
        } catch (IOException ignored) {}
    }

    private boolean isValidMaterial(@Nullable String material) {
        if (material == null || material.isEmpty()) return false;
        Material mat = Material.matchMaterial(material);
        return (mat != null);
    }

    private String predictCorrectValue(@NotNull Class<? extends Enum<?>> enumeration, @NotNull String value) {
        String currentWinner = ((enumeration.getEnumConstants().length > 0) ? enumeration.getEnumConstants()[0].name() : "");
        int currentDistance = 0;

        for (Enum<?> operation : enumeration.getEnumConstants()) {
            final int newDistance = StringUtils.getLevenshteinDistance(operation.name(), value);
            if (newDistance > currentDistance) {
                currentWinner = operation.name();
                currentDistance = newDistance;
            }
        }

        return currentWinner;
    }

    private String stitchSearchText(@Nullable String... searchText) {
        String stitchedSearchText = ((searchText != null && searchText.length == 1) ? searchText[0] : null);

        if (searchText != null && searchText.length > 1) {
            final StringBuilder sb = new StringBuilder();

            for (int i = -1; ++i < searchText.length; ) {
                final String arg = searchText[i];

                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }

            stitchedSearchText = sb.toString();
        }

        return ((stitchedSearchText != null && !stitchedSearchText.isEmpty()) ? stitchedSearchText : null);
    }

    @Override
    public boolean matches(@Nullable String title) {return ChatColor.stripColor(getTitle()).equals(ChatColor.stripColor(title));}

    /**
     * Gets the location of the button being looked for.
     *
     * @param slot The slot to look for.
     * @return The name of the button found.
     */
    @Override
    public String getButtonName(int slot) {
        return getButtonLocationMap().getOrDefault(slot, null);
    }

    /**
     * Builds the menu using a defined search text alongside the player.
     *
     * @param player     The player to base properties around.
     * @param searchText The searched text, if applicable.
     * @return The created menu.
     */
    public Inventory build(@NotNull Player player, @Nullable String... searchText) {
        final Inventory inventory = ((getSize() <= 5) ? INSTANCE.getServer().createInventory(null, InventoryType.HOPPER, getTitle())
                : INSTANCE.getServer().createInventory(null, getSize(), getTitle()));

        ArrayList<Integer> emptySlots = new ArrayList<>(getIntegerList("empty-slots"));
        buildButtons(player, inventory, emptySlots, stitchSearchText(searchText));

        if (getMenuName().contains("amount-selector")) {
            final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
            final Shop shop = dataPack.getSelectedShop();
            double finalAmount = 0;

            if (dataPack.getInteractionValue() != null) {
                finalAmount = (double) dataPack.getInteractionValue();
                dataPack.setInteractionValue(null);
            }

            final int amountSlot = getInt("buttons.amount.slot");
            final ItemStack amountItem = inventory.getItem(amountSlot);
            if (amountItem != null && amountItem.getItemMeta() != null) {
                final ItemMeta itemMeta = amountItem.getItemMeta();

                if (finalAmount == 0) {
                    if (dataPack.getInteractionType() == InteractionType.AMOUNT_BUY_PRICE) {
                        finalAmount = shop.getBuyPrice(false);
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_SELL_PRICE) {
                        finalAmount = shop.getSellPrice(false);
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_STOCK) {
                        finalAmount = shop.getStock();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE) {
                        finalAmount = shop.getStoredBalance();
                    } else if (dataPack.getInteractionType() == InteractionType.SHOP_ITEM_AMOUNT) {
                        finalAmount = shop.getShopItemAmount();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_PLAYER_BUY_LIMIT) {
                        finalAmount = shop.getPlayerBuyLimit();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_PLAYER_SELL_LIMIT) {
                        finalAmount = shop.getPlayerSellLimit();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_GLOBAL_BUY_LIMIT) {
                        finalAmount = shop.getGlobalBuyLimit();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_GLOBAL_SELL_LIMIT) {
                        finalAmount = shop.getGlobalSellLimit();
                    }
                }

                final String name = getString("buttons.amount.name");
                if (name != null) {
                    String disabled = INSTANCE.getLangConfig().getString("disabled");
                    if (disabled == null) disabled = "";

                    final boolean isDecimal = (dataPack.getInteractionType().name().contains("PRICE") || dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE),
                            isLimit = dataPack.getInteractionType().name().contains("LIMIT");
                    if (!isDecimal) amountItem.setAmount((int) Math.max(1, Math.min(finalAmount, amountItem.getType().getMaxStackSize())));
                    itemMeta.setDisplayName(INSTANCE.getManager().color(name
                            .replace("{amount}", (!isLimit ? (isDecimal ? INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), finalAmount)
                                    : INSTANCE.getManager().formatNumber(finalAmount, false))
                                    : ((finalAmount < 0) ? disabled : INSTANCE.getManager().formatNumber(finalAmount, false))))));
                }

                amountItem.setAmount((int) Math.min(amountItem.getType().getMaxStackSize(), Math.max(1, finalAmount)));
                amountItem.setItemMeta(itemMeta);
                inventory.setItem(amountSlot, INSTANCE.updateNBT(amountItem, "ds-amount", String.valueOf(finalAmount)));
            }

        }

        // fill empty slots. If defined, fill defined slots
        fillEmptySlots(inventory, emptySlots);

        final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
        final Shop shop = dataPack.getSelectedShop();

        if (shop != null) {
            if (getMenuName().contains("edit")) {
                final int saleSlot = getInt("sale-item-slot"), tradeSlot = getInt("trade-item-slot");

                if (saleSlot >= 0 && saleSlot < inventory.getSize()) {
                    if (shop.getShopItem() != null) {
                        final ItemStack clonedItem = dataPack.getSelectedShop().getShopItem().clone();
                        clonedItem.setAmount(dataPack.getSelectedShop().getShopItemAmount());
                        inventory.setItem(saleSlot, clonedItem);
                    } else inventory.setItem(saleSlot, null);
                }

                if (shouldShowTradeContent(shop) && tradeSlot >= 0 && tradeSlot < inventory.getSize())
                    inventory.setItem(tradeSlot, dataPack.getSelectedShop().getCurrencyItem().clone());

                final EcoHook ecoHook = INSTANCE.getEconomyHandler().getEcoHook(shop.getCurrencyType());
                if (ecoHook != null) updateButton(player, inventory, getConfiguration().getInt("buttons.currency-type.slot"),
                        shop, null, ("{type}:" + (shop.getCurrencyType().equals("item-for-item") ? shop.getTradeItemName() : ecoHook.getName())));
                return inventory;

            } else if (getMenuName().contains("transaction")) {
                ItemStack previewItem = dataPack.getSelectedShop().getShopItem().clone();
                if (dataPack.getSelectedShop().getCurrencyType().equals("item-for-item")) {
                    ItemMeta itemMeta = previewItem.getItemMeta();
                    if (itemMeta != null) {
                        List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore()),
                                previewLore = getStringList("trade-item-lore");
                        for (int i = -1; ++i < previewLore.size(); ) lore.add(INSTANCE.getManager().color(previewLore.get(i)));
                        itemMeta.setLore(lore);
                        previewItem.setItemMeta(itemMeta);
                    }
                }

                final int previewSlot = getInt("preview-slot");
                if (previewSlot >= 0 && previewSlot < inventory.getSize()) {
                    previewItem.setAmount(Math.min(dataPack.getSelectedShop().getShopItemAmount(), previewItem.getMaxStackSize()));
                    inventory.setItem(previewSlot, previewItem);
                }

                return inventory;
            }
        }

        if (getMenuName().contains("visit")) {
            final String buyType = getString("visit-types.both");
            updateButton(player, inventory, getConfiguration().getInt("buttons.type.slot"), shop, null, ("{type}:" + buyType));

            updateButton(player, inventory, getConfiguration().getInt("buttons.search.slot"),
                    shop, null, ("{search-text}:" + INSTANCE.getLangConfig().getString("not-applicable")));

            if (!dataPack.getPageMap().isEmpty()) switchPage(inventory, player, dataPack.getCurrentPage());

        } else if (getMenuName().contains("appearance") || getMenuName().contains("assistants")) {
            updateButton(player, inventory, getConfiguration().getInt("buttons.search.slot"),
                    shop, null, ("{search-text}:" + INSTANCE.getLangConfig().getString("not-applicable")));

            if (!dataPack.getPageMap().isEmpty()) switchPage(inventory, player, dataPack.getCurrentPage());
        }

        return inventory;
    }

    public void updateButton(@NotNull Player player, @NotNull Inventory inventory, int slot, @Nullable Shop shop,
                             @Nullable List<Integer> emptySlots, @Nullable String... extraPlaceHolders) {
        ConfigurationSection mainSection = getConfigurationSection("buttons");
        if (mainSection == null) return;
        buildButton(mainSection, getButtonName(slot), player, inventory, shop, emptySlots, extraPlaceHolders);
    }

    private void updatePageButtons(@NotNull Player player, @NotNull Inventory inventory, @NotNull DataPack dataPack,
                                   @Nullable Shop shop, @Nullable List<Integer> emptySlots) {
        ConfigurationSection mainSection = getConfigurationSection("buttons");
        if (mainSection != null) {
            if (dataPack.hasNextPage()) {
                buildButton(mainSection, "next", player, inventory, shop, emptySlots);
            } else fillSlot(inventory, mainSection.getInt("next.slot"));

            if (dataPack.hasPreviousPage()) {
                buildButton(mainSection, "previous", player, inventory, shop, emptySlots);
            } else fillSlot(inventory, mainSection.getInt("previous.slot"));
        }
    }

    private void buildButtons(@NotNull Player player, @NotNull Inventory inventory, @Nullable List<Integer> emptySlots, @Nullable String searchText) {
        ConfigurationSection mainSection = getConfigurationSection("buttons");
        if (mainSection != null) {
            final Collection<String> buttonActions = mainSection.getKeys(false);
            if (!buttonActions.isEmpty()) {

                final DDataPack dataPack = (DDataPack) INSTANCE.getManager().getDataPack(player);
                final Shop shop = dataPack.getSelectedShop();

                final boolean isPageMenu = (getMenuName().contains("appearance")
                        || getMenuName().contains("visit") || getMenuName().contains("assistants"));

                if (getMenuName().contains("appearance") || getMenuName().contains("assistants")) loadPages(player, dataPack, shop, searchText, null);
                else if (getMenuName().contains("visit")) loadPages(player, dataPack, null, searchText, null);

                buttonActions.parallelStream().forEach(buttonAction -> {
                    // checks whether to add the next and/or previous buttons for page menus
                    if (isPageMenu && (buttonAction.equals("next") && !dataPack.hasNextPage()
                            || buttonAction.equals("previous") && !dataPack.hasPreviousPage())
                            || (!shouldShowTradeContent(shop) && buttonAction.contains("trade"))
                            /* || (buttonAction.equals("custom-amount") && INSTANCE.isGeyserInstalled()
                            && org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(player.getUniqueId()))*/) return;

                    buildButton(mainSection, buttonAction, player, inventory, shop, emptySlots);
                });
            }
        }
    }

    public void buildButton(@NotNull ConfigurationSection mainSection, @NotNull String buttonAction, @NotNull Player player,
                            @NotNull Inventory inventory, @Nullable Shop shop, @Nullable List<Integer> emptySlots, @Nullable String... extraPlaceHolders) {
        final int slot = (mainSection.contains(buttonAction + ".slot") ? mainSection.getInt(buttonAction + ".slot") : 0);

        if (slot < 0 || slot >= inventory.getSize()) return;
        else inventory.setItem(slot, null);

        getButtonLocationMap().put(slot, buttonAction);

        if (emptySlots == null || !emptySlots.contains(slot)) {
            final String materialName = (mainSection.contains(buttonAction + ".material") ? mainSection.getString(buttonAction + ".material") : "STONE");
            final int amount = (mainSection.contains(buttonAction + ".amount") ? mainSection.getInt(buttonAction + ".amount") : 1),
                    durability = (mainSection.contains(buttonAction + ".durability") ? mainSection.getInt(buttonAction + ".durability") : 1);

            final CustomItem item = (shop != null ? new CustomItem(materialName, amount, durability, shop,
                    (shop.getShopItem() != null ? shop.getShopItem().getMaxStackSize() : 1), 1)
                    : new CustomItem(materialName, amount, durability));

            final String name = mainSection.getString(buttonAction + ".name");
            if (name != null && !name.isEmpty()) item.setDisplayName(null, shop, name, extraPlaceHolders);

            final List<String> lore = mainSection.getStringList(buttonAction + ".lore");
            if (!lore.isEmpty()) item.setLore(null, lore, extraPlaceHolders);

            final int customModelData = (mainSection.contains(buttonAction + ".custom-model-data")
                    ? mainSection.getInt(buttonAction + ".custom-model-data") : 0);
            item.setModelData(customModelData);

            final List<String> enchants = mainSection.getStringList(buttonAction + ".enchants");
            if (!enchants.isEmpty()) item.setEnchantments(enchants);

            final List<String> flags = mainSection.getStringList(buttonAction + ".flags");
            if (!flags.isEmpty()) item.setItemFlags(flags);

            inventory.setItem(slot, item.get());
        }
    }

    @Override
    public void switchPage(@NotNull Inventory inventory, @NotNull Player player, int page) {

        List<Integer> emptySlots = new ArrayList<>(getIntegerList("empty-slots"));
        for (int i = -1; ++i < inventory.getSize(); ) {
            if (emptySlots.contains(i)) inventory.setItem(i, null);
        }

        final DDataPack dataPack = (DDataPack) INSTANCE.getManager().getDataPack(player);
        final Shop shop = dataPack.getSelectedShop();

        if (page > dataPack.getCurrentPage() && dataPack.getPageMap().containsKey(page)) {
            dataPack.setCurrentPage(page);
            updatePageButtons(player, inventory, dataPack, shop, emptySlots);
        } else if (page < dataPack.getCurrentPage() && dataPack.getPageMap().containsKey(page)) {
            dataPack.setCurrentPage(page);
            updatePageButtons(player, inventory, dataPack, shop, emptySlots);
        }

        if (dataPack.getPageMap().containsKey(page)) {
            final List<ItemStack> buttons = dataPack.getPageMap().get(page);
            for (int i = -1; ++i < buttons.size(); ) {
                final ItemStack button = buttons.get(i);
                inventory.addItem(button);
            }
        }
    }

    @Override
    public void loadPages(@NotNull Player player, @NotNull DataPack dataPack, @Nullable Shop shop, @Nullable String searchText, @Nullable ItemStack typeItem) {
        dataPack.getPageMap().clear();

        final boolean forceUse = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");
        final int[] currentPage = {1};
        List<ItemStack> pageContents = new ArrayList<>();
        switch (getMenuName()) {

            case "visit": {
                final boolean showAdminShop = getBoolean("show-admin-shops");

                OfflinePlayer offlinePlayer = null;
                if (searchText != null && !searchText.isEmpty()) {
                    OfflinePlayer op = INSTANCE.getServer().getOfflinePlayer(searchText);
                    if (op.hasPlayedBefore()) offlinePlayer = op;
                }

                final ShopActionType actionType = (typeItem != null ? ShopActionType.getTypeFromItem(typeItem, this) : null);

                final double visitCost = getDouble("visit-charge");
                final String adminType = getString("visit-icon.type-admin"),
                        playerType = getString("visit-icon.type-normal"),
                        shopNameFormat = getString("visit-icon.name");
                final List<String> loreFormat = getStringList("visit-icon.lore");

                int counter = 0;

                List<Map.Entry<UUID, Shop>> shopList = new ArrayList<>(INSTANCE.getManager().getShopMap().entrySet());
                for (int i = -1; ++i < shopList.size(); ) {
                    final Shop currentShop = shopList.get(i).getValue();

                    if (currentShop == null || currentShop.getBaseLocation() == null || (!showAdminShop && currentShop.isAdminShop()) || currentShop.getShopItem() == null
                            || currentShop.getStock() == 0 || currentShop.getStock() < currentShop.getShopItemAmount()) continue;

                    if (actionType != null && actionType.failsCheck(currentShop)) continue;

                    if (searchText != null && !searchText.isEmpty()) {
                        if (offlinePlayer != null) {
                            if (currentShop.getOwnerUniqueId() == null || !currentShop.getOwnerUniqueId().toString().equals(offlinePlayer.getUniqueId().toString()))
                                continue;

                            OfflinePlayer op = INSTANCE.getServer().getOfflinePlayer(currentShop.getOwnerUniqueId());
                            if (op.hasPlayedBefore() && op.getName() != null && !op.getName().equalsIgnoreCase(offlinePlayer.getName())) continue;
                        } else if (currentShop.getShopItem().getItemMeta() != null
                                && !ChatColor.stripColor(currentShop.getShopItem().getItemMeta().getDisplayName().toLowerCase()).contains(searchText.toLowerCase())
                                && !currentShop.getShopItem().getType().name().toLowerCase().replace("_", " ").contains(searchText.toLowerCase())
                                && !currentShop.getDescription().toLowerCase().contains(searchText.toLowerCase())
                                && !(currentShop.getBuyPrice(false) + " " + currentShop.getSellPrice(false)).contains(searchText))
                            continue;

                        counter++;
                    }

                    final Location location = currentShop.getBaseLocation().asBukkitLocation();
                    ItemStack itemStack = new ItemStack(currentShop.getShopItem().getType(), 1, currentShop.getShopItem().getDurability());

                    itemStack = INSTANCE.updateNBT(itemStack, "currentShop-id", currentShop.getShopId().toString());
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    if (itemMeta != null) {
                        itemMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                        final String itemName = (currentShop.getShopItem() != null ? INSTANCE.getManager().getItemName(currentShop.getShopItem()) : ""),
                                tradeItemName = (currentShop.getCurrencyType().equals("item-for-item") ? ((!forceUse && currentShop.getTradeItem() != null)
                                        ? INSTANCE.getManager().getItemName(currentShop.getTradeItem())
                                        : INSTANCE.getManager().getItemName(INSTANCE.getManager().buildShopCurrencyItem(1))) : "");

                        itemMeta.setDisplayName(INSTANCE.getManager().color(INSTANCE.papiText(player,
                                Objects.requireNonNull(shopNameFormat).replace("{item}", itemName).replace("{trade}", tradeItemName))));
                        itemMeta.setLore(new ArrayList<String>() {{
                            OfflinePlayer offlinePlayer = (currentShop.getOwnerUniqueId() == null ? null :
                                    INSTANCE.getServer().getOfflinePlayer(currentShop.getOwnerUniqueId()));
                            for (int i = -1; ++i < loreFormat.size(); ) {
                                final String line = loreFormat.get(i);
                                if ((line.contains("{owner}") && currentShop.getOwnerUniqueId() == null)
                                        || (line.contains("{buy}") && currentShop.getBuyPrice(false) <= 0)
                                        || (line.contains("{sell}") && currentShop.getSellPrice(false) <= 0)) continue;

                                if (line.toLowerCase().contains("{enchants}")) {
                                    if (currentShop.getShopItem() == null) continue;

                                    if (currentShop.getShopItem().getType() == Material.ENCHANTED_BOOK) {
                                        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) currentShop.getShopItem().getItemMeta();
                                        if (bookMeta != null) for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet())
                                            add(INSTANCE.getManager().color(INSTANCE.papiText(player, line
                                                    .replace("{enchants}", (INSTANCE.getManager().getTranslatedName(entry.getKey())
                                                            + " " + INSTANCE.getManager().getRomanNumeral(entry.getValue()))))));
                                    } else if (!currentShop.getShopItem().getEnchantments().isEmpty()) {
                                        for (Map.Entry<Enchantment, Integer> entry : currentShop.getShopItem().getEnchantments().entrySet())
                                            add(INSTANCE.getManager().color(INSTANCE.papiText(player, line
                                                    .replace("{enchants}", (INSTANCE.getManager().getTranslatedName(entry.getKey()) + " "
                                                            + INSTANCE.getManager().getRomanNumeral(entry.getValue()))))));
                                    }

                                    if (currentShop.getShopItem().getType().name().contains("POTION")) {
                                        PotionMeta potionMeta = (PotionMeta) currentShop.getShopItem().getItemMeta();
                                        if (potionMeta != null) {
                                            final String translatedName = INSTANCE.getManager().getTranslatedName(potionMeta.getBasePotionData().getType());
                                            if (!translatedName.equalsIgnoreCase("Uncraftable"))
                                                add(INSTANCE.getManager().color(INSTANCE
                                                        .papiText(player, line.replace("{enchants}", (translatedName)))));
                                            for (PotionEffect potionEffect : potionMeta.getCustomEffects())
                                                add(INSTANCE.getManager().color(INSTANCE.papiText(player, line
                                                        .replace("{enchants}", (WordUtils.capitalize(potionEffect.getType().getName().toLowerCase().replace("_", " "))
                                                                + " " + INSTANCE.getManager().getRomanNumeral(potionEffect.getAmplifier() + 1)
                                                                + " " + (potionEffect.getDuration() / 20) + "s")))));
                                        }
                                        continue;
                                    }

                                    continue;
                                }

                                add(INSTANCE.getManager().color(INSTANCE.papiText(player, line
                                        .replace("{owner}", ((currentShop.getOwnerUniqueId() != null
                                                && offlinePlayer != null) ? Objects.requireNonNull(offlinePlayer.getName()) : ""))
                                        .replace("{balance}", (currentShop.getStoredBalance() < 0 ? "\u221E"
                                                : INSTANCE.getEconomyHandler().format(currentShop, currentShop.getCurrencyType(), currentShop.getStoredBalance())))
                                        .replace("{stock}", (currentShop.getStock() < 0 ? "\u221E"
                                                : INSTANCE.getManager().formatNumber(currentShop.getStock(), false)))
                                        .replace("{description}", ((currentShop.getDescription() != null && !currentShop.getDescription().isEmpty())
                                                ? currentShop.getDescription() : "---"))
                                        .replace("{world}", Objects.requireNonNull(location.getWorld()).getName())
                                        .replace("{x}", INSTANCE.getManager().formatNumber(location.getBlockX(), false))
                                        .replace("{y}", INSTANCE.getManager().formatNumber(location.getBlockY(), false))
                                        .replace("{z}", INSTANCE.getManager().formatNumber(location.getBlockZ(), false))
                                        .replace("{buy}", INSTANCE.getEconomyHandler().format(currentShop, currentShop.getCurrencyType(),
                                                currentShop.getBuyPrice(currentShop.canDynamicPriceChange())))
                                        .replace("{sell}", INSTANCE.getEconomyHandler().format(currentShop, currentShop.getCurrencyType(),
                                                currentShop.getSellPrice(currentShop.canDynamicPriceChange())))
                                        .replace("{cost}", INSTANCE.getEconomyHandler().format(currentShop, currentShop.getCurrencyType(), visitCost))
                                        .replace("{type}", (Objects.requireNonNull(currentShop.isAdminShop() ? adminType : playerType)))
                                        .replace("{amount}", INSTANCE.getManager().formatNumber(currentShop.getShopItemAmount(), false))
                                        .replace("{item}", itemName).replace("{trade}", tradeItemName))));
                            }
                        }});
                        itemStack.setItemMeta(itemMeta);
                    }

                    if (pageContents.size() >= (getSize() - 9)) {
                        dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                        pageContents.clear();
                        currentPage[0] += 1;
                    }

                    pageContents.add(INSTANCE.updateNBT(itemStack, "shop-id", currentShop.getShopId().toString()));
                }

                if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));

                if (searchText != null && !searchText.isEmpty()) {
                    final String message = INSTANCE.getLangConfig().getString((counter > 0) ? "visit-filter-count" : "visit-filter-none");
                    if (message != null && !message.equalsIgnoreCase(""))
                        INSTANCE.getManager().sendMessage(player, message
                                .replace("{count}", INSTANCE.getManager().formatNumber(counter, false))
                                .replace("{filter}", searchText));
                }
                break;
            }

            case "assistants": {
                if (shop != null) {
                    final String activeColor = getString("active-color"),
                            inActiveColor = getString("inactive-color");
                    final List<String> loreFormat = getStringList("head-lore");

                    INSTANCE.getServer().getOnlinePlayers().parallelStream().forEach(currentPlayer -> {

                        if (currentPlayer.getUniqueId().toString().equals(player.getUniqueId().toString())) return;

                        if (searchText != null && !searchText.isEmpty()
                                && !currentPlayer.getName().toLowerCase().startsWith(searchText.toLowerCase())
                                && !currentPlayer.getUniqueId().toString().startsWith(searchText)) return;

                        final CustomItem item = new CustomItem(("HEAD:" + currentPlayer.getName()), 0, 1)
                                .setDisplayName(player, shop, (shop.getAssistants().contains(currentPlayer.getUniqueId())
                                        ? (activeColor + currentPlayer.getName()) : (inActiveColor + currentPlayer.getName())))
                                .setLore(null, new ArrayList<String>() {{
                                    for (int i = -1; ++i < loreFormat.size(); ) {
                                        final String line = loreFormat.get(i);
                                        add(line.replace("{player}", currentPlayer.getName()));
                                    }
                                }});

                        if (pageContents.size() >= (getSize() - 9)) {
                            dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                            pageContents.clear();
                            currentPage[0] += 1;
                        }

                        pageContents.add(INSTANCE.updateNBT(item.get(), "uuid", currentPlayer.getUniqueId().toString()));
                    });

                    if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                }
                break;
            }

            case "appearance": {
                if (shop != null) {
                    String currentMaterial = (shop.getStoredBaseBlockMaterial() != null ? shop.getStoredBaseBlockMaterial()
                            : INSTANCE.getConfig().getString("shop-block-material"));
                    int currentDurability = -1;

                    if (currentMaterial != null) {
                        if (currentMaterial.contains(":")) {
                            String[] args = currentMaterial.split(":");
                            Material newMat = Material.getMaterial(args[0].toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (newMat != null) currentMaterial = newMat.name();
                            else if (INSTANCE.isItemAdderInstalled()) {
                                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                                if (customBlock != null) currentMaterial = customBlock.getId();
                            }

                            currentDurability = Integer.parseInt(args[1]);
                        } else {
                            Material newMat = Material.getMaterial(currentMaterial.toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (newMat != null) currentMaterial = newMat.name();
                            else if (INSTANCE.isItemAdderInstalled()) {
                                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(currentMaterial);
                                if (customBlock != null) currentMaterial = customBlock.getId();
                            }
                        }
                    }

                    String selectedName = getString("selected-format.name");
                    final String unlockedName = getString("unlocked-format.name"),
                            lockedName = getString("locked-format.name");

                    final List<String> appearances = getStringList("appearances"),
                            selectedLore = getStringList("selected-format.lore"),
                            unlockedLore = getStringList("unlocked-format.lore"),
                            lockedLore = getStringList("locked-format.lore");

                    if (getBoolean("sort-alphabetically")) Collections.sort(appearances); // sort appearances alphabetically

                    for (int i = -1; ++i < appearances.size(); ) {
                        final String appearance = appearances.get(i);
                        ItemStack itemStack = null;
                        double foundPrice = 0;

                        String material = null, unlockId = appearance;
                        if (appearance.contains(":")) {
                            String[] args = appearance.split(":");
                            unlockId = (args[0] + ":" + args[1]);

                            Material mat = Material.getMaterial(args[0]);
                            if (mat != null) {
                                material = mat.name();
                                int durability = Integer.parseInt(args[1]);
                                itemStack = new ItemStack(mat, 1, (byte) (Math.max(durability, 0)));
                                itemStack = INSTANCE.updateNBT(itemStack, "ds-type", material);
                            } else if (INSTANCE.isItemAdderInstalled()) {
                                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                                if (customBlock != null) {
                                    material = customBlock.getId();
                                    itemStack = INSTANCE.updateNBT(customBlock.getItemStack(), "ds-type", material);
                                }
                            }

                            if (args.length >= 3 && !INSTANCE.getManager().isNotNumeric(args[2])) foundPrice = Double.parseDouble(args[2]);
                        } else {
                            Material mat = Material.getMaterial(appearance);
                            if (mat != null) {
                                material = mat.name();
                                itemStack = INSTANCE.updateNBT(new ItemStack(mat), "ds-type", material);
                            } else if (INSTANCE.isItemAdderInstalled()) {
                                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(appearance);
                                if (customBlock != null) {
                                    material = customBlock.getId();
                                    itemStack = INSTANCE.updateNBT(customBlock.getItemStack(), "ds-type", material);
                                }
                            }
                        }

                        if (itemStack == null) continue;

                        final ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta != null) {
                            if ((material != null && material.equalsIgnoreCase(currentMaterial)
                                    && (currentDurability == itemStack.getDurability() || currentDurability <= -1))) {
                                if (selectedName != null) {
                                    if (INSTANCE.isItemAdderInstalled()) {
                                        dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(material);
                                        if (customBlock != null) selectedName = selectedName.replace("{material}", customBlock.getDisplayName());
                                        else selectedName = selectedName.replace("{material}", INSTANCE.getManager().getTranslatedName(itemStack.getType()));
                                    } else selectedName = selectedName.replace("{material}", INSTANCE.getManager().getTranslatedName(itemStack.getType()));
                                    itemMeta.setDisplayName(INSTANCE.getManager().color(selectedName));
                                }
                                itemMeta.setLore(new ArrayList<String>() {{
                                    for (int i = -1; ++i < selectedLore.size(); )
                                        add(INSTANCE.getManager().color(selectedLore.get(i)));
                                }});

                                if (getBoolean("selected-format.enchanted")) {
                                    try {
                                        itemMeta.addEnchant(Enchantment.DURABILITY, 0, true);
                                        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        INSTANCE.log(Level.WARNING, "Failed to hide the enchantments on the current base-block item"
                                                + " for the selection GUI. Please disable this option for your version.");
                                    }
                                }

                                itemStack.setItemMeta(itemMeta);
                            } else {
                                final boolean isUnlocked = dataPack.hasUnlockedBBM(unlockId);
                                String name = getString((isUnlocked ? "unlocked" : "locked") + "-format.name");
                                if (name != null) {
                                    if (INSTANCE.isItemAdderInstalled()) {
                                        dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(material);
                                        if (customBlock != null) name = name.replace("{material}", customBlock.getDisplayName());
                                        else name = name.replace("{material}", INSTANCE.getManager().getTranslatedName(itemStack.getType()));
                                    } else name = name.replace("{material}", INSTANCE.getManager().getTranslatedName(itemStack.getType()));

                                    itemMeta.setDisplayName(INSTANCE.getManager().color(Objects.requireNonNull((isUnlocked ? unlockedName : lockedName))
                                            .replace("{material}", name)));

                                    final double finalFoundPrice = foundPrice;
                                    itemMeta.setLore(new ArrayList<String>() {{
                                        if (appearance.contains(":")) {
                                            String[] args = appearance.split(":");
                                            for (int i = -1; ++i < (isUnlocked ? unlockedLore : lockedLore).size(); ) {
                                                String line = (isUnlocked ? unlockedLore : lockedLore).get(i);
                                                if (!line.equalsIgnoreCase("{requirement}"))
                                                    add(INSTANCE.getManager().color(INSTANCE.papiText(player, line.replace("{price}",
                                                            INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), finalFoundPrice)))));
                                                else if (args.length >= 4)
                                                    add(INSTANCE.getManager().color(INSTANCE.papiText(player, args[3])));
                                            }
                                        } else
                                            for (int i = -1; ++i < (isUnlocked ? unlockedLore : lockedLore).size(); )
                                                add(INSTANCE.getManager().color((isUnlocked ? unlockedLore : lockedLore).get(i)
                                                        .replace("{raw-price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), finalFoundPrice))
                                                        .replace("{price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), finalFoundPrice))));
                                    }});

                                    itemStack.setItemMeta(itemMeta);
                                }
                            }

                            if (searchText != null && !searchText.isEmpty()) {
                                boolean isNumeric = !INSTANCE.getManager().isNotNumeric(searchText);

                                if (!(isNumeric && foundPrice == Double.parseDouble(searchText))
                                        && !itemStack.getType().name().toLowerCase().replace("_", " ").contains(searchText.toLowerCase())
                                        && (shop.getShopItem().getItemMeta() != null
                                        && !ChatColor.stripColor(shop.getShopItem().getItemMeta().getDisplayName()).toLowerCase().contains(searchText.toLowerCase())))
                                    continue;
                            }

                            if (pageContents.size() >= (getInt("size") - 9)) {
                                dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                                pageContents.clear();
                                currentPage[0] += 1;
                            }

                            pageContents.add(INSTANCE.updateNBT(itemStack, "ds-bbm", shop.getShopId().toString()));
                        }

                        if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                    }
                    break;
                }
            }

            default: {
                break;
            }
        }

        dataPack.setCurrentPage(1);
    }

    public void updateSearchItem(@NotNull Inventory inventory, @NotNull String searchText) {
        final int searchItemSlot = getConfiguration().getInt("buttons.search.slot");
        if (searchItemSlot >= 0 && searchItemSlot < getSize()) {
            final ItemStack searchItem = inventory.getItem(searchItemSlot);
            if (searchItem != null && searchItem.getItemMeta() != null) {
                final ItemMeta itemMeta = searchItem.getItemMeta();
                itemMeta.setDisplayName(itemMeta.getDisplayName().replace("{search-text}", searchText));
                searchItem.setItemMeta(itemMeta);
                inventory.setItem(searchItemSlot, searchItem);
            }
        }
    }

    /**
     * Compares an itemstack to the menu's filler item.
     *
     * @param itemStack The item to compare.
     * @return Whether the itemstack is similar to the filler item.
     */
    public boolean isFillerItem(@NotNull ItemStack itemStack) {
        return itemStack.isSimilar(new CustomItem(getString("filler-material"), 0, 1)
                .setDisplayName(null, null, "&6").get());
    }

    private void fillEmptySlots(@NotNull Inventory inventory, @Nullable List<Integer> emptySlots) {
        final CustomItem fillItem = new CustomItem(getString("filler-material"), 0, 1)
                .setDisplayName(null, null, "&6")
                .setModelData(getInt("filler-model-data"));

        for (int i = -1; ++i < inventory.getSize(); ) {
            final ItemStack itemStack = inventory.getItem(i);
            if ((itemStack != null && !itemStack.getType().name().contains("AIR"))
                    || (emptySlots != null && emptySlots.contains(i))) continue;
            inventory.setItem(i, fillItem.get());
        }
    }

    private void fillSlot(@NotNull Inventory inventory, int slot) {
        if (slot >= 0 && slot < inventory.getSize())
            inventory.setItem(slot, new CustomItem(getString("filler-material"), 0, 1)
                    .setDisplayName(null, null, "&6")
                    .setModelData(getInt("filler-model-data"))
                    .get());
    }

    public int getButtonSlot(@NotNull String name) {
        for (Map.Entry<Integer, String> entry : getButtonLocationMap().entrySet())
            if (entry.getValue().equals(name)) return entry.getKey();
        return -1;
    }

    /**
     * Saves the configuration file to the disk.
     */
    @Override
    public void save() {
        if (file == null) return;

        try {
            save(file);
        } catch (IOException e) {
            e.printStackTrace();
            INSTANCE.getServer().getLogger().warning(e.getMessage());
        }
    }

    /**
     * Reloads the configuration file from disk.
     */
    @Override
    public void reload() {
        try {
            load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            INSTANCE.getServer().getLogger().warning(e.getMessage());
        }

        loadConfiguration(file);
    }

    @Override
    public FileConfiguration getConfiguration() {
        return this;
    }

    /**
     * @return The name of the configuration file.
     */
    @Override
    public String getFileName() {
        return fileName;
    }

    /**
     * @return The name of the menu.
     */
    @Override
    public String getMenuName() {
        return menuName;
    }

    /**
     * @return The configuration file associated to the menu.
     */
    @Override
    public File getFile() {
        return file;
    }

    /**
     * @return The map of buttons alongside their placement for the menu.
     */
    @Override
    public HashMap<Integer, String> getButtonLocationMap() {
        return buttonLocationMap;
    }

    /**
     * @return The size of the menu.
     */
    public int getSize() {
        return size;
    }

    /**
     * Sets the size of the menu for all future builds.
     *
     * @param size The new menu size.
     */
    public void setSize(int size) {
        if (size <= 5) this.size = 5;
        else if ((size % 9) == 0) this.size = Math.min(size, 54);
        else this.size = 54;
    }

    /**
     * @return The current color translated title of the menu.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title of the menu for all future builds.
     *
     * @param title The new title for the menu.
     */
    public void setTitle(@NotNull String title) {
        this.title = title;
    }

    public boolean shouldShowTradeContent(@NotNull Shop shop) {return (getMenuName().equals("edit") && shop.getCurrencyType().equalsIgnoreCase("item-for-item"));}

}
