/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_8_R3;

import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTReadLimiter;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.SerializeUtil;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;

public class SUtil implements SerializeUtil {

    private DisplayShops INSTANCE;

    public SUtil(DisplayShops instance) {this.INSTANCE = instance;}

    public String toString(@NotNull ItemStack itemStack) {
        YamlConfiguration itemConfig = new YamlConfiguration();
        itemConfig.set("item", itemStack);
        return itemConfig.saveToString().replace("'", "[sq]").replace("\"", "[dq]");
    }

    public ItemStack toItem(@NotNull String itemString) {

        if (!itemString.contains("item:"))
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(new BigInteger(itemString, 32).toByteArray());
                final DataInputStream dataInputStream = new DataInputStream(inputStream);
                final NBTTagCompound tag = NBTCompressedStreamTools.a(dataInputStream, NBTReadLimiter.a); // read() and UNLIMITED

                dataInputStream.close();
                inputStream.close();

                return CraftItemStack.asBukkitCopy(net.minecraft.server.v1_8_R3.ItemStack.createStack(tag));
            } catch (IOException e) {e.printStackTrace();}

        YamlConfiguration restoreConfig = new YamlConfiguration();
        try {
            restoreConfig.loadFromString(itemString.replace("[sq]", "'").replace("[dq]", "\""));
        } catch (InvalidConfigurationException e) {e.printStackTrace();}
        return restoreConfig.getItemStack("item");
    }

    public String toJSON(@NotNull ItemStack itemStack) {
        final net.minecraft.server.v1_8_R3.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = item.getTag();
        if (tag == null) tag = new NBTTagCompound();
        return tag.toString();
    }

    public String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag) {
        final net.minecraft.server.v1_8_R3.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = item.getTag();
        if(tag == null) tag = new NBTTagCompound();
        return tag.getString(nbtTag);
    }

    public ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value) {
        final net.minecraft.server.v1_8_R3.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = item.getTag();
        if(tag == null) tag = new NBTTagCompound();

        tag.setString(nbtTag, value);
        item.save(tag);

        return CraftItemStack.asBukkitCopy(item);
    }

}