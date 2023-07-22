package xzot1k.plugins.ds.core.tasks;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.objects.Shop;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShopVisitItemTask extends BukkitRunnable {

    private final DisplayShops INSTANCE;
    private final boolean showAdminShops, useVault, forceUse;
    private final Queue<UUID> rebuildQueue;

    private boolean runFirstTime = true;

    public ShopVisitItemTask(@NotNull DisplayShops pluginInstance) {
        this.INSTANCE = pluginInstance;
        rebuildQueue = new ConcurrentLinkedQueue<>();

        showAdminShops = INSTANCE.getMenusConfig().getBoolean("shop-visit-menu.show-admin-shops");
        useVault = (INSTANCE.getConfig().getBoolean("use-vault") && INSTANCE.getVaultEconomy() != null);
        forceUse = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");
    }

    @Override
    public void run() {

        if (runFirstTime) {

            Set<Map.Entry<UUID, Shop>> shops = INSTANCE.getManager().getShopMap().entrySet();
            for (Map.Entry<UUID, Shop> entry : shops) {
                final Shop shop = entry.getValue();
                if (shop.getBaseLocation() == null || (!showAdminShops && shop.isAdminShop()) || shop.getShopItem() == null) continue;

                shop.setVisitItemIcon(buildItem(shop));
                rebuildQueue.remove(entry.getKey());
            }

            runFirstTime = false;
        }

        while (!getRebuildQueue().isEmpty()) {
            final UUID shopId = getRebuildQueue().remove();

            final Shop shop = DisplayShops.getPluginInstance().getManager().getShopMap().getOrDefault(shopId, null);
            if (shop == null) continue;

            if (shop.getBaseLocation() == null || (!showAdminShops && shop.isAdminShop()) || shop.getShopItem() == null) continue;
            shop.setVisitItemIcon(buildItem(shop));
        }

        //  INSTANCE.getManager().sort((o1, o2) -> (o1.getValue().getType().name().compareToIgnoreCase(o2.getValue().getType().name())));
    }

    private ItemStack buildItem(@NotNull Shop shop) {
        ItemStack itemStack = new ItemStack(shop.getShopItem().getType(), 1, shop.getShopItem().getDurability());
        itemStack = INSTANCE.getPacketManager().updateNBT(itemStack, "shop-id", shop.getShopId().toString());

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            itemMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            final String itemName = (shop.getShopItem() != null ? INSTANCE.getManager().getItemName(shop.getShopItem()) : ""),
                    tradeItemName = (!useVault ? ((!forceUse && shop.getTradeItem() != null) ? INSTANCE.getManager().getItemName(shop.getTradeItem())
                            : INSTANCE.getManager().getItemName(INSTANCE.getManager().buildShopCurrencyItem(1))) : "");

            itemMeta.setDisplayName(INSTANCE.getManager().color(Objects.requireNonNull(INSTANCE.getMenusConfig().getString("shop-visit-menu.shop-name"))
                    .replace("{item}", itemName).replace("{trade}", tradeItemName)));
            itemMeta.setLore(new ArrayList<String>() {{
                final double visitCost = INSTANCE.getConfig().getDouble("visit-charge");
                final String adminType = INSTANCE.getMenusConfig().getString("shop-visit-menu.type-admin"),
                        playerType = INSTANCE.getMenusConfig().getString("shop-visit-menu.type-normal");

                OfflinePlayer offlinePlayer = (shop.getOwnerUniqueId() == null ? null : INSTANCE.getServer().getOfflinePlayer(shop.getOwnerUniqueId()));

                List<String> loreList = INSTANCE.getMenusConfig().getStringList("shop-visit-menu.shop-lore");
                for (int i = -1; ++i < loreList.size(); ) {
                    final String line = loreList.get(i);
                    if ((line.contains("{owner}") && shop.getOwnerUniqueId() == null) || (line.contains("{buy}") && shop.getBuyPrice(false) <= 0)
                            || (line.contains("{sell}") && shop.getSellPrice(false) <= 0))
                        continue;

                    if (line.toLowerCase().contains("{enchants}")) {
                        if (shop.getShopItem() == null) continue;

                        if (shop.getShopItem().getType() == Material.ENCHANTED_BOOK) {
                            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) shop.getShopItem().getItemMeta();
                            if (bookMeta != null)
                                for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet())
                                    add(INSTANCE.getManager().color(line.replace("{enchants}",
                                            (INSTANCE.getManager().getTranslatedName(entry.getKey()) + " " + INSTANCE.getManager().getRomanNumeral(entry.getValue())))));
                        } else if (!shop.getShopItem().getEnchantments().isEmpty()) {
                            for (Map.Entry<Enchantment, Integer> entry : shop.getShopItem().getEnchantments().entrySet())
                                add(INSTANCE.getManager().color(line.replace("{enchants}",
                                        (INSTANCE.getManager().getTranslatedName(entry.getKey()) + " " + INSTANCE.getManager().getRomanNumeral(entry.getValue())))));
                        }

                        if (shop.getShopItem().getType().name().contains("POTION")) {
                            PotionMeta potionMeta = (PotionMeta) shop.getShopItem().getItemMeta();
                            if (potionMeta != null) {
                                final String translatedName = INSTANCE.getManager().getTranslatedName(potionMeta.getBasePotionData().getType());
                                if (!translatedName.equalsIgnoreCase("Uncraftable"))
                                    add(INSTANCE.getManager().color(line.replace("{enchants}", (translatedName))));
                                for (PotionEffect potionEffect : potionMeta.getCustomEffects())
                                    add(INSTANCE.getManager().color(line
                                            .replace("{enchants}", (WordUtils.capitalize(potionEffect.getType().getName().toLowerCase()
                                                    .replace("_", " ")) + " " + INSTANCE.getManager().getRomanNumeral(potionEffect.getAmplifier()
                                                    + 1) + " " + (potionEffect.getDuration() / 20) + "s"))));
                            }
                            continue;
                        }

                        continue;
                    }

                    if ((line.contains("{buy}") && shop.getBuyPrice(shop.canDynamicPriceChange()) < 0)
                            || (line.contains("{sell}") && shop.getSellPrice(shop.canDynamicPriceChange()) < 0)) continue;

                    add(INSTANCE.getManager().color(line.replace("{owner}", ((shop.getOwnerUniqueId() != null && offlinePlayer != null
                                    && offlinePlayer.getName() != null) ? offlinePlayer.getName() : ""))
                            .replace("{balance}", (shop.getStoredBalance() < 0 ? "∞" : INSTANCE.getManager().formatNumber(shop.getStoredBalance(), true)))
                            .replace("{stock}", (shop.getStock() < 0 ? "∞" : INSTANCE.getManager().formatNumber(shop.getStock(), false)))
                            .replace("{description}", ((shop.getDescription() != null && !shop.getDescription().isEmpty()) ? shop.getDescription() : "---"))
                            .replace("{world}", shop.getBaseLocation().getWorldName())
                            .replace("{x}", INSTANCE.getManager().formatNumber((int) shop.getBaseLocation().getX(), false))
                            .replace("{y}", INSTANCE.getManager().formatNumber((int) shop.getBaseLocation().getY(), false))
                            .replace("{z}", INSTANCE.getManager().formatNumber((int) shop.getBaseLocation().getZ(), false))
                            .replace("{buy}", INSTANCE.getManager().formatNumber(shop.getBuyPrice(shop.canDynamicPriceChange()), true))
                            .replace("{sell}", INSTANCE.getManager().formatNumber(shop.getSellPrice(shop.canDynamicPriceChange()), true))
                            .replace("{cost}", INSTANCE.getManager().formatNumber(visitCost, true))
                            .replace("{type}", (Objects.requireNonNull(shop.isAdminShop() ? adminType : playerType)))
                            .replace("{amount}", INSTANCE.getManager().formatNumber(shop.getShopItemAmount(), false))
                            .replace("{item}", itemName)
                            .replace("{trade}", tradeItemName)));
                }
            }});
            itemStack.setItemMeta(itemMeta);
        }

        return itemStack;
    }

    public Queue<UUID> getRebuildQueue() {
        return rebuildQueue;
    }

    /*private boolean isAlreadyInList(List<Pair<Shop, ItemStack>> list, Shop shop) {
        for(int i = -1; ++i < list.size(); ) {
            final Pair<Shop, ItemStack> pair = list.get(i);
            if(pair.getKey().getShopId().toString().equals(shop.getShopId().toString())) return true;
        }
        return false;
    }*/

}
