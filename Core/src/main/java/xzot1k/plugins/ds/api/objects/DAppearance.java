package xzot1k.plugins.ds.api.objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DAppearance extends Appearance {
    private final DisplayShops INSTANCE = DisplayShops.getPluginInstance();

    public DAppearance(@NotNull String id, @NotNull String material, double price, @Nullable String permission, @Nullable String... requirement) {
        super(id, material, price, permission, requirement);
        registerAppearance(this);
    }

    public static void loadAppearances() {
        Menu menu = DisplayShops.getPluginInstance().getMenu("appearance");
        if (menu == null) return;

        Object appearanceObject = menu.getConfiguration().get("appearances");
        if (appearanceObject == null) {
            DisplayShops.getPluginInstance().log(Level.WARNING, "No appearances section was found in the \"" + menu.getConfiguration().getName() +
                    "\" file.");
            return;
        }

        // convert from old format
        List<?> appearanceList;
        if (appearanceObject instanceof List && (appearanceList = ((List<?>) appearanceObject)).parallelStream().noneMatch((o -> !(o instanceof String)))) {
            menu.getConfiguration().set("appearances", null);
            appearanceObject = menu.getConfiguration().createSection("appearances");

            final ConfigurationSection appearanceSection = (ConfigurationSection) appearanceObject;
            for (int i = -1; ++i < appearanceList.size(); ) {
                final String appearance = (String) appearanceList.get(i);

                // Old Format: 4 args
                // "END_PORTAL_FRAME:-1:50.0:&7Requires the &edisplayshops.bbm.END_PORTAL_FRAME &7permission."

                String material, id;
                double price = 0;
                List<String> requirement = new ArrayList<>();

                if (appearance.contains(":")) {
                    final String[] args = appearance.split(":");
                    material = ((id = args[0]) + ":" + args[1]);
                    if (!DisplayShops.getPluginInstance().getManager().isNotNumeric(args[2])) price = Double.parseDouble(args[2]);
                    if (args.length >= 4) requirement.add(args[3]);
                } else {
                    material = appearance;
                    id = appearance;
                }

                appearanceSection.set((id + ".material"), material);
                appearanceSection.set((id + ".price"), price);
                appearanceSection.set((id + ".permission"), ("displayshops.bbm." + id));
                appearanceSection.set((id + ".requirement"), requirement);
            }

            menu.save();
        }

        // load new format
        long counter = 0, failed = 0;
        final ConfigurationSection appearanceSection = (ConfigurationSection) appearanceObject;
        for (String id : appearanceSection.getKeys(true)) {
            final ConfigurationSection appearanceOptions = appearanceSection.getConfigurationSection(id);
            if (appearanceOptions == null) continue;

            String material = appearanceOptions.getString("material");
            if (material == null || material.isEmpty()) {
                failed++;
                DisplayShops.getPluginInstance().log(Level.WARNING, "The appearance \"" + id + "\" was unable to load due to a missing/invalid " +
                        "material value.");
                continue;
            }

            new DAppearance(id, material, appearanceOptions.getDouble("price"), appearanceOptions.getString("permission"),
                    appearanceOptions.getStringList("requirement").toArray(new String[]{}));
            counter++;
        }

        DisplayShops.getPluginInstance().log(Level.INFO, counter + " shop appearances were loaded into memory."
                + (failed > 0 ? " " + failed + " appearances failed to load." : ""));
    }

    private Pair<String, Integer> getCurrentMaterial(@NotNull Shop shop) {
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
        return new Pair<>(currentMaterial, currentDurability);
    }

    @Override
    public ItemStack build(@NotNull Player player, @NotNull Shop shop) {
        ItemStack itemStack = null;
        String material = null;
        if (getMaterial() != null && getMaterial().contains(":")) {
            final String[] args = getMaterial().split(":");
            Material mat = Material.getMaterial(args[0]);
            if (mat != null) {
                material = mat.name();
                int durability = Integer.parseInt(args[1]);
                itemStack = INSTANCE.updateNBT(new ItemStack(mat, 1, (byte) (Math.max(durability, 0))), "ds-type", material);
            } else if (INSTANCE.isItemAdderInstalled()) {
                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                if (customBlock != null) {
                    material = customBlock.getId();
                    itemStack = INSTANCE.updateNBT(customBlock.getItemStack(), "ds-type", material);
                }
            }
        } else {
            Material mat = Material.getMaterial(getMaterial());
            if (mat != null) {
                material = mat.name();
                itemStack = INSTANCE.updateNBT(new ItemStack(mat), "ds-type", material);
            } else if (INSTANCE.isItemAdderInstalled()) {
                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(getMaterial());
                if (customBlock != null) {
                    material = customBlock.getId();
                    itemStack = INSTANCE.updateNBT(customBlock.getItemStack(), "ds-type", material);
                }
            }
        }

        if (itemStack == null) return null;

        ItemMeta itemMeta = itemStack.getItemMeta();
        final Menu menu = INSTANCE.getMenu("appearance");
        if (itemMeta == null || menu == null) return INSTANCE.updateNBT(itemStack, "ds-bbm", shop.getShopId().toString());

        final Pair<String, Integer> current = getCurrentMaterial(shop);
        if (material.equalsIgnoreCase(current.getKey()) && (current.getValue() == itemStack.getDurability() || current.getValue() <= -1)) {
            String selectedName = menu.getConfiguration().getString("selected-format.name");
            final List<String> selectedLore = menu.getConfiguration().getStringList("selected-format.lore");

            if (selectedName != null) {
                Material mat = Material.getMaterial(material);
                selectedName = selectedName.replace("{material}", (mat != null ? INSTANCE.getManager().getTranslatedName(mat)
                        : getId().replace("_", " ")));
                itemMeta.setDisplayName(INSTANCE.getManager().color(selectedName));
            }
            itemMeta.setLore(new ArrayList<String>() {{
                for (int i = -1; ++i < selectedLore.size(); )
                    add(INSTANCE.getManager().color(selectedLore.get(i)));
            }});

            if (menu.getConfiguration().getBoolean("selected-format.enchanted")) {
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
            final String unlockedName = menu.getConfiguration().getString("unlocked-format.name"),
                    lockedName = menu.getConfiguration().getString("locked-format.name");
            final List<String> unlockedLore = menu.getConfiguration().getStringList("unlocked-format.lore"),
                    lockedLore = menu.getConfiguration().getStringList("locked-format.lore");

            final DataPack dataPack = INSTANCE.getManager().getDataPack(player);
            final boolean isUnlocked = dataPack.hasUnlockedBBM(getId());
            String name = menu.getConfiguration().getString((isUnlocked ? "unlocked" : "locked") + "-format.name");
            if (name != null) {
                Material mat = Material.getMaterial(material);
                name = name.replace("{material}", (mat != null ? INSTANCE.getManager().getTranslatedName(mat)
                        : getId().replace("_", " ")));
                String newName = (isUnlocked ? unlockedName : lockedName);
                if (newName != null) itemMeta.setDisplayName(INSTANCE.getManager().color(newName.replace("{material}", name)));
                itemMeta.setLore(new ArrayList<String>() {{
                    List<String> newLore = (isUnlocked ? unlockedLore : lockedLore);
                    for (int i = -1; ++i < newLore.size(); ) {
                        String line = newLore.get(i);
                        if (!line.equalsIgnoreCase("{requirement}")) {
                            add(INSTANCE.getManager().color(INSTANCE.papiText(player, line
                                    .replace("{price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), getPrice()))
                                    .replace("{raw-price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), getPrice()))
                                    .replace("{permission}", (getPermission() != null ? getPermission() : ""))
                                    .replace("{id}", (getId() != null ? getId() : "")))));
                        } else {
                            if (getRequirement() != null && getRequirement().isEmpty()) {
                                for (int j = -1; ++j < getRequirement().size(); ) {
                                    final String rLine = getRequirement().get(j);
                                    if (rLine != null) add(INSTANCE.getManager().color(INSTANCE.papiText(player, rLine
                                            .replace("{price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), getPrice()))
                                            .replace("{raw-price}", INSTANCE.getEconomyHandler().format(shop, shop.getCurrencyType(), getPrice()))
                                            .replace("{permission}", (getPermission() != null ? getPermission() : ""))
                                            .replace("{id}", (getId() != null ? getId() : "")))));
                                }
                            }
                        }
                    }
                }});
                itemStack.setItemMeta(itemMeta);
            }
        }

        return INSTANCE.updateNBT(itemStack, "ds-bbm", shop.getShopId().toString());
    }

    public boolean hasValidMaterial(@NotNull String material) {
        if (getMaterial() != null && getMaterial().contains(":")) {
            final String[] args = getMaterial().split(":");
            Material mat = Material.getMaterial(args[0]);
            if (mat != null) return true;
            else if (INSTANCE.isItemAdderInstalled()) {
                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(args[0]);
                return (customBlock != null);
            }
        } else {
            Material mat = Material.getMaterial(getMaterial());
            if (mat != null) return true;
            else if (INSTANCE.isItemAdderInstalled()) {
                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.getInstance(getMaterial());
                return (customBlock != null);
            }
        }
        return false;
    }

    @Override
    public int compareTo(@NotNull Appearance appearance) {return (getId().compareTo(appearance.getId()));}
}
