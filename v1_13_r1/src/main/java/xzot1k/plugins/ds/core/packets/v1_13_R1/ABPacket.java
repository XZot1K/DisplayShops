/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_13_R1;

import net.minecraft.server.v1_13_R1.ChatMessageType;
import net.minecraft.server.v1_13_R1.IChatBaseComponent;
import net.minecraft.server.v1_13_R1.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_13_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.ActionBarHandler;

public class ABPacket implements ActionBarHandler {
    @Override
    public void sendActionBar(@NotNull Player player, @NotNull String message) {
        IChatBaseComponent iChatBaseComponent = IChatBaseComponent.ChatSerializer.a("{\"text\": \""
                + DisplayShops.getPluginInstance().getManager().color(message) + "\"}");
        PacketPlayOutChat bar = new PacketPlayOutChat(iChatBaseComponent, ChatMessageType.GAME_INFO);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(bar);
    }

}
