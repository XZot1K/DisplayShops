package xzot1k.plugins.ds.core;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.objects.Display;
import xzot1k.plugins.ds.api.objects.Shop;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayManager {

    private final ConcurrentHashMap<UUID, Display> shopDisplays = new ConcurrentHashMap<>();

    public DisplayManager() {

    }

    public boolean ShouldSeeDisplay(UUID shopId, @NotNull Player player) {
        final Display display = shopDisplays.getOrDefault(shopId, null);
        if (display == null) {return false;}
        return display.shouldSee(player);
    }

    public Display getDisplay(UUID shopId) {
        Display display = shopDisplays.getOrDefault(shopId, null);
        if (display == null) {
            Shop shop = DisplayShops.getPluginInstance().getManager().getShopById(shopId);
            if (shop != null) {getShopDisplays().put(shopId, display = new Display(shop));}
        }
        return display;
    }

    // getters & setters
    public ConcurrentHashMap<UUID, Display> getShopDisplays() {return shopDisplays;}
}