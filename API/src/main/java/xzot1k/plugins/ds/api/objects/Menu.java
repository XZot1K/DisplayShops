package xzot1k.plugins.ds.api.objects;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public interface Menu {

    /**
     * @return Gets the configuration of the menu.
     */
    FileConfiguration getConfiguration();

    /**
     * @return The name of the configuration file associated to the menu.
     */
    String getFileName();

    /**
     * @return The menu name used for identification.
     */
    String getMenuName();

    /**
     * Builds the menu using a defined search text alongside the player.
     *
     * @param player     The player to base properties around.
     * @param searchText The searched text, if applicable.
     * @return The created menu.
     */
    Inventory build(@NotNull Player player, @Nullable String... searchText);

    /**
     * Checks if a title matches the title of the menu.
     *
     * @param title The title to compare.
     * @return Whether the title matches the menu's title.
     */
    boolean matches(@NotNull String title);

    /**
     * Gets the name of the button from the slot provided.
     *
     * @param slot The slot to look for.
     * @return The name of the button, if found.
     */
    String getButtonName(int slot);

    /**
     * Switch the current page to the specified page.
     *
     * @param inventory The inventory of to update for the selected page.
     * @param player    The player to switch the page for.
     * @param page      The page to switch to.
     */
    void switchPage(@NotNull Inventory inventory, @NotNull Player player, int page);

    /**
     * Load pages based on specific attributes alongside filter text.
     *
     * @param player     The player to create pages for.
     * @param dataPack   The player's datapack.
     * @param shop       The shop the player has currently selected.
     * @param searchText The filter text to filter the items by.
     */
    void loadPages(@NotNull Player player, @NotNull DataPack dataPack, @Nullable Shop shop, @Nullable String searchText);

    /**
     * Update the button based on specific attributes.
     *
     * @param player     The player to update the button for.
     * @param inventory  The inventory (menu) to update it in.
     * @param slot       The slot to update the button at.
     * @param shop       The shop to update the button for, if available.
     * @param emptySlots The empty slots to base it around.
     */
    void updateButton(@NotNull Player player, @NotNull Inventory inventory, int slot,
                      @Nullable Shop shop, @Nullable List<Integer> emptySlots);

    /**
     * @return The configuration file associated to the menu.
     */
    File getFile();

    /**
     * Saves the configuration file to the disk.
     */
    void save();

    /**
     * Reloads the configuration file from disk.
     */
    void reload();

    /**
     * @return The map of buttons alongside their placement for the menu.
     */
    HashMap<Integer, String> getButtonLocationMap();

}
