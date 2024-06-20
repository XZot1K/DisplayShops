package xzot1k.plugins.ds.core.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;

public class TextEntryMenu implements InventoryHolder {

    private final DisplayShops INSTANCE;
    private final Inventory inventory;

    public TextEntryMenu(String title, String originalValue) {
        INSTANCE = DisplayShops.getPluginInstance();
        inventory = INSTANCE.getServer().createInventory(this, InventoryType.ANVIL, title);

        AnvilInventory anvilInventory = (AnvilInventory) inventory;

        ItemStack itemStack = new ItemStack(Material.PAPER);
        if (originalValue != null && !originalValue.isEmpty()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null) {
                itemMeta.setDisplayName(originalValue);
                itemStack.setItemMeta(itemMeta);
            }
        }

        anvilInventory.setFirstItem(itemStack);
    }


    @Override
    public @NotNull Inventory getInventory() {return this.inventory;}
}