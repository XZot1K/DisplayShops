/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_18_R2;

import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTReadLimiter;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.SerializeUtil;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

public class SUtil implements SerializeUtil {

    private final DisplayShops INSTANCE;

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

                Constructor<?> constructor = net.minecraft.world.item.ItemStack.class.getDeclaredConstructor(NBTTagCompound.class);
                constructor.setAccessible(true);

                return CraftItemStack.asBukkitCopy((net.minecraft.world.item.ItemStack) constructor.newInstance(tag));
            } catch (IOException | NoSuchMethodException | InstantiationException
                     | IllegalAccessException | InvocationTargetException e) {e.printStackTrace();}

        YamlConfiguration restoreConfig = new YamlConfiguration();
        try {
            restoreConfig.loadFromString(itemString.replace("[sq]", "'").replace("[dq]", "\""));
        } catch (InvalidConfigurationException e) {e.printStackTrace();}
        return restoreConfig.getItemStack("item");
    }

    public String toJSON(@NotNull ItemStack itemStack) {
        final net.minecraft.world.item.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.u(); // getOrCreateTag()
        return tag.toString();
    }

    public String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag) {
        final net.minecraft.world.item.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.u(); // getOrCreateTag()
        return tag.l(nbtTag); // getString()
    }

    public ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value) {
        final net.minecraft.world.item.ItemStack item = CraftItemStack.asNMSCopy(itemStack);
        final NBTTagCompound tag = item.u(); // getOrCreateTag()

        tag.a(nbtTag, value); // putString()
        item.b(tag); // save()

        return CraftItemStack.asBukkitCopy(item);
    }

}