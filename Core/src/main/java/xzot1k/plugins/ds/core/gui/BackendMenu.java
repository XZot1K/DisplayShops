package xzot1k.plugins.ds.core.gui;

import net.md_5.bungee.api.ChatColor;
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
import xzot1k.plugins.ds.api.enums.InteractionType;
import xzot1k.plugins.ds.api.objects.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class BackendMenu extends YamlConfiguration implements Menu {

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
        this.file = file;
        this.fileName = file.getName();
        this.menuName = fileName.toLowerCase().replace(".yml", "");

        this.buttonLocationMap = new HashMap<>();

        try {

            load(file);

            final String title = getString("title");
            setTitle(DisplayShops.getPluginInstance().getManager().color((title != null && !title.isEmpty()) ? title : ""));
            setSize(getInt("size"));

        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().getServer().getLogger().warning(e.getMessage());
        }
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
    public boolean matches(@Nullable String title) {
        return (title != null && !title.isEmpty() && ChatColor.stripColor(getTitle()).equals(ChatColor.stripColor(title)));
    }

    /**
     * Gets the location of the button being looked for.
     *
     * @param slot The slot to look for.
     * @return The name of the button found.
     */
    @Override
    public String getButtonName(int slot) {return getButtonLocationMap().getOrDefault(slot, null);}

    /**
     * Builds the menu using a defined search text alongside the player.
     *
     * @param player     The player to base properties around.
     * @param searchText The searched text, if applicable.
     * @return The created menu.
     */
    public Inventory build(@NotNull Player player, @Nullable String... searchText) {

        final Inventory inventory = ((getSize() <= 5)
                ? DisplayShops.getPluginInstance().getServer().createInventory(null, InventoryType.HOPPER, getTitle())
                : DisplayShops.getPluginInstance().getServer().createInventory(null, getSize(), getTitle()));

        ArrayList<Integer> emptySlots = new ArrayList<>(getIntegerList("empty-slots"));

        buildButtons(player, inventory, emptySlots, stitchSearchText(searchText));

        if (getMenuName().contains("amount-selector")) {

            final DataPack dataPack = DisplayShops.getPluginInstance().getManager().getDataPack(player);
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
                    }/* else if (dataPack.getInteractionType() == InteractionType.AMOUNT_STOCK) {
                        finalAmount = shop.getStock();
                    } else if (dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE) {
                        finalAmount = shop.getStoredBalance();
                    }*/ else if (dataPack.getInteractionType() == InteractionType.AMOUNT_UNIT_COUNT) {
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

                String name = getString("buttons.amount.name");
                if (name != null) {
                    final boolean isDecimal = (dataPack.getInteractionType().name().contains("PRICE")
                            || dataPack.getInteractionType() == InteractionType.AMOUNT_BALANCE);

                    // TODO the currency symbol is not colored
                    if (isDecimal) name = (DisplayShops.getPluginInstance().getConfig().getString("currency-symbol") + name);

                    itemMeta.setDisplayName(DisplayShops.getPluginInstance().getManager().color(name
                            .replace("{amount}", DisplayShops.getPluginInstance().getManager().formatNumber(finalAmount, isDecimal))));
                }

                amountItem.setItemMeta(itemMeta);
                inventory.setItem(amountSlot, DisplayShops.getPluginInstance().getPacketManager()
                        .getSerializeUtil().updateNBT(amountItem, "ds-amount", String.valueOf(finalAmount)));
            }

        }

        // fill empty slots. If defined, fill defined slots
        fillEmptySlots(inventory, emptySlots);

        final DataPack dataPack = DisplayShops.getPluginInstance().getManager().getDataPack(player);

        if (getMenuName().contains("edit")) {

            final int saleSlot = getInt("sale-item-slot"), tradeSlot = getInt("trade-item-slot");

            if (saleSlot >= 0 && saleSlot < inventory.getSize()) inventory.setItem(saleSlot, dataPack.getSelectedShop().getShopItem());
            if (tradeSlot >= 0 && tradeSlot < inventory.getSize()) inventory.setItem(tradeSlot, dataPack.getSelectedShop().getTradeItem());

        } else if (getMenuName().contains("transaction")) {

            // TODO add "click to see trade item lore" to the sale item (preview item)
            ItemStack previewItem = dataPack.getSelectedShop().getShopItem().clone();
            if (!(DisplayShops.getPluginInstance().getConfig().getBoolean("use-vault")
                    && DisplayShops.getPluginInstance().getVaultEconomy() != null)) {
                ItemMeta itemMeta = previewItem.getItemMeta();
                if (itemMeta != null) {
                    List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore()),
                            previewLore = getStringList("trade-item-lore");
                    for (int i = -1; ++i < previewLore.size(); )
                        lore.add(DisplayShops.getPluginInstance().getManager().color(previewLore.get(i)));
                    itemMeta.setLore(lore);
                    previewItem.setItemMeta(itemMeta);
                }
            }

            final int previewSlot = getInt("preview-slot");
            if (previewSlot >= 0 && previewSlot < inventory.getSize()) inventory.setItem(previewSlot, previewItem);

        } else if (getMenuName().contains("visit") || getMenuName().contains("appearance")) {

            // loadPages(player, dataPack, dataPack.getSelectedShop(), stitchSearchText(searchText));
            if (!dataPack.getPageMap().isEmpty()) switchPage(inventory, player, dataPack.getCurrentPage());

        }

        return inventory;
    }

    public void updateButton(@NotNull Player player, @NotNull Inventory inventory, int slot,
                             @Nullable Shop shop, @Nullable List<Integer> emptySlots) {
        ConfigurationSection mainSection = getConfigurationSection("buttons");
        if (mainSection == null) return;
        buildButton(mainSection, getButtonName(slot), player, inventory, shop, emptySlots);
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

                final DDataPack dataPack = (DDataPack) DisplayShops.getPluginInstance().getManager().getDataPack(player);
                final Shop shop = dataPack.getSelectedShop();

                final boolean isPageMenu = (getMenuName().contains("appearance")
                        || getMenuName().contains("visit") || getMenuName().contains("assistants"));

                if (getMenuName().contains("appearance") || getMenuName().contains("assistants")) loadPages(player, dataPack, shop, searchText);
                else if (getMenuName().contains("visit")) loadPages(player, dataPack, null, searchText);

                buttonActions.parallelStream().forEach(buttonAction -> {
                    // checks whether to add the next and/or previous buttons for page menus
                    if (isPageMenu && (buttonAction.equals("next") && !dataPack.hasNextPage()
                            || buttonAction.equals("previous") && !dataPack.hasPreviousPage())) return;

                    buildButton(mainSection, buttonAction, player, inventory, shop, emptySlots);
                });
            }
        }
    }

    private void buildButton(@NotNull ConfigurationSection mainSection, @NotNull String buttonAction, @NotNull Player player,
                             @NotNull Inventory inventory, @Nullable Shop shop, @Nullable List<Integer> emptySlots) {

        final int slot = (mainSection.contains(buttonAction + ".slot") ? mainSection.getInt(buttonAction + ".slot") : 0);

        if (slot < 0 || slot >= inventory.getSize()) return;
        else inventory.setItem(slot, null);

        getButtonLocationMap().put(slot, buttonAction);

        if (emptySlots == null || !emptySlots.contains(slot)) {
            final String materialName = (mainSection.contains(buttonAction + ".material") ? mainSection.getString(buttonAction + ".material") : "STONE");
            final int amount = (mainSection.contains(buttonAction + ".amount") ? mainSection.getInt(buttonAction + ".amount") : 1),
                    durability = (mainSection.contains(buttonAction + ".durability") ? mainSection.getInt(buttonAction + ".durability") : 1);

            final CustomItem item = (shop != null ? new CustomItem(materialName, amount, durability, shop,
                    (shop.getShopItem() != null ? shop.getShopItem().getMaxStackSize() : 1), shop.getShopItemAmount())
                    : new CustomItem(materialName, amount, durability));

            final String name = mainSection.getString(buttonAction + ".name");
            if (name != null && !name.isEmpty()) item.setDisplayName(null, shop, name);

            final List<String> lore = mainSection.getStringList(buttonAction + ".lore");
            if (!lore.isEmpty()) item.setLore(null, lore);

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

        final DDataPack dataPack = (DDataPack) DisplayShops.getPluginInstance().getManager().getDataPack(player);
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
    public void loadPages(@NotNull Player player, @NotNull DataPack dataPack, @Nullable Shop shop, @Nullable String searchText) {

        dataPack.getPageMap().clear();

        final boolean useVault = (DisplayShops.getPluginInstance().getVaultEconomy() != null),
                forceUse = DisplayShops.getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use");

        final int[] currentPage = {1};
        List<ItemStack> pageContents = new ArrayList<>();
        switch (getMenuName()) {

            case "visit": {
                final boolean showAdminShop = getBoolean("show-admin-shops");

                OfflinePlayer offlinePlayer = null;
                if (searchText != null && !searchText.isEmpty()) {
                    OfflinePlayer op = DisplayShops.getPluginInstance().getServer().getOfflinePlayer(searchText);
                    if (op != null && op.hasPlayedBefore()) offlinePlayer = op;
                }

                final double visitCost = getDouble("visit-charge");
                final String adminType = getString("visit-icon.type-admin"),
                        playerType = getString("visit-icon.type-normal"),
                        shopNameFormat = getString("visit-icon.name");
                final List<String> loreFormat = getStringList("visit-icon.lore");

                int counter = 0;

                List<Map.Entry<UUID, Shop>> shopList = new ArrayList<>(DisplayShops.getPluginInstance().getManager().getShopMap().entrySet());
                for (int i = -1; ++i < shopList.size(); ) {
                    final Shop currentShop = shopList.get(i).getValue();

                    if (currentShop == null || currentShop.getBaseLocation() == null || (!showAdminShop && currentShop.isAdminShop()) || currentShop.getShopItem() == null
                            || currentShop.getStock() == 0 || currentShop.getStock() < currentShop.getShopItemAmount()) continue;

                    if (searchText != null && !searchText.isEmpty()) {

                        if (offlinePlayer != null) {
                            if (currentShop.getOwnerUniqueId() == null || !currentShop.getOwnerUniqueId().toString().equals(offlinePlayer.getUniqueId().toString()))
                                continue;

                            OfflinePlayer op = DisplayShops.getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId());
                            if (op != null && op.hasPlayedBefore() && op.getName() != null && !op.getName().equalsIgnoreCase(offlinePlayer.getName())) continue;
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

                    itemStack = DisplayShops.getPluginInstance().getPacketManager().updateNBT(itemStack, "currentShop-id", currentShop.getShopId().toString());
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    if (itemMeta != null) {
                        itemMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                        final String itemName = (currentShop.getShopItem() != null ? DisplayShops.getPluginInstance().getManager().getItemName(currentShop.getShopItem()) : ""),
                                tradeItemName = (!useVault ? ((!forceUse && currentShop.getTradeItem() != null)
                                        ? DisplayShops.getPluginInstance().getManager().getItemName(currentShop.getTradeItem())
                                        : DisplayShops.getPluginInstance().getManager().getItemName(DisplayShops.getPluginInstance().getManager().buildShopCurrencyItem(1))) : "");

                        itemMeta.setDisplayName(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player,
                                Objects.requireNonNull(shopNameFormat).replace("{item}", itemName).replace("{trade}", tradeItemName))));
                        itemMeta.setLore(new ArrayList<String>() {{
                            OfflinePlayer offlinePlayer = (currentShop.getOwnerUniqueId() == null ? null :
                                    DisplayShops.getPluginInstance().getServer().getOfflinePlayer(currentShop.getOwnerUniqueId()));
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
                                            add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, line
                                                    .replace("{enchants}", (DisplayShops.getPluginInstance().getManager().getTranslatedName(entry.getKey())
                                                            + " " + DisplayShops.getPluginInstance().getManager().getRomanNumeral(entry.getValue()))))));
                                    } else if (!currentShop.getShopItem().getEnchantments().isEmpty()) {
                                        for (Map.Entry<Enchantment, Integer> entry : currentShop.getShopItem().getEnchantments().entrySet())
                                            add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, line
                                                    .replace("{enchants}", (DisplayShops.getPluginInstance().getManager().getTranslatedName(entry.getKey()) + " "
                                                            + DisplayShops.getPluginInstance().getManager().getRomanNumeral(entry.getValue()))))));
                                    }

                                    if (currentShop.getShopItem().getType().name().contains("POTION")) {
                                        PotionMeta potionMeta = (PotionMeta) currentShop.getShopItem().getItemMeta();
                                        if (potionMeta != null) {
                                            final String translatedName = DisplayShops.getPluginInstance().getManager().getTranslatedName(potionMeta.getBasePotionData().getType());
                                            if (!translatedName.equalsIgnoreCase("Uncraftable"))
                                                add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance()
                                                        .papiText(player, line.replace("{enchants}", (translatedName)))));
                                            for (PotionEffect potionEffect : potionMeta.getCustomEffects())
                                                add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, line
                                                        .replace("{enchants}", (WordUtils.capitalize(potionEffect.getType().getName().toLowerCase().replace("_", " "))
                                                                + " " + DisplayShops.getPluginInstance().getManager().getRomanNumeral(potionEffect.getAmplifier() + 1)
                                                                + " " + (potionEffect.getDuration() / 20) + "s")))));
                                        }
                                        continue;
                                    }

                                    continue;
                                }

                                add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, line
                                        .replace("{owner}", ((currentShop.getOwnerUniqueId() != null && offlinePlayer != null) ? Objects.requireNonNull(offlinePlayer.getName()) : ""))
                                        .replace("{balance}", (currentShop.getStoredBalance() < 0 ? "\u221E"
                                                : DisplayShops.getPluginInstance().getManager().formatNumber(currentShop.getStoredBalance(), true)))
                                        .replace("{stock}", (currentShop.getStock() < 0 ? "\u221E"
                                                : DisplayShops.getPluginInstance().getManager().formatNumber(currentShop.getStock(), false)))
                                        .replace("{description}", ((currentShop.getDescription() != null && !currentShop.getDescription().isEmpty()) ? currentShop.getDescription() : "---"))
                                        .replace("{world}", Objects.requireNonNull(location.getWorld()).getName())
                                        .replace("{x}", DisplayShops.getPluginInstance().getManager().formatNumber(location.getBlockX(), false))
                                        .replace("{y}", DisplayShops.getPluginInstance().getManager().formatNumber(location.getBlockY(), false))
                                        .replace("{z}", DisplayShops.getPluginInstance().getManager().formatNumber(location.getBlockZ(), false))
                                        .replace("{buy}", DisplayShops.getPluginInstance().getManager().formatNumber(currentShop.getBuyPrice(currentShop.canDynamicPriceChange()), true))
                                        .replace("{sell}", DisplayShops.getPluginInstance().getManager().formatNumber(currentShop.getSellPrice(currentShop.canDynamicPriceChange()), true))
                                        .replace("{cost}", DisplayShops.getPluginInstance().getManager().formatNumber(visitCost, true))
                                        .replace("{type}", (Objects.requireNonNull(currentShop.isAdminShop() ? adminType : playerType)))
                                        .replace("{amount}", DisplayShops.getPluginInstance().getManager().formatNumber(currentShop.getShopItemAmount(), false))
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

                    pageContents.add(DisplayShops.getPluginInstance().getPacketManager().updateNBT(itemStack, "shop-id", currentShop.getShopId().toString()));
                }

                if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));

                if (searchText != null && !searchText.isEmpty()) {
                    final String message = DisplayShops.getPluginInstance().getLangConfig().getString((counter > 0) ? "visit-filter-count" : "visit-filter-none");
                    if (message != null && !message.equalsIgnoreCase(""))
                        DisplayShops.getPluginInstance().getManager().sendMessage(player, message
                                .replace("{count}", DisplayShops.getPluginInstance().getManager().formatNumber(counter, false))
                                .replace("{filter}", searchText));
                }
                break;
            }

            case "assistants": {
                if (shop != null) {
                    final String activeColor = getString("active-color"),
                            inActiveColor = getString("inactive-color");
                    final List<String> loreFormat = getStringList("head-lore");

                    DisplayShops.getPluginInstance().getServer().getOnlinePlayers().parallelStream().forEach(currentPlayer -> {

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

                        pageContents.add(DisplayShops.getPluginInstance().getPacketManager().updateNBT(item.get(), "uuid", currentPlayer.getUniqueId().toString()));
                    });

                    if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));
                }
                break;
            }

            case "appearance": {
                if (shop != null) {
                    String storedMaterialLine = shop.getStoredBaseBlockMaterial();
                    if (storedMaterialLine == null || storedMaterialLine.isEmpty())
                        storedMaterialLine = DisplayShops.getPluginInstance().getConfig().getString("shop-block-material");

                    Material currentMaterial = Material.CHEST;
                    int currentDurability = -1;

                    if (storedMaterialLine != null) {
                        if (storedMaterialLine.contains(":")) {
                            String[] args = storedMaterialLine.split(":");
                            currentMaterial = Material.getMaterial(args[0].toUpperCase()
                                    .replace(" ", "_").replace("-", "_"));
                            currentDurability = Integer.parseInt(args[1]);
                        } else currentMaterial = Material.getMaterial(storedMaterialLine.toUpperCase()
                                .replace(" ", "_").replace("-", "_"));
                    }

                    final String selectedName = getString("selected-format.name"),
                            unlockedName = getString("unlocked-format.name"),
                            lockedName = getString("locked-format.name");

                    final List<String> appearances = getStringList("appearances"),
                            selectedLore = getStringList("selected-format.lore"),
                            unlockedLore = getStringList("unlocked-format.lore"),
                            lockedLore = getStringList("locked-format.lore");

                    final boolean hasStarPerm = player.hasPermission("displayshops.bbm.*");

                    if (getBoolean("sort-alphabetically")) Collections.sort(appearances); // sort appearances alphabetically

                    for (int i = -1; ++i < appearances.size(); ) {
                        final String appearance = appearances.get(i);

                        ItemStack itemStack = null;
                        double foundPrice = 0;

                        Material material;
                        String unlockId = appearance, typeId = "";
                        if (appearance.contains(":")) {
                            String[] args = appearance.split(":");
                            unlockId = (args[0] + ":" + args[1]);

                            material = Material.getMaterial(args[0]);
                            if (material == null) {
                                if (DisplayShops.getPluginInstance().isItemAdderInstalled()) {
                                    dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                                    if (customBlock != null) {
                                        typeId = customBlock.getId();
                                        itemStack = DisplayShops.getPluginInstance().getPacketManager().updateNBT(customBlock.getItemStack(), "ds-type", typeId);
                                    }
                                }
                            } else {
                                int durability = Integer.parseInt(args[1]);
                                itemStack = new ItemStack(material, 1, (byte) (Math.max(durability, 0)));
                                itemStack = DisplayShops.getPluginInstance().getPacketManager().updateNBT(itemStack, "ds-type", material.name());
                            }

                            if (args.length >= 3 && !DisplayShops.getPluginInstance().getManager().isNotNumeric(args[2])) foundPrice = Double.parseDouble(args[2]);
                        } else {
                            material = Material.getMaterial(appearance);
                            if (material == null) {
                                if (DisplayShops.getPluginInstance().isItemAdderInstalled()) {
                                    dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(appearance);
                                    if (customBlock != null) {
                                        typeId = customBlock.getId();
                                        itemStack = DisplayShops.getPluginInstance().getPacketManager().updateNBT(customBlock.getItemStack(), "ds-type", typeId);
                                    }
                                }
                            } else itemStack = DisplayShops.getPluginInstance().getPacketManager().updateNBT(new ItemStack(material), "ds-type", material.name());
                        }

                        if (itemStack == null) continue;

                        final ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta != null) {
                            if ((typeId.equalsIgnoreCase(material.name()) || (currentMaterial != null && material.name().equalsIgnoreCase(currentMaterial.name()))
                                    && (currentDurability == itemStack.getDurability() || currentDurability <= -1))) {
                                itemMeta.setDisplayName(DisplayShops.getPluginInstance().getManager().color(Objects.requireNonNull(selectedName)
                                        .replace("{material}", DisplayShops.getPluginInstance().getManager().getTranslatedName(itemStack.getType()))));
                                itemMeta.setLore(new ArrayList<String>() {{
                                    for (int i = -1; ++i < selectedLore.size(); )
                                        add(DisplayShops.getPluginInstance().getManager().color(selectedLore.get(i)));
                                }});

                                if (getBoolean("selected-format.enchanted")) {
                                    try {
                                        itemMeta.addEnchant(Enchantment.DURABILITY, 0, true);
                                        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        DisplayShops.getPluginInstance().log(Level.WARNING, "Failed to hide the enchantments on the current base-block item"
                                                + " for the selection GUI. Please disable this option for your version.");
                                    }
                                }

                                itemStack.setItemMeta(itemMeta);
                            } else {

                                final boolean isUnlocked = dataPack.hasUnlockedBBM(unlockId);
                                String name = getString((isUnlocked ? "unlocked" : "locked") + "-format.name");

                                if (name != null) {
                                    if (DisplayShops.getPluginInstance().isItemAdderInstalled()) {
                                        dev.lone.itemsadder.api.CustomStack customStack = dev.lone.itemsadder.api.CustomStack.getInstance(typeId);
                                        if (customStack != null) name = name.replace("{material}", customStack.getDisplayName());
                                        else name = name.replace("{material}", DisplayShops.getPluginInstance().getManager().getTranslatedName(itemStack.getType()));
                                    } else name = name.replace("{material}", DisplayShops.getPluginInstance().getManager().getTranslatedName(itemStack.getType()));

                                    itemMeta.setDisplayName(DisplayShops.getPluginInstance().getManager().color(Objects.requireNonNull((isUnlocked ? unlockedName : lockedName))
                                            .replace("{material}", name)));

                                    final double finalFoundPrice = foundPrice;
                                    itemMeta.setLore(new ArrayList<String>() {{
                                        if (appearance.contains(":")) {
                                            String[] args = appearance.split(":");
                                            for (int i = -1; ++i < (isUnlocked ? unlockedLore : lockedLore).size(); ) {
                                                String line = (isUnlocked ? unlockedLore : lockedLore).get(i);
                                                if (!line.equalsIgnoreCase("{requirement}"))
                                                    add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, line.replace("{price}",
                                                            DisplayShops.getPluginInstance().getManager().formatNumber(finalFoundPrice, true)))));
                                                else if (args.length >= 4) add(DisplayShops.getPluginInstance().getManager().color(DisplayShops.getPluginInstance().papiText(player, args[3])));
                                            }
                                        } else
                                            for (int i = -1; ++i < (isUnlocked ? unlockedLore : lockedLore).size(); )
                                                add(DisplayShops.getPluginInstance().getManager().color((isUnlocked ? unlockedLore : lockedLore).get(i)
                                                        .replace("{price}", DisplayShops.getPluginInstance().getManager().formatNumber(finalFoundPrice, true))));
                                    }});

                                    itemStack.setItemMeta(itemMeta);
                                }
                            }

                            if (searchText != null && !searchText.isEmpty()) {

                                boolean isNumeric = !DisplayShops.getPluginInstance().getManager().isNotNumeric(searchText);

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

                            pageContents.add(DisplayShops.getPluginInstance().getPacketManager().updateNBT(itemStack, "ds-bbm", shop.getShopId().toString()));
                        }

                        if (!pageContents.isEmpty()) dataPack.getPageMap().put(currentPage[0], new ArrayList<>(pageContents));

                    }
                    break;
                }
            }

            default: {break;}
        }

        dataPack.setCurrentPage(1);
    }

    private void fillEmptySlots(@NotNull Inventory inventory, @Nullable List<Integer> emptySlots) {
        final CustomItem fillItem = new CustomItem(getString("filler-material"), 0, 1)
                .setDisplayName(null, null, "&6");

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
                    .setDisplayName(null, null, "&6").get());
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

        try {save(file);} catch (IOException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().getServer().getLogger().warning(e.getMessage());
        }
    }

    /**
     * Reloads the configuration file from disk.
     */
    @Override
    public void reload() {
        try {load(file);} catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().getServer().getLogger().warning(e.getMessage());
        }

        loadConfiguration(file);
    }

    @Override
    public FileConfiguration getConfiguration() {return this;}

    /**
     * @return The name of the configuration file.
     */
    @Override
    public String getFileName() {return fileName;}

    /**
     * @return The name of the menu.
     */
    @Override
    public String getMenuName() {return menuName;}

    /**
     * @return The configuration file associated to the menu.
     */
    @Override
    public File getFile() {return file;}

    /**
     * @return The map of buttons alongside their placement for the menu.
     */
    @Override
    public HashMap<Integer, String> getButtonLocationMap() {return buttonLocationMap;}

    /**
     * @return The size of the menu.
     */
    public int getSize() {return size;}

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
    public String getTitle() {return title;}

    /**
     * Sets the title of the menu for all future builds.
     *
     * @param title The new title for the menu.
     */
    public void setTitle(@NotNull String title) {this.title = title;}

}
