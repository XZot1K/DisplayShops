/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  com.sk89q.worldguard.bukkit.WorldGuardPlugin
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 */
package xzot1k.plugins.ds.core.hooks;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xzot1k.plugins.ds.DisplayShops;

public class WorldGuardHandler {
    private final DisplayShops instance;
    private final WorldGuardPlugin worldGuardPlugin;

    public WorldGuardHandler(DisplayShops instance) {
        this.instance = instance;
        this.worldGuardPlugin = this.getInstance().getServer().getPluginManager().getPlugin("WorldGuard") != null ? WorldGuardPlugin.inst() : null;
    }

    public boolean passedWorldGuardHook(Player player, Location location) {
        return true;
        //if (this.worldGuardPlugin == null) return true;
        //return this.worldGuardPlugin.canBuild(player, location);
    }

    private DisplayShops getInstance() {
        return this.instance;
    }
}

